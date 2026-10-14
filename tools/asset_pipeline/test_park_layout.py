import math
import struct
import unittest

from .park_layout import parse_layout


def fixture(*, version=12, quaternion=(0., 0., 0., 1.), dynamic_count=1):
    static = struct.pack('>II', 2, 2)
    for x in (10., 20.):
        static += struct.pack('>7fQ3f', x, 0., -5., *quaternion, 11, .2, .4, .6)
    static += bytes(32)  # Reserved capacity: 64 bytes per schema-2 entry.
    dynamic = struct.pack('>II', 1, dynamic_count)
    for i in range(dynamic_count):
        dynamic += struct.pack('>7fQI', float(i), 2., 3., 0., 0., 0., 1., 22, 1)
    dynamic += bytes(8*dynamic_count)
    ids = struct.pack('>IQ', 1, 11), struct.pack('>IQ', 1, 22)
    blocks = (static, dynamic, *ids)
    return struct.pack('>5IQ', version, *(len(b) for b in blocks), 99) + b''.join(blocks) + bytes(64)


class ParkLayoutTests(unittest.TestCase):
    def test_dynamic_records_preserve_the_extra_authored_word(self):
        layout = parse_layout(fixture(dynamic_count=2))
        self.assertEqual(layout.pieces[3].transform[3], (1., 2., 3., 1.))
        self.assertEqual(layout.pieces[2].authored_flags, 1)

    def test_keeps_every_placement_and_cross_checks_resource_tables(self):
        layout = parse_layout(fixture())
        self.assertEqual(layout.world_id, 99)
        self.assertEqual([p.resource_id for p in layout.pieces], [11, 11, 22])
        self.assertEqual(layout.pieces[1].transform[3], (20., 0., -5., 1.))
        self.assertEqual(layout.pieces[0].source_offset, 36)
        self.assertAlmostEqual(layout.pieces[0].color[1], .4)
        self.assertEqual(layout.metadata, bytes(64))

    def test_quaternion_matches_row_vector_y_rotation(self):
        h = math.sqrt(.5)
        p = parse_layout(fixture(quaternion=(0., h, 0., h))).pieces[0]
        self.assertAlmostEqual(p.transform[2][0], 1., places=5)
        self.assertAlmostEqual(p.transform[0][2], -1., places=5)

    def test_rejects_unsupported_truncated_and_invalid_transforms(self):
        for raw in (fixture(version=99), fixture()[:40], fixture()[:-64],
                    fixture(quaternion=(0., 0., 0., 0.)), fixture(quaternion=(math.nan, 0., 0., 1.))):
            with self.subTest(size=len(raw)):
                with self.assertRaises(ValueError):
                    parse_layout(raw)

    def test_rejects_index_mismatch_and_invalid_capacity(self):
        raw = bytearray(fixture())
        raw[224:232] = struct.pack('>Q', 55)
        with self.assertRaises(ValueError):
            parse_layout(bytes(raw))
        raw = bytearray(fixture())
        struct.pack_into('>I', raw, 4, 9)
        with self.assertRaises(ValueError):
            parse_layout(bytes(raw))
