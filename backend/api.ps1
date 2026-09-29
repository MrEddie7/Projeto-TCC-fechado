#Requires -Version 5.1
<#
.SYNOPSIS
    VEICULOTRACKER - controle da API (versao Windows).

.DESCRIPTION
    Equivalente ao api.sh do Linux. Sobe/derruba a API e o simulador de
    hardware juntos, com PID e log por processo.

.EXAMPLE
    .\api.ps1 start
    .\api.ps1 stop
    .\api.ps1 restart
    .\api.ps1 status
    .\api.ps1 logs

.NOTES
    Se a ExecutionPolicy bloquear o script, use o atalho:
        api.cmd start
#>

$ErrorActionPreference = 'Stop'

$Dir    = Split-Path -Parent $MyInvocation.MyCommand.Path
$RunDir = Join-Path $Dir '.run'

function New-RunDir {
    if (-not (Test-Path $RunDir)) { New-Item -ItemType Directory -Path $RunDir | Out-Null }
}

# ── Auxiliarios ──────────────────────────────────────────────────────────

# Le uma chave do .env sem poluir o ambiente.
function Get-EnvValue {
    param([string]$Key, [string]$Default = '')
    $file = Join-Path $Dir '.env'
    if (-not (Test-Path $file)) { return $Default }
    $line = Select-String -Path $file -Pattern "^$Key=" -ErrorAction SilentlyContinue |
            Select-Object -Last 1
    if (-not $line) { return $Default }
    $val = ($line.Line -split '=', 2)[1].Trim().Trim('"').Trim("'")
    if ($val) { return $val } else { return $Default }
}

# PID so e considerado valido se ainda for um processo node vivo
# (evita que um PID reciclado derrube um programa qualquer).
function Get-LivePid {
    param([string]$PidFile)
    if (-not (Test-Path $PidFile)) { return $null }
    $raw = (Get-Content $PidFile -Raw -ErrorAction SilentlyContinue)
    if (-not $raw) { return $null }
    $id = [int]($raw.Trim())
    $p = Get-Process -Id $id -ErrorAction SilentlyContinue
    if (-not $p) { return $null }
    if ($p.ProcessName -ne 'node') { return $null }
    return $id
}

# Espera o /health responder antes de devolver o controle.
function Wait-ForHealth {
    param([string]$Url, [int]$TimeoutSec = 15)
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-RestMethod -Uri "$Url/health" -TimeoutSec 2 -ErrorAction Stop
            if ($r.ok) { return $true }
        } catch { }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

# ── start ────────────────────────────────────────────────────────────────

function Start-Api {
    $pidFile = Join-Path $RunDir 'api.pid'
    $outLog  = Join-Path $RunDir 'api.out.log'
    $errLog  = Join-Path $RunDir 'api.err.log'

    $existing = Get-LivePid $pidFile
    if ($existing) {
        Write-Host "[api] ja esta no ar (pid $existing) em $script:ApiUrl"
        return
    }

    foreach ($f in @($outLog, $errLog)) { Set-Content -Path $f -Value '' -NoNewline }

    # Start-Process exige arquivos distintos para stdout e stderr.
    $p = Start-Process -FilePath 'node' `
        -ArgumentList 'src/server.js' `
        -WorkingDirectory $Dir `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -NoNewWindow -PassThru

    Set-Content -Path $pidFile -Value $p.Id -NoNewline

    if (Wait-ForHealth -Url $script:ApiUrl) {
        Write-Host "[api] no ar (pid $($p.Id)) em $script:ApiUrl"
    } else {
        Write-Host "[api] FALHOU ao subir. Ultimas linhas do log:" -ForegroundColor Red
        Get-Content $outLog, $errLog -Tail 15 -ErrorAction SilentlyContinue |
            ForEach-Object { Write-Host "  $_" }
        throw "API nao respondeu /health em 15s"
    }
}

function Start-Simulator {
    $pidFile = Join-Path $RunDir 'sim.pid'
    $outLog  = Join-Path $RunDir 'sim.out.log'
    $errLog  = Join-Path $RunDir 'sim.err.log'

    if ($env:START_SIMULATOR -eq 'false') {
        Write-Host '[sim] pulado (START_SIMULATOR=false)'
        return
    }

    $existing = Get-LivePid $pidFile
    if ($existing) {
        Write-Host "[sim] ja esta no ar (pid $existing)"
        return
    }

    # Credenciais: env > .env > primeiro device do banco.
    $deviceId = if ($env:VTSIM_DEVICE_ID) { $env:VTSIM_DEVICE_ID } else { Get-EnvValue 'VTSIM_DEVICE_ID' }
    $apiKey   = if ($env:VTSIM_API_KEY)   { $env:VTSIM_API_KEY }   else { Get-EnvValue 'VTSIM_API_KEY' }

    if (-not $deviceId -or -not $apiKey) {
        $js = 'const { DatabaseSync } = require("node:sqlite");' +
              'const db = new DatabaseSync("./data/veiculotracker.db", { readOnly: true });' +
              'const r = db.prepare("SELECT device_id, api_key FROM devices ORDER BY id LIMIT 1").get();' +
              'console.log(r ? r.device_id + " " + r.api_key : "");'
        Push-Location $Dir
        try {
            $found = & node -e $js 2>$null
        } finally {
            Pop-Location
        }
        if ($found) {
            $parts   = $found.Trim() -split '\s+'
            $deviceId = $parts[0]
            $apiKey   = $parts[1]
        }
    }

    if (-not $deviceId -or -not $apiKey) {
        Write-Host '[sim] ignorado: sem device_id/api_key. Rode "npm run seed" antes.' -ForegroundColor Yellow
        return
    }

    foreach ($f in @($outLog, $errLog)) { Set-Content -Path $f -Value '' -NoNewline }

    # As variaveis precisam estar no ambiente antes do Start-Process para
    # que o filho as herde.
    $env:VTSIM_DEVICE_ID = $deviceId
    $env:VTSIM_API_KEY   = $apiKey
    $env:VTSIM_API_URL   = $script:ApiUrl

    $p = Start-Process -FilePath 'node' `
        -ArgumentList 'src/hardware-simulator.js' `
        -WorkingDirectory $Dir `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -NoNewWindow -PassThru

    Set-Content -Path $pidFile -Value $p.Id -NoNewline
    Start-Sleep -Milliseconds 800

    if (Get-LivePid $pidFile) {
        Write-Host "[sim] no ar (pid $($p.Id)) como $deviceId"
    } else {
        Write-Host '[sim] FALHOU ao subir. Ultimas linhas do log:' -ForegroundColor Red
        Get-Content $outLog, $errLog -Tail 15 -ErrorAction SilentlyContinue |
            ForEach-Object { Write-Host "  $_" }
        throw 'Simulador nao ficou no ar'
    }
}

# ── stop ─────────────────────────────────────────────────────────────────

function Stop-Process_ {
    param([string]$Name, [string]$PidFile)
    $id = Get-LivePid $PidFile
    if (-not $id) {
        Remove-Item $PidFile -ErrorAction SilentlyContinue
        Write-Host "[$Name] nao estava rodando"
        return
    }
    # Mata apenas o PID registrado, nunca a arvore de processos: a API
    # pode ter criado filhos e o usuario nao pediu isso.
    Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 300
    if (Get-Process -Id $id -ErrorAction SilentlyContinue) {
        Stop-Process -Id $id -Force -ErrorAction SilentlyContinue
    }
    Remove-Item $PidFile -ErrorAction SilentlyContinue
    Write-Host "[$Name] parado (pid $id)"
}

function Stop-All {
    Stop-Process_ -Name 'sim' -PidFile (Join-Path $RunDir 'sim.pid')
    Stop-Process_ -Name 'api' -PidFile (Join-Path $RunDir 'api.pid')
}

# ── status / logs ────────────────────────────────────────────────────────

function Show-Status {
    $apiId = Get-LivePid (Join-Path $RunDir 'api.pid')
    $simId = Get-LivePid (Join-Path $RunDir 'sim.pid')

    if ($apiId) { Write-Host "[api] rodando (pid $apiId) em $script:ApiUrl" }
    else        { Write-Host '[api] parado' }
    if ($simId) { Write-Host "[sim] rodando (pid $simId)" }
    else        { Write-Host '[sim] parado' }

    try {
        $h = Invoke-RestMethod -Uri "$script:ApiUrl/health" -TimeoutSec 3 -ErrorAction Stop
        Write-Host "[health] ok=$($h.ok) service=$($h.service)"
    } catch {
        Write-Host '[health] sem resposta'
    }
}

function Show-Logs {
    $files = @(
        (Join-Path $RunDir 'api.out.log'), (Join-Path $RunDir 'api.err.log'),
        (Join-Path $RunDir 'sim.out.log'), (Join-Path $RunDir 'sim.err.log')
    ) | Where-Object { Test-Path $_ }
    if (-not $files) { Write-Host 'Nenhum log ainda.'; return }
    Get-Content $files -Tail 40 -Wait
}

# ── main ─────────────────────────────────────────────────────────────────

New-RunDir
Set-Location $Dir

if (-not $env:PORT) { $env:PORT = Get-EnvValue 'PORT' '3000' }
$script:ApiUrl = "http://localhost:$($env:PORT)"

$Action = if ($args.Count -gt 0) { $args[0] } else { 'start' }

switch ($Action.ToLower()) {
    'start'   { Start-Api; Start-Simulator }
    'stop'    { Stop-All }
    'restart' { Stop-All; Write-Host ''; Start-Api; Start-Simulator }
    'status'  { Show-Status }
    'logs'    { Show-Logs }
    default {
        Write-Host 'Uso: api.ps1 {start|stop|restart|status|logs}' -ForegroundColor Red
        exit 1
    }
}
