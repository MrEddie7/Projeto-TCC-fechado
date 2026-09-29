@echo off
REM ─────────────────────────────────────────────────────────────────────────
REM Atalho para o api.ps1.
REM
REM Existe porque o PowerShell do Windows costuma bloquear a execucao de
REM scripts .ps1 por ExecutionPolicy. Este wrapper chama o script com
REM -ExecutionPolicy Bypass, valido apenas para o processo atual.
REM
REM Uso:  api.cmd start | stop | restart | status | logs
REM ─────────────────────────────────────────────────────────────────────────

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0api.ps1" %*
exit /b %ERRORLEVEL%
