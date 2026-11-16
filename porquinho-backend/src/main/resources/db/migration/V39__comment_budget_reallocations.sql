-- V39: comentário da tabela budget_reallocations corrige o cabeçalho da V33 (retro Epic 6 B4)
COMMENT ON TABLE budget_reallocations IS 'Registro de auditoria de cada dinheiro movido entre categorias filhas no mês. Nenhuma tela lê esta tabela hoje: o histórico da 6.8 lê só budget_allocations (listagem de realocações em aberto, retro do Epic 6 S5).';
