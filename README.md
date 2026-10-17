# File Viewer

A read-only Android app that opens any file: code with syntax highlighting, images, PDFs, audio and video, archives, Office and OpenDocument files, EPUB, RTF, Markdown, HTML, CSV, JSON, SQLite, fonts, APKs and certificates. A file it doesn't recognize still opens as text, hex or file info.

What each format gets is listed in [docs/SUPPORT.md](docs/SUPPORT.md). The design is in [docs/PLAN.md](docs/PLAN.md).

## Download

Get the APK from the [latest release](https://github.com/HuuTrucNguyen0508/File_Viewer/releases/latest), copy it to an Android 11+ phone and open it.

## Build

Requires JDK 17 and the Android SDK (compileSdk 37).

```bash
export JAVA_HOME=$HOME/.jdks/temurin-17
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Install on a phone

1. On the phone, enable **Developer options → USB debugging** and connect it by USB.
2. Run `~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk`.

Alternatively, copy the APK to the phone and open it, allowing installs from that app when Android asks.

On first launch the in-app browser asks for **All files access**. Without it, you can still open files through the system file picker. The app also appears in **Open with** and **Share** for every file type.

Requires Android 11 or later.
