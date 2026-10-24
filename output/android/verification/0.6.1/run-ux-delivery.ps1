& 'F:/CODE/healthathon/output/android/verification/0.6.1/run-ux-final-checks.ps1'
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Output 'DEBUG_CHECKS_COMPLETE_RELEASE_BUILD_STARTING'
& 'F:/CODE/healthathon/output/android/verification/0.6.1/run-ux-release.ps1'
exit $LASTEXITCODE
