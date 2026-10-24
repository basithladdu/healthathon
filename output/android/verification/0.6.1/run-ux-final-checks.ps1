$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'C:/Program Files/Android/Android Studio/jbr'
$env:GRADLE_USER_HOME = 'F:/BuildCaches/healthathon-gradle'
$env:TEMP = 'F:/BuildCaches/sahara-release-tmp'
$env:TMP = $env:TEMP
$folder = 'F:/CODE/healthathon/output/android/verification/0.6.1'
Set-Location 'F:/CODE/healthathon/android'
$started = [DateTime]::UtcNow.ToString('o')
$log = Join-Path $folder 'ux-combined-checks.log'
# Only test execution and final packaging are forced; dependency compilation still checks final frozen inputs normally.
& ./gradlew.bat :app:testDebugUnitTest --rerun :app:packageDebug --rerun :app:packageDebugAndroidTest --rerun '-Pksp.incremental=false' '-Pkotlin.compiler.execution.strategy=in-process' --offline --no-daemon --max-workers=1 --console=plain '-Dorg.gradle.jvmargs=-Xms64m -Xmx1024m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -XX:CICompilerCount=2 -XX:MaxMetaspaceSize=512m -XX:ReservedCodeCacheSize=96m -Xss512k -Dfile.encoding=UTF-8 -Djava.io.tmpdir=F:/BuildCaches/sahara-release-tmp' *> $log
$result = $LASTEXITCODE
@{ startedAt=$started; finishedAt=[DateTime]::UtcNow.ToString('o'); exitCode=$result } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $folder 'ux-combined-checks-exit.json') -Encoding UTF8
Get-Content -LiteralPath $log -Tail 65
exit $result
