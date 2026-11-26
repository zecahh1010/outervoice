$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$tools = Join-Path (Split-Path $root -Parent) '.build-tools'
$jdk = (Get-ChildItem (Join-Path $tools 'java') -Directory | Select-Object -First 1).FullName
$sdk = (Get-ChildItem (Join-Path $tools 'android-build') -Directory | Select-Object -First 1).FullName
$platform = (Get-ChildItem (Join-Path $tools 'android-platform') -Directory | Select-Object -First 1).FullName
$out = Join-Path $root 'build/native-probe'
New-Item -ItemType Directory -Force $out,(Join-Path $out 'classes'),(Join-Path $out 'dex') | Out-Null
function Check($step) { if ($LASTEXITCODE -ne 0) { throw "$step failed" } }
& (Join-Path $jdk 'bin/javac.exe') -encoding UTF-8 --release 8 -cp (Join-Path $platform 'android.jar') -d (Join-Path $out 'classes') (Join-Path $PSScriptRoot 'VoiceProbe.java')
Check 'Probe compilation'
$classes = @(Get-ChildItem (Join-Path $out 'classes') -Recurse -Filter '*.class' | ForEach-Object FullName)
& (Join-Path $jdk 'bin/java.exe') -cp (Join-Path $sdk 'lib/d8.jar') com.android.tools.r8.D8 --lib (Join-Path $platform 'android.jar') --min-api 28 --output (Join-Path $out 'dex') @classes
Check 'Probe dex'
& (Join-Path $sdk 'aapt2.exe') link -I (Join-Path $platform 'android.jar') --manifest (Join-Path $PSScriptRoot 'AndroidManifest.xml') -o (Join-Path $out 'unsigned.apk')
Check 'Probe resources'
& (Join-Path $jdk 'bin/jar.exe') uf (Join-Path $out 'unsigned.apk') -C (Join-Path $out 'dex') classes.dex
Check 'Probe assembly'
& (Join-Path $sdk 'zipalign.exe') -f 4 (Join-Path $out 'unsigned.apk') (Join-Path $out 'aligned.apk')
Check 'Probe alignment'
& (Join-Path $jdk 'bin/java.exe') -jar (Join-Path $sdk 'lib/apksigner.jar') sign --ks (Join-Path $tools 'outervoice-signing.jks') --ks-key-alias outervoice --ks-pass ('file:' + (Join-Path $tools 'outervoice-signing-password.txt')) --out (Join-Path $out 'probe.apk') (Join-Path $out 'aligned.apk')
Check 'Probe signing'
Write-Output "Probe built: $out/probe.apk"
