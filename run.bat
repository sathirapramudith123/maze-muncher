@echo off
rem Double-click this file to compile and start Pac-Man (Windows).
cd /d "%~dp0Pacman\src"

where javac >nul 2>nul
if errorlevel 1 (
    echo Java JDK was not found. Install it from https://adoptium.net and try again.
    pause
    exit /b 1
)

echo Compiling Pac-Man...
javac *.java
if errorlevel 1 (
    echo.
    echo Compiling failed - see the errors above.
    pause
    exit /b 1
)

start "" javaw App
