@echo off
title VoiceBlock - Fabric 1.21 SERVER (dev)
cd /d C:\Users\Administrator\Desktop\VoiceBlock-MultiVersion
echo ==========================================================
echo   VoiceBlock test server  -  Fabric 1.21 (dev)
echo ==========================================================
echo.
echo Wait until you see:   Done (x.xxxs)! For help, type "help"
echo To stop: type   stop   and press Enter.
echo.
call gradlew.bat :fabric-1.21:runServer --console=plain
echo.
pause
