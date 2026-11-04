-- V24: Add version to accounts (retro do Epic 4, X1 - edição concorrente de conta)
-- Controle de versão otimista: toda alteração da conta (edição, transferência, reconciliação, remoção e
-- restauração) incrementa a versão. O PUT da edição envia a versão lida ao abrir o formulário e recebe 409
-- quando ela diverge, para não desfazer uma transferência ou reconciliação feita em outra tela.

ALTER TABLE accounts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN accounts.version IS 'Versão para controle otimista (@Version). Incrementada a cada alteração da conta.';
