"""Native collision adaptation: transform authored points, retain unit features.

Output is RWCM with float vertices and triangle units, already supported by
skate-data. Quad diagonals use the native smooth edge code 0x1a; boundary
codes retain the same association as the existing retail decoder.
"""
from dataclasses import dataclass
from pathlib import Path
import struct
import sys
import math

VENDOR = Path(__file__).resolve().parents[1]/'vendor/university/tools/vanilla_map_extraction/tools'
if str(VENDOR) not in sys.path:
    sys.path.insert(0, str(VENDOR))
from retail_collision_mesh import _decode_cluster


@dataclass(frozen=True)
class NativeTriangle:
    points: tuple[tuple[float, float, float], ...]
    edges: tuple[int, int, int] | None
    surface: int
    group: int
    flags: int


def _f32(x):
    return struct.unpack('>f', struct.pack('>f', x))[0]


def decode_mesh(raw: bytes):
    if len(raw) < 96:
        raise ValueError('Truncated native park collision')
    size = struct.unpack_from('>I', raw, 80)[0]
    table = struct.unpack_from('>I', raw, 52)[0]
    count = struct.unpack_from('>I', raw, 64)[0]
    gran = struct.unpack_from('>f', raw, 56)[0]
    if size != len(raw) or table < 96 or not count or table+count*4 > size or not math.isfinite(gran) or gran <= 0:
        raise ValueError('Invalid native park collision header')
    output = []
    prior = table+count*4
    for i in range(count):
        at = struct.unpack_from('>I', raw, table+i*4)[0]
        if at < prior or at+16 > size:
            raise ValueError('Invalid native collision cluster offset')
        nbytes = struct.unpack_from('>H', raw, at+8)[0]
        if nbytes < 16 or at+nbytes > size:
            raise ValueError('Truncated native collision cluster')
        prior = at+nbytes
        cluster = raw[at:prior]
        unit_start = (struct.unpack_from('>H', cluster, 4)[0]+1)*16
        unit_size = struct.unpack_from('>H', cluster, 2)[0]
        if unit_start+unit_size > len(cluster):
            raise ValueError('Invalid native collision unit extent')
        vertex_count, mode = cluster[10], cluster[12]
        vertices = []
        for j in range(vertex_count):
            if mode == 0:
                off, width = 16+j*16, 12
                if off+width > unit_start: raise ValueError('Truncated float vertices')
                p = struct.unpack_from('>3f', cluster, off)
            elif mode == 1:
                off = 28+j*6
                if off+6 > unit_start: raise ValueError('Truncated compressed vertices')
                base = struct.unpack_from('>3i', cluster, 16)
                delta = struct.unpack_from('>3H', cluster, off)
                p = tuple(_f32(_f32(max(-2147483648, min(2147483647, b+d)))*gran) for b,d in zip(base,delta))
            elif mode == 2:
                off = 16+j*12
                if off+12 > unit_start: raise ValueError('Truncated integer vertices')
                p = tuple(_f32(_f32(v)*gran) for v in struct.unpack_from('>3i', cluster, off))
            else:
                raise ValueError('Unsupported native vertex compression')
            if not all(math.isfinite(v) for v in p): raise ValueError('Nonfinite native collision vertex')
            vertices.append(p)
        header = bytearray(cluster[:16])
        struct.pack_into('>H', header, 4, vertex_count)
        units = cluster[unit_start:unit_start+unit_size]
        struct.pack_into('>H', header, 8, 16+vertex_count*16+len(units))
        header[12] = 0
        floats = b''.join(struct.pack('>4f', *v, 0.) for v in vertices)
        decoded, _, _, _ = _decode_cluster(bytes(header)+floats+units, 1., raw[62], raw[63])
        output.extend(NativeTriangle((t.a,t.b,t.c), t.edge_codes, t.surface, t.group_id or 0, (t.unit_flags & 0xf0)|1) for t in decoded)
    if len(output) != struct.unpack_from('>I', raw, 40)[0]:
        raise ValueError('Native park collision triangle count mismatch')
    return tuple(output), struct.unpack_from('>H', raw, 60)[0]


def pack_mesh(triangles, mesh_flags: int):
    if not triangles:
        raise ValueError('Park piece has no native collision triangles')
    clusters = []
    for start in range(0, len(triangles), 40):
        group = triangles[start:start+40]
        units = bytearray(); vertices = bytearray()
        for i,t in enumerate(group):
            if not all(math.isfinite(v) for p in t.points for v in p):
                raise ValueError('Nonfinite transformed collision')
            for p in t.points: vertices += struct.pack('>4f', *p, 0.)
            units += bytes((t.flags, i*3, i*3+1, i*3+2))
            if t.flags & 0x20:
                if t.edges is None: raise ValueError('Missing native edges')
                units += bytes(t.edges)
            if t.flags & 0x40: units += struct.pack('<H', t.group)
            if t.flags & 0x80: units += struct.pack('<H', t.surface)
        header = bytearray(16)
        struct.pack_into('>3H', header, 0, len(group), len(units), len(group)*3)
        struct.pack_into('>H', header, 8, 16+len(vertices)+len(units))
        header[10] = len(group)*3
        clusters.append(bytes(header)+vertices+units)
    header = bytearray(144)  # Runtime constructs its own broadphase, not the source KD tree.
    points = [p for t in triangles for p in t.points]
    struct.pack_into('>3f', header, 0, *(min(p[j] for p in points) for j in range(3)))
    struct.pack_into('>3f', header, 16, *(max(p[j] for p in points) for j in range(3)))
    struct.pack_into('>I', header, 40, len(triangles))
    struct.pack_into('>IIfHBBI', header, 48, 96,144,1.,mesh_flags,2,2,len(clusters))
    at = 144+len(clusters)*4
    table = bytearray()
    for c in clusters:
        table += struct.pack('>I', at); at += len(c)
    struct.pack_into('>I', header, 80, at)
    return bytes(header)+bytes(table)+b''.join(clusters)
