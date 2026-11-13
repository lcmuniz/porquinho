import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub, type VueWrapper } from '@vue/test-utils'
import GoalsView from '../GoalsView.vue'
import type { Goal } from '@/types/goal'

const mockList = vi.fn()
const mockCreate = vi.fn()

vi.mock('@/services/goalService', () => ({
  listGoals: () => mockList(),
  createGoal: (...a: unknown[]) => mockCreate(...a),
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
  wrapper = mount(GoalsView, { attachTo: document.body, global: { stubs: { RouterLink: RouterLinkStub } } })
  await flushPromises()
  return wrapper
}

const inDialog = <T extends Element = HTMLElement>(testId: string) =>
  document.body.querySelector<T>(`[data-testid="${testId}"]`)

async function type(testId: string, value: string) {
  const el = inDialog<HTMLInputElement>(testId)!
  el.value = value
  el.dispatchEvent(new Event('input'))
  await flushPromises()
}

async function openDialog() {
  await wrapper!.get('[data-testid="add-goal"]').trigger('click')
  await flushPromises()
}

async function submit() {
  inDialog<HTMLFormElement>('goal-form')!.dispatchEvent(new Event('submit'))
  await flushPromises()
}

/** Intl usa espaço não separável depois de "R$"; normaliza para comparar. */
const plain = (s: string) => s.replace(/\s/g, ' ')

describe('GoalsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Só o Date é falso: "hoje" fixo em 13/11/2026 (10:00 em São Paulo)
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-13T13:00:00Z'))
    mockList.mockResolvedValue([goal('g1', 'Reserva de Emergência')])
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('mostra o card com nome, descrição, barra em 0%, data-alvo e aporte', async () => {
    mockList.mockResolvedValue([
      goal('g1', 'Viagem', {
        description: 'Praia em julho',
        targetAmount: 12000,
        targetDate: '2027-11-13',
        monthlyAllocation: 1000,
      }),
    ])
    await mountView()
    const card = wrapper!.get('[data-testid="goal-g1"]')
    expect(card.text()).toContain('Viagem')
    expect(card.text()).toContain('Praia em julho')
    expect(plain(card.get('[data-testid="goal-progress-text"]').text())).toBe('R$ 0,00 de R$ 12.000,00 (0%)')
    expect(card.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('0')
    expect(card.text()).toContain('Data-alvo: 13/11/2027')
    expect(plain(card.text())).toContain('Aporte mensal: R$ 1.000,00')
  })

  it('card sem data e sem aporte não mostra essas linhas', async () => {
    await mountView()
    const text = wrapper!.get('[data-testid="goal-g1"]').text()
    expect(text).not.toContain('Data-alvo')
    expect(text).not.toContain('Aporte mensal')
  })

  it('lista vazia mostra o estado vazio e o botão Criar meta', async () => {
    mockList.mockResolvedValue([])
    await mountView()
    expect(wrapper!.find('[data-testid="goals-empty"]').exists()).toBe(true)
    expect(wrapper!.get('[data-testid="add-goal"]').attributes('disabled')).toBeUndefined()
  })

  it('falha ao carregar mostra a mensagem e "Tentar de novo" chama o GET de novo', async () => {
    mockList.mockRejectedValueOnce(new Error('rede'))
    await mountView()
    expect(wrapper!.get('[data-testid="goals-load-failed"]').text()).toContain('Não foi possível carregar suas metas.')
    await wrapper!.get('[data-testid="retry-button"]').trigger('click')
    await flushPromises()
    expect(mockList).toHaveBeenCalledTimes(2)
    expect(wrapper!.find('[data-testid="goal-g1"]').exists()).toBe(true)
  })

  it('cria outra meta: fecha o diálogo, a nova aparece por último e "Meta criada" é anunciada', async () => {
    mockCreate.mockResolvedValue(goal('g2', 'Viagem', { description: 'Praia', targetAmount: 12000 }))
    await mountView()
    await openDialog()
    await type('goal-name-input', ' Viagem ')
    await type('goal-target-amount-input', '12.000,00')
    await type('goal-description-input', '  Praia  ')
    await submit()

    expect(mockCreate).toHaveBeenCalledWith({
      name: 'Viagem',
      targetAmount: 12000,
      targetDate: null,
      monthlyAllocation: null,
      description: 'Praia',
    })
    expect(inDialog('goal-dialog')).toBeNull()
    const ids = wrapper!.findAll('li').map((li) => li.attributes('data-testid'))
    expect(ids).toEqual(['goal-g1', 'goal-g2'])
    expect(wrapper!.get('[role="status"]').text()).toBe('Meta criada')
    expect(mockList).toHaveBeenCalledTimes(1)
  })

  it('enquanto salva, Cancelar fica desabilitado e update:open(false) não fecha', async () => {
    let resolveCreate: (g: Goal) => void = () => {}
    mockCreate.mockReturnValue(new Promise<Goal>((resolve) => (resolveCreate = resolve)))
    await mountView()
    await openDialog()
    await type('goal-name-input', 'Viagem')
    await type('goal-target-amount-input', '100')
    await submit()

    expect(inDialog<HTMLButtonElement>('goal-cancel')!.disabled).toBe(true)
    expect(inDialog('goal-save')!.textContent?.trim()).toBe('Salvando...')
    wrapper!.findComponent({ name: 'Dialog' }).vm.$emit('update:open', false)
    await flushPromises()
    expect(inDialog('goal-dialog')).not.toBeNull()

    resolveCreate(goal('g2', 'Viagem'))
    await flushPromises()
    expect(inDialog('goal-dialog')).toBeNull()
  })

  it('descrição em branco vai como null', async () => {
    mockCreate.mockResolvedValue(goal('g2', 'Viagem'))
    await mountView()
    await openDialog()
    await type('goal-name-input', 'Viagem')
    await type('goal-target-amount-input', '100')
    await type('goal-description-input', '   ')
    await submit()
    expect(mockCreate).toHaveBeenCalledWith(expect.objectContaining({ description: null }))
  })

  it('sugere o aporte pela data até o usuário editá-lo; depois mudar a data não sobrescreve', async () => {
    await mountView()
    await openDialog()
    await type('goal-target-amount-input', '12.000,00')
    await type('goal-target-date-input', '2027-11-13')
    expect(inDialog<HTMLInputElement>('goal-monthly-allocation-input')!.value).toBe('1.000,00')

    await type('goal-monthly-allocation-input', '500,00')
    await type('goal-target-date-input', '2027-05-13')
    expect(inDialog<HTMLInputElement>('goal-monthly-allocation-input')!.value).toBe('500,00')
  })

  it('data no passado não envia e mostra a mensagem no campo', async () => {
    await mountView()
    await openDialog()
    await type('goal-name-input', 'Viagem')
    await type('goal-target-amount-input', '100')
    await type('goal-target-date-input', '2026-11-12')
    await submit()
    expect(mockCreate).not.toHaveBeenCalled()
    expect(inDialog('goal-target-date-error')!.textContent?.trim()).toBe('Escolha uma data de hoje em diante')
  })

  it('400 com errors.description aparece embaixo do campo', async () => {
    mockCreate.mockRejectedValue({
      response: { status: 400, data: { errors: { description: 'Use no máximo 500 caracteres na descrição' } } },
    })
    await mountView()
    await openDialog()
    await type('goal-name-input', 'Viagem')
    await type('goal-target-amount-input', '100')
    await submit()
    expect(inDialog('goal-description-error')!.textContent?.trim()).toBe('Use no máximo 500 caracteres na descrição')
    expect(inDialog('goal-dialog')).not.toBeNull()
  })

  it('falha ao salvar (500 / rede) mantém o diálogo aberto com o erro geral', async () => {
    mockCreate.mockRejectedValue(new Error('rede'))
    await mountView()
    await openDialog()
    await type('goal-name-input', 'Viagem')
    await type('goal-target-amount-input', '100')
    await submit()
    const error = inDialog('goal-form-error')!
    expect(error.getAttribute('role')).toBe('alert')
    expect(error.textContent).toContain('Não foi possível criar a meta')
    expect(inDialog('goal-dialog')).not.toBeNull()
    expect(wrapper!.findAll('li')).toHaveLength(1)
  })
})
