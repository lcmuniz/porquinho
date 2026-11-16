-- V43: consentimento LGPD guardado junto da exclusão (retro Epic 1 A4, NFR25)
-- O purge apaga a linha de users e, com ela, lgpd_consent_at. account_deletions sobrevive ao purge: com esta coluna,
-- a linha registra quando o titular consentiu, quando pediu a exclusão (requested_at) e quando ela ocorreu (purged_at).
ALTER TABLE account_deletions ADD COLUMN lgpd_consent_at TIMESTAMP NULL;

-- Pedidos ainda pendentes: o usuário (soft-deleted) ainda está em users
UPDATE account_deletions d SET lgpd_consent_at = u.lgpd_consent_at
FROM users u WHERE u.id = d.user_id AND d.lgpd_consent_at IS NULL;

COMMENT ON COLUMN account_deletions.lgpd_consent_at IS
    'Cópia de users.lgpd_consent_at feita no pedido de exclusão; sobrevive ao purge (NFR25).';
