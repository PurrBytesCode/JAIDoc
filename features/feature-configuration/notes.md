# Notes

## Observations

- The configuration has 9 YAML files — this is a good balance between modularity and complexity.
- Most configuration values have environment variable overrides with the pattern `${VAR:default}` — this enables
  deployment flexibility without modifying the YAML files.
- The ONNX model URI must use the `file:` scheme — bare Windows paths like `C:\...\onnx\model.onnx` will not work
  because Spring AI tries to parse them as HTTP URLs.

## Decisions

- Use environment variable overrides instead of separate config files for different environments (dev, staging, prod) —
  this is simpler and more flexible.
- The SQLite password has no default — it must be provided via environment variable for production deployments.
- The `spring.threads.virtual.enabled=true` enables virtual threads for all async operations — this is a key performance
  optimization for I/O-bound workloads.
