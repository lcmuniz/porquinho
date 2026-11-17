-- V48: Add version to credit_cards (triagem 16/11, grupo B - edição concorrente de cartão)
-- Controle de versão otimista, como nas contas (V24): toda alteração do cartão incrementa a versão. O PUT da edição
-- envia a versão lida ao abrir o formulário e recebe 409 quando ela diverge (a última gravação não vence calada).

ALTER TABLE credit_cards ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN credit_cards.version IS 'Versão para controle otimista (@Version). Incrementada a cada alteração do cartão.';
