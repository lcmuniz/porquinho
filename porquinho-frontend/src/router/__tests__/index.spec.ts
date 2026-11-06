import { describe, it, expect } from 'vitest'
import router from '../index'

describe('router', () => {
  it('resolve a revisão da importação, com autenticação (Story 5.9)', () => {
    const route = router.resolve('/importar/i1/revisao')

    expect(route.name).toBe('import-review')
    expect(route.params.id).toBe('i1')
    expect(route.meta.requiresAuth).toBe(true)
  })
})
