#!/usr/bin/env bash
#
# Downloads the ONNX transformer model and tokenizer files for JAIDoc.
#
# The model is not tracked in Git — you must run this script before starting the app.
#
# If no model or variant is specified, an interactive menu is shown to choose which model
# and variant to download.
#
# Usage:
#   ./scripts/download-onnx-transformer-model.sh [model] [variant]
#
# Available models:
#   multilingual-e5-small                 (~113 MB, INT8, AVX-512+VNNI — best quality/speed, 50+ langs)
#   multilingual-e5-base                  (~113 MB, INT8, AVX-512+VNNI — larger model, 100+ langs)
#   paraphrase-multilingual-MiniLM-L12-v2 (~113 MB, INT8, AVX-512+VNNI — multilingual MiniLM)
#   all-MiniLM-L12-v2                     (~33 MB, INT8, AVX-512+VNNI — fast English-only)
#   all-MiniLM-L6-v2                      (~23 MB, INT8, AVX-512+VNNI — smallest, fastest, English-only)
#
# Available variants:
#   model_qint8_avx512_vnni   (~113 MB, INT8, AVX-512 + VNNI — fastest on modern Intel/AMD)
#   model_O4                  (~224 MB, OpenVINO optimized variant)
#   model                     (~449 MB, FP16 base — largest file, slowest inference)
#
# Example:
#   ./scripts/download-onnx-transformer-model.sh
#   ./scripts/download-onnx-transformer-model.sh multilingual-e5-small model_qint8_avx512_vnni

set -euo pipefail

HUGGINGFACE_BASE="https://huggingface.co"
TOKENIZER_FILENAME="tokenizer.json"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ONNX_DIR="${SCRIPT_DIR}/../onnx"

# Create onnx directory if it doesn't exist
if [ ! -d "${ONNX_DIR}" ]; then
    echo "Creating directory: ${ONNX_DIR}"
    mkdir -p "${ONNX_DIR}"
fi

# Select model
if [ -z "${1:-}" ]; then
    echo "Available ONNX embedding models:"
    echo
    echo "  [1] multilingual-e5-small                 — ~113 MB — INT8, AVX-512+VNNI — best quality/speed, 50+ langs"
    echo "  [2] multilingual-e5-base                  — ~113 MB — INT8, AVX-512+VNNI — larger model, 100+ langs"
    echo "  [3] paraphrase-multilingual-MiniLM-L12-v2 — ~113 MB — INT8, AVX-512+VNNI — multilingual MiniLM"
    echo "  [4] all-MiniLM-L12-v2                     — ~33 MB  — INT8, AVX-512+VNNI — fast English-only"
    echo "  [5] all-MiniLM-L6-v2                      — ~23 MB  — INT8, AVX-512+VNNI — smallest, fastest, English-only"
    echo
    read -rp "Select model (1-5): " Choice

    case "$Choice" in
        1) MODEL="multilingual-e5-small";             HF="intfloat/multilingual-e5-small" ;;
        2) MODEL="multilingual-e5-base";              HF="intfloat/multilingual-e5-base" ;;
        3) MODEL="paraphrase-multilingual-MiniLM-L12-v2"; HF="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2" ;;
        4) MODEL="all-MiniLM-L12-v2";                 HF="sentence-transformers/all-MiniLM-L12-v2" ;;
        5) MODEL="all-MiniLM-L6-v2";                  HF="sentence-transformers/all-MiniLM-L6-v2" ;;
        *) echo "Invalid selection." >&2; exit 1 ;;
    esac
else
    MODEL="$1"
    case "$MODEL" in
        multilingual-e5-small)             HF="intfloat/multilingual-e5-small" ;;
        multilingual-e5-base)              HF="intfloat/multilingual-e5-base" ;;
        paraphrase-multilingual-MiniLM-L12-v2) HF="sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2" ;;
        all-MiniLM-L12-v2)                 HF="sentence-transformers/all-MiniLM-L12-v2" ;;
        all-MiniLM-L6-v2)                  HF="sentence-transformers/all-MiniLM-L6-v2" ;;
        *) echo "Unknown model: ${MODEL}" >&2; exit 1 ;;
    esac
fi

# Select variant
if [ -z "${2:-}" ]; then
    echo ""
    echo "Available ONNX variants for ${MODEL}:"
    echo
    echo "  [1] model_qint8_avx512_vnni — ~113 MB — INT8, AVX-512 + VNNI — fastest on modern Intel/AMD"
    echo "  [2] model_O4                — ~224 MB — OpenVINO optimized variant"
    echo "  [3] model                   — ~449 MB — FP16 base — largest file, slowest inference"
    echo
    read -rp "Select variant (1-3): " Choice

    case "$Choice" in
        1) VARIANT="model_qint8_avx512_vnni" ;;
        2) VARIANT="model_O4" ;;
        3) VARIANT="model" ;;
        *) echo "Invalid selection." >&2; exit 1 ;;
    esac
else
    VARIANT="$2"
fi

MODEL_FILENAME="${VARIANT}.onnx"

echo ""
echo "Downloading ONNX transformer model for JAIDoc..."
echo "  Model   : ${MODEL}"
echo "  Variant : ${VARIANT}"
echo "  Output  : ${ONNX_DIR}"
echo

# Download model file
MODEL_URL="${HUGGINGFACE_BASE}/${HF}/resolve/main/onnx/${MODEL_FILENAME}?download=true"
echo "Downloading model..."
if curl -fSL "${MODEL_URL}" -o "${ONNX_DIR}/${MODEL_FILENAME}"; then
    echo "  OK: ${MODEL_FILENAME}"
else
    echo "  FAILED: ${MODEL_FILENAME}" >&2
    exit 1
fi

# Download tokenizer file
TOKENIZER_URL="${HUGGINGFACE_BASE}/${HF}/resolve/main/onnx/${TOKENIZER_FILENAME}?download=true"
echo "Downloading tokenizer..."
if curl -fSL "${TOKENIZER_URL}" -o "${ONNX_DIR}/${TOKENIZER_FILENAME}"; then
    echo "  OK: ${TOKENIZER_FILENAME}"
else
    echo "  FAILED: ${TOKENIZER_FILENAME}" >&2
    exit 1
fi

# Show file sizes
MODEL_SIZE=$(stat -f%z "${ONNX_DIR}/${MODEL_FILENAME}" 2>/dev/null || stat -c%s "${ONNX_DIR}/${MODEL_FILENAME}" 2>/dev/null)
TOKENIZER_SIZE=$(stat -f%z "${ONNX_DIR}/${TOKENIZER_FILENAME}" 2>/dev/null || stat -c%s "${ONNX_DIR}/${TOKENIZER_FILENAME}" 2>/dev/null)

echo
echo "Download complete:"
echo "  ${MODEL_FILENAME}  - $(printf '%.1f MB' $(echo "scale=1; ${MODEL_SIZE} / 1048576" | bc))"
echo "  ${TOKENIZER_FILENAME}  - $(printf '%.1f MB' $(echo "scale=1; ${TOKENIZER_SIZE} / 1048576" | bc))"
echo
echo "To use a different model or variant, set the AI_TRANSFORMER_ONNX environment variable:"
echo "  e.g. export AI_TRANSFORMER_ONNX=./onnx/model.onnx"
echo
echo "You can now start the application."