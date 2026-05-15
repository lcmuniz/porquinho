
## Deferred from: code review of 1-7-request-permanent-account-deletion-lgpd-compliance (2026-05-15)

- @Transactional(propagation=NEVER) em requestDeletion — defesa futura caso codigo externo tente envolver a chamada em outra transacao. Atualmente o controller nao envolve, entao nao ha risco real.
- JWT blacklist TTL pode ser 0 ou negativo se token esta no ultimo segundo de validade — janela de exposicao <1min, atacante precisaria de timing perfeito.
- DataExport.findAllByUserId sem stream/paginacao — risco teorico de OOM se retencao for aumentada para anos. Atualmente 24h de retencao mantem lista pequena.
- Rate limit so por userId permite que um atacante com credencials valida bloqueie o usuario legitimo por 24h. Mitigacao real exige rate limit adicional por IP — fora do escopo do MVP.
