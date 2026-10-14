-- Callback do Flyway, executado ao fim de cada migrate, quando o histórico já está liberado.
-- Bloqueia o histórico de migrações para a Data API do Supabase (ver V2). Idempotente.
ALTER TABLE flyway_schema_history ENABLE ROW LEVEL SECURITY;
