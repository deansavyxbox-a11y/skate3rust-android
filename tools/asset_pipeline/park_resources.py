"""Resolve authored recipe IDs to model, material, texture and collision data."""
from dataclasses import dataclass
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
import numpy as np
import hashlib
import struct
from .park_collision import VENDOR, decode_mesh
from tools.owned_game.big import BigArchive

UTT = Path(__file__).resolve().parents[1]/'vendor/utt'
for directory in (VENDOR, UTT):
    if str(directory) not in sys.path: sys.path.insert(0,str(directory))
from build_retail_collision_archive import collision_sections
from retail_grind_splines import decode_grind_splines
from mdl_parser import parse_rx2 as parse_model
import rx2_parser


@dataclass(frozen=True)
class RecipeItem:
    name: str
    model_id: int
    material_ids: tuple[int, ...]


@dataclass(frozen=True)
class RecipeMaterial:
    shader: str
    bindings: dict[str, int]


@dataclass(frozen=True)
class Recipe:
    items: dict[int, RecipeItem]
    materials: dict[int, RecipeMaterial]


def parse_recipe(raw: bytes) -> Recipe:
    root = ET.fromstring(raw)
    if root.tag != 'compositeasset': raise ValueError('Unsupported park recipe root')
    materials = {}
    for m in root.findall('mat'):
        key = int(m.attrib['id'],0)
        if key in materials or not key: raise ValueError('Duplicate park material')
        bindings = {}
        for b in m.findall('sp'):
            role = b.attrib['chn']; role = {'transparency':'transparent', 'detailnormal':'detail'}.get(role,role)
            if role in bindings: raise ValueError('Duplicate recipe texture channel')
            bindings[role] = int(b.attrib['id'],0)
        materials[key] = RecipeMaterial(m.attrib['type'], bindings)
    items = {}
    for item in root.iter('mod'):
        key = int(item.attrib['id'],0)
        lod = item.find("lod[@idx='0']")
        if lod is None: raise ValueError('Park item has no LOD0')
        ids = tuple(int(m.attrib['id'],0) for m in lod.iter('matvar'))
        if key in items or not ids or any(m not in materials for m in ids):
            raise ValueError('Invalid park item/material references')
        items[key] = RecipeItem(item.attrib['n'], int(lod.attrib['arenaid'],0), ids)
    return Recipe(items, materials)


def require_resources(recipe: Recipe, models, textures):
    for item in recipe.items.values():
        if item.model_id not in models: raise ValueError(f'Missing park model {item.model_id:016x}')
        for mid in item.material_ids:
            for tid in recipe.materials[mid].bindings.values():
                if tid not in textures: raise ValueError(f'Missing park texture {tid:016x}')


def transform_points(points, transform):
    t = np.asarray(transform, dtype=np.float64)
    if t.shape != (4,4) or not np.isfinite(t).all() or not np.allclose(t[:,3],(0,0,0,1)):
        raise ValueError('Invalid park affine transform')
    return np.asarray(points) @ t[:3,:3] + t[3,:3]


@dataclass
class PieceAsset:
    item: RecipeItem
    meshes: list
    materials: tuple[RecipeMaterial, ...]
    collision: tuple
    rails: list
    raw: bytes
    dynamic: bool
    source_sha256: str


def decode_park_rgb565(raw: bytes, width: int, height: int) -> bytes:
    """Read X360 packed base mip RGB565, preserving original BE words.

    Addressing follows SK8-Engine's PackedBaseOffset/PackedTiledOffset.
    """
    lw, lh = (width-1).bit_length(), (height-1).bit_length()
    px, py = (0,0) if min(lw,lh)>4 else ((0,16) if lw>lh else (16,0))
    pitch = (width+31)&~31
    result = bytearray()
    for row in range(height):
        for col in range(width):
            x,y = col+px,row+py
            macro = ((x>>5)+(y>>5)*(pitch>>5))<<8
            micro = ((x&7)+((y&14)<<2))<<1
            offset = macro+((micro&~15)<<1)+(micro&15)+((y&1)<<4)
            address = ((offset&~511)<<3)+((y&16)<<7)+((offset&448)<<2)+(((((y&8)>>2)+(x>>3))&3)<<6)+(offset&63)
            if address+2>len(raw): raise ValueError('Truncated park RGB565 texture')
            value = int.from_bytes(raw[address:address+2],'big')
            result.extend((((value>>11)&31)*255//31,((value>>5)&63)*255//63,(value&31)*255//31,255))
    return bytes(result)


class ParkCatalog:
    def __init__(self, game_root: Path, cache: Path):
        self.archive = BigArchive(game_root/'data/content/parkassets.big')
        self.cache = cache
        cache.mkdir(parents=True, exist_ok=True)
        self.recipes = {}
        self.models = {False:{}, True:{}}
        self.texture_entries = {}
        self.pieces = {}
        self.textures = {}
        for kind, dynamic in (('static',False),('dynamic',True)):
            entries = [e for e in self.archive.entries if f'/recipe/{kind}/' in e.path and e.path.endswith('.xml')]
            if len(entries) != 1: raise ValueError('Ambiguous park recipe')
            self.recipes[dynamic] = parse_recipe(self.archive.read(entries[0]))
        for entry in self.archive.entries:
            match = re.search(r'/0x([0-9a-fA-F]{16})\.rx2$',entry.path)
            if match is None: continue
            key = int(match.group(1),16)
            if '/model/' in entry.path:
                dynamic = '/dynamic/model/' in entry.path
                if key in self.models[dynamic]: raise ValueError('Ambiguous park model ID')
                self.models[dynamic][key] = entry
            elif '/texture/' in entry.path:
                self.texture_entries.setdefault(key,[]).append(entry)

    def texture(self, key: int):
        if key in self.textures: return self.textures[key]
        entries = self.texture_entries.get(key,[])
        if not entries: raise ValueError(f'Missing park texture {key:016x}')
        source = self.archive.read(entries[0])
        if any(self.archive.read(e) != source for e in entries[1:]):
            raise ValueError('Conflicting park texture resource')
        parsed = rx2_parser.parse_rx2(source)
        if len(parsed.textures) != 1:
            raise ValueError(f'Unsupported multi-surface park texture {key:016x}')
        t = parsed.textures[0]
        if t.fmt_id == rx2_parser.FMT_B5G6R5:
            t.rgba = decode_park_rgb565(source[t.data_offset:t.data_offset+t.buffer_size], t.width, t.height)
        if len(t.rgba) != t.width*t.height*4: raise ValueError('Incomplete park texture decode')
        name = f'0x{key:016x}'
        path = self.cache/(name+'.rgba')
        path.write_bytes(t.rgba)
        value = dict(width=t.width,height=t.height,rgba=str(path.resolve()), source_sha256=hashlib.sha256(source).hexdigest())
        self.textures[key] = value
        return value


def resolve_piece(resource_id: int, catalog: ParkCatalog) -> PieceAsset:
    if resource_id in catalog.pieces: return catalog.pieces[resource_id]
    choices = [(dynamic,r.items[resource_id],r) for dynamic,r in catalog.recipes.items() if resource_id in r.items]
    if len(choices) != 1: raise ValueError(f'Unresolved/ambiguous park piece {resource_id:016x}')
    dynamic,item,recipe = choices[0]
    entry = catalog.models[dynamic].get(item.model_id)
    if entry is None: raise ValueError(f'Missing park model {item.model_id:016x}')
    raw = catalog.archive.read(entry)
    model = parse_model(raw)
    if not model.meshes or len(model.meshes) != len(item.material_ids):
        raise ValueError(f'Park model/material count mismatch: {item.name}')
    materials = tuple(recipe.materials[m] for m in item.material_ids)
    for m in materials:
        for tid in m.bindings.values(): catalog.texture(tid)
    sections = collision_sections(raw)
    if not sections: raise ValueError(f'Park piece lacks native collision: {item.name}')
    collision = tuple(decode_mesh(s) for s in sections)
    rails = decode_grind_splines(raw)
    value = PieceAsset(item, model.meshes, materials, collision, rails, raw, dynamic, hashlib.sha256(raw).hexdigest())
    catalog.pieces[resource_id] = value
    return value


def packed_frame(asset: PieceAsset, mesh):
    """Decode the model's authored D3D NORMAL/TANGENT/BINORMAL attributes."""
    vectors = {}
    for attribute in mesh.attributes:
        d = attribute.descriptor
        if len(d) != 16 or d[9] not in (3,6,7) or int.from_bytes(d[4:8],'big') & 63 != 16:
            continue
        rows = []
        for i in range(mesh.vertex_count):
            p = struct.unpack_from('>I', asset.raw, mesh.source_offsets['vertex_buffer']+i*mesh.vertex_stride+attribute.offset)[0]
            components = []
            for value,bits,divisor in ((p&2047,11,1023.),((p>>11)&2047,11,1023.),((p>>22)&1023,10,511.)):
                sign = 1<<(bits-1)
                components.append(((value^sign)-sign)/divisor)
            rows.append(components)
        v = np.asarray(rows,dtype='f4')
        v /= np.maximum(np.linalg.norm(v,axis=1,keepdims=True),1e-20)
        vectors[d[9]] = v
    normal = vectors.get(3)
    tangent,binormal = vectors.get(6),vectors.get(7)
    if normal is None and tangent is not None and binormal is not None:
        normal = np.cross(tangent,binormal)
        normal /= np.maximum(np.linalg.norm(normal,axis=1,keepdims=True),1e-20)
    handedness = None
    if normal is not None and tangent is not None:
        handedness = np.ones(mesh.vertex_count,dtype='f4') if binormal is None else np.sign(np.sum(np.cross(normal,tangent)*binormal,axis=1))
    return normal,tangent,handedness
