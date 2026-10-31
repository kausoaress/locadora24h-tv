@echo off
rem Lanca uma versao nova do app: aumenta o numero, gera o APK assinado e publica no GitHub.
setlocal
cd /d "%~dp0"
chcp 65001 >nul
set "PASTA=%~dp0"
set "JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot"
set "ANDROID_HOME=%USERPROFILE%\Android\Sdk"
set "PATH=%JAVA_HOME%\bin;C:\Program Files\GitHub CLI;C:\Program Files\Git\cmd;%PATH%"

if not exist "%JAVA_HOME%\bin\java.exe" ( echo Nao achei o Java 17 em %JAVA_HOME% & goto :fim )
if not exist keystore.properties ( echo Falta o keystore.properties ^(a chave de assinatura^). Sem ele o APK nao pode ser assinado. & goto :fim )
gh auth status >nul 2>&1
if errorlevel 1 ( echo Voce nao esta logado no GitHub. Rode:  gh auth login & goto :fim )

rem 1) aumenta o numero da versao (guarda o antigo para desfazer se der erro)
for /f "tokens=2 delims==" %%a in ('findstr "versionCode" version.properties') do set /a ANTIGA=%%a
set /a NOVA=ANTIGA+1
> version.properties echo versionCode=%NOVA%
echo.
echo === Versao nova: 1.0.%NOVA% ^(a anterior era 1.0.%ANTIGA%^) ===

rem 2) gera o APK assinado
call "%~dp0gradlew.bat" assembleRelease
if errorlevel 1 (
  echo.
  echo O build deu erro. Voltando o numero da versao para %ANTIGA%.
  > version.properties echo versionCode=%ANTIGA%
  goto :fim
)

rem 3) copia para dist\locadora24h-tv.apk
if not exist dist mkdir dist
copy /y app\build\outputs\apk\release\app-release.apk dist\locadora24h-tv.apk >nul
if errorlevel 1 ( echo Nao consegui copiar o APK. & goto :fim )

rem 4) guarda no Git e envia
git add -A
git commit -m "Versao 1.0.%NOVA%"
git push
if errorlevel 1 ( echo O git push falhou. Resolva e rode de novo. & goto :fim )

rem 5) cria a versao no GitHub com o APK anexado (nome exato: locadora24h-tv.apk)
gh release create v1.0.%NOVA% dist\locadora24h-tv.apk --title "Locadora 24h TV 1.0.%NOVA%" --notes "APK assinado, versao 1.0.%NOVA%." --latest
if errorlevel 1 ( echo A criacao da versao no GitHub falhou. & goto :fim )

echo.
echo Versao 1.0.%NOVA% publicada! O endereco locadora24h.vercel.app/tv ja baixa esta versao.

:fim
echo.
pause
