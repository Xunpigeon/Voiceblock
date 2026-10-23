@echo off
title VoiceBlock - Forge 1.20.1 CLIENT (dev)
cd /d C:\Users\Administrator\Desktop\VoiceBlock-MultiVersion
echo Starting Forge 1.20.1 client...
echo In game: Multiplayer - Direct Connection - 127.0.0.1
echo.
call gradlew.bat :forge-1.20.1:runClient --console=plain
echo.
pause
