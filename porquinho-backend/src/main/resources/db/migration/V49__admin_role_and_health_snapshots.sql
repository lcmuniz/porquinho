-- V49: papel de administrador e histórico de saúde do sistema (Story 10.1)
-- is_admin: promovido à mão (UPDATE users SET is_admin = true WHERE email = ...), sem tela; ver docs/deploy-checklist.md.
-- system_health_snapshots: uma linha por minuto com o delta dos contadores http.server.requests (fora /actuator).
-- Os contadores do Micrometer zeram a cada restart; o snapshot gravado é o que faz uptime e erros de 7/30 dias
-- sobreviverem a ele. O próprio job apaga as linhas com mais de 31 dias.

ALTER TABLE users ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN users.is_admin IS 'Administrador do sistema: acessa /api/v1/admin/* e a tela /admin.';

CREATE TABLE system_health_snapshots (
    id BIGSERIAL PRIMARY KEY,
    recorded_at TIMESTAMPTZ NOT NULL,
    request_count BIGINT NOT NULL,
    error_count BIGINT NOT NULL,
    total_time_ms DOUBLE PRECISION NOT NULL
);

CREATE INDEX idx_system_health_snapshots_recorded_at ON system_health_snapshots (recorded_at);

COMMENT ON TABLE system_health_snapshots IS 'Snapshot por minuto das requisições da API: base do uptime % e dos erros de 24h/7d/30d.';
COMMENT ON COLUMN system_health_snapshots.request_count IS 'Requisições da API no minuto (fora /actuator).';
COMMENT ON COLUMN system_health_snapshots.error_count IS 'Respostas 5xx no minuto (fora /actuator).';
COMMENT ON COLUMN system_health_snapshots.total_time_ms IS 'Soma dos tempos de resposta do minuto, em ms.';
