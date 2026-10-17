# Phase 2 review

Reviewed `13be4dd..b3c7c0a` on `feat/initial-app`. Verification uses Android 11, API 30, on `emulator-5554`. Changes and commits remain local.

## Fixed findings

- **P1, `viewer/SafeXml.kt:50`.** Every SAX document preview failed on Android because `SAXParserFactory.setXIncludeAware(false)` throws `UnsupportedOperationException`. The unsupported setter is removed. XInclude defaults off. The actual Android Expat reader accepts ordinary XML and rejects internal, external-file, and external-network DOCTYPEs before emitting text. `phase2-security-before.log` records the original failure; `phase2-security-final.log` records the device checks.
- **P1, `viewers/documents/XlsxParser.kt:16`, `OdfParser.kt:57`, `SheetGrid.kt:34`.** Repeated sheets could each retain four million HTML characters, bypassing the intended document bound. Workbooks now share one output budget. Compact repeated-row ODS and repeated-sheet XLSX regressions verify the combined output bound.
- **P1, `viewers/documents/DocBuffer.kt:6`, `HtmlOut.kt:47`, `SlideHtml.kt:8`.** Paragraph, run, cell, and slide-title buffers could grow before the final HTML limit applied. ODF repeated spaces amplified a small input into unbounded output. Intermediate buffers now refuse oversized blocks, and HTML writers check remaining space before appending. EPUB text chunks and slide titles also respect the output bound. Oversized blocks offer Text as the fallback.
- **P2, `viewer/SafeWebView.kt:23`.** The API 30 WebView hardware layer erased surrounding Compose controls. A software layer preserves the header, viewer chips, and preview notes. `phase2-docx-light.png` and `phase2-docx-transparent.png` show failed renderings; `phase2-docx-software.png` and the final gallery show the restored controls. Changing only the background color did not fix it and was discarded.
- **P2, `viewers/data/sqlite/SqliteSession.kt:64`, `SqliteViewer.kt:68`, `SqlText.kt:59`.** Oversized WAL databases fell through to opening the original file. Failed copies could leave scratch directories, WAL copies were unbounded, and prompt cancellation could lose a database handle returned from IO. Required copies now refuse oversized files, bound both database and WAL streams, clean failed copies, and acquire the session through the existing cancellation-safe owner. The on-device probe checks original database and sidecar hashes and rejects a sparse file over 2 GB without creating an original SHM file.
- **P2, `viewers/data/sqlite/SqliteSession.kt:33`, `SqlText.kt:15`.** A large BLOB or text cell overflowed Android's CursorWindow before Kotlin could clip it. SELECT projections now retrieve BLOB lengths and bounded text with their SQLite types. A real 5 MB BLOB and 3 MB text row now display. `phase2-sqlite-blob-before.png` records the failure; `phase2-final-sqlite-blob.png` records the fix.
- **P2, `viewers/data/sqlite/SqliteObjectScreen.kt:87`.** The Rows tab consumed the toolbar width, hiding the table name and Schema tab. Intrinsic tab widths keep both controls visible. Schema highlighting, pagination, and table scrolling passed on the emulator.
- **P2, `viewers/misc/cert/PemBlocks.kt:19`.** An unclosed sequence of PEM BEGIN markers triggered repeated whole-file regex searches. A marker scan now processes each boundary once, checks interruption, and reports incomplete files. Private-key blocks never undergo Base64 decoding or enter the certificate/key-body model. A 200,000-marker regression completes within its test timeout.
- **P2, `viewers/misc/font/Coverage.kt:75`.** Overlapping format-4 cmap segments could trigger billions of repeated code-point iterations. The parser rejects unsorted or overlapping segments and checks interruption. Font loading now uses an interruptible worker. A 32,767-overlapping-segment regression passes.
- **P2, `viewers/data/json/JsonParser.kt:38`, `JsonParser.kt:48`, `JsonViewer.kt:48`.** Truncation lost open containers' child counts, and JSONL accepted several values on one line or a value split across lines. Parent counts are repaired after truncation, and JSONL enforces line boundaries. Files over 32 MB go directly to source. JSON and CSV parsing now honor worker interruption. JSONL regressions fail before the fix and pass after it.
- **P2, `viewers/documents/DocxParser.kt:47`.** Tracked old run/paragraph properties overrode current formatting, and tables inside text boxes lost their cell structure. Old property subtrees are skipped, and text boxes use their own document handler before joining the outer paragraph. Text boxes share remaining output space, propagate truncation with a note, and retain their partial text. Long legacy runs flush at the bound and retain their truncated preview. The text-box regression fails before the fix.

`MiscLimits` and `DataLimits` are removed. Their callers and font-directory bounds use `source/Limits.kt`. `ApkFacts` uses the KTX drawable conversion, removing the remaining `UseKtx` warning.

## Emulator verification matrix

Screenshots are under `/tmp/fv-verify/`; the names below are relative to that directory. Final screenshots have matching JSON accessibility snapshots, except the over-32-MB JSON source. Its snapshot helper refused the application tree, so its JSON file records that diagnostic and its PNG is the verification evidence. Contact sheets were inspected alongside individual before/after and interaction screenshots.

| Viewer or boundary | Verified behavior | Screenshot evidence |
|---|---|---|
| DOCX | Heading, bold text, list, table, embedded image | `phase2-final-docx-light.png`, `phase2-final-docx-dark.png` |
| XLSX | Multiple sheets, dates, 2,000-row truncation note, sheet switching | `phase2-final-xlsx-light.png`, `phase2-final-xlsx-tab2.png`, `phase2-final-xlsx-dark.png` |
| PPTX | Both slides, titles, images | `phase2-final-pptx-light.png` |
| ODT, ODS, ODP | Heading and image, repeated sheet rows, slide title and image | `phase2-final-odt-light.png`, `phase2-final-ods-light.png`, `phase2-final-odp-light.png` |
| EPUB | Book images, chapter titles, next chapter, table of contents; escaping image omitted | `phase2-final-epub-light.png`, `phase2-final-epub-chapter2.png`, `phase2-final-epub-nav.png`, `phase2-final-epub-dark.png` |
| RTF and legacy Office | Bold, accented and Unicode text, simple table; labelled binary text extraction | `phase2-final-rtf-light.png`, `phase2-final-doc-light.png`, `phase2-final-xls-light.png`, `phase2-final-ppt-light.png` |
| Fonts | TTF and OTF metadata/specimen; WOFF2 documented fallback | `phase2-final-ttf-light.png`, `phase2-final-otf-light.png`, `phase2-final-woff2-light.png` |
| APK | Settings APK label, icon, package, versions, permissions, sensitive badges, signer | `phase2-final-apk-light.png`, `phase2-final-apk-dark.png` |
| Certificates and keys | PEM chain, expired badge, DER facts, public key fingerprint, hidden private body | `phase2-final-chain-pem-light.png`, `phase2-final-expired-pem-light.png`, `phase2-final-leaf-der-light.png`, `phase2-final-public-key-pem-light.png`, `phase2-final-private-pem-light.png`, `phase2-final-certificate-dark.png`, `phase2-final-chain-second-cert.png` |
| Markdown | Front matter, table, task list, local image, raw HTML shown literally | `phase2-final-md-light.png`, `phase2-final-md-dark.png` |
| HTML | Local image, `SCRIPT BLOCKED` stays unchanged, remote/traversing/file resources blocked | `phase2-final-html-light.png`, `phase2-final-html-dark.png` |
| CSV and TSV | Quoted newline and quotes, semicolon/tab detection, row dialog, horizontal and vertical scroll with fixed header | `phase2-final-csv-quoted-row.png`, `phase2-final-csv-both-scrolls.png`, `phase2-final-semicolon-csv-light.png`, `phase2-final-tsv-light.png`, `phase2-final-csv-dark.png` |
| JSON and JSONL | Typed tree, deep-input source/error, 200,000-node truncation, invalid JSON line/column, two JSONL records | `phase2-final-json-light.png`, `phase2-final-deep-json-light.png`, `phase2-final-huge-json-light.png`, `phase2-final-invalid-json-light.png`, `phase2-final-jsonl-light.png`, `phase2-final-json-dark.png` |
| SQLite | Tables/views and counts, rows, sticky header while scrolling both ways, page 2, SQL schema, large BLOB label | `phase2-final-sqlite-objects.png`, `phase2-final-sqlite-both-scrolls.png`, `phase2-final-sqlite-page2.png`, `phase2-final-sqlite-schema.png`, `phase2-final-sqlite-blob.png` |
| SQLite WAL | Uncheckpointed row visible; original database and WAL hashes unchanged | `phase2-final-sqlite-wal-row.png`, `phase2-wal-original.txt`, `phase2-security-final.log` |
| Font input and rotation | Visible stock keyboard, retained sample after rotation | `phase2-final-font-keyboard.png`, `phase2-final-font-rotation.png` |
| Content URIs | SQLite, WAL-main-only warning, font, APK and DOCX with All files access denied | `phase2-final-uri-phase2-normal-db.png`, `phase2-final-uri-phase2-wal-db.png`, `phase2-final-uri-phase2-ttf.png`, `phase2-final-uri-phase2-apk.png`, `phase2-final-uri-phase2-docx.png` |
| SQLite journal | Committed row visible, database and journal hashes unchanged | `phase2-final-sqlite-journal-row.png`, `phase2-journal-original.txt` |
| Large JSON | 33 MB input opens in paged source with the size note | `phase2-final-json-over32mb.png` |
| Malicious packages | DOCX/EPUB XXE refused; DOCX `../../` image target omitted | `phase2-final-xxe-docx-light.png`, `phase2-final-xxe-epub-light.png`, `phase2-final-traversal-docx-light.png` |

## Verification results

```bash
JAVA_HOME=$HOME/.jdks/temurin-17 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Passed with 193 JVM tests, zero failures, and zero lint issues. The build log is `/tmp/fv-verify/phase2-final-build.log`. `git diff --check` passes, and Kotlin sources remain below 400 lines.


The reproducible generators, device security probe, and UI driver are in `tools/verification/`. The generator needs no network. Legacy Office fixtures test the documented extractor using synthetic OLE signatures; they do not validate fidelity against Microsoft Office.

Rotation checks also cover DOCX, XLSX, EPUB with chapter 2 retained, APK, Markdown, HTML, CSV, JSON, certificates, SQLite, and the font sample. The evidence is `phase2-final-*-rotation.png` and `phase2-final-sqlite-landscape.png`. The original light mode, portrait rotation, automatic rotation, and All files UID allowance are restored. The current-process log is `phase2-final-app.log`; it contains no fatal exception, ANR, leaked-window or dead-object message.

An early UI run overlapped the security instrumentation's app shutdown and was discarded. The font entry helper's invisible IME also produced launcher captures, which were discarded and replaced by the stock-keyboard and retained-text screenshots. A wildcard listing in the WAL driver was corrected. The final WAL screenshot shows row 225, `WAL_ONLY_HERON`, rather than relying only on its row count.

Prove It Works drove device checks. Build the Lever produced the fixture generator and security probe. Fix Root Causes removed the Android SAX failure and moved SQLite clipping before CursorWindow allocation. Model the Domain put shared output budgets and resource limits in explicit owners.

The decision trail is `.audit/phase2-review.tsv`. Independent `claudeAgent/claude-opus-5-5` reviews checked the original parsers and two follow-up rounds. The final narrow review passed with no remaining P1 or P2 findings. No confirmed finding remains open. Its two minor follow-ups, shared textbox space and legacy-run offsets, were also fixed and tested. A reported copy-cancellation concern was refuted because `LimitedInputStream` checks interruption on every read. Strict rejection of malformed cmap ranges is intentional. Codex Pro `gpt-6.1-sol` performed all device interaction, implementation fixes, builds, and tests.

Local commits are `c980932` for document previews, `0157abb` for database/data/misc fixes and limits, and `b273e1a` for verification tools. The review report and trail are committed separately. Nothing was pushed and no pull request was opened.

## Remaining limits

Verification covers one API 30 emulator. Physical devices and newer Android versions were not checked. The documented preview limits remain, including approximate Office layout, legacy best-effort extraction, device-dependent WOFF loading, and WOFF2 fallback. A single content URI does not grant access to sibling HTML assets or a database's separate WAL. The SQLite viewer explains missing WAL changes. Journal rollback is intentionally not applied. A live database can change while its files are copied; this is not a transactional backup tool.
