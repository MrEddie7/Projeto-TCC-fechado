# Securitas — Rastreamento e Segurança Veicular

Sistema de rastreamento veicular composto por um aplicativo Android, uma API
REST em Node.js, um painel web administrativo e um rastreador GPS opcional com
ESP32. O usuário cadastra veículos, acompanha a telemetria em tempo real,
consulta rotas e envia comandos remotos de bloqueio/desbloqueio.

```
Aplicativo Android  ⇄  API Node.js (Express)  ⇄  Rastreador GPS (ESP32)
                              ⇅
                     Firebase (Auth + Firestore + Realtime Database)
```

## Componentes

| Diretório | Descrição |
|---|---|
| [`app/`](app/) | App Android em Kotlin, Jetpack Compose e Material 3 (mínimo Android 8 / API 26). |
| [`backend/`](backend/) | API REST, painel web do administrador e simulador de rastreador. |
| [`hardware/`](hardware/) | Firmware PlatformIO/Arduino do rastreador ESP32 e esquemático de ligações. |
| [`site/`](site/) | Aplicação Laravel usada como site do projeto (fora do deploy da Vercel). |
| [`download-site/`](download-site/) | Página estática que serve o APK para instalação direta. |
| [`docs/`](docs/) | Documentação/publicação do projeto. |

Documentação detalhada: [`DESCRICAO_DO_PROJETO.txt`](DESCRICAO_DO_PROJETO.txt),
[`backend/API_ESTRUTURA.txt`](backend/API_ESTRUTURA.txt),
[`backend/VERCEL_DEPLOY.md`](backend/VERCEL_DEPLOY.md) e
[`download-site/README.md`](download-site/README.md).

## Ambientes publicados

- API e painel: <https://projeto-tcc-fechado-public.vercel.app>
- Download do app: <https://securitas-download.vercel.app>

## Funcionalidades

- Login, cadastro e autenticação pelo Firebase Authentication.
- Dashboard da frota, cadastro/edição/exclusão de veículos.
- Detalhe do veículo com rastreador vinculado, estado de conexão e última telemetria.
- Mapa com a posição ao vivo e histórico de rotas; comunicação em tempo real via SSE.
- Comandos remotos de bloqueio/desbloqueio com fila e confirmação (ACK) pelo rastreador.
- Localização do próprio celular, com envio manual ou automático à API.
- Painel administrativo: visão geral, logs, diagnóstico, posicionamento manual no mapa,
  simulação de veículos e sincronização do Firebase.
- Firmware ESP32 com Wi-Fi/4G, GPS, relé de corte da ignição, leitura de bateria e RF 433 MHz.

## Executando localmente

### API

Requer Node.js >= 22.5.

```bash
cd backend
npm install
cp .env.example .env          # ajuste as variáveis
npm start                     # http://localhost:3000
```

A API precisa das credenciais do Firebase. Para execução local, coloque a conta de
serviço em `backend/firebase-service-account.json` e configure `FIREBASE_DATABASE_URL`
e `FIRESTORE_ID` no `.env`. Para o painel web, defina também `FIREBASE_API_KEY`.

Comandos úteis em `backend/`:

```bash
npm run dev        # API com recarregamento automático
npm run simulator  # simula um rastreador (VTSIM_DEVICE_ID e VTSIM_API_KEY obrigatórios)
npm run api        # sobe a API e gerencia simuladores por device server-sim
npm run prune      # limpa telemetria antiga
```

### App Android

Requer Android SDK e JDK 17. O arquivo `local.properties` (com `sdk.dir`) não é versionado.

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

O APK de referência para download fica em
`backend/public/download/securitas-app-debug.apk` e é publicado pelo
`download-site/deploy.sh`.

### Firmware

O firmware e as instruções de gravação estão em [`hardware/README.txt`](hardware/README.txt);
a pinagem em [`hardware/LIGACOES_DE_PORTA.txt`](hardware/LIGACOES_DE_PORTA.txt).
As credenciais do rastreador são geradas em `POST /v1/vehicles/:id/device` e gravadas
em `hardware/firmware/src/secrets.h` (não versionado; use `secrets.example.h`).

## Deploy

### API (Vercel)

O `vercel.json` da raiz define o serviço `backend` (Express) respondendo na raiz do domínio.
As variáveis de ambiente obrigatórias estão listadas em
[`backend/VERCEL_DEPLOY.md`](backend/VERCEL_DEPLOY.md).

```bash
npx vercel --prod
```

### Site de download (Vercel)

O site é estático e publicado em um projeto Vercel separado (`securitas-download`).
O script copia o APK de `backend/public/download/` para um diretório temporário e
publica a página:

```bash
./download-site/deploy.sh
```

## Rotas principais da API

- Públicas: `GET /health`, `GET /api`, `GET /v1/auth/config`.
- App (Bearer ID Token do Firebase): `/v1/auth/me`, `/v1/vehicles`,
  `/v1/vehicles/:id/telemetry/*`, `/v1/vehicles/:id/commands`, `/v1/vehicles/:id/routes`,
  `/v1/routes`, `/v1/mobile/location`, `/v1/mobile/device`.
- Rastreador (`x-device-id` + `x-api-key`): `POST /v1/hardware/telemetry`,
  `GET /v1/hardware/:deviceId/status`, `GET /v1/hardware/:deviceId/commands`,
  `POST /v1/hardware/:deviceId/commands/:commandId/ack`.
- Administração (painel web): `/v1/admin/*`.

A referência completa está em [`backend/API_ESTRUTURA.txt`](backend/API_ESTRUTURA.txt).

## Segurança

Nunca versionar credenciais: `backend/firebase-service-account.json`, `.env`,
`hardware/firmware/src/secrets.h`, `.vercel/` e `*.apk` são ignorados pelo Git.
Chaves de dispositivo já divulgadas em logs ou comandos devem ser regeneradas em
`POST /v1/vehicles/:id/device`.
