// ============================================================================
//  SECURITAS - Implementacao do cliente HTTP da API
//
//  O protocolo HTTP/1.1 foi escrito a mao (em vez de usar a classe
//  HTTPClient do core do Arduino) porque o HTTPClient do ESP32 aceita
//  apenas WiFiClient& - e no 4G o transporte e o TinyGsmClient do TinyGSM.
//  Com esse cliente simples o MESMO codigo serve para Wi-Fi e para 4G:
//  muda apenas o ponteiro de transporte entregue em setNetworkClient().
// ============================================================================
#include "api_client.h"

// Cliente de rede ativo (Wi-Fi ou 4G), definido pelo main.cpp. Quando nulo,
// o ApiClient cai para o Wi-Fi interno (bancada).
static Client* g_netClient = nullptr;

// Indice da URL que respondeu por ultimo. A maquina de bancada muda de IP
// conforme a rede Wi-Fi, por isso o cliente tenta todas as candidatas.
static int g_activeBase = 0;

namespace {

const uint32_t HTTP_TIMEOUT_MS = 15000;   // limite de cada etapa da leitura
const uint32_t CONNECT_TIMEOUT_MS = 12000; // limite da conexao TCP

// Separa "http://host:porta" em host, porta e esquema.
bool splitUrl(const String& url, String& host, uint16_t& port, bool& https) {
  https = false;
  port = 80;
  String rest = url;
  if (rest.startsWith("https://")) {
    https = true;
    port = 443;
    rest = rest.substring(8);
  } else if (rest.startsWith("http://")) {
    rest = rest.substring(7);
  }
  int slash = rest.indexOf('/');
  if (slash >= 0) rest = rest.substring(0, slash);
  int colon = rest.lastIndexOf(':');
  if (colon >= 0) {
    port = (uint16_t)rest.substring(colon + 1).toInt();
    rest = rest.substring(0, colon);
  }
  host = rest;
  return host.length() > 0;
}

// Le uma linha (ate o LF) da resposta.
bool readLine(Client* c, String& line, uint32_t timeoutMs) {
  uint32_t start = millis();
  line = "";
  while (millis() - start < timeoutMs) {
    int n = c->available();
    while (n-- > 0) {
      char ch = (char)c->read();
      if (ch == '\n') return true;
      if (ch != '\r') line += ch;
    }
    delay(2);
  }
  return false;
}

// Le exatamente N bytes do corpo.
String readFixed(Client* c, unsigned len, uint32_t timeoutMs) {
  String body = "";
  uint32_t start = millis();
  while (body.length() < len && millis() - start < timeoutMs) {
    int n = c->available();
    while (n-- > 0 && body.length() < len) body += (char)c->read();
    delay(2);
  }
  return body;
}

// Le o corpo ate o servidor fechar a conexao (usado quando a resposta nao
// traz Content-Length).
String readUntilClose(Client* c, uint32_t timeoutMs) {
  String body = "";
  uint32_t start = millis();
  while (millis() - start < timeoutMs) {
    int n = c->available();
    while (n-- > 0) body += (char)c->read();
    if (!c->connected() && c->available() == 0) break;
    delay(2);
  }
  return body;
}

// Decodifica um corpo "chunked" (Transfer-Encoding: chunked).
String readChunked(Client* c, uint32_t timeoutMs) {
  String body = "";
  uint32_t start = millis();
  while (millis() - start < timeoutMs) {
    String line;
    if (!readLine(c, line, timeoutMs)) break;
    unsigned size = (unsigned)strtoul(line.c_str(), NULL, 16);
    if (size == 0) break;                       // fim dos chunks
    body += readFixed(c, size, timeoutMs);
    String crlf;
    readLine(c, crlf, 1000);                    // CRLF final do chunk
  }
  return body;
}

// Procura um header (sem diferenciar maiusculas/minusculas).
String headerValue(const String& headers, const char* name) {
  String target = String(name);
  target.toLowerCase();
  int pos = 0;
  while (pos < headers.length()) {
    int eol = headers.indexOf('\n', pos);
    if (eol < 0) eol = headers.length();
    String line = headers.substring(pos, eol);
    line.trim();
    int colon = line.indexOf(':');
    if (colon > 0) {
      String key = line.substring(0, colon);
      key.trim();
      key.toLowerCase();
      if (key == target) {
        String value = line.substring(colon + 1);
        value.trim();
        return value;
      }
    }
    pos = eol + 1;
  }
  return "";
}

// Executa UMA requisicao HTTP/1.1 sobre o transporte informado.
ApiResponse httpExchange(Client* transport, const char* method,
                          const String& host, uint16_t port, const String& path,
                          const String& jsonBody) {
  ApiResponse out;

  // A rede da faculdade e lenta (latencia alta e pacotes perdidos), entao o
  // timeout de conexao e generoso e ha uma segunda tentativa.
  transport->setTimeout(CONNECT_TIMEOUT_MS);
  bool connected = false;
  for (int attempt = 0; attempt < 2 && !connected; attempt++) {
    connected = transport->connect(host.c_str(), port);
    if (!connected) {
      Serial.printf("[api] nao conectou em %s:%u (tentativa %d)\n", host.c_str(),
                    port, attempt + 1);
      delay(1000);
    }
  }
  if (!connected) return out;

  // ── Monta a requisicao ───────────────────────────────────────────────────
  String req;
  req.reserve(256 + jsonBody.length());
  req += method;
  req += ' ';
  req += path;
  req += " HTTP/1.1\r\n";
  req += "Host: " + host + ':';
  req += String(port);
  req += "\r\n";
  // "close" faz o servidor encerrar a conexao depois da resposta: simplifica
  // a leitura do corpo.
  req += "Connection: close\r\n";
  req += "Accept: application/json\r\n";
  req += "Content-Type: application/json\r\n";
  req += "x-device-id: ";
  req += DEVICE_ID;
  req += "\r\n";
  req += "x-api-key: ";
  req += API_KEY;
  req += "\r\n";
  req += "Content-Length: ";
  req += String(jsonBody.length());
  req += "\r\n\r\n";
  req += jsonBody;

  transport->print(req);

  // ── Le a resposta ────────────────────────────────────────────────────────
  String statusLine, headers;
  if (!readLine(transport, statusLine, HTTP_TIMEOUT_MS)) {
    Serial.println("[api] resposta vazia (timeout)");
    transport->stop();
    return out;
  }
  // "HTTP/1.1 200 OK"
  int sp1 = statusLine.indexOf(' ');
  int sp2 = (sp1 >= 0) ? statusLine.indexOf(' ', sp1 + 1) : -1;
  out.status = (sp2 > sp1) ? statusLine.substring(sp1 + 1, sp2).toInt() : 0;

  uint32_t start = millis();
  while (millis() - start < HTTP_TIMEOUT_MS) {
    String line;
    if (!readLine(transport, line, HTTP_TIMEOUT_MS)) break;
    if (line.length() == 0) break;   // linha em branco: fim dos headers
    headers += line;
    headers += '\n';
  }

  // ── Corpo ────────────────────────────────────────────────────────────────
  if (out.status != 204 && out.status != 304) {
    if (headerValue(headers, "Transfer-Encoding").indexOf("chunked") >= 0) {
      out.body = readChunked(transport, HTTP_TIMEOUT_MS);
    } else {
      String lenStr = headerValue(headers, "Content-Length");
      if (lenStr.length() > 0) {
        out.body = readFixed(transport, (unsigned)lenStr.toInt(),
                             HTTP_TIMEOUT_MS);
      } else {
        out.body = readUntilClose(transport, 5000);
      }
    }
  }

  out.ok = out.status >= 200 && out.status < 300;
  transport->stop();
  return out;
}

}  // namespace

void ApiClient::setNetworkClient(Client* client) { g_netClient = client; }

int ApiClient::baseUrlCount() {
  int n = 1;
#ifdef API_BASE_URL_2
  if (String(API_BASE_URL_2).length() > 0) n = 2;
#endif
#ifdef API_BASE_URL_3
  if (String(API_BASE_URL_3).length() > 0) n = 3;
#endif
  return n;
}

String ApiClient::baseUrlAt(int i) {
  if (i == 0) return String(API_BASE_URL);
#ifdef API_BASE_URL_2
  if (i == 1) return String(API_BASE_URL_2);
#endif
#ifdef API_BASE_URL_3
  if (i == 2) return String(API_BASE_URL_3);
#endif
  return String();
}

String ApiClient::baseUrl() { return baseUrlAt(g_activeBase); }

ApiResponse ApiClient::requestTo(const char* method, const String& url,
                                 const String& path,
                                 const String& jsonBody) {
  String host;
  uint16_t port = 80;
  bool https = false;
  if (!splitUrl(url, host, port, https)) {
    Serial.printf("[api] URL invalida: %s\n", url.c_str());
    return ApiResponse();
  }

  // Escolhe o transporte:
  //  1. cliente de rede explicito (Wi-Fi ou TinyGsmClient) definido pelo main;
  //  2. senao, WiFiClient/WiFiClientSecure conforme o esquema da URL.
  WiFiClient wifiClient;
  WiFiClientSecure secureClient;
  Client* transport = g_netClient;
  if (!transport) {
    if (https) {
      secureClient.setInsecure();  // sem validacao de cadeia (ESP32 nao tem NTP)
      transport = &secureClient;
    } else {
      transport = &wifiClient;
    }
  } else if (https) {
    // O cliente 4G (TinyGsmClient) nao faz TLS. Em HTTPS o transporte
    // explicito e ignorado e vale o Wi-Fi com certificado inseguro.
    Serial.println("[api] HTTPS com cliente 4G: usando Wi-Fi");
    secureClient.setInsecure();
    transport = &secureClient;
  }

  return httpExchange(transport, method, host, port, path, jsonBody);
}

ApiResponse ApiClient::request(const char* method, const String& path,
                               const String& jsonBody) {
  // Tenta a URL que ja respondeu por ultimo primeiro; depois as outras.
  int total = baseUrlCount();
  ApiResponse out;
  for (int i = 0; i < total; i++) {
    int idx = (g_activeBase + i) % total;
    String url = baseUrlAt(idx);
    if (url.length() == 0) continue;

    out = requestTo(method, url, path, jsonBody);
    if (out.status > 0) {
      if (idx != g_activeBase) {
        Serial.printf("[api] servidor ativo: %s\n", url.c_str());
        g_activeBase = idx;
      }
      return out;
    }
  }
  return out;
}

DeviceStatus ApiClient::checkStatus() {
  DeviceStatus st;
  ApiResponse r = request("GET", "/v1/hardware/" + String(DEVICE_ID) + "/status");
  st.httpStatus = r.status;
  if (!r.ok) {
    if (r.status == 0) {
      Serial.println("[api] API nao respondeu (HTTP 0) - rede ou API_BASE_URL");
    } else {
      Serial.printf("[api] boot status falhou (HTTP %d): %s\n", r.status,
                    r.body.c_str());
    }
    return st;
  }

  JsonDocument doc;
  if (deserializeJson(doc, r.body)) return st;

  st.registered = doc["registered"] | false;
  st.pendingCommands = doc["pendingCommands"] | 0;
  if (doc["vehicle"].is<JsonObject>()) {
    st.plate = String((const char*)(doc["vehicle"]["plate"] | ""));
    st.isBlocked = doc["vehicle"]["isBlocked"] | false;
  }
  return st;
}

bool ApiClient::sendTelemetry(double lat, double lng, float speedKmh,
                              float heading, float batteryVolts) {
  JsonDocument doc;
  doc["latitude"] = lat;
  doc["longitude"] = lng;
  doc["speed"] = speedKmh;
  doc["heading"] = heading;
  // A API espera "battery"; enviamos a tensao em volts do pack 3S.
  doc["battery"] = batteryVolts;
  doc["timestamp"] = (uint32_t)(millis() / 1000);  // ajustado no ingest

  String body;
  serializeJson(doc, body);
  ApiResponse r = request("POST", "/v1/hardware/telemetry", body);
  if (!r.ok) {
    Serial.printf("[api] telemetria falhou (HTTP %d): %s\n", r.status,
                  r.body.c_str());
  }
  return r.ok;
}

int ApiClient::fetchPendingCommands(String ids[], String commands[],
                                    int maxItems) {
  ApiResponse r = request(
      "GET", "/v1/hardware/" + String(DEVICE_ID) + "/commands?status=pending");
  if (!r.ok) return 0;

  JsonDocument doc;
  if (deserializeJson(doc, r.body)) return 0;

  int n = 0;
  for (JsonObject cmd : doc["commands"].as<JsonArray>()) {
    if (n >= maxItems) break;
    ids[n] = String((long)(cmd["id"] | 0));
    commands[n] = String((const char*)(cmd["command"] | ""));
    n++;
  }
  return n;
}

bool ApiClient::ackCommand(const String& commandId, bool executed,
                           const String& note) {
  JsonDocument doc;
  doc["ok"] = executed;
  doc["note"] = note;
  String body;
  serializeJson(doc, body);

  String path = "/v1/hardware/" + String(DEVICE_ID) + "/commands/" + commandId +
                "/ack";
  ApiResponse r = request("POST", path, body);
  if (!r.ok) {
    Serial.printf("[api] ACK falhou (HTTP %d): %s\n", r.status, r.body.c_str());
  }
  return r.ok;
}
