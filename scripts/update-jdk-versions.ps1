# Generates the "Current Versions" section for documentation/JDK-SOURCES.md
# by fetching the latest JDK update versions from Oracle's release notes.
#
# Usage:
#   powershell -File scripts\update-jdk-versions.ps1
#
# The script outputs the markdown content and instructions for the AI to
# automatically update the documentation file.

$ErrorActionPreference = "Stop"

$UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

# Config: JDK version -> release notes URL + parsing rules
$JdkConfigs = @(
    @{ Version = "8";     Url = "https://www.oracle.com/java/technologies/javase/8u-relnotes.html";  Pattern = 'href="/java/technologies/javase/8u(\d+)-relnotes' },
    @{ Version = "11";    Url = "https://www.oracle.com/java/technologies/javase/11u-relnotes.html";   Pattern = 'JDK 11\.0\.(\d+)' },
    @{ Version = "17";    Url = "https://www.oracle.com/java/technologies/javase/17u-relnotes.html";   Pattern = 'JDK 17\.0\.(\d+)' },
    @{ Version = "21";    Url = "https://www.oracle.com/java/technologies/javase/21u-relnotes.html";   Pattern = 'JDK 21\.0\.(\d+)' },
    @{ Version = "25";    Url = "https://www.oracle.com/java/technologies/javase/25u-relnotes.html";   Pattern = 'JDK 25\.0\.(\d+)' }
)

# Fetch and parse versions for each JDK
$JdkVersions = @{}
foreach ($config in $JdkConfigs) {
    Write-Host "Fetching JDK $($config.Version)..."
    try {
        $html = Invoke-WebRequest -Uri $config.Url -UserAgent $UserAgent -UseBasicParsing | Select-Object -ExpandProperty Content
        $matches = [regex]::Matches($html, $config.Pattern)
        $updateNumbers = @($matches | ForEach-Object { [int]$_.Groups[1].Value } | Sort-Object -Descending | Select-Object -Unique)
        $JdkVersions[$config.Version] = $updateNumbers
    }
    catch {
        Write-Host "Error fetching JDK $($config.Version): $_" -ForegroundColor Red
        exit 1
    }
}

# Format version strings based on JDK type
function Format-JdkVersions {
    param(
        [string]$JdkVersion,
        [int[]]$UpdateNumbers
    )
    if ($JdkVersion -eq "8") {
        # JDK 8 format: 8uNNN
        $formatted = $UpdateNumbers | ForEach-Object { "8u$_" }
    }
    else {
        # Modern JDK format: X.0.N
        $formatted = $UpdateNumbers | ForEach-Object { "$JdkVersion.0.$_" }
    }
    return $formatted -join ", "
}

# Build the markdown content for the Current Versions section
$lines = @()
$lines += "## Current Versions"
$lines += ""
$lines += "The following update versions are currently available on [Oracle's JDK Release Notes](https://www.oracle.com/java/technologies/javase/jdk-relnotes-index.html):"
$lines += ""

foreach ($config in $JdkConfigs) {
    $formatted = Format-JdkVersions -JdkVersion $config.Version -UpdateNumbers $JdkVersions[$config.Version]
    $lines += "**JDK $($config.Version)** -- Update versions: $formatted"
    $lines += ""
}

$markdownContent = ($lines | Out-String).TrimEnd()

# Output the markdown content and AI instructions
Write-Host $markdownContent
