# MCP Server

## Setup

The app uses Spring AI MCP Server with **streamable** protocol over **stdio**:

```yaml
spring:
  ai:
    mcp:
      server:
        enabled: true
        name: JaiDoc
        protocol: streamable
        stdio: true
```

Tools are not yet implemented — the MCP component is a stub.

## JetBrains MCP Adapter

The IDE connects via `.mcp.json` which proxies the MCP stdio server through
`@pyroprompts/mcp-stdio-to-streamable-http-adapter`:

```json
{
  "mcpServers": {
    "jetbrains": {
      "command": "npx.cmd",
      "args": [
        "@pyroprompts/mcp-stdio-to-streamable-http-adapter"
      ],
      "env": {
        "URI": "http://127.0.0.1:64960/stream"
      }
    }
  }
}
```

The MCP server must be running on `127.0.0.1:64960` for the adapter to connect.

### JetBrains Tools

Tools allow the AI model to execute actions on the host machine:

| Tool                         | Description                                                         | Parameters                                                                                                                                            |
|------------------------------|---------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `read_file`                  | Read file content from the filesystem                               | `path` (string) — absolute path to the file                                                                                                           |
| `write_file`                 | Write content to a file, creating parent directories if needed      | `path` (string) — absolute path, `content` (string) — file content                                                                                    |
| `edit_file`                  | Edit a file by searching and replacing text                         | `path` (string) — absolute path, `old_string` (string) — text to find, `new_string` (string) — replacement text                                       |
| `create_file`                | Create a new file with given content                                | `path` (string) — absolute path, `content` (string) — file content                                                                                    |
| `delete_file`                | Delete a file from the filesystem                                   | `path` (string) — absolute path                                                                                                                       |
| `list_directory`             | List directory contents in tree format                              | `path` (string) — absolute path to directory                                                                                                          |
| `search_in_files`            | Search for text within project files using IntelliJ's search engine | `searchText` (string) — substring to find, `directoryToSearch` (string, optional), `fileMask` (string, optional), `caseSensitive` (boolean, optional) |
| `search_regex`               | Search for regex matches within project files                       | `q` (string) — regex pattern, `paths` (string array, optional) — glob patterns to filter results                                                      |
| `search_symbol`              | Semantic lookup of symbols by identifier fragments                  | `q` (string) — symbol query text, `paths` (string array, optional), `include_external` (boolean, optional)                                            |
| `execute_command`            | Execute a shell command in the IDE's terminal                       | `command` (string) — command to run, `timeout` (number, optional), `maxLinesCount` (number, optional)                                                 |
| `git_add`                    | Add files to git staging area                                       | `paths` (string array) — files to add                                                                                                                 |
| `git_commit`                 | Commit staged changes with a message                                | `message` (string) — commit message                                                                                                                   |
| `git_status`                 | Check git status (working tree state)                               | *none*                                                                                                                                                |
| `git_diff`                   | Show git diff (unstaged changes)                                    | `path` (string, optional) — file to diff                                                                                                              |
| `git_log`                    | View git log with filter options                                    | `path` (string, optional), `maxCount` (number, optional), `since` (string, optional)                                                                  |
| `find_files_by_glob`         | Search for files matching a glob pattern                            | `globPattern` (string) — glob pattern, `subDirectoryRelativePath` (string, optional)                                                                  |
| `find_files_by_name_keyword` | Search for files by name keyword (case-insensitive)                 | `nameKeyword` (string) — keyword to search                                                                                                            |
| `reformat_file`              | Reformat a file using the IDE's code formatter                      | `path` (string) — absolute path to the file                                                                                                           |
| `get_file_problems`          | Analyze a file for errors and warnings using IntelliJ's inspections | `filePath` (string) — path relative to project root, `errorsOnly` (boolean, optional)                                                                 |
| `rename_refactoring`         | Rename a symbol throughout the project (intelligent rename)         | `pathInProject` (string), `symbolName` (string), `newName` (string)                                                                                   |
| `get_symbol_info`            | Get detailed information about a symbol at a specific position      | `filePath` (string), `line` (number), `column` (number)                                                                                               |
| `build_project`              | Build the project or compile specific files                         | `filesToRebuild` (string array, optional), `timeout` (number, optional)                                                                               |
| `debug_run`                  | Debug a run configuration or executable code location               | `configurationName` (string, optional), `filePath` (string, optional), `line` (number, optional)                                                      |

### JetBrains Resources

Resources allow the AI model to read structured data from the IDE:

| Resource             | Description                                           | URI Pattern                                                                     |
|----------------------|-------------------------------------------------------|---------------------------------------------------------------------------------|
| File contents        | Read any file in the project or dependencies          | `file://<path>`                                                                 |
| Open editors         | List of currently open editor files                   | `editor://open`                                                                 |
| Project modules      | List of project modules and their types               | `project://modules`                                                             |
| Project dependencies | List of project dependencies                          | `project://dependencies`                                                        |
| Database connections | List of configured database connections               | `database://connections`                                                        |
| Database schemas     | List of schemas in a connection                       | `database://schemas?connectionId=<id>`                                          |
| Schema objects       | List of objects within a schema                       | `database://objects?connectionId=<id>&schemaName=<name>`                        |
| Object descriptions  | Structure of a database object (columns, types, keys) | `database://object?connectionId=<id>&schemaName=<name>&kind=<kind>&name=<name>` |
| Table data           | Preview table data in CSV format                      | `database://table-data?connectionId=<id>&schemaName=<name>&tableName=<name>`    |
| SQL queries          | List recent SQL queries                               | `database://queries?connectionId=<id>`                                          |
| Run configurations   | List of project run configurations                    | `project://run-configs`                                                         |
| Debugger status      | Current debugger session state                        | `debugger://status`                                                             |

**Note:** This MCP server does not expose prompts or logging endpoints.
Prompts and skills (like `code-review`, `security-review`) are **Claude Code skills**,
not MCP resources — they run inside Claude Code, not on the MCP server.
