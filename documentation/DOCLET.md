# JAIDoc — Java Doclet to JSON

JAIDoc serializes the complete Javadoc of a Java project into structured JSON with every element (types, fields, methods,
constructors, annotations, block tags, etc.).

## Overview

The project has two main components:

- **JsonDoclet** — A JDK doclet that reads Java source code via the `jdk.javadoc.doclet` API and produces structured
  JSON output with every Javadoc element (types, fields, methods, constructors, annotations, block tags, etc.).
- **MCP Server** — A Spring AI MCP server that exposes the documentation to AI models (currently a stub; tools are not
  yet implemented).

## Architecture

```
com.purrbyte.ai.doclet
├── JsonDoclet          # Main doclet — orchestrates option parsing, element iteration, JSON writing
├── TypeJsonBuilder     # Converts javax.lang.model elements into JSON nodes
├── DocTreeJson         # Serializes Javadoc comment trees (com.sun.source.doctree) into structured JSON and plain text
└── ChunkWriter         # Writes JSONL chunks (splits oversized text)
```

**Key dependencies:**

- **Jackson 3** (`tools.jackson.*`) — JSON serialization; requires JDK 17+
- **Spring AI MCP Server** — Model Context Protocol server (stub)
- **Lombok** — Boilerplate reduction

## Building

```bash
mvn clean package
```

The build produces a Spring Boot fat JAR in `target/` and a doclet JAR in `doclet/`. The doclet is invoked through the
standard javadoc CLI:

```bash
javadoc \
  -docletpath doclet/JAIDoc-doclet.jar \
  -doclet com.purrbyte.ai.doclet.JsonDoclet \
  -d output-json --pretty \
  -sourcepath src -subpackages com.mycompany
```

### Doclet-specific options

| Option                     | Argument count | Default              | Description                                          |
|----------------------------|----------------|----------------------|------------------------------------------------------|
| `-d`, `--output-directory` | 1              | `json-doclet-out`    | Output directory                                     |
| `--doc-version <v>`        | 1              | —                    | Version recorded as `version` in `index.json`        |
| `--pretty`                 | 0              | —                    | Format JSON with indentation                         |
| `--no-chunks`              | 0              | —                    | Do not generate chunks.jsonl                         |
| `--chunks-file <path>`     | 1              | `<out>/chunks.jsonl` | Path of the JSONL chunks file                        |
| `--max-chunk-chars <n>`    | 1              | 4000                 | Maximum chunk size in characters before splitting    |
| `--chunk-overlap <n>`      | 1              | 200                  | Overlap between fragments when a chunk is split      |
| `--only-documented`        | 0              | —                    | Emit chunks only for elements with a Javadoc comment |

Standard javadoc options (`-doctitle`, `-windowtitle`, `-charset`, `-link`, `-header`, `-footer`, `-notimestamp`) are
accepted and silently ignored so the doclet doesn't break integrations with Maven/Gradle.

### Documenting Java 8 sources

There is a bug in the JDK javadoc tool (verified on JDK 21): `--release 8` (or `-source 8`) combined with `-subpackages`
throws an `AssertionError` from the module system. Workarounds:

**Option (a): Explicit file list via `@argfile`** — works with `--release 8`:

```bash
find src -name '*.java' > sources.txt
javadoc --release 8 -docletpath doclet/JAIDoc-doclet.jar \
        -doclet com.purrbyte.ai.doclet.JsonDoclet -d output-json @sources.txt
```

**Option (b): Use `--release 11` with `-subpackages`** — if the code compiles as Java 11+:

```bash
javadoc --release 11 -docletpath doclet/JAIDoc-doclet.jar \
        -doclet com.purrbyte.ai.doclet.JsonDoclet \
        -d output-json -sourcepath src -subpackages com.mycompany
```

For Java 9–27 sources with modules, `-subpackages` works normally.

## Output structure

A single giant JSON is a bad idea: hard to version, hard to load partially, hard to chunk for embeddings. The chosen
structure:

```
output-json/
├── index.json                          # Manifest: generator, counts, paths
├── chunks.jsonl                        # 1 JSON line per element (splits oversized text)
├── module-<name>.json                  # Module documentation (if any)
└── api/
    └── com/mycompany/
        ├── package-info.json           # Package documentation
        ├── Account.json                # 1 file per top-level type
        └── Order.json                  #   (nested types go inside their parent)
```

### index.json — the manifest

```json
{
  "generator": "json-doclet 1.0.0",
  "version": "25.0.3",
  "generatedAt": "2026-06-10T04:19:29Z",
  "javaRuntime": "21.0.11",
  "modules": [],
  "packages": [
    {
      "name": "com.example.bank",
      "file": "api/com/example/bank/package-info.json"
    }
  ],
  "types": [
    {
      "qualifiedName": "com.example.bank.Account",
      "kind": "CLASS",
      "package": "com.example.bank",
      "file": "api/com/example/bank/Account.json"
    }
  ],
  "typeCount": 3,
  "chunksFile": "chunks.jsonl",
  "chunkCount": 29
}
```

### Type JSON — what each `<Type>.json` contains

Each type file contains the full parsed structure: `kind`, names, modifiers, annotations with values, `typeParameters`
with bounds and descriptions, superclass, interfaces, `permittedSubclasses` (sealed), `recordComponents` (description
taken from the record's `@param`), `enumConstants`, `fields` (with `constantValue`), `constructors`, `methods` (
parameters paired with their `@param`, `@throws` paired with exception types, `returnDescription`, `defaultValue` on
`@interface`, varargs), recursive `nestedTypes`, deprecation (`{since, forRemoval, description}`),`source {file, line}`,
and the `doc` block with:

- `firstSentence` — first sentence of the Javadoc (plain text)
- `bodyText` — full body as plain text (HTML converted, entities decoded)
- `body` — structured list of nodes `{kind: TEXT|CODE|LINK|ENTITY|START_ELEMENT|...}`
- `blockTags` — all block tags (`PARAM`, `RETURN`, `THROWS`, `SEE`, `SINCE`, `AUTHOR`, `VERSION`, `DEPRECATED`,
  `SERIAL*`, `VALUE`, `INDEX`, `SUMMARY`, `SYSTEM_PROPERTY`, `HIDDEN`, `PROVIDES`, `USES`, and custom tags like
  `UNKNOWN_BLOCK_TAG`)

### Example: type header (generic class)

```json
{
  "kind": "CLASS",
  "name": "Account",
  "qualifiedName": "com.example.bank.Account",
  "package": "com.example.bank",
  "modifiers": [
    "public"
  ],
  "typeParameters": [
    {
      "name": "T",
      "bounds": [
        "java.lang.Comparable<T>"
      ]
    }
  ],
  "superclass": "java.lang.Object",
  "source": {
    "file": "src/com/example/bank/Account.java",
    "line": 27
  },
  "doc": {
    "firstSentence": "Represents a bank account with balance in cents.",
    "bodyText": "Represents a bank account with balance in cents.\n\nUsage example:\n\nAccount c = new Account(\"123\", 1000);\nc.deposit(500);\n\nSupports deposits\nSupports withdrawals & transfers",
    "body": [
      {
        "kind": "TEXT",
        "text": "Represents a bank account with balance in "
      },
      {
        "kind": "CODE",
        "body": "cents"
      },
      {
        "kind": "TEXT",
        "text": ".\n\n "
      }
    ],
    "blockTags": [
      {
        "kind": "PARAM",
        "isTypeParameter": true,
        "name": "T",
        "description": "the identifier type parameter"
      },
      {
        "kind": "AUTHOR",
        "name": "Bank Team"
      },
      {
        "kind": "VERSION",
        "body": "2.1"
      },
      {
        "kind": "SINCE",
        "body": "1.0"
      }
    ]
  }
}
```

### Example: method (varargs, deprecation, @throws)

```json
{
  "kind": "METHOD",
  "name": "deposit",
  "modifiers": [
    "public"
  ],
  "annotations": [
    {
      "type": "java.lang.Deprecated",
      "values": {
        "since": "\"2.0\"",
        "forRemoval": "true"
      }
    }
  ],
  "returnType": "long",
  "parameters": [
    {
      "name": "montos",
      "type": "long[]",
      "varargs": true,
      "description": "amounts to deposit (varargs)"
    }
  ],
  "signature": "deposit(long[])",
  "deprecated": {
    "isDeprecated": true,
    "since": "2.0",
    "forRemoval": true,
    "description": "use #depositSafe(long) since version 2.0"
  },
  "source": {
    "file": "src/com/example/bank/Account.java",
    "line": 56
  },
  "doc": {
    "firstSentence": "Deposits amounts into the account.",
    "body": [
      {
        "kind": "TEXT",
        "text": "Deposits amounts into the account. ... with a "
      },
      {
        "kind": "LINK",
        "reference": "java.util.List",
        "label": "labeled link"
      },
      {
        "kind": "TEXT",
        "text": "."
      }
    ],
    "blockTags": [
      {
        "kind": "PARAM",
        "isTypeParameter": false,
        "name": "montos",
        "description": "amounts to deposit (varargs)"
      },
      {
        "kind": "RETURN",
        "description": "the new balance"
      },
      {
        "kind": "THROWS",
        "exception": "IllegalStateException",
        "description": "if the account is closed"
      },
      {
        "kind": "DEPRECATED",
        "body": "use #depositSafe(long) since version 2.0"
      },
      {
        "kind": "SINCE",
        "body": "1.2"
      }
    ]
  }
}
```

### Example: constant field and record components

```json
{
  "kind": "FIELD",
  "name": "SALDO_MAXIMO",
  "type": "long",
  "modifiers": [
    "public",
    "static",
    "final"
  ],
  "constantValue": "1000000000",
  "source": {
    "file": "src/com/example/bank/Account.java",
    "line": 30
  },
  "doc": {
    "firstSentence": "Maximum allowed balance, in cents."
  }
}
```

```json
"recordComponents": [
{
"name": "code",
"type": "java.lang.String",
"description": "ISO 4217 code, e.g. \"USD\""
},
{
"name": "decimals",
"type": "int",
"description": "number of decimal places"
}
]
```

### chunks.jsonl — JSONL chunk file

One JSON line per documented element, splitting oversized text at paragraph/line boundaries:

```json
{
  "id": "com.example.bank.Account#deposit(long[])",
  "text": "method com.example.bank.Account#deposit(long[]) -> long\n\nDeposits amounts into the account. The first sentence ends here. This is\nthe second sentence with a labeled link.\n\n@param montos amounts to deposit (varargs)\n@return the new balance\n@throws IllegalStateException if the account is closed\n@deprecated use #depositSafe(long) since version 2.0\n@since 1.2",
  "metadata": {
    "kind": "METHOD",
    "documented": true,
    "type": "com.example.bank.Account",
    "package": "com.example.bank",
    "member": "deposit",
    "signature": "deposit(long[])",
    "deprecated": true,
    "since": "1.2",
    "file": "src/com/example/bank/Account.java",
    "line": 56
  }
}
```

When the text exceeds `--max-chunk-chars`, the `ChunkWriter` splits it into overlapping fragments at paragraph/line
boundaries.

## Chunk metadata fields (optional)

The `ChunkWriter` emits optional metadata for each chunk. These fields are useful when chunks are used for downstream
processing:

| Field        | Description                               |
|--------------|-------------------------------------------|
| `kind`       | Element kind: CLASS, METHOD, FIELD, etc.  |
| `documented` | Whether the element has a Javadoc comment |
| `type`       | Qualified type name                       |
| `package`    | Package name                              |
| `member`     | Member name (field, method, constructor)  |
| `signature`  | Method/constructor signature              |
| `deprecated` | Whether the element is deprecated         |
| `since`      | `@since` value if present                 |
| `file`       | Source file path                          |
| `line`       | Source line number                        |

## Compatibility

- Uses the modern `jdk.javadoc.doclet` API (JDK 9+). The old `com.sun.javadoc` API was removed in JDK 13.
- Jackson 3 (`tools.jackson.*`) requires Java 17, so the doclet must run on JDK 17 or higher (17, 21, 25, 27…).
- To document source code from Java 8 to 27, pass `--release N` (or `-source N`) to the javadoc tool from within the
  application.
