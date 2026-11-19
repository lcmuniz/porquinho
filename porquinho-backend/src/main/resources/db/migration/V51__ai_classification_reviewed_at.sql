-- V51: data de revisão da classificação da IA (retro do Epic 10, A3)
-- A precisão da IA (aba IA do /admin e alerta AI_ACCURACY da Story 10.5) passa a contar pela data em que o usuário
-- revisou a classificação, não pela data em que a IA classificou: uma correção feita no dia seguinte à importação
-- entra na janela de 24 h do alerta.
-- Gravada quando a transação sai da revisão (aprovada ou importação concluída) e a cada vez que o usuário escolhe a
-- categoria (a última escolha vale). Nula enquanto a transação está em revisão.

ALTER TABLE ai_classification_history ADD COLUMN reviewed_at TIMESTAMPTZ;

-- Linhas antigas: a última alteração da transação revisada é a melhor aproximação da revisão
UPDATE ai_classification_history h SET reviewed_at = t.updated_at
FROM transactions t
WHERE t.id = h.transaction_id AND t.review_status <> 'PENDING';

COMMENT ON COLUMN ai_classification_history.reviewed_at IS 'Quando o usuário revisou (aprovou, corrigiu ou concluiu a importação); nula em revisão.';

-- Métrica nova do alerta (retenção travada, mesma retro): só o comentário muda, alert_type não tem CHECK
COMMENT ON COLUMN system_alerts.alert_type IS 'Métrica: ERROR_RATE, UPTIME, RESPONSE_TIME, IMPORT_ERROR_RATE, AI_ACCURACY, ACTIVE_SESSIONS, REDIS_DOWN, DB_POOL_EXHAUSTED ou RETENTION_STALLED.';
