# Format support

Every file opens. Each format is one of:

- **Full**: a dedicated viewer shows the content as intended.
- **Preview**: you see the content, but not exactly as the original app shows it.
- **Fallback**: no dedicated viewer yet. The file opens in Info and Hex, and can be forced into Text.
- **Planned**: a phase-2 viewer will cover it. Until then it behaves as noted.

Previews are bounded: a document stops after about 4 million characters of output across a whole document or workbook, any one part over 64 MB or 256 MB unpacked in total is refused, and embedded images over 32 MB are not shown. A single text block that exceeds the intermediate text limit is refused with Text available as a fallback.

Any file can be switched to **Text** (with an encoding picker), **Hex**, and **Info** from the viewer chips, so a misdetected file is never stuck.

## Text and code

| Format | Support | Notes |
|---|---|---|
| Source code (about 60 languages: Kotlin, Java, Python, JS/TS, C/C++, C#, Go, Rust, Swift, Dart, Ruby, PHP, shell, SQL, Lua, R, Julia, Groovy/Gradle, Zig, GraphQL, Protobuf, and more) | Full | Syntax highlighting, light and dark themes, line numbers, find, go to line, word wrap, pinch zoom, copy |
| Config files (YAML, TOML, INI, XML, .env, Dockerfile, Makefile, dotfiles) | Full | Highlighted by extension or file name |
| Plain text, logs, files with no extension | Full | Content sniffing detects text; BOMs and UTF-16 without a BOM are recognized |
| Text over 5 MB | Full | Shown without highlighting |
| Text over 20 MB | Preview | Paged plain-text mode: scrolls through any size, no find or highlighting |
| Languages without a bundled grammar (Scala, Haskell, Elixir, Nix, Terraform...) | Full | Plain text; pick a similar grammar from the syntax menu |
| Markdown (.md, .markdown, .mdx; README and CHANGELOG with no extension when they read as Markdown) | Full | Rendered with tables, task lists, strikethrough, bare links and fenced code; front matter shown as its own block. HTML written inside the file appears as text, links are shown but don't open, and images load from the file's own folder (or the same archive). Code shows the highlighted source |
| HTML, XHTML | Full | Shown in a sandbox: scripts off, nothing loads from the internet. Styles and images load only from the page's folder or below it, so pages that need scripts or remote files may look incomplete. Code shows the highlighted source |
| JSON, GeoJSON, JSON Lines (.jsonl, .ndjson) | Full | Collapsible tree with typed value colors and child counts; hold a row to copy its path or value. Invalid JSON shows the line and column of the first problem above the source. Stops drawing after 200,000 values, nesting past 200 levels is an error, files over 32 MB show the source instead |
| CSV, TSV, PSV | Full | Table with a fixed header row and row numbers, scrolling both ways; the delimiter (comma, tab, semicolon, pipe) is guessed and can be changed; quoted fields and line breaks inside cells work; first row as header can be switched off. Tap a row to read it in full. Stops at 50,000 rows, 200 columns and 2,000 characters per cell, with a note. Text shows the source |
| RTF | Preview | Paragraphs, bold/italic/underline, accented and Unicode characters, simple tables. Fonts, colors, pictures, headers and footnotes are skipped. Raw source under Text |

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
| DOCX, ODT | Preview | Paragraphs, headings, bold/italic/underline, lists, tables, embedded PNG/JPEG/GIF/WebP/SVG images; links shown as text. Headers, footers, comments and footnotes are left out; EMF/WMF drawings show a placeholder. Contents lists the files inside |
| XLSX, ODS | Preview | One tab per sheet; text, numbers, booleans, errors, dates, percentages and fixed decimals, with column letters and row numbers. Formulas show their last saved value; merged cells, charts and cell styling aren't shown. Each sheet stops at 2,000 rows and 100 columns, with a note |
| PPTX, ODP | Preview | Every slide in order as a card: title, text with bullets, tables, pictures. Slide layout, speaker notes, charts and SmartArt aren't shown |
| EPUB (2 and 3) | Preview | Chapter list from the book's table of contents, previous/next, images from the book. The book's own CSS, scripts and internal links are dropped |
| Password-protected DOCX, XLSX, PPTX | Fallback | Shows a clear message |
| DOC, XLS, PPT (legacy binary) | Fallback | Best-effort text: readable runs pulled from the file, labelled as such. Formatting, binary numbers and CJK text are not recovered |

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
| SQLite databases (.db, .sqlite, .sqlite3 and others, found by signature) | Full | Tables and views with row counts, the CREATE statements with highlighting, and rows 100 at a time. Opened read-only; databases in WAL mode, or with a `-wal` or `-journal` file beside them, are read from a private copy so the original is never touched, though changes still sitting in a `-journal` are not applied. Encrypted databases (SQLCipher) can't be opened and fall back to Info and Hex. BLOB cells show their size without loading their contents; text cells stop at 2,000 characters. A content URI exposes only the selected database file, so uncheckpointed changes in a separate WAL are unavailable unless the database is opened as a local file with its sidecars. The viewer states this when a WAL-mode file has no accessible WAL |
| Fonts (TTF, OTF, TTC first face) | Full | Name in its own face, sizes from 12 to 56, a field to try your own text (edits nothing on disk), glyph grid with code point and Unicode name |
| WOFF | Full if the device loads it | Android may refuse WOFF; the viewer then says so and offers Info and Hex |
| WOFF2 | Fallback | Android can't load it; Info and Hex, with a note |
| APK (also inside archives) | Full | Icon, name, package, version, min and target Android, permissions with sensitive ones flagged, signer certificates and SHA-256. Contents, Hex and Info stay one tap away. APK bundles (APKS, XAPK) open as archives |
| Certificates and keys (PEM, CRT, CER, DER, P7B) | Full | Subject, issuer, validity with an expired badge, serial, algorithms, key size, alternative names, key usage, SHA-1 and SHA-256 fingerprints; certificate chains list every certificate. Public keys show algorithm, size and fingerprint. Private keys are named and never displayed; Text still opens the raw file |
| PKCS#12 (.p12, .pfx), certificate requests (.csr) | Fallback | Password-protected or not parsed; Hex and Text |
| Executables (ELF, PE, Mach-O), class, dex, wasm, disk images | Fallback | Identified by signature in Info |
| Unknown binary | Fallback | Hex view handles files of any size without loading them into memory |

Archive folder trees hide entries whose paths exceed 4,096 characters or 128 components, or would exceed 200,000 folders and files. The archive shows the hidden-entry count; Hex and Info remain available.
Font metadata selects preferred name records before decoding and displays at most 4,096 characters per name, across at most eight displayed names.
ZIPs are preflighted before indexing: at most 100,000 actual central-directory records and 8 MiB of central metadata including headers, names, extras, and comments. Local-header extras have a separate 4 MiB budget counted per referencing entry, even when entries share a local header, and any one header may carry at most 64 extra fields, ignoring repeats in local headers, with 300,000 fields across the archive. Malformed preflight layouts are rejected at every size. Unicode name/comment recovery and ZIP64 entry streams remain supported. Detection falls back to plain ZIP on a limit, so Hex and Info remain reachable.
Markdown switches to source with a note above 360,000 parser-work units, 64 leading container levels, or 8,000,000 rendered HTML characters. Each line costs 4 units; a block-starting line adds 32; markup/autolink punctuation costs 1 per character. Recognized table bodies add 4 units per column per row, including CommonMark's pipe-free lazy continuation rows. A stray pipe elsewhere adds no table cost. Container nesting is counted only at the line prefix, including ordered list markers. There is no separate per-line token cap. Several-MB prose and ordinary long lines can render; parser and renderer failures also fall back to source.
Content URI cache copies and image memory-cache identities belong to one opening. Selecting a retained shared-list item queries a fresh source for that viewer session. Reopening a URI reads current provider bytes, while rotation, viewer switches, font/APK file copies, and archive entries reuse that opening's identity. Old copies remain eligible for the existing LRU trim.
Hex uses windows of up to 1,000,000,000 rows (8 GB with 8-byte rows, 16 GB with 16-byte rows). Larger files show Previous/Next controls and the inclusive byte range. Go to offset moves to the window and row containing the requested byte, including the last byte of Long-sized files; no tail is truncated. Files fitting one window retain the existing layout. The top row is saved as a byte offset across recreation, then mapped to the current window and row width.
