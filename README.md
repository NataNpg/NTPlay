# NTPlay - player de listas M3U/M3U8 (PC + Android)

Um único código (`www/index.html`) roda no PC (Electron) e no Android (Capacitor).

## PC (Windows)
Use o pacote pronto NTPlay-Windows.zip (NTPlay.exe), ou gere você mesmo:
    npm install
    npm run pc          # abre o app
    npm run pc:build    # instalador + versão portátil em dist/

## APK (Android) - sem instalar nada no seu PC
Suba esta pasta para um repositório no GitHub > Actions > "Build NTPlay" > Run workflow.
Baixe o artefato BOB-Player-apk (app-debug.apk) e instale no celular/TV Box.

## Recursos
Live TV / Filmes / Séries / Replay separados, séries agrupadas por temporada e episódio,
playlists salvas, favoritos, botão Voltar (Esc), opção "Sempre no VLC" (PC).
