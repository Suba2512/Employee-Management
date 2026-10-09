@echo off
setlocal
echo ========================================================
echo Launching Employee Database - Enterprise Manager Portal
echo ========================================================

set "MAVEN_PATH=C:\Users\ssuba\apache-maven-3.9.9\bin"
if exist "%MAVEN_PATH%" (
    set "PATH=%MAVEN_PATH%;%PATH%"
)

cd /d "%~dp0"
call mvn javafx:run
if errorlevel 1 (
    echo.
    echo Running via direct JavaFX launcher fallback...
    set "M2=C:\Users\ssuba\.m2\repository"
    set "FX_PATH=%M2%\org\openjfx\javafx-base\21.0.6\javafx-base-21.0.6-win.jar;%M2%\org\openjfx\javafx-controls\21.0.6\javafx-controls-21.0.6-win.jar;%M2%\org\openjfx\javafx-graphics\21.0.6\javafx-graphics-21.0.6-win.jar"
    set "CP=target\classes;%M2%\org\xerial\sqlite-jdbc\3.49.1.0\sqlite-jdbc-3.49.1.0.jar"
    java --module-path "%FX_PATH%" --add-modules javafx.controls,javafx.graphics,javafx.base -cp "%CP%" ManagerPortal
)

pause
