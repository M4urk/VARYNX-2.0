@echo off
:: VARYNX Guardian Service — Windows Launcher
:: Runs the guardian service as a background process
:: Auto-start via registry key set by the installer

title VARYNX Guardian Service
cd /d "%~dp0"

:: Find Java (prefer bundled JRE, fall back to PATH)
if exist "%~dp0..\runtime\bin\java.exe" (
    set "JAVA_CMD=%~dp0..\runtime\bin\java.exe"
) else (
    set "JAVA_CMD=java"
)

:: Launch service
"%JAVA_CMD%" -Xmx256m -jar "%~dp0varynx-service.jar" 42400
