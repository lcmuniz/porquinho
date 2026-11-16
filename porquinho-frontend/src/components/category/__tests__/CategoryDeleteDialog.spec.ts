import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import CategoryDeleteDialog from '../CategoryDeleteDialog.vue'
import type { Category } from '@/types/category'

const mockDelete = vi.fn()
const mockUsage = vi.fn()

vi.mock('@/services/categoryService', () => ({
  deleteCategory: (...a: unknown[]) => mockDelete(...a),
  getCategoryUsage: (...a: unknown[]) => mockUsage(...a),
}))

function category(id: string, name: string, parentCategoryId: string | null): Category {
  return {
    id,
    categoryName: name,
    parentCategoryId,
    colorHex: '#2563EB',
    iconName: 'house',
    systemDefault: false,
    createdAt: '2026-11-09T12:00:00Z',
  }
}

const GROUP = category('g1', 'Essenciais', null)
const FOOD = category('c1', 'Mercado', 'g1')
const HOUSE = category('c2', 'Moradia', 'g1')
const LIST = [GROUP, FOOD, HOUSE]

let wrapper: VueWrapper | null = null

async function open(target: Category) {
  wrapper = mount(CategoryDeleteDialog, {
    attachTo: document.body,
    props: { category: target, categories: LIST },
  })
  await flushPromises()
  return wrapper
}

const inDialog = <T extends Element = HTMLElement>(testId: string) =>
  document.body.querySelector<T>(`[data-testid="${testId}"]`)
const confirmButton = () => inDialog<HTMLButtonElement>('confirm-delete-button')!

async function chooseTarget(id: string) {
  const select = inDialog<HTMLSelectElement>('delete-target')!
  select.value = id
  select.dispatchEvent(new Event('change'))
  await flushPromises()
}

describe('CategoryDeleteDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockDelete.mockResolvedValue(undefined)
    mockUsage.mockResolvedValue({ transactionCount: 0, allocatedAmount: 0 })
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
  })

  it('sem transações nem orçamento aberto exclui sem destino', async () => {
    await open(FOOD)

    expect(mockUsage).toHaveBeenCalledWith('c1')
    expect(inDialog('delete-target')).toBeNull()
    confirmButton().click()
    await flushPromises()

    expect(mockDelete).toHaveBeenCalledWith('c1', undefined)
    expect(wrapper!.emitted('deleted')?.[0]).toEqual([FOOD])
  })

  it('com transações pede o destino, sem oferecer a própria categoria', async () => {
    mockUsage.mockResolvedValue({ transactionCount: 3, allocatedAmount: 0 })
    await open(FOOD)

    expect(document.body.textContent).toContain('3 transações vão para:')
    const options = [...inDialog<HTMLSelectElement>('delete-target')!.options].map((o) => o.value)
    expect(options).toEqual(['', 'c2'])
    expect(confirmButton().disabled).toBe(true)
    await chooseTarget('c2')
    confirmButton().click()
    await flushPromises()

    expect(mockDelete).toHaveBeenCalledWith('c1', 'c2')
  })

  it('só com orçamento aberto também pede o destino', async () => {
    mockUsage.mockResolvedValue({ transactionCount: 0, allocatedAmount: 300 })
    await open(FOOD)

    expect(inDialog('delete-target')).not.toBeNull()
    expect(document.body.textContent).toMatch(/R\$\s300,00 de orçamento vai para:/)
    expect(confirmButton().disabled).toBe(true)
  })

  it('com transações e orçamento o rótulo cita os dois', async () => {
    mockUsage.mockResolvedValue({ transactionCount: 1, allocatedAmount: 300 })
    await open(FOOD)

    expect(document.body.textContent).toMatch(/1 transação e R\$\s300,00 de orçamento vão para:/)
  })

  it('Excluir fica desabilitado enquanto o uso carrega', async () => {
    mockUsage.mockReturnValue(new Promise(() => {}))
    await open(FOOD)

    expect(confirmButton().disabled).toBe(true)
  })

  it('grupo vazio não busca o uso e exclui direto', async () => {
    await open(GROUP)

    expect(mockUsage).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('O grupo vazio sai da sua lista.')
    confirmButton().click()
    await flushPromises()

    expect(mockDelete).toHaveBeenCalledWith('g1', undefined)
    expect(wrapper!.emitted('deleted')?.[0]).toEqual([GROUP])
  })

  it('erro da API aparece no diálogo, que continua aberto', async () => {
    mockDelete.mockRejectedValue({ response: { status: 409, data: { detail: 'Mantenha ao menos uma categoria' } } })
    await open(FOOD)

    confirmButton().click()
    await flushPromises()

    expect(inDialog('delete-error')!.textContent).toContain('Mantenha ao menos uma categoria')
    expect(inDialog('delete-dialog')).not.toBeNull()
    expect(wrapper!.emitted('deleted')).toBeUndefined()
    expect(wrapper!.emitted('close')).toBeUndefined()
  })

  it('404 conta como excluída', async () => {
    mockDelete.mockRejectedValue({ response: { status: 404, data: { detail: 'Categoria não encontrada' } } })
    await open(FOOD)

    confirmButton().click()
    await flushPromises()

    expect(wrapper!.emitted('deleted')?.[0]).toEqual([FOOD])
    expect(inDialog('delete-error')).toBeNull()
  })

  it('Cancelar emite close sem excluir', async () => {
    await open(FOOD)

    inDialog<HTMLButtonElement>('cancel-delete-button')!.click()
    await flushPromises()

    expect(wrapper!.emitted('close')).toHaveLength(1)
    expect(mockDelete).not.toHaveBeenCalled()
  })
})
