package com.fileviewer.app.detect

/** File-name based detection: whole names first (Makefile, .bashrc), then extensions. */
object ExtensionTable {

    fun byName(fileName: String): FileKind? {
        val lower = fileName.lowercase()
        names[lower]?.let { return it }
        if (lower.startsWith(".env.")) return code("dotenv")
        if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) return FileKind.Archive(ArchiveFormat.GZIP)
        if (lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2")) return FileKind.Archive(ArchiveFormat.BZIP2)
        if (lower.endsWith(".tar.xz") || lower.endsWith(".txz")) return FileKind.Archive(ArchiveFormat.XZ)
        val dot = lower.lastIndexOf('.')
        if (dot <= 0 || dot == lower.length - 1) return null
        return extensions[lower.substring(dot + 1)]
    }

    fun byExtension(ext: String): FileKind? = extensions[ext.lowercase()]

    private fun code(lang: String) = CodeLanguages.text(lang)
    private fun plain(label: String) = FileKind.Text(null, label)

    private val names: Map<String, FileKind> = buildMap {
        listOf("makefile", "gnumakefile", "kbuild").forEach { put(it, code("makefile")) }
        listOf("dockerfile", "containerfile").forEach { put(it, code("dockerfile")) }
        listOf(".bashrc", ".bash_profile", ".bash_logout", ".zshrc", ".zprofile", ".zshenv", ".profile",
            ".bash_aliases", ".xinitrc", ".xprofile", "pkgbuild", "apkbuild").forEach { put(it, code("shellscript")) }
        listOf(".gitignore", ".dockerignore", ".npmignore", ".hgignore", ".eslintignore", ".prettierignore")
            .forEach { put(it, code("ignore")) }
        listOf(".gitconfig", ".editorconfig", ".gitmodules", ".gitattributes", ".npmrc", ".pylintrc", "setup.cfg",
            "tox.ini", ".flake8").forEach { put(it, code("ini")) }
        listOf(".env").forEach { put(it, code("dotenv")) }
        listOf("gemfile", "rakefile", "podfile", "vagrantfile", "guardfile", "brewfile", "fastfile", "appfile")
            .forEach { put(it, code("ruby")) }
        put("jenkinsfile", code("groovy"))
        listOf(".babelrc", ".prettierrc", ".eslintrc", ".jshintrc", ".swcrc").forEach { put(it, code("json")) }
        listOf("cargo.lock", "pipfile", "poetry.lock", "uv.lock").forEach { put(it, code("toml")) }
        put("commit_editmsg", code("git-commit"))
        listOf("license", "licence", "copying", "readme", "authors", "contributors", "changelog", "notice",
            "todo", "install", "news", "go.mod", "go.sum", "cmakelists.txt", "procfile", ".nvmrc", ".python-version")
            .forEach { put(it, plain("Plain text")) }
    }

    private val extensions: Map<String, FileKind> = buildMap {
        fun lang(id: String, vararg exts: String) = exts.forEach { put(it, code(id)) }
        lang("kotlin", "kt", "kts")
        lang("java", "java", "jav", "bsh")
        lang("groovy", "groovy", "gvy", "gradle", "gy")
        lang("javascript", "js", "mjs", "cjs", "es6", "jsm")
        lang("javascriptreact", "jsx")
        lang("typescript", "ts", "mts", "cts")
        lang("typescriptreact", "tsx")
        lang("python", "py", "pyw", "pyi", "pyx", "gyp", "rpy", "wsgi")
        lang("c", "c", "h")
        lang("cpp", "cpp", "cc", "cxx", "c++", "hpp", "hh", "hxx", "h++", "ino", "ipp", "inl", "cu", "cuh", "tcc")
        lang("csharp", "cs", "csx", "cake")
        lang("fsharp", "fs", "fsi", "fsx", "fsscript")
        lang("go", "go")
        lang("rust", "rs")
        lang("swift", "swift")
        lang("objective-c", "m", "mm")
        lang("dart", "dart")
        lang("ruby", "rb", "rbx", "gemspec", "rake", "ru", "erb", "podspec", "rbi")
        lang("php", "php", "php3", "php4", "php5", "phtml", "ctp")
        lang("perl", "pl", "pm", "pod", "t", "psgi")
        lang("lua", "lua", "luau", "rockspec")
        lang("r", "r", "rhistory", "rprofile", "rt")
        lang("julia", "jl")
        lang("clojure", "clj", "cljs", "cljc", "cljx", "clojure", "edn")
        lang("coffeescript", "coffee", "cson", "iced")
        lang("shellscript", "sh", "bash", "zsh", "ksh", "fish", "bats", "command", "ebuild", "eclass")
        lang("powershell", "ps1", "psm1", "psd1", "pssc", "psrc")
        lang("bat", "bat", "cmd")
        lang("sql", "sql", "dsql", "psql", "pgsql", "mysql", "ddl", "dml")
        lang("css", "css")
        lang("scss", "scss", "sass")
        lang("less", "less")
        lang("html", "xhtml", "shtml", "vue", "svelte", "astro", "jsp", "asp", "aspx", "cshtml", "razor")
        lang("handlebars", "hbs", "handlebars", "mustache")
        lang("pug", "pug", "jade")
        lang("xml", "xsd", "plist", "csproj", "vbproj", "fsproj", "props", "targets", "nuspec", "resx", "xaml",
            "pom", "rss", "atom", "wsdl", "xliff", "xlf", "kml", "gpx", "opml", "storyboard", "xib", "iml",
            "manifest", "glade", "ui", "tmx", "dtd", "ent", "xul", "rdf", "owl", "fxml", "smil")
        lang("xsl", "xsl", "xslt")
        lang("jsonc", "jsonc", "code-workspace", "code-snippets")
        lang("jsonl", "jsonl", "ndjson")
        lang("toml", "toml")
        lang("ini", "ini", "cfg", "conf", "properties", "prefs", "desktop", "service", "socket", "timer",
            "mount", "target", "inf", "reg", "url", "directory", "theme", "repo")
        lang("dotenv", "env")
        lang("makefile", "mk", "mak", "make")
        lang("dockerfile", "dockerfile", "containerfile")
        lang("diff", "diff", "patch", "rej")
        lang("latex", "tex", "ltx", "sty", "cls", "dtx", "ins")
        lang("bibtex", "bib")
        lang("restructuredtext", "rst")
        lang("log", "log")
        lang("hlsl", "hlsl", "hlsli", "fx", "fxh", "vsh", "psh", "cginc", "compute", "glsl", "vert", "frag",
            "geom", "tesc", "tese", "comp", "shader", "wgsl", "metal")
        lang("vb", "vb", "vbs", "brs", "bas", "vba")
        lang("zig", "zig", "zon")
        lang("proto", "proto")
        lang("graphql", "graphql", "gql", "graphqls")

        listOf("txt", "text", "nfo", "me", "1st", "asc", "srt", "vtt", "sub", "lst", "list", "lrc", "readme",
            "cue", "m3u", "m3u8", "pls", "gitkeep", "keep", "pem", "crt", "cer", "key", "pub", "csr", "gpg",
            "sig", "srx", "org", "adoc", "asciidoc", "textile", "wiki", "mediawiki", "tsv-schema", "ass", "ssa",
            "nix", "tf", "tfvars", "hcl", "hs", "lhs", "elm", "ex", "exs", "erl", "hrl", "scala", "sc", "sbt",
            "ml", "mli", "nim", "cr", "d", "v", "sv", "vhd", "vhdl", "asm", "s", "f", "f90", "f95", "for",
            "pas", "pp", "lisp", "el", "scm", "rkt", "tcl", "awk", "sed", "vim", "cmake", "lock", "sum", "mdx",
            "pbxproj", "strings", "po", "pot", "ics", "vcf", "vcard", "eml", "mbox", "gcode", "nc", "ps", "eps",
            "cpuprofile", "sln", "bazel", "bzl", "star", "gn", "gni", "ninja", "meson", "jsonnet", "libsonnet",
            "rego", "cue-lang", "prisma", "sol", "move", "cairo", "odin", "gleam", "jai", "vala", "hx", "ahk",
            "au3", "applescript", "scpt", "ps1xml", "rc", "def", "map", "dot", "gv", "puml", "plantuml", "mmd")
            .forEach { putIfAbsent(it, plain("Plain text")) }

        put("md", FileKind.Data(DataFormat.MARKDOWN)); put("markdown", FileKind.Data(DataFormat.MARKDOWN))
        put("mkd", FileKind.Data(DataFormat.MARKDOWN)); put("mdown", FileKind.Data(DataFormat.MARKDOWN))
        put("html", FileKind.Data(DataFormat.HTML)); put("htm", FileKind.Data(DataFormat.HTML))
        put("json", FileKind.Data(DataFormat.JSON)); put("geojson", FileKind.Data(DataFormat.JSON))
        put("webmanifest", FileKind.Data(DataFormat.JSON)); put("har", FileKind.Data(DataFormat.JSON))
        put("ipynb", FileKind.Data(DataFormat.JSON)); put("json5", FileKind.Data(DataFormat.JSON))
        put("csv", FileKind.Data(DataFormat.CSV)); put("tsv", FileKind.Data(DataFormat.TSV)); put("tab", FileKind.Data(DataFormat.TSV))
        put("xml", FileKind.Data(DataFormat.XML)); put("svgz", FileKind.Image(ImageFormat.SVG))
        put("yaml", FileKind.Data(DataFormat.YAML)); put("yml", FileKind.Data(DataFormat.YAML))
        listOf("sqlite", "sqlite3", "db", "db3", "s3db", "sl3").forEach { put(it, FileKind.Data(DataFormat.SQLITE)) }

        mapOf("png" to ImageFormat.PNG, "apng" to ImageFormat.PNG, "jpg" to ImageFormat.JPEG, "jpeg" to ImageFormat.JPEG,
            "jpe" to ImageFormat.JPEG, "jfif" to ImageFormat.JPEG, "gif" to ImageFormat.GIF, "webp" to ImageFormat.WEBP,
            "heic" to ImageFormat.HEIF, "heif" to ImageFormat.HEIF, "hif" to ImageFormat.HEIF, "avif" to ImageFormat.AVIF,
            "bmp" to ImageFormat.BMP, "dib" to ImageFormat.BMP, "ico" to ImageFormat.ICO, "cur" to ImageFormat.ICO,
            "svg" to ImageFormat.SVG, "tif" to ImageFormat.TIFF, "tiff" to ImageFormat.TIFF, "dng" to ImageFormat.TIFF,
            "psd" to ImageFormat.PSD).forEach { (e, f) -> put(e, FileKind.Image(f)) }
        put("pdf", FileKind.Pdf)
        mapOf("mp4" to MediaFormat.MP4, "m4v" to MediaFormat.MP4, "mov" to MediaFormat.MOV, "qt" to MediaFormat.MOV,
            "mkv" to MediaFormat.MKV, "mka" to MediaFormat.MKV, "webm" to MediaFormat.WEBM, "3gp" to MediaFormat.THREE_GP,
            "3g2" to MediaFormat.THREE_GP, "avi" to MediaFormat.AVI, "mts" to MediaFormat.MPEG_TS,
            "m2ts" to MediaFormat.MPEG_TS, "mpg" to MediaFormat.MPEG_PS, "mpeg" to MediaFormat.MPEG_PS,
            "flv" to MediaFormat.FLV, "wmv" to MediaFormat.WMV, "wma" to MediaFormat.WMV, "mp3" to MediaFormat.MP3,
            "m4a" to MediaFormat.M4A, "m4b" to MediaFormat.M4A, "aac" to MediaFormat.AAC, "flac" to MediaFormat.FLAC,
            "ogg" to MediaFormat.OGG, "oga" to MediaFormat.OGG, "ogv" to MediaFormat.OGG, "opus" to MediaFormat.OPUS,
            "wav" to MediaFormat.WAV, "amr" to MediaFormat.AMR).forEach { (e, f) -> put(e, FileKind.Media(f)) }
        mapOf("zip" to null, "jar" to "JAR", "war" to "JAR", "ear" to "JAR", "aar" to "AAR", "apk" to "APK",
            "apks" to "APK", "xapk" to "APK", "aab" to "App Bundle", "whl" to "Python wheel", "nupkg" to "NuGet",
            "vsix" to "VSIX", "crx" to null, "xpi" to "Firefox add-on", "kmz" to null, "cbz" to "Comic book",
            "ipa" to "iOS app", "zipx" to null).forEach { (e, sub) -> put(e, FileKind.Archive(ArchiveFormat.ZIP, sub)) }
        put("tar", FileKind.Archive(ArchiveFormat.TAR)); put("gz", FileKind.Archive(ArchiveFormat.GZIP))
        put("bz2", FileKind.Archive(ArchiveFormat.BZIP2)); put("xz", FileKind.Archive(ArchiveFormat.XZ))
        put("7z", FileKind.Archive(ArchiveFormat.SEVEN_Z)); put("rar", FileKind.Archive(ArchiveFormat.RAR))
        put("cbr", FileKind.Archive(ArchiveFormat.RAR)); put("deb", FileKind.Archive(ArchiveFormat.AR))
        put("a", FileKind.Archive(ArchiveFormat.AR))
        mapOf("docx" to DocumentFormat.DOCX, "docm" to DocumentFormat.DOCX, "dotx" to DocumentFormat.DOCX,
            "xlsx" to DocumentFormat.XLSX, "xlsm" to DocumentFormat.XLSX, "pptx" to DocumentFormat.PPTX,
            "ppsx" to DocumentFormat.PPTX, "odt" to DocumentFormat.ODT, "ods" to DocumentFormat.ODS,
            "odp" to DocumentFormat.ODP, "epub" to DocumentFormat.EPUB, "rtf" to DocumentFormat.RTF,
            "doc" to DocumentFormat.DOC, "dot" to DocumentFormat.DOC, "xls" to DocumentFormat.XLS,
            "ppt" to DocumentFormat.PPT, "pps" to DocumentFormat.PPT).forEach { (e, f) -> put(e, FileKind.Document(f)) }
        mapOf("ttf" to FontFormat.TTF, "otf" to FontFormat.OTF, "woff" to FontFormat.WOFF, "woff2" to FontFormat.WOFF2,
            "ttc" to FontFormat.TTC).forEach { (e, f) -> put(e, FileKind.Font(f)) }
        mapOf("exe" to "Windows executable", "dll" to "Windows library", "so" to "Shared library",
            "o" to "Object file", "class" to "Java class file", "dex" to "Dalvik executable", "wasm" to "WebAssembly module",
            "bin" to "Binary data", "dat" to "Binary data", "img" to "Disk image", "iso" to "Disk image",
            "dmg" to "Disk image", "pyc" to "Python bytecode", "mid" to "MIDI sequence", "midi" to "MIDI sequence",
            "zst" to "Zstandard compressed data", "msi" to "Windows installer")
            .forEach { (e, label) -> put(e, FileKind.Binary(label)) }
    }
}
