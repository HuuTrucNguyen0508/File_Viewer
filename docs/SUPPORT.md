# Format support

Every file opens. Each format is one of:

- **Full**: a dedicated viewer shows the content as intended.
- **Preview**: you see the content, but not exactly as the original app shows it.
- **Fallback**: no dedicated viewer yet. The file opens in Info and Hex, and can be forced into Text.
- **Planned**: a phase-2 viewer will cover it. Until then it behaves as noted.

Any file can be switched to **Text** (with an encoding picker), **Hex**, and **Info** from the viewer chips, so a misdetected file is never stuck.

## Text and code

| Format | Support | Notes |
|---|---|---|
| Source code (about 60 languages: Kotlin, Java, Python, JS/TS, C/C++, C#, Go, Rust, Swift, Dart, Ruby, PHP, shell, SQL, Lua, R, Julia, Groovy/Gradle, Zig, GraphQL, Protobuf, and more) | Full | Syntax highlighting, light and dark themes, line numbers, find, go to line, word wrap, pinch zoom, copy |
| Config files (JSON, YAML, TOML, INI, XML, .env, Dockerfile, Makefile, dotfiles) | Full | Highlighted by extension or file name |
| Plain text, logs, files with no extension | Full | Content sniffing detects text; BOMs and UTF-16 without a BOM are recognized |
| Text over 5 MB | Full | Shown without highlighting |
| Text over 20 MB | Preview | Paged plain-text mode: scrolls through any size, no find or highlighting |
| Languages without a bundled grammar (Scala, Haskell, Elixir, Nix, Terraform...) | Full | Plain text; pick a similar grammar from the syntax menu |
| Markdown, HTML | Full (source) | Rendered view **planned** |
| JSON | Full (source) | Collapsible tree **planned** |
| CSV, TSV | Full (source) | Table view **planned** |
| RTF | Preview | Raw RTF source; a rendered view is **planned** |

## Images

| Format | Support | Notes |
|---|---|---|
| JPEG, PNG, WebP, GIF, BMP, ICO | Full | Zoom and pan; animated GIF and WebP play; swipe between images in the same folder |
| HEIC / HEIF | Full | Decoded by the platform |
| AVIF | Full on Android 12+ | Fallback on Android 11, with a note |
| SVG | Full | Rendered image plus XML source |
| TIFF, PSD, camera RAW (DNG) | Fallback | Best-effort thumbnail **planned** |

## Documents

| Format | Support | Notes |
|---|---|---|
| PDF | Full | Page list, pinch zoom with sharp re-rendering, page indicator, jump to page |
| Password-protected PDF | Fallback | Shows a clear message |
| DOCX, XLSX, PPTX, ODT, ODS, ODP | Preview | Browse the files inside the package; a document preview is **planned** |
| EPUB | Preview | Browse the files inside; a reader is **planned** |
| DOC, XLS, PPT (legacy binary) | Fallback | Text extraction **planned** |

## Audio and video

Playback uses ExoPlayer and the phone's own decoders. The container is always detected; whether it plays depends on the device. When playback fails, the viewer shows the error and the tracks the platform found, with Info and Hex one tap away.

| Format | Support |
|---|---|
| MP4, MOV, MKV, WebM, 3GP, MPEG-TS, FLV | Full (if the device decodes the codecs) |
| MP3, M4A, AAC, FLAC, Ogg Vorbis, Opus, WAV, AMR | Full |
| AVI, MPEG-PS, WMV/WMA | Best effort; often Fallback |
| MIDI | Fallback |

## Archives

| Format | Support | Notes |
|---|---|---|
| ZIP, JAR, AAR, APK (as archive), WHL, CBZ | Full | Folder tree; tap an entry to open it in its viewer |
| TAR, TAR.GZ / TGZ, TAR.BZ2, TAR.XZ | Full | |
| GZ, BZ2, XZ holding a single file | Full | Shown as one entry |
| 7z | Full | Stream-only sources are copied to the cache first |
| ar / DEB | Full | Lists the outer members |
| Encrypted ZIP or 7z entries | Fallback | Listed with a lock; can't be opened |
| RAR / CBR | Fallback | No RAR reader yet; Info and Hex |
| Archives nested more than 4 levels deep | Fallback | Opens as raw bytes only |
| Zstandard (.zst) | Fallback | |

## Everything else

| Format | Support | Notes |
|---|---|---|
| SQLite databases | Fallback | Table browser **planned** |
| Fonts (TTF, OTF, WOFF, WOFF2) | Fallback | Sample text and glyph grid **planned** |
| APK details (name, icon, permissions) | Fallback | APK info **planned**; contents browsable now |
| Certificates (PEM, CRT) | Full (as text) | Parsed fields **planned** |
| Executables (ELF, PE, Mach-O), class, dex, wasm, disk images | Fallback | Identified by signature in Info |
| Unknown binary | Fallback | Hex view handles files of any size without loading them into memory |
