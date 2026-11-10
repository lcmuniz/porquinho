-- Callback do Flyway (roda após todo migrate): liga Row-Level Security em todas as tabelas do schema public
-- (alerta rls_disabled_in_public do Supabase). Callback e não migration versionada porque o ALTER na
-- flyway_schema_history trava durante a migração (lock do próprio Flyway) e porque cobre tabelas futuras sozinho.
-- Sem policies de propósito: anon/authenticated (PostgREST) ficam sem acesso. O backend conecta como dono das
-- tabelas, que ignora RLS (sem FORCE), então nada muda para a aplicação.
DO $$
DECLARE t record;
BEGIN
    FOR t IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
             WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') AND NOT c.relrowsecurity LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t.relname);
    END LOOP;
END $$;
