# Phase 1 review

Reviewed `6fe3ee1..2bd39ad` and fixed the findings on `feat/initial-app`. Runtime verification used the running Android 11, API 30 emulator `emulator-5554`. All changes remain local.

## Fixed findings

- **P1, `detect/Magic.kt:83`.** An eight-byte `ftyp` header constructed an invalid range and crashed detection. A JVM regression fails before the fix.
- **P1, `source/Resources.kt:8`, `viewer/SeekableLoader.kt:24`, `viewers/pdf/PdfViewer.kt:64`.** Cancellation while returning an IO-acquired resource could lose its handle. Acquisition now closes undelivered resources, and composition owns delivered handles through `finally`. The JVM cancellation test covers the dispatcher handoff.
- **P1, `source/ArchiveAccess.kt:93`, `source/ArchiveAccess.kt:142`, `source/ArchiveAccess.kt:198`.** Failed decompressor construction and entry lookup could leak input streams. Compressed TAR entry lookup lacked the listing scan bound. Constructors and lookup failures now close their resources. Compressed scans and XZ/7z decoder memory are bounded. The gzip-constructor regression fails before the fix.
- **P2, `source/SeekableSource.kt:61`, `source/ContentUriSource.kt:51`.** A raw stream and `ParcelFileDescriptor` shared descriptor ownership. `AutoCloseInputStream` now owns closure. Invalid descriptor windows are rejected, and partial windows are copied for consumers needing a whole-file descriptor. A real sender's offset-window PDF opened successfully.
- **P2, `source/Materialize.kt:28`, `FileViewerApp.kt:18`.** Concurrent materializations shared a partial filename, and startup cleanup could remove the current session's newly cached file. Partial names and session directories are now distinct. A concurrent-copy JVM test passes.
- **P2, `MainActivity.kt:55`, `detect/FileTypeDetector.kt:24`.** Incoming URI metadata ran on the main thread. It now runs off main, older requests cancel on new intents, rotation can retry an unfinished request, and missing read permission does not escape detection as an unhandled exception. `singleTop` activates the existing `onNewIntent` path for repeated deliveries.
- **P2, `viewers/pdf/PdfViewer.kt:160`, `viewers/pdf/PinchToZoom.kt:20`.** Zoom could retain the previous bitmap without requesting a sharper rendering, and gesture callbacks retained initial state. Rendering now runs for each requested width; callbacks see current state. The first visible page stays anchored, and zoom survives rotation. Renderer-construction and failed-render cleanup were also tightened.
- **P2, `viewers/image/ImageViewer.kt:54`.** Swiping back to the starting image left the next image's header and metadata visible. Every settled image now updates the current source. Viewer selection and the current local image survive rotation.
- **P2, `viewers/media/MediaViewer.kt:79`, `viewers/media/AudioPlayer.kt:32`.** Media could continue playing after the app left the foreground. The lifecycle observer now pauses it, removes itself before release, and retains position on rotation. Audio uses cover art and tags, a music placeholder, and readable Compose controls.
- **P2, `viewers/code/CodeEditorView.kt:31`, `viewers/code/CodeViewer.kt:70`.** Find counts depended on a one-second polling window. Sora result and selection events now update them. A manual encoding choice also reset on rotation and garbled Windows-1252 text; encoding and syntax now survive rotation. Completed checksums survive rotation, and cancelling a checksum run no longer exposes a premature restart.

## Emulator evidence

Screenshots and native accessibility snapshots are under `/tmp/fv-verify/`. Each PNG named below has a same-name JSON snapshot unless it is an animation frame.

- **Code and text.** `code-kotlin.png`, `text-sample-py.png`, `text-sample-rs.png`, `text-script.png`, and `text-utf16-txt.png`. Kotlin, Python, Rust, extensionless Python, and UTF-16 displayed correctly. `final-code-find.png` and `final-code-find-next.png` show 194 matches and navigation. `final-code-line150.png` shows the requested line. `encoding-picker.png` and `final-encoding-rotation.png` show manual Windows-1252 and a readable euro sign after rotation.
- **Images.** `image-01-photo.png`, `image-02-grid.png`, `image-05-vector.png`, and `image-06-photo.png` cover JPEG, PNG, SVG, and HEIC. `final-gif-frame0.png`, `final-gif-frame1.png`, `final-webp-frame0.png`, and `final-webp-frame1.png` capture distinct animation frames. Center pixels changed between the fixture's green and blue states. `final-image-swipe-next.png`, `final-image-swipe-back.png`, and `final-image-pinch.png` cover swipe and pinch, including the repaired header.
- **PDF.** `final-pdf-page80-zoom3.png` and `final-pdf-rotation.png` show page 80 of a 120-page PDF after two pinches and rotation. `intent-window-pdf.png` covers a PDF between a 512-byte prefix and suffix exposed through an `AssetFileDescriptor` window.
- **Media.** `media-video-playing.png` shows MP4 playback at two seconds. `final-audio-mp3-playing.png`, `final-audio-resume-paused.png`, `final-audio-rotation.png`, and `final-audio-wav.png` cover art/tags, playback, background pause, landscape controls, and an audio file without cover art. `final-media-error.png` and `final-media-error-hex.png` cover unsupported FFV1 video and its working Hex fallback.
- **Archives.** `viewer-bundle-zip.png`, `viewer-code-tar-gz.png`, and `viewer-bundle-7z.png` show listings. `archive-entry-bundle-zip.png`, `archive-entry-code-tar-gz.png`, and `archive-entry-bundle-7z.png` show Python entries opened from each archive. `archive-locked.png` covers encrypted ZIP refusal. `viewer-sample-rar.png` shows the documented RAR fallback. `viewer-document-docx.png` shows the phase-one Office package browser. `final-archive-traversal-name.png` opens `../display-only.txt` as a stream without filesystem extraction; materialization uses generated filenames.
- **Hex and Info.** `final-hex-200mb-end.png` shows the random 200 MB binary near its end. `info-binary.png`, `final-info-image.png`, `final-info-media.png`, and `final-info-hashes-rotation.png` cover file facts, image metadata, media information, and checksums.
- **Entry points and permissions.** `intent-send.png`, `intent-clip-new.png`, `intent-multiple.png`, and `intent-multiple-open.png` cover real ACTION_SEND, ClipData-only SEND, ACTION_SEND_MULTIPLE, selection, and repeated deliveries. These passed with All files access denied and files held by a private provider. `saf-open-pdf.png` and `saf-recent.png` cover the picker fallback and durable URI recents. `permission-system.png`, `permission-home-granted.png`, `browser-fixtures.png`, and `browser-open-image.png` cover the permission settings and in-app browser. Temporary share URIs were not added to recents.
- **Appearance and lifecycle.** The `final-*` viewer screenshots above include dark mode and landscape checks. The emulator's initial light mode, automatic rotation, portrait rotation value, and All files access setting were restored. `final-app.log` contains no fatal exception or dead-thread message from the final verification process.

The initial instrumentation sender force-stopped its provider when it finished and invalidated grants. Its early results were discarded. The committed helper uses a normal Activity for sharing. Android's native picker grid required D-pad center to activate the focused tile; a selected tile alone was not counted as a successful open.

## Verification results

```bash
JAVA_HOME=$HOME/.jdks/temurin-17 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Passed. There are 53 JVM tests, with zero failures. Lint reports zero issues. The final log is `/tmp/fv-verify/final-build.log`. `git diff --check` also passes, and all Kotlin source files remain below 400 lines.

`regressions-before.log` records the original short-ftyp crash and gzip-constructor leak. The resource-ownership tests also pass for cancellation, simultaneous materialization, and a bounded skip. Those ownership tests were not run against the original implementation, so they are final-behavior evidence rather than recorded red/green evidence.

An independent `claudeAgent/claude-sonnet-5-5` review checked the follow-up diff. Its plain-archive cap concern was addressed. Its assertion that plain TAR previously sought over bodies was disproved by the sparse-file experiment and Commons Compress bytecode, so no TAR speedup is claimed. The experiment's invalid performance assertion was removed. Observer removal and player release now share an owner. Later screenshots replace intermediate evidence for the repaired image, PDF, and encoding behavior. The append-only decision trail is `.audit/phase1-review.tsv`.

## Remaining limits

Verification covered one API 30 emulator, not physical devices or newer Android versions. Media decoding depends on the device. RAR, encrypted archive contents, password-protected PDFs, and phase-two document previews retain the documented support limits in `SUPPORT.md`. Header-encrypted 7z archives cannot expose names for a locked-entry listing. No push or pull request was created.
