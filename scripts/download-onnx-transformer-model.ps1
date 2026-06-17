<#
.SYNOPSIS
    Downloads the ONNX transformer model and tokenizer files for JAIDoc.

.DESCRIPTION
    Downloads the model and tokenizer files from HuggingFace into the onnx/ directory.
    The model is not tracked in Git — you must run this script before starting the app.

    If no model or variant is specified, an interactive menu is shown to choose which model
    and variant to download.

.PARAMETER Model
    The model to download (e.g. multilingual-e5-small).
    Available: multilingual-e5-small, multilingual-e5-base, paraphrase-multilingual-MiniLM-L12-v2,
               all-MiniLM-L12-v2, all-MiniLM-L6-v2

.PARAMETER Variant
    The ONNX variant to download (e.g. model_qint8_avx512_vnni).
    Available: model_qint8_avx512_vnni, model_O4, model

.EXAMPLE
    .\scripts\download-onnx-transformer-model.ps1
    .\scripts\download-onnx-transformer-model.ps1 -Model multilingual-e5-small -Variant model_qint8_avx512_vnni
#>
param(
    [string]$Model,
    [string]$Variant
)

$HuggingFaceBase = "https://huggingface.co"

$Models = @(
    @{ Name = "multilingual-e5-small";     HF = "intfloat/multilingual-e5-small";              Desc = "INT8, AVX-512+VNNI — best quality/speed, 50+ langs" },
    @{ Name = "multilingual-e5-base";      HF = "intfloat/multilingual-e5-base";                 Desc = "INT8, AVX-512+VNNI — larger model, 100+ langs" },
    @{ Name = "paraphrase-multilingual-MiniLM-L12-v2"; HF = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"; Desc = "INT8, AVX-512+VNNI — multilingual MiniLM" },
    @{ Name = "all-MiniLM-L12-v2";         HF = "sentence-transformers/all-MiniLM-L12-v2";       Desc = "INT8, AVX-512+VNNI — fast English-only" },
    @{ Name = "all-MiniLM-L6-v2";          HF = "sentence-transformers/all-MiniLM-L6-v2";        Desc = "INT8, AVX-512+VNNI — smallest, fastest, English-only" }
)

$Variants = @(
    @{ Name = "model_qint8_avx512_vnni"; Size = "~113 MB"; Desc = "INT8, AVX-512 + VNNI — fastest on modern Intel/AMD" },
    @{ Name = "model_O4";                Size = "~224 MB"; Desc = "OpenVINO optimized variant" },
    @{ Name = "model";                   Size = "~449 MB"; Desc = "FP16 base — largest file, slowest inference" }
)

$ScriptDir = $PSScriptRoot
$OnnxDir = Join-Path $ScriptDir "..\onnx"

# Create onnx directory if it doesn't exist
if (-not (Test-Path $OnnxDir)) {
    Write-Host "Creating directory: $OnnxDir" -ForegroundColor Cyan
    New-Item -ItemType Directory -Path $OnnxDir | Out-Null
}

# Show interactive menu if no model or variant specified
if ([string]::IsNullOrEmpty($Model) -or [string]::IsNullOrEmpty($Variant)) {
    # Select model
    if ([string]::IsNullOrEmpty($Model)) {
        Write-Host "Available ONNX embedding models:" -ForegroundColor Cyan
        Write-Host ""
        for ($i = 0; $i -lt $Models.Count; $i++) {
            Write-Host "  [$(( $i + 1 ))] $($Models[$i].Name) — $($Models[$i].Desc)" -ForegroundColor Yellow
        }
        Write-Host ""
        $Choice = Read-Host "Select model (1-5)"

        if ($Choice -notmatch '^\d+$' -or $Choice -lt 1 -or $Choice -gt $Models.Count) {
            Write-Host "Invalid selection." -ForegroundColor Red
            exit 1
        }

        $Model = $Models[[int]$Choice - 1].Name
    }

    # Select variant
    if ([string]::IsNullOrEmpty($Variant)) {
        Write-Host ""
        Write-Host "Available ONNX variants for $Model:" -ForegroundColor Cyan
        Write-Host ""
        for ($i = 0; $i -lt $Variants.Count; $i++) {
            Write-Host "  [$(( $i + 1 ))] $($Variants[$i].Name) — $($Variants[$i].Size) — $($Variants[$i].Desc)" -ForegroundColor Yellow
        }
        Write-Host ""
        $Choice = Read-Host "Select variant (1-3)"

        if ($Choice -notmatch '^\d+$' -or $Choice -lt 1 -or $Choice -gt $Variants.Count) {
            Write-Host "Invalid selection." -ForegroundColor Red
            exit 1
        }

        $Variant = $Variants[[int]$Choice - 1].Name
    }
}

$HfRepo = ($Models | Where-Object { $_.Name -eq $Model }).HF
$ModelFilename = "$Variant.onnx"
$TokenizerFilename = "tokenizer.json"

Write-Host ""
Write-Host "Downloading ONNX transformer model for JAIDoc..." -ForegroundColor Cyan
Write-Host "  Model   : $Model" -ForegroundColor Yellow
Write-Host "  Variant : $Variant" -ForegroundColor Yellow
Write-Host "  Output  : $OnnxDir" -ForegroundColor Yellow
Write-Host ""

# Download model file
$ModelUrl = "$HuggingFaceBase/$HfRepo/resolve/main/onnx/$ModelFilename?download=true"
Write-Host "Downloading model..." -ForegroundColor Green
try {
    Invoke-WebRequest -Uri $ModelUrl -OutFile (Join-Path $OnnxDir $ModelFilename) -UseBasicParsing
    Write-Host "  OK: $ModelFilename" -ForegroundColor Green
} catch {
    Write-Host "  FAILED: $ModelFilename - $_" -ForegroundColor Red
    exit 1
}

# Download tokenizer file
$TokenizerUrl = "$HuggingFaceBase/$HfRepo/resolve/main/onnx/$TokenizerFilename?download=true"
Write-Host "Downloading tokenizer..." -ForegroundColor Green
try {
    Invoke-WebRequest -Uri $TokenizerUrl -OutFile (Join-Path $OnnxDir $TokenizerFilename) -UseBasicParsing
    Write-Host "  OK: $TokenizerFilename" -ForegroundColor Green
} catch {
    Write-Host "  FAILED: $TokenizerFilename - $_" -ForegroundColor Red
    exit 1
}

# Show file sizes
$ModelSize = (Get-Item (Join-Path $OnnxDir $ModelFilename)).Length
$TokenizerSize = (Get-Item (Join-Path $OnnxDir $TokenizerFilename)).Length
Write-Host ""
Write-Host "Download complete:" -ForegroundColor Green
Write-Host "  $ModelFilename  - $(('{0:N1} MB' -f ($ModelSize / 1MB)))" -ForegroundColor Green
Write-Host "  $TokenizerFilename  - $(('{0:N1} MB' -f ($TokenizerSize / 1MB)))" -ForegroundColor Green
Write-Host ""
Write-Host "To use a different model or variant, set the AI_TRANSFORMER_ONNX environment variable:" -ForegroundColor Cyan
Write-Host "  e.g. export AI_TRANSFORMER_ONNX=./onnx/model.onnx" -ForegroundColor Yellow
Write-Host ""
Write-Host "You can now start the application." -ForegroundColor Cyan