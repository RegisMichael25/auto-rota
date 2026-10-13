# auto-rota

POC de planejamento de rotas de entrega para lojistas (somente Brasil).

## Pré-requisitos

- Java 21 (o Maven vem pelo wrapper `backend/mvnw`, não precisa instalar)
- Docker + Docker Compose (no macOS, Colima com pelo menos 4 GB: `colima start --memory 4`)
- Node 20+ (frontend)

## Banco local

```bash
docker-compose up -d postgres
```

PostgreSQL 16 em `localhost:5440` (altere com `POSTGRES_PORT`), banco/usuário/senha `autorota` (apenas desenvolvimento).

## Backend

```bash
cd backend
./mvnw spring-boot:run          # perfil dev por padrão
curl localhost:8080/actuator/health
```

Testes (usam Testcontainers, exigem Docker ativo):

```bash
./mvnw test
```

Com Colima, o Testcontainers precisa saber onde está o socket:

```bash
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## Frontend

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
npm test           # Vitest
npm run build
```

A URL do backend vem de `VITE_API_URL` (ver `frontend/.env.example`); em branco, usa `http://localhost:8080`.

## OSRM local (opcional)

O mapa inteiro do Brasil exige muita memória no pré-processamento (bem mais que 8 GB).
Para desenvolvimento, use uma região da Geofabrik: `sul`, `sudeste`, `nordeste`, `norte` ou `centro-oeste`.
Sem OSRM local, aponte `app.external.osrm.base-url` para `https://router.project-osrm.org` (servidor público de demonstração).

```bash
export OSRM_REGION=sudeste-latest
mkdir -p osrm-data
curl -L -o osrm-data/$OSRM_REGION.osm.pbf \
  https://download.geofabrik.de/south-america/brazil/$OSRM_REGION.osm.pbf

IMG=ghcr.io/project-osrm/osrm-backend:v6.0.0
docker run --rm -t -v "$PWD/osrm-data:/data" $IMG osrm-extract -p /opt/car.lua /data/$OSRM_REGION.osm.pbf
docker run --rm -t -v "$PWD/osrm-data:/data" $IMG osrm-partition /data/$OSRM_REGION.osrm
docker run --rm -t -v "$PWD/osrm-data:/data" $IMG osrm-customize /data/$OSRM_REGION.osrm

docker-compose --profile osrm up -d osrm
curl "http://localhost:5000/route/v1/driving/-46.6333,-23.5505;-46.6560,-23.5614?overview=false"
```

## Variáveis de ambiente (perfil `prod`)

Ver `.env.example`. Nunca versione valores reais.
