# Verified source paths and category-based local build materials.
function Get-ProjectMaterialsRoot {
    param(
        [Parameter(Mandatory = $true)][string]$ProjectRoot,
        [ValidateSet('Forme', 'Workspace', 'Snapshots', 'Trash')][string]$Category = 'Workspace'
    )
    $source = [IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\', '/')
    $drive = if ([IO.Directory]::GetLogicalDrives() -contains 'D:\') { 'D:\' } else { 'C:\' }
    $projectName = if ($source.Equals('D:\espeak-1.44.05-source', [StringComparison]::OrdinalIgnoreCase)) {
        'espeak'
    } else { [IO.Path]::GetFileName($source) }
    return [IO.Path]::Combine($drive, 'work', $Category, $projectName)
}

function Join-ProjectMaterialPath {
    param(
        [Parameter(Mandatory = $true, Position = 0)][string]$Path,
        [Parameter(Mandatory = $true, Position = 1)][string]$ChildPath
    )
    if ([IO.Path]::IsPathRooted($ChildPath)) {
        return [IO.Path]::GetFullPath($ChildPath)
    }
    $root = [IO.Path]::GetFullPath($Path).TrimEnd('\', '/')
    $parts = $ChildPath -split '[\\/]', 2
    $first = $parts[0]
    if ($first -iin @('Forme', 'Workspace', 'snapshots', 'trash')) {
        $category = @{Forme='Forme';Workspace='Workspace';snapshots='Snapshots';trash='Trash'}[$first]
        $root = Get-ProjectMaterialsRoot $root $category
        $relative = if ($parts.Count -gt 1) { $parts[1] } else { '' }
    } elseif ($first -ieq 'build') {
        $root = Get-ProjectMaterialsRoot $root
        $relative = $ChildPath
    } else {
        # Sources, runtime data and current installfiles stay in the repository.
        $relative = $ChildPath
    }
    $result = [IO.Path]::GetFullPath([IO.Path]::Combine($root, $relative))
    if (!$result.Equals($root, [StringComparison]::OrdinalIgnoreCase) -and
        !$result.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw "Path escapes the permitted project area: $ChildPath"
    }
    return $result
}

function Test-ProjectMaterialPath {
    param([string]$Path, [string]$ProjectRoot)
    $full = [IO.Path]::GetFullPath($Path).TrimEnd('\', '/')
    $roots = @([IO.Path]::GetFullPath($ProjectRoot).TrimEnd('\', '/'))
    foreach ($category in @('Forme', 'Workspace', 'Snapshots', 'Trash')) {
        $roots += Get-ProjectMaterialsRoot $ProjectRoot $category
    }
    foreach ($root in $roots) {
        if ($full.Equals($root, [StringComparison]::OrdinalIgnoreCase) -or
            $full.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase)) { return $true }
    }
    return $false
}

# MSBuild selects the drive by existence, never by access or available space.
$env:ProjectMaterialsDrive = if ([IO.Directory]::GetLogicalDrives() -contains 'D:\') { 'D:' } else { 'C:' }
