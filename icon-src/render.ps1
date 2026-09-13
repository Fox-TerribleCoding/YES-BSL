# Rasterize an SVG master to PNG at an exact pixel size, using the local Edge
# install in headless mode. Vector source stays crisp at every size because the
# SVG is re-rendered at the requested width/height rather than downscaled.
#
#   .\render.ps1 -Svg icon.svg -Size 512
#   .\render.ps1 -Svg icon.svg -Size 400  -Out ..\build\icon-400.png
#   .\render.ps1 -Svg *.svg  -Sizes 512,400,256,128

[CmdletBinding()]
param(
    [Parameter(Mandatory)][string[]]$Svg,
    [int[]]$Sizes = @(512),
    [string]$OutDir
)

$ErrorActionPreference = 'Stop'

$Edge = @(
    "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
    "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
    "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe"
) | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
if (-not $Edge) { throw 'no Edge/Chrome found for rasterization' }

$Root    = $PSScriptRoot
if (-not $OutDir) { $OutDir = Join-Path $Root 'png' }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$scratch = Join-Path $env:TEMP 'yesbsl-icon-render'
New-Item -ItemType Directory -Force -Path $scratch | Out-Null
$profile = Join-Path $scratch 'profile'

foreach ($file in $Svg) {
    $path = if ([System.IO.Path]::IsPathRooted($file)) { $file } else { Join-Path $Root $file }
    if (-not (Test-Path -LiteralPath $path)) { throw "svg not found: $path" }

    $name = [System.IO.Path]::GetFileNameWithoutExtension($path)
    $text = Get-Content -LiteralPath $path -Raw -Encoding UTF8

    foreach ($size in $Sizes) {
        # Drop the intrinsic width/height, then force the exact size in CSS.
        # Without the explicit px size an inline <svg> that only has a viewBox
        # falls back to the viewBox size, and --window-size merely crops it.
        $scaled = $text -replace '(<svg\b[^>]*?)\swidth="[^"]*"', '$1'
        $scaled = $scaled -replace '(<svg\b[^>]*?)\sheight="[^"]*"', '$1'

        $html = "<!doctype html><meta charset=""utf-8""><style>" +
                "html,body{margin:0;padding:0;background:#000;overflow:hidden}" +
                "svg{display:block;width:${size}px;height:${size}px}" +
                "</style>$scaled"
        $htmlPath = Join-Path $scratch "$name-$size.html"
        Set-Content -LiteralPath $htmlPath -Value $html -Encoding UTF8

        $png = Join-Path $OutDir "$name-$size.png"
        if (Test-Path -LiteralPath $png) { Remove-Item -LiteralPath $png -Force }

        $url = 'file:///' + ($htmlPath -replace '\\', '/' -replace ' ', '%20')
        # Edge chatters on stderr; that must not abort the script.
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        & $Edge --headless --disable-gpu --no-first-run --no-default-browser-check `
                --hide-scrollbars --force-device-scale-factor=1 --disable-lcd-text `
                --log-level=3 --user-data-dir="$profile" --window-size="$size,$size" `
                --screenshot="$png" $url 2>$null | Out-Null
        $ErrorActionPreference = $prevEap

        if (-not (Test-Path -LiteralPath $png)) { throw "render failed: $png" }
        Write-Host ("{0,-28} {1,5}x{1,-5} {2,8:N0} B" -f (Split-Path $png -Leaf), $size, (Get-Item $png).Length)
    }
}
