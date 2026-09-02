[CmdletBinding()]
param(
    [string]$SigningDirectory,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path -LiteralPath $PSScriptRoot).Path
if ([string]::IsNullOrWhiteSpace($SigningDirectory)) {
    $SigningDirectory = Join-Path $repoRoot '..\signing'
}
$signingRoot = (Resolve-Path -LiteralPath $SigningDirectory).Path
$propertiesPath = Join-Path $signingRoot 'mespeak-release.properties'
if (-not (Test-Path -LiteralPath $propertiesPath -PathType Leaf)) {
    throw "Missing signing properties: $propertiesPath"
}

$properties = ConvertFrom-StringData (Get-Content -LiteralPath $propertiesPath -Raw)
$required = @(
    'legacyStore', 'legacyStorePassword', 'legacyKeyAlias', 'legacyKeyPassword',
    'releaseStore', 'releaseStorePassword', 'releaseKeyAlias', 'releaseKeyPassword',
    'lineage'
)
foreach ($name in $required) {
    if ([string]::IsNullOrWhiteSpace($properties[$name])) {
        throw "Missing signing property: $name"
    }
}

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][object[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$FailureMessage
    )
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $FilePath @Arguments
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($exitCode -ne 0) { throw $FailureMessage }
}

if (-not $SkipBuild) {
    Invoke-NativeCommand -FilePath (Join-Path $repoRoot 'gradlew.bat') `
        -Arguments @('assembleRelease', '--console=plain') `
        -FailureMessage 'Release build failed.'
}

$androidHome = $env:ANDROID_HOME
if ([string]::IsNullOrWhiteSpace($androidHome)) {
    throw 'ANDROID_HOME is not set.'
}
$apksigner = Get-ChildItem -LiteralPath (Join-Path $androidHome 'build-tools') `
    -Filter apksigner.bat -File -Recurse |
    Sort-Object FullName -Descending |
    Select-Object -First 1 -ExpandProperty FullName
if ([string]::IsNullOrWhiteSpace($apksigner)) { throw 'apksigner was not found.' }

$manifest = Get-Content -LiteralPath (Join-Path $repoRoot 'AndroidManifest.xml') -Raw
$versionMatch = [regex]::Match($manifest, 'android:versionName="([^"]+)"')
if (-not $versionMatch.Success) { throw 'Cannot read versionName from AndroidManifest.xml.' }
$versionName = $versionMatch.Groups[1].Value
$unsignedApk = Join-Path $repoRoot 'build\outputs\apk\release\mespeak-x32-release-unsigned.apk'
$signedApk = Join-Path $repoRoot "build\outputs\apk\mespeak-x32-$versionName.apk"
if (-not (Test-Path -LiteralPath $unsignedApk -PathType Leaf)) {
    throw "Missing unsigned release APK: $unsignedApk"
}

$legacyStore = Join-Path $signingRoot $properties.legacyStore
$releaseStore = Join-Path $signingRoot $properties.releaseStore
$lineage = Join-Path $signingRoot $properties.lineage
foreach ($path in @($legacyStore, $releaseStore, $lineage)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing signing file: $path" }
}

$env:MESPEAK_LEGACY_STORE_PASSWORD = $properties.legacyStorePassword
$env:MESPEAK_LEGACY_KEY_PASSWORD = $properties.legacyKeyPassword
$env:MESPEAK_RELEASE_STORE_PASSWORD = $properties.releaseStorePassword
$env:MESPEAK_RELEASE_KEY_PASSWORD = $properties.releaseKeyPassword
try {
    $arguments = @(
        'sign', '--out', $signedApk,
        '--rotation-min-sdk-version', '28',
        '--v4-signing-enabled', 'false',
        '--ks', $legacyStore,
        '--ks-key-alias', $properties.legacyKeyAlias,
        '--ks-pass', 'env:MESPEAK_LEGACY_STORE_PASSWORD',
        '--key-pass', 'env:MESPEAK_LEGACY_KEY_PASSWORD',
        '--next-signer',
        '--ks', $releaseStore,
        '--ks-key-alias', $properties.releaseKeyAlias,
        '--ks-pass', 'env:MESPEAK_RELEASE_STORE_PASSWORD',
        '--key-pass', 'env:MESPEAK_RELEASE_KEY_PASSWORD',
        '--lineage', $lineage,
        $unsignedApk
    )
    Invoke-NativeCommand -FilePath $apksigner -Arguments $arguments `
        -FailureMessage 'APK signing failed.'
    Invoke-NativeCommand -FilePath $apksigner `
        -Arguments @('verify', '--verbose', $signedApk) `
        -FailureMessage 'APK signature verification failed.'
}
finally {
    Remove-Item Env:MESPEAK_LEGACY_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:MESPEAK_LEGACY_KEY_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:MESPEAK_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:MESPEAK_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue
}

Write-Output $signedApk
