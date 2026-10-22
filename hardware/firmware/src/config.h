// ============================================================================
//  SECURITAS - Configuracao de hardware do firmware
//  Pinos do ESP32 DevKit V1. Espelho de hardware/LIGACOES_DE_PORTA.txt
//
//  Se mudar a fiacao, mude APENAS aqui — o resto do firmware usa estes nomes.
// ============================================================================
#pragma once

// ── GPS u-blox NEO-M8N (UART2) ────────────────────────────────────────────
// TX do GPS -> RX do ESP32 ; RX do GPS -> TX do ESP32
#define GPS_RX_PIN          16      // UART2 RX
#define GPS_TX_PIN          17      // UART2 TX
#define GPS_BAUD            9600    // padrao de fabrica do NEO-M8N

// ── Modem 4G LTE SIM7600G-H (UART1) ───────────────────────────────────────
// TXD do modem -> RX do ESP32 ; RXD do modem -> TX do ESP32
#define LTE_RX_PIN          25      // UART1 RX
#define LTE_TX_PIN          26      // UART1 TX
#define LTE_PWRKEY_PIN      27      // pulso de ~1s liga/desliga o modem
#define LTE_BAUD            115200  // baud padrao do SIM7600

// ── Rele de corte da ignicao ──────────────────────────────────────────────
// GPIO23 -> R1k -> PC817 -> R10k -> 2N2222 -> bobina do rele (+1N4007)
#define RELAY_PIN           23
// Nivel do GPIO que ENERGIZA o rele (= estado BLOQUEADO, contato NA aberto).
// Se o seu modulo de rele for ativo em LOW, mude para LOW.
#define IGNITION_ACTIVE_HIGH  true

// ── Receptor RF 433 MHz (RXB6 / SYN480R) ──────────────────────────────────
// Pino INPUT-ONLY (sem pull-up interno). Usado como gatilho local.
#define RF_DATA_PIN         34

// ── Leitura da tensao da bateria (divisor 100k / 22k) ─────────────────────
// Vbat --[100k]--+--[22k]-- GND ; ponto medio -> GPIO36
#define BATTERY_ADC_PIN     36
#define BATTERY_DIVIDER_R1  100000.0f   // 100k em serie
#define BATTERY_DIVIDER_R2  22000.0f    // 22k para GND
#define BATTERY_FULL_MV     12600       // 3 celulas Li-ion cheias (12.6V)
#define BATTERY_EMPTY_MV    9900        // 3 celulas Li-ion em corte (3.3V/cel)

// ── LED de status (onboard, azul) ─────────────────────────────────────────
#define STATUS_LED_PIN      2

// ── Intervalos de operacao ────────────────────────────────────────────────
#define TELEMETRY_INTERVAL_MS   10000UL  // envia telemetria a cada 10s
#define COMMANDS_INTERVAL_MS    15000UL  // checa comandos a cada 15s
#define HEARTBEAT_INTERVAL_MS   60000UL  // "estou vivo" a cada 60s
#define WIFI_RETRY_MS           5000UL   // tempo entre tentativas de Wi-Fi
#define LTE_RETRY_MS          120000UL   // tempo entre tentativas do 4G (2 min)

// Quantas redes Wi-Fi o firmware conhece (principal + reservas).
// Os nomes/senhas ficam em secrets.h. Se uma rede nao existir no secrets.h,
// o espaco correspondente fica vazio e e pulado.
#define WIFI_NETWORK_COUNT          3
