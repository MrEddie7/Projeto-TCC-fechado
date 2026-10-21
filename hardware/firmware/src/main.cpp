// ============================================================================
//  SECURITAS - Firmware do rastreador veicular (ESP32 DevKit V1)
// ----------------------------------------------------------------------------
//  Fluxo:
//    1. boot: liga o modem 4G, tenta Wi-Fi (preferencial) e valida credenciais
//       em  GET /v1/hardware/:id/status
//    2. a cada TELEMETRY_INTERVAL_MS: le GPS + bateria e faz
//       POST /v1/hardware/telemetry
//    3. a cada COMMANDS_INTERVAL_MS: faz polling de comandos
//       GET /v1/hardware/:id/commands?status=pending
//       e aciona o rele (block/unblock), confirmando com
//       POST /v1/hardware/:id/commands/:id/ack
//    4. a cada HEARTBEAT_INTERVAL_MS: GET /v1/hardware/:id/status
//       (mantem o dispositivo "online" no painel mesmo sem GPS conectado)
//
//  Fiacao e pinos: hardware/LIGACOES_DE_PORTA.txt
//  Protocolo da API: backend/API_ESTRUTURA.txt, secao 7
// ============================================================================
#include <Arduino.h>
#include <WiFi.h>

#include "config.h"
#include "secrets.h"

// TinyGSM: modem SIM7600G-H. O TinyGSM 0.12 nao gerencia o PWRKEY, por isso
// o pulso de ligamento e feito aqui (ver lteConnect).
#define TINY_GSM_MODEM_SIM7600
#include <TinyGsmClient.h>

#include "api_client.h"
#include "gps_module.h"

// ── Objetos globais ───────────────────────────────────────────────────────
GpsModule gps;

// Serial do modem 4G (UART1). Reaproveitada pelo TinyGSM.
HardwareSerial serialLte(1);
TinyGsm modem(serialLte);

// Cliente HTTP sobre o 4G. Registrado no ApiClient quando o modem assume
// a rede (ver ensureOnline).
TinyGsmClient gsmClient(modem);

// ── Estado da aplicacao ───────────────────────────────────────────────────
bool ignitionCut = false;            // true = veiculo bloqueado (rele aberto)
unsigned long lastTelemetry = 0;
unsigned long lastCommandPoll = 0;
unsigned long lastHeartbeat = 0;
unsigned long lastWifiTry = 0;
unsigned long lastLteTry = 0;
bool wifiTried = false;              // ja tentou Wi-Fi pelo menos uma vez
bool lteTried = false;               // ja tentou o 4G pelo menos uma vez
bool lteInitDone = false;             // modem.init() so na primeira vez
bool lteReady = false;                // 4G com GPRS aberto

// ── Controle do rele ──────────────────────────────────────────────────────
void applyRelay(bool blocked) {
  ignitionCut = blocked;
  // O relê fica ENERGIZADO quando bloqueado (contato NA abre a ignicao).
  bool level = IGNITION_ACTIVE_HIGH ? blocked : !blocked;
  digitalWrite(RELAY_PIN, level ? HIGH : LOW);
  Serial.printf("[rele] ignicao %s\n", blocked ? "CORTADA (bloqueado)"
                                                : "LIBERADA (normal)");
}

// ── Leitura da tensao da bateria (divisor 100k/22k em GPIO36) ─────────────
float readBatteryVolts() {
  // Media de 16 amostras para reduzir ruido do ADC.
  uint32_t acc = 0;
  for (int i = 0; i < 16; i++) {
    acc += analogReadMilliVolts(BATTERY_ADC_PIN);
    delay(2);
  }
  float mvAtPin = acc / 16.0f;
  // Reconstroi a tensao real do pack a partir do divisor.
  float packMv = mvAtPin * (BATTERY_DIVIDER_R1 + BATTERY_DIVIDER_R2) /
                 BATTERY_DIVIDER_R2;
  return packMv / 1000.0f;
}

// ── Conectividade ─────────────────────────────────────────────────────────
bool wifiConnect() {
  if (WiFi.status() == WL_CONNECTED) return true;

  // Tenta a rede principal e, se nao conseguir, a rede reserva. Isso permite
  // o rastreador funcionar em duas redes conhecidas (casa e escritorio, por
  // exemplo) sem trocar o firmware.
  for (int i = 0; i < WIFI_NETWORK_COUNT; i++) {
    const char* ssid = (i == 0) ? WIFI_SSID : WIFI_SSID_2;
    const char* pass = (i == 0) ? WIFI_PASS : WIFI_PASS_2;
    if (!ssid || strlen(ssid) == 0) continue;

    Serial.printf("[net] tentando Wi-Fi \"%s\"...\n", ssid);
    WiFi.mode(WIFI_STA);
    WiFi.begin(ssid, pass);

    unsigned long start = millis();
    while (WiFi.status() != WL_CONNECTED && millis() - start < 15000) {
      gps.update();
      delay(250);
    }
    if (WiFi.status() == WL_CONNECTED) {
      Serial.printf("[net] Wi-Fi conectado: %s\n",
                    WiFi.localIP().toString().c_str());
      return true;
    }
    Serial.printf("[net] falha na rede \"%s\"\n", ssid);
    WiFi.disconnect(true);
  }
  return false;
}

// ── Pulso no PWRKEY do modem (liga/desliga o SIM7600) ──────────────────────
// O TinyGSM nao cuida do PWRKEY: este pulso e a forma de ligar o modem.
// CUIDADO: pulsar de novo desliga um modem que ja estiver ligado.
void pulsePowerKey() {
  pinMode(LTE_PWRKEY_PIN, OUTPUT);
  digitalWrite(LTE_PWRKEY_PIN, HIGH);
  delay(200);
  digitalWrite(LTE_PWRKEY_PIN, LOW);
  delay(1000);
  digitalWrite(LTE_PWRKEY_PIN, HIGH);
  delay(3000);
}

bool lteConnect() {
  // O init() (e o PWRKEY) so pode rodar uma vez por boot: repetir o pulso
  // desligaria o modem ja ligado.
  if (!lteInitDone) {
    lteInitDone = true;
    Serial.println("[net] inicializando modem SIM7600 (4G)...");
    pulsePowerKey();
    if (!modem.init()) {
      // O modem pode ter respondido ao primeiro pulso (ligando) ou o pulso
      // pode ter desligado um modem que ja estava ligado. Second try:
      Serial.println("[net] modem nao respondeu - novo pulso no PWRKEY");
      delay(1000);
      pulsePowerKey();
      if (!modem.init()) {
        Serial.println("[net] modem nao respondeu ao AT (sem SIM ou sem sinal)");
        return false;
      }
    }
    Serial.printf("[net] modem: %s\n", modem.getModemName().c_str());
  }

  // Aguarda registrar na rede (ate 30s).
  if (!modem.waitForNetwork(30000L)) {
    Serial.println("[net] sem registro na rede celular");
    return false;
  }
  if (!modem.gprsConnect(GPRS_APN, GPRS_USER, GPRS_PASS)) {
    Serial.println("[net] falha ao abrir o GPRS/APN");
    return false;
  }
  Serial.println("[net] 4G conectado");
  lteReady = true;
  // As proximas chamadas HTTP passam pelo modem, e nao pelo Wi-Fi.
  ApiClient::setNetworkClient(&gsmClient);
  return true;
}

bool ensureOnline() {
  if (WiFi.status() == WL_CONNECTED) {
    // Se o 4G tinha assumido, devolve o transporte para o Wi-Fi.
    if (lteReady) {
      lteReady = false;
      ApiClient::setNetworkClient(nullptr);
    }
    return true;
  }
  if (lteReady) return true;

  // A primeira tentativa acontece de imediato (os temporizadores comecam em
  // zero; sem os flags abaixo o boot pularia a rede por causa do millis()).
  if (!wifiTried || millis() - lastWifiTry > WIFI_RETRY_MS) {
    wifiTried = true;
    lastWifiTry = millis();
    if (wifiConnect()) return true;
  }

  // Fallback: 4G. So tenta novamente apos LTE_RETRY_MS para nao repetir a
  // sequencia de AT a cada ciclo do loop.
  if (!lteTried || millis() - lastLteTry > LTE_RETRY_MS) {
    lteTried = true;
    lastLteTry = millis();
    return lteConnect();
  }
  return false;
}

// ── Boot ──────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("\n============================================");
  Serial.println(" SECURITAS - rastreador ESP32");
  Serial.println("============================================");

  pinMode(STATUS_LED_PIN, OUTPUT);
  pinMode(RELAY_PIN, OUTPUT);
  applyRelay(false);  // comeca com a ignicao liberada (seguranca)

  // ADC da bateria (0..3.3V, atenuacao 11dB).
  analogReadResolution(12);
  analogSetPinAttenuation(BATTERY_ADC_PIN, ADC_11db);

  // RF 433 MHz: entrada digital (input-only).
  pinMode(RF_DATA_PIN, INPUT);

  // UARTs dos modulos.
  Serial2.begin(GPS_BAUD, SERIAL_8N1, GPS_RX_PIN, GPS_TX_PIN);  // GPS
  serialLte.begin(LTE_BAUD, SERIAL_8N1, LTE_RX_PIN, LTE_TX_PIN); // 4G
  gps.begin();

  // Conectividade: Wi-Fi primeiro; se nao houver, 4G.
  ensureOnline();

  // Diagnostico de rede (ajuda a descobrir por que a API nao responde).
  if (WiFi.status() == WL_CONNECTED) {
    Serial.printf("[net] ip=%s mask=%s gw=%s dns=%s\n",
                  WiFi.localIP().toString().c_str(),
                  WiFi.subnetMask().toString().c_str(),
                  WiFi.gatewayIP().toString().c_str(),
                  WiFi.dnsIP().toString().c_str());
  }

  // Valida as credenciais no backend (protocolo da secao 7).
  if (WiFi.status() == WL_CONNECTED || lteReady) {
    DeviceStatus st = ApiClient::checkStatus();
    if (st.httpStatus == 0) {
      // Nao deu para nem falar com a API: e problema de rede/endereco.
      Serial.println("[boot] API nao respondeu - rede ou API_BASE_URL errados");
    } else if (!st.registered) {
      Serial.println("[boot] credenciais invalidas - verifique secrets.h");
    } else {
      Serial.printf("[boot] registrado. Veiculo: %s | pendentes: %d\n",
                    st.plate.c_str(), st.pendingCommands);
      applyRelay(st.isBlocked);  // sincroniza o rele com o backend
    }
  } else {
    Serial.println("[boot] sem rede - seguindo offline");
  }

  digitalWrite(STATUS_LED_PIN, HIGH);
}

// ── Loop ──────────────────────────────────────────────────────────────────
void loop() {
  gps.update();  // alimenta o parser continuamente

  unsigned long now = millis();

  // 1) Telemetria
  if (now - lastTelemetry >= TELEMETRY_INTERVAL_MS) {
    lastTelemetry = now;
    if (ensureOnline()) {
      if (gps.hasFix()) {
        float bat = readBatteryVolts();
        ApiClient::sendTelemetry(gps.latitude(), gps.longitude(),
                                 gps.speedKmh(), gps.headingDeg(), bat);
        Serial.printf("[tel] %.6f, %.6f | %.1f km/h | bat %.2fV\n",
                      gps.latitude(), gps.longitude(), gps.speedKmh(), bat);
      } else {
        Serial.println("[tel] aguardando fix do GPS (sem enviar)");
      }
    } else {
      Serial.println("[tel] offline - telemetria nao enviada");
    }
    // Pisca o LED a cada ciclo de telemetria.
    digitalWrite(STATUS_LED_PIN, !digitalRead(STATUS_LED_PIN));
  }

  // 2) Comandos (block/unblock)
  if (now - lastCommandPoll >= COMMANDS_INTERVAL_MS) {
    lastCommandPoll = now;
    if (ensureOnline()) {
      String ids[4];
      String cmds[4];
      int n = ApiClient::fetchPendingCommands(ids, cmds, 4);
      for (int i = 0; i < n; i++) {
        Serial.printf("[cmd] recebido: %s (id=%s)\n", cmds[i].c_str(),
                      ids[i].c_str());
        bool exec = false;
        String note;
        if (cmds[i] == "block") {
          applyRelay(true);
          exec = true;
          note = "rele acionado pelo ESP32";
        } else if (cmds[i] == "unblock") {
          applyRelay(false);
          exec = true;
          note = "rele liberado pelo ESP32";
        } else {
          note = "comando desconhecido";
        }
        ApiClient::ackCommand(ids[i], exec, note);
        Serial.printf("[cmd] ACK %s\n", exec ? "ok" : "falha");
      }
    }
  }

  // 3) Heartbeat: mantem o rastreador "online" no painel mesmo sem GPS.
  if (now - lastHeartbeat >= HEARTBEAT_INTERVAL_MS) {
    lastHeartbeat = now;
    if (ensureOnline()) {
      DeviceStatus st = ApiClient::checkStatus();
      if (st.registered) {
        Serial.printf("[hb] online | veiculo: %s | pendentes: %d\n",
                      st.plate.c_str(), st.pendingCommands);
      }
    }
  }

  delay(50);
}
