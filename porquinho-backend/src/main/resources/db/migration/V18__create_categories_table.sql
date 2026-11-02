-- V18: Create categories table (Story 3.4 - categorias de gastos sugeridas no onboarding)
-- Envelopes do orçamento (Epic 6) e destino da classificação (Epic 5). Hierarquia de um nível via
-- parent_category_id: os grupos sugeridos (Essenciais, Variáveis, Financeiro) são categorias-pai.
-- Soft delete via deleted_at. Sem índice parcial (H2 não suporta WHERE em índice).
-- TIMESTAMP WITH TIME ZONE (= TIMESTAMPTZ no PostgreSQL): o alias TIMESTAMPTZ não existe no H2.

CREATE TABLE categories (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,  -- DEFAULT before PRIMARY KEY: required by H2
    user_id UUID NOT NULL REFERENCES users(id),
    category_name VARCHAR(50) NOT NULL,
    parent_category_id UUID REFERENCES categories(id),
    color_hex VARCHAR(7) NOT NULL,
    icon_name VARCHAR(50) NOT NULL,
    is_system_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT categories_parent_not_self CHECK (parent_category_id IS NULL OR parent_category_id <> id),
    CONSTRAINT categories_color_hex_check CHECK (color_hex ~ '^#[0-9A-Fa-f]{6}$')
);

CREATE INDEX idx_categories_user_id ON categories(user_id);
CREATE INDEX idx_categories_parent_category_id ON categories(parent_category_id);

COMMENT ON TABLE categories IS 'Categorias (envelopes) do usuário (Story 3.4; ampliada nos Epics 5 e 6). Soft delete via deleted_at.';
COMMENT ON COLUMN categories.category_name IS 'Nome exibido, ex.: "Moradia".';
COMMENT ON COLUMN categories.parent_category_id IS 'Categoria-pai (grupo, ex.: "Essenciais"); NULL para as categorias de primeiro nível.';
COMMENT ON COLUMN categories.color_hex IS 'Cor no formato #RRGGBB, de uma paleta predefinida.';
COMMENT ON COLUMN categories.icon_name IS 'Nome do ícone Lucide, ex.: "house".';
COMMENT ON COLUMN categories.is_system_default IS 'true quando criada a partir das sugestões do sistema (onboarding).';
COMMENT ON COLUMN categories.deleted_at IS 'Preenchido quando o usuário exclui a categoria (soft delete).';
