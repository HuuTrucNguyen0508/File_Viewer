#!/usr/bin/env python3
"""Download the bundled TextMate grammars and write assets/textmate/languages.json.

Rerun to refresh grammars. Each entry: (language id, url, scopeName or None to read it from the file).
Language ids are what detect/ExtensionTable.kt refers to.
"""
import json, pathlib, urllib.request

VS = "https://raw.githubusercontent.com/microsoft/vscode/main/extensions/"
GRAMMARS = [
    ("bat", VS + "bat/syntaxes/batchfile.tmLanguage.json"),
    ("clojure", VS + "clojure/syntaxes/clojure.tmLanguage.json"),
    ("coffeescript", VS + "coffeescript/syntaxes/coffeescript.tmLanguage.json"),
    ("c", VS + "cpp/syntaxes/c.tmLanguage.json"),
    ("cpp", VS + "cpp/syntaxes/cpp.tmLanguage.json"),
    ("cpp-macro", VS + "cpp/syntaxes/cpp.embedded.macro.tmLanguage.json"),
    ("csharp", VS + "csharp/syntaxes/csharp.tmLanguage.json"),
    ("css", VS + "css/syntaxes/css.tmLanguage.json"),
    ("dart", VS + "dart/syntaxes/dart.tmLanguage.json"),
    ("diff", VS + "diff/syntaxes/diff.tmLanguage.json"),
    ("dockerfile", VS + "docker/syntaxes/docker.tmLanguage.json"),
    ("dotenv", VS + "dotenv/syntaxes/dotenv.tmLanguage.json"),
    ("fsharp", VS + "fsharp/syntaxes/fsharp.tmLanguage.json"),
    ("git-commit", VS + "git-base/syntaxes/git-commit.tmLanguage.json"),
    ("ignore", VS + "git-base/syntaxes/ignore.tmLanguage.json"),
    ("go", VS + "go/syntaxes/go.tmLanguage.json"),
    ("groovy", VS + "groovy/syntaxes/groovy.tmLanguage.json"),
    ("handlebars", VS + "handlebars/syntaxes/Handlebars.tmLanguage.json"),
    ("hlsl", VS + "hlsl/syntaxes/hlsl.tmLanguage.json"),
    ("html", VS + "html/syntaxes/html.tmLanguage.json"),
    ("ini", VS + "ini/syntaxes/ini.tmLanguage.json"),
    ("java", VS + "java/syntaxes/java.tmLanguage.json"),
    ("javascript", VS + "javascript/syntaxes/JavaScript.tmLanguage.json"),
    ("javascriptreact", VS + "javascript/syntaxes/JavaScriptReact.tmLanguage.json"),
    ("json", VS + "json/syntaxes/JSON.tmLanguage.json"),
    ("jsonc", VS + "json/syntaxes/JSONC.tmLanguage.json"),
    ("jsonl", VS + "json/syntaxes/JSONL.tmLanguage.json"),
    ("julia", VS + "julia/syntaxes/julia.tmLanguage.json"),
    ("latex", VS + "latex/syntaxes/LaTeX.tmLanguage.json"),
    ("tex", VS + "latex/syntaxes/TeX.tmLanguage.json"),
    ("bibtex", VS + "latex/syntaxes/Bibtex.tmLanguage.json"),
    ("less", VS + "less/syntaxes/less.tmLanguage.json"),
    ("log", VS + "log/syntaxes/log.tmLanguage.json"),
    ("lua", VS + "lua/syntaxes/lua.tmLanguage.json"),
    ("makefile", VS + "make/syntaxes/make.tmLanguage.json"),
    ("markdown", VS + "markdown-basics/syntaxes/markdown.tmLanguage.json"),
    ("objective-c", VS + "objective-c/syntaxes/objective-c.tmLanguage.json"),
    ("perl", VS + "perl/syntaxes/perl.tmLanguage.json"),
    ("php", VS + "php/syntaxes/php.tmLanguage.json"),
    ("powershell", VS + "powershell/syntaxes/powershell.tmLanguage.json"),
    ("pug", VS + "pug/syntaxes/pug.tmLanguage.json"),
    ("python", VS + "python/syntaxes/MagicPython.tmLanguage.json"),
    ("regexp-python", VS + "python/syntaxes/MagicRegExp.tmLanguage.json"),
    ("r", VS + "r/syntaxes/r.tmLanguage.json"),
    ("restructuredtext", VS + "restructuredtext/syntaxes/rst.tmLanguage.json"),
    ("ruby", VS + "ruby/syntaxes/ruby.tmLanguage.json"),
    ("rust", VS + "rust/syntaxes/rust.tmLanguage.json"),
    ("scss", VS + "scss/syntaxes/scss.tmLanguage.json"),
    ("shellscript", VS + "shellscript/syntaxes/shell-unix-bash.tmLanguage.json"),
    ("sql", VS + "sql/syntaxes/sql.tmLanguage.json"),
    ("swift", VS + "swift/syntaxes/swift.tmLanguage.json"),
    ("typescript", VS + "typescript-basics/syntaxes/TypeScript.tmLanguage.json"),
    ("typescriptreact", VS + "typescript-basics/syntaxes/TypeScriptReact.tmLanguage.json"),
    ("vb", VS + "vb/syntaxes/asp-vb-net.tmLanguage.json"),
    ("xml", VS + "xml/syntaxes/xml.tmLanguage.json"),
    ("xsl", VS + "xml/syntaxes/xsl.tmLanguage.json"),
    ("yaml", VS + "yaml/syntaxes/yaml.tmLanguage.json"),
    ("yaml-1.0", VS + "yaml/syntaxes/yaml-1.0.tmLanguage.json"),
    ("yaml-1.1", VS + "yaml/syntaxes/yaml-1.1.tmLanguage.json"),
    ("yaml-1.2", VS + "yaml/syntaxes/yaml-1.2.tmLanguage.json"),
    ("yaml-1.3", VS + "yaml/syntaxes/yaml-1.3.tmLanguage.json"),
    ("yaml-embedded", VS + "yaml/syntaxes/yaml-embedded.tmLanguage.json"),
    ("kotlin", "https://raw.githubusercontent.com/fwcd/vscode-kotlin/main/syntaxes/kotlin.tmLanguage.json"),
    ("toml", "https://raw.githubusercontent.com/tamasfe/taplo/master/editors/vscode/toml.tmLanguage.json"),
    ("zig", "https://raw.githubusercontent.com/ziglang/vscode-zig/master/syntaxes/zig.tmLanguage.json"),
    ("proto", "https://raw.githubusercontent.com/zxh0/vscode-proto3/master/syntaxes/proto3.tmLanguage.json"),
    ("graphql", "https://raw.githubusercontent.com/graphql/graphiql/main/packages/vscode-graphql-syntax/grammars/graphql.json"),
]

out = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/textmate"
(out / "grammars").mkdir(parents=True, exist_ok=True)
languages = []
for lang_id, url in GRAMMARS:
    data = json.loads(urllib.request.urlopen(url, timeout=30).read())
    rel = f"grammars/{lang_id}.tmLanguage.json"
    (out / rel).write_text(json.dumps(data, separators=(",", ":")))
    languages.append({"name": lang_id, "scopeName": data["scopeName"], "grammar": f"textmate/{rel}"})
    print(lang_id, data["scopeName"])
(out / "languages.json").write_text(json.dumps({"languages": languages}, indent=1))
