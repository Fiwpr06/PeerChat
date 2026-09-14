@echo off
cd /d "%~dp0"

:: Khoi dong Client Terminal an hoan toan cua so console
start "" wscript.exe "%~dp0START-CLIENT.vbs"
exit /b 0
