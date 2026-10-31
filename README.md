# Locadora 24h TV

App Android (Kotlin, sem Android Studio) para o **Fire TV**. Ele abre o catálogo https://locadora24h.vercel.app/?modo=tv&app=1
num WebView em tela cheia e entrega o controle de videogame ao site como teclas de teclado.

- **minSdk 22** (Fire TVs antigos), **targetSdk 34**. Aparece na tela inicial da TV (categoria `LEANBACK_LAUNCHER`).
- Todos os links abrem dentro do app. **Voltar** (botão B ou voltar do controle remoto) volta uma página; na primeira tela pergunta "Sair do app?".
- Tela sempre acesa enquanto o app está aberto.

## Como o controle funciona

| Onde | O que acontece |
|---|---|
| **Catálogo** (locadora24h.vercel.app) | Direcional e analógico esquerdo = setas · A = Enter · B = voltar · X = `X` · Y = `Y` · LB = Page Up · RB = Page Down (as teclas do modo TV do site) |
| **Dentro de um jogo** | Cada botão vira a tecla do mapa do jogo, entregue ao WebView como tecla de verdade |
| **Start segurado por 2 s** (dentro de um jogo) | Menu do app: *Ver mapa do controle / Voltar ao catálogo / Continuar* |

O mapa vem do site: antes de abrir o jogo, o site chama `Locadora24hApp.setMapa(json)`. Exemplo:

```json
{ "A": "Z", "B": "X", "X": "C", "Y": "V", "LB": "Q", "RB": "E", "Start": "Enter", "Select": "Escape", "direcional": "setas" }
```

- `direcional` aceita `"setas"` ou `"wasd"` (vale também para o analógico esquerdo).
- Botão que não está no mapa fica **sem uso** (não faz nada).
- Teclas aceitas: letras, números, `Enter`, `Escape`, `Space`, `Shift`, `Ctrl`, `Alt`, `Tab`, `Backspace`, `F1`–`F12` e as setas (`Up`, `Down`, `Left`, `Right`).
- **Sem mapa** (o site chama `limparMapa()`): direcional = setas, A = Enter, B = Escape, X = Z, Y = X, Start = Enter.

Limitação: como o app entrega o controle como teclas, um jogo que use a Gamepad API do navegador (controle "de verdade") não recebe o controle.

## Pastas e arquivos

| Arquivo | Para que serve |
|---|---|
| `app/src/main/kotlin/...` | O código: `MainActivity` (tela e WebView), `Controle` (ponte do controle), `Mapa` (teclas e mapa), `PonteJs` (o que o site enxerga) |
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
