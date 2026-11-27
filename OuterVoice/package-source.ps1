$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$parent = Split-Path $root -Parent
[xml]$manifest = Get-Content -LiteralPath (Join-Path $root 'app/src/main/AndroidManifest.xml')
$version = $manifest.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android')
$dist = Join-Path $root 'dist'
$archive = Join-Path $dist "OuterVoice-$version-source.zip"
New-Item -ItemType Directory -Force $dist | Out-Null
# Allow-list corresponding sources and evidence. No build outputs, credentials or signing keys.
$files = @(
    Get-ChildItem -LiteralPath $root -File | Where-Object { $_.Extension -in @('.md','.txt','.ps1') }
    foreach ($folder in @('app/src','tests','qa')) {
        Get-ChildItem -LiteralPath (Join-Path $root $folder) -Recurse -File | Where-Object { $_.Extension -notin @('.wav','.apk','.jks','.keystore','.idsig') -and $_.Name -notlike '*signing-password*' }
    }
) | Sort-Object FullName -Unique
Add-Type -AssemblyName System.IO.Compression.FileSystem
$stream = [System.IO.File]::Open($archive,[System.IO.FileMode]::Create)
$zip = New-Object System.IO.Compression.ZipArchive($stream,[System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $files) {
        $entry = $file.FullName.Substring($parent.Length + 1).Replace('\','/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$file.FullName,$entry,[System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $zip.Dispose(); $stream.Dispose() }
$hashes = foreach ($file in @((Join-Path $dist "OuterVoice-$version.apk"),$archive)) {
    (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + (Split-Path $file -Leaf)
}
[System.IO.File]::WriteAllText((Join-Path $dist 'SHA256.txt'),($hashes -join "`n") + "`n",(New-Object System.Text.UTF8Encoding($false)))
Write-Output "Source archive: $archive"
