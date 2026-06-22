#!/bin/bash
# Validate that the feature workspace is properly structured.
# Usage: ./scripts/validate.sh <feature-directory>

set -euo pipefail

FEATURE_DIR="${1:?Usage: $0 <feature-directory>}"

if [ ! -d "$FEATURE_DIR" ]; then
    echo "Error: directory '$FEATURE_DIR' does not exist"
    exit 1
fi

# Check required files
REQUIRED_FILES=("README.md" "data-flow.md" "mock-requests.json")
for file in "${REQUIRED_FILES[@]}"; do
    if [ ! -f "$FEATURE_DIR/$file" ]; then
        echo "Error: missing required file '$file'"
        exit 1
    fi
done

# Check README.md has YAML frontmatter
if ! head -3 "$FEATURE_DIR/README.md" | grep -q "^---$"; then
    echo "Error: README.md must have YAML frontmatter"
    exit 1
fi

echo "Validation passed for $FEATURE_DIR"
