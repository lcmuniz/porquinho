import { api } from './api'
import type {
  Category,
  CategoryOptions,
  CategorySuggestionGroup,
  CreateCategoryInput,
  UpdateCategoryInput,
} from '@/types/category'

/** Story 3.4: categorias ativas do usuário logado (pais e filhas), em ordem de nome. */
export async function listCategories(): Promise<Category[]> {
  const response = await api.get<Category[]>('/api/v1/categories')
  return response.data
}

/** Story 3.4: catálogo fixo de categorias sugeridas, em 3 grupos. */
export async function getCategorySuggestions(): Promise<CategorySuggestionGroup[]> {
  const response = await api.get<CategorySuggestionGroup[]>('/api/v1/categories/suggestions')
  return response.data
}

/** Story 3.5: paleta de cores e lista de ícones permitidos. */
export async function getCategoryOptions(): Promise<CategoryOptions> {
  const response = await api.get<CategoryOptions>('/api/v1/categories/options')
  return response.data
}

/** Story 3.5: cria uma categoria dentro de um grupo (201). 400 inválida; 409 nome repetido. */
export async function createCategory(input: CreateCategoryInput): Promise<Category> {
  const response = await api.post<Category>('/api/v1/categories', input)
  return response.data
}

/** Story 3.5: altera nome, cor e/ou ícone. 400 inválido; 404 não encontrada; 409 nome repetido. */
export async function updateCategory(id: string, input: UpdateCategoryInput): Promise<Category> {
  const response = await api.patch<Category>(`/api/v1/categories/${id}`, input)
  return response.data
}

/** Retro Epic 6 A4: quantas transações ativas a categoria filha tem (decide se a exclusão pede destino). */
export async function getCategoryUsage(id: string): Promise<{ transactionCount: number }> {
  const response = await api.get<{ transactionCount: number }>(`/api/v1/categories/${id}/usage`)
  return response.data
}

/**
 * Story 3.5: exclui (soft delete). 409 na última categoria, num grupo com categorias ou numa filha com transações
 * sem destino. Retro Epic 6 A4: com `targetCategoryId`, transações, orçamento e padrões vão para o destino.
 */
export async function deleteCategory(id: string, targetCategoryId?: string): Promise<void> {
  await api.delete(`/api/v1/categories/${id}`, targetCategoryId ? { params: { targetCategoryId } } : undefined)
}
