@echo off
cd /d "%~dp0"

if "%1"=="--cli" goto :cli
if "%1"=="-cli" goto :cli

:: Khoi dong Server GUI an hoan toan cua so console
start "" wscript.exe "%~dp0START-SERVER.vbs"
exit /b 0

:cli
:: Che do Console CLI truyen thong
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
echo ==================================================
echo   LAUNCHING PEERCHAT SERVER (CLI MODE)...
echo ==================================================
"C:\Program Files\Java\jdk-21\bin\java.exe" -cp target\classes com.peerchat.server.Server %*
