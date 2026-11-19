-- V52: início da recusa permanente do aviso de retenção (decisão de 2026-11-19)
-- Quando o SMTP recusa o endereço com 5xx (caixa inexistente, supressão) por 14 dias seguidos, a retenção apaga a
-- conta mesmo sem aviso entregue. Esta coluna guarda a primeira recusa permanente; um envio aceito ou a reativação
-- do plano zeram. Falha temporária (sem SMTP, 4xx, timeout, conexão) não grava nada aqui.
ALTER TABLE subscriptions
    ADD COLUMN retention_refused_since TIMESTAMPTZ NULL;
