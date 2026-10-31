# Locadora 24h TV

App Android (Kotlin, sem Android Studio) para o **Fire TV**. Ele abre o catálogo https://locadora24h.vercel.app/?modo=tv&app=1
num WebView em tela cheia e entrega o controle de videogame ao site como teclas de teclado.

- **minSdk 22** (Fire TVs antigos), **targetSdk 34**. Aparece na tela inicial da TV (categoria `LEANBACK_LAUNCHER`).
- Todos os links abrem dentro do app. **Voltar** (botão B ou voltar do controle remoto) volta uma página; na primeira tela pergunta "Sair do app?".
- Tela sempre acesa enquanto o app está aberto.

## Como o controle funciona

| Onde | O que acontece |
|---|---|
| **Menu do app aberto** | Direcional ou analógico andam no menu · **A** escolhe · **B** fecha (o menu fica por cima do WebView e não rouba o foco) |
| **Catálogo** (locadora24h.vercel.app) | Direcional e analógico esquerdo = setas · **A** = Enter · **B** e o voltar do controle remoto = tecla Escape para o site (ele decide: volta uma tela ou pergunta se quer sair) · **X** = `X` · **Y** = `Y` · **LB** = Page Up · **RB** = Page Down · **Select** = `C` (celular como teclado) · **Start** = `M` (menu do site) |
| **Dentro de um jogo** | Cada botão vira a tecla do mapa do jogo, entregue ao WebView como tecla de verdade. O **voltar do controle remoto não sai do jogo**: abre o menu do app (*Continuar / Ver mapa do controle / Voltar ao catálogo*) |
| **Start segurado por 2 s** (dentro de um jogo) | Abre esse mesmo menu |

Dentro de um jogo o botão **B** do controle é uma tecla do jogo (a do mapa), não "voltar": se ele abrisse o menu, os jogos ficariam injogáveis.

O mapa vem do site: ao abrir o jogo, o site manda o mapa (JSON). Exemplo:

```json
{ "A": "Z", "B": "X", "X": "C", "Y": "V", "LB": "Q", "RB": "E", "Start": "Enter", "Select": "Escape", "direcional": "setas" }
```

- `direcional` aceita `"setas"` ou `"wasd"` (vale também para o analógico esquerdo).
- Botão que não está no mapa fica **sem uso** (não faz nada).
- Teclas aceitas: letras, números, `Enter`, `Escape`, `Space`, `Shift`, `Ctrl`, `Alt`, `Tab`, `Backspace`, `F1`–`F12` e as setas (`Up`, `Down`, `Left`, `Right`).
- **Sem mapa**: direcional = setas, A = Enter, B = Escape, X = Z, Y = X, Start = Enter.

Limitação: como o app entrega o controle como teclas, um jogo que use a Gamepad API do navegador (controle "de verdade") não recebe o controle.

## Ponte com o site (`window.Locadora24hApp`)

O modo TV do site usa estas chamadas (todas devolvem texto ou JSON e nunca lançam erro). Só são obedecidas `abrirJogo`, `verificarLinks` e `sair`
quando quem chama é o catálogo (e não um site de jogo):

| Chamada | O que faz |
|---|---|
| `abrirJogo(url, mapaJson, id)` | Guarda o mapa, marca "em jogo" e abre a URL. Se a página não carregar em **15 s** (ou der erro), volta ao catálogo e avisa o site (`window.L24HTV.toast(...)`) |
| `verificarLinks('[{"id":"...","url":"..."}]')` | Confere se cada site responde (HEAD, ou GET se não aceitar HEAD; limite de 6 s; **no máximo 3 ao mesmo tempo**, em segundo plano) e devolve `window.L24HTV.aoStatusLinks({id:{ok,ms,quando}})`. Site que responde 401/403/405/429 conta como "no ar" (só barrou o robô) |
| `infoAparelho()` | `{memoriaMB, modelo, androidApi}` |
| `controles()` | Nomes dos controles de jogo conectados, ex. `["8BitDo Pro 2"]` |
| `desempenho()` | `{id:{fps,quando}}`: quadros por segundo medidos de verdade nesta TV |
| `setMapa(json)`, `limparMapa()`, `versao()`, `sair()` | Definem/limpam o mapa, devolvem a versão do app, fecham o app |

**Quadros por segundo:** 8 segundos depois de um jogo abrir, o app conta `requestAnimationFrame` por 3 s e guarda o resultado (por jogo, no próprio app).
É isso que alimenta o selo "Roda liso nesta TV" dos jogos já jogados. Site de jogo com campo de texto: ao receber foco, o teclado do Android abre.

**Fluidez:** aceleração por hardware, sem barras de rolagem nem efeito de "puxar" nas bordas, cache padrão do WebView, sem animações do Android por cima e sem registros (logs).
Ao abrir, uma tela escura (#0E0F14) com o selo "24h" aparece no lugar do flash branco.

## Pastas e arquivos

| Arquivo | Para que serve |
|---|---|
| `app/src/main/kotlin/...` | O código: `MainActivity` (tela e WebView), `Controle` (ponte do controle), `Mapa` (teclas e mapa), `PonteJs` (o que o site enxerga), `MenuApp` (menu por cima do WebView), `VerificadorLinks` ("No ar") |
| `lancar-versao.bat` | Aumenta a versão, gera o APK assinado e cria a versão no GitHub |
| `instalar-na-tv.bat` | Instala o APK no seu Fire TV pela rede |
| `version.properties` | O número da versão (o `lancar-versao.bat` aumenta sozinho) |
| `chave-do-app/` e `keystore.properties` | **A chave de assinatura e a senha. Ficam fora do Git. Guarde uma cópia!** |

### A chave de assinatura

O Android só instala uma atualização por cima do app se ela for assinada com **a mesma chave**. Se você perder a pasta
`chave-do-app/` ou a senha, não dá mais para atualizar: seria preciso desinstalar o app de todas as TVs e instalar um app "novo".
Guarde uma cópia da pasta `chave-do-app/` **e** do `keystore.properties` num pendrive ou na nuvem (privada, nunca num repositório).

## Lançar uma versão nova

1. Dê dois cliques em `lancar-versao.bat` (é preciso estar logado no GitHub: `gh auth login`, uma vez só).
2. Ele aumenta o número da versão, gera `dist\locadora24h-tv.apk`, guarda no Git e cria a versão no GitHub.
3. O endereço **locadora24h.vercel.app/tv** passa a baixar essa versão.

## Instalar no Fire TV pela rede

1. **Ligar a depuração ADB:** no Fire TV, vá em *Configurações > Meu Fire TV > Sobre* e aperte o botão do meio **7 vezes** sobre o nome do aparelho
   (aparece "Você agora é um desenvolvedor"). Volte e abra *Opções do desenvolvedor*: ligue **Depuração ADB**.
   (Em alguns modelos também é preciso ligar *Apps de fontes desconhecidas*.)
2. **Achar o IP:** *Configurações > Meu Fire TV > Sobre > Rede* mostra o IP (algo como `192.168.0.25`). O PC e a TV precisam estar na mesma rede.
3. Dê dois cliques em `instalar-na-tv.bat`. Na primeira vez ele pede o IP e guarda em `tv-ip.txt`.
   A TV vai perguntar "Permitir depuração USB?": marque **Sempre** e confirme com o controle remoto.
4. Abra **Locadora 24h TV** na lista de apps.

Sem PC: no Fire TV instale o app **Downloader**, abra e digite `locadora24h.vercel.app/tv`.

## Ferramentas usadas (só linha de comando)

Java 17 (Microsoft OpenJDK), Android SDK (command-line tools, platform-tools, build-tools 34, plataforma 34), Gradle 8.10.2 (wrapper),
Android Gradle Plugin 8.7.3, Kotlin 2.0.21 e o GitHub CLI (`gh`). Para compilar na mão:

```
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot
set ANDROID_HOME=%USERPROFILE%\Android\Sdk
gradlew.bat testDebugUnitTest assembleDebug
```
