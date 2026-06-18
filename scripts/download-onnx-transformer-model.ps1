<#
.SYNOPSIS
    Downloads the ONNX transformer model and tokenizer files for JAIDoc.

.DESCRIPTION
    Downloads the model and tokenizer files from HuggingFace into the onnx/ directory.
    The model is not tracked in Git - you must run this script before starting the app.

    If no model or variant is specified, an interactive menu is shown to choose which model
    and variant to download.

.PARAMETER Model
    The model to download (e.g. multilingual-e5-small).
    Available: multilingual-e5-small, multilingual-e5-base, paraphrase-multilingual-MiniLM-L12-v2,
               all-MiniLM-L12-v2, all-MiniLM-L6-v2

.PARAMETER Variant
    The ONNX variant to download (e.g. model_qint8_avx512_vnni).
    Available: model_qint8_avx512_vnni, model_O4, model

.PARAMETER OutputDir
    The output directory for the downloaded files. Defaults to onnx/ relative to the project root.
    Use a file: URI for absolute paths (e.g., file:///C:/Users/.../onnx).

.EXAMPLE
    .\scripts\download-onnx-transformer-model.ps1
    .\scripts\download-onnx-transformer-model.ps1 -Model multilingual-e5-small -Variant model_qint8_avx512_vnni
    .\scripts\download-onnx-transformer-model.ps1 -Model multilingual-e5-small -Variant model_qint8_avx512_vnni -OutputDir .\data\models\onnx
#>
param(
    [string]$Model,
    [string]$Variant,
    [string]$OutputDir
)

# Make failures terminate cleanly (mirrors `set -e` in the bash script).
$ErrorActionPreference = "Stop"
# In PowerShell 7.4+ a native command's non-zero exit throws under ErrorActionPreference=Stop.
# We inspect curl's exit code manually, so opt out here (no-op on Windows PowerShell 5.1).
$PSNativeCommandUseErrorActionPreference = $false

$HuggingFaceBase = "https://huggingface.co"

$Models = @(
    @{ Name = "multilingual-e5-small";     HF = "intfloat/multilingual-e5-small";              Desc = "INT8, AVX-512+VNNI - best quality/speed, 50+ langs" },
    @{ Name = "multilingual-e5-base";      HF = "intfloat/multilingual-e5-base";                 Desc = "INT8, AVX-512+VNNI - larger model, 100+ langs" },
    @{ Name = "paraphrase-multilingual-MiniLM-L12-v2"; HF = "sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2"; Desc = "INT8, AVX-512+VNNI - multilingual MiniLM" },
    @{ Name = "all-MiniLM-L12-v2";         HF = "sentence-transformers/all-MiniLM-L12-v2";       Desc = "INT8, AVX-512+VNNI - fast English-only" },
    @{ Name = "all-MiniLM-L6-v2";          HF = "sentence-transformers/all-MiniLM-L6-v2";        Desc = "INT8, AVX-512+VNNI - smallest, fastest, English-only" }
)

$Variants = @(
    @{ Name = "model_qint8_avx512_vnni"; Size = "~113 MB"; Desc = "INT8, AVX-512 + VNNI - fastest on modern Intel/AMD" },
    @{ Name = "model_O4";                Size = "~224 MB"; Desc = "OpenVINO optimized variant" },
    @{ Name = "model";                   Size = "~449 MB"; Desc = "FP16 base - largest file, slowest inference" }
)

# Resolve the script directory robustly (falls back when $PSScriptRoot is empty,
# e.g. when the script body is dot-sourced or pasted into a session).
$ScriptDir = if ($PSScriptRoot) { $PSScriptRoot } else { Split-Path -Parent $MyInvocation.MyCommand.Path }
if ([string]::IsNullOrEmpty($OutputDir)) {
    $OnnxDir = Join-Path $ScriptDir "..\onnx"
} else {
    $OnnxDir = $OutputDir
    # Resolve relative paths against the project root
    if (-not (Test-Path $OnnxDir -PathType Container) -and (Resolve-Path $OnnxDir -ErrorAction SilentlyContinue)) {
        $OnnxDir = (Resolve-Path $OnnxDir).Path
    }
}

# Create output directory if it doesn't exist
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
            Write-Host "  [$(( $i + 1 ))] $($Models[$i].Name) - $($Models[$i].Desc)" -ForegroundColor Yellow
        }
        Write-Host ""
        $Choice = Read-Host "Select model (1-5)"

        if ($Choice -notmatch '^\d+$' -or [int]$Choice -lt 1 -or [int]$Choice -gt $Models.Count) {
            Write-Host "Invalid selection." -ForegroundColor Red
            exit 1
        }

        $Model = $Models[[int]$Choice - 1].Name
    }

    # Select variant
    if ([string]::IsNullOrEmpty($Variant)) {
        Write-Host ""
        Write-Host "Available ONNX variants for ${Model}:" -ForegroundColor Cyan
        Write-Host ""
        for ($i = 0; $i -lt $Variants.Count; $i++) {
            Write-Host "  [$(( $i + 1 ))] $($Variants[$i].Name) - $($Variants[$i].Size) - $($Variants[$i].Desc)" -ForegroundColor Yellow
        }
        Write-Host ""
        $Choice = Read-Host "Select variant (1-3)"

        if ($Choice -notmatch '^\d+$' -or [int]$Choice -lt 1 -or [int]$Choice -gt $Variants.Count) {
            Write-Host "Invalid selection." -ForegroundColor Red
            exit 1
        }

        $Variant = $Variants[[int]$Choice - 1].Name
    }
}

$HfRepo = ($Models | Where-Object { $_.Name -eq $Model }).HF
if ([string]::IsNullOrEmpty($HfRepo)) {
    Write-Host "Unknown model: $Model" -ForegroundColor Red
    exit 1
}
$ModelFilename = "$Variant.onnx"
$TokenizerFilename = "tokenizer.json"

# Downloads use curl.exe (bundled with Windows 10 1803+ and Windows 11) to mirror the bash
# script and keep the two in sync. curl streams large files efficiently and shows a progress
# bar; Invoke-WebRequest in Windows PowerShell 5.1 renders progress so slowly that a 100+ MB
# download can look hung.
# Note: `curl` may be a PowerShell alias for Invoke-WebRequest, so we always call `curl.exe`.
if (-not (Get-Command curl.exe -ErrorAction SilentlyContinue)) {
    Write-Host "curl.exe was not found. It ships with Windows 10 (1803+) and Windows 11." -ForegroundColor Red
    Write-Host "Install curl or update Windows, then re-run this script." -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Downloading ONNX transformer model for JAIDoc..." -ForegroundColor Cyan
Write-Host "  Model   : $Model" -ForegroundColor Yellow
Write-Host "  Variant : $Variant" -ForegroundColor Yellow
Write-Host "  Output  : $OnnxDir" -ForegroundColor Yellow
if ([string]::IsNullOrEmpty($OutputDir)) {
    Write-Host "  (default: project onnx/ directory)" -ForegroundColor DarkGray
}
Write-Host ""

# Download model file
# Wrap variables before '?' in ${} so PowerShell does not absorb the '?' into the variable
# name (e.g. $ModelFilename?download would expand to nothing, yielding a malformed URL).
$ModelUrl = "$HuggingFaceBase/$HfRepo/resolve/main/onnx/${ModelFilename}?download=true"
Write-Host "Downloading model..." -ForegroundColor Green
& curl.exe -fSL $ModelUrl -o (Join-Path $OnnxDir $ModelFilename)
if ($LASTEXITCODE -ne 0) {
    Write-Host "  FAILED: $ModelFilename (curl exit $LASTEXITCODE)" -ForegroundColor Red
    exit 1
}
Write-Host "  OK: $ModelFilename" -ForegroundColor Green

# Download tokenizer file
$TokenizerUrl = "$HuggingFaceBase/$HfRepo/resolve/main/onnx/${TokenizerFilename}?download=true"
Write-Host "Downloading tokenizer..." -ForegroundColor Green
& curl.exe -fSL $TokenizerUrl -o (Join-Path $OnnxDir $TokenizerFilename)
if ($LASTEXITCODE -ne 0) {
    Write-Host "  FAILED: $TokenizerFilename (curl exit $LASTEXITCODE)" -ForegroundColor Red
    exit 1
}
Write-Host "  OK: $TokenizerFilename" -ForegroundColor Green

# Show file sizes
$ModelSize = (Get-Item (Join-Path $OnnxDir $ModelFilename)).Length
$TokenizerSize = (Get-Item (Join-Path $OnnxDir $TokenizerFilename)).Length
Write-Host ""
Write-Host "Download complete:" -ForegroundColor Green
Write-Host "  $ModelFilename  - $(('{0:N1} MB' -f ($ModelSize / 1MB)))" -ForegroundColor Green
Write-Host "  $TokenizerFilename  - $(('{0:N1} MB' -f ($TokenizerSize / 1MB)))" -ForegroundColor Green
Write-Host ""
Write-Host "To use a different model or variant, set the AI_TRANSFORMER_ONNX environment variable:" -ForegroundColor Cyan
Write-Host "  e.g. AI_TRANSFORMER_ONNX=file:///C:/Users/.../onnx/model.onnx" -ForegroundColor Yellow
Write-Host ""
Write-Host "You can now start the application." -ForegroundColor Cyan