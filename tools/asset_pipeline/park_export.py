"""Bake full park layouts into validated supplemental SKATE packages."""
from dataclasses import dataclass, asdict
from pathlib import Path, PurePosixPath
import copy
import hashlib
import itertools
import json
import math
import os
import struct
import tempfile
import numpy as np

from .environment import Collections, key_hash
from .park_layout import parse_layout
from .park_resources import ParkCatalog, resolve_piece, packed_frame, transform_points
from .park_collision import NativeTriangle, pack_mesh
from .teleports import location_records
from .map_writer import write
from tools.owned_game.big import BigArchive

FULL_WORLDS = {'MegaPark':'dist_megapark_full', 'IndustrialSkatePark':'dist_industrialpark_full',
               'StartPark':'dist_startpark_full', 'DownTownSkatePark':'dist_downtownpark_full'}


@dataclass(frozen=True)
class Selection:
    member: str
    stream: str
    world_id: int
    locator: str


@dataclass(frozen=True)
class ExportReceipt:
    district: str
    layout_member: str
    layout_sha256: str
    placed_pieces: int
    resources: int
    render_triangles: int
    collision_triangles: int
    rails: int
    package_sha256: str
    spawn: tuple[float, ...]
    heading: float
    resource_hashes: dict[str, str]


def full_selection(data: dict, district: str) -> Selection:
    if district not in FULL_WORLDS: raise ValueError('Unsupported full park district')
    c = Collections(data)
    try: fields, _ = c.resolve('world', FULL_WORLDS[district])
    except KeyError as e: raise ValueError('Full park world is absent') from e
    value = fields[key_hash('Hash_79B248E8469DC5DA')]['data'].replace('\\','/')
    p = PurePosixPath(value)
    if p.is_absolute() or '..' in p.parts or p.suffix != '.park': raise ValueError('Unsafe park reference')
    stream = fields[key_hash('WorldStream')]['data']
    if stream != 'DIST_'+district: raise ValueError('Park world points at another district')
    return Selection('data/'+value, stream,
                     int(fields[key_hash('Hash_7DFD997E3F13540A')]['data'][:16],16),
                     fields[key_hash('Hash_3735C5C12E8E7AE1')]['data'])


def transform_segment(payload: str, transform) -> str:
    values = np.frombuffer(bytes.fromhex(payload),dtype='>f4').copy()
    if values.size != 30 or not np.isfinite(values).all(): raise ValueError('Invalid native park rail')
    t = np.asarray(transform)
    for word in (0,4,8): values[word:word+3] = values[word:word+3] @ t[:3,:3]
    values[12:15] = transform_points(values[12:15],t)
    corners = np.array(list(itertools.product(*zip(values[20:23],values[24:27]))))
    bounds = transform_points(corners,t)
    values[20:23] = bounds.min(axis=0); values[24:27] = bounds.max(axis=0)
    return values.tobytes().hex()


def publish_package(prepared: Path, target: Path):
    os.replace(prepared,target)


def load_selected_layout(archive, selected: Selection):
    entries = [e for e in archive.entries if e.path == selected.member]
    if len(entries) != 1: raise ValueError('Missing or ambiguous full park layout')
    raw = archive.read(entries[0])
    # The shipped Starter_Fullpark retains an older embedded park identifier
    # than its current world row. The database's exact file reference is the
    # authority; do not reinterpret that identifier as a compatibility check.
    return raw, parse_layout(raw)


def _collections(game_root):
    root = game_root.parent/'data'
    marker = json.loads((root/'installation.json').read_text())
    relative = PurePosixPath(marker['directory'])
    if relative.is_absolute() or '..' in relative.parts: raise ValueError('Invalid active installation')
    return json.loads((root/relative/'assets/private/stock/skater-collections.json').read_text())


def _start(game_root, selected):
    found = []
    for p in (game_root/'data/content/global_locators').rglob('*.rx2'):
        if p.parent.name.casefold() != selected.stream.casefold(): continue
        for r in location_records(p.read_bytes()):
            if r['locator'] == selected.locator: found.append(r['matrix'])
    unique = {json.dumps(m) for m in found}
    if len(unique) != 1: raise ValueError('Missing or ambiguous authored park start')
    m = found[0]
    return tuple(m[3][:3]), math.atan2(m[2][0],m[2][2])


def export_full_park(game_root: Path, district: str, destination: Path, *, catalog=None, collections=None) -> ExportReceipt:
    selected = full_selection(_collections(game_root) if collections is None else collections,district)
    archive = BigArchive(game_root/'data/big/miscload.big')
    raw,layout = load_selected_layout(archive,selected)
    spawn, heading = _start(game_root,selected)
    destination.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='park-bake-',dir=destination.parent) as tmp:
        root = Path(tmp)
        catalog = catalog or ParkCatalog(game_root,root/'textures')
        models = []; used = set(); collision = []; rails = []; sources = {}
        render_count = collision_count = 0
        for i,p in enumerate(layout.pieces):
            a = resolve_piece(p.resource_id,catalog)
            if a.dynamic != p.dynamic: raise ValueError('Park piece category mismatch')
            sources[f'{p.resource_id:016x}'] = a.source_sha256
            arrays = {}; mesh_entries = []
            t = np.asarray(p.transform,dtype=float)
            for j,(mesh,material,mid) in enumerate(zip(a.meshes,a.materials,a.item.material_ids,strict=True)):
                arrays[f'vertices_{j}'] = transform_points(mesh.vertices,t).astype('f4')
                arrays[f'faces_{j}'] = mesh.faces
                if mesh.uvs is not None: arrays[f'uvs_{j}'] = mesh.uvs
                normal,tangent,handedness = packed_frame(a,mesh)
                if normal is None: normal = mesh.normals
                if normal is not None: arrays[f'retail_normals_{j}'] = (normal @ t[:3,:3]).astype('f4')
                if tangent is not None and handedness is not None:
                    arrays[f'retail_tangents_{j}'] = (tangent @ t[:3,:3]).astype('f4')
                    arrays[f'retail_tangent_handedness_{j}'] = handedness
                bindings = {role:f'0x{tid:016x}' for role,tid in material.bindings.items()}
                used.update(material.bindings.values())
                alpha = 1 if 'alphatest' in material.shader.lower() else 2 if 'water' in material.shader.lower() else 0
                mesh_entries.append(dict(index=j,texture_id=bindings.get('diffuse',bindings.get('transparent')),
                    retail_texture_ids=bindings,shader_name=material.shader,retail_material_guid=f'0x{mid:016x}',
                    source_offsets=mesh.source_offsets,alpha_mode=alpha,color=list(p.color)))
                render_count += len(mesh.faces)
            npz = root/f'piece-{i}.npz'; np.savez(npz,**arrays)
            models.append(dict(asset_id=f'0x{p.resource_id:016x}',npz=npz.name,meshes=mesh_entries))
            for j,(triangles,flags) in enumerate(a.collision):
                placed = tuple(NativeTriangle(tuple(tuple(v) for v in transform_points(q.points,t)),q.edges,q.surface,q.group,q.flags) for q in triangles)
                collision.append((f'park/{i}/{p.resource_id:016x}/{j}',pack_mesh(placed,flags)))
                collision_count += len(placed)
            for r in a.rails:
                r = copy.deepcopy(r); r['asset_id'] = f'0x{a.item.model_id:016x}'
                r['stream_file'] = f'parkassets.big/{a.item.model_id:016x}#placement={i}'
                r['placement_source_offset'] = p.source_offset
                r['native_segment_payloads'] = [transform_segment(s,t) for s in r['native_segment_payloads']]
                rails.append(r)
        textures = {f'0x{tid:016x}':catalog.textures[tid] for tid in sorted(used)}
        manifest = dict(map_name=district,models=models,textures=textures,grind_splines=rails,
                        normal_texture_policy=dict(excluded_texture_ids=['0x0000043d03e3870a','0x0000475d03e3870a']),
                        other_presentation_assets=[],grind_coordinate_policy={'mode':'world_space'})
        mp = root/'manifest.json'; mp.write_text(json.dumps(manifest))
        cp = root/'park.rwcmset'
        with cp.open('wb') as f:
            f.write(b'RWCMSET1'+struct.pack('<I',len(collision)))
            for name,data in collision:
                b = name.encode();f.write(struct.pack('<I',len(b))+b+struct.pack('<I',len(data))+data)
        out = root/'park.skate'
        write(mp,out,cp,prepared_spawn=spawn)
        data = bytearray(out.read_bytes()); offset = 16+len(district.encode())
        struct.pack_into('<f',data,offset+12,heading);out.write_bytes(data)
        receipt = ExportReceipt(district,selected.member,hashlib.sha256(raw).hexdigest(),len(layout.pieces),
            len(sources),render_count,collision_count,len(rails),hashlib.sha256(data).hexdigest(),spawn,heading,sources)
        publish_package(out,destination)
        destination.with_suffix('.receipt.json').write_text(json.dumps(asdict(receipt),indent=2))
        return receipt


if __name__ == '__main__':
    import argparse
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--game-root',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--district',choices=tuple(FULL_WORLDS),action='append')
    args = p.parse_args();args.output.mkdir(parents=True,exist_ok=True)
    catalog = ParkCatalog(args.game_root,args.output/'resource-cache')
    for district in args.district or FULL_WORLDS:
        receipt = export_full_park(args.game_root,district,args.output/(district+'.skate'),catalog=catalog)
        print(json.dumps(asdict(receipt)),flush=True)
