#!/usr/bin/env python3
"""Inspect shipped 64-bit ELF LOAD alignment and RELRO without executing libraries.

This proves binary layout only, not a successful run on a 16 KB Android device.
APK ZIP alignment must additionally pass zipalign -c -P 16 4.
"""
import argparse
from pathlib import Path
import struct
import zipfile


def inspect_elf(data: bytes) -> tuple[int, list[int], bool]:
    if data[:4] != b"\x7fELF" or len(data) < 64:
        raise ValueError("missing/truncated ELF header")
    bits = {1: 32, 2: 64}.get(data[4])
    endian = {1: "<", 2: ">"}.get(data[5])
    if bits is None or endian is None:
        raise ValueError("unsupported ELF format")
    if bits == 64:
        offset = struct.unpack_from(endian + "Q", data, 32)[0]
        entry_size, count = struct.unpack_from(endian + "HH", data, 54)
        fmt, alignment_index = endian + "IIQQQQQQ", 7
    else:
        offset = struct.unpack_from(endian + "I", data, 28)[0]
        entry_size, count = struct.unpack_from(endian + "HH", data, 42)
        fmt, alignment_index = endian + "IIIIIIII", 7
    if not count or entry_size < struct.calcsize(fmt) or offset + count * entry_size > len(data):
        raise ValueError("missing/truncated program headers")
    aligns, relro = [], False
    for index in range(count):
        segment = struct.unpack_from(fmt, data, offset + index * entry_size)
        if segment[0] == 1:
            aligns.append(segment[alignment_index])
        if segment[0] == 0x6474E552:
            relro = True
    if not aligns:
        raise ValueError("no LOAD segments")
    return bits, aligns, relro


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("archive", type=Path)
    args = parser.parse_args()
    failed, checked = 0, 0
    with zipfile.ZipFile(args.archive) as archive:
        for name in archive.namelist():
            if not name.endswith(".so"):
                continue
            try:
                bits, aligns, relro = inspect_elf(archive.read(name))
                valid = (bits != 64 or min(aligns) >= 16384) and relro
                checked += 1
                print(f"{'PASS' if valid else 'FAIL'} {name}: ELF{bits}, LOAD min={min(aligns)}, RELRO={relro}")
                failed += not valid
            except (ValueError, struct.error) as error:
                print(f"FAIL {name}: {error}")
                failed += 1
    print(f"Native binary layout: {checked} libraries, {failed} failures; device runtime is a separate check")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
