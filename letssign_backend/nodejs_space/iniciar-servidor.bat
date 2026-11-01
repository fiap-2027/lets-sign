@echo off
setlocal enabledelayedexpansion
rem Liga o servidor do EasySign (porta 3000). Deixe esta janela aberta enquanto testa o app.
rem Para desligar: feche a janela ou aperte Ctrl+C.
cd /d "%~dp0"
title Servidor EasySign
if not exist node_modules (
  echo Instalando dependencias pela primeira vez...
  call npm install --no-audit --no-fund || goto erro
  call npx prisma generate || goto erro
)
call npx prisma migrate deploy || goto erro
echo.
echo Compilando...
call npx nest build || goto erro
echo.
for /f "tokens=2 delims=:" %%a in ('ipconfig ^| findstr /c:"IPv4"') do (
  set ip=%%a
  echo Endereco para o celular: http://!ip: =!:3000/
)
echo Servidor ligando em http://localhost:3000 ...
echo.
node dist\src\main.js
pause
exit /b

:erro
echo.
echo Algo deu errado. Copie a mensagem acima e mande para o Claude.
pause
