[CmdletBinding()]
param(
    [string]$EspeakExe,
    [string]$DataPath,
    [switch]$InstallDefault,
    [switch]$CheckOnly,
    [switch]$VerifyOnly
)

$ErrorActionPreference = 'Stop'
if (($CheckOnly -and $InstallDefault) -or ($VerifyOnly -and ($CheckOnly -or $InstallDefault))) {
    throw 'Choose one verification or installation mode.'
}
$projectRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $projectRoot 'project_materials.ps1')
function Read-DictionarySource([string]$Path) {
    $encoding = New-Object System.Text.UTF8Encoding($false, $true)
    $text = $encoding.GetString([IO.File]::ReadAllBytes($Path))
    if ($text.Length -gt 0 -and $text[0] -eq [char]0xfeff) {
        throw "UTF-8 BOM must be removed before native dictionary compilation: $Path"
    }
    return $text.Replace("`r`n", "`n").Replace("`r", "`n")
}
function Get-DictionarySourceHash([string]$Path) {
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.Encoding]::UTF8.GetBytes((Read-DictionarySource $Path))
        return [BitConverter]::ToString($algorithm.ComputeHash($bytes)).Replace('-', '')
    } finally { $algorithm.Dispose() }
}
if (-not $DataPath) {
    $DataPath = if (Test-Path -LiteralPath (Join-Path $projectRoot 'espeak-data')) {
        $projectRoot
    } else { Join-Path $projectRoot 'engine' }
}
$dataRoot = (Resolve-Path -LiteralPath $DataPath).Path
$runtimeData = Join-Path $dataRoot 'espeak-data'
$productData = if (Test-Path -LiteralPath (Join-Path $projectRoot 'espeak-data')) {
    Join-Path $projectRoot 'espeak-data'
} else { Join-Path $projectRoot 'engine\espeak-data' }
$manifest = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'phoneme-data.json') -Raw | ConvertFrom-Json
foreach ($checkedData in @($runtimeData, $productData) | Select-Object -Unique) {
    foreach ($property in $manifest.PSObject.Properties) {
        $file = Join-Path $checkedData $property.Name
        $hash = if ($property.Name -ceq 'voices/pl') {
            Get-DictionarySourceHash $file
        } else { (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash }
        if ($hash -cne $property.Value) {
            throw "Incompatible phoneme data: $($property.Name). Use this release's matching core/data; no tables were replaced."
        }
    }
}

# Approved numeric differences; stress differences are checked separately below.
$changes = @(
    @{ Key = '_3X'; Old = "t|Sydz;'ES;c;i"; New = "t|Sydz;'ES;ts;i" },
    @{ Key = '_4X'; Old = "tStERdz;'ES;c;i"; New = "tStERdz;'ES;ts;i" },
    @{ Key = '_2C'; Old = "dv;'ES;c;E"; New = "dv;'ES;ts;E" }
)
$legacySource = Join-Path $PSScriptRoot 'dictsource'
if (@(Get-ChildItem -LiteralPath $legacySource -File).Count -ne 2) {
    throw 'Review dictionary dependencies before changing the complete two-file Polish source set.'
}
$legacyList = Read-DictionarySource (Join-Path $legacySource 'pl_list')
$defaultList = $legacyList
foreach ($change in $changes) {
    $pattern = '(?m)^(' + [regex]::Escape($change.Key) + '\s+)' + [regex]::Escape($change.Old) + '(?=\r?$)'
    if ([regex]::Matches($defaultList, $pattern).Count -ne 1) {
        throw "Expected exactly one legacy entry: $($change.Key)"
    }
    $defaultList = [regex]::Replace($defaultList, $pattern, { param($match) $match.Groups[1].Value + $change.New })
}
foreach ($word in @('maksimum', 'minimum')) {
    $pattern = '(?m)^(' + [regex]::Escape($word) + '[\t ]+)\$2$'
    if ([regex]::Matches($defaultList, $pattern).Count -ne 1) {
        throw "Expected one colloquial stress preference: $word"
    }
    $defaultList = [regex]::Replace($defaultList, $pattern, { param($match) $match.Groups[1].Value + '$1' })
}
$collage = 'kola' + [char]0x17c
$pattern = '(?m)^(' + [regex]::Escape($collage) + '[\t ]+)\$1$'
if ([regex]::Matches($defaultList, $pattern).Count -ne 1) { throw 'Expected one alternative collage stress preference.' }
$defaultList = [regex]::Replace($defaultList, $pattern, { param($match) $match.Groups[1].Value + '$2' })

# Restore exactly the reviewed noun rules, never the verb stress rules.
$defaultRules = Read-DictionarySource (Join-Path $legacySource 'pl_rules')
$carefulRulesPath = Join-Path $PSScriptRoot 'careful-stress-rules.txt'
$carefulRules = @((Read-DictionarySource $carefulRulesPath) -split "`n" | Where-Object { $_.Length -gt 0 })
$tonikaPattern = '\bi \((?:ka|ki|kach|ce|k' + [char]0x119 + '|k' + [char]0x105 + '|ko)_'
if ($carefulRules.Count -ne 133 -or @($carefulRules | Select-Object -Unique).Count -ne 133) {
    throw 'The approved noun stress map must contain exactly 133 unique rules.'
}
foreach ($careful in $carefulRules) {
    if ($careful -notmatch '=[iy]$' -or ($careful -notmatch '\(L01' -and
        $careful -notmatch $tonikaPattern)) {
        throw 'An unapproved rule entered the noun stress map.'
    }
    $colloquial = $careful -replace '=([iy])$', '$1'
    $pattern = '(?m)^' + [regex]::Escape($colloquial) + '$'
    if ([regex]::Matches($defaultRules, $pattern).Count -ne 1) {
        throw "Expected exactly one colloquial noun rule: $colloquial"
    }
    $defaultRules = [regex]::Replace($defaultRules, $pattern, { $careful })
}
$canonicalSource = Join-Path $projectRoot 'dictsource'
if (Test-Path -LiteralPath $canonicalSource) {
    if ((Read-DictionarySource (Join-Path $canonicalSource 'pl_list')) -cne $defaultList -or
        (Read-DictionarySource (Join-Path $canonicalSource 'pl_rules')) -cne $defaultRules) {
        throw 'Default and alternative sources differ outside the approved number/stress map.'
    }
}

$legacyBinary = Join-Path $PSScriptRoot 'pl.dict'
$defaultBinary = Join-Path $productData 'pl_dict'
$dictionaryManifestPath = Join-Path $PSScriptRoot 'dictionary-manifest.json'
$sourcePaths = @('dictsource/pl_list', 'dictsource/pl_rules', 'careful-stress-rules.txt')
if ($VerifyOnly) {
    $dictionaryManifest = Get-Content -LiteralPath $dictionaryManifestPath -Raw | ConvertFrom-Json
    foreach ($relativePath in $sourcePaths) {
        if ((Get-DictionarySourceHash (Join-Path $PSScriptRoot $relativePath)) -cne $dictionaryManifest.SourceSHA256.$relativePath) {
            throw "Dictionary source changed without rebuilding both variants: $relativePath"
        }
    }
    if (@($dictionaryManifest.SourceSHA256.PSObject.Properties).Count -ne 3 -or
        (Get-FileHash -LiteralPath $legacyBinary).Hash -cne $dictionaryManifest.LegacySHA256 -or
        (Get-FileHash -LiteralPath $defaultBinary).Hash -cne $dictionaryManifest.DefaultSHA256) {
        throw 'A dictionary is stale or does not match the verified pair.'
    }
    Write-Output 'Polish default/legacy dictionary sources, binaries and phoneme data verified.'
    return
}
if (-not $EspeakExe) { throw 'EspeakExe is required for compilation.' }
$exe = (Resolve-Path -LiteralPath $EspeakExe).Path
$buildRoot = Join-Path (Get-ProjectMaterialsRoot $projectRoot) ('mydict-rebuild\' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
$utf8 = New-Object System.Text.UTF8Encoding($false)
$results = @{}
foreach ($variant in @('default', 'legacy')) {
    $stage = Join-Path $buildRoot $variant
    $stageData = Join-Path $stage 'espeak-data'
    $stageSource = Join-Path $stage 'dictsource'
    $null = New-Item -ItemType Directory -Path $stageSource, (Join-Path $stageData 'voices') -Force
    foreach ($property in $manifest.PSObject.Properties) {
        $target = Join-Path $stageData $property.Name
        $null = New-Item -ItemType Directory -Path (Split-Path $target -Parent) -Force
        Copy-Item -LiteralPath (Join-Path $runtimeData $property.Name) -Destination $target
    }
    # Initialization needs an existing Polish dictionary before --compile loads it.
    Copy-Item -LiteralPath (Join-Path $runtimeData 'pl_dict') -Destination $stageData
    $rulesText = if ($variant -ceq 'default') { $defaultRules } else { Read-DictionarySource (Join-Path $legacySource 'pl_rules') }
    [IO.File]::WriteAllText((Join-Path $stageSource 'pl_rules'), $rulesText, $utf8)
    $listText = if ($variant -ceq 'default') { $defaultList } else { $legacyList }
    [IO.File]::WriteAllText((Join-Path $stageSource 'pl_list'), $listText, $utf8)

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $exe
    $startInfo.Arguments = '"--path=' + $stage + '" -q --compile=pl'
    $startInfo.WorkingDirectory = $stageSource
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo
    try {
        if (-not $process.Start()) { throw 'Dictionary compiler could not start.' }
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(30000)) {
            $process.Kill()
            $null = $process.WaitForExit(5000)
            throw 'Dictionary compilation timed out.'
        }
        [IO.File]::WriteAllText((Join-Path $stage 'compile.stdout.txt'), $stdout.Result, $utf8)
        [IO.File]::WriteAllText((Join-Path $stage 'compile.stderr.txt'), $stderr.Result, $utf8)
        if ($process.ExitCode -ne 0) { throw "Dictionary compilation failed; see $stage." }
    } finally { $process.Dispose() }
    $binary = Join-Path $stageData 'pl_dict'
    if ((Get-Item -LiteralPath $binary).Length -lt 1000) { throw 'Compiled dictionary is incomplete.' }
    $results[$variant] = [pscustomobject]@{
        Binary = $binary
        DataPath = $stage
        SHA256 = (Get-FileHash -LiteralPath $binary -Algorithm SHA256).Hash
    }
}

if ($CheckOnly) {
    if ((Get-FileHash -LiteralPath $legacyBinary).Hash -cne $results.legacy.SHA256 -or
        (Get-FileHash -LiteralPath $defaultBinary).Hash -cne $results.default.SHA256) {
        throw 'A compiled dictionary differs from its reproducible source build.'
    }
} else {
    Copy-Item -LiteralPath $results.legacy.Binary -Destination $legacyBinary
    if ($InstallDefault) { Copy-Item -LiteralPath $results.default.Binary -Destination $defaultBinary }
    $sourceHashes = [ordered]@{}
    foreach ($relativePath in $sourcePaths) {
        $sourceHashes[$relativePath] = Get-DictionarySourceHash (Join-Path $PSScriptRoot $relativePath)
    }
    $dictionaryManifest = [ordered]@{
        DefaultSHA256 = $results.default.SHA256
        LegacySHA256 = $results.legacy.SHA256
        SourceSHA256 = $sourceHashes
    }
    [IO.File]::WriteAllText($dictionaryManifestPath, ($dictionaryManifest | ConvertTo-Json -Depth 3) + "`n", $utf8)
}
[pscustomobject]@{ Default = $results.default; Legacy = $results.legacy; InstalledDefault = [bool]$InstallDefault }
