import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ReallocateForm from '../ReallocateForm.vue'
import type { Budget } from '@/types/budget'

const mockReallocate = vi.fn()

vi.mock('@/services/budgetService', () => ({
  reallocateBudget: (...a: unknown[]) => mockReallocate(...a),
}))

const OPTIONS = [
  { id: 'c1', name: 'Lazer', remaining: 200 },
  { id: 'c2', name: 'Mercado', remaining: -10 },
  { id: 'c3', name: 'Farmácia', remaining: null },
]

const updated: Budget = { month: '2026-11', version: 'v2', totalAvailable: 1000, totalAllocated: 400, categories: [] }

function mountForm() {
  return mount(ReallocateForm, { props: { month: '2026-11', options: OPTIONS }, attachTo: document.body })
}

type Wrapper = ReturnType<typeof mountForm>

const text = (w: Wrapper, testId: string) => w.get(`[data-testid="${testId}"]`).text().replace(/\s/g, ' ')

async function fill(w: Wrapper, from: string, to: string, amount: string) {
  await w.get('[data-testid="reallocate-from-select"]').setValue(from)
  await w.get('[data-testid="reallocate-to-select"]').setValue(to)
  await w.get('[data-testid="reallocate-amount-input"]').setValue(amount)
  await w.get('[data-testid="reallocate-form"]').trigger('submit')
  await flushPromises()
}

describe('ReallocateForm', () => {
  beforeEach(() => {
    mockReallocate.mockReset().mockResolvedValue(updated)
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('a origem lista só categorias com restante > 0 e cada opção mostra o restante', () => {
    const w = mountForm()
    const from = w.findAll('[data-testid="reallocate-from-select"] option').map((o) => o.text().replace(/\s/g, ' '))
    expect(from).toEqual(['Lazer — restante R$ 200,00'])
    const to = w.findAll('[data-testid="reallocate-to-select"] option').map((o) => o.text())
    expect(to).toHaveLength(3)
    expect(to[1]!.replace(/\s/g, ' ')).toBe('Mercado — restante -R$ 10,00')
    expect(to[2]).toBe('Farmácia — sem orçamento')
    expect((w.get('[data-testid="reallocate-to-select"]').element as HTMLSelectElement).value).toBe('c2')
  })

  it('envia e emite o mês recalculado com a mensagem de sucesso', async () => {
    const w = mountForm()
    await fill(w, 'c1', 'c3', '50,00')

    expect(mockReallocate).toHaveBeenCalledWith('2026-11', { fromCategoryId: 'c1', toCategoryId: 'c3', amount: 50 })
    const [data, message] = w.emitted('moved')![0] as [Budget, string]
    expect(data).toBe(updated)
    expect(message.replace(/\s/g, ' ')).toBe('R$ 50,00 movido de Lazer para Farmácia')
  })

  it.each([
    ['c1', 'c1', '10', 'reallocate-to-error', 'Escolha categorias diferentes'],
    ['c1', 'c3', '', 'reallocate-amount-error', 'Informe o valor'],
    ['c1', 'c3', '0', 'reallocate-amount-error', 'O valor precisa ser maior que zero'],
    ['c1', 'c3', '1,001', 'reallocate-amount-error', 'Digite o valor no formato 1.234,56 (até 2 casas depois da vírgula)'],
  ])('valida localmente sem chamar o backend (%s → %s, "%s")', async (from, to, amount, testId, message) => {
    const w = mountForm()
    await fill(w, from, to, amount)
    expect(text(w, testId)).toBe(message)
    expect(mockReallocate).not.toHaveBeenCalled()
  })

  it('400 do backend vai para o pai com o detail e mantém o que foi digitado', async () => {
    mockReallocate.mockRejectedValue({
      response: { status: 400, data: { detail: 'O valor passa do restante de Lazer (R$ 200,00)' } },
    })
    const w = mountForm()
    await fill(w, 'c1', 'c3', '200,01')

    expect(w.emitted('failed')![0]).toEqual(['O valor passa do restante de Lazer (R$ 200,00)'])
    expect((w.get('[data-testid="reallocate-amount-input"]').element as HTMLInputElement).value).toBe('200,01')
    expect(w.emitted('moved')).toBeUndefined()
  })

  it('400 de campo aparece no campo', async () => {
    mockReallocate.mockRejectedValue({
      response: { status: 400, data: { detail: 'x', errors: { amount: 'Informe o valor com até 2 casas decimais' } } },
    })
    const w = mountForm()
    await fill(w, 'c1', 'c3', '10')
    expect(text(w, 'reallocate-amount-error')).toBe('Informe o valor com até 2 casas decimais')
    expect(w.emitted('failed')).toBeUndefined()
  })
})
