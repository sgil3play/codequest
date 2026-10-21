# syntax=docker/dockerfile:1
#
# Build del frontend de CodeQuest.
#
# Contexto de build: la raiz del repositorio (docker compose lo define).
#
#   docker build -f frontend/Dockerfile -t codequest-frontend .

# ---------------------------------------------------------------------------
# Etapa 1: compilacion
# ---------------------------------------------------------------------------
FROM node:24-alpine AS build
WORKDIR /build

# Se copia primero solo el manifiesto: la instalacion de dependencias se
# cachea mientras el package.json no cambie.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY frontend/ ./
RUN npm run build

# ---------------------------------------------------------------------------
# Etapa 2: servidor estatico
# ---------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

COPY frontend/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD wget -q -O- http://localhost/ >/dev/null 2>&1 || exit 1

CMD ["nginx", "-g", "daemon off;"]
