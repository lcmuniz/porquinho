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
