-- V44: budget_reallocations fica só como auditoria; a listagem de realocações saiu do Epic 6 (retro Epic 6 S5)
COMMENT ON TABLE budget_reallocations IS 'Registro de auditoria de cada dinheiro movido entre categorias filhas no mês. Nenhuma tela lê esta tabela: a listagem de realocações foi cortada do Epic 6 (retro S5). Sai no export LGPD e é apagada no purge.';
