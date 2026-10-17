#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
probe_build="$PWD/build/device-probe"
sdk="$HOME/Android/Sdk"
build_tools="$sdk/build-tools/37.0.0"
jdk="$HOME/.jdks/temurin-17"
export JAVA_HOME="$jdk"
export PATH="$jdk/bin:$PATH"
mkdir -p "$probe_build/classes" "$probe_build/dex"
"$build_tools/aapt2" link -o "$probe_build/probe.apk" -I "$sdk/platforms/android-37.0/android.jar" --manifest tools/verification/probe-manifest.xml
"$jdk/bin/javac" -source 8 -target 8 -cp "$sdk/platforms/android-37.0/android.jar" -d "$probe_build/classes" tools/verification/DeviceProbe.java
"$jdk/bin/jar" cf "$probe_build/classes.jar" -C "$probe_build/classes" .
JAVA_HOME="$jdk" "$build_tools/d8" --min-api 30 --lib "$sdk/platforms/android-37.0/android.jar" --output "$probe_build/dex" "$probe_build/classes.jar"
python3 - "$probe_build" <<'PY'
import sys, zipfile
from pathlib import Path
root = Path(sys.argv[1])
with zipfile.ZipFile(root/'probe.apk', 'a') as z:
    z.write(root/'dex/classes.dex', 'classes.dex')
PY
JAVA_HOME="$jdk" "$build_tools/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android "$probe_build/probe.apk"
"$sdk/platform-tools/adb" -s emulator-5554 install -r "$probe_build/probe.apk"
for fixture in /tmp/fv-verify/fixtures/*; do
    [ "$(basename "$fixture")" = "random-200mb.bin" ] && continue
    "$sdk/platform-tools/adb" -s emulator-5554 shell run-as com.fileviewer.fixture mkdir -p files
    "$sdk/platform-tools/adb" -s emulator-5554 shell -T "run-as com.fileviewer.fixture sh -c \"cat > files/$(basename "$fixture")\"" < "$fixture"
done
