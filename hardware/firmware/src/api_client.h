// ============================================================================
//  SECURITAS - Cliente HTTP da API do rastreador
//
//  Concentra TODAS as chamadas ao backend (protocolo da secao 7 de
//  backend/API_ESTRUTURA.txt):
//    GET  /v1/hardware/:id/status            (boot + heartbeat)
//    POST /v1/hardware/telemetry             (telemetria)
//    GET  /v1/hardware/:id/commands?status=pending
//    POST /v1/hardware/:id/commands/:id/ack
//
//  Autenticacao: headers x-device-id e x-api-key em todas as chamadas.
//
//  O transporte (Wi-Fi ou 4G) e definido por main.cpp via setNetworkClient().
//  A API pode estar em mais de um endereco (a maquina de bancada muda de IP
//  conforme a rede): por isso existem varias URLs candidatas em secrets.h e o
//  cliente usa a primeira que responder.
// ============================================================================
#pragma once

#include <Arduino.h>
#include <ArduinoJson.h>
#include <WiFiClient.h>
#include <WiFiClientSecure.h>

#include "secrets.h"
#include "config.h"

// Resultado de uma chamada HTTP.
struct ApiResponse {
  bool ok = false;        // true quando o codigo HTTP ficou em 200..299
  int status = 0;         // codigo HTTP (0 = nem chegou a conectar)
  String body;            // corpo bruto da resposta
};

// Estado do veiculo devolvido no boot.
struct DeviceStatus {
  bool registered = false;
  String plate;
  bool isBlocked = false;
  int pendingCommands = 0;
  int httpStatus = 0;      // codigo HTTP da consulta (0 = nem respondeu)
};

class ApiClient {
 public:
  // Informa o cliente de rede a ser usado nas chamadas HTTP.
  // nullptr = usa o Wi-Fi interno do ESP32 (padrao em bancada).
  static void setNetworkClient(Client* client);

  // Cliente HTTP basico com os headers de autenticacao do dispositivo.
  static ApiResponse request(const char* method, const String& path,
                             const String& jsonBody = "");

  // Valida as credenciais no boot. Retorna o status do rastreador.
  static DeviceStatus checkStatus();

  // Envia uma leitura de telemetria. Retorna true se a API aceitou (201).
  static bool sendTelemetry(double lat, double lng, float speedKmh,
                            float heading, float batteryVolts);

  // Busca comandos pendentes. Retorna a quantidade encontrada e preenche as
  // listas de id e tipo (block/unblock).
  static int fetchPendingCommands(String ids[], String commands[], int maxItems);

  // Confirma a execucao (ou falha) de um comando.
  static bool ackCommand(const String& commandId, bool executed,
                         const String& note);

 private:
  // Quantas URLs candidatas existem em secrets.h.
  static int baseUrlCount();
  // URL candidata de indice i (vazia se nao existir).
  static String baseUrlAt(int i);
  // URL que respondeu por ultimo (a "da casa").
  static String baseUrl();
  // Executa a chamada contra UM endereco especifico.
  static ApiResponse requestTo(const char* method, const String& url,
                               const String& path, const String& jsonBody);
};
