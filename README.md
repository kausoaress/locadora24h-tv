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
| `bloqueio()` / `setBloqueio(ligado)` | Estado e contadores do bloqueador de anúncios (JSON) / liga ou desliga (só obedece o catálogo; a escolha fica guardada) |

**Quadros por segundo:** 8 segundos depois de um jogo abrir, o app conta `requestAnimationFrame` por 3 s e guarda o resultado (por jogo, no próprio app).
É isso que alimenta o selo "Roda liso nesta TV" dos jogos já jogados. Site de jogo com campo de texto: ao receber foco, o teclado do Android abre.

**Fluidez:** aceleração por hardware, sem barras de rolagem nem efeito de "puxar" nas bordas, cache padrão do WebView, sem animações do Android por cima e sem registros (logs).
Ao abrir, uma tela escura (#0E0F14) com o selo "24h" aparece no lugar do flash branco.

## Bloqueador de anúncios

Só dentro deste app (e do `.exe` do Windows). É honesto dizer: **reduz** muitos anúncios, mas não garante que todos sumam.

- `shouldInterceptRequest` devolve uma resposta **vazia (200)** para imagens, scripts e quadros de domínios de anúncio (o domínio ou um "pai" dele está na lista). O quadro principal nunca é barrado por aqui (a tela não fica em branco).
- Lista embutida: `app/src/main/assets/bloqueio-hosts.txt` (StevenBlack/hosts, licença MIT; só domínios, um por linha). Para atualizar: `ferramentas/atualizar-bloqueio.bat` (na pasta do site) e depois `lancar-versao.bat`.
- Listas extras e exceções: baixadas de `https://locadora24h.vercel.app/api/bloqueio` toda vez que o app abre (sem rede, vale a última guardada). Edite no `/admin`, aba **Anúncios**.
- **Nunca** bloqueia o domínio do catálogo nem o da página principal do jogo aberto (nem o mesmo site). Exceções do `/admin` também nunca são bloqueadas.
- Janela nova (`window.open`) sem clique de verdade e troca de site automática (`location.href` sem toque, depois dos 3 primeiros segundos da página) são barradas; com clique, abre na mesma janela como sempre.
- Um CSS esconde blocos comuns de anúncio (`ins.adsbygoogle`, `doubleclick`...), sem encostar no jogo.
- Menu do jogo (Start segurado) e menu do catálogo (☰): **"Bloqueador de anúncios: ligado/desligado"** (guardado). Quando um jogo não abre, o aviso sugere desligar.
- Diagnóstico (`?debug=1`): mostra domínios carregados, pedidos e janelas barrados e a origem da lista extra.
- Código: `BloqueioAnuncios.kt` (regras, testável: `BloqueioAnunciosTest`, 25 testes) e os ganchos no `MainActivity.kt`.

## Pastas e arquivos

| Arquivo | Para que serve |
|---|---|
| `app/src/main/kotlin/...` | O código: `MainActivity` (tela e WebView), `Controle` (ponte do controle), `Mapa` (teclas e mapa), `PonteJs` (o que o site enxerga), `MenuApp` (menu por cima do WebView), `VerificadorLinks` ("No ar"), `BloqueioAnuncios` (bloqueador de anúncios) |
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

## Ver o que o app está fazendo (Logcat)

O app escreve cada passo no Logcat do Android com a tag **`L24H`**: o `abrirJogo` recebido (url e id), o `loadUrl`, `onPageStarted`, `onPageFinished`,
`onReceivedError` (com código e descrição), `onReceivedHttpError`, `onReceivedSslError`, `onRenderProcessGone`, os user agents usados e o limite de 15 s.
Fica ligado também na versão final (é só texto).

1. Deixe a Depuração ADB ligada no Fire TV (veja "Instalar no Fire TV pela rede").
2. **Dois cliques em `logcat-da-tv.bat`**: ele conecta na TV, grava tudo da tag `L24H` em `logcat-L24H.txt` e mostra na tela.
3. Para acompanhar ao vivo, num terminal: `adb connect IP:5555` e depois `adb logcat -s L24H` (o `adb` fica em `%USERPROFILE%\Android\Sdk\platform-tools`). Ctrl+C para parar.

No site, o log do modo TV aparece em **?debug=1** ou **segurando Select por 2 segundos**: mostra a versão do site e do app, a URL do jogo, o user agent e os últimos eventos.

## Ferramentas usadas (só linha de comando)

Java 17 (Microsoft OpenJDK), Android SDK (command-line tools, platform-tools, build-tools 34, plataforma 34), Gradle 8.10.2 (wrapper),
Android Gradle Plugin 8.7.3, Kotlin 2.0.21 e o GitHub CLI (`gh`). Para compilar na mão:

```
set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot
set ANDROID_HOME=%USERPROFILE%\Android\Sdk
gradlew.bat testDebugUnitTest assembleDebug
```
