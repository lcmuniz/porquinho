-- V35: goals.template (Story 8.6)
-- Modelo de origem da meta criada em /metas (os modelos ficam só no front). Nulo para metas personalizadas,
-- para a meta do onboarding e para as metas antigas (sem backfill).

ALTER TABLE goals ADD COLUMN template VARCHAR(30);
ALTER TABLE goals ADD CONSTRAINT goals_template_check
    CHECK (template IS NULL OR template IN ('EMERGENCY_FUND', 'VACATION', 'BIG_PURCHASE'));

COMMENT ON COLUMN goals.template IS 'Modelo de origem (EMERGENCY_FUND, VACATION, BIG_PURCHASE) ou nulo (Story 8.6).';
