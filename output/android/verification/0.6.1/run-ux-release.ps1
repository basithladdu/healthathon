$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:GRADLE_USER_HOME = 'F:/BuildCaches/healthathon-gradle'
$env:TEMP = 'F:/BuildCaches/sahara-release-tmp'
$env:TMP = $env:TEMP
Set-Location 'F:/CODE/healthathon/android'
$folder = 'F:/CODE/healthathon/output/android/verification/0.6.1'
$started = [DateTime]::UtcNow.ToString('o')
$log = Join-Path $folder 'ux-release-build.log'
& ./gradlew.bat :app:assembleRelease :app:bundleRelease '-Pksp.incremental=false' '-Pkotlin.compiler.execution.strategy=in-process' --offline --no-daemon --max-workers=1 --console=plain '-Dorg.gradle.jvmargs=-Xms64m -Xmx1536m -XX:+UseSerialGC -XX:NewRatio=4 -XX:ActiveProcessorCount=2 -XX:CICompilerCount=2 -XX:MaxMetaspaceSize=512m -XX:ReservedCodeCacheSize=96m -Xss512k -Dfile.encoding=UTF-8 -Djava.io.tmpdir=F:/BuildCaches/sahara-release-tmp' *> $log
$result = $LASTEXITCODE
@{ startedAt=$started; finishedAt=[DateTime]::UtcNow.ToString('o'); exitCode=$result } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $folder 'ux-release-build-exit.json') -Encoding UTF8
Get-Content -LiteralPath $log -Tail 50
exit $result
