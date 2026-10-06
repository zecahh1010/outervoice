param([switch]$TestOnly)
$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$toolsRoot = Join-Path (Split-Path $projectRoot -Parent) '.build-tools'
$jdk = (Get-ChildItem -LiteralPath (Join-Path $toolsRoot 'java') -Directory | Select-Object -First 1).FullName
$sdkBuild = (Get-ChildItem -LiteralPath (Join-Path $toolsRoot 'android-build') -Directory | Select-Object -First 1).FullName
$sdkPlatform = (Get-ChildItem -LiteralPath (Join-Path $toolsRoot 'android-platform') -Directory | Select-Object -First 1).FullName
$androidJar = Join-Path $sdkPlatform 'android.jar'
$java = Join-Path $jdk 'bin/java.exe'
$javac = Join-Path $jdk 'bin/javac.exe'
$build = Join-Path $projectRoot 'build'
$dist = Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Force -Path $build,$dist,(Join-Path $build 'classes'),(Join-Path $build 'dex'),(Join-Path $build 'tests') | Out-Null
function Check-Exit($step) { if ($LASTEXITCODE -ne 0) { throw "$step failed ($LASTEXITCODE)" } }
& $javac -encoding UTF-8 -d (Join-Path $build 'tests') (Join-Path $projectRoot 'app/src/main/java/com/zecadev/outervoice/WavFormat.java') (Join-Path $projectRoot 'tests/WavFormatTest.java')
Check-Exit 'WAV test compilation'
& $java -cp (Join-Path $build 'tests') WavFormatTest
Check-Exit 'WAV format tests'
& $javac -encoding UTF-8 -classpath (Join-Path $build 'tests') -d (Join-Path $build 'tests') (Join-Path $projectRoot 'app/src/main/java/com/zecadev/outervoice/WavTools.java') (Join-Path $projectRoot 'tests/WavToolsTest.java')
Check-Exit 'WAV tools test compilation'
& $java -cp (Join-Path $build 'tests') WavToolsTest
Check-Exit 'WAV writing and normalization tests'
if ($TestOnly) { return }
$resources = Join-Path $build 'resources.zip'
& (Join-Path $sdkBuild 'aapt2.exe') compile --dir (Join-Path $projectRoot 'app/src/main/res') -o $resources
Check-Exit 'Resource compilation'
$resourceApk = Join-Path $build 'resources.apk'
& (Join-Path $sdkBuild 'aapt2.exe') link -I $androidJar --manifest (Join-Path $projectRoot 'app/src/main/AndroidManifest.xml') -A (Join-Path $projectRoot 'app/src/main/assets') -o $resourceApk $resources
Check-Exit 'Resource linking'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app/src/main/java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
& $javac -encoding UTF-8 --release 8 -classpath $androidJar -d (Join-Path $build 'classes') @sources
Check-Exit 'Java compilation'
$classes = @(Get-ChildItem -LiteralPath (Join-Path $build 'classes') -Recurse -Filter '*.class' | ForEach-Object { $_.FullName })
& $java -cp (Join-Path $sdkBuild 'lib/d8.jar') com.android.tools.r8.D8 --lib $androidJar --min-api 28 --output (Join-Path $build 'dex') @classes
Check-Exit 'DEX compilation'
$unsigned = Join-Path $build 'unsigned.apk'
Copy-Item -LiteralPath $resourceApk -Destination $unsigned -Force
& (Join-Path $jdk 'bin/jar.exe') uf $unsigned -C (Join-Path $build 'dex') classes.dex
Check-Exit 'APK assembly'
$aligned = Join-Path $build 'aligned.apk'
& (Join-Path $sdkBuild 'zipalign.exe') -f 4 $unsigned $aligned
Check-Exit 'ZIP alignment'
$key = Join-Path $toolsRoot 'outervoice-signing.jks'
$passwordFile = Join-Path $toolsRoot 'outervoice-signing-password.txt'
if (-not (Test-Path -LiteralPath $key)) {
    $signingPassword = [Guid]::NewGuid().ToString('N')
    [System.IO.File]::WriteAllText($passwordFile, $signingPassword, (New-Object System.Text.UTF8Encoding($false)))
    & (Join-Path $jdk 'bin/keytool.exe') -genkeypair -keystore $key -storetype JKS -storepass:file $passwordFile -keypass:file $passwordFile -alias outervoice -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Zeca, OU=Outer Voice'
    Check-Exit 'Signing key creation'
}
$apk = Join-Path $dist 'OuterVoice-1.2.2.apk'
& $java -jar (Join-Path $sdkBuild 'lib/apksigner.jar') sign --ks $key --ks-key-alias outervoice --ks-pass "file:$passwordFile" --out $apk $aligned
Check-Exit 'APK signing'
& $java -jar (Join-Path $sdkBuild 'lib/apksigner.jar') verify --verbose --print-certs $apk
Check-Exit 'APK signature verification'
& (Join-Path $sdkBuild 'zipalign.exe') -c 4 $apk
Check-Exit 'APK alignment verification'
$digest = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
[System.IO.File]::WriteAllText((Join-Path $dist 'SHA256.txt'), "$digest  OuterVoice-1.2.2.apk`n", (New-Object System.Text.UTF8Encoding($false)))
Write-Output "APK built: $apk"
