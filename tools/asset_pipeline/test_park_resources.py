import struct
import unittest
import numpy as np

from .park_resources import parse_recipe, require_resources, transform_points
from .park_collision import pack_mesh, decode_mesh, NativeTriangle


class ParkResourceTests(unittest.TestCase):
    def test_packed_rgb565_neutral_normal_and_truncation(self):
        from .park_resources import decode_park_rgb565
        # Packed 16px base occupies x=16..31; untouched origin is zero.
        raw = bytearray(4096)
        for i in range(0, len(raw), 2):
            raw[i:i+2] = bytes.fromhex('841f')
        raw[:128] = bytes(128)
        self.assertEqual(decode_park_rgb565(bytes(raw),16,16), bytes((131,129,255,255))*256)
        with self.assertRaises(ValueError):
            decode_park_rgb565(bytes(32),16,16)

    def test_recipe_resolves_item_to_model_and_ordered_materials(self):
        recipe = parse_recipe(b'<compositeasset><mat id="0x1" type="environmentPark"><sp id="0x9" chn="diffuse"/></mat><comp n="ramps"><mod id="0x2" n="ramp"><lod idx="0" arenaid="0x7"><matinst><matvar id="0x1"/></matinst></lod></mod></comp></compositeasset>')
        self.assertEqual(recipe.items[2].model_id, 7)
        self.assertEqual(recipe.items[2].material_ids, (1,))
        self.assertEqual(recipe.materials[1].bindings, {'diffuse': 9})
        with self.assertRaises(ValueError):
            require_resources(recipe, {7: 'model'}, {})
        with self.assertRaises(ValueError):
            require_resources(recipe, {}, {9: 'texture'})

    def test_transform_matches_authored_row_vector_position(self):
        m = np.array(((0.,0.,-1.,0.),(0.,1.,0.,0.),(1.,0.,0.,0.),(10.,2.,3.,1.)))
        np.testing.assert_allclose(transform_points(np.array([[0.,0.,4.]]), m), [[14.,2.,3.]])

    def test_native_collision_round_trip_preserves_features(self):
        t = NativeTriangle(((0.,0.,0.),(2.,0.,0.),(0.,0.,2.)), (0x81,0x1a,0x3f), 7, 12, 0xe1)
        raw = pack_mesh((t,), 0x10)
        decoded, flags = decode_mesh(raw)
        self.assertEqual(flags, 0x10)
        self.assertEqual(decoded, (t,))
        with self.assertRaises(ValueError):
            decode_mesh(raw[:-1])

    def test_unsigned_compressed_offsets_follow_native_reader(self):
        # One vertex uses offset 40000: interpreting it as signed loses 65536.
        t = NativeTriangle(((40000.,0.,0.),(40001.,0.,0.),(40000.,0.,1.)), None, 0, 0, 1)
        raw = bytearray(pack_mesh((t,), 0))
        cluster = struct.unpack_from('>I', raw, 144)[0]
        old_units = bytes(raw[cluster+64:])
        verts = struct.pack('>3i', 0,0,0) + struct.pack('>9H',40000,0,0,40001,0,0,40000,0,1)
        verts += bytes(32-len(verts))
        header = bytearray(raw[cluster:cluster+16])
        struct.pack_into('>H', header, 4, 2)
        struct.pack_into('>H', header, 8, 48+len(old_units))
        header[12] = 1
        raw[cluster:] = header+verts+old_units
        struct.pack_into('>I', raw, 80, len(raw))
        decoded, _ = decode_mesh(bytes(raw))
        self.assertEqual(decoded[0].points, t.points)
