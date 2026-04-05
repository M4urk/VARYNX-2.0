## VARYNX 2.0 — Windows Code Signing (Authenticode)
##
## Prerequisites:
##   1. A code-signing certificate (.pfx) from a trusted CA
##   2. Windows SDK installed (for signtool.exe)
##   3. Set VARYNX_SIGN_PFX and VARYNX_SIGN_PASSWORD environment variables
##
## Usage:
##   .\sign-windows.ps1 -ArtifactDir "build\distributions"
##

param(
    [Parameter(Mandatory = $true)]
    [string]$ArtifactDir,

    [string]$PfxPath = $env:VARYNX_SIGN_PFX,
    [string]$PfxPassword = $env:VARYNX_SIGN_PASSWORD,
    [string]$TimestampServer = "http://timestamp.digicert.com",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

if (-not $PfxPath) {
    Write-Error "No PFX path specified. Set VARYNX_SIGN_PFX or pass -PfxPath."
    exit 1
}
if (-not (Test-Path $PfxPath)) {
    Write-Error "PFX file not found: $PfxPath"
    exit 1
}

# Find signtool.exe
$signtool = Get-ChildItem "C:\Program Files (x86)\Windows Kits\10\bin\*\x64\signtool.exe" -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending |
    Select-Object -First 1

if (-not $signtool) {
    Write-Error "signtool.exe not found. Install the Windows SDK."
    exit 1
}

Write-Host "Using signtool: $($signtool.FullName)"

# Files to sign: EXE, DLL, MSI
$extensions = @("*.exe", "*.dll", "*.msi")
$files = @()
foreach ($ext in $extensions) {
    $files += Get-ChildItem -Path $ArtifactDir -Filter $ext -Recurse
}

if ($files.Count -eq 0) {
    Write-Warning "No signable files found in $ArtifactDir"
    exit 0
}

Write-Host "Found $($files.Count) file(s) to sign."

$failed = 0
foreach ($file in $files) {
    Write-Host "  Signing: $($file.Name)"
    if ($DryRun) {
        Write-Host "    [DRY RUN] Would sign $($file.FullName)"
        continue
    }

    $args = @(
        "sign",
        "/f", $PfxPath,
        "/p", $PfxPassword,
        "/fd", "sha256",
        "/tr", $TimestampServer,
        "/td", "sha256",
        "/d", "VARYNX 2.0",
        "/du", "https://varynx.com",
        $file.FullName
    )

    & $signtool.FullName @args
    if ($LASTEXITCODE -ne 0) {
        Write-Error "  FAILED to sign: $($file.Name)"
        $failed++
    } else {
        Write-Host "    OK"
    }
}

if ($failed -gt 0) {
    Write-Error "$failed file(s) failed to sign."
    exit 1
}

Write-Host "All $($files.Count) file(s) signed successfully."
