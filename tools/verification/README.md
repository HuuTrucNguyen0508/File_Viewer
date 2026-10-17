# Emulator verification tools

Run these from the project root using bash. They create fixtures and screenshots under `/tmp/fv-verify` and build an independent fixture sender under the ignored `build/device-probe` directory. They do not change the release app's dependencies.

Prerequisites are Python with Pillow, ffmpeg, ImageMagick with HEIC support, 7z, Android SDK 37.0, and the existing Android debug signing key. The RAR fixture downloads the [libarchive test fixture](https://github.com/libarchive/libarchive/blob/master/libarchive/test/test_read_format_rar.rar.uu). Other fixtures are generated locally.

```bash
python3 tools/verification/make_fixtures.py
~/Android/Sdk/platform-tools/adb -s emulator-5554 push /tmp/fv-verify/fixtures/. /sdcard/Download/fv-fixtures/
bash tools/verification/build_probe.sh
```

`build_probe.sh` installs a debug-only APK with a private content provider and a normal sender Activity. It copies fixture files into the sender's private storage. Temporary URI grants, rather than All files access, make these files readable by File Viewer.

```bash
~/Android/Sdk/platform-tools/adb -s emulator-5554 shell am start -W -n 'com.fileviewer.fixture/.DeviceProbe$Sender' --es mode send --es files sample.kt --es delivery new
~/Android/Sdk/platform-tools/adb -s emulator-5554 shell am start -W -n 'com.fileviewer.fixture/.DeviceProbe$Sender' --es mode clip --es files sample.py --es delivery new
~/Android/Sdk/platform-tools/adb -s emulator-5554 shell am start -W -n 'com.fileviewer.fixture/.DeviceProbe$Sender' --es mode multiple --es files sample.kt,sample.py --es delivery new
~/Android/Sdk/platform-tools/adb -s emulator-5554 shell am start -W -n 'com.fileviewer.fixture/.DeviceProbe$Sender' --es mode send --es files window.pdf
```

`window.pdf` contains a PDF between a 512-byte prefix and suffix. The provider exposes only the PDF window through an `AssetFileDescriptor`.

The instrumentation entry point injects a two-finger pinch into the foreground app. Use it on local-file viewers. Starting instrumentation force-stops the fixture provider and revokes its temporary grants.

```bash
~/Android/Sdk/platform-tools/adb -s emulator-5554 shell am instrument -w -e mode pinch -e factor 2 com.fileviewer.fixture/.DeviceProbe
```

`emulator.py` provides `open_file`, `shot`, `tap`, `fill`, `back`, and `pinch`. `shot` saves both a PNG and the native accessibility snapshot. The checked-in defaults name this review's T3 device session. For a new session, call T3 `device_open` and set `FV_DEVICE_FLAGS` to its returned flags before importing the module. The native Android 11 picker needed D-pad center, keycode 23, to activate a focused file tile. A selected tile alone does not prove the file opened.


## Phase 2

The additional generator needs Python Pillow, fontTools with WOFF2 support, the bundled IBM Plex font, and `/usr/share/fonts/OTF/PowerlineSymbols.otf` for the OTF fixture. It builds small Office and OpenDocument packages, an EPUB, data files, and database sidecars locally. The legacy Office files are synthetic OLE-signature fixtures for best-effort text extraction. The APK comes from the emulator's installed Settings app. Certificate fixtures come from the committed test resources.

```bash
python3 tools/verification/phase2_fixtures.py
~/Android/Sdk/platform-tools/adb -s emulator-5554 push /tmp/fv-verify/fixtures/. /sdcard/Download/fv-fixtures/
bash tools/verification/build_probe.sh
```

Set `FV_DEVICE_FLAGS` to the flags returned by T3 `device_open`. Run each command sequentially. The security instrumentation stops the target app when it finishes, so it must finish before interactive verification starts.

```bash
~/Android/Sdk/platform-tools/adb -s emulator-5554 shell am instrument -w com.fileviewer.fixture/.DeviceSecurity
python3 tools/verification/phase2_verify.py gallery
python3 tools/verification/phase2_verify.py interactions
python3 tools/verification/phase2_verify.py dark
python3 tools/verification/phase2_verify.py font
python3 tools/verification/phase2_verify.py uri
python3 tools/verification/phase2_verify.py extras
python3 tools/verification/phase2_verify.py rotations
```

`DeviceSecurity` runs against the app's actual Android classes. It checks ordinary XML, internal and external DOCTYPE rejection, WebView settings, unchanged WAL database and sidecar hashes, and refusal of a sparse database larger than the private-copy limit. Its temporary databases are removed in `finally`.

The UI driver saves `phase2-final-*.png` files and matching accessibility snapshots under `/tmp/fv-verify`. Inspect the images as well as the snapshots. Android's accessibility tree sometimes omits a visible workbook tab while exposing the WebView, so the driver uses its screenshot-confirmed location on the 1080 by 1920 emulator for that tab. Scroll coordinates assume the same screen size. `dark` and `font` restore this review's original light mode and automatic portrait rotation. The font check restores the previous IME and hardware-keyboard setting. It switches to the emulator's stock Gboard for the visible-keyboard capture because agent-device text entry temporarily uses its own IME. `uri` denies the UID's All files access and restores the original allowed UID mode after sharing private provider files. The oversized JSON fixture can make the accessibility helper refuse its tree; `extras` preserves that diagnostic and captures the screen instead.
