"""Read shipped full-park placement blocks; retain unrelated metadata verbatim.

The four length-prefixed blocks contain static/dynamic placements and their
independent resource inventories. Serialized entries occupy 36/48 bytes while
the blocks reserve 48/64 bytes per entry. Unused capacity contains allocator
fill in shipped files: it is not additional placement data.
"""
from dataclasses import dataclass
import math
import struct


@dataclass(frozen=True)
class ParkPiece:
    resource_id: int
    transform: tuple[tuple[float, ...], ...]
    source_offset: int
    dynamic: bool
    color: tuple[float, float, float]
    authored_flags: int | None = None


@dataclass(frozen=True)
class ParkLayout:
    version: int
    world_id: int
    pieces: tuple[ParkPiece, ...]
    metadata: bytes


def _transform(position, quaternion):
    if not all(math.isfinite(v) for v in (*position, *quaternion)):
        raise ValueError('Nonfinite park transform')
    x, y, z, w = quaternion
    length = sum(v*v for v in quaternion)
    if abs(length-1.) > .001:
        raise ValueError('Park quaternion is not unit length')
    return ((1-2*(y*y+z*z), 2*(x*y+z*w), 2*(x*z-y*w), 0.),
            (2*(x*y-z*w), 1-2*(x*x+z*z), 2*(y*z+x*w), 0.),
            (2*(x*z+y*w), 2*(y*z-x*w), 1-2*(x*x+y*y), 0.),
            (*position, 1.))


def _pieces(block, offset, dynamic):
    if len(block) < 8:
        raise ValueError('Truncated park placement block')
    schema, count = struct.unpack_from('>II', block)
    if schema not in (1, 2):
        raise ValueError('Unsupported park placement schema')
    if dynamic and schema != 1:
        raise ValueError('Unsupported dynamic park placement schema')
    stride, capacity = (40, 48) if dynamic else ((36, 48) if schema == 1 else (48, 64))
    if len(block) != 8 + count*capacity:
        raise ValueError('Invalid park placement capacity')
    result = []
    for i in range(count):
        at = 8+i*stride
        values = struct.unpack_from('>7fQ', block, at)
        color = struct.unpack_from('>3f', block, at+36) if schema == 2 else (1., 1., 1.)
        if not values[7] or not all(math.isfinite(v) and 0. <= v <= 1.001 for v in color):
            raise ValueError('Invalid park resource or tint')
        result.append(ParkPiece(values[7], _transform(values[:3], values[3:7]),
                                offset+at, dynamic, color,
                                struct.unpack_from('>I', block, at+36)[0] if dynamic else None))
    return result


def _inventory(block):
    if len(block) < 4:
        raise ValueError('Truncated park resource inventory')
    count = struct.unpack_from('>I', block)[0]
    if len(block) != 4+count*8:
        raise ValueError('Invalid park resource inventory extent')
    ids = struct.unpack_from('>'+str(count)+'Q', block, 4)
    if len(set(ids)) != len(ids) or 0 in ids:
        raise ValueError('Invalid park resource inventory IDs')
    return ids


def parse_layout(raw: bytes) -> ParkLayout:
    if len(raw) < 28:
        raise ValueError('Truncated park header')
    version, *sizes, world = struct.unpack_from('>5IQ', raw)
    if version not in (10, 11, 12):
        raise ValueError(f'Unsupported full-park version {version}')
    at = 28
    blocks = []
    offsets = []
    for size in sizes:
        if size > len(raw)-at:
            raise ValueError('Truncated park section')
        blocks.append(raw[at:at+size]); offsets.append(at); at += size
    # Camera/profile metadata is outside the four gameplay blocks. It is
    # retained, not interpreted or used to invent placements. BIG validates
    # the exact member size/decompression; a private receipt pins its hash.
    if len(raw)-at < 64:
        raise ValueError('Truncated park metadata')
    static = _pieces(blocks[0], offsets[0], False)
    dynamic = _pieces(blocks[1], offsets[1], True)
    for pieces, inventory in ((static, blocks[2]), (dynamic, blocks[3])):
        actual = tuple(dict.fromkeys(p.resource_id for p in pieces))
        if actual != _inventory(inventory):
            raise ValueError('Park placements disagree with resource inventory')
    return ParkLayout(version, world, tuple(static+dynamic), raw[at:])
