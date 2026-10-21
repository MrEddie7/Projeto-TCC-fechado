// ============================================================================
//  SECURITAS - Implementacao da leitura do GPS
// ============================================================================
#include "gps_module.h"

void GpsModule::begin() {
  // Serial2 ja configurada no setup(); aqui apenas garantimos o parser.
  Serial.println("[gps] NEO-M8N em UART2 (9600 baud)");
}

void GpsModule::update() {
  // Consome TUDO que estiver no buffer da UART do GPS.
  while (Serial2.available() > 0) {
    _gps.encode(Serial2.read());
  }
}

bool GpsModule::hasFix() const {
  // Exige posicao valida e idade recente (<2s), evitando coordenadas antigas.
  return _gps.location.isValid() && _gps.location.age() < 2000;
}

double GpsModule::latitude() const { return _gps.location.lat(); }
double GpsModule::longitude() const { return _gps.location.lng(); }

float GpsModule::speedKmh() const {
  // TinyGPSPlus devolve m/s; convertemos para km/h. Sem fix -> 0.
  return _gps.speed.isValid() ? _gps.speed.kmph() : 0.0f;
}

float GpsModule::headingDeg() const {
  return _gps.course.isValid() ? _gps.course.deg() : 0.0f;
}
