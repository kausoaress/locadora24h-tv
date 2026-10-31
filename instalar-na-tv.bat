@echo off
rem Instala o APK no Fire TV pela rede (ADB).
setlocal
cd /d "%~dp0"
chcp 65001 >nul
set "ADB=%USERPROFILE%\Android\Sdk\platform-tools\adb.exe"

if not exist "%ADB%" ( echo Nao achei o adb em %ADB% & goto :fim )
if not exist dist\locadora24h-tv.apk ( echo Nao achei dist\locadora24h-tv.apk. Rode o lancar-versao.bat primeiro. & goto :fim )

set "IP="
if exist tv-ip.txt set /p IP=<tv-ip.txt
if "%IP%"=="" (
  echo Ainda nao sei o IP do seu Fire TV. Para achar: no Fire TV, Configuracoes ^> Meu Fire TV ^> Sobre ^> Rede.
  set /p IP=Digite o IP do Fire TV ^(ex.: 192.168.0.25^):
  if "%IP%"=="" goto :fim
  > tv-ip.txt echo %IP%
  echo IP guardado em tv-ip.txt ^(para mudar, apague esse arquivo^).
)

echo.
echo Conectando em %IP%:5555 ... ^(se a TV perguntar "Permitir depuracao USB?", marque "Sempre" e aperte OK^)
"%ADB%" connect %IP%:5555
echo.
echo Instalando o app...
"%ADB%" install -r dist\locadora24h-tv.apk
if errorlevel 1 (
  echo.
  echo A instalacao falhou. Veja a mensagem acima. Se a TV nao respondeu, confira se a Depuracao ADB esta ligada.
  goto :fim
)
echo.
echo Pronto! Abra "Locadora 24h TV" na lista de apps da TV.

:fim
echo.
pause
