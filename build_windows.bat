@echo off
setlocal

echo ===========================================
echo    MegumiDownloader Build Script (Windows) 
echo ===========================================

REM 1. Build Windows Standalone
echo [1/2] Building Windows Standalone Distributable...
call gradlew.bat :app:createReleaseDistributable

IF %ERRORLEVEL% NEQ 0 (
    echo Build Failed!
    exit /b %ERRORLEVEL%
)

REM 2. Pack Windows Version
echo [2/2] Packing Windows version...

set DIST_DIR=app\build\compose\binaries\main-release\app
set OUTPUT_ZIP=MegumiDownload-Windows.zip

IF EXIST "%DIST_DIR%" (
    echo Found distribution at %DIST_DIR%
    echo Zipping to %OUTPUT_ZIP%...
    
    REM Use PowerShell to zip because Windows has no native zip command line tool easily accessible
    powershell -Command "Compress-Archive -Path '%DIST_DIR%\*' -DestinationPath '%OUTPUT_ZIP%' -Force"
    
    echo Windows Standalone ZIP created at:
    echo   %OUTPUT_ZIP%
) ELSE (
    echo Error: Distributable directory not found at %DIST_DIR%
    exit /b 1
)

echo ===========================================
echo            Build Complete!                 
echo ===========================================
pause
