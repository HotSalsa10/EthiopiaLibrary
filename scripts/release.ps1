<#
.SYNOPSIS
    Cuts a new EthiopiaLibrary release: builds and tests the signed release
    APK, asserts versionCode was actually bumped since the last published
    release, then publishes latest.json + the APK to GitHub Releases so
    tablets in the field can self-update (see update/UpdateWorker.kt).

.PARAMETER NotesAm
    Amharic release notes shown in the app (optional).
.PARAMETER NotesAr
    Arabic release notes shown in the app (optional).
.PARAMETER NotesEn
    English release notes shown in the app (optional).

.EXAMPLE
    .\scripts\release.ps1 -NotesEn "Fixes the overdue count on always-on tablets."
#>

param(
    [string]$NotesAm = "",
    [string]$NotesAr = "",
    [string]$NotesEn = ""
)

$ErrorActionPreference = "Stop"

$repo = "HotSalsa10/EthiopiaLibrary"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

# --- read the version this build declares ---
$gradleFile = Join-Path $repoRoot "app\build.gradle.kts"
$gradleContent = Get-Content $gradleFile -Raw
$versionCodeMatch = [regex]::Match($gradleContent, 'versionCode\s*=\s*(\d+)')
$versionNameMatch = [regex]::Match($gradleContent, 'versionName\s*=\s*"([^"]+)"')
if (-not $versionCodeMatch.Success -or -not $versionNameMatch.Success) {
    throw "Could not read versionCode/versionName from app/build.gradle.kts"
}
$versionCode = [int]$versionCodeMatch.Groups[1].Value
$versionName = $versionNameMatch.Groups[1].Value
Write-Host "Releasing versionCode=$versionCode versionName=$versionName"

# --- assert versionCode was actually bumped, against what tablets currently see ---
$latestJsonUrl = "https://github.com/$repo/releases/latest/download/latest.json"
$previousVersionCode = $null
try {
    $previous = Invoke-RestMethod -Uri $latestJsonUrl -TimeoutSec 15
    $previousVersionCode = [int]$previous.versionCode
    Write-Host "Previously published versionCode: $previousVersionCode"
} catch {
    Write-Host "No previous release found (or not reachable) - treating this as the first release. ($($_.Exception.Message))"
}
if ($null -ne $previousVersionCode -and $previousVersionCode -ge $versionCode) {
    throw "versionCode $versionCode is not newer than the last published release ($previousVersionCode). Bump versionCode in app/build.gradle.kts first."
}

# --- build: full test suite, then the signed release APK ---
Write-Host "Running tests and building the signed release APK..."
& .\gradlew.bat test assembleRelease
if ($LASTEXITCODE -ne 0) { throw "gradlew test assembleRelease failed" }

$apkPath = Join-Path $repoRoot "app\build\outputs\apk\release\app-release.apk"
if (-not (Test-Path $apkPath)) { throw "Expected release APK not found at $apkPath" }

# --- hash + manifest ---
$sha256 = (Get-FileHash -Path $apkPath -Algorithm SHA256).Hash.ToLower()
Write-Host "APK SHA-256: $sha256"

$tag = "v$versionCode"
$manifest = [ordered]@{
    versionCode = $versionCode
    versionName = $versionName
    apkUrl      = "https://github.com/$repo/releases/latest/download/app-release.apk"
    sha256      = $sha256
    notes_am    = $NotesAm
    notes_ar    = $NotesAr
    notes_en    = $NotesEn
}
$manifestPath = Join-Path $repoRoot "latest.json"
$manifest | ConvertTo-Json | Set-Content -Path $manifestPath -Encoding utf8

# --- publish: tag, APK, and manifest together, so `releases/latest` always points at a consistent pair ---
Write-Host "Creating GitHub release $tag..."
& gh release create $tag $apkPath $manifestPath `
    --repo $repo `
    --title "v$versionName ($versionCode)" `
    --notes "Automated release. See latest.json for trilingual release notes."
if ($LASTEXITCODE -ne 0) { throw "gh release create failed" }

Remove-Item $manifestPath -ErrorAction SilentlyContinue
Write-Host ""
Write-Host "Released $tag. Tablets will discover it at:"
Write-Host "  $latestJsonUrl"
