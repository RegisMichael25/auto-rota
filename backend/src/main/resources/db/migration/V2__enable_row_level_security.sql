-- No Supabase, a Data API (PostgREST) expõe o schema public aos papéis anon e authenticated.
-- RLS ligado e sem políticas bloqueia esse acesso. O backend conecta como dono das tabelas
-- (postgres no Supabase, com BYPASSRLS), então não é afetado.
-- A flyway_schema_history fica no afterMigrate.sql: alterá-la durante a migração trava no lock do Flyway.
ALTER TABLE route_plan ENABLE ROW LEVEL SECURITY;
ALTER TABLE route_stop ENABLE ROW LEVEL SECURITY;
ALTER TABLE cep_cache ENABLE ROW LEVEL SECURITY;
ALTER TABLE geocode_cache ENABLE ROW LEVEL SECURITY;
