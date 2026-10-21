================================================================================
  SECURITAS - HARDWARE DO RASTREADOR VEICULAR (ESP32)
================================================================================

  Documentacao da pasta "hardware/":

  LIGACOES_DE_PORTA.txt   Pinagem completa (ESP32 DevKit V1 + GPS NEO-M8N +
                          SIM7600G-H 4G + RX 433MHz + rele + BMS 3S) com a
                          BOM, o diagrama de blocos, a fiacao modulo a modulo
                          e o checklist de montagem.

  firmware/               Codigo-fonte do firmware (PlatformIO / Arduino).

--------------------------------------------------------------------------------
  1. COMO GRAVAR O FIRMWARE
--------------------------------------------------------------------------------

  1. Instalar o PlatformIO Core:
       python3 -m venv ~/.pioenv
       ~/.pioenv/bin/pip install platformio

  2. Configurar as credenciais:
       cd hardware/firmware
       cp src/secrets.example.h src/secrets.h
       $EDITOR src/secrets.h

     Preencha:
       WIFI_SSID / WIFI_PASS    -> rede principal usada pelo ESP32
       WIFI_SSID_2 / WIFI_PASS_2-> rede reserva (usada se a principal falhar)
       API_BASE_URL             -> IP da maquina que roda o backend
       API_BASE_URL_2 / _3      -> outros enderecos do backend (opcionais)
       DEVICE_ID / API_KEY      -> gerados pela API (secao 3)

     O firmware conhece DUAS redes Wi-Fi e ate TRES enderecos da API. Ele
     tenta a rede principal; se nao conseguir, tenta a reserva. Para a API,
     usa o primeiro endereco que responder e continua nele enquanto ele
     funcionar. Isso evita reprogramar o rastreador quando o IP do servidor
     muda de rede.

     ATENCAO (rede da faculdade): o roteador ISOLA os clientes Wi-Fi entre
     si. O ESP32 (no Wi-Fi) NAO alcanca o IP Wi-Fi do servidor; ele chega no
     servidor pelo IP de rede cabeada (192.168.20.126) ou por outro
     roteamento. Se nenhum dos tres enderecos responder, o firmware fica
     offline e tenta de novo a cada ciclo.

  3. Compilar e gravar:
       cd hardware/firmware
       pio run -t upload

  4. Acompanhar os logs:
       pio device monitor -b 115200

--------------------------------------------------------------------------------
  2. ESTRUTURA DO FIRMWARE
--------------------------------------------------------------------------------

  firmware/platformio.ini        Projeto PlatformIO (placa esp32dev)
  firmware/src/config.h          PINAGEM e intervalos (espelho do TXT)
  firmware/src/secrets.h         Credenciais (NAO versionar)
  firmware/src/secrets.example.h Modelo das credenciais
  firmware/src/main.cpp          Loop principal: boot, rede, telemetria, comandos
  firmware/src/api_client.h/.cpp Cliente HTTP da API (protocolo da secao 7)
  firmware/src/gps_module.h/.cpp Leitura do NEO-M8N via TinyGPSPlus

--------------------------------------------------------------------------------
  3. COMO OBTER AS CREDENCIAIS (device_id / api_key)
--------------------------------------------------------------------------------

  A API gera as credenciais do rastreador e as guarda no Firestore:

    POST /v1/vehicles/:id/device
    (Authorization: Bearer <Firebase ID Token do dono do veiculo>)

    resp 201:
    { "device": { "deviceId": "vt_xxxxxxxxxxxxxxxx",
                  "apiKey":  "xxxx... (48 chars)" } }

  Cole os dois valores em firmware/src/secrets.h.

  ATENCAO: criar um device via API vincula um RASTREADOR REAL ao veiculo.
  Nao confundir com a simulacao (device com model='server-sim').

--------------------------------------------------------------------------------
  4. PROTOCOLO IMPLEMENTADO
--------------------------------------------------------------------------------

  GET  /v1/hardware/:deviceId/status            -> boot + heartbeat (60s)
  POST /v1/hardware/telemetry                   -> a cada 10s (com fix do GPS)
  GET  /v1/hardware/:deviceId/commands?status=pending
                                                 -> a cada 15s
  POST /v1/hardware/:deviceId/commands/:id/ack  -> confirma block/unblock

  Todas as chamadas enviam os headers:
    x-device-id: <DEVICE_ID>
    x-api-key  : <API_KEY>

  Especificacao completa: backend/API_ESTRUTURA.txt, secao 7.

--------------------------------------------------------------------------------
  5. COMPORTAMENTO SEM MODULOS CONECTADOS
--------------------------------------------------------------------------------

  O firmware foi escrito para funcionar em bancada mesmo com apenas o ESP32
  ligado (sem GPS, sem 4G, sem rele):

    - Sem GPS: nao envia telemetria (enviaria coordenadas invalidas), mas
      continua enviando o HEARTBEAT, que marca o rastreador como "online"
      no painel (Firestore devices.lastSeen).
    - Sem modem 4G: o firmware tenta ligar o modem (pulso no PWRKEY, GPIO27),
      mas sem modem/SIM a unica consequencia e o fallback celular nao existir.
    - Sem rele: o GPIO23 fica em nivel de corte, sem efeito; os comandos
      block/unblock continuam sendo confirmados pelo ACK (com a nota de que
      o rele nao foi acionado fisicamente).

  No log do monitor serial, um ESP32 "nu" (so Wi-Fi) mostra:
    [net] tentando Wi-Fi "SALA-MAKER"...
    [net] falha na rede "SALA-MAKER"
    [net] tentando Wi-Fi "F211"...
    [net] Wi-Fi conectado: 192.168.99.193
    [net] ip=192.168.99.193 mask=255.255.0.0 gw=192.168.15.1 dns=192.168.1.42
    [boot] registrado. Veiculo: ESP3201 | pendentes: 0
    [rele] ignicao LIBERADA (normal)
    [tel] aguardando fix do GPS (sem enviar)
    [hb] online | veiculo: ESP3201 | pendentes: 0

  O "[hb]" e o heartbeat: ele atualiza o campo devices.lastSeen no Firestore
  e e o que mantem o rastreador marcado como ativo no painel admin, mesmo
  sem GPS ligado.

--------------------------------------------------------------------------------
  6. DIAGNOSTICO
--------------------------------------------------------------------------------

  Sintoma                          Provavel causa
  ------------------------------  ------------------------------------------
  "falha na rede X"                SSID/senha errados em secrets.h
  "sem rede - seguindo offline"    nenhuma das redes respondeu (sinal? senha?)
  "API nao respondeu - rede ou API_BASE_URL errados"
                                    nao alcanca nenhum dos tres enderecos
                                    (roteador sem rota ate a API / isolamento
                                    de clientes Wi-Fi)
  "[api] boot status falhou (HTTP 4xx)"
                                    DEVICE_ID/API_KEY divergentes dos da API
  "[api] boot status falhou (HTTP 0)"
                                    a API esta fora do ar (ou o endereco
                                    certo nao esta no secrets.h)
  "aguardando fix do GPS"          modulo NEO-M8N sem antena ou sem skyview
  nao aparece no painel            o device foi criado para OUTRO veiculo

  Dica: se o ESP32 cair numa rede que nao tem rota ate a API, troque a
  ordem em secrets.h (API_BASE_URL) ou troque a rede principal.

================================================================================
