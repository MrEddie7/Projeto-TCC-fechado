// ============================================================================
//  SECURITAS - Credenciais do dispositivo (MODELO)
//
//  Copie este arquivo para "secrets.h" e preencha com os SEUS dados:
//      cp secrets.example.h secrets.h
//
//  secrets.h NAO deve ser versionado (esta no .gitignore).
// ============================================================================
#pragma once

// ── Wi-Fi: rede principal ─────────────────────────────────────────────────
#define WIFI_SSID   "NOME_DA_SUA_REDE"
#define WIFI_PASS   "SENHA_DO_WIFI"

// ── Wi-Fi: rede reserva (opcional) ────────────────────────────────────────
// Se a rede principal nao responder, o firmware tenta esta. Deixe "" para
// desativar a rede reserva.
#define WIFI_SSID_2 "OUTRA_REDE_CONHECIDA"
#define WIFI_PASS_2 "SENHA_DA_OUTRA_REDE"

// ── Rede celular (fallback via SIM7600) ───────────────────────────────────
// Preencha conforme a operadora (ex.: Vivo, Claro, TIM).
#define GPRS_APN    "zap.vivo.com.br"
#define GPRS_USER   ""
#define GPRS_PASS   ""

// ── Endereco da API ───────────────────────────────────────────────────────
// Na bancada, com o ESP32 na mesma rede do servidor, use o IP local da
// maquina que roda o backend (ex.: "http://192.168.0.10:3000").
// Se quiser expor o backend na internet, use o IP/dominio publico.
//
// Pode-se informar ate TRES enderecos: o firmware usa o primeiro que
// responder e continua nele enquanto ele funcionar.
#define API_BASE_URL   "http://192.168.0.10:3000"
// #define API_BASE_URL_2 "http://192.168.0.11:3000"
// #define API_BASE_URL_3 "https://api.securitas.app"

// ── Credenciais do rastreador ─────────────────────────────────────────────
// Obtidas com POST /v1/vehicles/:id/device (ver API_ESTRUTURA.txt, secao 7).
// Cole EXATAMENTE os valores devolvidos pela API.
#define DEVICE_ID   "vt_xxxxxxxxxxxxxxxx"
#define API_KEY     "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
