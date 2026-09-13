@echo off
title PEERCHAT // CLIENT TERMINAL
set "JAVA_HOME=C:\Program Files\Java\jdk-21"
cd /d "%~dp0"
echo ==================================================
echo   LAUNCHING PEERCHAT CLIENT TERMINAL...
echo ==================================================
set "JFX=C:\Users\fiwpr\.m2\repository\org\openjfx\javafx-base\21.0.6\javafx-base-21.0.6-win.jar;C:\Users\fiwpr\.m2\repository\org\openjfx\javafx-controls\21.0.6\javafx-controls-21.0.6-win.jar;C:\Users\fiwpr\.m2\repository\org\openjfx\javafx-fxml\21.0.6\javafx-fxml-21.0.6-win.jar;C:\Users\fiwpr\.m2\repository\org\openjfx\javafx-graphics\21.0.6\javafx-graphics-21.0.6-win.jar"
"C:\Program Files\Java\jdk-21\bin\java.exe" --module-path "target\classes;%JFX%" --add-modules javafx.controls,javafx.fxml,javafx.graphics,com.peerchat -m com.peerchat/com.peerchat.client.Main
pause
