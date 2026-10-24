$ErrorActionPreference = 'Stop'
$adb = 'C:/Users/basit/AppData/Local/Android/Sdk/platform-tools/adb.exe'
$folder = 'F:/CODE/healthathon/output/android/verification/0.6.1'
$classes = @('CarePainWebViewRuntimeTest','CareFeedbackUiTest','CareSymptomRoomFlowTest','WelcomeInlinePanelTest','CareInformationUiTest','CareVisibleActionsUiTest','DoctorCaptureHeroTest','DoctorEmbeddedNavigationTest','SaharaTitleTypographyTest','CareSymptomDiaryUiTest','CarePainHistoryUiTest')
$results = @()
foreach ($test in $classes) {
    $started = [DateTime]::UtcNow.ToString('o')
    $path = Join-Path $folder "$test-device.log"
    & $adb shell am instrument -w -r -e class "in.wedevit.care.$test" 'in.wedevit.sahara.development.test/androidx.test.runner.AndroidJUnitRunner' *> $path
    $exitCode = $LASTEXITCODE
    $output = Get-Content -LiteralPath $path -Raw
    $match = [regex]::Match($output, 'OK \((\d+) tests?\)')
    $passed = $exitCode -eq 0 -and $match.Success -and $output -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed'
    $results += [ordered]@{ class=$test; startedAt=$started; finishedAt=[DateTime]::UtcNow.ToString('o'); adbExitCode=$exitCode; passed=$passed; tests= $(if ($match.Success) {[int]$match.Groups[1].Value} else {0}); log="$test-device.log" }
    [ordered]@{ device='Samsung A34'; scope='Fictional or UUID-scoped fixtures only; no live accounts or production patient writes'; classes=$results } | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath (Join-Path $folder 'ux-device-test-results.json') -Encoding UTF8
    Write-Output "$test passed=$passed"
    if (!$passed) { Get-Content -LiteralPath $path -Tail 65 }
}
if ($results.Where({!$_.passed}).Count -gt 0) { exit 1 }
