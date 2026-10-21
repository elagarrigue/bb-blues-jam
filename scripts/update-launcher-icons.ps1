$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$repoRoot = Split-Path $PSScriptRoot -Parent
$resRoot = Join-Path $repoRoot 'app/src/main/res'
$source = [System.Drawing.Bitmap]::FromFile((Join-Path $repoRoot 'docs/design/launcher/artwork.png'))
try {
    # Trim transparent margins before fitting the artwork into Android's safe zone.
    $left = $source.Width
    $top = $source.Height
    $right = 0
    $bottom = 0
    for ($y = 0; $y -lt $source.Height; $y++) {
        for ($x = 0; $x -lt $source.Width; $x++) {
            if ($source.GetPixel($x, $y).A -gt 8) {
                $left = [Math]::Min($left, $x)
                $top = [Math]::Min($top, $y)
                $right = [Math]::Max($right, $x)
                $bottom = [Math]::Max($bottom, $y)
            }
        }
    }
    if ($right -le $left -or $bottom -le $top) { throw 'Empty launcher artwork.' }
    $crop = [System.Drawing.Rectangle]::new($left, $top, $right - $left + 1, $bottom - $top + 1)
    $centerX = ($left + $right) / 2.0
    $centerY = ($top + $bottom) / 2.0
    $radius = 0.0
    for ($y = $top; $y -le $bottom; $y++) {
        for ($x = $left; $x -le $right; $x++) {
            if ($source.GetPixel($x, $y).A -gt 8) {
                $distance = [Math]::Sqrt([Math]::Pow($x - $centerX, 2) + [Math]::Pow($y - $centerY, 2))
                $radius = [Math]::Max($radius, $distance)
            }
        }
    }

    function Write-Icon([string]$Path, [int]$Size, [double]$MaxRadius, [string]$Background) {
        $bitmap = [System.Drawing.Bitmap]::new($Size, $Size)
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        $mask = [System.Drawing.Drawing2D.GraphicsPath]::new()
        try {
            $graphics.SmoothingMode = 'AntiAlias'
            $graphics.InterpolationMode = 'HighQualityBicubic'
            $graphics.PixelOffsetMode = 'HighQuality'
            $graphics.Clear([System.Drawing.Color]::Transparent)
            if ($Background -eq 'round') {
                $mask.AddEllipse(0, 0, $Size, $Size)
                $graphics.SetClip($mask)
            }
            if ($Background -ne 'transparent') {
                $brush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#111318'))
                try { $graphics.FillRectangle($brush, 0, 0, $Size, $Size) } finally { $brush.Dispose() }
            }
            $scale = $Size * $MaxRadius / $radius
            $width = [single]($crop.Width * $scale)
            $height = [single]($crop.Height * $scale)
            $target = [System.Drawing.RectangleF]::new(($Size - $width) / 2, ($Size - $height) / 2, $width, $height)
            $graphics.DrawImage($source, $target, $crop, [System.Drawing.GraphicsUnit]::Pixel)
            $bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $mask.Dispose()
            $graphics.Dispose()
            $bitmap.Dispose()
        }
    }

    # 0.30 radius fits inside the 66/108 adaptive-icon safe circle, including antialiasing.
    Write-Icon (Join-Path $resRoot 'drawable-nodpi/launcher_brand.png') 1024 0.30 'transparent'
    $densities = @{ mdpi = 48; hdpi = 72; xhdpi = 96; xxhdpi = 144; xxxhdpi = 192 }
    foreach ($density in $densities.GetEnumerator()) {
        $folder = Join-Path $resRoot ('mipmap-' + $density.Key)
        Write-Icon (Join-Path $folder 'ic_launcher.png') $density.Value 0.41 'square'
        Write-Icon (Join-Path $folder 'ic_launcher_round.png') $density.Value 0.41 'round'
    }
    Write-Output 'Updated adaptive foreground and all ten legacy launcher icons.'
} finally {
    $source.Dispose()
}
