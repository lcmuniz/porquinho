import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import EmergencyFundStep from '../EmergencyFundStep.vue'

const mockCreateGoal = vi.fn()
const mockFinish = vi.fn()
const mockListGoals = vi.fn()

vi.mock('@/services/goalService', () => ({
  createGoal: (...a: unknown[]) => mockCreateGoal(...a),
  listGoals: (...a: unknown[]) => mockListGoals(...a),
}))

vi.mock('@/services/onboardingService', () => ({
  finishOnboarding: (...a: unknown[]) => mockFinish(...a),
}))

const doneProgress = {
  currentStep: 8,
  stepsCompleted: ['dashboard_gps_explained', 'emergency_fund_created'],
  completed: true,
}

async function mountStep() {
  const wrapper = mount(EmergencyFundStep, { attachTo: document.body })
  await flushPromises()
  return wrapper
}

type Wrapper = Awaited<ReturnType<typeof mountStep>>

const input = (wrapper: Wrapper, field: string) =>
  wrapper.get<HTMLInputElement>(`[data-testid="goal-${field}-input"]`)

/** Intl usa espaço não separável depois de "R$"; normaliza para comparar. */
const plain = (s: string) => s.replace(/\s/g, ' ')

describe('EmergencyFundStep', () => {
  beforeEach(() => {
    // Só o Date é falso: "hoje" fixo em 02/11/2026, as promessas seguem normais
    vi.useFakeTimers({ toFake: ['Date'] })
    // 13:00 UTC = 10:00 em São Paulo de 02/11/2026
    vi.setSystemTime(new Date('2026-11-02T13:00:00Z'))
    mockListGoals.mockReset().mockResolvedValue([])
    mockCreateGoal.mockReset().mockResolvedValue({ id: 'g1' })
    mockFinish.mockReset().mockResolvedValue(doneProgress)
  })

  afterEach(() => {
    vi.useRealTimers()
    document.body.innerHTML = ''
  })

  it('mostra o título, os campos pré-preenchidos e a prévia em 0%', async () => {
    const wrapper = await mountStep()

    expect(wrapper.get('h1').text()).toBe('Crie sua primeira meta: Reserva de Emergência')
    expect(document.activeElement).toBe(wrapper.get('h1').element)
    expect(input(wrapper, 'name').element.value).toBe('Reserva de Emergência')
    expect(input(wrapper, 'target-amount').element.value).toBe('25.000,00')
    expect(input(wrapper, 'target-date').element.value).toBe('2027-11-02')
    expect(input(wrapper, 'target-date').attributes('type')).toBe('date')
    expect(input(wrapper, 'target-date').attributes('min')).toBe('2026-11-02')
    expect(input(wrapper, 'monthly-allocation').element.value).toBe('2.083,34')

    const bar = wrapper.get('[data-testid="goal-progress"]')
    expect(bar.attributes('role')).toBe('progressbar')
    expect(bar.attributes('aria-valuenow')).toBe('0')
    const preview = plain(wrapper.get('[data-testid="goal-preview"]').text())
    expect(preview).toContain('0%')
    expect(preview).toContain('R$ 0,00 de R$ 25.000,00')
    expect(wrapper.get('[data-testid="goal-forecast"]').text()).toBe(
      'Previsão de conclusão: novembro de 2027',
    )
  })

  it('todos os campos têm label', async () => {
    const wrapper = await mountStep()
    for (const id of [
      'goal-name',
      'goal-target-amount',
      'goal-target-date',
      'goal-monthly-allocation',
    ]) {
      expect(wrapper.find(`label[for="${id}"]`).exists()).toBe(true)
      expect(wrapper.find(`#${id}`).exists()).toBe(true)
    }
  })

  it('mudar valor ou data recalcula o aporte e a previsão', async () => {
    const wrapper = await mountStep()

    await input(wrapper, 'target-amount').setValue('12.000,00')
    expect(input(wrapper, 'monthly-allocation').element.value).toBe('1.000,00')

    await input(wrapper, 'target-date').setValue('2027-05-02')
    expect(input(wrapper, 'monthly-allocation').element.value).toBe('2.000,00')
    expect(wrapper.get('[data-testid="goal-forecast"]').text()).toBe(
      'Previsão de conclusão: maio de 2027',
    )

    // Data no mesmo mês: mínimo de 1 mês
    await input(wrapper, 'target-date').setValue('2026-11-20')
    expect(input(wrapper, 'monthly-allocation').element.value).toBe('12.000,00')
  })

  it('depois de editar o aporte, mudar valor ou data não o sobrescreve; a previsão segue o aporte', async () => {
    const wrapper = await mountStep()

    await input(wrapper, 'monthly-allocation').setValue('1.000,00')
    await input(wrapper, 'target-amount').setValue('30.000,00')
    await input(wrapper, 'target-date').setValue('2028-11-02')

    expect(input(wrapper, 'monthly-allocation').element.value).toBe('1.000,00')
    // 30.000 ÷ 1.000 = 30 meses a partir de 11/2026 → maio de 2029
    expect(wrapper.get('[data-testid="goal-forecast"]').text()).toBe(
      'Previsão de conclusão: maio de 2029',
    )
  })

  it('sem aporte, a prévia mostra a data-alvo; sem os dois, pede um deles', async () => {
    const wrapper = await mountStep()

    await input(wrapper, 'monthly-allocation').setValue('')
    expect(wrapper.get('[data-testid="goal-forecast"]').text()).toBe(
      'Previsão de conclusão: novembro de 2027',
    )

    await input(wrapper, 'target-date').setValue('')
    expect(wrapper.get('[data-testid="goal-forecast"]').text()).toContain(
      'Informe um aporte mensal ou uma data-alvo',
    )
  })

  it('"Criar meta e concluir" cria a meta e depois conclui com skipped=false', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="create-goal-button"]').trigger('click')
    await flushPromises()

    expect(mockCreateGoal).toHaveBeenCalledWith({
      name: 'Reserva de Emergência',
      targetAmount: 25000,
      targetDate: '2027-11-02',
      monthlyAllocation: 2083.34,
    })
    expect(mockFinish).toHaveBeenCalledWith(false)
    expect(mockCreateGoal.mock.invocationCallOrder[0]).toBeLessThan(
      mockFinish.mock.invocationCallOrder[0]!,
    )
    expect(wrapper.emitted('done')).toEqual([[doneProgress]])
  })

  it('sem data e sem aporte manda null nos dois', async () => {
    const wrapper = await mountStep()
    await input(wrapper, 'target-date').setValue('')
    await input(wrapper, 'monthly-allocation').setValue('')

    await wrapper.get('[data-testid="create-goal-button"]').trigger('click')
    await flushPromises()

    expect(mockCreateGoal).toHaveBeenCalledWith(
      expect.objectContaining({ targetDate: null, monthlyAllocation: null }),
    )
  })

  it('duplo clique cria só uma meta', async () => {
    const wrapper = await mountStep()
    const button = wrapper.get('[data-testid="create-goal-button"]')

    await Promise.all([button.trigger('click'), button.trigger('click')])
    await flushPromises()

    expect(mockCreateGoal).toHaveBeenCalledTimes(1)
    expect(mockFinish).toHaveBeenCalledTimes(1)
  })

  it.each([
    ['target-amount', '0', 'O valor-alvo precisa ser maior que zero'],
    ['target-amount', '', 'Informe o valor-alvo da meta'],
    ['target-amount', 'abc', 'Digite o valor no formato 25.000,00 (até 2 casas depois da vírgula)'],
    ['target-date', '2026-11-01', 'Escolha uma data de hoje em diante'],
    ['monthly-allocation', '-1,00', 'O aporte mensal não pode ser negativo'],
    ['name', '   ', 'Informe o nome da meta'],
    ['name', '\u200B\uFEFF', 'Informe o nome da meta'],
  ])('valida %s = "%s" no campo, sem chamar a API', async (field, value, message) => {
    const wrapper = await mountStep()
    await input(wrapper, field).setValue(value)

    await wrapper.get('[data-testid="create-goal-button"]').trigger('click')
    await flushPromises()

    const error = wrapper.get(`[data-testid="goal-${field}-error"]`)
    expect(error.text()).toBe(message)
    expect(input(wrapper, field).attributes('aria-invalid')).toBe('true')
    expect(input(wrapper, field).attributes('aria-describedby')).toContain(error.attributes('id'))
    expect(mockCreateGoal).not.toHaveBeenCalled()
    expect(mockFinish).not.toHaveBeenCalled()
  })

  it('hoje é aceito como data-alvo', async () => {
    const wrapper = await mountStep()
    await input(wrapper, 'target-date').setValue('2026-11-02')

    await wrapper.get('[data-testid="create-goal-button"]').trigger('click')
    await flushPromises()

    expect(mockCreateGoal).toHaveBeenCalledWith(
      expect.objectContaining({ targetDate: '2026-11-02' }),
    )
  })

  it('400 do backend aparece no campo e não conclui', async () => {
    mockCreateGoal.mockRejectedValue({
      response: {
        status: 400,
        data: {
          detail: 'Escolha uma data de hoje em diante',
          errors: { targetDate: 'Escolha uma data de hoje em diante' },
        },
      },
    })
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="create-goal-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="goal-target-date-error"]').text()).toBe(
      'Escolha uma data de hoje em diante',
    )
    expect(mockFinish).not.toHaveBeenCalled()
    expect(wrapper.emitted('error')).toBeUndefined()
    expect(wrapper.get('[data-testid="create-goal-button"]').attributes('disabled')).toBeUndefined()
  })

  it('falha ao criar a meta mostra o toast e libera os botões', async () => {
    mockCreateGoal.mockRejectedValue(new Error('Network Error'))
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="create-goal-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('error')?.[0]?.[0]).toContain('Não foi possível criar a meta')
    expect(mockFinish).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="create-goal-button"]').attributes('disabled')).toBeUndefined()
  })

  it('meta criada e /finish falhou: tentar de novo chama só o /finish', async () => {
    mockFinish.mockRejectedValueOnce({ response: { status: 500, data: { detail: '' } } })
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="create-goal-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('error')?.[0]?.[0]).toContain('Não foi possível concluir o onboarding')
    expect(wrapper.emitted('done')).toBeUndefined()
    expect(wrapper.get('[data-testid="goal-saved-message"]').text()).toContain(
      'Sua meta já foi salva',
    )
    expect(wrapper.find('[data-testid="skip-button"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="create-goal-button"]').text()).toBe('Concluir')

    await wrapper.get('[data-testid="create-goal-button"]').trigger('click')
    await flushPromises()

    expect(mockCreateGoal).toHaveBeenCalledTimes(1)
    expect(mockFinish).toHaveBeenCalledTimes(2)
    expect(wrapper.emitted('done')).toEqual([[doneProgress]])
  })

  it('409 do /finish mostra o detail do backend no toast', async () => {
    mockFinish.mockRejectedValue({
      response: {
        status: 409,
        data: { detail: 'Crie sua meta para concluir, ou toque em "Pular por agora".' },
      },
    })
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="create-goal-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('error')?.[0]?.[0]).toBe(
      'Crie sua meta para concluir, ou toque em "Pular por agora".',
    )
  })

  it('"Pular por agora" só conclui com skipped=true, sem criar meta', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="skip-button"]').trigger('click')
    await flushPromises()

    expect(mockCreateGoal).not.toHaveBeenCalled()
    expect(mockFinish).toHaveBeenCalledWith(true)
    expect(wrapper.emitted('done')).toEqual([[doneProgress]])
  })

  it('Voltar emite back sem chamar a API', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="back-button"]').trigger('click')

    expect(wrapper.emitted('back')).toHaveLength(1)
    expect(mockCreateGoal).not.toHaveBeenCalled()
    expect(mockFinish).not.toHaveBeenCalled()
  })

  it('botões e campos têm alvo de toque de 44px', async () => {
    const wrapper = await mountStep()
    for (const testid of ['back-button', 'create-goal-button', 'skip-button']) {
      expect(wrapper.get(`[data-testid="${testid}"]`).classes()).toContain('min-h-[44px]')
    }
    for (const field of ['name', 'target-amount', 'target-date', 'monthly-allocation']) {
      expect(input(wrapper, field).classes()).toContain('min-h-[44px]')
    }
  })

  it('"hoje" é o dia em São Paulo: perto da meia-noite UTC ainda vale o dia anterior', async () => {
    // 01:30 UTC de 03/11 = 22:30 de 02/11 em São Paulo
    vi.setSystemTime(new Date('2026-11-03T01:30:00Z'))
    const wrapper = await mountStep()

    expect(input(wrapper, 'target-date').attributes('min')).toBe('2026-11-02')
    expect(input(wrapper, 'target-date').element.value).toBe('2027-11-02')

    await input(wrapper, 'target-date').setValue('2026-11-02')
    await wrapper.get('[data-testid="create-goal-button"]').trigger('click')
    await flushPromises()

    expect(mockCreateGoal).toHaveBeenCalledWith(
      expect.objectContaining({ targetDate: '2026-11-02' }),
    )
  })

  it('aporte de 0,01 numa meta grande mostra "daqui a mais de 100 anos" sem quebrar', async () => {
    const wrapper = await mountStep()

    await input(wrapper, 'monthly-allocation').setValue('0,01')

    expect(wrapper.get('[data-testid="goal-forecast"]').text()).toBe(
      'Previsão de conclusão: daqui a mais de 100 anos',
    )
  })

  it('com meta ativa já salva, mostra-a só leitura, esconde o Pular e Concluir chama só o /finish', async () => {
    mockListGoals.mockResolvedValue([
      {
        id: 'g0',
        name: 'Minha reserva',
        description: null,
        targetAmount: 18000,
        currentAmount: 0,
        targetDate: '2027-06-02',
        monthlyAllocation: 2250,
        completed: false,
        createdAt: '2026-11-02T12:00:00Z',
      },
    ])
    const wrapper = await mountStep()

    expect(input(wrapper, 'name').element.value).toBe('Minha reserva')
    expect(input(wrapper, 'target-amount').element.value).toBe('18.000,00')
    expect(input(wrapper, 'target-date').element.value).toBe('2027-06-02')
    expect(input(wrapper, 'monthly-allocation').element.value).toBe('2.250,00')
    expect(input(wrapper, 'name').attributes('disabled')).toBeDefined()
    const message = wrapper.get('[data-testid="goal-saved-message"]')
    expect(message.attributes('role')).toBe('status')
    expect(message.text()).toBe('Sua meta já foi salva. Toque em Concluir para terminar.')
    expect(wrapper.find('[data-testid="skip-button"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="create-goal-button"]').text()).toBe('Concluir')

    await wrapper.get('[data-testid="create-goal-button"]').trigger('click')
    await flushPromises()

    expect(mockCreateGoal).not.toHaveBeenCalled()
    expect(mockFinish).toHaveBeenCalledWith(false)
    expect(wrapper.emitted('done')).toEqual([[doneProgress]])
  })

  it('falha ao listar as metas não impede criar a meta', async () => {
    mockListGoals.mockRejectedValue(new Error('Network Error'))
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="create-goal-button"]').trigger('click')
    await flushPromises()

    expect(mockCreateGoal).toHaveBeenCalledOnce()
    expect(mockFinish).toHaveBeenCalledWith(false)
  })
})
