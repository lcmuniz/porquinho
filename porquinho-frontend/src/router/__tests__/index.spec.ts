import { describe, it, expect } from 'vitest'
import router from '../index'

describe('router', () => {
  it('resolve a revisão da importação, com autenticação (Story 5.9)', () => {
    const route = router.resolve('/importar/i1/revisao')

    expect(route.name).toBe('import-review')
    expect(route.params.id).toBe('i1')
    expect(route.meta.requiresAuth).toBe(true)
  })

  it('resolve as categorias, com autenticação (Story 6.1)', () => {
    const route = router.resolve('/categorias')

    expect(route.name).toBe('categories')
    expect(route.meta.requiresAuth).toBe(true)
  })

  it('resolve as metas, com autenticação (Story 8.1)', () => {
    const route = router.resolve('/metas')

    expect(route.name).toBe('goals')
    expect(route.meta.requiresAuth).toBe(true)
  })

  it('resolve o ajuste de alocações das metas, com autenticação (Story 8.5)', () => {
    const route = router.resolve('/metas/ajustar')

    expect(route.name).toBe('goal-allocations')
    expect(route.meta.requiresAuth).toBe(true)
  })

  it('resolve o histórico da meta, com autenticação (Story 8.8)', () => {
    const route = router.resolve('/metas/g1/historico')

    expect(route.name).toBe('goal-history')
    expect(route.params.id).toBe('g1')
    expect(route.meta.requiresAuth).toBe(true)
  })

  it('resolve o orçamento, com autenticação (Story 6.3)', () => {
    const route = router.resolve('/orcamento')

    expect(route.name).toBe('budget')
    expect(route.meta.requiresAuth).toBe(true)
  })

  it('resolve o planejado × real, com autenticação (Story 6.9)', () => {
    const route = router.resolve('/orcamento/comparativo')

    expect(route.name).toBe('budget-comparison')
    expect(route.meta.requiresAuth).toBe(true)
  })
})
