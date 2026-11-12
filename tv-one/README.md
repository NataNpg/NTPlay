# NTPlay-one

Player de **IPTV para Android TV**, leve e simples, com motor de vídeo **libVLC**.
Deriva do NTPlay (app nativo de Android TV), trocando o player ExoPlayer por libVLC e
rodando **sem login obrigatório** — você põe sua própria lista e usa.

- **Pacote:** `com.natanael.ntplayone.tv` (instala ao lado do NTPlay normal, sem conflito)
- **Baixar o APK (sempre a última versão):**
  https://github.com/natanpg/NTPlay/releases/download/tv-one-latest/NTPlay-one.apk

## O que faz

- **Listas M3U e Xtream Codes** (servidor/usuário/senha). Várias listas ao mesmo tempo.
- **Passar a lista do celular por QR code**: na TV, abra **☰ Listas**; o celular (mesmo Wi-Fi)
  lê o QR e envia o link/M3U direto, sem precisar de internet externa.
- **Navegação Android TV**: TV ao vivo, Filmes, Séries, Animes, Favoritos, Recomendações —
  foco por controle remoto, capas oficiais (TMDB/TVmaze/iTunes), busca.
- **Player libVLC**: roda TS/HLS, canais ao vivo em pedaços, AC3/EAC3/DTS e HEVC/4K sem os
  problemas de codec do ExoPlayer. Controles próprios: pausar, avançar/voltar, velocidade,
  áudio, legenda, favoritar, trocar de canal, guia (EPG) e ajuste de tela.
- **VLC externo**: se algum vídeo não rodar no aparelho, botão "Abrir em outro player (VLC/MX)".
- **Atualização automática do próprio app** pela release `tv-latest`.

## Compilar

Tudo é compilado pelo **GitHub Actions** (`.github/workflows/android-tv-one.yml`), sem ambiente local:

- Todo push que mexe em `tv-one/**` dispara a compilação.
- O APK é publicado na release **`tv-one-latest`** (arquivo `NTPlay-one.apk`).
- **Assinatura:** sem segredos configurados, usa a **chave debug** (compila e instala na hora).
  Para atualizações por cima no futuro, crie uma chave fixa e os Secrets
  `NT_KEYSTORE_B64` + `NT_KS_PASS`.
- **TMDB (opcional):** Secret `TMDB_KEY` para capas e sinopses oficiais.

## Tecnologias

Kotlin · Jetpack Compose (Material3) · **libVLC 3.6** · OkHttp · Coil · ZXing (QR) ·
minSdk 21 · targetSdk 34 · só arquiteturas arm (APK menor).

---

Autor: **Natanael G Silva**. Projeto irmão do [NTPlay](https://github.com/natanpg/NTPlay).
