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
    # GitHub serves release assets (incl. latest.json) with
    # Content-Type: application/octet-stream, not application/json - so
    # Invoke-RestMethod never auto-deserializes it and just returns the raw
    # response as a System.String, decoded with a guessed (wrong) encoding
    # since there's no text content-type to hint at UTF-8. `.versionCode` on
    # a bare/mis-decoded string silently evaluates to $null (no error), and
    # [int]$null is 0, not a parse failure - so the "newer than last
    # published" guard below always compared against 0 and never actually
    # blocked anything. WebClient with an explicit UTF-8 Encoding decodes
    # correctly (and strips a real BOM) regardless of content-type.
    $webClient = New-Object System.Net.WebClient
    $webClient.Encoding = [System.Text.Encoding]::UTF8
    $previousRaw = $webClient.DownloadString($latestJsonUrl)
    $previous = $previousRaw.TrimStart([char]0xFEFF) | ConvertFrom-Json
    $previousVersionCode = [int]$previous.versionCode
    Write-Host "Previously published versionCode: $previousVersionCode"
} catch {
    Write-Host "No previous release found (or not reachable) - treating this as the first release. ($($_.Exception.Message))"
}
if ($null -ne $previousVersionCode -and $previousVersionCode -ge $versionCode) {
    throw "versionCode $versionCode is not newer than the last published release ($previousVersionCode). Bump versionCode in app/build.gradle.kts first."
}

# --- build: full test suite, then the signed release APK ---
# testDebugUnitTest, not the bare `test` task: the exported Room schema
# JSONs are debug-only assets (see app/build.gradle.kts), so
# MigrationTest's fixtures aren't visible to testReleaseUnitTest at all -
# that variant fails on FileNotFoundException regardless of migration
# correctness. testDebugUnitTest exercises the identical business logic
# and is what gates every commit in this project.
Write-Host "Running tests and building the signed release APK..."
& .\gradlew.bat testDebugUnitTest assembleRelease
if ($LASTEXITCODE -ne 0) { throw "gradlew testDebugUnitTest assembleRelease failed" }

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
# `Set-Content -Encoding utf8` always prepends a UTF-8 BOM in Windows
# PowerShell 5.1 (no utf8NoBOM option here) - a leading BOM breaks strict
# JSON parsers, including this same script's own ConvertFrom-Json when it
# later reads a previously-published manifest back. Write via .NET directly
# to keep the artifact plain UTF-8, no BOM.
$manifestJson = $manifest | ConvertTo-Json
[System.IO.File]::WriteAllText($manifestPath, $manifestJson, (New-Object System.Text.UTF8Encoding($false)))

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
