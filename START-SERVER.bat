@echo off
title PEERCHAT // SERVER CONSOLE
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
cd /d "%~dp0"
echo ==================================================
echo   STARTING PEERCHAT TACTICAL SERVER...
echo ==================================================
"C:\Program Files\Java\jdk-21\bin\java.exe" -cp target\classes com.peerchat.server.Server
pause
