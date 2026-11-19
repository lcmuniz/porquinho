-- V50: alertas automáticos de métricas críticas (Story 10.5)
-- Um job a cada 5 minutos avalia as métricas da saúde, das importações e da IA. Cada violação nova vira uma linha.
-- Episódio: a linha da chave com normalized_at nulo. Enquanto ele existe a chave não abre outro alerta; a métrica
-- voltar ao normal grava normalized_at. Resolver (resolved_at/resolved_by) é ação do admin e não fecha o episódio.
-- As linhas nunca são apagadas. Só agregados e o nome do banco, nenhum dado de usuário.
-- ok_requests: tráfego "de usuário" do alerta de sessões ativas = 0; robôs (401/404) e o webhook do Stripe ficam fora.

ALTER TABLE system_health_snapshots ADD COLUMN ok_requests BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN system_health_snapshots.ok_requests IS 'Respostas 2xx no minuto, fora /actuator e fora /api/v1/webhooks/.';

CREATE TABLE system_alerts (
    id BIGSERIAL PRIMARY KEY,
    alert_key VARCHAR(80) NOT NULL,
    alert_type VARCHAR(40) NOT NULL,
    bank VARCHAR(40),
    metric_value NUMERIC(14, 2) NOT NULL,
    threshold NUMERIC(14, 2) NOT NULL,
    triggered_at TIMESTAMPTZ NOT NULL,
    normalized_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_system_alerts_key_triggered ON system_alerts (alert_key, triggered_at DESC);
-- No máximo um episódio aberto por chave, mesmo com duas execuções ao mesmo tempo
CREATE UNIQUE INDEX uq_system_alerts_open_episode ON system_alerts (alert_key) WHERE normalized_at IS NULL;
CREATE INDEX idx_system_alerts_triggered ON system_alerts (triggered_at DESC);

COMMENT ON TABLE system_alerts IS 'Alertas de métricas críticas do sistema (Story 10.5): histórico da aba Alertas do /admin.';
COMMENT ON COLUMN system_alerts.alert_key IS 'Métrica, com o banco quando é por banco (ex.: IMPORT_ERROR_RATE:ITAU).';
COMMENT ON COLUMN system_alerts.alert_type IS 'Métrica: ERROR_RATE, UPTIME, RESPONSE_TIME, IMPORT_ERROR_RATE, AI_ACCURACY, ACTIVE_SESSIONS, REDIS_DOWN ou DB_POOL_EXHAUSTED.';
COMMENT ON COLUMN system_alerts.metric_value IS 'Valor da métrica na violação.';
COMMENT ON COLUMN system_alerts.threshold IS 'Limite da métrica.';
COMMENT ON COLUMN system_alerts.triggered_at IS 'Hora da violação.';
COMMENT ON COLUMN system_alerts.normalized_at IS 'Quando a métrica voltou ao normal (fim do episódio); nulo enquanto viola.';
COMMENT ON COLUMN system_alerts.resolved_at IS 'Quando um admin marcou como resolvido; nulo enquanto aberto.';
COMMENT ON COLUMN system_alerts.resolved_by IS 'Admin que marcou como resolvido.';
