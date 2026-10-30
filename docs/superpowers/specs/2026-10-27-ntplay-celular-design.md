# NTPlay Celular — app nativo Android para telefone

Spec de desenho. Autor: Natanael G Silva · 27/10/2026
Reconferida em 30/10/2026 contra a TV 1.10.6 — os três alvos de remendo seguem idênticos.
Repositório `NataNpg/NTPlay`

---

## 1. Objetivo

Criar um app Android **nativo para celular**, irmão do app de Android TV que já existe,
reaproveitando o motor já provado (M3U, Xtream, metadados, nuvem) e escrevendo do zero
só a parte que é de toque: navegação e player.

O app de celular é um **companheiro completo da TV**:

- Entra com a **mesma conta** (usuário e senha criados no painel `www/contas.html`),
  sujeita à mesma validade. Sem conta válida, o app não abre.
- Compartilha a **mesma nuvem** da TV: listas, favoritos, grupos e "continuar assistindo"
  são os mesmos blocos criptografados. Parar um filme no celular e continuar na TV é o
  comportamento esperado, não um efeito colateral.

### Restrição que manda nesta spec

**Nenhum arquivo dentro de `tv-native/` é alterado.** O app de TV que está no ar na
1.10.1 não muda, não é recompilado e não é republicado. O workflow `android-tv.yml`
filtra por `tv-native/**` e por ele mesmo; como nenhum dos dois é tocado, ele nunca
dispara. A release `tv-latest` permanece como está, e não existe nada a reverter.

### Critérios de sucesso

1. Instala ao lado do app de TV, sem conflito, assinado com a mesma chave.
2. Entra com uma conta existente e, em menos de um minuto, já mostra as listas que
   estavam na TV — sem o usuário cadastrar nada de novo.
3. Abre canal ao vivo, filme e episódio de série, com os mesmos servidores do usuário.
4. O app de TV continua intocado: mesma release, mesmo APK, mesmo `versionCode`
   (1.10.6 / 1037 em 30/10/2026).

### Fora de escopo nesta versão

- Aba **Futebol** (é a tela mais cara e a menos natural no telefone).
- Troca de modo de tela HDMI (só faz sentido na TV).
- Servidor HTTP local para QR code (no celular o link é colado direto).
- Picture-in-picture e áudio em segundo plano.

---

## 2. Arquitetura

### 2.1 Estrutura

Uma pasta nova no repositório, espelhando o formato de `tv-native/`: projeto Gradle
próprio, com seu `settings.gradle.kts` e seu `gradle.properties`.

```
tv-native/                   INTOCADO
mobile-native/               novo
  settings.gradle.kts
  gradle.properties
  min_version
  app/
    build.gradle.kts         applicationId com.natanael.ntplay.mobile
    src/main/java/com/natanael/ntplay/mobile/
      MainActivity.kt  UI.kt  Player.kt
    src/main/res/            ícone, splash, file_paths.xml
    build/generated/core/    (gerado na compilação, não versionado)
```

### 2.2 Como o motor chega ao celular

Uma tarefa Gradle no `mobile-native/app/build.gradle.kts` copia **13 arquivos** de
`tv-native/app/src/main/java/com/natanael/ntplay/tv/` para
`build/generated/core/com/natanael/ntplay/core/`, trocando a linha `package` de
`com.natanael.ntplay.tv` para `com.natanael.ntplay.core`. Essa pasta gerada entra como
`srcDir` do módulo, e `preBuild` depende da tarefa — então funciona tanto no GitHub
Actions quanto no Android Studio, sem passo manual.

**Dez arquivos vão sem nenhuma alteração de lógica**, só a troca de `package`:

`Store` · `Library` · `Xtream` · `Net` · `Util` · `Logos` · `Trending` · `Epg` ·
`LiveLoop` · `Sync`

Isso é possível porque, verificado no código, esses dez são **autocontidos**: não
referenciam `AppState`, `Presence`, `Updater`, `UI`, `Player`, `Futebol` nem
`LocalServer`, e nenhum deles usa `R.*` ou `BuildConfig`. Tudo que é específico do app é
resolvido em tempo de execução por `ctx.packageName` e `ctx.packageManager`. Como os dez
vão todos para o mesmo pacote novo, as chamadas entre eles continuam válidas.

**Três arquivos recebem remendos pontuais** durante a cópia:

| Arquivo | Trecho procurado | Vira |
|---|---|---|
| `AppState.kt` (linha 19) | `val TABS = listOf("RECOMENDAÇÕES", …)` | `val TABS = listOf("INÍCIO", "FILMES", "SÉRIES", "TV", "BUSCA")` |
| `Presence.kt` (linha 86) | `put("p", "tv")` e `put("v", "tv-" + ver)` | `"mob"` e `"mob-" + ver` |
| `Updater.kt` (linhas 16 e 25) | `tv-latest` e `tv-version.json` | `mob-latest` e `mob-version.json`, e o endereço passa a usar `NataNpg` (a TV ainda usa `natanpg` minúsculo e depende do redirecionamento do GitHub — pendência 6 do documento geral; o celular nasce sem essa dívida) |

**Cada remendo verifica antes de trocar.** Se o trecho procurado não for encontrado — por
exemplo, porque uma versão futura da TV reescreveu aquela linha — a tarefa lança
`GradleException` com o nome do arquivo e o trecho que faltou, e a compilação do celular
para. Nunca gera silenciosamente um app que se identifica como TV ou que procura
atualização na release errada.

`Store.tab` não precisa de remendo: ele devolve `"RECOMENDAÇÕES"` como padrão, que não
existe na lista de abas do celular, e a linha 29 do `AppState`
(`if (it in TABS) it else TABS[0]`) já cai sozinha em `"INÍCIO"`. Os dois apps também têm
`applicationId` diferente, logo SharedPreferences separados — um não lê a preferência do
outro.

`Futebol.kt` e `LocalServer.kt` não são copiados: o Futebol está fora de escopo e o
servidor local do QR não faz sentido no celular, onde o link é colado direto.

### 2.3 O que é escrito do zero

`MainActivity.kt`, `UI.kt` (telas de toque) e `Player.kt` (player de toque), no pacote
`com.natanael.ntplay.mobile`, importando `com.natanael.ntplay.core.*`.

### 2.4 Consequência aceita desta abordagem

O motor continua existindo num lugar só, então uma correção feita no Xtream, no Net ou no
Sync para a TV chega ao celular na próxima compilação do celular. O preço é que uma
mudança no motor da TV pode quebrar a compilação do celular. Quando isso acontecer,
quebra **só a do celular** — a da TV não depende de nada em `mobile-native/`.

---

## 3. Telas do celular

Barra de navegação inferior com cinco destinos: **Início · Filmes · Séries · TV · Busca**.
Tudo em retrato, menos o player.

| Tela | Conteúdo |
|---|---|
| **Entrada** | Usuário e senha em retrato; QR code e WhatsApp (42) 92002-8569 para pedir acesso ou renovação. Mesma regra da TV: sem conta válida, nada é acessível. |
| **Início** | Prateleiras horizontais: Continuar assistindo (com ✕ para remover), Destaques, Em alta, Lançamentos, e Favoritos separados em Canais / Filmes / Séries. |
| **Filmes** / **Séries** | Grade de 3 capas por linha, fita de categorias rolando na horizontal no topo, filtros 📅 Ano e selo LEG/DUB (reaproveitados de `Util.audioTag` e `Util.yearOf`). Puxar para baixo atualiza a lista. |
| **TV** | Lista vertical (não grade): logo do canal, nome e o programa que está passando agora, via `Epg` (XMLTV, com `get_short_epg` de reserva). Fita de categorias no topo e **campo de filtro próprio**, que peneira a lista de canais na própria tela — é a forma de achar canal por nome no celular, e não encosta no motor. |
| **Busca** | Campo no topo; resultados agrupados em Filmes / Séries. **Sem canais ao vivo**, igual à TV desde a 1.10.6 (`AppState.search` varre `movies + series`) — assim o `AppState` é copiado sem remendo nenhum na busca. Canal se acha pelo campo próprio da aba TV. |
| **Detalhes** | Capa grande no topo, sinopse, nota e ano; botões Assistir, ★ Favoritar e ＋ Grupo. Série com seletor de temporada e episódios em lista, carregados sob demanda. |
| **Menu ☰ (topo)** | Listas e provedores (com vencimento e telas da conta Xtream), Conta e nuvem, Preferências, Favoritos e grupos, Infantil, Animes. |

Infantil e Animes usam `Library.kids` e `Library.anime`, que já existem e funcionam por
tema de conteúdo (`Util.theme`), sem relação com os nomes das abas.

---

## 4. Player de toque

Escrito do zero, mas reaproveitando o miolo já provado do `Player.kt` da TV:
`LiveLoopDataSource` para os canais entregues em pedaços, reconexão automática
(8 tentativas), fallback `.ts` → `.m3u8` e os parâmetros de buffer por tipo de conteúdo.

Controles:

- Toque simples mostra e esconde os controles.
- Duplo toque no lado esquerdo/direito: ±10 s.
- Arrastar na barra de tempo para avançar e voltar.
- Arrastar na vertical: brilho na metade esquerda, volume na direita.
- No ao vivo, deslizar para cima/baixo troca de canal (equivalente ao ▲▼ da TV).
- Paisagem automática em tela cheia; voltar sai do player.

Mantidos da TV: exibição da qualidade real, faixas de legenda e áudio, pular abertura,
próximo episódio, e o aviso de erro de decodificação com a opção de abrir em outro player.

---

## 5. Dados, estado e erros

Sem mudança de modelo. O celular usa o mesmo `Store` (mesmas chaves de SharedPreferences,
no espaço dele), o mesmo `Sync` com as mesmas regras de união, e as mesmas mensagens de
erro detalhadas do `Net` (vários User-Agents, troca https→http).

**Correções herdadas de graça.** Como o motor é copiado da TV no estado em que ela está,
o celular nasce com o que foi consertado lá até a 1.10.6. A mais importante é a da 1.10.3:
`Item.norm` passou de `LazyThreadSafetyMode.NONE` para `PUBLICATION` dentro de
`Library.kt`. Era o que fechava o app na busca, levou duas tentativas para ser achado na
TV, e é um defeito de corrida entre threads que seria bem difícil de diagnosticar de novo
num app novo.

Única diferença deliberada: **memória de imagens**. O celular tem bem mais RAM que o stick
de TV, então o Coil usa `ARGB_8888` em vez de `RGB565` e um cache de disco maior — capas
mais nítidas, sem o risco que justificou a restrição na TV (versão 1.2.1).

---

## 6. Compilação e entrega

| Item | TV (inalterado) | Celular (novo) |
|---|---|---|
| Pasta | `tv-native/` | `mobile-native/` |
| `applicationId` | `com.natanael.ntplay.tv` | `com.natanael.ntplay.mobile` |
| Versão | `1.10.6` (código 1037) | `1.0.0` |
| Workflow | `android-tv.yml` | `android-mobile.yml` (novo) |
| Dispara em | `tv-native/**` | `mobile-native/**` e manualmente |
| Release | `tv-latest` | `mob-latest` |
| APK | `NTPlay-TV.apk` | `NTPlay-Celular.apk` |
| Versão JSON | `tv-version.json` | `mob-version.json` |
| Trava de versão | `tv-native/min_version` | `mobile-native/min_version` |

O workflow do celular dispara **só** em `mobile-native/**` e por `workflow_dispatch`.
Deliberadamente não dispara em mudanças de `tv-native/`: assim você controla quando uma
correção feita na TV passa para o celular, bastando rodar o workflow à mão. (Se preferir
propagação automática depois, é uma linha a mais no filtro de caminhos.)

O APK é compilado com `gradle assembleRelease` dentro de `mobile-native/`, projeto Gradle
independente — o build da TV e o do celular nunca se cruzam.

**Assinatura**: os dois apps usam a mesma chave, dos secrets `NT_KEYSTORE_B64` e
`NT_KS_PASS` que já existem. Como o `applicationId` é diferente, os dois APKs convivem no
mesmo aparelho sem conflito.

**Servidor**: nada muda. A Edge Function `ntsync` e a tabela `ntplay_accounts` atendem o
celular como estão — o app de celular é só mais um cliente da mesma conta.

---

## 7. Riscos

O risco para o app de TV é **zero por construção**: nenhum arquivo de `tv-native/` é
lido para escrita, o workflow da TV não dispara e a release `tv-latest` não é tocada.

O risco que resta é todo do lado do celular:

1. **A tarefa de cópia.** Se um dos três remendos deixar de encontrar seu trecho, a
   compilação do celular falha com mensagem nomeando o arquivo. É falha barulhenta e
   contida, mas exige um ajuste no `build.gradle.kts` do celular quando acontecer.
2. **Não há ambiente local de compilação.** Como já acontece na TV, todo ajuste é testado
   compilando no GitHub Actions, e os testes de verdade são feitos no aparelho.
3. **Player de toque é código novo.** É a maior parte do trabalho original e a que mais
   vai precisar de iteração com o aparelho na mão.

4. **Mais aparelhos por conta.** Este é o efeito que não aparece no código. Hoje cada
   cliente usa a conta numa TV; com o celular, passa a usar em dois aparelhos, e os dois
   recebem o **mesmo blob da nuvem**, logo a mesma lista com as mesmas credenciais. O que
   limita não é o NTPlay nem o Supabase: é o `max_connections` do provedor da lista
   (seção 8 do documento geral — a conta `teste` já bate no teto de 20 aparelhos e é isso
   que causa o "fica carregando"). Lançar o celular **aumenta essa pressão**, e vale
   avisar os clientes de lista de 1 tela que TV e celular não tocam ao mesmo tempo.
   Efeito colateral já conhecido: aparelhos da mesma conta gravam no mesmo registro a cada
   90 s, então favoritos e "continuar assistindo" de um sobrescrevem os do outro.

   **Decidido:** o celular **avisa na tela** quando a conta Xtream da lista reporta
   `max_connections: 1`, explicando que TV e celular não podem assistir ao mesmo tempo.
   O dado já vem do `Xtream` (é o mesmo que alimenta "telas em uso" em ☰ Listas), então é
   só apresentação — nenhum remendo no motor.

   **Não entra nesta versão:** a sobrescrita do progresso entre aparelhos da mesma conta
   fica como está hoje na TV. Resolver de verdade exigiria mudar o formato do blob na
   nuvem (progresso por aparelho em vez de um só por conta), o que mexeria no `Sync` e,
   por consequência, no app de TV — justamente o que esta spec se compromete a não fazer.
   Fica anotado para quando houver uma mudança de formato planejada nos dois lados.

### Oportunidade relacionada (fora desta spec)

A pendência 1 do documento geral é que as versões de **PC e web/celular não têm login** —
quem não consegue conta na TV usa essas versões sem barreira. A versão web/celular
(Capacitor, pasta `www/`) é exatamente o que este app nativo substitui, com login
obrigatório. Depois que o celular estiver no ar e testado, retirar a página web do ar
fecha metade daquela porta sem perder nada para o usuário legítimo. Decisão separada, a
tomar depois — não faz parte deste trabalho.
