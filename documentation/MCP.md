# MCP Server

## Setup

The app uses Spring AI MCP Server with **streamable** protocol:

```yaml
spring:
  ai:
    mcp:
      server:
        enabled: true
        name: JaiDoc
        protocol: streamable
```

The `JavaDocMCP` component exposes three tools for JDK Javadoc persistence and search:

#### JDK Javadoc Tools

| Tool | Description | Parameters |
|------|-------------|------------|
| `ingestVersion` | Ingest the generated Javadoc JSON of a JDK version into the searchable database | `version` (string) — JDK version, e.g. `25.0.3` |
| `listVersions` | List JDK versions whose documentation has been generated | *none* |
| `searchJavadoc` | Semantic search of the JDK Javadoc within a single version | `version` (string) — JDK version, `query` (string) — natural language query, `topK` (number) — max results |

The `McpToolsConfiguration` bean registers these tools via `MethodToolCallbackProvider`.

## JetBrains MCP Server

The project ships with a pre-configured `.mcp.json` for direct streamable-HTTP connection to JetBrains:

```json
{
  "mcpServers": {
    "JetBrains": {
      "type": "streamable-http",
      "url": "http://127.0.0.1:64960/stream",
      "headers": {}
    }
  }
}
```

This bypasses the stdio-to-HTTP adapter and connects directly to the JetBrains MCP Server plugin running on port `64960`.

### JetBrains Tools

Tools allow the AI model to execute actions on the host machine:

#### File Operations

| Tool                  | Description                                                                                                     | Parameters                                                                                                      |
|-----------------------|-----------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| `read_file`           | Read file content from the filesystem                                                                           | `file_path` (string) — absolute path to the file, `mode` (string, optional), `start_line` (number, optional), `max_lines` (number, optional)          |
| `write_file`          | Write content to a file, creating parent directories if needed                                                  | `file_path` (string) — absolute path, `content` (string) — file content                                                                       |
| `edit_file`           | Edit a file by searching and replacing text                                                                     | `path` (string) — absolute path, `old_string` (string) — text to find, `new_string` (string) — replacement text |
| `create_file`         | Create a new file with given content                                                                            | `path` (string) — absolute path, `content` (string) — file content                                              |
| `delete_file`         | Delete a file from the filesystem                                                                               | `path` (string) — absolute path                                                                                 |
| `list_directory`      | List directory contents in tree format                                                                          | `path` (string) — absolute path to directory                                                                    |
| `list_directory_tree` | Provides a tree representation of the specified directory in the pseudo graphic format like `tree` utility does | `directoryPath` (string) — directory path, `maxDepth` (number, optional), `timeout` (number, optional)          |
| `reformat_file`       | Reformat a file using the IDE's code formatter                                                                  | `path` (string) — absolute path to the file                                                                     |
| `open_file_in_editor` | Opens the specified file in the JetBrains IDE editor                                                            | `filePath` (string) — path relative to the project root                                                         |

#### Search Operations

| Tool                         | Description                                                                                  | Parameters                                                                                                                                                        |
|------------------------------|----------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `search_in_files_by_text`    | Searches for a text substring within all files in the project using IntelliJ's search engine | `searchText` (string) — text substring to search for, `directoryToSearch` (string, optional), `fileMask` (string, optional), `caseSensitive` (boolean, optional)  |
| `search_in_files_by_regex`   | Searches with a regex pattern within all files in the project using IntelliJ's search engine | `regexPattern` (string) — regex pattern to search for, `directoryToSearch` (string, optional), `fileMask` (string, optional), `caseSensitive` (boolean, optional) |
| `search_regex`               | Search for regex matches within project files                                                | `q` (string) — regex pattern, `paths` (string array, optional) — glob patterns to filter results                                                                  |
| `search_text`                | Searches for a text substring within project files                                           | `q` (string) — text to search for, `paths` (string array, optional) — glob patterns to filter results                                                             |
| `search_file`                | Searches for files by glob pattern within the project                                        | `q` (string) — glob pattern to search for, `paths` (string array, optional), `limit` (number, optional)                                                           |
| `find_files_by_glob`         | Search for files matching a glob pattern                                                     | `globPattern` (string) — glob pattern, `subDirectoryRelativePath` (string, optional)                                                                              |
| `find_files_by_name_keyword` | Search for files by name keyword (case-insensitive)                                          | `nameKeyword` (string) — keyword to search                                                                                                                        |
| `search_symbol`              | Semantic lookup of symbols by identifier fragments                                           | `q` (string) — symbol query text, `paths` (string array, optional), `include_external` (boolean, optional)                                                        |

#### Code Introspection

| Tool                               | Description                                                                         | Parameters                                                                                   |
|------------------------------------|-------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------|
| `get_symbol_info`                  | Get detailed information about a symbol at a specific position                      | `filePath` (string), `line` (number), `column` (number)                                      |
| `get_file_problems`                | Analyze a file for errors and warnings using IntelliJ's inspections                 | `filePath` (string) — path relative to project root, `errorsOnly` (boolean, optional)        |
| `generate_psi_tree`                | Creates a PSI tree for provided Java or Kotlin code and returns it as indented text | `code` (string) — source code snippet to parse, `language` (string) — 'Java' or 'Kotlin'     |
| `generate_inspection_kts_api`      | Returns the Inspection KTS API documentation for the target language                | `language` (string) — 'Java' or 'Kotlin'                                                     |
| `generate_inspection_kts_examples` | Returns example inspection.kts templates for the target language                    | `language` (string) — 'Java', 'Kotlin', or 'Any' (default)                                   |
| `run_inspection_kts`               | Compiles an inspection.kts script and runs it against a target file                 | `inspectionKtsCode` (string), `contextPath` (string), `targetFileContent` (string, optional) |
| `validate_inspection_kts`          | Validates an inspection.kts script against specification examples                   | `inspectionKtsCode` (string), `pathToSpecification` (string)                                 |

#### Rename and Refactoring

| Tool                 | Description                                                 | Parameters                                                          |
|----------------------|-------------------------------------------------------------|---------------------------------------------------------------------|
| `rename_refactoring` | Rename a symbol throughout the project (intelligent rename) | `pathInProject` (string), `symbolName` (string), `newName` (string) |

#### Project Structure

| Tool                       | Description                                                                             | Parameters |
|----------------------------|-----------------------------------------------------------------------------------------|------------|
| `get_repositories`         | Retrieves the list of VCS roots in the project                                          | *none*     |
| `get_project_modules`      | Get a list of all modules in the project with their types                               | *none*     |
| `get_project_dependencies` | Get a list of all dependencies defined in the project                                   | *none*     |
| `get_all_open_file_paths`  | Returns active editor's and other open editors' file paths relative to the project root | *none*     |

#### Build and Run

| Tool                        | Description                                                                                            | Parameters                                                                                                                                                                                                                            |
|-----------------------------|--------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `build_project`             | Build the project or compile specific files                                                            | `filesToRebuild` (string array, optional), `timeout` (number, optional), `projectPath` (string, optional)                                                                                                                             |
| `execute_run_configuration` | Run either an existing run configuration by name or a temporary run configuration from a code location | `configurationName` (string, optional), `filePath` (string, optional), `line` (number, optional), `timeout` (number, optional), `waitForExit` (boolean, optional)                                                                     |
| `execute_terminal_command`  | Executes a specified shell command in the IDE's integrated terminal                                    | `command` (string) — command to execute, `executeInShell` (boolean, optional), `reuseExistingTerminalWindow` (boolean, optional), `timeout` (number, optional), `maxLinesCount` (number, optional), `truncateMode` (string, optional) |
| `runNotebookCell`           | Execute one or all cells of a Jupyter notebook                                                         | `file_path` (string) — absolute path to the .ipynb notebook, `cell_id` (string, optional) — Jupyter cell ID, `projectPath` (string, optional)                                                                                         |

#### Database Operations

| Tool                              | Description                                                                                                                                   | Parameters                                                                                                                      |
|-----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `list_database_connections`       | Retrieves a list of configured database connections or data sources in the project                                                            | *none*                                                                                                                          |
| `test_database_connection`        | Checks whether a specific database connection is valid and reachable                                                                          | `id` (string) — unique ID of a database connection                                                                              |
| `list_database_schemas`           | Retrieves a list of database schemas in the specified database connection                                                                     | `connectionId` (string), `selectedOnly` (boolean)                                                                               |
| `list_schema_object_kinds`        | Retrieves supported schema object kinds (e.g., table, view, routine) for the given database connection                                        | `connectionId` (string)                                                                                                         |
| `list_schema_objects`             | Retrieves a list of database objects within the given schema                                                                                  | `connectionId` (string), `databaseName` (string), `schemaName` (string), `kind` (string, optional)                              |
| `get_database_object_description` | Retrieves the structure of a database object (columns, types, keys, indexes) within a particular schema as a hierarchical text representation | `connectionId` (string), `databaseName` (string), `schemaName` (string), `kind` (string), `objectName` (string)                 |
| `preview_table_data`              | Previews data of a table, view, materialized view or other table-like object using given database connection                                  | `connectionId` (string), `databaseName` (string), `schemaName` (string), `tableName` (string), `maxRowCount` (number, optional) |
| `execute_sql_query`               | Executes a SQL query against the given database connection                                                                                    | `connectionId` (string), `databaseName` (string), `schemaName` (string), `queryText` (string)                                   |
| `list_recent_sql_queries`         | Retrieves a list of recent (including currently running) queries for the given database connection                                            | `connectionId` (string)                                                                                                         |
| `cancel_sql_query`                | Cancels a running query using its unique ID                                                                                                   | `sessionId` (number) — unique ID of a query session                                                                             |

#### Debugger Operations

| Tool                            | Description                                                                                  | Parameters                                                                                                                                                                                        |
|---------------------------------|----------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `xdebug_control_session`        | Controls the execution of a debug session                                                    | `action` (string) — STEP_INTO, STEP_OVER, STEP_OUT, RESUME, PAUSE, STOP, WAIT_FOR_PAUSE, DRAIN_EVENTS, `sessionId` (string, optional), `timeout` (number, optional)                               |
| `xdebug_get_debugger_status`    | Returns the current status of the debugger including all active debug sessions               | *none*                                                                                                                                                                                            |
| `xdebug_start_debugger_session` | Start a debugger session for either an existing run configuration by name or a code location | `configurationName` (string, optional), `filePath` (string, optional), `line` (number, optional), `timeout` (number, optional)                                                                    |
| `xdebug_get_threads`            | Returns the list of threads in the debug session                                             | `sessionId` (string, optional)                                                                                                                                                                    |
| `xdebug_get_stack`              | Returns the call stack for a thread in the debug session                                     | `sessionId` (string, optional), `threadId` (string, optional), `limit` (number, optional), `offset` (number, optional)                                                                            |
| `xdebug_get_frame_values`       | Returns the values visible in the specified stack frame as a tree structure                  | `sessionId` (string, optional), `frameIndex` (number, optional), `depth` (number, optional)                                                                                                       |
| `xdebug_get_value_by_path`      | Gets the value of a nested object by following a path of property names                      | `sessionId` (string, optional), `frameIndex` (number, optional), `path` (string array)                                                                                                            |
| `xdebug_evaluate_expression`    | Evaluates an expression in the context of the current stack frame                            | `expression` (string), `sessionId` (string, optional), `frameIndex` (number, optional), `depth` (number, optional)                                                                                |
| `xdebug_set_variable`           | Mutates a variable value by path in the selected stack frame                                 | `path` (string array), `newValue` (string), `sessionId` (string, optional), `frameIndex` (number, optional)                                                                                       |
| `xdebug_set_breakpoint`         | Creates or updates a breakpoint                                                              | `breakpointId` (string, optional), `filePath` (string, optional), `line` (number, optional), `condition` (string, optional), `isLogMessage` (boolean, optional), `isLogStack` (boolean, optional) |
| `xdebug_list_breakpoints`       | Lists all breakpoints in the project or in a specific file                                   | `filePath` (string, optional)                                                                                                                                                                     |
| `xdebug_remove_breakpoint`      | Removes breakpoints filtered by owner and optional selectors                                 | `breakpointId` (string, optional), `filePath` (string, optional), `line` (number, optional), `owner` (string, optional)                                                                           |
| `xdebug_run_to_line`            | Resumes execution to a target line                                                           | `filePath` (string), `line` (number), `sessionId` (string, optional), `timeout` (number, optional)                                                                                                |

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
