# POC: Planejamento de rotas de entrega para lojistas

## Contexto

Sistema para lojistas que entregam produtos. O usuário informa a origem (unidade física do lojista) e até 10 destinos (CEP + número). O sistema:

- calcula a melhor ordem de visita com distância real de estrada;
- desenha a rota no mapa;
- estima distância, tempo, litros e custo de combustível;
- suporta rota circular (retorna à origem) e aberta (termina na última parada);
- mantém histórico de rotas;
- permite marcar entregas como feitas ou não feitas manualmente.

## Restrições

- Somente entregas no Brasil.
- Sem autenticação, sem cadastro de cliente e sem cadastro de distribuidora.
- Sem custo: apenas serviços gratuitos. Não use bibliotecas ou serviços com cobrança obrigatória.
- Nunca grave segredos no repositório. Use variáveis de ambiente e mantenha `.env.example` apenas com nomes.

## Stack

- **Backend:** Java 21, Spring Boot 3, Spring Web, Validation, Spring Cache, Spring Data JPA, Flyway, springdoc-openapi, Actuator, PostgreSQL.
- **Frontend:** React + Vite + TypeScript, react-leaflet (tiles OpenStreetMap), React Query.
- **Banco:** Supabase (PostgreSQL gerenciado, plano gratuito).
- **Infra local:** Docker Compose com PostgreSQL e OSRM (mapa do Brasil).
- **Deploy:** Render (backend em Docker), Vercel (frontend).

## Serviços externos

- **ViaCEP:** `https://viacep.com.br/ws/{cep}/json/`. Retorna `{"erro": true}` para CEP inexistente.
- **BrasilAPI:** `https://brasilapi.com.br/api/cep/v2/{cep}`. Pode trazer latitude e longitude; nem sempre traz.
- **Nominatim:** geocodificação de logradouro + número + cidade + UF. Exige `User-Agent` identificado, no máximo 1 requisição por segundo e cache.
- **OSRM:** `/table` para matriz de distâncias e durações; `/route` com `geometries=geojson` para a geometria. Local em desenvolvimento; servidor público de demonstração em produção.

## Fluxo de cálculo

1. ViaCEP para endereço.
2. BrasilAPI para coordenadas; se não vierem, Nominatim com logradouro + número.
3. OSRM `/table` entre origem e destinos.
4. Otimização: vizinho mais próximo seguido de 2-opt, sobre a matriz. A origem é o índice 0 (início fixo). Circular: a origem também fecha o ciclo. Aberta: o último nó é livre.
5. OSRM `/route` para a geometria da ordem final.
6. Combustível: `litros = distância_km / consumo_km_por_litro`; `custo = litros × preço_por_litro`. Valores monetários em `BigDecimal`.

## Decisões de modelagem

- `route_plan` guarda origem, premissas (consumo, preço, `round_trip`), totais, geometria e status.
- `route_stop` guarda os destinos com `input_order` (ordem informada) e `visit_order` (ordem otimizada), além do trecho anterior e do status de entrega.
- `cep_cache` e `geocode_cache` evitam chamadas repetidas às APIs externas. `geocode_cache` também guarda "não encontrado" (`lat` NULL).
- Status com `CHECK` e `@Enumerated(EnumType.STRING)`, não `ENUM` do PostgreSQL.
- **O Flyway é a única fonte do schema.** Nunca aplique DDL diretamente no Supabase (`apply_migration` ou `execute_sql` com DDL). Use `execute_sql` apenas para consultas de verificação.

## Divisão de responsabilidades

Nas etapas de `docs/ETAPAS.md`, cada item tem um rótulo:

- **[AUTO]:** execute sozinho com as ferramentas disponíveis (código, testes, Docker local, conector Supabase). Não peça permissão para cada comando.
- **[USUÁRIO]:** exige minha conta, login ou decisão de custo. Pare, escreva o bloco **AÇÃO DO USUÁRIO** com passos numerados e exatos, e aguarde minha resposta.

Regras adicionais:

- Nunca peça que eu cole senhas, tokens ou connection strings no chat. Indique a variável de ambiente em que o valor deve ser colocado.
- Antes de qualquer criação cobrável no Supabase, rode `get_cost`, mostre o custo e espere confirmação explícita antes de `confirm_cost`.
- Se houver ambiguidade, pergunte antes de decidir.
- Decisões em aberto viram propriedades configuráveis, não código fixo.

## Como trabalhar

- Siga `docs/ETAPAS.md` na ordem. Não avance sem minha confirmação ao final de cada etapa.
- Ao final de cada etapa: liste arquivos criados ou alterados, mostre o resultado dos testes e, se houver, as ações [USUÁRIO] pendentes.
- Commits pequenos por etapa, com mensagem descritiva. Não faça push sem eu pedir.
- Antes de instalar dependências ou subir containers, diga o que vai executar.