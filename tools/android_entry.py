"""Small Chaquopy bridge into the existing owned-disc conversion modules."""
from pathlib import Path
import os
import sys


def convert_owned(game_root, staging, work, callbacks, source_root):
    bundle = Path(source_root)
    tools = bundle / "tools"
    if str(bundle) not in sys.path:
        sys.path.insert(0, str(bundle))
    if str(tools) not in sys.path:
        sys.path.insert(0, str(tools))
    from tools.asset_pipeline import fast_refpack
    if os.environ.get("SKATE_REFPACK_LIBRARY") and fast_refpack._library is None:
        raise RuntimeError("The Android native decompression runtime is unavailable")
    from tools.asset_pipeline.android_install import convert_owned as convert
    return convert(Path(game_root), Path(staging), Path(work), callbacks.report,
                   callbacks.checkCancelled)
