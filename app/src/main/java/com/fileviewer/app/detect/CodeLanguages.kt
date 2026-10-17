package com.fileviewer.app.detect

/** Display names for the bundled grammar ids (see tools/fetch_grammars.py). */
object CodeLanguages {
    private val labels = mapOf(
        "bat" to "Batch script", "clojure" to "Clojure", "coffeescript" to "CoffeeScript", "c" to "C",
        "cpp" to "C++", "csharp" to "C#", "css" to "CSS", "dart" to "Dart", "diff" to "Diff",
        "dockerfile" to "Dockerfile", "dotenv" to "Environment file", "fsharp" to "F#",
        "git-commit" to "Git commit message", "ignore" to "Ignore file", "go" to "Go", "groovy" to "Groovy",
        "handlebars" to "Handlebars", "hlsl" to "HLSL", "html" to "HTML", "ini" to "INI config",
        "java" to "Java", "javascript" to "JavaScript", "javascriptreact" to "JSX", "json" to "JSON",
        "jsonc" to "JSON with comments", "jsonl" to "JSON Lines", "julia" to "Julia", "latex" to "LaTeX",
        "tex" to "TeX", "bibtex" to "BibTeX", "less" to "Less", "log" to "Log", "lua" to "Lua",
        "makefile" to "Makefile", "markdown" to "Markdown", "objective-c" to "Objective-C", "perl" to "Perl",
        "php" to "PHP", "powershell" to "PowerShell", "pug" to "Pug", "python" to "Python", "r" to "R",
        "restructuredtext" to "reStructuredText", "ruby" to "Ruby", "rust" to "Rust", "scss" to "SCSS",
        "shellscript" to "Shell script", "sql" to "SQL", "swift" to "Swift", "typescript" to "TypeScript",
        "typescriptreact" to "TSX", "vb" to "Visual Basic", "xml" to "XML", "xsl" to "XSLT", "yaml" to "YAML",
        "kotlin" to "Kotlin", "toml" to "TOML", "zig" to "Zig", "proto" to "Protocol Buffers",
        "graphql" to "GraphQL",
    )

    fun label(id: String): String = labels[id] ?: id

    /** Ids offered in the editor's language picker, sorted by label. */
    val pickable: List<String> by lazy { labels.keys.sortedBy { labels[it]!!.lowercase() } }

    fun text(language: String) = FileKind.Text(language, label(language))
}
