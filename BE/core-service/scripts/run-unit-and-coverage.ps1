param(
  [string]$TestClass = "JobAdCandidateServiceImplTest"
)

$ErrorActionPreference = "Stop"

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\\java.exe"))) {
  $javaSettings = cmd /c "java -XshowSettings:properties -version 2>&1"
  $detectedJavaHome = ($javaSettings |
    Select-String "java.home" |
    ForEach-Object { ($_ -split "=")[1].Trim() } |
    Select-Object -First 1)

  if ($detectedJavaHome -and (Test-Path (Join-Path $detectedJavaHome "bin\\java.exe"))) {
    $env:JAVA_HOME = $detectedJavaHome
    $env:Path = "$env:JAVA_HOME\\bin;$env:Path"
    Write-Host "[JAVA] Auto-set JAVA_HOME=$env:JAVA_HOME" -ForegroundColor Yellow
  }
}

if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME "bin\\java.exe"))) {
  throw "JAVA_HOME is not set correctly. Please set JAVA_HOME to your JDK path before running this script."
}

Write-Host "[1/4] Running unit tests for $TestClass ..." -ForegroundColor Cyan
& .\mvnw.cmd "-Dtest=$TestClass" test
if ($LASTEXITCODE -ne 0) {
  throw "Unit tests failed (test phase)."
}

Write-Host "[2/4] Generating JaCoCo report ..." -ForegroundColor Cyan
& .\mvnw.cmd "-Dtest=$TestClass" verify
if ($LASTEXITCODE -ne 0) {
  throw "Build failed (verify phase)."
}

Write-Host "[3/4] Test result files:" -ForegroundColor Green
Write-Host "- target\surefire-reports\*.txt"
Write-Host "- target\surefire-reports\TEST-*.xml"

Write-Host "[4/4] Coverage report files:" -ForegroundColor Green
if (Test-Path ".\target\site\jacoco\index.html") {
  Write-Host "- target\site\jacoco\index.html"
} else {
  Write-Host "- target\site\jacoco\index.html (NOT FOUND)"
}

if (Test-Path ".\target\jacoco.exec") {
  Write-Host "- target\jacoco.exec"
} else {
  Write-Host "- target\jacoco.exec (NOT FOUND)"
}

Write-Host "Open coverage report:" -ForegroundColor Yellow
Write-Host "start target\site\jacoco\index.html"
