#!/usr/bin/env bash
# Deploy do site de download para a Vercel (projeto securitas-download).
#
# O APK (25 MB) NAO e versionado (ignorado por *.apk). A fonte de verdade
# continua em backend/public/download/. Este script monta um diretorio temporario
# FORA do repo, copia os arquivos versionados de download-site/ + o APK da fonte,
# e faz deploy de la.
#
# Por que fora do repo? A raiz do repo tem um vercel.json em modo monorepo
# ("services.backend") que faz o Vercel CLI resolver o projeto errado (ja mandou
# este site para o projeto da API). Um dir temporario evita que o CLI enxergue
# aquela config, exatamente como no deploy original em /tmp.
#
# Uso:
#   VERCEL_TOKEN=<token> ./deploy.sh
#
# Requisitos: node/npm (Vercel CLI) e o projeto "securitas-download" existente
# na Vercel (id prj_cGJhnxPfFWv4c2PCaB9duZwAwdxw). Se for apagado, recrie-o com
# o mesmo nome (framework: nenhum) antes de rodar.
set -euo pipefail
cd "$(dirname "$0")"   # download-site/

APK_SRC="../backend/public/download/securitas-app-debug.apk"
if [ ! -f "$APK_SRC" ]; then
  echo "ERRO: APK fonte nao encontrado em $APK_SRC" >&2
  exit 1
fi

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

# Arquivos versionados do site
cp index.html qr.png vercel.json "$STAGE/"
# APK da fonte (nao versionado aqui)
cp "$APK_SRC" "$STAGE/securitas-app-debug.apk"
# Link deterministico ao projeto securitas-download
mkdir -p "$STAGE/.vercel"
printf '%s' '{"projectId":"prj_cGJhnxPfFWv4c2PCaB9duZwAwdxw","orgId":"team_Cy4P2bM5EMXNns67lScCse4U"}' > "$STAGE/.vercel/project.json"

echo "Staging em $STAGE ($(stat -c%s "$STAGE/securitas-app-debug.apk") bytes de APK)"
cd "$STAGE"
npx --yes vercel@latest deploy --prod --yes
