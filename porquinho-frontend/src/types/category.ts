/** Categoria (envelope) do usuário (Story 3.4). `parentCategoryId` é nulo nas categorias de primeiro nível. */
export interface Category {
  id: string
  categoryName: string
  parentCategoryId: string | null
  colorHex: string
  /** Nome do ícone Lucide em kebab-case, ex.: 'heart-pulse'. */
  iconName: string
  systemDefault: boolean
  createdAt: string
}

/** Uma categoria sugerida no passo 4 do onboarding (catálogo fixo do backend). */
export interface CategorySuggestion {
  /** Chave estável enviada no POST, ex.: 'housing'. */
  key: string
  name: string
  iconName: string
  colorHex: string
}

/** Grupo de sugestões (Essenciais, Variáveis, Financeiro); vira a categoria-pai das escolhidas. */
export interface CategorySuggestionGroup extends CategorySuggestion {
  suggestions: CategorySuggestion[]
}

/** Story 3.5: cores (#RRGGBB) e ícones Lucide permitidos para criar ou editar uma categoria. */
export interface CategoryOptions {
  colors: string[]
  icons: string[]
}

/**
 * Story 3.5: corpo do POST /api/v1/categories (categoria nova dentro de um grupo). Story 6.1: cor e ícone
 * opcionais; sem eles o backend usa a cor do grupo e o ícone `folder`. Story 6.2: sem `parentId` cria um grupo.
 */
export interface CreateCategoryInput {
  name: string
  colorHex?: string
  iconName?: string
  parentId?: string
}

/** Story 3.5: corpo do PATCH /api/v1/categories/{id}; só os campos enviados mudam. Story 6.2: `parentId` move. */
export interface UpdateCategoryInput {
  name?: string
  colorHex?: string
  iconName?: string
  parentId?: string
}
