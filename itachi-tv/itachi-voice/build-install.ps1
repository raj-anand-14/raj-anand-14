# Builds Itachi Voice and installs it on the TV. Run through build-install.bat (double-click).
# Everything shown here is also written to work\itachi-voice\build.log so Claude can read it.
param([Parameter(Mandatory = $true)][string]$TvHost)
$ErrorActionPreference = 'Continue'
$here = $PSScriptRoot
$repo = Resolve-Path (Join-Path $here '..\..')
Start-Transcript -Path (Join-Path $here 'build.log') -Force | Out-Null
try {
    Write-Host "== Finding JDK 17+"
    $javaCandidates = @()
    # The JDK and SDK Codex downloaded for the Itachi launcher build.
    $tempBuilds = Get-ChildItem $env:TEMP -Directory -Filter 'cleanyourtv-build-*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending
    foreach ($t in $tempBuilds) { $javaCandidates += Get-ChildItem (Join-Path $t.FullName 'jdk') -Directory -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName } }
    if ($env:JAVA_HOME) { $javaCandidates += $env:JAVA_HOME }
    $javaCandidates += Get-ChildItem 'C:\Program Files\Android' -Directory -ErrorAction SilentlyContinue | ForEach-Object { Join-Path $_.FullName 'jbr' }
    foreach ($root in @("$env:USERPROFILE\.jdks", 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java', 'C:\Program Files\Microsoft', 'C:\Program Files\Zulu', "$env:LOCALAPPDATA\Programs")) {
        $javaCandidates += Get-ChildItem $root -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match 'jdk|jbr|temurin|zulu' } | ForEach-Object { $_.FullName }
    }
    $java = $null
    foreach ($c in $javaCandidates) {
        if (-not (Test-Path "$c\bin\java.exe")) { continue }
        $v = (& "$c\bin\java.exe" -version 2>&1 | Select-Object -First 1) -as [string]
        if ($v -match '"(\d+)') { if ([int]$Matches[1] -ge 17) { $java = $c; Write-Host "   $c  ($v)"; break } }
    }
    if (-not $java) { throw "No JDK 17+ found. Looked in: $($javaCandidates -join '; ')" }
    $env:JAVA_HOME = $java

    Write-Host "== Finding Android SDK"
    $sdkCandidates = @($tempBuilds | ForEach-Object { Join-Path $_.FullName 'android-sdk' })
    $sdkCandidates += @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, "$env:LOCALAPPDATA\Android\Sdk", 'C:\Android\Sdk', 'C:\Android\android-sdk') | Where-Object { $_ }
    $sdkCandidates += Get-ChildItem "$env:USERPROFILE\Documents\Github", "$env:USERPROFILE\Documents", "$env:USERPROFILE", 'C:\' -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match 'sdk' } | ForEach-Object { $_.FullName }
    $sdk = $sdkCandidates | Where-Object { Test-Path (Join-Path $_ 'platforms') } | Select-Object -First 1
    if (-not $sdk) { throw "No Android SDK found. Looked in: $($sdkCandidates -join '; ')" }
    Write-Host "   $sdk"
    $env:ANDROID_HOME = $sdk
    "sdk.dir=$($sdk -replace '\\', '\\')" | Out-File -Encoding ascii (Join-Path $here 'local.properties')

    Write-Host "== Building (first build can take a few minutes)"
    Push-Location $here
    & .\gradlew.bat --no-daemon assembleRelease 2>&1 | ForEach-Object { Write-Host "$_" }
    $code = $LASTEXITCODE
    Pop-Location
    if ($code -ne 0) { throw "Gradle build failed (exit $code). The errors are above." }
    $apk = Join-Path $here 'app\build\outputs\apk\release\app-release.apk'
    Write-Host "   APK: $apk ($((Get-Item $apk).Length) bytes)"

    Write-Host "== Installing on the TV at $TvHost"
    $env:TV_HOST = $TvHost
    $py = Join-Path $repo 'tools\venv\Scripts\python.exe'
    Push-Location $repo
    & $py -I tools\b64push.py $apk /data/local/tmp/itachi-voice.apk 2>&1 | Select-Object -Last 3 | ForEach-Object { Write-Host "   $_" }
    if ($LASTEXITCODE -ne 0) { Pop-Location; throw 'Copying the APK to the TV failed. Is the TV on and ADB allowed?' }
    $out = (& $py -I tools\tv.py 'pm install -r /data/local/tmp/itachi-voice.apk; rm -f /data/local/tmp/itachi-voice.apk' 2>&1) -join ' '
    Write-Host "   $out"
    if ("$out" -notmatch 'Success') { Pop-Location; throw 'pm install failed.' }
    & $py -I tools\tv.py 'pm grant com.itachi.voice android.permission.RECORD_AUDIO && echo mic-granted' 2>&1 | ForEach-Object { Write-Host "   $_" }
    & $py -I tools\tv.py 'am start -n com.itachi.voice/.VoiceActivity' 2>&1 | ForEach-Object { Write-Host "   $_" }
    Pop-Location
    Write-Host ""
    Write-Host "Installed. Itachi Voice is open on the TV."
}
catch {
    Write-Host ""
    Write-Host "FAILED: $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    Stop-Transcript | Out-Null
}
