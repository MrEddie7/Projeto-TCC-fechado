// ============================================================================
//  SECURITAS - Leitura do GPS u-blox NEO-M8N (UART2)
//
//  Usa TinyGPSPlus para parsear as sentencas NMEA do modulo. O firmware so
//  envia telemetria quando ha "fix" valido (senao manda coordenadas 0,0, o
//  que a API rejeita).
//
//  Dependencia: mikalhart/TinyGPSPlus (declarada em platformio.ini).
// ============================================================================
#pragma once

#include <Arduino.h>
#include <TinyGPSPlus.h>

#include "config.h"

class GpsModule {
 public:
  void begin();
  // Alimenta o parser com o que chegou da serial. Chamar com frequencia.
  void update();

  bool hasFix() const;            // true quando temos posicao valida
  double latitude() const;
  double longitude() const;
  float speedKmh() const;         // velocidade em km/h
  float headingDeg() const;       // rumo em graus (0 = norte)

 private:
  // "mutable" porque os acessores do TinyGPSPlus (kmph(), deg()) nao sao
  // const, e os metodos abaixo de leitura precisam ser const.
  mutable TinyGPSPlus _gps;
};
