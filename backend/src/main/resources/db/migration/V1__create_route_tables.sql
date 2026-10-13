-- Planos de rota, paradas e caches das APIs externas.
-- Status como VARCHAR + CHECK (não ENUM do PostgreSQL), mapeados com @Enumerated(EnumType.STRING).

CREATE TABLE route_plan (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name                  VARCHAR(120),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),

    origin_cep            VARCHAR(8)    NOT NULL,
    origin_street         VARCHAR(200),
    origin_number         VARCHAR(20)   NOT NULL,
    origin_complement     VARCHAR(100),
    origin_neighborhood   VARCHAR(100),
    origin_city           VARCHAR(100)  NOT NULL,
    origin_uf             VARCHAR(2)    NOT NULL,
    origin_lat            DOUBLE PRECISION NOT NULL,
    origin_lng            DOUBLE PRECISION NOT NULL,

    round_trip            BOOLEAN       NOT NULL DEFAULT TRUE,
    consumption_km_per_l  NUMERIC(6, 2) NOT NULL,
    fuel_price_per_l      NUMERIC(8, 3) NOT NULL,

    total_distance_m      INTEGER       NOT NULL,
    total_duration_s      INTEGER       NOT NULL,
    fuel_liters           NUMERIC(10, 3) NOT NULL,
    fuel_cost             NUMERIC(12, 2) NOT NULL,
    return_distance_m     INTEGER,
    return_duration_s     INTEGER,

    route_geometry        JSONB,
    status                VARCHAR(20)   NOT NULL DEFAULT 'PLANEJADA',

    CONSTRAINT ck_route_plan_status
        CHECK (status IN ('PLANEJADA', 'EM_ANDAMENTO', 'CONCLUIDA')),
    CONSTRAINT ck_route_plan_origin_cep CHECK (origin_cep ~ '^[0-9]{8}$'),
    CONSTRAINT ck_route_plan_origin_uf CHECK (origin_uf IN (
        'AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA',
        'PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO')),
    CONSTRAINT ck_route_plan_origin_coords
        CHECK (origin_lat BETWEEN -90 AND 90 AND origin_lng BETWEEN -180 AND 180),
    CONSTRAINT ck_route_plan_consumption CHECK (consumption_km_per_l > 0),
    CONSTRAINT ck_route_plan_fuel_price CHECK (fuel_price_per_l > 0),
    CONSTRAINT ck_route_plan_totals CHECK (
        total_distance_m >= 0 AND total_duration_s >= 0 AND fuel_liters >= 0 AND fuel_cost >= 0),
    CONSTRAINT ck_route_plan_return CHECK (
        (round_trip AND return_distance_m IS NOT NULL AND return_duration_s IS NOT NULL
            AND return_distance_m >= 0 AND return_duration_s >= 0)
        OR (NOT round_trip AND return_distance_m IS NULL AND return_duration_s IS NULL))
);

CREATE INDEX ix_route_plan_created_at ON route_plan (created_at);

CREATE TABLE route_stop (
    id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    route_plan_id   UUID          NOT NULL REFERENCES route_plan (id) ON DELETE CASCADE,
    input_order     INTEGER       NOT NULL,
    visit_order     INTEGER       NOT NULL,

    cep             VARCHAR(8)    NOT NULL,
    street          VARCHAR(200),
    number          VARCHAR(20)   NOT NULL,
    complement      VARCHAR(100),
    neighborhood    VARCHAR(100),
    city            VARCHAR(100)  NOT NULL,
    uf              VARCHAR(2)    NOT NULL,
    lat             DOUBLE PRECISION NOT NULL,
    lng             DOUBLE PRECISION NOT NULL,
    coord_source    VARCHAR(20)   NOT NULL,

    -- Trecho anterior: da parada (ou origem) anterior até esta.
    leg_distance_m  INTEGER       NOT NULL,
    leg_duration_s  INTEGER       NOT NULL,

    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDENTE',
    delivered_at    TIMESTAMPTZ,
    delivery_note   VARCHAR(500),

    CONSTRAINT uq_route_stop_input_order UNIQUE (route_plan_id, input_order),
    CONSTRAINT uq_route_stop_visit_order UNIQUE (route_plan_id, visit_order),
    CONSTRAINT ck_route_stop_input_order CHECK (input_order BETWEEN 1 AND 10),
    CONSTRAINT ck_route_stop_visit_order CHECK (visit_order BETWEEN 1 AND 10),
    CONSTRAINT ck_route_stop_cep CHECK (cep ~ '^[0-9]{8}$'),
    CONSTRAINT ck_route_stop_uf CHECK (uf IN (
        'AC','AL','AP','AM','BA','CE','DF','ES','GO','MA','MT','MS','MG','PA',
        'PB','PR','PE','PI','RJ','RN','RS','RO','RR','SC','SP','SE','TO')),
    CONSTRAINT ck_route_stop_coords CHECK (lat BETWEEN -90 AND 90 AND lng BETWEEN -180 AND 180),
    CONSTRAINT ck_route_stop_coord_source CHECK (coord_source IN ('BRASILAPI', 'NOMINATIM', 'GOOGLE')),
    CONSTRAINT ck_route_stop_leg CHECK (leg_distance_m >= 0 AND leg_duration_s >= 0),
    CONSTRAINT ck_route_stop_status CHECK (status IN ('PENDENTE', 'ENTREGUE', 'NAO_ENTREGUE')),
    CONSTRAINT ck_route_stop_delivered_at CHECK (status <> 'ENTREGUE' OR delivered_at IS NOT NULL)
);

-- Endereço do ViaCEP por CEP. Chave: SHA-256 (hex) do CEP normalizado (8 dígitos).
-- found = FALSE registra CEP inexistente ({"erro": true}).
CREATE TABLE cep_cache (
    key_hash      VARCHAR(64)   PRIMARY KEY,
    cep           VARCHAR(8)    NOT NULL,
    found         BOOLEAN       NOT NULL,
    street        VARCHAR(200),
    complement    VARCHAR(100),
    neighborhood  VARCHAR(100),
    city          VARCHAR(100),
    uf            VARCHAR(2),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_cep_cache_key CHECK (key_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_cep_cache_cep CHECK (cep ~ '^[0-9]{8}$'),
    CONSTRAINT ck_cep_cache_found CHECK (NOT found OR (city IS NOT NULL AND uf IS NOT NULL))
);

-- Coordenadas por consulta (BrasilAPI por CEP ou Nominatim por endereço).
-- Chave: SHA-256 (hex) da consulta normalizada. lat/lng NULL = não encontrado.
CREATE TABLE geocode_cache (
    key_hash    VARCHAR(64)   PRIMARY KEY,
    query_text  VARCHAR(500)  NOT NULL,
    provider    VARCHAR(20)   NOT NULL,
    lat         DOUBLE PRECISION,
    lng         DOUBLE PRECISION,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_geocode_cache_key CHECK (key_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_geocode_cache_provider CHECK (provider IN ('BRASILAPI', 'NOMINATIM', 'GOOGLE')),
    CONSTRAINT ck_geocode_cache_coords CHECK (
        (lat IS NULL AND lng IS NULL)
        OR (lat BETWEEN -90 AND 90 AND lng BETWEEN -180 AND 180))
);
