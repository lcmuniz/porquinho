import { api } from './api'
import type { Category, CategorySuggestionGroup } from '@/types/category'

/** Story 3.4: categorias ativas do usuário logado (pais e filhas), da mais antiga para a mais nova. */
export async function listCategories(): Promise<Category[]> {
  const response = await api.get<Category[]>('/api/v1/categories')
  return response.data
}

/** Story 3.4: catálogo fixo de categorias sugeridas, em 3 grupos. */
export async function getCategorySuggestions(): Promise<CategorySuggestionGroup[]> {
  const response = await api.get<CategorySuggestionGroup[]>('/api/v1/categories/suggestions')
  return response.data
}
