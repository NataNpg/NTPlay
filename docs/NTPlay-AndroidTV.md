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
