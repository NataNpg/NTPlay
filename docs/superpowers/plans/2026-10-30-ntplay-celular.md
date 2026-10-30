# NTPlay Celular Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Criar `mobile-native/`, um app Android nativo de celular para o NTPlay, que reaproveita o motor do app de Android TV copiando-o na compilação, sem alterar nenhum arquivo de `tv-native/`.

**Architecture:** Projeto Gradle independente em `mobile-native/`. Uma tarefa Gradle copia 13 arquivos `.kt` de `tv-native/app/src/main/java/com/natanael/ntplay/tv/` para `build/generated/core/com/natanael/ntplay/core/`, trocando a linha `package` e aplicando 3 remendos verificados (abas, plataforma da presença, release do atualizador). Cada remendo falha a compilação se seu trecho não for encontrado. A interface (`UI.kt`, `Player.kt`, `MainActivity.kt`) é escrita do zero para toque, no pacote `com.natanael.ntplay.mobile`.

**Tech Stack:** Kotlin 1.9.24, Java 17, Jetpack Compose (BOM 2024.06.00), Material3, Media3/ExoPlayer 1.3.1, OkHttp 4.12.0, Coil 2.6.0, ZXing core 3.5.3, AGP 8.5.2, Gradle 8.9, minSdk 21, targetSdk/compileSdk 34.

**Spec:** `docs/superpowers/specs/2026-10-27-ntplay-celular-design.md`

## Global Constraints

- **Nenhum arquivo dentro de `tv-native/` pode ser criado, alterado ou removido.** Qualquer tarefa que precise disso está errada e deve parar. Ao fim de cada tarefa, `git diff --stat HEAD -- tv-native/` deve sair vazio.
- Todo trabalho acontece dentro de `mobile-native/` (mais `.github/workflows/android-mobile.yml` na Tarefa 8).
- `applicationId` e `namespace`: `com.natanael.ntplay.mobile`. Pacote da interface: `com.natanael.ntplay.mobile`. Pacote do motor copiado: `com.natanael.ntplay.core`.
- `versionName = "1.0.0"`. `versionCode = (System.getenv("NT_VERSION_CODE") ?: "1").toInt()`.
- minSdk 21, targetSdk 34, compileSdk 34, Java 17, `isCoreLibraryDesugaringEnabled = true` (exigido pelo decodificador FFmpeg do Jellyfin).
- Assinatura: `signingConfigs` lê `NT_KS_FILE` e `NT_KS_PASS` do ambiente, alias `ntplay`; sem eles, cai na chave `debug`. Copiar a forma de `tv-native/app/build.gradle.kts:20-32`.
- `build/generated/core/` nunca é versionado. Acrescentar `mobile-native/app/build/` ao `.gitignore` se ainda não estiver coberto.
- Textos da interface em português do Brasil.
- Contato exibido ao usuário: WhatsApp (42) 92002-8569, link `https://wa.me/5542920028569`.
- Endereço do atualizador do celular: `https://github.com/NataNpg/NTPlay/releases/download/mob-latest/` (grafia `NataNpg`, não `natanpg`).
- Não existe ambiente local de compilação: a verificação de cada tarefa é `gradle :app:compileReleaseKotlin` dentro de `mobile-native/`, ou o GitHub Actions. O Actions garante que compila, não que o comportamento está certo.

## Review Focus

Cinco condições que a spec implica, que nenhum teste de tarefa exercita por si, e que mordem o usuário real. Cada uma recebe seu teste na tarefa indicada.

1. **A TV muda e o remendo não bate mais** — o `grep` do remendo encontra zero ou mais de uma ocorrência. Esperado: a compilação do celular falha nomeando o arquivo e o trecho, nunca gera um app que se identifica como `"tv"` ou que busca atualização em `tv-latest`. *(Tarefa 2)*
2. **Conta de 1 tela** — `max_connections: 1` e o usuário abre no celular com a TV ligada. Esperado: aviso explicando que os dois não tocam ao mesmo tempo, em vez de erro cru de rede. *(Tarefa 7)*
3. **Lista vazia ou sem nenhuma lista cadastrada** — primeira abertura depois do login, nuvem sem listas. Esperado: cada aba mostra texto orientando a adicionar uma lista, não grade vazia nem travamento. *(Tarefa 5)*
4. **Sem internet com acesso ainda válido** — `Sync.call` falha com `code == 0`. Esperado: o app abre com o conteúdo em cache e vale a última validade confirmada (`Store.accessExp`), em vez de bloquear. *(Tarefa 4)*
5. **Canal cuja lista não tem EPG** — `Epg.nowNext` devolve `null to null`. Esperado: a linha do canal mostra só o nome, sem espaço reservado vazio nem texto de erro. *(Tarefa 6)*

---

## Estrutura de arquivos

**Criados em `mobile-native/`:**

| Arquivo | Responsabilidade |
|---|---|
| `settings.gradle.kts` | Repositórios e `include(":app")`. |
| `gradle.properties` | Cópia de `tv-native/gradle.properties`. |
| `min_version` | Trava de versão do celular. Conteúdo inicial: `0`. |
| `app/build.gradle.kts` | Configuração do app, dependências e a tarefa `generateCore`. |
| `app/src/main/AndroidManifest.xml` | Permissões, atividade, FileProvider. |
| `app/src/main/res/` | Ícone, logo, `xml/file_paths.xml`, `values/themes.xml`. Sem banner e sem splash. |
| `app/src/main/java/com/natanael/ntplay/mobile/Theme.kt` | Cores e `NtTheme` do celular. |
| `app/src/main/java/com/natanael/ntplay/mobile/MainActivity.kt` | Entrada, Coil, `onResume` de atualização. |
| `app/src/main/java/com/natanael/ntplay/mobile/Login.kt` | Tela de entrada (`LoginGate`). |
| `app/src/main/java/com/natanael/ntplay/mobile/Shell.kt` | `App`, barra de abas inferior, roteamento de telas. |
| `app/src/main/java/com/natanael/ntplay/mobile/Home.kt` | Aba Início (prateleiras). |
| `app/src/main/java/com/natanael/ntplay/mobile/Grid.kt` | Abas Filmes/Séries: grade, categorias, filtros. |
| `app/src/main/java/com/natanael/ntplay/mobile/Live.kt` | Aba TV: lista de canais com EPG e filtro. |
| `app/src/main/java/com/natanael/ntplay/mobile/Search.kt` | Aba Busca. |
| `app/src/main/java/com/natanael/ntplay/mobile/Detail.kt` | Tela de detalhes e episódios. |
| `app/src/main/java/com/natanael/ntplay/mobile/Cards.kt` | `PosterCard`, `ChannelRow`, selos LEG/DUB, estados vazios. |
| `app/src/main/java/com/natanael/ntplay/mobile/Player.kt` | Player de toque. |
| `app/src/main/java/com/natanael/ntplay/mobile/Menu.kt` | Menu ☰: listas, provedores, conta, preferências, grupos. |

**Criado fora:** `.github/workflows/android-mobile.yml`.

**Motor copiado na compilação** (não versionado, gerado em `app/build/generated/core/com/natanael/ntplay/core/`): `Store`, `Library`, `Xtream`, `Net`, `Util`, `Logos`, `Trending`, `Epg`, `LiveLoop`, `Sync` (sem alteração de lógica) e `AppState`, `Presence`, `Updater` (com remendo). **Não copiados:** `UI.kt`, `Player.kt`, `MainActivity.kt`, `Futebol.kt`, `LocalServer.kt`.

---

### Task 1: Projeto Gradle que compila vazio

Primeiro a casca compilando, antes de qualquer cópia de motor: assim a Tarefa 2 falha por causa da cópia e não por configuração.

**Files:**
- Create: `mobile-native/settings.gradle.kts`
- Create: `mobile-native/gradle.properties`
- Create: `mobile-native/min_version`
- Create: `mobile-native/app/build.gradle.kts`
- Create: `mobile-native/app/src/main/AndroidManifest.xml`
- Create: `mobile-native/app/src/main/res/values/themes.xml`
- Create: `mobile-native/app/src/main/java/com/natanael/ntplay/mobile/MainActivity.kt`
- Modify: `.gitignore`

**Interfaces:**
- Consumes: nada.
- Produces: projeto Gradle `mobile-native/` com módulo `:app`, `applicationId com.natanael.ntplay.mobile`; `MainActivity` como `ComponentActivity`.

- [ ] **Step 1: Criar `settings.gradle.kts` e `gradle.properties`**

`settings.gradle.kts` copia a forma de `tv-native/settings.gradle.kts`, com `rootProject.name = "NTPlayMobile"` e `include(":app")`. `gradle.properties` é cópia literal de `tv-native/gradle.properties`.

- [ ] **Step 2: Criar `min_version` com o conteúdo `0`**

- [ ] **Step 3: Criar `app/build.gradle.kts`**

Usar `tv-native/app/build.gradle.kts` como modelo, aplicando as Global Constraints (namespace, applicationId, versionName, assinatura, desugaring). Dependências: as mesmas da TV, sem `zxing` nesta tarefa (entra na Tarefa 4). `buildFeatures { compose = true }`, `kotlinCompilerExtensionVersion = "1.5.14"`.

- [ ] **Step 4: Criar `AndroidManifest.xml`**

Diferenças obrigatórias em relação ao da TV: **sem** `LEANBACK_LAUNCHER` e **sem** `android:banner`; `android:screenOrientation="portrait"` na atividade; `uses-feature android.hardware.touchscreen` com `required="true"`. Manter de `tv-native/app/src/main/AndroidManifest.xml`: permissão de internet, `usesCleartextTraffic="true"`, `largeHeap="true"`, permissão de instalação de pacotes e o `provider` do FileProvider com autoridade `${applicationId}.fileprovider` apontando para `@xml/file_paths` (criar `res/xml/file_paths.xml` igual ao da TV).

- [ ] **Step 5: Criar `themes.xml` e `MainActivity.kt` mínimos**

`MainActivity` estende `ComponentActivity` e chama `setContent { }` com um `Text("NTPlay")`. Nada mais — a interface real vem depois.

- [ ] **Step 6: Acrescentar `mobile-native/app/build/` ao `.gitignore`**

- [ ] **Step 7: Verificar que compila**

Run: `cd mobile-native && gradle :app:compileReleaseKotlin --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Verificar que a TV não foi tocada**

Run: `git diff --stat HEAD -- tv-native/`
Expected: saída vazia

- [ ] **Step 9: Commit**

```bash
git add mobile-native .gitignore
git commit -m "celular: projeto Gradle inicial"
```

---

### Task 2: Tarefa `generateCore` — cópia do motor com remendos verificados

O coração do plano. Os remendos verificam antes de trocar: sem isso, uma mudança futura na TV geraria silenciosamente um app que se identifica como TV.

**Files:**
- Modify: `mobile-native/app/build.gradle.kts`
- Test: `mobile-native/app/build.gradle.kts` (a própria tarefa falha; a verificação é a saída do Gradle)

**Interfaces:**
- Consumes: Tarefa 1 (projeto que compila).
- Produces: pacote `com.natanael.ntplay.core` disponível ao módulo, com `AppState(ctx: Context)`, `TABS`, `Store`, `Library`, `Item`, `Ep`, `Epg`, `Net`, `Util`, `Sync`, `Xtream`, `Updater`, `Presence`, `Trending`, `Logos`, `LiveLoopDataSource`. Tarefa Gradle chamada `generateCore`, da qual `preBuild` depende.

- [ ] **Step 1: Escrever o teste que falha — remendo que não encontra o alvo**

Antes de implementar, provar que a verificação funciona. Acrescentar ao `build.gradle.kts` a tarefa `generateCore` com a lista de remendos, e rodar uma vez com um alvo deliberadamente errado (por exemplo `put("p", "TV_ERRADO")` no lugar do alvo do `Presence`).

- [ ] **Step 2: Rodar e verificar que falha com mensagem útil**

Run: `cd mobile-native && gradle :app:generateCore --no-daemon`
Expected: FAIL com `GradleException` citando `Presence.kt` e o trecho não encontrado.

- [ ] **Step 3: Corrigir o alvo e implementar `generateCore` por inteiro**

A tarefa lê de `../tv-native/app/src/main/java/com/natanael/ntplay/tv/` e escreve em `build/generated/core/com/natanael/ntplay/core/`. Para cada arquivo: substituir a primeira linha `package com.natanael.ntplay.tv` por `package com.natanael.ntplay.core`, aplicar os remendos do arquivo e gravar.

Arquivos sem remendo (10): `Store.kt`, `Library.kt`, `Xtream.kt`, `Net.kt`, `Util.kt`, `Logos.kt`, `Trending.kt`, `Epg.kt`, `LiveLoop.kt`, `Sync.kt`.

Arquivos com remendo (3) — cada par é `de` → `para`, e **cada `de` deve ocorrer exatamente uma vez** no arquivo; zero ou duas ocorrências lançam `GradleException` com o nome do arquivo e o texto procurado:

| Arquivo | `de` (verbatim) | `para` |
|---|---|---|
| `AppState.kt` | `val TABS = listOf("RECOMENDAÇÕES", "TV AO VIVO", "FUTEBOL", "FILMES", "SÉRIES", "INFANTIL", "ANIMES", "FAVORITOS")` | `val TABS = listOf("INÍCIO", "FILMES", "SÉRIES", "TV", "BUSCA")` |
| `Presence.kt` | `put("p", "tv"); put("v", "tv-" + ver)` | `put("p", "mob"); put("v", "mob-" + ver)` |
| `Updater.kt` | `"https://github.com/natanpg/NTPlay/releases/download/tv-latest/"` | `"https://github.com/NataNpg/NTPlay/releases/download/mob-latest/"` |
| `Updater.kt` | `"tv-version.json?t="` | `"mob-version.json?t="` |

Registrar `build/generated/core` como `srcDir` do `main` e fazer `preBuild` depender de `generateCore`. Declarar as entradas e saídas da tarefa (`@InputDirectory` / `@OutputDirectory`, ou `inputs.dir`/`outputs.dir`) para a compilação incremental não usar cópia velha.

- [ ] **Step 4: Rodar e verificar que gera os 13 arquivos**

Run: `cd mobile-native && gradle :app:generateCore --no-daemon && ls app/build/generated/core/com/natanael/ntplay/core/ | wc -l`
Expected: `13`

- [ ] **Step 5: Verificar que os remendos entraram e nada de TV sobrou**

Run: `cd mobile-native && grep -rl 'ntplay.tv\|"p", "tv"\|tv-latest\|tv-version' app/build/generated/core/ ; grep -c 'mob-latest' app/build/generated/core/com/natanael/ntplay/core/Updater.kt`
Expected: nenhum arquivo listado pelo primeiro `grep`; `1` no segundo.

- [ ] **Step 6: Verificar que o módulo compila com o motor**

Run: `cd mobile-native && gradle :app:compileReleaseKotlin --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Verificar que a TV não foi tocada**

Run: `git diff --stat HEAD -- tv-native/`
Expected: saída vazia

- [ ] **Step 8: Commit**

```bash
git add mobile-native/app/build.gradle.kts
git commit -m "celular: copia o motor da TV no build, com remendos verificados"
```

---

### Task 3: Tema, identidade visual e recursos

**Files:**
- Create: `mobile-native/app/src/main/java/com/natanael/ntplay/mobile/Theme.kt`
- Create: `mobile-native/app/src/main/res/drawable-nodpi/logo.png`
- Create: `mobile-native/app/src/main/res/mipmap-*/ic_launcher.png`, `ic_launcher_round.png`
- Modify: `mobile-native/app/src/main/res/values/themes.xml`

**Interfaces:**
- Consumes: Tarefa 1.
- Produces: `NtTheme(content: @Composable () -> Unit)` e as cores `Bg`, `Bg2`, `PanelC`, `CardC`, `Purple`, `Cyan`, `Btn` em `com.natanael.ntplay.mobile`.

- [ ] **Step 1: Copiar as imagens da TV para os recursos do celular**

Copiar de `tv-native/app/src/main/res/` para `mobile-native/app/src/main/res/`: `drawable-nodpi/logo.png` e os seis pares `mipmap-*/ic_launcher*.png`. **Não copiar** `drawable/banner.png` (é só para Android TV) nem `drawable-nodpi/splash.jpg`: o celular abre na tela de entrada, e a spec não pede tela de abertura — recurso sem uso só engorda o APK. Copiar arquivo de imagem não é alterar `tv-native/`.

- [ ] **Step 2: Criar `Theme.kt` com a paleta do NTPlay**

Mesmos valores da TV (`tv-native/.../UI.kt`, seção de cores da 1.6.0): azul-marinho escuro com ciano e roxo `#8B5CF6`. `NtTheme` aplica `darkColorScheme` do Material3. **Sem** `UI_SCALE` e **sem** as margens de overscan (56/30 dp) — são correções de TV e no celular comem a tela.

- [ ] **Step 3: Verificar que compila**

Run: `cd mobile-native && gradle :app:compileReleaseKotlin --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add mobile-native/app/src/main/java/com/natanael/ntplay/mobile/Theme.kt mobile-native/app/src/main/res
git commit -m "celular: tema e identidade visual"
```

---

### Task 4: Tela de entrada e acesso por conta

Sem isso nada do app é acessível, então vem antes das telas de conteúdo.

**Files:**
- Create: `mobile-native/app/src/main/java/com/natanael/ntplay/mobile/Login.kt`
- Modify: `mobile-native/app/src/main/java/com/natanael/ntplay/mobile/MainActivity.kt`
- Modify: `mobile-native/app/build.gradle.kts` (acrescentar `com.google.zxing:core:3.5.3`)

**Interfaces:**
- Consumes: `AppState(ctx)`, `AppState.locked: Boolean`, `AppState.accessMsg: String`, `AppState.syncBusy: Boolean`, `AppState.accountLogin(user: String, pass: String)`, `AppState.accessText(): String`, `NtTheme`.
- Produces: `@Composable fun LoginGate(st: AppState)`; `MainActivity` que cria o `AppState`, configura o Coil e roteia entre `LoginGate` e o app.

- [ ] **Step 1: Escrever o teste que falha — acesso válido sem internet não bloqueia**

Item 4 do Review Focus. Teste instrumentado (`androidTest`) ou de unidade sobre a regra de bloqueio, o que a estrutura permitir:

```kotlin
@Test
fun acessoValidoSemInternetNaoBloqueia() {
    // store.syncUser preenchido e store.accessExp no futuro
    // Sync.call falhando com code == 0 (sem rede)
    // Esperado: locked == false — vale a última validade confirmada
    assertFalse(st.locked)
}
```

- [ ] **Step 2: Rodar e verificar que falha**

Run: `cd mobile-native && gradle :app:testReleaseUnitTest --no-daemon`
Expected: FAIL (ou erro de compilação por `LoginGate` inexistente)

- [ ] **Step 3: Implementar `LoginGate(st: AppState)` em `Login.kt`**

Retrato. Logo no topo, campos de usuário e senha (senha com `PasswordVisualTransformation`), botão Entrar que chama `st.accountLogin(user, pass)` e desabilita quando `st.syncBusy`. `st.accessMsg` aparece como mensagem de erro. Abaixo, o QR code do WhatsApp (gerar com ZXing a partir de `https://wa.me/5542920028569`) e o texto "Peça seu teste do aplicativo ou da lista — WhatsApp (42) 92002-8569". `ime` ajustando o teclado sem cobrir os campos.

- [ ] **Step 4: Implementar `MainActivity`**

Criar `AppState(applicationContext)` com `remember`, chamar `st.start()` num `LaunchedEffect`. Configurar o Coil como em `tv-native/.../MainActivity.kt:16-27`, **mudando duas coisas**: `allowRgb565(false)` e cache de disco de `256L * 1024 * 1024`. `onResume` com o limite de 10 min chamando `st.checkUpdate()`, como em `MainActivity.kt:40-49`. Rotear: `if (st.locked) LoginGate(st) else App(st)` — nesta tarefa, `App` ainda é o `Text` provisório.

- [ ] **Step 5: Rodar e verificar que passa**

Run: `cd mobile-native && gradle :app:testReleaseUnitTest :app:compileReleaseKotlin --no-daemon`
Expected: PASS e `BUILD SUCCESSFUL`

- [ ] **Step 6: Verificar que a TV não foi tocada**

Run: `git diff --stat HEAD -- tv-native/`
Expected: saída vazia

- [ ] **Step 7: Commit**

```bash
git add mobile-native
git commit -m "celular: tela de entrada por conta"
```

---

### Task 5: Casca do app, barra de abas e estados vazios

**Files:**
- Create: `mobile-native/app/src/main/java/com/natanael/ntplay/mobile/Shell.kt`
- Create: `mobile-native/app/src/main/java/com/natanael/ntplay/mobile/Cards.kt`
- Modify: `mobile-native/app/src/main/java/com/natanael/ntplay/mobile/MainActivity.kt`
- Test: `mobile-native/app/src/androidTest/java/com/natanael/ntplay/mobile/ShellTest.kt`

**Interfaces:**
- Consumes: `AppState.tab: String`, `AppState.lib: Library?`, `AppState.status: String`, `AppState.busy: Boolean`, `TABS` (já `listOf("INÍCIO","FILMES","SÉRIES","TV","BUSCA")`), `Item`, `Util.audioTag(it)`, `Util.cleanT(s)`, `Util.quality(s)`, `Util.yearOf(s)`.
- Produces: `@Composable fun App(st: AppState)`; `@Composable fun PosterCard(it: Item, onClick: () -> Unit)`; `@Composable fun EmptyState(msg: String, action: String?, onAction: (() -> Unit)?)`.

- [ ] **Step 1: Escrever o teste que falha — sem lista, cada aba orienta**

Item 3 do Review Focus.

```kotlin
@Test
fun semListaMostraOrientacao() {
    // st.lib == null, nenhuma lista cadastrada
    composeRule.onNodeWithText("Adicione uma lista para começar").assertIsDisplayed()
}
```

- [ ] **Step 2: Rodar e verificar que falha**

Run: `cd mobile-native && gradle :app:compileReleaseAndroidTestKotlin --no-daemon`
Expected: FAIL por `App` ou `EmptyState` inexistente

- [ ] **Step 3: Implementar `App(st)` em `Shell.kt`**

`Scaffold` com `NavigationBar` embaixo, um item por entrada de `TABS`, ícone e rótulo; selecionado = `st.tab`, clique atribui `st.tab`. Topo com logo, título da aba e botão ☰. Corpo roteando por `st.tab` para as telas das tarefas seguintes (nesta tarefa, `EmptyState` provisório em cada uma). Quando `st.lib == null` e não há lista, mostrar `EmptyState("Adicione uma lista para começar", "Abrir Listas") { }`. Enquanto `st.busy`, indicador de progresso com `st.status`.

- [ ] **Step 4: Implementar `PosterCard` e `EmptyState` em `Cards.kt`**

`PosterCard`: capa via Coil com proporção 2:3, título limpo (`Util.cleanT`) embaixo em uma linha, ano e qualidade numa segunda linha menor, e o selo LEG/DUB (`Util.audioTag`) no canto — ciano para LEG, roxo para DUB. Área de toque mínima de 48 dp.

- [ ] **Step 5: Trocar o `Text` provisório do `MainActivity` por `App(st)`**

- [ ] **Step 6: Rodar e verificar que passa**

Run: `cd mobile-native && gradle :app:compileReleaseKotlin :app:compileReleaseAndroidTestKotlin --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add mobile-native
git commit -m "celular: casca, barra de abas e estados vazios"
```

---

### Task 6: Abas de conteúdo — Início, Filmes, Séries, TV, Busca

**Files:**
- Create: `mobile-native/app/src/main/java/com/natanael/ntplay/mobile/Home.kt`
- Create: `mobile-native/app/src/main/java/com/natanael/ntplay/mobile/Grid.kt`
- Create: `mobile-native/app/src/main/java/com/natanael/ntplay/mobile/Live.kt`
- Create: `mobile-native/app/src/main/java/com/natanael/ntplay/mobile/Search.kt`
- Modify: `mobile-native/app/src/main/java/com/natanael/ntplay/mobile/Shell.kt`
- Test: `mobile-native/app/src/androidTest/java/com/natanael/ntplay/mobile/LiveTest.kt`

**Interfaces:**
- Consumes: `Library.movies`, `.series`, `.live`, `.kids`, `.anime`; os grupos `Library.liveGroups`, `.movieGroups`, `.serieGroups`, todos `List<Pair<String, List<Item>>>`; `AppState.search(q: String): List<Item>` (suspensa; devolve só filmes e séries); `AppState.openDetail(it: Item)`; `AppState.continueList(): List<Pair<Item, Ep>>` (já sem os terminados e ordenada por mais recente — `progMap` é privado, não tente acessá-lo); `AppState.resumeOf(ep: Ep): Long`; `AppState.removeProg(key: String)` (recebe `item.key`, não a URL); `AppState.progVer: Int` (muda quando o progresso muda, para recompor); `AppState.favs: Set<String>`; `Epg.nowNext(it): Pair<Epg.P?, Epg.P?>`; `Epg.nowTitle(it): String?`; `Trending`; `PosterCard`; `EmptyState`.
- Produces: `@Composable fun HomeScreen(st)`, `MoviesScreen(st)`, `SeriesScreen(st)`, `LiveScreen(st)`, `SearchScreen(st)`.

- [ ] **Step 1: Escrever o teste que falha — canal sem EPG mostra só o nome**

Item 5 do Review Focus.

```kotlin
@Test
fun canalSemEpgMostraSoONome() {
    // Item de canal cujo tvgId e norm não existem no Epg
    // Esperado: o nome aparece; nenhum texto de programa, nenhum "—", nenhuma linha vazia reservada
    composeRule.onNodeWithText("Canal Teste").assertIsDisplayed()
    composeRule.onNodeWithTag("epg-now").assertDoesNotExist()
}
```

- [ ] **Step 2: Rodar e verificar que falha**

Run: `cd mobile-native && gradle :app:compileReleaseAndroidTestKotlin --no-daemon`
Expected: FAIL por `LiveScreen` inexistente

- [ ] **Step 3: Implementar `LiveScreen(st)` em `Live.kt`**

`LazyColumn` de canais. Cada linha: logo (Coil, 56 dp), nome, e o programa atual de `Epg.nowTitle(it)` **somente quando não for nulo** — a linha do programa não é composta quando não há EPG, com a etiqueta de teste `epg-now`. Fita de categorias (`Library.liveGroups`) numa `LazyRow` no topo, mais um `TextField` de filtro que peneira por `it.norm.contains(Util.norm(texto))`. Toque abre o player via `st.playItem(it)`.

- [ ] **Step 4: Rodar e verificar que passa**

Run: `cd mobile-native && gradle :app:compileReleaseAndroidTestKotlin --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Implementar `MoviesScreen` e `SeriesScreen` em `Grid.kt`**

`LazyVerticalGrid` de 3 colunas com `PosterCard`. `LazyRow` de categorias no topo: `Library.movieGroups` em Filmes e `Library.serieGroups` em Séries. Botão 📅 Ano abrindo diálogo com os anos presentes (`Util.yearOf` e o cache de metadados), mais "✕ Limpar"; o filtro zera ao trocar de aba. `PullToRefresh` chamando a recarga das listas. Guardar e restaurar a posição da rolagem ao voltar dos detalhes, com `rememberSaveable`.

- [ ] **Step 6: Implementar `HomeScreen` em `Home.kt`**

`LazyColumn` de prateleiras, cada uma uma `LazyRow`: Continuar assistindo (de `st.continueList()`, que já vem ordenado e sem os terminados, com ✕ chamando `st.removeProg(item.key)`), Destaques, Em alta e Lançamentos (de `Trending`), e Favoritos em Canais / Filmes / Séries (de `st.favs`). Prateleira sem itens não é composta. Recompor quando `st.progVer` mudar.

- [ ] **Step 7: Implementar `SearchScreen` em `Search.kt`**

`TextField` no topo, chamando `st.search(q)` com atraso de ~300 ms a cada digitação. Resultados em duas seções, Filmes e Séries. **Sem seção de canais** — `AppState.search` varre só `movies + series`; canal se acha pelo filtro da aba TV.

- [ ] **Step 8: Ligar as cinco telas no `when (st.tab)` do `Shell.kt`**

- [ ] **Step 9: Verificar que compila**

Run: `cd mobile-native && gradle :app:compileReleaseKotlin --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: Commit**

```bash
git add mobile-native
git commit -m "celular: abas Inicio, Filmes, Series, TV e Busca"
```

---

### Task 7: Detalhes, menu, listas e aviso de 1 tela

**Files:**
- Create: `mobile-native/app/src/main/java/com/natanael/ntplay/mobile/Detail.kt`
- Create: `mobile-native/app/src/main/java/com/natanael/ntplay/mobile/Menu.kt`
- Modify: `mobile-native/app/src/main/java/com/natanael/ntplay/mobile/Shell.kt`
- Test: `mobile-native/app/src/androidTest/java/com/natanael/ntplay/mobile/MenuTest.kt`

**Interfaces:**
- Consumes: `AppState.detail: Item?`, `.loadEps(it)`, `.playItem(it, ep, q)`, `.openGroups(it)`, `.favs`, `.refreshAccounts()`, `.accountLogin`, `.accountLogout()`, `.accessText()`, `.syncNow`, `Store.account(id): String`, `Store.setAccount`, `Store.lists(): MutableList<ListMeta>`, `Xtream.Account(exp, status, maxCon, active)`, `Util.cleanT`.
- Produces: `@Composable fun DetailScreen(st)`, `@Composable fun MenuSheet(st, onClose: () -> Unit)`, `@Composable fun OneScreenWarning(maxCon: Int)`.

- [ ] **Step 1: Escrever o teste que falha — conta de 1 tela avisa**

Item 2 do Review Focus.

```kotlin
@Test
fun contaDeUmaTelaAvisa() {
    // Store.account(id) com maxCon == 1
    composeRule.onNodeWithText("Esta lista permite 1 tela por vez", substring = true).assertIsDisplayed()
}

@Test
fun contaDeDuasTelasNaoAvisa() {
    // maxCon == 2 — o aviso não deve aparecer
    composeRule.onNodeWithText("1 tela por vez", substring = true).assertDoesNotExist()
}
```

- [ ] **Step 2: Rodar e verificar que falha**

Run: `cd mobile-native && gradle :app:compileReleaseAndroidTestKotlin --no-daemon`
Expected: FAIL por `MenuSheet` ou `OneScreenWarning` inexistente

- [ ] **Step 3: Implementar `OneScreenWarning(maxCon: Int)` e `MenuSheet(st, onClose)` em `Menu.kt`**

`OneScreenWarning` compõe apenas quando `maxCon == 1`, com o texto "Esta lista permite 1 tela por vez: TV e celular não podem assistir ao mesmo tempo." O valor vem de `Store.account(id)`, cujo formato é `exp|status|maxCon|active|ts` (ver `AppState.refreshAccounts`) — ler o terceiro campo.

`MenuSheet` é um `ModalBottomSheet` com: Conta e nuvem (usuário, `st.accessText()`, Sincronizar agora, Sair), Listas e provedores (nome, vencimento, telas, Atualizar, Excluir, Adicionar por link e por Xtream, e o `OneScreenWarning` por lista), Preferências (adulto, legendas, pular abertura, `bufferMode`, `adultByName`, `base64`, atualização automática), Favoritos e grupos, Infantil (`Library.kids`) e Animes (`Library.anime`). Chamar `st.refreshAccounts()` num `LaunchedEffect` ao abrir.

- [ ] **Step 4: Rodar e verificar que passa**

Run: `cd mobile-native && gradle :app:compileReleaseAndroidTestKotlin --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Implementar `DetailScreen(st)` em `Detail.kt`**

Capa grande no topo com o título sobreposto, sinopse, nota e ano. Botões Assistir (`st.playItem`), ★ Favoritar e ＋ Grupo (`st.openGroups`). Série: seletor de temporada e `LazyColumn` de episódios com nomes limpos ("Episódio 4"), carregados por `st.loadEps(it)` com botão de nova tentativa quando falha. Continuar de onde parou quando houver progresso.

- [ ] **Step 6: Ligar `DetailScreen` (quando `st.detail != null`) e o botão ☰ ao `MenuSheet` no `Shell.kt`**

- [ ] **Step 7: Verificar que compila**

Run: `cd mobile-native && gradle :app:compileReleaseKotlin --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add mobile-native
git commit -m "celular: detalhes, menu, listas e aviso de 1 tela"
```

---

### Task 8: Player de toque

**Files:**
- Create: `mobile-native/app/src/main/java/com/natanael/ntplay/mobile/Player.kt`
- Modify: `mobile-native/app/src/main/java/com/natanael/ntplay/mobile/Shell.kt`
- Modify: `mobile-native/app/src/main/java/com/natanael/ntplay/mobile/MainActivity.kt`

**Interfaces:**
- Consumes: `AppState.play: PlayReq?`, `.closePlayer()`, `.saveProg(req, pos, dur)`, `.next(req)`, `.prev(req)`, `.toast(msg)`, `.subs`, `Store.bufferMode`, `Store.skipIntro`, `PlayReq(item, ep, resume)`, `LiveLoopDataSource(upstream, url)`, `Net.client`, `Util.UA`, `Epg.nowNext(it)`.
- Produces: `@Composable fun PlayerScreen(st: AppState, req: PlayReq)`.

- [ ] **Step 1: Implementar a construção do ExoPlayer em `Player.kt`**

Copiar a receita de `tv-native/.../Player.kt:150-172`: `OkHttpDataSource.Factory(Net.client).setUserAgent(Util.UA)`; para canal ao vivo, envolver em `DataSource.Factory { LiveLoopDataSource(okF.createDataSource(), req.ep.url) }`; `DefaultExtractorsFactory` com `FLAG_ALLOW_NON_IDR_KEYFRAMES or FLAG_DETECT_ACCESS_UNITS`; `DefaultRenderersFactory` com `setEnableDecoderFallback(true)` e `EXTENSION_RENDERER_MODE_ON`; `DefaultLoadControl` com os mesmos tempos por `bufferMode` (ao vivo fixo em 20/60 s); `trackSelectionParameters` com áudio "pt" e qualidade máxima.

**Não copiar** a troca de modo de tela HDMI (`setDisplayModeFor`) — é só de TV. **Não referenciar `Fut`**: `Futebol.kt` não é copiado, então o EPG do player vem de `Epg.nowNext(req.item)`, não de `Fut.epgOf`.

- [ ] **Step 2: Implementar os controles de toque**

Tela cheia em paisagem (`requestedOrientation` em `SENSOR_LANDSCAPE` ao entrar, de volta a `PORTRAIT` ao sair) e barras do sistema escondidas. Gestos: toque simples alterna os controles (que escondem sozinhos em 4 s); duplo toque à esquerda `seek(-10_000)` e à direita `seek(+10_000)`; arrastar na barra de tempo busca; arrastar na vertical na metade esquerda ajusta o brilho (`window.attributes.screenBrightness`) e na direita o volume (`AudioManager`); no ao vivo, deslizar na vertical troca de canal (`st.next`/`st.prev`).

Controles visíveis: ⏸/▶, ⏪10/10⏩, barra de tempo com posição e duração, CC Legenda, Áudio, ★ Favoritar, Próximo (quando houver), Pular abertura (`Store.skipIntro`) e Sair. No ao vivo, nome do canal e o programa atual quando `Epg.nowNext` devolver algo.

- [ ] **Step 3: Implementar a recuperação de erro**

Mesma lógica de `tv-native/.../Player.kt:280-295`: reconexão automática até 8 tentativas; na terceira, canal ao vivo com `/live/` terminando em `.ts` tenta `.m3u8`. Erro `ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES` mostra o aviso com a opção de abrir em outro player (VLC/MX) por `Intent`.

- [ ] **Step 4: Salvar o progresso ao sair**

`st.saveProg(req, player.currentPosition, player.duration.coerceAtLeast(0))` ao fechar, e também em `onPause` da atividade, para não perder o ponto se o usuário sair pelo botão do sistema.

- [ ] **Step 5: Ligar no `Shell.kt` — `st.play != null` mostra `PlayerScreen` sobre tudo**

- [ ] **Step 6: Verificar que compila**

Run: `cd mobile-native && gradle :app:compileReleaseKotlin --no-daemon`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Verificar que a TV não foi tocada**

Run: `git diff --stat HEAD -- tv-native/`
Expected: saída vazia

- [ ] **Step 8: Commit**

```bash
git add mobile-native
git commit -m "celular: player de toque"
```

---

### Task 9: Workflow de compilação e publicação

**Files:**
- Create: `.github/workflows/android-mobile.yml`

**Interfaces:**
- Consumes: Tarefas 1–8; segredos `NT_KEYSTORE_B64` e `NT_KS_PASS`.
- Produces: release `mob-latest` com `NTPlay-Celular.apk` e `mob-version.json` (`code`, `name`, `min`).

- [ ] **Step 1: Criar o workflow**

Usar `.github/workflows/android-tv.yml` como modelo. Diferenças obrigatórias:

- `on.push.paths`: **apenas** `mobile-native/**` e `.github/workflows/android-mobile.yml`. **Não** incluir `tv-native/**` — a propagação de uma correção da TV para o celular é por `workflow_dispatch`, decidida por quem mantém.
- `working-directory: mobile-native`, comando `gradle :app:assembleRelease`.
- APK copiado para `NTPlay-Celular.apk`; `versionName` lido de `mobile-native/app/build.gradle.kts`; `min` lido de `mobile-native/min_version`; JSON gravado em `mob-version.json`.
- `gh release delete mob-latest --yes --cleanup-tag || true` e `gh release create mob-latest ...`.
- Manter a anotação `::error::` que extrai as linhas de erro do Gradle — é o único jeito de ler a falha sem abrir o log.

- [ ] **Step 2: Verificar que o workflow não dispara em mudanças da TV**

Run: `grep -A6 '^on:' .github/workflows/android-mobile.yml`
Expected: nenhuma linha com `tv-native`

- [ ] **Step 3: Verificar que o workflow da TV não foi alterado**

Run: `git diff --stat HEAD -- .github/workflows/android-tv.yml tv-native/`
Expected: saída vazia

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/android-mobile.yml
git commit -m "celular: workflow de compilacao e release mob-latest"
```

- [ ] **Step 5: Enviar e conferir a compilação**

```bash
git push origin main
```
Expected: o workflow "Android celular (APK)" conclui com sucesso e a release `mob-latest` recebe `NTPlay-Celular.apk` e `mob-version.json`. A release `tv-latest` permanece intocada.

---

## Depois do plano

O APK sai da release `mob-latest`. O Actions garante que compila, não que o comportamento está certo: instalar no celular e conferir, nesta ordem, entrada com conta existente, listas descendo da nuvem, canal ao vivo, filme, episódio de série e o aviso de 1 tela. É a mesma forma de trabalho da TV, onde todo teste de comportamento é feito no aparelho.
