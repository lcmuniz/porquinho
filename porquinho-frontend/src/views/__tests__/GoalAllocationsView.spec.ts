import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub, type VueWrapper } from '@vue/test-utils'
import GoalAllocationsView from '../GoalAllocationsView.vue'
import type { Goal } from '@/types/goal'

const mockList = vi.fn()
const mockSummary = vi.fn()
const mockAllocations = vi.fn()
const mockPush = vi.fn()

vi.mock('@/services/goalService', () => ({
  listGoals: () => mockList(),
  getGoalsSummary: () => mockSummary(),
  updateGoalAllocations: (...a: unknown[]) => mockAllocations(...a),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRouter: () => ({ push: (...a: unknown[]) => mockPush(...a) }),
}))

function goal(id: string, name: string, extra: Partial<Goal> = {}): Goal {
  return {
    id,
    name,
    description: null,
    targetAmount: 25000,
    currentAmount: 0,
    targetDate: null,
    monthlyAllocation: null,
    completed: false,
    createdAt: '2026-11-13T12:00:00Z',
    ...extra,
  }
}

let wrapper: VueWrapper | null = null

async function mountView() {
  wrapper = mount(GoalAllocationsView, {
    attachTo: document.body,
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
  await flushPromises()
  return wrapper
}

/** Intl usa espaço não separável depois de "R$"; normaliza para comparar. */
const plain = (s: string) => s.replace(/\s/g, ' ')
const row = (id: string, testid: string) =>
  wrapper!.get(`[data-testid="allocation-${id}"] [data-testid="${testid}"]`)
const text = (testid: string) => plain(wrapper!.get(`[data-testid="${testid}"]`).text())

async function type(id: string, value: string) {
  await row(id, 'allocation-input').setValue(value)
  await flushPromises()
}

async function save() {
  await wrapper!.get('[data-testid="allocations-form"]').trigger('submit')
  await flushPromises()
}

describe('GoalAllocationsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-13T13:00:00Z'))
    mockList.mockResolvedValue([
      goal('a', 'Viagem', { monthlyAllocation: 500 }),
      goal('b', 'Carro', { monthlyAllocation: 200 }),
    ])
    mockSummary.mockResolvedValue({ totalAvailable: 10000 })
    mockPush.mockResolvedValue(undefined)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('mostra nome, antes, campo preenchido e os totais', async () => {
    await mountView()
    expect(wrapper!.get('h1').text()).toBe('Ajustar alocações')
    expect(row('a', 'allocation-before').element.closest('li')!.textContent).toContain('Viagem')
    expect(plain(row('a', 'allocation-before').text())).toBe('Antes: R$ 500,00/mês')
    expect((row('a', 'allocation-input').element as HTMLInputElement).value).toBe('500,00')
    expect(text('total-before')).toBe('Total antes: R$ 700,00/mês')
    expect(text('total-after')).toBe('Total depois: R$ 700,00/mês')
  })

  it('redistribui: manda só as metas alteradas e volta para /metas com o estado de sucesso', async () => {
    mockList.mockResolvedValue([
      goal('a', 'Viagem', { monthlyAllocation: 500 }),
      goal('b', 'Carro', { monthlyAllocation: 200 }),
      goal('c', 'Casa', { monthlyAllocation: 50 }),
    ])
    mockAllocations.mockResolvedValue([])
    await mountView()
    await type('a', '300')
    await type('b', '400,00')
    await save()
    expect(mockAllocations).toHaveBeenCalledWith([
      { goalId: 'a', monthlyAllocation: 300 },
      { goalId: 'b', monthlyAllocation: 400 },
    ])
    expect(mockPush).toHaveBeenCalledWith({ name: 'goals', state: { allocationsUpdated: true } })
  })

  it('prévia: o valor digitado muda a projeção e o total depois, sem salvar; o total antes fica', async () => {
    mockList.mockResolvedValue([
      goal('a', 'Viagem', { targetAmount: 1000, monthlyAllocation: 100, targetDate: '2027-07-13' }),
    ])
    await mountView()
    expect(plain(row('a', 'allocation-projection').text())).toBe(
      'Projeção de conclusão: setembro de 2027',
    )
    await type('a', '125')
    expect(plain(row('a', 'allocation-projection').text())).toBe(
      'Projeção de conclusão: julho de 2027',
    )
    expect(text('total-before')).toBe('Total antes: R$ 100,00/mês')
    expect(text('total-after')).toBe('Total depois: R$ 125,00/mês')
    expect(mockAllocations).not.toHaveBeenCalled()
  })

  it('total depois acima do disponível avisa e salvar continua permitido', async () => {
    mockSummary.mockResolvedValue({ totalAvailable: 1000 })
    mockAllocations.mockResolvedValue([])
    await mountView()
    expect(wrapper!.find('[data-testid="allocations-over-available"]').exists()).toBe(false)
    await type('a', '900')
    expect(text('allocations-over-available')).toBe(
      'Suas metas somam mais do que você tem disponível',
    )
    await save()
    expect(mockAllocations).toHaveBeenCalledWith([{ goalId: 'a', monthlyAllocation: 900 }])
  })

  it('sem alteração não chama a API e avisa', async () => {
    mockList.mockResolvedValue([
      goal('a', 'Viagem', { monthlyAllocation: 500 }),
      goal('b', 'Carro', { monthlyAllocation: null }),
    ])
    await mountView()
    await type('a', '500')
    await save()
    expect(mockAllocations).not.toHaveBeenCalled()
    expect(text('allocations-status')).toBe('Nenhuma alteração para salvar')
  })

  it('meta sem alocação: 0 conta como alteração', async () => {
    mockList.mockResolvedValue([goal('a', 'Viagem', { monthlyAllocation: null })])
    mockAllocations.mockResolvedValue([])
    await mountView()
    await type('a', '0')
    await save()
    expect(mockAllocations).toHaveBeenCalledWith([{ goalId: 'a', monthlyAllocation: 0 }])
  })

  it.each([
    ['', 'Informe o aporte mensal'],
    ['-1', 'O aporte mensal não pode ser negativo'],
    ['1,005', 'Informe um valor válido, com até 2 casas decimais'],
  ])('valor "%s" mostra "%s" embaixo do campo e não salva', async (value, message) => {
    await mountView()
    await type('a', value)
    await save()
    expect(row('a', 'allocation-error').text()).toBe(message)
    expect(wrapper!.find('[data-testid="allocation-b"] [data-testid="allocation-error"]').exists()).toBe(
      false,
    )
    expect(mockAllocations).not.toHaveBeenCalled()
  })

  it('400 do backend vai para o campo da meta certa', async () => {
    mockAllocations.mockRejectedValue({
      response: {
        status: 400,
        data: { errors: { 'items[1].monthlyAllocation': 'O aporte mensal não pode ser negativo' } },
      },
    })
    await mountView()
    await type('a', '300')
    await type('b', '400')
    await save()
    expect(row('b', 'allocation-error').text()).toBe('O aporte mensal não pode ser negativo')
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('404 (meta de outro usuário) mostra a mensagem geral e fica na tela', async () => {
    mockAllocations.mockRejectedValue({
      response: { status: 404, data: { detail: 'Meta não encontrada' } },
    })
    await mountView()
    await type('a', '300')
    await save()
    expect(text('allocations-form-error')).toBe('Meta não encontrada')
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('uma meta só: a tela funciona igual', async () => {
    mockList.mockResolvedValue([goal('a', 'Viagem', { monthlyAllocation: 500 })])
    mockAllocations.mockResolvedValue([])
    await mountView()
    await type('a', '600')
    await save()
    expect(mockAllocations).toHaveBeenCalledWith([{ goalId: 'a', monthlyAllocation: 600 }])
  })

  it('falha ao carregar mostra a mensagem e "Tentar de novo" chama o GET de novo', async () => {
    mockList.mockRejectedValueOnce(new Error('rede'))
    await mountView()
    expect(wrapper!.find('[data-testid="allocations-load-failed"]').exists()).toBe(true)
    await wrapper!.get('[data-testid="retry-button"]').trigger('click')
    await flushPromises()
    expect(mockList).toHaveBeenCalledTimes(2)
    expect(wrapper!.find('[data-testid="allocation-a"]').exists()).toBe(true)
  })

  it('lista vazia mostra o estado vazio', async () => {
    mockList.mockResolvedValue([])
    await mountView()
    expect(wrapper!.find('[data-testid="allocations-empty"]').exists()).toBe(true)
  })

  it('segundo envio com o PUT pendente não chama a API de novo', async () => {
    mockAllocations.mockReturnValue(new Promise(() => {}))
    await mountView()
    await type('a', '300')
    await save()
    await save()
    expect(mockAllocations).toHaveBeenCalledTimes(1)
  })

  it('erro de validação leva o foco ao primeiro campo inválido, na ordem das metas', async () => {
    await mountView()
    await type('b', '-1')
    await type('a', '')
    await save()
    expect(document.activeElement).toBe(row('a', 'allocation-input').element)
  })

  it('400 do backend leva o foco ao campo da meta', async () => {
    mockAllocations.mockRejectedValue({
      response: {
        status: 400,
        data: { errors: { 'items[0].monthlyAllocation': 'O aporte mensal não pode ser negativo' } },
      },
    })
    await mountView()
    await type('b', '400')
    await save()
    expect(document.activeElement).toBe(row('b', 'allocation-input').element)
  })
})
