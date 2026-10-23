@echo off
title VoiceBlock - Forge 1.20.1 SERVER (dev)
cd /d C:\Users\Administrator\Desktop\VoiceBlock-MultiVersion
echo ==========================================================
echo   VoiceBlock test server  -  Forge 1.20.1 (dev)
echo ==========================================================
echo.
echo Starting... first launch takes 30-60 seconds.
echo Wait until you see:   Done (x.xxxs)! For help, type "help"
echo To stop: type   stop   and press Enter.
echo.
call gradlew.bat :forge-1.20.1:runServer --console=plain
echo.
pause
