# NTPlay — PC (Windows / Electron)

Resumo técnico para futuras atualizações. Autor: **Natanael G Silva** · djnatan0@live.com
Situação em 20/10/2026 · repositório `natanpg/NTPlay` (raiz, `electron/` + `www/`)

---

## 1. O que é

Player IPTV (M3U / Xtream) para Windows, feito em **Electron**. A interface web (`www/`) é a mesma base das versões navegador, celular (Capacitor) e iOS.

- **Versão atual:** **3.2.0** (`package.json`)
- **Downloads (sempre a última versão):**
  - Instalador: `https://github.com/NataNpg/NTPlay/releases/download/pc-latest/NTPlay.Setup.<versão>.exe`
  - Portátil: `https://github.com/NataNpg/NTPlay/releases/download/pc-latest/NTPlay.<versão>.exe`
  - Página da release: https://github.com/NataNpg/NTPlay/releases/tag/pc-latest
- **Página para enviar a lista pelo QR code (GitHub Pages):** https://natanpg.github.io/NTPlay/enviar.html
- **Painel de usuários (GitHub Pages):** https://natanpg.github.io/NTPlay/admin.html — pede a chave privada (arquivo `ntplay-painel-chave.json`, **fora do repositório**, com o dono).

## 2. Tecnologias

| Item | Detalhe |
|---|---|
| App | Electron 31, electron-builder 24 (NSIS + portátil, `--publish never`) |
| Player | hls.js 1.5.7, mpegts.js 1.7.3, `<video>` nativo, **FFmpeg** (`ffmpeg-static` 5.3.0, fora do asar via `asarUnpack`) |
| Outros | qrcode-generator 1.4.4, Capacitor 6 (celular/iOS, mesmo código web) |
| Vendor | `npm run vendor` copia hls/mpegts/qrcode para `www/vendor/`, que é ignorado no git |
| Config | `www/config.js` (lista padrão com usuário/senha), **ignorado no git**. Modelo: `www/config.example.js` |

## 3. Compilação e publicação

- Workflow: `.github/workflows/build.yml` (manual, `workflow_dispatch`).
  - Jobs: Android (Capacitor), iOS (IPA sem assinatura), Windows e **`pc-latest`**. Este último publica os `.exe` na release `pc-latest`, apagando e recriando.
  - O job `release` só roda com tag `v*`. Criar tag pelo ambiente do Claude é bloqueado, por isso foi criado o `pc-latest`.
- Para lançar uma versão: aumentar `"version"` no `package.json` → push → `gh workflow run build.yml --ref main`.
- O GitHub Pages é publicado por `.github/workflows/pages.yml` (pasta `www`).

## 4. Arquivos principais

| Arquivo | Função |
|---|---|
| `electron/main.js` | Janela e atalhos (Ctrl+O abrir M3U, F11 tela cheia, F12 DevTools, Ctrl+R recarregar), User-Agent VLC, abrir no VLC e o **repasse local de vídeo** (seção 5). |
| `electron/preload.js` | Expõe `bobNative`: `openVlc`, `players`, `openExt`, `ffOk`, `pickFile`, `proxyPort`, `onOpen`. |
| `www/js/app.js` | Toda a interface e o player: biblioteca, abas, séries, favoritos, progresso, metadados, QR, escolha do player (`order()` / `play()`) e o **painel de diagnóstico (Ctrl+D)**. |
| `www/js/platform.js`, `native.js` | Adaptações por plataforma (celular, TV via Capacitor). |
| `www/index.html`, `css/app.css` | Layout. |
| `www/js/futebol.js` | Aba **FUTEBOL**: jogos do dia pela API pública da ESPN (sem chave) e canais da lista que passam os jogos. Carregado antes do `app.js`. |
| `www/js/presenca.js` | Presença criptografada para o painel (ver seção 6c). Carregado depois do `app.js`. |
| `www/admin.html` | Painel: quantos aparelhos usam o NTPlay agora e as listas de cada um. |
| `www/enviar.html` | Página do celular para enviar a lista (QR code). |

## 5. Repasse local de vídeo (`electron/main.js`)

Servidor HTTP em `127.0.0.1:<porta aleatória>`, informada ao app via IPC `proxy-port` / `window.NT_PROXY`.

- `/p?u=<url>`:
  - busca o canal como **VLC**, sem os cabeçalhos de navegador que os servidores IPTV bloqueiam;
  - segue redirecionamentos (até 8) e **reescreve listas HLS** para os pedaços também passarem pelo repasse.
- `/p?live=1&u=<url>` — **canais ao vivo em pedaços:**
  - o servidor do usuário entrega ~5–8 MB (~20 s) e fecha a conexão;
  - o repasse pede o próximo pedaço e **emenda na mesma resposta**;
  - só repassa **pacotes TS inteiros de 188 bytes** e realinha no byte `0x47` a cada pedaço. Sem isso, o mpegts.js dava "sync_byte = 255, not 0x47" e o vídeo congelava;
  - até 12 falhas seguidas com nova tentativa.
- **Cache de redirecionamento:** 10 min, só para pedidos que não são ao vivo. Nos pedaços ao vivo fica desligado, porque os tokens são de uso único (o servidor responde 409).
- `/probe?u=` existe, mas **não é mais usado no ao vivo**: em contas de 1 tela a consulta extra derrubava o vídeo.
- `/log` devolve as últimas 40 linhas do log do repasse (aparece no painel Ctrl+D).
- **Recusa do servidor antes do primeiro byte** (403/401/429/5xx, típico de conta com 1 tela ainda presa à conexão anterior): o repasse espera e tenta de novo até 4 vezes (1,5 s, 3 s, 4,5 s, 6 s). 400/404/410 não são repetidos. A última recusa fica em `/lasterr` (status, texto do servidor, host).
- `/ff?u=&live=0|1&ss=&v=copy|enc&id=` — **modo compatibilidade (FFmpeg)**. A entrada é sempre o próprio repasse (`/p?…`), então vale a cara de VLC, a espera da conexão única, os certificados ruins e a emenda dos pedaços. `copy` mantém o vídeo e converte o áudio para AAC (resolve AC3/EAC3/DTS mudos); `enc` converte o vídeo para H.264 (HEVC 10 bits, 4K) com `h264_nvenc` → `h264_qsv` → `h264_amf` → `libx264` (até 1080p), detectado uma vez no início. Saída MPEG-TS para o mpegts.js. `/ffinfo?id=` devolve duração, codecs e erro.
- **Players externos** (`open-ext`): VLC, mpv/mpv.net, PotPlayer, MPC-HC/MPC-BE, procurados em Arquivos de Programas e `%LOCALAPPDATA%\Programs`. Canais ao vivo vão pelo repasse (`/p?live=1`), filmes pelo link direto.
- Keep-alive desligado, para a conexão fechar de verdade ao trocar de canal.

**Ordem de tentativa** (`app.js`, função `play(c, auto, force)`):
- Ao vivo: `mpegts` (repasse, live=1) → `hls` → `native` → `ffcopy` → `ffenc`. Se o link termina em `.m3u8`, tenta antes a versão `.ts` (`mpegtsT`).
- Filmes/séries: `native` → `hls`/`mpegts` → `ffcopy` → `ffenc`.
- Nome com 4K/UHD/H.265/HEVC e Chromium sem HEVC (`MediaSource.isTypeSupported('…hvc1…')`): começa no `ffenc`. Preferência "Reprodução no PC" (Listas M3U → Preferências): automática / sempre `ffcopy` / sempre `ffenc` (`LS.ntPMode`).
- Se o mpegts/hls recebe recusa HTTP (≠404) depois das tentativas do repasse, para a fila (os outros players pegariam a mesma recusa) e mostra o motivo (`whyFail`): conta vencida/bloqueada, telas em uso (`player_api.php`: `active_cons`/`max_connections`), canal fora do ar (404) ou servidor com problema.
- 6 s depois de começar: sem quadros de vídeo → `ffenc`; sem áudio decodificado (`webkitAudioDecodedByteCount == 0`) → `ffcopy`, no mesmo ponto do filme.
- Modo FFmpeg em filmes: o `<video>` fica sem controles nativos e aparece a barra própria (`#ffb`): avançar/voltar reinicia o FFmpeg com `-ss`. O progresso usa `curT()`/`durT()` (base + tempo do vídeo).

## 6. Diagnóstico

- **Ctrl+D:** painel com o canal, cada tentativa de player, erros do hls.js, estado do vídeo (tempo, duração, pausado/fim) e o log do repasse (status, tempo de resposta, redirecionamentos, tamanho dos pedaços).
- **F12:** DevTools. Ficaram quebradas na 3.0.1–3.0.2 porque os cabeçalhos eram alterados também em `devtools://`; corrigido para alterar só `http(s)`.
- Pelo console: `S.cur.c.url` (canal atual), `NT_PROXY`, `mpegts`, `Hls`.

## 6b. Biblioteca, capas e abas (3.1.0)

- **Séries:** o nome é limpo por `serT()` ("Colony S03 Colony" → "Colony") e a chave é o nome normalizado (+ `|L` para legendado), então as temporadas que vinham separadas se juntam. Episódio repetido (mesmo S/E) é ignorado. O título do episódio aparece sem o prefixo (`epName`). O botão mostra o primeiro episódio real (T3 E1 quando só existe a T3).
- **Capas e sinopses** (`getInfo`, cache `LS.ntInfo2` — o antigo `ntInfo` é apagado para refazer as capas erradas): Xtream do próprio servidor (`get_vod_info` pelo ID do filme; `get_series` uma vez por conta e `get_series_info` quando falta sinopse) → TMDB (se houver chave v3 ou token v4; usa `tmdb_id` do Xtream quando existe; escolhe o resultado por título + ano, não o primeiro) → TVMaze (séries) / iTunes (filmes) → Wikipédia pt → iTunes/TVMaze e Wikipédia en (traduzida). A comparação de títulos (`okT`) exige nome igual ou ≥ 85% das palavras, e o ano não pode divergir.
- **Gêneros TMDB em português:** tabela `TG` com os gêneros de `/3/genre/movie/list?language=pt-BR` e `/3/genre/tv/list`, atualizada pela API quando há chave.
- **Animes:** `theme()` marca ANIMES pelo grupo (anime, otaku, Crunchyroll, Animax…), por uma lista de títulos japoneses conhecidos (`ANI_T`) ou quando o TMDB/TVMaze dizem que é animação japonesa (`INFO.an`). Anime tem prioridade sobre Infantil.
- **FUTEBOL:** `futebol.js`. Busca o placar de ~33 campeonatos (`site.api.espn.com/apis/site/v2/sports/soccer/<liga>/scoreboard?dates=AAAAMMDD&lang=pt&region=br`), agrupa por campeonato, mostra horário/ao vivo/placar e liga cada jogo aos canais da lista que têm os nomes dos times (canais de evento). Atualiza a cada 60 s com jogo ao vivo. Abaixo, "Canais de jogos e eventos" e "Canais de esporte" (regex `FUT_SP`).
- **Selo de vencimento das listas** (igual ao Android TV): `player_api.php` da conta (`exp_date`, `status`, `max_connections`, `active_cons`), cache de 6 h em `LS.ntAcct`; o ↻ força a checagem. Verde > 7 dias, amarelo ≤ 7, vermelho vencida, mais "telas em uso/permitidas".

## 6c. Travadas, futebol ao vivo e painel (3.2.0)

- **Travadas no ao vivo:** o servidor entrega o canal em pedaços e cada pedaço novo vem com os tempos (PTS/DTS) saltando para frente ou para trás. O VLC refaz os tempos; o mpegts.js não, e o vídeo parava. Agora, no PC, o ao vivo começa pelo `ffcopy` (FFmpeg copia o vídeo e refaz os tempos — testado: fluxo emendado com saltos sai contínuo). Preferência "Canais ao vivo pelo FFmpeg" (`LS.ntLiveFF`, ligado por padrão). Além disso o player tem um **pulador de buracos** (`gapJump`): se o vídeo fica parado antes de um trecho que já chegou, pula para ele; no ao vivo, se ficar mais de 45 s atrás, avança. O reinício automático por espera só acontece depois de 25 s no ao vivo e não acontece se ainda está chegando dado.
- **Séries sem SxxEyy:** `epInfo` reconhece "EP01", "Episódio 12", "Capítulo 3", "E05" (temporada por "Temporada 2"/"T2"); e nome terminado em número dentro de grupo de séries. Antes cada episódio virava uma série com a mesma capa e a capa era descartada como "genérica". A regra da capa genérica agora conta títulos diferentes.
- **Futebol — passando agora:** `get_short_epg` (Xtream) de cada canal de esporte (uma consulta por canal-base, sem FHD/HD/SD, 4 em paralelo, até 160, cache 5 min). Canal entra em "Passando jogo agora" se o programa atual parece jogo (`FUT_GAME`) e não é reprise/debate (`FUT_NOT`), ou se é canal de evento com os times de um jogo ao vivo. Os cartões dos jogos mostram só canais da programação ou com os nomes dos times. A lista completa de canais de esporte fica atrás de um botão.
- **Presença / painel:** a cada 5 min (e ao começar a assistir) o app manda para `ntfy.sh/<tópico>` uma mensagem `ntp1.<chave efêmera>.<iv>.<dados>` — ECDH P-256 com a chave pública do painel + SHA-256 + AES-GCM. Conteúdo: id aleatório do aparelho, nome do aparelho (opcional), plataforma, versão, se está assistindo (tipo e grupo do canal) e as listas (nome, host do servidor, itens, ativa, vencimento, telas). **Sem usuário, senha ou link completo.** Desligável em Preferências (`LS.ntPres`). O ntfy guarda 12 h; o painel lê `/json?poll=1&since=12h` a cada 30 s e considera "online" quem mandou sinal nos últimos 7 min. O app da TV (`Presence.kt`) manda o mesmo formato (testado: cifrado em Java, decifrado pelo painel).

## 7. O que já foi descoberto sobre os servidores do usuário

- Lista antiga `teleflixhd.club`: fora do ar (Cloudflare 522 e "Welcome to nginx").
- Lista atual: servidor lento no redirecionamento (1,5 a 40 s). Cadeia de exemplo: `cldx-rio-go.top` → `globalx…b-cdn.net` → `crpx84.quantumgrid.lol/auth/<token>` (uso único).
- Canais entregues **em pedaços** e conta com **`max_connections: 1`**.
- Alguns canais respondem como HLS (`application/vnd.apple.mpegurl`), outros como TS (`video/mp2t`), mesmo sem extensão no link.

## 8. Histórico das versões de PC

| Versão | Mudança |
|---|---|
| 3.0.0 | Versão inicial (Electron + interface web). |
| 3.0.1 | Repasse local com cara de VLC e alternativa HLS. *Bug: trava ao trocar de player.* |
| 3.0.2 | Corrige o bug, detecta HLS/TS por consulta, cache de redirecionamento, tempo maior. |
| 3.0.3 | Prefere `.ts` contínuo, painel Ctrl+D, DevTools corrigido. |
| 3.0.4 | Conexão única (sem consulta) e emenda dos pedaços. *Congelava: pacotes TS desalinhados.* |
| 3.0.5 | Emenda alinhada em pacotes de 188 bytes, sem cache de token. |
| 3.2.0 | Ao vivo pelo FFmpeg por padrão (fim das travadas por salto de tempo), pulador de buracos, séries EP01/Capítulo agrupadas (capas), Futebol só com canais passando jogo (EPG), presença + painel `admin.html`. **Aguardando teste do usuário.** |
| 3.1.0 | Repasse espera a conexão única liberar e mostra o motivo da falha; modo FFmpeg (áudio AC3/DTS, vídeo 4K/H.265 com placa de vídeo) e players externos (VLC, mpv, PotPlayer, MPC-HC); séries com temporadas juntas; capas/sinopses do Xtream + TMDB; animes; aba FUTEBOL; selo de vencimento das listas. |

## 9. Pendências / ideias

1. **Confirmar a 3.2.0 no PC do usuário** (troca de canal com conta de 1 tela, filme com áudio AC3, canal 4K/H.265). Se ainda houver pausa na emenda: os timestamps (PTS) podem recomeçar a cada pedaço. Aí a saída é usar `fixAudioTimestampGap` / `liveBufferLatencyChasing` no mpegts.js, ou transmuxar no processo principal.
3. Assinar o executável, para o Windows (SmartScreen) não bloquear.
4. Atualização automática a partir da release `pc-latest`.
5. Painel de diagnóstico com botão "copiar".
