import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub, type VueWrapper } from '@vue/test-utils'
import GoalsView from '../GoalsView.vue'
import type { Goal } from '@/types/goal'

const mockList = vi.fn()
const mockCreate = vi.fn()
const mockAllocation = vi.fn()
const mockContribute = vi.fn()
const mockSummary = vi.fn()
const mockProgress = vi.fn()

vi.mock('@/services/goalService', () => ({
  listGoals: () => mockList(),
  createGoal: (...a: unknown[]) => mockCreate(...a),
  updateGoalAllocation: (...a: unknown[]) => mockAllocation(...a),
  contributeToGoal: (...a: unknown[]) => mockContribute(...a),
  getGoalsSummary: () => mockSummary(),
  getGoalProgress: (...a: unknown[]) => mockProgress(...a),
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
    template: null,
    ...extra,
  }
}

let wrapper: VueWrapper | null = null

async function mountView() {
  wrapper = mount(GoalsView, {
    attachTo: document.body,
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
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
    mockSummary.mockResolvedValue({ totalAvailable: 10000 })
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
    expect(plain(card.get('[data-testid="goal-progress-text"]').text())).toBe(
      'R$ 0,00 de R$ 12.000,00 (0%)',
    )
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
    expect(wrapper!.get('[data-testid="goals-load-failed"]').text()).toContain(
      'Não foi possível carregar suas metas.',
    )
    await wrapper!.get('[data-testid="retry-button"]').trigger('click')
    await flushPromises()
    expect(mockList).toHaveBeenCalledTimes(2)
    expect(wrapper!.find('[data-testid="goal-g1"]').exists()).toBe(true)
  })

  it('cria outra meta: fecha o diálogo, a nova aparece por último e "Meta criada" é anunciada', async () => {
    mockCreate.mockResolvedValue(
      goal('g2', 'Viagem', { description: 'Praia', targetAmount: 12000 }),
    )
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
    expect(inDialog('goal-target-date-error')!.textContent?.trim()).toBe(
      'Escolha uma data de hoje em diante',
    )
  })

  it('400 com errors.description aparece embaixo do campo', async () => {
    mockCreate.mockRejectedValue({
      response: {
        status: 400,
        data: { errors: { description: 'Use no máximo 500 caracteres na descrição' } },
      },
    })
    await mountView()
    await openDialog()
    await type('goal-name-input', 'Viagem')
    await type('goal-target-amount-input', '100')
    await submit()
    expect(inDialog('goal-description-error')!.textContent?.trim()).toBe(
      'Use no máximo 500 caracteres na descrição',
    )
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

  describe('Story 8.6: modelos de meta', () => {
    async function choose(code: string) {
      const radio = inDialog<HTMLInputElement>(`goal-template-${code}`)!
      radio.checked = true
      radio.dispatchEvent(new Event('change'))
      await flushPromises()
    }
    const value = (testId: string) => inDialog<HTMLInputElement>(testId)!.value

    it('começa em "Personalizada" com o rótulo "Usar modelo"', async () => {
      await mountView()
      await openDialog()
      expect(inDialog('goal-dialog')!.querySelector('legend')!.textContent?.trim()).toBe('Usar modelo')
      expect(inDialog<HTMLInputElement>('goal-template-CUSTOM')!.checked).toBe(true)
    })

    it('"Férias" preenche os campos e o aporte sugerido; editado, o POST leva o modelo', async () => {
      mockCreate.mockResolvedValue(
        goal('g2', 'Férias', { targetAmount: 10000, template: 'VACATION' }),
      )
      await mountView()
      await openDialog()
      await type('goal-name-input', 'Outra')
      await choose('VACATION')
      expect(value('goal-name-input')).toBe('Férias')
      expect(value('goal-target-amount-input')).toBe('8.000,00')
      expect(value('goal-target-date-input')).toBe('2027-05-13')
      expect(value('goal-description-input')).toBe('Viagem de férias')
      expect(value('goal-monthly-allocation-input')).toBe('1.333,34')

      await type('goal-target-amount-input', '10.000,00')
      await submit()
      expect(mockCreate).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'Férias', targetAmount: 10000, template: 'VACATION' }),
      )
      expect(
        wrapper!.get('[data-testid="goal-g2"] [data-testid="goal-template-badge"]').text(),
      ).toBe('Template: Férias')
    })

    it('"Reserva de Emergência" recalcula o aporte mesmo depois de editado', async () => {
      await mountView()
      await openDialog()
      await choose('VACATION')
      await type('goal-monthly-allocation-input', '10,00')
      await choose('EMERGENCY_FUND')
      expect(value('goal-target-amount-input')).toBe('25.000,00')
      expect(value('goal-target-date-input')).toBe('2027-11-13')
      expect(value('goal-description-input')).toBe('6 meses de despesas')
      expect(value('goal-monthly-allocation-input')).toBe('2.083,34')
    })

    it('voltar a "Personalizada" limpa o formulário e o POST vai sem modelo', async () => {
      mockCreate.mockResolvedValue(goal('g2', 'Viagem'))
      await mountView()
      await openDialog()
      await choose('BIG_PURCHASE')
      await choose('CUSTOM')
      expect(value('goal-name-input')).toBe('')
      expect(value('goal-target-amount-input')).toBe('')
      expect(value('goal-target-date-input')).toBe('')
      expect(value('goal-monthly-allocation-input')).toBe('')
      expect(value('goal-description-input')).toBe('')

      await type('goal-name-input', 'Viagem')
      await type('goal-target-amount-input', '100')
      await submit()
      expect(mockCreate.mock.calls[0]![0]).not.toHaveProperty('template')
    })

    it('reabrir o diálogo volta para "Personalizada"', async () => {
      mockCreate.mockResolvedValue(goal('g2', 'Férias', { template: 'VACATION' }))
      await mountView()
      await openDialog()
      await choose('VACATION')
      await submit()
      await openDialog()
      expect(inDialog<HTMLInputElement>('goal-template-CUSTOM')!.checked).toBe(true)
      expect(value('goal-name-input')).toBe('')
    })

    it('400 com modelo inválido mostra a mensagem geral do diálogo', async () => {
      mockCreate.mockRejectedValue({
        response: { status: 400, data: { errors: { template: 'Modelo de meta inválido' } } },
      })
      await mountView()
      await openDialog()
      await choose('VACATION')
      await submit()
      expect(inDialog('goal-form-error')!.textContent).toContain('Não foi possível criar a meta')
    })

    it('meta sem modelo não mostra o selo', async () => {
      await mountView()
      expect(wrapper!.find('[data-testid="goal-template-badge"]').exists()).toBe(false)
    })
  })

  describe('Story 8.2: alocação e aporte', () => {
    async function openAmount(goalId: string, action: 'edit-allocation' | 'contribute') {
      await wrapper!
        .get(`[data-testid="goal-${goalId}"] [data-testid="${action}"]`)
        .trigger('click')
      await flushPromises()
    }

    async function submitAmount() {
      inDialog<HTMLFormElement>('amount-form')!.dispatchEvent(new Event('submit'))
      await flushPromises()
    }

    it('edita a alocação: card mostra o novo valor, o total muda e a mensagem é anunciada', async () => {
      mockList.mockResolvedValue([
        goal('g1', 'Viagem', { monthlyAllocation: 500 }),
        goal('g2', 'Carro', { monthlyAllocation: 300 }),
      ])
      mockAllocation.mockResolvedValue(goal('g1', 'Viagem', { monthlyAllocation: 700 }))
      await mountView()
      expect(plain(wrapper!.get('[data-testid="goals-total"]').text())).toContain('R$ 800,00/mês')

      await openAmount('g1', 'edit-allocation')
      expect(inDialog<HTMLInputElement>('amount-input')!.value).toBe('500,00')
      await type('amount-input', '700')
      await submitAmount()

      expect(mockAllocation).toHaveBeenCalledWith('g1', 700)
      expect(inDialog('amount-dialog')).toBeNull()
      expect(plain(wrapper!.get('[data-testid="goal-g1"]').text())).toContain(
        'Aporte mensal: R$ 700,00/mês',
      )
      expect(plain(wrapper!.get('[data-testid="goals-total"]').text())).toContain('R$ 1.000,00/mês')
      expect(wrapper!.get('[role="status"]').text()).toBe('Alocação atualizada para Viagem')
    })

    it('aporta numa meta: a barra mostra o novo valor e a outra meta fica igual', async () => {
      mockList.mockResolvedValue([
        goal('g1', 'Viagem', { targetAmount: 1000 }),
        goal('g2', 'Carro', { targetAmount: 5000, currentAmount: 100 }),
      ])
      mockContribute.mockResolvedValue(
        goal('g1', 'Viagem', { targetAmount: 1000, currentAmount: 200 }),
      )
      await mountView()

      await openAmount('g1', 'contribute')
      await type('amount-input', '200,00')
      await submitAmount()

      expect(mockContribute).toHaveBeenCalledWith('g1', 200)
      expect(
        plain(wrapper!.get('[data-testid="goal-g1"] [data-testid="goal-progress-text"]').text()),
      ).toBe('R$ 200,00 de R$ 1.000,00 (20%)')
      expect(
        plain(wrapper!.get('[data-testid="goal-g2"] [data-testid="goal-progress-text"]').text()),
      ).toBe('R$ 100,00 de R$ 5.000,00 (2%)')
      expect(wrapper!.get('[role="status"]').text()).toBe('Aporte registrado em Viagem')
      expect(mockList).toHaveBeenCalledTimes(1)
    })

    it('aporte além do alvo limita a barra a 100%', async () => {
      mockContribute.mockResolvedValue(
        goal('g1', 'Reserva de Emergência', { currentAmount: 30000 }),
      )
      await mountView()
      await openAmount('g1', 'contribute')
      await type('amount-input', '30.000')
      await submitAmount()
      expect(
        wrapper!.get('[data-testid="goal-g1"] [role="progressbar"]').attributes('aria-valuenow'),
      ).toBe('100')
    })

    it.each([
      ['edit-allocation', '-1', 'O aporte mensal não pode ser negativo'],
      ['edit-allocation', '', 'Informe o aporte mensal'],
      ['edit-allocation', '1,001', 'Informe um valor válido, com até 2 casas decimais'],
      ['contribute', '0', 'O aporte precisa ser maior que zero'],
      ['contribute', '-5', 'O aporte precisa ser maior que zero'],
    ] as const)(
      '%s com "%s" mostra o erro embaixo do campo sem chamar a API',
      async (action, value, message) => {
        await mountView()
        await openAmount('g1', action)
        await type('amount-input', value)
        await submitAmount()
        expect(inDialog('amount-error')!.textContent!.trim()).toBe(message)
        expect(inDialog<HTMLInputElement>('amount-input')!.getAttribute('aria-invalid')).toBe(
          'true',
        )
        expect(mockAllocation).not.toHaveBeenCalled()
        expect(mockContribute).not.toHaveBeenCalled()
      },
    )

    it('400 do backend vai para o campo', async () => {
      mockContribute.mockRejectedValue({
        response: {
          status: 400,
          data: { detail: 'x', errors: { amount: 'O aporte precisa ser maior que zero' } },
        },
      })
      await mountView()
      await openAmount('g1', 'contribute')
      await type('amount-input', '10')
      await submitAmount()
      expect(inDialog('amount-error')!.textContent!.trim()).toBe(
        'O aporte precisa ser maior que zero',
      )
    })

    it('404 (meta de outro usuário) mostra a mensagem geral no diálogo', async () => {
      mockAllocation.mockRejectedValue({
        response: { status: 404, data: { detail: 'Meta não encontrada' } },
      })
      await mountView()
      await openAmount('g1', 'edit-allocation')
      await type('amount-input', '10')
      await submitAmount()
      expect(inDialog('amount-form-error')!.textContent!.trim()).toBe('Meta não encontrada')
      expect(inDialog('amount-dialog')).not.toBeNull()
    })

    it('a lista aparece sem esperar o resumo', async () => {
      mockSummary.mockReturnValue(new Promise(() => {}))
      await mountView()
      expect(wrapper!.find('[data-testid="goal-g1"]').exists()).toBe(true)
    })

    it('avisa quando o total alocado passa do disponível', async () => {
      mockSummary.mockResolvedValue({ totalAvailable: 799.99 })
      mockList.mockResolvedValue([goal('g1', 'Viagem', { monthlyAllocation: 800 })])
      await mountView()
      expect(wrapper!.get('[data-testid="goals-over-available"]').text()).toBe(
        'Suas metas somam mais do que você tem disponível',
      )
    })

    it('sem aviso quando cabe no disponível ou quando o resumo falha', async () => {
      mockList.mockResolvedValue([
        goal('g1', 'Viagem', { monthlyAllocation: 0.1 }),
        goal('g2', 'X', { monthlyAllocation: 0.2 }),
      ])
      mockSummary.mockResolvedValue({ totalAvailable: 0.3 })
      await mountView()
      expect(wrapper!.find('[data-testid="goals-over-available"]').exists()).toBe(false)
      wrapper!.unmount()

      mockSummary.mockRejectedValue(new Error('rede'))
      mockList.mockResolvedValue([goal('g1', 'Viagem', { monthlyAllocation: 800 })])
      await mountView()
      expect(wrapper!.find('[data-testid="goal-g1"]').exists()).toBe(true)
      expect(wrapper!.find('[data-testid="goals-over-available"]').exists()).toBe(false)
    })
  })

  describe('Story 8.5: ajustar alocações', () => {
    afterEach(() => window.history.replaceState(null, ''))

    it('"Ajustar alocações" só aparece com 2 ou mais metas', async () => {
      await mountView()
      expect(wrapper!.find('[data-testid="adjust-allocations"]').exists()).toBe(false)
      wrapper!.unmount()

      mockList.mockResolvedValue([goal('g1', 'Viagem'), goal('g2', 'Carro')])
      await mountView()
      expect(
        wrapper!.getComponent<typeof RouterLinkStub>('[data-testid="adjust-allocations"]').props('to'),
      ).toBe('/metas/ajustar')
    })

    it('voltando do ajuste anuncia "Alocações atualizadas" e limpa o estado', async () => {
      window.history.replaceState({ allocationsUpdated: true }, '')
      await mountView()
      expect(wrapper!.get('[data-testid="goals-success"]').text()).toBe('Alocações atualizadas')
      expect(window.history.state.allocationsUpdated).toBeNull()
    })

    it('sem o estado não mostra a mensagem', async () => {
      await mountView()
      expect(wrapper!.get('[data-testid="goals-success"]').text()).toBe('')
    })
  })

  describe('Story 8.4: projeção de conclusão', () => {
    const text = (testid: string) =>
      plain(wrapper!.get(`[data-testid="goal-g1"] [data-testid="${testid}"]`).text())
    const hasSuggestion = () =>
      wrapper!.find('[data-testid="goal-g1"] [data-testid="goal-suggestion"]').exists()

    async function saveAllocation(value: string) {
      await wrapper!.get('[data-testid="goal-g1"] [data-testid="edit-allocation"]').trigger('click')
      await flushPromises()
      await type('amount-input', value)
      inDialog<HTMLFormElement>('amount-form')!.dispatchEvent(new Event('submit'))
      await flushPromises()
    }

    it.each([
      [{}, 'Projeção de conclusão: setembro de 2027', null],
      [{ targetDate: '2027-10-01' }, 'Projeção de conclusão: setembro de 2027', null],
      [
        { targetDate: '2027-07-13' },
        'Projeção de conclusão: setembro de 2027',
        'Aumente alocação para R$ 125,00/mês para atingir meta no prazo',
      ],
      [
        { monthlyAllocation: null, targetDate: '2027-07-13' },
        'Defina alocação mensal para ver projeção',
        null,
      ],
      [{ currentAmount: 1200 }, 'Valor-alvo atingido', null],
      [
        { targetAmount: 1_000_000, monthlyAllocation: 0.01 },
        'Projeção de conclusão: daqui a mais de 100 anos',
        null,
      ],
      [
        { currentAmount: 400, targetDate: '2026-11-30' },
        'Projeção de conclusão: maio de 2027',
        'O prazo desta meta já chegou. Aporte R$ 600,00 para concluí-la',
      ],
    ])('%o → "%s"', async (extra, projection, suggestion) => {
      mockList.mockResolvedValue([
        goal('g1', 'Viagem', { targetAmount: 1000, monthlyAllocation: 100, ...extra }),
      ])
      await mountView()
      expect(text('goal-projection')).toBe(projection)
      const shown = wrapper!.find('[data-testid="goal-g1"] [data-testid="goal-suggestion"]')
      expect(shown.exists() ? plain(shown.text()) : null).toBe(suggestion)
    })

    it('salvar a alocação sugerida tira a sugestão, projeta no mês do prazo e vira "Em dia"', async () => {
      const late = { targetAmount: 1000, monthlyAllocation: 100, targetDate: '2027-07-13' }
      mockList.mockResolvedValue([goal('g1', 'Viagem', late)])
      mockAllocation.mockResolvedValue(goal('g1', 'Viagem', { ...late, monthlyAllocation: 125 }))
      await mountView()
      expect(text('goal-pace')).toBe('Atrasada')

      await saveAllocation('125')

      expect(mockList).toHaveBeenCalledTimes(1)
      expect(hasSuggestion()).toBe(false)
      expect(text('goal-projection')).toBe('Projeção de conclusão: julho de 2027')
      expect(text('goal-pace')).toBe('Em dia')
    })

    it('definir alocação numa meta sem alocação mostra a projeção', async () => {
      mockList.mockResolvedValue([
        goal('g1', 'Viagem', { targetAmount: 1000, monthlyAllocation: null }),
      ])
      mockAllocation.mockResolvedValue(
        goal('g1', 'Viagem', { targetAmount: 1000, monthlyAllocation: 100 }),
      )
      await mountView()
      expect(text('goal-projection')).toBe('Defina alocação mensal para ver projeção')

      await saveAllocation('100')

      expect(text('goal-projection')).toBe('Projeção de conclusão: setembro de 2027')
    })
  })

  describe('Story 8.3: ritmo e evolução', () => {
    it('barra tem marcos de 25/50/75% e fica verde "Em dia" sem data-alvo', async () => {
      await mountView()
      const card = wrapper!.get('[data-testid="goal-g1"]')
      expect(card.findAll('[data-testid="goal-progress-mark"]')).toHaveLength(3)
      expect(card.get('[data-testid="goal-progress-fill"]').classes()).toContain('bg-green-500')
      expect(card.get('[data-testid="goal-pace"]').text()).toBe('Em dia')
    })

    it.each([
      ['2027-07-13', 'bg-yellow-500', 'Atrasada'],
      ['2027-04-13', 'bg-red-500', 'Muito atrasada'],
    ])('alvo 1000, 0 acumulado, 100/mês, prazo %s → %s "%s"', async (targetDate, bar, label) => {
      mockList.mockResolvedValue([
        goal('g1', 'Viagem', { targetAmount: 1000, monthlyAllocation: 100, targetDate }),
      ])
      await mountView()
      const card = wrapper!.get('[data-testid="goal-g1"]')
      expect(card.get('[data-testid="goal-progress-fill"]').classes()).toContain(bar)
      expect(card.get('[data-testid="goal-pace"]').text()).toBe(label)
    })

    it('"Ver evolução" só busca a série ao abrir; "Ocultar evolução" recolhe', async () => {
      mockProgress.mockResolvedValue({ points: [{ month: '2026-11', amount: 0 }] })
      await mountView()
      expect(mockProgress).not.toHaveBeenCalled()
      const toggle = wrapper!.get('[data-testid="goal-g1"] [data-testid="toggle-chart"]')
      expect(toggle.attributes('aria-expanded')).toBe('false')

      await toggle.trigger('click')
      await flushPromises()
      expect(mockProgress).toHaveBeenCalledWith('g1')
      expect(toggle.attributes('aria-expanded')).toBe('true')
      expect(toggle.text()).toBe('Ocultar evolução')
      expect(wrapper!.find('[data-testid="goal-chart"]').exists()).toBe(true)

      await toggle.trigger('click')
      expect(toggle.attributes('aria-expanded')).toBe('false')
      expect(wrapper!.find('[data-testid="goal-chart"]').exists()).toBe(false)
    })

    it('falha (ex.: 404) mostra a mensagem no lugar do gráfico', async () => {
      mockProgress.mockRejectedValue({ response: { status: 404 } })
      await mountView()
      await wrapper!.get('[data-testid="toggle-chart"]').trigger('click')
      await flushPromises()
      expect(wrapper!.get('[data-testid="goal-chart-error"]').text()).toBe(
        'Não foi possível carregar a evolução',
      )
    })

    it('aporte com o gráfico aberto atualiza barra, texto e o último ponto sem recarregar a página', async () => {
      mockList.mockResolvedValue([goal('g1', 'Viagem', { targetAmount: 1000 })])
      mockProgress.mockResolvedValueOnce({ points: [{ month: '2026-11', amount: 0 }] })
      mockProgress.mockResolvedValueOnce({ points: [{ month: '2026-11', amount: 200 }] })
      mockContribute.mockResolvedValue(
        goal('g1', 'Viagem', { targetAmount: 1000, currentAmount: 200 }),
      )
      await mountView()
      await wrapper!.get('[data-testid="toggle-chart"]').trigger('click')
      await flushPromises()

      await wrapper!.get('[data-testid="goal-g1"] [data-testid="contribute"]').trigger('click')
      await flushPromises()
      await type('amount-input', '200')
      inDialog<HTMLFormElement>('amount-form')!.dispatchEvent(new Event('submit'))
      await flushPromises()

      expect(mockProgress).toHaveBeenCalledTimes(2)
      expect(mockList).toHaveBeenCalledTimes(1)
      expect(plain(wrapper!.get('[data-testid="goal-progress-text"]').text())).toBe(
        'R$ 200,00 de R$ 1.000,00 (20%)',
      )
      expect(wrapper!.get('[role="progressbar"]').attributes('aria-valuenow')).toBe('20')
      expect(
        plain(wrapper!.get('[data-testid="goal-chart-point-0"]').attributes('aria-label')!),
      ).toBe('novembro de 2026, R$ 200,00')
    })

    it('fechar enquanto a série carrega não reabre o gráfico quando ela chega', async () => {
      let resolve!: (value: unknown) => void
      mockProgress.mockReturnValue(new Promise((r) => (resolve = r)))
      await mountView()
      const toggle = wrapper!.get('[data-testid="toggle-chart"]')
      await toggle.trigger('click')
      await toggle.trigger('click')
      resolve({ points: [{ month: '2026-11', amount: 0 }] })
      await flushPromises()
      expect(wrapper!.find('[data-testid="goal-chart"]').exists()).toBe(false)
      expect(wrapper!.find('[data-testid="goal-chart-error"]').exists()).toBe(false)
      expect(toggle.attributes('aria-expanded')).toBe('false')
    })

    it('aporte com o gráfico fechado não busca a série', async () => {
      mockContribute.mockResolvedValue(goal('g1', 'Reserva de Emergência', { currentAmount: 10 }))
      await mountView()
      await wrapper!.get('[data-testid="contribute"]').trigger('click')
      await flushPromises()
      await type('amount-input', '10')
      inDialog<HTMLFormElement>('amount-form')!.dispatchEvent(new Event('submit'))
      await flushPromises()
      expect(mockProgress).not.toHaveBeenCalled()
    })
  })
})
