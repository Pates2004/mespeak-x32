# Build and validation paths for the user's centralized assistant materials.
function Get-ProjectMaterialsRoot {
    param([Parameter(Mandatory = $true)][string]$ProjectRoot)
    $source = [IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\', '/')
    $drive = if ([IO.Directory]::GetLogicalDrives() -contains 'D:\') { 'D:\' } else { 'C:\' }
    $umbrella = 'D:\espeak-1.44.05-source'
    if ($source.Equals($umbrella, [StringComparison]::OrdinalIgnoreCase) -or
        $source.StartsWith($umbrella + '\', [StringComparison]::OrdinalIgnoreCase)) {
        return [IO.Path]::Combine($drive, 'work', 'espeak-1.44.05-source' + $source.Substring($umbrella.Length))
    }
    return [IO.Path]::Combine($drive, 'work', [IO.Path]::GetFileName($source))
}

function Join-ProjectMaterialPath {
    param([Parameter(Mandatory = $true, Position = 0)][string]$Path,
          [Parameter(Mandatory = $true, Position = 1)][string]$ChildPath)
    if ([IO.Path]::IsPathRooted($ChildPath)) {
        return [IO.Path]::GetFullPath($ChildPath)
    }
    $root = [IO.Path]::GetFullPath($Path).TrimEnd('\', '/')
    $first = ($ChildPath -split '[\\/]')[0]
    if ($first -iin @('Forme', 'Workspace', 'installfiles', 'snapshots', 'trash', 'build', '.gradle', '.cxx')) {
        $root = Get-ProjectMaterialsRoot $root
    }
    $result = [IO.Path]::GetFullPath([IO.Path]::Combine($root, $ChildPath))
    if (-not $result.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path escapes the permitted project area: $ChildPath"
    }
    return $result
}

function Test-ProjectMaterialPath {
    param([string]$Path, [string]$ProjectRoot)
    $full = [IO.Path]::GetFullPath($Path).TrimEnd('\', '/')
    foreach ($root in @([IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\', '/'), (Get-ProjectMaterialsRoot $ProjectRoot))) {
        if ($full.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase)) { return $true }
    }
    return $false
}

# Build scripts supply the verified drive to MSBuild without a silent access fallback.
$env:ProjectMaterialsDrive = if ([IO.Directory]::GetLogicalDrives() -contains 'D:\') { 'D:' } else { 'C:' }
