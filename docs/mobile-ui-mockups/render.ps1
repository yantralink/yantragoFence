param([string]$ChromePath = 'C:\Program Files\Google\Chrome\Application\chrome.exe')

$ErrorActionPreference = 'Stop'
$source = ([Uri](Join-Path $PSScriptRoot 'gallery.html')).AbsoluteUri
$screenIds = @(
    '01-splash', '02-login', '03-machines', '04-machine-details',
    '05-machine-location', '06-machine-map', '07-dashboard', '08-alerts',
    '09-profile', '10-settings', '11-history', '12-notifications-concept',
    '13-empty-state', '14-error-state', '15-loading-state', '16-command-pending'
)
$common = @('--headless', '--disable-gpu', '--no-first-run', '--no-default-browser-check', '--hide-scrollbars', '--virtual-time-budget=1000', '--log-level=3')

function Assert-Layout([string]$Theme) {
    $profile = Join-Path $env:TEMP "yantrago-mockup-$([guid]::NewGuid().ToString('N'))"
    $dom = & $ChromePath @common "--user-data-dir=$profile" '--dump-dom' "${source}?theme=$Theme" 2>$null | Out-String
    if ($LASTEXITCODE -ne 0 -or $dom -notmatch 'data-ready="true"') {
        throw "The $Theme gallery did not render successfully."
    }
    $overflow = [regex]::Match($dom, 'data-overflow="([^"]*)"').Groups[1].Value
    if ($overflow -ne 'none') { throw "Overflow detected in $Theme gallery: $overflow" }
    Write-Output "Verified $Theme gallery: all 16 screens fit their viewport."
}

function Export-Image([string]$Url, [string]$Output, [string]$WindowSize, [int]$Scale) {
    $profile = Join-Path $env:TEMP "yantrago-mockup-$([guid]::NewGuid().ToString('N'))"
    & $ChromePath @common "--user-data-dir=$profile" "--force-device-scale-factor=$Scale" "--window-size=$WindowSize" "--screenshot=$Output" $Url 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0 -or -not [IO.File]::Exists($Output)) {
        throw "Chrome failed to export $Output"
    }
}

foreach ($theme in @('light', 'dark')) {
    Assert-Layout $theme
    foreach ($id in $screenIds) {
        $output = Join-Path $PSScriptRoot "$theme-$id.png"
        Export-Image "${source}?screen=$id&theme=$theme&capture=1" $output '390,844' 2
        Write-Output "Exported $theme-$id.png"
    }
    Export-Image "${source}?theme=$theme&overview=1" (Join-Path $PSScriptRoot "overview-$theme.png") '1668,3940' 1
    Write-Output "Exported overview-$theme.png"
}
Write-Output 'Complete: 32 individual PNGs and 2 overview PNGs. No Flutter files changed.'
