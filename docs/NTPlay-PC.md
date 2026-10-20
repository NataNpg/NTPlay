# NTPlay — PC (Windows / Electron)

Resumo técnico para futuras atualizações. Autor: **Natanael G Silva** · djnatan0@live.com
Situação em 20/10/2026 · repositório `natanpg/NTPlay` (raiz, `electron/` + `www/`)

---

## 1. O que é

Player IPTV (M3U / Xtream) para Windows, feito em **Electron**. A interface web (`www/`) é a mesma base das versões navegador, celular (Capacitor) e iOS.

- **Versão atual:** **3.0.5** (`package.json`)
- **Downloads (sempre a última versão):**
  - Instalador: `https://github.com/NataNpg/NTPlay/releases/download/pc-latest/NTPlay.Setup.<versão>.exe`
  - Portátil: `https://github.com/NataNpg/NTPlay/releases/download/pc-latest/NTPlay.<versão>.exe`
  - Página da release: https://github.com/NataNpg/NTPlay/releases/tag/pc-latest
- **Página para enviar a lista pelo QR code (GitHub Pages):** https://natanpg.github.io/NTPlay/enviar.html

## 2. Tecnologias

| Item | Detalhe |
|---|---|
| App | Electron 31, electron-builder 24 (NSIS + portátil, `--publish never`) |
| Player | hls.js 1.5.7, mpegts.js 1.7.3, `<video>` nativo |
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
| `electron/preload.js` | Expõe `bobNative`: `openVlc`, `pickFile`, `proxyPort`, `onOpen`. |
| `www/js/app.js` | Toda a interface e o player: biblioteca, abas, séries, favoritos, progresso, metadados, QR, escolha do player (`order()` / `play()`) e o **painel de diagnóstico (Ctrl+D)**. |
| `www/js/platform.js`, `native.js` | Adaptações por plataforma (celular, TV via Capacitor). |
| `www/index.html`, `css/app.css` | Layout. |
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
- Keep-alive desligado, para a conexão fechar de verdade ao trocar de canal.

**Ordem de tentativa no ao vivo** (`app.js`, função `play`): `mpegts` (repasse, live=1) → `hls` → `native` → versões diretas sem repasse. Se o link termina em `.m3u8`, tenta antes a versão `.ts` (`mpegtsT`).

## 6. Diagnóstico

- **Ctrl+D:** painel com o canal, cada tentativa de player, erros do hls.js, estado do vídeo (tempo, duração, pausado/fim) e o log do repasse (status, tempo de resposta, redirecionamentos, tamanho dos pedaços).
- **F12:** DevTools. Ficaram quebradas na 3.0.1–3.0.2 porque os cabeçalhos eram alterados também em `devtools://`; corrigido para alterar só `http(s)`.
- Pelo console: `S.cur.c.url` (canal atual), `NT_PROXY`, `mpegts`, `Hls`.

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
| 3.0.5 | Emenda alinhada em pacotes de 188 bytes, sem cache de token. **Aguardando teste do usuário.** |

## 9. Pendências / ideias

1. **Confirmar a 3.0.5 no PC do usuário.** Se ainda houver pausa na emenda: os timestamps (PTS) podem recomeçar a cada pedaço. Aí a saída é usar `fixAudioTimestampGap` / `liveBufferLatencyChasing` no mpegts.js, ou transmuxar no processo principal.
2. Aviso na tela quando a conta tem só 1 tela e outro aparelho está usando.
3. Assinar o executável, para o Windows (SmartScreen) não bloquear.
4. Atualização automática a partir da release `pc-latest`.
5. Painel de diagnóstico com botão "copiar".
