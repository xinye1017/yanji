#!/usr/bin/env bash
# Verify the assembled APK satisfies Android 15+ / Google Play 16KiB page-size
# compatibility: every packaged .so must (a) be ELF LOAD-segment aligned to
# >= 16384 and (b) start at a ZIP offset aligned to 16384 (zipalign -P 16).
#
# This guards the two independent failure modes behind the Play "16KB alignment"
# warning: a .so linked with a 4KB max-page-size (fixed by passing
# -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON to CMake on NDK r27+) and a correctly
# linked .so that AGP fails to zipalign at 16KB.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

APK="${YANJI_APK:-app/build/outputs/apk/debug/app-debug.apk}"
if [ ! -f "$APK" ]; then
  echo "APK not found: $APK (assemble it first)." >&2
  exit 1
fi

python3 - "$APK" <<'PY'
import struct
import sys
import zipfile

APK = sys.argv[1]
PAGE = 16384
EXPECTED_ALIGN = {16384}  # every LOAD segment must align to 16KiB exactly

def elf_load_aligns(data: bytes):
    if data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    is64 = data[4] == 2
    if is64:
        phoff = struct.unpack_from("<Q", data, 0x20)[0]
        phentsize = struct.unpack_from("<H", data, 0x36)[0]
        phnum = struct.unpack_from("<H", data, 0x38)[0]
        align_off, align_fmt = 0x30, "<Q"
    else:
        phoff = struct.unpack_from("<I", data, 0x1C)[0]
        phentsize = struct.unpack_from("<H", data, 0x2A)[0]
        phnum = struct.unpack_from("<H", data, 0x2C)[0]
        align_off, align_fmt = 0x1C, "<I"
    aligns = set()
    for i in range(phnum):
        o = phoff + i * phentsize
        if struct.unpack_from("<I", data, o)[0] == 1:  # PT_LOAD
            aligns.add(struct.unpack_from(align_fmt, data, o + align_off)[0])
    return aligns

def data_offset(info: zipfile.ZipInfo) -> int:
    # Local file header: 30-byte fixed part, then name + extra fields.
    with open(APK, "rb") as fh:
        fh.seek(info.header_offset)
        header = fh.read(30)
        if header[:4] != b"PK\x03\x04":
            raise ValueError(f"bad local header for {info.filename}")
        name_len = struct.unpack_from("<H", header, 26)[0]
        extra_len = struct.unpack_from("<H", header, 28)[0]
    return info.header_offset + 30 + name_len + extra_len

problems = []
with zipfile.ZipFile(APK) as z:
    sos = [i for i in z.infolist() if i.filename.endswith(".so")]
    if not sos:
        problems.append("APK contains no .so files")
    for info in sos:
        aligns = elf_load_aligns(z.read(info))
        if aligns != EXPECTED_ALIGN:
            problems.append(
                f"{info.filename}: ELF LOAD p_align={sorted(aligns)}, expected {sorted(EXPECTED_ALIGN)}"
            )
        offset = data_offset(info)
        if offset % PAGE != 0:
            problems.append(
                f"{info.filename}: ZIP data offset {offset} is not {PAGE}-aligned (remainder {offset % PAGE})"
            )

if problems:
    print("16KiB alignment check FAILED:", file=sys.stderr)
    for p in problems:
        print(f"  - {p}", file=sys.stderr)
    sys.exit(1)

print(f"16KiB alignment guard: PASS ({len(sos)} .so files, ELF p_align=16384, ZIP offset aligned to 16384).")
PY
