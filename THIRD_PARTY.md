# Third-party content bundled in the app

| What | Source | License |
|---|---|---|
| TextMate grammars (most languages) | microsoft/vscode `extensions/*/syntaxes` | MIT (individual grammars carry their upstream MIT/BSD notices in the JSON) |
| Kotlin grammar | fwcd/vscode-kotlin | MIT |
| TOML grammar | tamasfe/taplo | MIT |
| Zig grammar | ziglang/vscode-zig | MIT |
| Protocol Buffers grammar | zxh0/vscode-proto3 | MIT |
| GraphQL grammar | graphql/graphiql (vscode-graphql-syntax) | MIT |
| IBM Plex Sans | google/fonts `ofl/ibmplexsans` | SIL Open Font License 1.1 |
| JetBrains Mono | google/fonts `ofl/jetbrainsmono` | SIL Open Font License 1.1 |

Grammars are fetched by `tools/fetch_grammars.py`. Library dependencies (Sora Editor, Coil, telephoto, Media3, Apache Commons Compress, XZ for Java) are pulled by Gradle under their own licenses (LGPL-2.1 for Sora Editor; Apache-2.0 for the rest; XZ for Java is 0BSD).
