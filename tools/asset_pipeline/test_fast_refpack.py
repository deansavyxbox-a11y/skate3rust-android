import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

class NativeRefPackTests(unittest.TestCase):
    def test_configured_native_library_matches_python_and_rejects_bad_streams(self):
        root=Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory() as temporary:
            library=Path(temporary)/"refpack.dll"
            build=subprocess.run(["rustc","--edition=2024","--crate-type=cdylib","-O",str(root/"tools/asset_pipeline/refpack_native.rs"),"-o",str(library)],capture_output=True,text=True)
            self.assertEqual(build.returncode,0,build.stderr)
            code="""
import sys
sys.path.insert(0,sys.argv[1])
from tools.asset_pipeline import fast_refpack
from tools.owned_game.refpack import decompress
vectors=[(bytes([0xff])+b'abc',3),(bytes([0xe0])+b'ABCD'+bytes([4,3,0xfc]),8),(bytes([1,0,65,0xfc]),4)]
for data,size in vectors:
    native=fast_refpack.decode(data,size,0)
    assert native is not None,'Android library override was ignored'
    original=fast_refpack._library
    fast_refpack._library=None
    reference=decompress(data,size)
    fast_refpack._library=original
    assert native==reference,(native,reference)
for data,size in [(bytes([0,0]),3),(bytes([0xe0,1]),4),(bytes([0xff])+b'abc',2)]:
    try:fast_refpack.decode(data,size,0)
    except ValueError:pass
    else:raise AssertionError('Invalid stream accepted')
"""
            env=os.environ.copy();env["SKATE_REFPACK_LIBRARY"]=str(library)
            result=subprocess.run([sys.executable,"-I","-c",code,str(root)],env=env,capture_output=True,text=True)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)

if __name__=="__main__":unittest.main()
