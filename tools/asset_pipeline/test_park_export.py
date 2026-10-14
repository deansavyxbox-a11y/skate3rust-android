import tempfile
import unittest
from pathlib import Path
import numpy as np

from .park_export import full_selection, transform_segment, publish_package, load_selected_layout, Selection
from .test_park_layout import fixture


def world(key, layout):
    return dict(class_='world', key=key, parent='', fields={
        'WorldStream':dict(data='DIST_MegaPark'),
        'Hash_79B248E8469DC5DA':dict(data=layout),
        'Hash_7DFD997E3F13540A':dict(data='00000000000000630000000000000000'),
        'Hash_3735C5C12E8E7AE1':dict(data='start')})


class ParkExportTests(unittest.TestCase):
    def test_legacy_embedded_identifier_does_not_override_database_file_reference(self):
        from types import SimpleNamespace
        entry=SimpleNamespace(path='data/skatepark/Starter_Fullpark.park')
        archive=SimpleNamespace(entries=[entry],read=lambda e: fixture())
        selected=Selection(entry.path,'DIST_StartPark',77,'start')
        raw,layout=load_selected_layout(archive,selected)
        self.assertEqual(raw,fixture())
        self.assertEqual(layout.world_id,99)
        with self.assertRaises(ValueError):
            load_selected_layout(archive,Selection('data/skatepark/Other.park','DIST_StartPark',77,'start'))

    def test_chooses_the_full_world_reference_not_base_or_tutorial(self):
        rows = [world('dist_megapark','skatepark\\MegaPark_Empty.park'),
                world('dist_megapark_full','skatepark\\MegaPark_Fullpark.park')]
        for r in rows: r['class'] = r.pop('class_')
        selected = full_selection({'collections':rows}, 'MegaPark')
        self.assertEqual(selected.member,'data/skatepark/MegaPark_Fullpark.park')
        self.assertEqual(selected.world_id,99)
        with self.assertRaises(ValueError):
            full_selection({'collections':rows[:1]}, 'MegaPark')

    def test_spline_rotation_transforms_derivatives_and_bounds(self):
        data=np.zeros(30,dtype='>f4');data[8:11]=(0,0,4);data[12:15]=(0,0,1)
        data[20:23]=(0,0,1);data[24:27]=(0,0,5)
        t=np.array(((0,0,-1,0),(0,1,0,0),(1,0,0,0),(10,2,3,1)),dtype=float)
        got=np.frombuffer(bytes.fromhex(transform_segment(data.tobytes().hex(),t)),dtype='>f4')
        np.testing.assert_allclose(got[8:11],(4,0,0))
        np.testing.assert_allclose(got[12:15],(11,2,3))
        np.testing.assert_allclose(got[20:23],(11,2,3))
        np.testing.assert_allclose(got[24:27],(15,2,3))

    def test_failed_publication_keeps_previous_package(self):
        with tempfile.TemporaryDirectory() as tmp:
            target=Path(tmp)/'map.skate';target.write_bytes(b'previous')
            with self.assertRaises(FileNotFoundError):
                publish_package(Path(tmp)/'absent',target)
            self.assertEqual(target.read_bytes(),b'previous')
