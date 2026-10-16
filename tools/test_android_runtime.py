"""Verify the generated converter bundle without importing the source checkout."""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

class AndroidRuntimeBundleTests(unittest.TestCase):
    def test_isolated_bundle_reaches_owned_archive_parser(self):
        bundle=Path(__file__).resolve().parents[1]/"android/app/build/generated/python-assets/android-python"
        self.assertTrue((bundle/"android_entry.py").is_file(), "Stage the Android Python sources first")
        with tempfile.TemporaryDirectory() as temporary:
            root=Path(temporary)
            game=root/"disc"
            for name in ["default.xex", "data/big/miscload.big", "data/big/miscboot.big", "data/big/db.big", "data/content/createacharacter.big", "data/content/worldDIST_University.big"]:
                p=game/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(b"fixture")
            code="""
import sys
sys.path.insert(0,sys.argv[1])
import android_entry
class Callbacks:
    def report(self,message): pass
    def checkCancelled(self): pass
try:
    android_entry.convert_owned(sys.argv[2],sys.argv[3],sys.argv[4],Callbacks(),sys.argv[1])
except Exception as error:
    assert type(error).__name__=='FormatError', repr(error)
    print('Reached owned BIG parser; invalid synthetic data rejected as expected')
else:
    raise AssertionError('Invalid synthetic game data must be rejected')
"""
            result=subprocess.run([sys.executable,"-I","-c",code,str(bundle),str(game),str(root/"stage"),str(root/"work")],capture_output=True,text=True,cwd=temporary)
            self.assertEqual(result.returncode,0,result.stdout+result.stderr)

if __name__=="__main__":unittest.main()
