/** Story 6.4: mesma regra em todo o épico (restante < 0; até 20% do alocado; acima de 20%). */
export type BudgetStatus = 'HEALTHY' | 'LOW' | 'OVERSPENT'

/**
 * Story 6.3/6.4: orçamento de uma categoria filha no mês. `allocated` nulo = sem orçamento (e então `remaining` e
 * `status` também são nulos); `committed` nulo = mês fechado.
 */
export interface CategoryBudget {
  categoryId: string
  allocated: number | null
  spent: number
  committed: number | null
  remaining: number | null
  status: BudgetStatus | null
}

/** Story 6.3: resposta de GET/PUT /api/v1/budgets/{AAAA-MM}. `version` volta no PUT (409 se o mês mudou). */
export interface Budget {
  month: string
  version: string
  totalAvailable: number
  totalAllocated: number
  categories: CategoryBudget[]
}

/** Story 6.3: uma alocação do PUT; categoria fora da lista fica sem orçamento. */
export interface BudgetAllocationInput {
  categoryId: string
  amount: number
}

/** Story 6.5: corpo do POST /api/v1/budgets/{AAAA-MM}/reallocations. */
export interface BudgetReallocationInput {
  fromCategoryId: string
  toCategoryId: string
  amount: number
}

/** Story 6.8: um mês com orçamento no histórico da categoria (AAAA-MM). */
export interface CategoryBudgetMonth {
  month: string
  allocated: number
  spent: number
  remaining: number
  status: BudgetStatus
}

/** Story 6.8: resposta de GET /api/v1/budgets/categories/{id}/history, do mês mais antigo ao mais novo. */
export interface CategoryBudgetHistory {
  categoryId: string
  categoryName: string
  months: CategoryBudgetMonth[]
}
