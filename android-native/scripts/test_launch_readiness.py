import importlib.util
from pathlib import Path
import sys
import unittest

spec = importlib.util.spec_from_file_location("android_readiness", Path(__file__).with_name("check-launch-readiness.py"))
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)

class PermissionTests(unittest.TestCase):
    def setUp(self):
        self.manifest = module.read("app/src/main/AndroidManifest.xml")
        self.screen = module.read("app/src/main/java/com/nugulmap/nativeapp/ui/map/MapScreen.kt")
        self.provider = module.read("app/src/main/java/com/nugulmap/nativeapp/ui/map/CurrentLocationProvider.kt")

    def test_optional_foreground_location_has_runtime_gate_and_manual_search(self):
        self.assertEqual(set(), module.unexpected_permissions(self.manifest, self.screen, self.provider))

    def test_missing_runtime_gate_rejects_location(self):
        self.assertIn("android.permission.ACCESS_FINE_LOCATION", module.unexpected_permissions(self.manifest, "", self.provider))

    def test_background_location_remains_forbidden(self):
        manifest = self.manifest + '<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />'
        self.assertEqual({"android.permission.ACCESS_BACKGROUND_LOCATION"}, module.unexpected_permissions(manifest, self.screen, self.provider))

if __name__ == "__main__":
    unittest.main()
