# Share Claude's skill packages with Codex without copying their contents.
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$source = Join-Path $repoRoot '.claude/skills'
$agentsDirectory = Join-Path $repoRoot '.agents'
$link = Join-Path $agentsDirectory 'skills'

if (-not (Test-Path -LiteralPath $source -PathType Container)) {
    throw "Claude skills directory is missing: $source"
}

New-Item -ItemType Directory -Path $agentsDirectory -Force | Out-Null
$existing = Get-Item -LiteralPath $link -Force -ErrorAction SilentlyContinue
if ($null -ne $existing) {
    if ($existing.LinkType -notin @('SymbolicLink', 'Junction')) {
        throw "Refusing to replace an existing directory or file: $link"
    }
    $target = [string]($existing.Target | Select-Object -First 1)
    if (-not [System.IO.Path]::IsPathRooted($target)) {
        $target = Join-Path $agentsDirectory $target
    }
    if ([System.IO.Path]::GetFullPath($target) -ne [System.IO.Path]::GetFullPath($source)) {
        throw "Existing link points elsewhere: $link -> $target"
    }
} else {
    # Windows junctions do not require administrator privileges or Developer Mode.
    New-Item -ItemType Junction -Path $link -Target $source | Out-Null
}

$skills = @(Get-ChildItem -LiteralPath $link -Directory | Where-Object {
    Test-Path -LiteralPath (Join-Path $_.FullName 'SKILL.md') -PathType Leaf
})
Write-Output "Codex skills link: $link -> $source"
Write-Output "Available skills ($($skills.Count)): $($skills.Name -join ', ')"
