@echo off
rem BOB Player - abre o app sem Node.js, usando Edge ou Chrome (CORS desativado so neste perfil)
set "P=%~dp0www\index.html"
set "URL=file:///%P:\=/%"
set "FLAGS=--app="%URL%" --disable-web-security --user-data-dir="%LOCALAPPDATA%\BOBPlayer" --autoplay-policy=no-user-gesture-required"

if exist "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" ( start "" "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" %FLAGS% & exit /b )
if exist "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" ( start "" "%ProgramFiles%\Microsoft\Edge\Application\msedge.exe" %FLAGS% & exit /b )
if exist "%ProgramFiles%\Google\Chrome\Application\chrome.exe" ( start "" "%ProgramFiles%\Google\Chrome\Application\chrome.exe" %FLAGS% & exit /b )
if exist "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" ( start "" "%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe" %FLAGS% & exit /b )
echo Edge ou Chrome nao encontrado. Instale um deles e tente de novo.
pause
