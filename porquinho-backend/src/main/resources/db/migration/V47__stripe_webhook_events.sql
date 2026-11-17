-- V47: eventos do Stripe já processados (pré-10.4, grupo A). Um evento reentregue (retry ou reenvio manual pelo
-- Dashboard) devolve 200 sem efeito. Gravado só depois do processamento com sucesso. Sem dado pessoal: fora do
-- purge e do export LGPD. Não é limpa: o volume é pequeno.

CREATE TABLE stripe_webhook_events (
    event_id VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE stripe_webhook_events IS
    'Eventos do Stripe já processados (dedup do webhook). Só id, tipo e horário; nenhum dado pessoal.';
