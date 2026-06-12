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

## JetBrains Adapter

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
