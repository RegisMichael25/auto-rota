# auto-rota

POC de planejamento de rotas de entrega para lojistas, somente no Brasil. O lojista informa a origem (a loja) e até 10 destinos (CEP + número); o sistema calcula a ordem de visita pela menor distância de estrada, desenha a rota no mapa, estima distância, tempo, litros e custo de combustível, guarda o histórico e permite marcar cada entrega como feita ou não feita.

Sem login, sem cadastro e sem custo: só serviços gratuitos (ViaCEP, BrasilAPI, Nominatim, OSRM e tiles do OpenStreetMap).

| Pasta | Conteúdo |
|---|---|
| `backend/` | Java 21, Spring Boot 3.5, Flyway, PostgreSQL |
| `frontend/` | React + Vite + TypeScript, react-leaflet, React Query |
| `docker-compose.yml` | PostgreSQL local e OSRM local (opcional) |

## Ferramentas e serviços utilizados

Tudo é gratuito; nenhum serviço exige cadastro, chave de API ou cartão.

### Serviços externos (APIs públicas)

| Serviço | Para quê | Endereço |
|---|---|---|
| **ViaCEP** | Endereço do CEP (logradouro, bairro, cidade, UF) | `https://viacep.com.br/ws/{cep}/json/` |
| **BrasilAPI** (CEP v2) | Coordenada do CEP, usada como último recurso (costuma ser o centro da cidade) | `https://brasilapi.com.br/api/cep/v2/{cep}` |
| **Nominatim** (OpenStreetMap) | Geocodificação de logradouro + número + cidade; limite de 1 req/s e `User-Agent` identificado | `https://nominatim.openstreetmap.org` |
| **OSRM** | Matriz de distâncias e tempos (`/table`) e geometria da rota (`/route`) | Local (Docker) ou servidor público `https://router.project-osrm.org` |
| **Tiles do OpenStreetMap** | Imagens do mapa no frontend | `https://tile.openstreetmap.org` |
| **Geofabrik** | Download dos mapas do Brasil por região para o OSRM local | `https://download.geofabrik.de/south-america/brazil/` |

### Backend

| Ferramenta | Uso |
|---|---|
| Java 21 | Linguagem |
| Spring Boot 3.5 (Web, Validation, Data JPA, Cache, Actuator) | API REST, validação, acesso ao banco, health check |
| Spring `RestClient` | Chamadas às APIs externas, com timeout |
| Flyway | Única fonte do schema do banco (migrations SQL) |
| PostgreSQL 16 | Banco de dados (local em Docker; Supabase em produção) |
| springdoc-openapi 2.8 | Documentação da API e Swagger UI |
| Maven Wrapper | Build sem instalar o Maven |
| JUnit 5, AssertJ, Spring Boot Test | Testes |
| Testcontainers | PostgreSQL real nos testes |
| WireMock 3 | Simula ViaCEP, BrasilAPI, Nominatim e OSRM nos testes |

### Frontend

| Ferramenta | Uso |
|---|---|
| React 19 + TypeScript | Interface |
| Vite | Servidor de desenvolvimento e build |
| Leaflet + react-leaflet | Mapa, marcadores numerados e linha da rota |
| TanStack React Query | Chamadas à API com loading, erro e cache |
| React Router | Páginas: planejar, histórico e detalhe da rota |
| Vitest + Testing Library + jsdom | Testes |
| oxlint | Lint |

### Infraestrutura

| Ferramenta | Uso |
|---|---|
| Docker + Docker Compose | PostgreSQL e OSRM locais |
| Colima (macOS) | Runtime do Docker usado no desenvolvimento |
| Imagem `ghcr.io/project-osrm/osrm-backend:v6.0.0` | OSRM local |
| Supabase (plano gratuito) | Banco em produção |
| Render (plano gratuito) | Hospedagem do backend em Docker (`render.yaml`) |
| Vercel (plano Hobby) | Hospedagem do frontend (`frontend/vercel.json`) |

## Pré-requisitos

- **Java 21**. O Maven vem pelo wrapper (`backend/mvnw`); não precisa instalar.
- **Docker** com Compose. No macOS com Colima, use pelo menos 4 GB: `colima start --memory 4 --cpu 4`.
  Os comandos abaixo usam `docker-compose`; com o plugin novo, troque por `docker compose`.
- **Node 20+** e npm.
- Portas livres: `5440` (PostgreSQL), `8080` (backend), `5173` (frontend) e, se usar OSRM local, `5000`.

## Subir o POC em 5 passos

Sem OSRM local, usando o servidor público de demonstração do OSRM:

```bash
# 1. Banco
docker-compose up -d postgres

# 2. Backend (perfil dev; o Flyway cria as tabelas na primeira subida)
cd backend
OSRM_BASE_URL=https://router.project-osrm.org ./mvnw spring-boot:run

# 3. Em outro terminal: frontend
cd frontend
npm install
npm run dev
```

4. Abra http://localhost:5173, informe a origem e os destinos e clique em **Calcular rota**.
5. Confira a API em http://localhost:8080/swagger-ui.html e a saúde em http://localhost:8080/actuator/health.

Para testar rápido, use CEPs de São Paulo: origem `01001-000` nº 1; destinos `01310-100` nº 1000, `04538-133` nº 1500 e `01414-001` nº 100.

## Banco local

```bash
docker-compose up -d postgres
```

PostgreSQL 16 em `localhost:5440` (troque com `POSTGRES_PORT`), banco, usuário e senha `autorota` (apenas desenvolvimento). O schema vem só das migrations do Flyway em `backend/src/main/resources/db/migration`.

## OSRM local (opcional)

O OSRM local evita o limite do servidor público. O mapa inteiro do Brasil exige bem mais que 8 GB de memória no pré-processamento; para desenvolvimento, use uma região da Geofabrik: `sul`, `sudeste`, `nordeste`, `norte` ou `centro-oeste`. Rotas fora da região escolhida não são encontradas.

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

Com o OSRM local no ar, suba o backend sem `OSRM_BASE_URL` (o padrão é `http://localhost:5000`).

## Backend

```bash
cd backend
./mvnw spring-boot:run      # perfil dev por padrão
./mvnw test                 # usa Testcontainers: exige Docker ativo
./mvnw -DskipTests package  # gera target/auto-rota-backend-*.jar
```

Com Colima, o Testcontainers precisa saber onde está o socket:

```bash
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

### Endpoints

| Método | Caminho | Uso |
|---|---|---|
| POST | `/api/routes/plan` | Calcula e salva uma rota |
| GET | `/api/routes?name=&from=&to=&page=&size=` | Histórico (datas `AAAA-MM-DD`, inclusivas, no fuso `app.time-zone`) |
| GET | `/api/routes/{id}` | Detalhe com paradas e geometria |
| POST | `/api/routes/{id}/stops/{visitOrder}/deliver` | Marca como entregue (corpo opcional `{"deliveryNote": "..."}`) |
| POST | `/api/routes/{id}/stops/{visitOrder}/fail` | Marca como não entregue |
| GET | `/api/cep/{cep}` | Endereço do CEP |

Erros seguem a RFC 7807 (`ProblemDetail`) com um campo `code` estável: `VALIDATION_ERROR` (400, com `errors` por campo), `CEP_NOT_FOUND` (404), `NOT_FOUND` (404), `ADDRESS_NOT_GEOCODED` (422), `ROUTE_NOT_FOUND` (422), `EXTERNAL_SERVICE_UNAVAILABLE` (503) e `INTERNAL_ERROR` (500).

### Como a rota é calculada

1. ViaCEP dá o endereço do CEP.
2. Coordenadas: por padrão o Nominatim geocodifica logradouro + número, aceitando só resultados na cidade do CEP; se o número não existir no OpenStreetMap, usa um ponto da própria rua; se nada servir, usa a BrasilAPI. A ordem é configurável (`GEOCODING_STRATEGY`).
3. OSRM `/table` dá a matriz de distâncias e tempos.
4. Vizinho mais próximo seguido de 2-opt escolhe a ordem (origem fixa no início; na rota circular, também no fim).
5. OSRM `/route` dá a geometria e os trechos; os totais somam os trechos, incluindo a volta.
6. Combustível: `litros = km / consumo` e `custo = litros × preço`, em `BigDecimal`.

ViaCEP, BrasilAPI e Nominatim têm cache no banco (`cep_cache` e `geocode_cache`), inclusive para "não encontrado".

## Frontend

```bash
cd frontend
npm install
npm run dev        # http://localhost:5173
npm test           # Vitest + Testing Library
npm run build      # gera dist/
```

A URL do backend vem de `VITE_API_URL` (ver `frontend/.env.example`). Em branco, usa `http://localhost:8080`. No build de produção (`npm run build`), vale `frontend/.env.production`, que aponta para o backend publicado. O backend só aceita chamadas das origens em `app.cors.allowed-origins` (em dev, `http://localhost:5173`).

## Deploy gratuito

Banco no Supabase, backend no Render e frontend na Vercel, todos em planos gratuitos.

### Banco (Supabase)

1. Crie um projeto na região `sa-east-1` e defina a senha do banco.
2. Em **Connect**, copie a string do **Session pooler**. A conexão direta (`db.<ref>.supabase.co`) só tem IPv6 e o Render não alcança.
3. Converta para JDBC: `jdbc:postgresql://<host-do-pooler>:5432/postgres?sslmode=require`. O usuário é `postgres.<ref>`.

O Flyway cria e atualiza o schema quando o backend sobe. A V2 liga RLS sem políticas: a Data API do Supabase fica sem acesso às tabelas, e o backend, que conecta como dono, não é afetado.

### Backend (Render)

O `render.yaml` na raiz define o serviço: Docker (`backend/Dockerfile`), plano Free e health check em `/actuator/health`.

1. No Render: **New → Blueprint** e conecte o repositório.
2. Preencha `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `NOMINATIM_USER_AGENT` e `CORS_ALLOWED_ORIGINS` (URL da Vercel; até ter a URL, use `http://localhost:5173`).

Para testar a imagem localmente:

```bash
cd backend
docker build -t auto-rota-backend:local .
docker run --rm -p 8080:8080 -m 512m --env-file ../.env auto-rota-backend:local
curl http://localhost:8080/actuator/health
```

### Frontend (Vercel)

1. Na Vercel: **Add New → Project**, importe o repositório e defina **Root Directory** como `frontend`. O preset Vite é detectado.
2. A URL do backend vem de `frontend/.env.production`; se o backend mudar de endereço, atualize esse arquivo ou defina `VITE_API_URL` no projeto da Vercel (tem prioridade, mas exige Redeploy). Faça o deploy.
3. Volte ao Render, troque `CORS_ALLOWED_ORIGINS` pela URL da Vercel e salve, o que reinicia o serviço.

O `frontend/vercel.json` manda toda rota para o `index.html`, para que endereços como `/rotas/{id}` funcionem ao recarregar a página.

### Limites dos planos gratuitos

- **Render Free:** o serviço dorme após 15 minutos sem acesso; a primeira requisição depois disso leva cerca de 1 minuto. 512 MB de memória.
- **Supabase Free:** o projeto é pausado após 7 dias sem uso e pode ser reativado no painel (depois de muito tempo pausado, deixa de poder).
- **Vercel Hobby:** apenas uso não comercial.

## Variáveis de ambiente

Os nomes estão em `.env.example` (backend e Compose) e `frontend/.env.example`. Nunca versione valores reais.

| Variável | Perfil | Padrão | Para quê |
|---|---|---|---|
| `DATABASE_URL` | prod | — | JDBC do PostgreSQL, ex.: `jdbc:postgresql://host:5432/postgres?sslmode=require` |
| `DATABASE_USERNAME`, `DATABASE_PASSWORD` | prod | — | Credenciais do banco |
| `CORS_ALLOWED_ORIGINS` | prod | — | URL do frontend publicado (várias separadas por vírgula) |
| `NOMINATIM_USER_AGENT` | prod | — | Identificação exigida pelo Nominatim, com contato: `auto-rota-poc/1.0 (seu-email-ou-url)` |
| `NOMINATIM_BASE_URL` | prod | `https://nominatim.openstreetmap.org` | Instância do Nominatim |
| `OSRM_BASE_URL` | dev e prod | dev `http://localhost:5000`; prod `https://router.project-osrm.org` | Servidor OSRM |
| `OSRM_SNAP_RADIUS_M` | prod | `1000` | Distância máxima (m) para encaixar um ponto numa via |
| `DB_POOL_MAX_SIZE` | prod | `5` | Máximo de conexões do pool com o banco |
| `GEOCODING_STRATEGY` | prod | `NOMINATIM_FIRST` | `NOMINATIM_FIRST` ou `BRASILAPI_FIRST` |
| `OPTIMIZER_METRIC` | prod | `DISTANCE` | Minimizar `DISTANCE` (combustível) ou `DURATION` (tempo) |
| `FUEL_DEFAULT_CONSUMPTION_KM_PER_L` | prod | `10.00` | Consumo quando o pedido não informa |
| `FUEL_DEFAULT_PRICE_PER_L` | prod | `6.000` | Preço do litro quando o pedido não informa (valor provisório) |
| `PORT` | todos | `8080` | Porta HTTP do backend |
| `POSTGRES_PORT` | Compose/dev | `5440` | Porta do PostgreSQL local no host |
| `OSRM_REGION` | Compose | `sudeste-latest` | Região do OSRM local |
| `VITE_API_URL` | frontend | `http://localhost:8080` | URL do backend usada no build do frontend |

Em dev, as demais propriedades ficam em `backend/src/main/resources/application.yml` e podem ser trocadas por variável de ambiente no formato do Spring (ex.: `APP_GEOCODING_STRATEGY=BRASILAPI_FIRST`).

## Limitações conhecidas

- **Serviços públicos gratuitos, sem garantia.** O servidor de demonstração do OSRM e o Nominatim público têm limites de uso e podem ficar lentos ou fora do ar; nesses casos a API responde 503. Não servem para produção com volume real.
- **Nominatim: 1 requisição por segundo.** Cada endereço novo pode levar até cerca de 2 s (com número e, se preciso, só a rua). Uma rota com 10 endereços inéditos pode passar de 10 s. Endereços repetidos vêm do cache. O limite vale por instância do backend; com várias instâncias, o total passaria de 1 req/s.
- **Precisão do endereço.** Se o OpenStreetMap não tiver o número, o ponto fica em algum trecho da rua, não na porta. A BrasilAPI costuma devolver o centro da cidade, por isso fica como último recurso. CEP geral de cidade (sem logradouro) usa a coordenada da cidade.
- **Cache sem expiração.** `cep_cache` e `geocode_cache` não expiram; um CEP alterado nos Correios ou um endereço corrigido no OpenStreetMap só é relido apagando a linha correspondente.
- **OSRM local regional.** Com uma região da Geofabrik, rotas que saem dela não são encontradas (422).
- **Somente carro.** O perfil do OSRM é `driving`; não há restrição de veículo, pedágio ou trânsito em tempo real. Os tempos são estimativas sem trânsito.
- **Otimização heurística.** Vizinho mais próximo + 2-opt dá boas rotas para até 10 destinos, mas não garante a ótima.
- **Sem autenticação.** Qualquer pessoa com acesso à URL vê o histórico e marca entregas. É uma escolha do POC.
- **Tiles do OpenStreetMap.** O mapa usa `tile.openstreetmap.org`, adequado só para uso leve, conforme a política de uso do OSM.
- **Preço padrão do combustível** (`6.000`) é provisório; ajuste `FUEL_DEFAULT_PRICE_PER_L`.
