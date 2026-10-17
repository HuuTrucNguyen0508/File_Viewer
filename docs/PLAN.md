# File Viewer: plan

## Goal

User's words: "a phone app that allows me to view absolutely every type of file. I don't have the need to be able to edit all but syntax highlight will be nice."

Android only (user's choice). Personal app, sideloaded, not on the Play Store. Read-only.

"Every type of file" is met two ways: a rich viewer for each common family, and a universal fallback so that **no file ever fails to open**. Unknown binary falls back to a hex view plus file info; unknown text falls back to the plain text view.

## Stack

- Kotlin, Jetpack Compose, Material 3. Single `:app` module.
- Build template copied from `~/Documents/Code/Media Compressor/android`: Gradle 9.4.1 wrapper, AGP 9.2.1, Kotlin compose plugin 2.4.10, compose-bom 2026.06.01, compileSdk/targetSdk 36, JDK 17 (`JAVA_HOME=~/.jdks/temurin-17`).
- minSdk 30 (HEIF decode, scoped storage behavior, Media3).
- Package `com.fileviewer.app`. App name "File Viewer".

## Entry points

1. **Open with / Share**: intent filters for `ACTION_VIEW` and `ACTION_SEND` with `*/*` on `content:` and `file:` schemes, so the app appears for every file in every file manager, mail app and browser.
2. **In-app browser**: `MANAGE_EXTERNAL_STORAGE` ("All files access"), acceptable because this is a personal sideloaded app. Folder list, breadcrumbs, sort (name/size/date), show hidden toggle, recent files. If the permission is denied, fall back to the SAF picker (`ACTION_OPEN_DOCUMENT`).

Intent filters (review finding 3): `ACTION_VIEW` filters on `content:`/`file:` with `*/*`; **separate MIME-only** `ACTION_SEND` and `ACTION_SEND_MULTIPLE` filters (no scheme), reading `EXTRA_STREAM` and `ClipData`. Handle `onNewIntent`. Temporary URI grants are not persisted; recents only store paths from the in-app browser, or URIs where `takePersistableUriPermission` succeeded. SAF stays available from the browser even with all-files access (cloud, USB providers).

All viewers consume one abstraction, never a raw `File`:

```kotlin
interface FileSource {
    val name: String
    val size: Long?          // null when the provider doesn't report it
    val mimeHint: String?    // from ContentResolver or intent
    fun openStream(): InputStream          // caller closes
    fun seekableOrNull(): SeekableSource?   // cheap: real file path, or a seekable PFD (pipe-backed PFDs are NOT seekable)
}

// Background, cancellable, progress-reporting copy into the app cache, for stream-only
// sources and APIs needing a real fd/path (PdfRenderer, SQLite, Typeface, Hex on a pipe).
// Bounded by Limits.maxMaterializeBytes; partial files are deleted on cancel/failure.
// One cache dir per opened source, generated file names (never entry names), cleared on exit/LRU.
suspend fun FileSource.materialize(progress: (Long) -> Unit): SeekableSource
```

Implementations: `LocalFileSource` (path), `ContentUriSource` (content:// via ContentResolver; respects AssetFileDescriptor start offset/length), `ArchiveEntrySource` (entry inside an archive; materialized lazily). Every viewer that needs seeking (Hex, PDF, SQLite, ZIP central directory) shows a progress bar while a stream-only source materializes.

### Resource limits (review finding 4)

One `Limits` object in `source/`, used everywhere: max bytes read for text (by bytes actually read, not reported size), max line length before soft-breaking for display, max decompressed bytes and entry count per archive, max archive nesting depth, max cache size, max image decode dimensions. One `SafeWebView` factory (JS off, no network, no file access beyond the given content, no content-provider access) and one `SafeXml` factory (external entities and DTDs disabled) shared by every viewer.

## Detection

`FileTypeDetector.detect(source): FileKind`, in order:
1. Magic bytes (first 4 KB): PDF, PNG, JPEG, GIF, WebP, HEIF/AVIF (ftyp brands), MP4/MOV (ftyp), Matroska/WebM, OGG, FLAC, RIFF (WAV/AVI), MP3 (ID3/frame sync), ZIP (then inspect entries: OOXML docx/xlsx/pptx, ODF via `mimetype` entry, EPUB, APK/JAR), GZIP, BZ2, XZ, 7z, RAR, TAR (`ustar` at 257), SQLite, ELF, PE, Mach-O, class, TTF/OTF/WOFF, PSD, TIFF, BMP, ICO.
2. Extension map (large table, including hundreds of source-code extensions and dotfiles like `Makefile`, `Dockerfile`, `.bashrc`).
3. MIME hint.
4. Content sniff (review finding 5): decode a BOM first (UTF-8/16/32), then test decoded characters; heuristically detect BOM-less UTF-16 (alternating NULs); tolerate a sample truncated mid UTF-8 sequence; then valid UTF-8 with few control chars → text; otherwise binary.

ZIP subtype inspection reads at most a bounded number of entries and is deferred (kind = generic ZIP) when it would require materializing a stream-only source. **Every file, whatever its kind, can be forced into Text (with encoding picker), Hex and Info** so a misdetected file is never trapped.

`FileKind` is a sealed type; each kind maps to a primary viewer plus alternate viewers. Every file also offers **Hex** and **Info** as alternates, and every text-renderable kind (Markdown, HTML, SVG, JSON, CSV, XML) offers **Source** with syntax highlighting.

## Viewers

Phase 1 (the core app, one owner):

| Viewer | Covers | Implementation |
|---|---|---|
| Code/Text | All source code, config, logs, plain text, any unknown text | **Sora Editor** (`io.github.rosemoe:editor` + `language-textmate`) in read-only mode, bundled TextMate grammars (VS Code, MIT) for ~50 popular languages, light/dark themes. Line numbers, word wrap toggle, find, pinch zoom, go-to-line, copy. Encoding auto-detect (BOM, UTF-8 validity, fallback ISO-8859-1) with manual override. Files over ~5 MB: highlighting off; over ~50 MB: open in hex-style paged text mode instead of loading whole file |
| Image | JPEG, PNG, GIF (animated), WebP (animated), HEIC/HEIF, AVIF, BMP, ICO, SVG | Coil 3 (+ `coil-svg`, `coil-gif`), zoom/pan via `me.saket.telephoto:zoomable-image-coil3`. Swipe between images in the same folder |
| PDF | PDF | `android.graphics.pdf.PdfRenderer`, lazy page list rendered at screen width, zoom, page indicator, jump to page. Password-protected PDFs show a clear message |
| Media | MP4, MKV, WebM, MOV, 3GP, MP3, M4A, AAC, FLAC, OGG/Opus, WAV | Media3 ExoPlayer + `media3-ui`. Audio shows embedded cover art and tags |
| Archive | ZIP, JAR, APK (as archive), TAR, GZ, BZ2, XZ, 7z, TGZ | `java.util.zip` + Apache Commons Compress (+ `xz` for XZ/7z). Browse entries as a folder tree; tap an entry to open it in the matching viewer (extract that entry to cache). RAR: listing via Commons Compress is unsupported; show Info + Hex |
| Hex | Everything | Virtualized `LazyColumn`, 16 bytes per row, offset / hex / ASCII columns, reads 64 KB pages via random access, never loads whole file. Go-to-offset |
| Info | Everything | Name, size, detected kind, MIME, path/URI, modified date, MD5/SHA-1/SHA-256 on demand, EXIF for images, media metadata for audio/video |

Phase 2 (parallel owners, separate packages):

- **Data**: Markdown rendered (CommonMark → HTML in WebView, bundled offline CSS), HTML rendered in a sandboxed WebView (JS off by default, no network), CSV/TSV as a scrollable table, JSON as a collapsible tree, SQLite browser (tables → paged rows), XML/YAML via Code viewer.
- **Documents**: DOCX, XLSX, PPTX, ODT/ODS/ODP, EPUB, RTF. Lightweight parsers of the zipped XML into HTML (text, headings, lists, tables, embedded images; sheets as tables; slides as text + images). No Apache POI (too heavy for Android). EPUB: chapter list + WebView reader.
- **Misc**: Fonts (TTF/OTF: sample text at several sizes, glyph grid), APK info (`PackageManager.getPackageArchiveInfo`: name, icon, version, permissions), certificates (PEM/DER/CRT parsed fields), TIFF/PSD/RAW via best effort thumbnail or Info+Hex.

### Support honesty (review finding 6)

- Media: the container is detected, but playback depends on device decoders. On a playback error, show the codec info and offer Info/Hex instead of a blank player.
- AVIF only decodes on Android 12+; on 11, show Info + Hex with a note.
- Known gaps shown as such, not as errors: RAR (Info + Hex), encrypted ZIP entries (listed, marked locked), legacy binary DOC/XLS/PPT (best-effort text extraction later; Info + Hex now). Office/EPUB output is labelled "Preview" (extracted content, not the original layout). RTF needs its own small parser.
- `docs/SUPPORT.md` lists each format as Full / Preview / Fallback.

## Build pins (review finding 2)

Sora 0.24.7 and Coil 3.6.3 require compileSdk 37; android-37.0 is installed. Prefer compileSdk 37 (targetSdk 36) with current versions if AGP 9.2.1 accepts it; otherwise pin Sora 0.24.6 and a Coil version compatible with telephoto 0.19.0 and compileSdk 36. Pin every version explicitly and compile the full graph before writing features.

## UI shape

- Browser screen → Viewer screen. The Viewer screen has a top bar with file name, a viewer switcher (chips: e.g. "Rendered · Source · Hex · Info"), and an overflow menu (share, open with another app, copy path).
- Light/dark following the system. The implementer runs `/frontend-design` before writing any UI.

## Package layout

```
app/src/main/java/com/fileviewer/app/
  MainActivity.kt          intent handling, navigation
  source/                  FileSource + implementations
  detect/                  FileKind, FileTypeDetector, extension/magic tables
  browser/                 file browser screen, permission flow, recents
  viewer/                  ViewerScreen, viewer switcher, ViewerRegistry
  viewers/code/            viewers/image/  viewers/pdf/  viewers/media/
  viewers/archive/         viewers/hex/    viewers/info/
  viewers/data/            viewers/documents/  viewers/misc/   (phase 2)
  ui/theme/
```

`ViewerRegistry` (one file) maps `FileKind` → ordered list of viewers.

Phase 1 also freezes the shared contracts phase 2 depends on (review finding 7): `FileSource` + materialization, `Limits`, archive entry access, `ViewerRegistry`/navigation, `SafeWebView`, `SafeXml`, and a shared offline HTML renderer (`viewer/html/`: wraps an HTML fragment in bundled CSS for light/dark, rendered in `SafeWebView`). The integrator solely owns `FileKind`, detection tables, Gradle files, the manifest, shared resources and registry wiring. Phase 2 owners write only inside their own `viewers/<area>/` package and hand back registry/detection/dependency changes as a list for the integrator to apply.

## Verification

- Unit tests (JVM): `FileTypeDetector` on a fixture set (one small file per family, including extensionless and misnamed files), hex paging math, archive listing.
- `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` passes.
- On the emulator: push a fixture folder to `/sdcard/Download/fv-fixtures/`, open each file through the in-app browser and through `adb shell am start -a android.intent.action.VIEW -d <content uri>`, screenshot each viewer. A 200 MB random binary opens in Hex without OOM.
