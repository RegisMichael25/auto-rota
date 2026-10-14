# Etapas do POC

Cada etapa termina com aceite. Não avance sem confirmação.

## Etapa 1: Esqueleto do backend [AUTO]

- Projeto Spring Boot com as dependências do CLAUDE.md.
- Pacotes: `client`, `service`, `optimizer`, `domain`, `repository`, `web` (controllers e DTOs), `config`.
- `application.yml` com perfis `dev`, `test` e `prod`. URLs externas e datasource como propriedades.
- Perfil `prod` lê variáveis de ambiente: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `OSRM_BASE_URL`, `NOMINATIM_BASE_URL`, `NOMINATIM_USER_AGENT`, `CORS_ALLOWED_ORIGINS`, `PORT`.
- `docker-compose.yml` com PostgreSQL e OSRM. O preparo do OSRM com o mapa do Brasil (Geofabrik) fica no README.
- `.env.example` com os nomes das variáveis, sem valores.

**Aceite:** a aplicação sobe em `dev`, conecta no PostgreSQL do Compose e responde em `/actuator/health`.

## Etapa 2: Modelo de dados (Flyway) [AUTO]

`V1__create_route_tables.sql` com:

- `route_plan`: id UUID, `name` opcional, `created_at`, origem completa com lat/lng, `round_trip` (default true), `consumption_km_per_l`, `fuel_price_per_l`, totais, `return_distance_m` e `return_duration_s` (só quando `round_trip = true`), `route_geometry` JSONB, `status` (PLANEJADA, EM_ANDAMENTO, CONCLUIDA). CHECKs para status, UF, consumo, preço e consistência de `round_trip` com os campos de retorno.
- `route_stop`: id UUID, `route_plan_id` com `ON DELETE CASCADE`, `input_order` (1 a 10), `visit_order`, endereço, lat/lng, `coord_source` (BRASILAPI, NOMINATIM, GOOGLE), `leg_distance_m`, `leg_duration_s`, `status` (PENDENTE, ENTREGUE, NAO_ENTREGUE), `delivered_at`, `delivery_note`. CHECK exigindo `delivered_at` quando ENTREGUE. UNIQUE em `(route_plan_id, input_order)` e `(route_plan_id, visit_order)`.
- `cep_cache` e `geocode_cache` (hash SHA-256 como chave; lat/lng NULL = não encontrado).
- Índice em `route_plan(created_at)`.
- Entidades JPA correspondentes.

**Aceite:** Flyway aplica a migração limpa no PostgreSQL local; teste de integração com Testcontainers sobe o schema.

## Etapa 3: Clientes de CEP e geocodificação [AUTO]

- `ViaCepClient`: normaliza CEP (só dígitos), trata `erro` com exceção de domínio, cache em `cep_cache`.
- `BrasilApiClient`: retorna vazio quando não há coordenadas, sem lançar erro.
- `NominatimClient`: país Brasil fixo na consulta; limite de 1 req/s com controle thread-safe; `User-Agent` configurado; cache em `geocode_cache`, inclusive "não encontrado".
- Serviço que orquestra ViaCEP → BrasilAPI → Nominatim (só se faltarem coordenadas) e registra `coord_source`.
- Testes com WireMock: sucesso, CEP inexistente, coordenadas ausentes, cache hit, intervalo do Nominatim.

**Aceite:** testes passam; chamada repetida não gera requisição externa.

## Etapa 4: Cliente OSRM [AUTO]

- `OsrmClient.table(origem, destinos)`: matriz de distâncias (m) e durações (s).
- `OsrmClient.route(coordenadas)`: geometria GeoJSON e totais.
- Indisponibilidade vira erro de domínio claro.
- Testes com WireMock.

**Aceite:** testes passam.

## Etapa 5: Otimizador [AUTO]

- `NearestNeighbor` e `TwoOpt` sobre a matriz. Origem no índice 0.
- Circular: origem fecha o ciclo e o custo inclui o trecho de volta.
- Aberta: último nó livre, sem trecho de volta.
- Retorna ordem, `leg_distance_m`/`leg_duration_s` por parada e, no modo circular, `return_distance_m`/`return_duration_s`.
- Testes unitários com matrizes pequenas e ótimo verificável, incluindo um caso em que o 2-opt melhora o vizinho mais próximo.

**Aceite:** testes passam; o 2-opt nunca piora o resultado.

## Etapa 6: Planejamento e combustível [AUTO]

- `RoutePlannerService`: resolve endereços, monta matriz, otimiza, busca geometria, calcula litros e custo, grava `route_plan` e `route_stop` em uma transação.
- `FuelCalculator` separado, com testes dos dois modos e do arredondamento monetário.
- Padrões de consumo e preço no `application.yml`, sobrescritos pela requisição.

**Aceite:** teste de integração com OSRM e APIs simuladas gera rota completa nos dois modos.

## Etapa 7: API REST [AUTO]

- `POST /api/routes/plan`
- `GET /api/routes` (paginado, filtro por período e `name`)
- `GET /api/routes/{id}`
- `POST /api/routes/{id}/stops/{visitOrder}/deliver` (com `delivery_note` opcional)
- `POST /api/routes/{id}/stops/{visitOrder}/fail`
- `GET /api/cep/{cep}`
- Validação: máximo 10 destinos, CEP com 8 dígitos, número obrigatório, consumo e preço positivos. Erros em `ProblemDetail` (RFC 7807). Documentação com springdoc.

**Aceite:** testes MockMvc de sucesso e validação; Swagger UI acessível.

## Etapa 8: Frontend [AUTO]

- Vite + TypeScript; cliente HTTP em `src/api`; URL da API em `VITE_API_URL`.
- Tela de planejamento: origem, até 10 destinos, toggle "Retornar à origem" (padrão ligado), consumo e preço. CEP válido preenche o endereço.
- Mapa com marcadores numerados na ordem de visita e `Polyline` da geometria.
- Resultado: ordem otimizada e informada, distância, tempo, litros, custo e trecho de volta (se circular).
- Paradas com "Entregue", "Não entregue" e observação.
- Histórico com filtro e detalhe.
- Loading e erro com React Query.

**Aceite:** fluxo completo funciona no navegador contra o backend local.

## Etapa 9: Revisão e README [AUTO]

- README com pré-requisitos, Compose, preparo do OSRM, execução de backend e frontend, variáveis e limitações conhecidas.
- Revisão: consultas sem cache, chamadas externas sem tratamento de erro, dinheiro em `double`, falta de transação, lacunas de teste. Corrija o que encontrar.

**Aceite:** um desenvolvedor sobe o POC seguindo só o README.

## Etapa 10: Banco no Supabase [AUTO + USUÁRIO]

1. [AUTO] `list_organizations` e `list_projects`. Se já existir projeto deste POC, use-o.
2. [AUTO] `get_cost` para projeto gratuito. Mostre o custo.
3. [USUÁRIO] Confirme a criação. Só então execute `confirm_cost` e `create_project` (nome `rotas-poc`, região mais próxima do Brasil, `sa-east-1` se disponível).
4. [AUTO] `get_project_url` e `get_publishable_keys`, registrando apenas os nomes das variáveis em `.env.example`.
5. [USUÁRIO] Copie a string de conexão em Project Settings → Database e cole nas variáveis de ambiente locais (`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`). Não cole a senha no chat.
6. [AUTO] Rode o perfil `prod` contra o Supabase. Verifique com `list_migrations` e `list_tables`.

**Aceite:** `list_tables` mostra `route_plan`, `route_stop`, `cep_cache` e `geocode_cache`; `list_migrations` mostra a V1 aplicada.

## Etapa 11: Deploy [AUTO + USUÁRIO]

1. [AUTO] Dockerfile multi-stage do backend (JRE 21 enxuto, porta via `PORT`).
2. [AUTO] `render.yaml` (Blueprint): serviço Docker, plano gratuito, health check em `/actuator/health`, segredos declarados sem valor.
3. [AUTO] `vercel.json` se necessário; frontend lendo `VITE_API_URL`.
4. [AUTO] CORS restrito a `CORS_ALLOWED_ORIGINS`.
5. [AUTO] `docker build` local e teste da imagem no perfil `prod`.
6. [AUTO] Seção de deploy no README.
7. [USUÁRIO] Crie o repositório no GitHub e envie o código (`git push`). Confirme quando estiver feito.
8. [USUÁRIO] Render: New → Blueprint, conectar o repositório, preencher `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` e `CORS_ALLOWED_ORIGINS`. Informe a URL pública do backend.
9. [USUÁRIO] Vercel: Add New → Project, importar o repositório, definir `VITE_API_URL` com a URL do Render. Informe a URL do frontend.
10. [USUÁRIO] Render: atualizar `CORS_ALLOWED_ORIGINS` com a URL da Vercel e reiniciar.
11. [AUTO] Testar `/actuator/health` e, se eu fornecer a URL, `/api/routes/plan`.
12. [USUÁRIO] Teste o fluxo completo pela URL da Vercel e me diga o resultado.

**Aceite:** health check responde na URL pública; fluxo funciona pela Vercel; migrações aplicadas no Supabase.