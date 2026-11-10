/** Story 6.3: orçamento de uma categoria filha no mês. `allocated` nulo = sem orçamento; `committed` nulo = mês fechado. */
export interface CategoryBudget {
  categoryId: string
  allocated: number | null
  spent: number
  committed: number | null
}

/** Story 6.3: resposta de GET/PUT /api/v1/budgets/{AAAA-MM}. */
export interface Budget {
  month: string
  totalAvailable: number
  totalAllocated: number
  categories: CategoryBudget[]
}

/** Story 6.3: uma alocação do PUT; categoria fora da lista fica sem orçamento. */
export interface BudgetAllocationInput {
  categoryId: string
  amount: number
}
