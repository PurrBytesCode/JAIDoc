# Transformer Model — ONNX Embedding

This directory holds the ONNX model and tokenizer files used by Spring AI's Transformer embedding for semantic search.
The model is **not** tracked in Git (see `.gitignore`), so you must download it before the application can run.

## Model Selection

| Variant                        | Size    | Description                                                                        |
|--------------------------------|---------|------------------------------------------------------------------------------------|
| `model_qint8_avx512_vnni.onnx` | ~113 MB | Quantized INT8, AVX-512 + VNNI — fastest on modern Intel (Ice Lake+) or AMD Zen 3+ |
| `model_O4.onnx`                | ~224 MB | OpenVINO optimized variant                                                         |
| `model.onnx`                   | ~449 MB | FP16 base model — largest file, slowest inference                                  |

The application defaults to `model_qint8_avx512_vnni.onnx` (the fastest). Override the model with the
`AI_TRANSFORMER_ONNX` environment variable.

## Configuration

The model is configured via [application.yaml](../src/main/resources/application.yaml):

```yaml
spring:
  ai:
    embedding:
      transformer:
        onnx:
          model-uri: ${AI_TRANSFORMER_ONNX:./onnx/model_qint8_avx512_vnni.onnx}
        tokenizer:
          uri: ${AI_TRANSFORMER_TOKENIZER:./onnx/tokenizer.json}
```

Override the model path by setting the `AI_TRANSFORMER_ONNX` environment variable:

```bash
# Use the FP16 base model (largest, slowest)
export AI_TRANSFORMER_ONNX=./onnx/model.onnx
```

> **Note:** Spring AI's `ModelUri` requires a `file:` URI scheme for absolute paths. Bare Windows paths like
> `C:\...\onnx\tokenizer.json` will not work. See [URI Scheme Requirement](#uri-scheme-requirement) for details.

## Alternative Models

You can swap the model for a different one — just download the files and set both `AI_TRANSFORMER_ONNX` and
`AI_TRANSFORMER_TOKENIZER` to point to the correct paths. Different models have different tokenizers, so you must
update both.

## URI Scheme Requirement

Spring AI's `ModelUri` does **not** accept bare Windows file paths. A path like
`C:\Users\aluis\...\onnx\tokenizer.json` will fail because Spring AI tries to parse it as an HTTP URL.

You must use a `file:` URI scheme:

- **Relative paths (default)** — no environment variables needed, just run the app from the project root:
  ```yaml
  spring:
    ai:
      embedding:
        transformer:
          onnx:
            model-uri: ./onnx/model_qint8_avx512_vnni.onnx
          tokenizer:
            uri: ./onnx/tokenizer.json
  ```

- **Absolute paths via environment variables** — use `file:///` with forward slashes:
  ```bash
  # Windows (PowerShell)
  $env:AI_TRANSFORMER_ONNX = "file:///C:/Users/aluis/IdeaProjects/JAIDoc/onnx/model_qint8_avx512_vnni.onnx"
  $env:AI_TRANSFORMER_TOKENIZER = "file:///C:/Users/aluis/IdeaProjects/JAIDoc/onnx/tokenizer.json"
  ```

- **Absolute paths in the YAML** — same `file:///` syntax with forward slashes:
  ```yaml
  spring:
    ai:
      embedding:
        transformer:
          onnx:
            model-uri: file:///C:/Users/aluis/IdeaProjects/JAIDoc/onnx/model_qint8_avx512_vnni.onnx
          tokenizer:
            uri: file:///C:/Users/aluis/IdeaProjects/JAIDoc/onnx/tokenizer.json
  ```

**Key rules:**

- Always prefix with `file://` (three slashes total for absolute paths)
- Use **forward slashes** (`/`), not Windows backslashes (`\`)
- Relative paths (`./onnx/...`) work without any scheme — just run the app from the project root

| Model                                     | Quantized ONNX | Tokenizer | Embedding Dim | Languages |
|-------------------------------------------|:--------------:|-----------|:-------------:|-----------|
| **multilingual-e5-small**                 |    ~113 MB     | ~16 MB    |      384      | 50+       |
| **multilingual-e5-base**                  |    ~113 MB     | ~22 MB    |      768      | 100+      |
| **paraphrase-multilingual-MiniLM-L12-v2** |    ~113 MB     | ~22 MB    |      384      | 50+       |
| **all-MiniLM-L12-v2**                     |     ~33 MB     | ~22 MB    |      384      | English   |
| **all-MiniLM-L6-v2**                      |     ~23 MB     | ~22 MB    |      384      | English   |

### multilingual-e5-small (default)

- [intfloat/multilingual-e5-small](https://huggingface.co/intfloat/multilingual-e5-small)
- Best quality/speed tradeoff, supports 50+ languages

### multilingual-e5-base

- [intfloat/multilingual-e5-base](https://huggingface.co/intfloat/multilingual-e5-base)
- Larger model with better quality and wider language support (100+), but higher memory usage

### paraphrase-multilingual-MiniLM-L12-v2

- [sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2](https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2)
- Multilingual version of MiniLM, good for general-purpose English + multilingual embeddings

### all-MiniLM-L12-v2

- [sentence-transformers/all-MiniLM-L12-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L12-v2)
- Fast, small English-only model — ideal for English-only workflows with lower resource usage

### all-MiniLM-L6-v2

- [sentence-transformers/all-MiniLM-L6-v2](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2)
- Even smaller than L12, fastest inference, English-only

## Download

Run the appropriate script for your platform — it will ask which variant you want:

### PowerShell (Windows)

```powershell
# Download with defaults (project onnx/ directory)
.\scripts\download-onnx-transformer-model.ps1

# Download to a custom directory
.\scripts\download-onnx-transformer-model.ps1 -Model multilingual-e5-small -Variant model_qint8_avx512_vnni -OutputDir .\data\models\onnx

# Override the output path for the current run (e.g., when the working dir is target/)
.\scripts\download-onnx-transformer-model.ps1 -Model multilingual-e5-small -Variant model_qint8_avx512_vnni -OutputDir ..\onnx
```

### Bash (Linux / macOS)

```bash
# Download with defaults (project onnx/ directory)
./scripts/download-onnx-transformer-model.sh

# Download to a custom directory
./scripts/download-onnx-transformer-model.sh multilingual-e5-small model_qint8_avx512_vnni ./data/models/onnx

# Override the output path for the current run (e.g., when the working dir is target/)
./scripts/download-onnx-transformer-model.sh multilingual-e5-small model_qint8_avx512_vnni ../onnx
```

## Model Source

- **Model**: [intfloat/multilingual-e5-small](https://huggingface.co/intfloat/multilingual-e5-small)
- **Task**: Text embeddings, multilingual (50+ languages)
- **Quantization**: INT8 for the `model_qint8_*` variants, FP16 for `model.onnx`