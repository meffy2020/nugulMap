import importlib.util
from pathlib import Path
import struct
import unittest

spec = importlib.util.spec_from_file_location("native_alignment", Path(__file__).with_name("verify-native-page-alignment.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class NativeAlignmentTests(unittest.TestCase):
    def elf(self, alignment, relro=True):
        data = bytearray(64 + 56 * (2 if relro else 1))
        data[:6] = b"\x7fELF\x02\x01"
        struct.pack_into("<Q", data, 32, 64)
        struct.pack_into("<HH", data, 54, 56, 2 if relro else 1)
        struct.pack_into("<IIQQQQQQ", data, 64, 1, 0, 0, 0, 0, 0, 0, alignment)
        if relro:
            struct.pack_into("<IIQQQQQQ", data, 120, 0x6474E552, 0, 0, 0, 0, 0, 0, alignment)
        return bytes(data)

    def test_reads_16kb_segments_and_relro(self):
        self.assertEqual((64, [16384], True), module.inspect_elf(self.elf(16384)))

    def test_exposes_unaligned_or_missing_relro(self):
        self.assertEqual((64, [4096], False), module.inspect_elf(self.elf(4096, False)))

    def test_truncated_elf_cannot_pass(self):
        with self.assertRaises(ValueError):
            module.inspect_elf(self.elf(16384)[:80])

if __name__ == "__main__":
    unittest.main()
