@echo off
setlocal enabledelayedexpansion

echo.
echo ===============================================
echo     SMART STRUCTURE SCANNER
echo ===============================================
echo.

REM Get target directory
set "TARGET_DIR=%~1"
if "%TARGET_DIR%"=="" set "TARGET_DIR=%CD%"

if "%TARGET_DIR:~-1%"=="\" set "TARGET_DIR=%TARGET_DIR:~0,-1%"

echo Scanning: %TARGET_DIR%
echo.

set "OUTPUT=%TARGET_DIR%\STRUCTURE.txt"
type nul > "%OUTPUT%"

REM Temp file for processing
set "TEMP_LIST=%TEMP%\structure_list.txt"
type nul > "%TEMP_LIST%"

REM Collect all paths (directories and files)
for /f "delims=" %%i in ('dir /b /s "%TARGET_DIR%\*.java" 2^>nul') do (
    set "FULL=%%i"
    set "REL=!FULL:%TARGET_DIR%\=!"
    
    REM Replace backslash with forward slash (optional)
    set "REL=!REL:\=/!"
    
    REM Add to list
    echo !REL!>> "%TEMP_LIST%"
)

REM Sort the list
sort "%TEMP_LIST%" > "%TEMP_LIST%.sorted"

REM Process and extract unique directory paths
set "LAST_DIR="
for /f "delims=" %%p in (%TEMP_LIST%.sorted) do (
    set "PATH=%%p"
    
    REM Extract directory path
    for %%d in ("!PATH!") do set "DIR=%%~dpd"
    
    REM Remove trailing slash and convert to relative
    set "DIR=!DIR:~0,-1!"
    set "DIR=!DIR:%TARGET_DIR%\=!"
    set "DIR=!DIR:\=/!"
    
    REM Output directory if new
    if not "!DIR!"=="!LAST_DIR!" (
        if not "!DIR!"=="" (
            echo !DIR!>> "%OUTPUT%"
            echo !DIR!
        )
        set "LAST_DIR=!DIR!"
    )
    
    REM Output file
    echo !PATH!>> "%OUTPUT%"
    echo !PATH!
)

REM Cleanup
del "%TEMP_LIST%" "%TEMP_LIST%.sorted" 2>nul

echo.
echo ===============================================
echo ✅ Structure saved to: %OUTPUT%
echo ===============================================
echo.
pause