# NTPlay — Android TV (app nativo)

Resumo técnico para futuras atualizações. Autor: **Natanael G Silva** · djnatan0@live.com
Situação em 20/10/2026 · repositório `natanpg/NTPlay`, pasta `tv-native/`

---

## 1. O que é

Player IPTV (listas M3U / Xtream Codes) **nativo** para Android TV, feito para substituir a versão antiga em WebView (Capacitor), que não rodava na TV.

- **Pacote:** `com.natanael.ntplay.tv`
- **Versão no Gradle:** `versionName 1.0.0` / `versionCode 1`. Ainda não foi incrementada; o certo é incrementar a cada release.
- **Download do APK (sempre a última versão):**
  https://github.com/natanpg/NTPlay/releases/download/tv-latest/NTPlay-TV.apk
- **Aparelho do usuário:** TV LG com stick Android TV, controle remoto e mouse. O stick deixa a imagem muito saturada e corta as bordas (overscan).

## 2. Tecnologias

| Item | Versão / detalhe |
|---|---|
| Linguagem | Kotlin 1.9.24, Java 17 |
| Interface | Jetpack Compose (BOM 2024.06.00), Material3 |
| Player | Media3 / ExoPlayer 1.3.1 (exoplayer, hls, ui, datasource-okhttp) |
| Áudio extra | `org.jellyfin.media3:media3-ffmpeg-decoder:1.3.1+2` (AC3/EAC3/DTS), exige *core library desugaring* |
| Rede | OkHttp 4.12.0 |
| Imagens | Coil 2.6.0 (com o cliente `Net.img`, que usa User-Agent de navegador) |
| QR code | ZXing core 3.5.3 |
| Build | AGP 8.5.2, Gradle 8.9, minSdk 21, targetSdk/compileSdk 34 |
| Assinatura | release assinado com a chave *debug* (não é para a Play Store) |

**Manifest:** leanback e touchscreen marcados como não obrigatórios, `LEANBACK_LAUNCHER`, banner, paisagem, `largeHeap`, `usesCleartextTraffic`.

## 3. Compilação e publicação

- Workflow: `.github/workflows/android-tv.yml`
  - Roda em todo push que altera `tv-native/**`, ou manualmente (`workflow_dispatch`).
  - Executa `gradle assembleRelease` e publica o APK na release **`tv-latest`** (apaga e recria).
  - Em caso de erro, cria uma anotação `::error::` com as linhas `e:` do Kotlin. É o jeito de ler o erro sem acesso ao log.
- **Não existe ambiente local de compilação.** Todo ajuste é testado compilando no GitHub Actions.

## 4. Arquivos (`tv-native/app/src/main/java/com/natanael/ntplay/tv/`)

| Arquivo | Função |
|---|---|
| `MainActivity.kt` | Entrada do app. Configura o Coil com `Net.img` e trata as teclas de canal +/− no player. |
| `AppState.kt` | Estado global: abas, lista, detalhes, player, favoritos, progresso, busca, episódios sob demanda, vencimento das listas, tendências e logos. |
| `UI.kt` | Toda a interface Compose: cabeçalho, barra lateral, cartões, carrossel de destaques, detalhes, busca, diálogo de listas com QR code e rodapé. |
| `Player.kt` | Player ExoPlayer com controles próprios compactos: barra de tempo interativa, legenda, áudio, favoritar, pular abertura/créditos, qualidade, reconexão e opção de abrir em outro player. |
| `LiveLoop.kt` | `LiveLoopDataSource`: **emenda os pedaços** dos canais ao vivo quando o servidor fecha a conexão a cada ~20 s. |
| `Library.kt` | Leitura da M3U (`M3u.parse`) e montagem da biblioteca: TV / filmes / séries, agrupamentos, adulto por último, logos herdados entre versões do mesmo canal. |
| `Xtream.kt` | API Xtream (`player_api.php`): montagem da lista, episódios por série, informações de filme/série, conta (vencimento e telas). |
| `Net.kt` | Clientes OkHttp, `httpFallback` (https→http) e `Meta` (sinopse e capa: Xtream → TVMaze → iTunes → Wikipédia pt/en, com tradução), mais `Relay` (QR) e `Qr`. |
| `Logos.kt` | Logos de canais pela base iptv-org (`channels.json` + `logos.json`), com cache de 30 dias em `logos.tsv`. |
| `Trending.kt` | "Em alta": Top 10 Netflix, iTunes BR, TVMaze (episódios do dia) e lançamentos da lista. Mostra só o que existe na lista. |
| `Store.kt` | SharedPreferences e arquivos: listas, favoritos, progresso, preferências (adulto, autoNext, legendas, pular abertura), cache de informações (`info.json`) e vencimento das contas. |
| `Util.kt` | Normalização de texto, ano, qualidade (4K/FHD/H.265), detecção de episódio (S01E01 / 1x01), classificação TV/filme/série, adulto, infantil e animes. |

## 5. Funcionalidades prontas

**Listas**
- Várias listas ao mesmo tempo (ligar/desligar, atualizar, excluir).
- Adicionar por link ou pelo celular via **QR code**: página `https://natanpg.github.io/NTPlay/enviar.html`, relay ntfy.sh com AES-GCM.
- Links `get.php?username=&password=` são lidos pela **API Xtream**. Isso evita o HTTP 522 do `get.php`, e a M3U normal fica como reserva.
- Vencimento da conta, status e telas (`exp_date`, `max_connections`, `active_cons`).
- Mensagem de erro detalhada na tela. Tenta vários User-Agents e troca https→http automaticamente.

**Navegação**
- Abas: RECOMENDAÇÕES · TV AO VIVO · FILMES · SÉRIES · INFANTIL · ANIMES · FAVORITOS.
- Barra lateral de categorias, cartões com capa, títulos limpos (ano e qualidade embaixo do cartão).
- Recomendações:
  - carrossel com até 12 destaques;
  - Continuar assistindo, com botão Remover;
  - Em alta e Lançamentos;
  - favoritos separados por Canais / Filmes / Séries.
- **Segurar OK** (ou apertar Menu) favorita qualquer item.
- Interface 20% menor (`UI_SCALE = 0.8f` em `UI.kt`) e margem de segurança para overscan (56/30 dp).
- Foco: borda branca e leve aumento. Mouse: hover, roda e arrastar para rolar.

**Detalhes**
- Sinopse, nota, ano e capa.
- Séries em duas colunas, com os episódios na altura inteira e nomes limpos ("Episódio 4").
- Episódios carregados sob demanda, com botão de nova tentativa.

**Player**
- Controles compactos: ⏪10s / ⏸ / 10s⏩ / Próximo / CC Legenda / Áudio / Favoritar / Sair.
- Qualidade real exibida (ex.: "Full HD 1080p · H.264 · 30 fps").
- Barra de tempo: ◀ ▶ avança/volta, segurar acelera; o mouse clica ou arrasta.
- Pular abertura (+85 s, configurável em `Store.skipIntro`). Próximo episódio / pular créditos no fim. O aviso flutuante dura 5 s.
- Legendas desligadas por padrão (preferência em Listas).
- Canais: ▲▼ trocam de canal; reconexão automática (8 tentativas); `.ts` com falha tenta `.m3u8`.
- Buffer: 20 s mínimo / 60 s máximo no ao vivo e 30/120 s nos filmes; inicia com 1,5 s e retoma com 4 s após travar.
- Erro de decodificação (4K / H.265) oferece "Abrir em outro player (VLC / MX)".

## 6. Problemas conhecidos / pendências

1. **Canais em pedaços:** os servidores do usuário entregam o canal em pedaços de ~20 s e fecham a conexão. O `LiveLoopDataSource` emenda os pedaços, mas **ainda não foi confirmado na TV**. Se travar na emenda, verificar timestamps e alinhamento TS (no PC foi preciso alinhar pacotes de 188 bytes; o `TsExtractor` do ExoPlayer costuma ressincronizar sozinho).
2. **Contas de 1 tela** (`max_connections: 1`): TV e PC com a mesma lista ao mesmo tempo derrubam um ao outro.
3. **Capas faltando** em parte dos filmes. A busca em segundo plano tem limite de 12–30 s; se persistir, investigar certificados em Android antigo (Let's Encrypt).
4. **4K / H.265:** depende do hardware do stick.
5. `versionName` nunca foi incrementado.
6. Nada foi testado em emulador; os testes foram feitos pelo usuário, na TV, com fotos.

## 7. Ideias para próximas versões

- Guia de programação (EPG / XMLTV) nos canais.
- Perfil infantil com bloqueio por PIN para adulto.
- Tempo da abertura por série (memorizar quando o usuário pula).
- Atualizar o app pelo próprio app (verificar a release `tv-latest`).
- Incrementar `versionCode` automaticamente no workflow.

## Presença (1.2.0 — anônima)

`Presence.kt` manda a cada 5 min um sinal anônimo (mesmo formato `ntp1.` do web): só `{id (hash diário), p:"tv", v, t}` — sem lista, nome, modelo nem o que assiste. O aviso/opção foi removido (é anônimo). `versionName 1.2.0` / `versionCode 3`.

## Memória de imagens (1.2.1)

Stick de Android TV tem pouca memória. O Coil (MainActivity) passou a usar RGB565 (metade de memória por imagem), cache em RAM limitado a 20% e cache em disco (96 MB em `cacheDir/img_cache`). Objetivo: capas que falhavam ao decodificar passarem a carregar e o app parar de fechar sozinho com listas grandes. `versionName 1.2.1` / `versionCode 4`. **A travada no ao vivo e a aba Futebol na TV ainda estão pendentes — precisam de teste no aparelho para iterar.**

## Versão 1.3.0 (Android TV) — Futebol, qualidade e compatibilidade

Tudo nesta versão é só no app nativo de Android TV.

- **Aba FUTEBOL** (`Futebol.kt`): jogos do dia pela API pública da ESPN (`site.api.espn.com/.../soccer/<liga>/scoreboard?dates=AAAAMMDD&lang=pt&region=br`) para ~16 campeonatos (Brasileirão A/B, Copa do Brasil, Libertadores, Sul-Americana, Champions, Europa, ligas europeias, Mundo/Eliminatórias, MLS). Agrupa por campeonato, mostra horário/ao vivo/placar e, por baixo, os canais de esporte da lista. Em cada jogo, casa canais da lista pelo nome dos times (sem EPG por enquanto). Cache 1 min com jogo ao vivo, 10 min senão. Seletor Ontem/Hoje/Amanhã. Adicionada em `TABS` (AppState) e no `when (tab)` de `Browse` (UI.kt).
- **Qualidade máxima / 4K** (`Player.kt`): `trackSelectionParameters` com `setMaxVideoSize(MAX, MAX)`, `setMaxVideoBitrate(MAX)` e `setForceHighestSupportedBitrate(true)` — nunca reduz resolução/bitrate e aceita 4K/UHD quando o canal entrega. `DefaultRenderersFactory` já usa decodificadores de hardware com fallback (HEVC/H.265).
- **Buffering de filmes/séries** (preferência `bufferMode` no Store; botão em Preferências): Padrão (15 s), Grande (~25 s) ou Muito grande (~45 s). Aplicado ao `DefaultLoadControl` só no VOD; ao vivo fica fixo.
- **Compatibilidade de fonte (Xtream)** em Preferências: `adultByName` (detectar adulto pelo nome da categoria — liga/desliga a heurística `Util.isAdult`) e `base64` (`Util.maybeB64` decodifica nomes/categorias só quando o texto realmente parece Base64). `Library.build(files, showAdult, b64, adultByName)`.
- **Memória de imagens** (1.2.1, mantida): Coil com RGB565 + cache de RAM 20% + cache em disco (96 MB). Resolveu capas em branco e o app fechando com listas grandes.

### Pendências pedidas (ainda a fazer, precisam de teste no aparelho)
- Painel do ao vivo mais rico (EPG agora/próximo, barra de opções como no app de referência).
- Tela de provedores com estatísticas/diagnóstico e mais ações.
- Grupos de favoritos personalizados (filmes/séries/animes) criados pelo usuário.
- Config de playlist (auto-atualização, intervalo, retenção de EPG) e visual do player de VOD.
- `versionName 1.3.0` / `versionCode 5`.

## Versão 1.4.0 (Android TV) — 4K real, Futebol por EPG, rolagem

- **Saída 4K/UHD real** (`Player.kt`, `setDisplayModeFor`): ao detectar o tamanho do vídeo (`onVideoSizeChanged`), troca a saída HDMI para a resolução do conteúdo via `window.attributes.preferredDisplayModeId`, casando a taxa de quadros (evita judder). Só muda em conteúdo ≥1440p; para o resto volta ao padrão do sistema. É isso que faz a TV mostrar "UHD ativo" no 4K. Volta ao padrão ao sair do player. Requer Android 6+.
- **Aba Futebol por programação (EPG)** (`Futebol.kt`): `loadEpg` lê `get_short_epg` dos canais de esporte (5 em paralelo, até 140 canais-base, cache 5 min) e descobre o que cada um passa agora. "Passando jogo agora" lista só os canais cujo programa atual é um jogo. Em cada jogo do dia, mostra abaixo só o canal que está passando aquele jogo (dois times no programa, ou canal de evento com os dois nomes). Removida a lista geral de canais que não estavam passando jogo. Igual ao PC.
- **Rolagem volta onde parou** (`UI.kt`, `savedGridState`/`savedListState` + `AppState.scroll`): a posição das grades de filmes/séries e das prateleiras é guardada ao abrir um item e restaurada na volta, em vez de voltar ao topo.
- `versionName 1.4.0` / `versionCode 6`.

### Observação sobre 4K que "não entra"
Se o filme 4K não inicia e dá erro de decodificador (`ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES`), é limite de hardware do aparelho (alguns boxes não decodificam HEVC 10-bit 4K). O app mostra o aviso e sugere a versão Full HD/HD. A troca de modo de tela resolve o "UHD ativo" quando o aparelho CONSEGUE decodificar.

## Versão 1.5.0 (Android TV) — guia no player, grupos, provedores, auto-update

- **Guia agora/próximo no player ao vivo** (`Player.kt` + `Fut.epgOf`): ao abrir um canal, o painel do topo mostra o programa atual (título, horário e barra de progresso) e "A seguir". Lê `get_short_epg` do servidor do canal.
- **Grupos personalizados** (`Store.groupsRaw`, `AppState` grupos, `UI.FavoritesScreen`/`GroupDialog`): o usuário cria grupos (ex.: "Ação", "Doramas", "Para ver depois") e adiciona filmes/séries pelo botão **＋ Grupo** na tela do título. A aba FAVORITOS agora mostra cada grupo como uma prateleira, além de "★ Favoritos". Grupos guardados como JSON nas preferências.
- **Provedores com estatísticas** (`ListsDialog`): mostra Ao vivo / Filmes / Séries da biblioteca, mantém por lista o vencimento/telas (conta Xtream) e as ações Atualizar/Excluir.
- **Atualização automática das listas** (`AppState.autoUpdate`): em Preferências, "Atualizar ao abrir o app" e "Atualização automática" (6/12/24 h). Re-sincroniza listas Xtream (via `get_series`/`player_api`) ou baixa a M3U de novo. Desligada por padrão.
- `versionName 1.5.0` / `versionCode 7`.

## Versão 1.6.0 (Android TV) — novo visual, abertura, Futebol compacto, LEG/DUB, animes por provedor

- **Nova identidade visual**: paleta azul-marinho escuro com ciano e roxo (do logo). Cores em `UI.kt` (`Bg`, `Bg2`, `PanelC`, `CardC`, `Pink` agora roxo `#8B5CF6`, `Cyan`, `Btn`, `FocusBg`). Ícone do app, logo do cabeçalho e banner da TV trocados pelo novo logo (`res/drawable-nodpi/logo.png`, `banner.png`, `mipmap-*`).
- **Abertura com logo** (`UI.Splash`): tela cheia com a arte (`res/drawable-nodpi/splash.jpg`) enquanto a lista carrega em segundo plano; some quando termina (mín. 1,8 s, máx. ~20 s), mostrando o status.
- **Futebol compacto** (`Futebol.kt`): cards menores, 3 por linha; abaixo dos times aparece o "📺 Canal" que passa o jogo (ou aviso se não houver na lista).
- **Etiqueta LEG / DUB** (`Util.audioTag`, `PosterCard`): selo no canto do card de filme/série — LEG (legendado, ciano) ou DUB (dublado, roxo), detectado no título/grupo/1º episódio.
- **Animes por provedor** (`UI.AnimeScreen`, `Util.animeSrc`): botões Todos / Crunchyroll / Funimation Now filtram os animes cujo grupo ou nome cita o provedor; conteúdos desses provedores entram na aba Animes.
- `versionName 1.6.0` / `versionCode 8`.

## Versão 1.6.1 (Android TV) — Futebol navegável, filtro por ano, QR local, abertura sem recarga

- **Futebol com controle remoto** (`Futebol.kt`): cada card de jogo agora é focável (antes só o topo tinha itens focáveis, então o D-pad não descia). OK no card abre o 1º canal do jogo.
- **Filtro por ano** (`UI.Browse`, `YearDialog`, `yearOfItem`, `AppState.yearSel`): botão "📅 Ano" no canto superior direito de Filmes, Séries e Animes. Lista só os anos presentes (ano no nome `(2023)` ou no cache de metadados); "✕ Limpar" remove. Zera ao trocar de aba. `Util.yearOf` agora usa regex pré-compilado.
- **QR code pela rede local** (`LocalServer.kt`, `ListsDialog`): ao abrir Listas, a TV sobe um mini servidor HTTP na porta 8765 com token aleatório; o QR aponta para `http://IP-da-TV:8765/token`. O celular (mesmo Wi‑Fi) abre a página, cola o link/M3U e envia direto, sem internet nem GitHub. Servidor para ao fechar o diálogo. Sem rede local, cai no QR antigo (GitHub).
- **Abertura sem recarregar** (`UI.App`, `AppState.trendBusy`): a tela de abertura agora espera também os recomendados (máx. ~6 s) além da lista, para não "carregar de novo" dentro do app.
- **Recomendados só filmes e séries**: removidas "TV ao vivo" e "Canais favoritos"; "Continuar assistindo" ignora canais; "Filmes/Séries" genéricos só aparecem se não houver recomendados (sem internet).
- `versionName 1.6.1` / `versionCode 9`.

## Versão 1.7.0 (Android TV) — conta na nuvem (login/senha), abertura mais completa

- **Conta NTPlay + sincronização** (`Sync.kt`, `AppState` seção "conta + sincronização", `UI.AccountDialog`, botão **☁ Conta** no cabeçalho): usuário e senha; salva **listas (só as que têm link), favoritos, grupos e progresso "continuar assistindo"** para usar em outro aparelho.
  - **Servidor grátis**: projeto Supabase `baylcjfedxnjnjkvaufw` (região ca-central-1), tabela `public.ntplay_accounts` (RLS ligado, sem acesso direto pela API) e Edge Function `ntsync` (`verify_jwt=false`, usa a service role internamente). Endpoint: `https://baylcjfedxnjnjkvaufw.supabase.co/functions/v1/ntsync`. Ações: `reg` (criar conta), `get`, `put`. Limites: 500 contas, 2 MB por conta, bloqueio de 15 min após 8 senhas erradas. Plano grátis do Supabase pausa o projeto após ~7 dias sem uso (reativar no painel).
  - **Privacidade**: a senha nunca sai da TV. `Sync.derive` gera 2 chaves (PBKDF2): uma só para o login (o servidor guarda apenas hash+salt dela) e outra que **criptografa os dados com AES-256-GCM** antes do envio. O servidor só vê texto cifrado; o dono do servidor não lê as listas dos usuários. Esqueceu a senha = dados da nuvem irrecuperáveis.
  - **Regra de sincronização**: a cada 90 s, ao abrir e ~10 s após mudar favoritos/grupos/listas. Se a nuvem é mais nova e nada mudou local → aplica a nuvem; se os dois mudaram → une (favoritos/grupos/listas) e envia; se só local mudou → envia. Listas que faltam são baixadas; se alguma falhar, não sobrescreve a nuvem.
  - Ainda **não** integrado ao app de PC/web (só TV por enquanto).
- **Recomendados**: "Continuar assistindo" e "Canais favoritos" voltaram.
- **Remover (✕) menor** no "Continuar assistindo".
- **Abertura**: agora também pré-carrega as capas dos primeiros recomendados (máx. ~6 s) antes de liberar a tela, para não "carregar de novo" depois.
- `versionName 1.7.0` / `versionCode 10`.

## Versão 1.8.0 (Android TV) — acesso por usuário e senha com validade

- **Tela de entrada obrigatória** (`UI.LoginGate`, `AppState.locked`): ao abrir, se não houver conta conectada (ou o acesso expirou), o app mostra só usuário/senha. Sem login, nada do app fica acessível. Salvamento/sincronização continuam como na 1.7.0 (listas, favoritos, grupos, progresso criptografados).
- **Contas criadas só pelo administrador, com validade** (servidor `ntsync` v2): colunas `expires_at`, `note`, `last_seen` em `ntplay_accounts`. O cadastro público (`reg`) foi fechado. Login com conta expirada/sem validade → 403 e o app bloqueia. A cada 90 s o app revalida; conta excluída, senha trocada ou expirada trava o app. Offline: continua usando a última validade confirmada (`Store.accessExp`) até vencer.
- **Painel do administrador** `www/contas.html` (publicado em `.../NTPlay/contas.html` pelo Pages): cria conta (usuário, senha, 1/2/3/6 meses, 1/2 anos ou N dias), renova (+30/+90/+365 dias), bloqueia, troca senha, exclui, mostra último acesso. Protegido pela chave `NT_ADMIN_KEY` (segredo da Edge Function, definido no painel do Supabase; não fica no código). A senha é derivada no navegador (mesma PBKDF2 do app); o servidor só recebe a chave de login derivada.
- **Outro usuário na mesma TV**: ao entrar com usuário diferente do anterior, os dados locais (listas, favoritos, grupos, progresso) são apagados antes de sincronizar, para não misturar contas.
- Troca de senha pelo painel: os dados já salvos na nuvem daquela conta não abrem com a senha nova (a criptografia depende da senha).
- A conta `natan` (já existente) recebeu 365 dias de validade.
- `versionName 1.8.0` / `versionCode 11`.

## Versão 1.9.0 (Android TV) — atualização automática + assinatura fixa

- **Atualização automática** (`Updater.kt`, `AppState` seção "atualização automática do app", `UI.UpdateDialog`): ao abrir (após 20 s) e a cada 6 h o app lê `tv-version.json` da release `tv-latest`. Se houver versão maior, **baixa o APK sozinho** e mostra "Nova versão X — Instalar agora / Depois". O Android sempre pede a confirmação final (instalação 100% silenciosa só existe em aparelho com root/administrador). Na primeira vez pode abrir a tela "instalar apps desconhecidos": permitir para o NTPlay.
- **Versão mínima (trava de versões antigas)**: arquivo `tv-native/min_version` (número de `versionCode`). Se for maior que a versão instalada, o app mostra a atualização como obrigatória (sem "Depois"). Para forçar todos a atualizar: editar `min_version` e dar push. Só vale para versões ≥ 1.9.0.
- **Workflow** (`android-tv.yml`): gera `tv-version.json` (`code`, `name`, `min`) e publica junto do APK.
- **Assinatura fixa e secreta** (`signingConfigs.ntplay`, GitHub Secrets `NT_KEYSTORE_B64` + `NT_KS_PASS`): o workflow reconstrói o `.jks` dos secrets e falha com aviso se faltarem. A chave NÃO fica no repositório (a primeira, da 1.9.0, foi descartada por ter sido publicada). Guarde o arquivo `.jks` em local seguro: perdê-lo exige reinstalar o app de novo.
- Limpeza no GitHub: artefatos de compilação antigos apagados (restou só o mais recente). A tag órfã "Lançamento" continua precisando ser apagada pelo site.
- `versionName 1.9.0` / `versionCode 12`.

## Versão 1.9.1 (Android TV) — chave de assinatura nos Secrets
- Nova chave (secreta) em GitHub Secrets; build usa `NT_KS_FILE`/`NT_KS_PASS`. Quem instalou a 1.9.0 (chave antiga, pública) precisa reinstalar uma vez. `versionName 1.9.1` / `versionCode 13`.

## Versão 1.9.2 (Android TV) — contato por WhatsApp na entrada, nuvem junto da lista; novo painel de contas
- **Tela de entrada** (`UI.LoginGate`): QR code pequeno + número do WhatsApp (42) 92002-8569 (`https://wa.me/5542920028569`) para pedir acesso/renovação.
- **Nuvem junto da lista**: o botão "☁ Conta" saiu do cabeçalho; a conta/nuvem (usuário, validade, "Sincronizar agora", "Sair da conta") agora fica dentro de **☰ Listas**, acima das listas.
- **Painel de contas** (`www/contas.html`) refeito no novo design (cartões, gráficos, status, busca, filtros), nas cores/logo do NTPlay, ligado ao servidor real: criar (7/30/90/365 dias ou data), editar validade/anotação, renovar +30 dias, bloquear agora, nova senha, excluir, exportar CSV, último acesso. Não mostra nem guarda senhas (só na hora de criar/trocar, para copiar a mensagem de acesso). A chave de administrador é verificada no servidor (não está mais no código).
- Servidor `ntsync` v4: novas operações `setexp` (data exata), `note`, e `create` aceita `date`.
- `versionName 1.9.2` / `versionCode 14`.

## 1.10.0
- Xtream Codes (servidor, usuário e senha) na aba ☰ Listas.
- Guia de programação (EPG/XMLTV): programa atual nos cards de canais; no player ao vivo, agora/próximo, 📅 Guia, 📺 Canais e ⛶ Tela.
- Abertura por série: o app memoriza onde você pulou a abertura e oferece "Pular abertura" nos próximos episódios.
- versionCode automático no workflow (1000 + nº da execução).

## 1.10.1
- Painel de contas: coluna "Aparelhos" (quantos aparelhos usam a conta e quantos estão online agora; passe o mouse para ver os nomes). O app envia um ID do aparelho e o modelo a cada sincronização.

## 1.10.2
- **Correção: o app fechava ao buscar filme ou série.** Filmes entravam na biblioteca sem checagem de repetição, ao contrário dos canais ao vivo (`seenLive`) e das séries (`LinkedHashMap`). Painéis Xtream listam o mesmo `stream_id` em mais de uma categoria (o mesmo filme em "LANÇAMENTOS" e em "AÇÃO"), e duas listas ligadas ao mesmo tempo repetem a lista inteira — nos dois casos nasciam dois `Item` com a chave `m:<mesma-url>`.
  Navegando por categoria o problema não aparece, porque cada grade mostra um grupo só. **A busca é o único lugar que junta tudo numa lista só**: o `LazyVerticalGrid` usa `items(res, key = { it.key })` e chave repetida no Compose lança `IllegalArgumentException`, fechando o app. A mesma raiz atingia a prateleira "🆕 Lançamentos de filmes" e a aba Favoritos (ao favoritar um filme duplicado).
  Corrigido em `Library.build` (`seenMovie`), com uma segunda barreira no `AppState.search` (`distinctBy { it.key }`).
- Busca mais leve: para de varrer quando as duas listas de resultado enchem, em vez de normalizar o nome da biblioteca inteira.
- `versionName 1.10.2`.

## 1.10.3
- **Correção real do app fechando na busca** (a 1.10.2 não resolveu). `Item.norm` era `by lazy(LazyThreadSafetyMode.NONE)`, e três threads leem esse campo ao mesmo tempo:
  1. a thread principal, nos cards de canal (`UI.PosterCard` → `Epg.nowTitle`, introduzido na 1.10.0);
  2. `Dispatchers.Default`, em `AppState.search`, que percorre `lib.every`;
  3. `Dispatchers.IO`, em `Epg.load`, que lê `c.norm` de todos os canais ao vivo.
  O `UnsafeLazyImpl` do Kotlin (modo `NONE`) não tem `@Volatile`: uma thread pode enxergar `initializer` já anulado por outra e `_value` ainda não publicado, e aí o `initializer!!()` lança `NullPointerException`. Como é corrida, fechava de forma intermitente — e só na busca, porque é o único lugar que varre a biblioteca inteira enquanto os cards e o EPG leem o mesmo campo.
  Trocado para `LazyThreadSafetyMode.PUBLICATION`: publica com CAS sobre campo volátil, sem trava. `Util.norm` é puro, então rodar duas vezes é inofensivo.
- Tela de entrada e ☰ Listas: bloco único `ContatoTeste` (QR code do WhatsApp + número), agora com o texto "Peça seu teste do aplicativo ou da lista".
- `versionName 1.10.3`.

## 1.10.4
- **Teste de 1 dia, só no aparelho** (`AppState` seção conta, `Store.trialExp`/`trialUsed`): entrar com o usuário `demo` e a senha `ntplay2026` libera o app por 24 h **sem tocar no servidor** e sem nuvem. Quem entra assim **põe a própria lista** — por isso não disputa as conexões (`max_connections`) da sua lista nem mistura favoritos com ninguém. Vencido o prazo, o app volta à tela de entrada sozinho (checado junto do ciclo de 90 s).
  - `trialUsed` impede repetir o teste no mesmo aparelho; limpar os dados do app ou reinstalar reinicia (é a limitação de não usar servidor).
  - Entrar com conta de verdade zera o teste; sair do teste também.
  - Usuário/senha ficam em `TRIAL_USER`/`TRIAL_PASS` no `AppState` — troque e publique para girar a senha.
  - O nome `demo` não pode ser criado como conta no painel, senão o teste local tomaria a frente.
- **Botão focado legível** (`UI.TvButton`): focado ou sob o mouse, fundo branco e texto escuro. O comentário do código já dizia isso, mas o texto era sempre branco — em TV com cor estourada (stick saturado) o roxo virava branco e o botão sumia.
- `versionName 1.10.4`.

## 1.10.5
- **Teste de 1 dia removido.** O acesso volta a ser só por conta criada por você no painel, com validade. Quem quiser testar pede pelo WhatsApp (o QR e o número continuam na tela de entrada e em ☰ Listas). Saíram `TRIAL_USER`/`TRIAL_PASS`, `startTrial`, `trialCheck` e os campos `trialExp`/`trialUsed` do `Store`.
  - Quem tiver iniciado o teste na 1.10.4 volta à tela de entrada ao atualizar. As chaves `trial_exp`/`trial_used` ficam no SharedPreferences sem uso e não atrapalham.
- Mantidas da 1.10.4: o botão focado com fundo branco e texto escuro (`UI.TvButton`).
- `versionName 1.10.5`.

## 1.10.6
- **Busca só de filmes, séries e animes** (`AppState.search`): varre `movies + series` em vez de `every`, então canais ao vivo não aparecem mais no resultado (os animes estão dentro de filmes/séries, então continuam vindo). Cartões da busca agora têm sempre 112 dp e abrem a tela de detalhes.
- **Atualização ao reabrir o app** (`MainActivity.onResume`): a checagem só acontecia 20 s depois de criar o `AppState` e de 6 em 6 h. Na TV o app fica na memória, então sair e voltar NÃO recriava o `AppState` e a versão nova só aparecia até 6 h depois. Agora confere toda vez que o app volta para a frente, no máximo uma vez a cada 10 min.
- `versionName 1.10.6`.
