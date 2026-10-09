# Employee Database - Enterprise Manager Portal Launcher
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "Launching Employee Database - Enterprise Manager Portal" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan

$mavenPath = "C:\Users\ssuba\apache-maven-3.9.9\bin"
if (Test-Path $mavenPath) {
    $env:Path = "$mavenPath;" + $env:Path
}

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location -Path $scriptDir

try {
    mvn javafx:run
} catch {
    Write-Host "Falling back to direct Java runtime execution..." -ForegroundColor Yellow
    $m2 = "C:\Users\ssuba\.m2\repository"
    $fxPath = "$m2\org\openjfx\javafx-base\21.0.6\javafx-base-21.0.6-win.jar;$m2\org\openjfx\javafx-controls\21.0.6\javafx-controls-21.0.6-win.jar;$m2\org\openjfx\javafx-graphics\21.0.6\javafx-graphics-21.0.6-win.jar"
    $cp = "target\classes;$m2\org\xerial\sqlite-jdbc\3.49.1.0\sqlite-jdbc-3.49.1.0.jar"
    java --module-path $fxPath --add-modules javafx.controls,javafx.graphics,javafx.base -cp $cp ManagerPortal
}
