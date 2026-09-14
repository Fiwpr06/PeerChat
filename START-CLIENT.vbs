' ==============================================================================
' PEERCHAT // KHOI DONG CLIENT TERMINAL AN HOAN TOAN CUA SO CONSOLE / TERMINAL
' ==============================================================================
Option Explicit

Dim WshShell, fso, scriptDir, javaHome, userProfile, jfxPath, javaExe, cmd

Set WshShell = CreateObject("WScript.Shell")
Set fso = CreateObject("Scripting.FileSystemObject")

' Lay duong dan thu muc hien tai
scriptDir = fso.GetParentFolderName(WScript.ScriptFullName)
WshShell.CurrentDirectory = scriptDir

' Tim kiem duong dan Java (uu tien bien moi truong, sau do den JDK 21 mac dinh)
javaHome = WshShell.Environment("PROCESS")("JAVA_HOME")
If javaHome = "" Then
    javaHome = WshShell.Environment("SYSTEM")("JAVA_HOME")
End If
If javaHome = "" Or Not fso.FileExists(javaHome & "\bin\javaw.exe") Then
    If fso.FileExists("C:\Program Files\Java\jdk-21\bin\javaw.exe") Then
        javaHome = "C:\Program Files\Java\jdk-21"
    End If
End If

If javaHome <> "" And fso.FileExists(javaHome & "\bin\javaw.exe") Then
    javaExe = """" & javaHome & "\bin\javaw.exe"""
Else
    javaExe = "javaw.exe"
End If

' Thu vien JavaFX tu thu muc .m2
userProfile = WshShell.ExpandEnvironmentStrings("%USERPROFILE%")
jfxPath = userProfile & "\.m2\repository\org\openjfx\javafx-base\21.0.6\javafx-base-21.0.6-win.jar;" & _
          userProfile & "\.m2\repository\org\openjfx\javafx-controls\21.0.6\javafx-controls-21.0.6-win.jar;" & _
          userProfile & "\.m2\repository\org\openjfx\javafx-fxml\21.0.6\javafx-fxml-21.0.6-win.jar;" & _
          userProfile & "\.m2\repository\org\openjfx\javafx-graphics\21.0.6\javafx-graphics-21.0.6-win.jar"

cmd = javaExe & " --module-path ""target\classes;" & jfxPath & """ --add-modules javafx.controls,javafx.fxml,javafx.graphics,com.peerchat -m com.peerchat/com.peerchat.client.Main"

' Chay ung dung voi javaw (javaw khong co console, 1 giup hien thi giao dien GUI JavaFX binh thuong)
WshShell.Run cmd, 1, False
