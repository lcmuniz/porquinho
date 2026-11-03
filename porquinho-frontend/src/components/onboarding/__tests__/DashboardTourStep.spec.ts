import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import DashboardTourStep from '../DashboardTourStep.vue'

const mockCompleteDashboardTour = vi.fn()

vi.mock('@/services/onboardingService', () => ({
  completeDashboardTour: (...a: unknown[]) => mockCompleteDashboardTour(...a),
}))

const nextProgress = {
  currentStep: 8,
  stepsCompleted: [
    'tutorial_viewed',
    'accounts_added',
    'cards_added',
    'categories_suggested',
    'categories_customized',
    'import_skipped',
    'dashboard_gps_explained',
  ],
  completed: false,
}

const TITLES = [
  'Conheça o Dashboard GPS',
  'Onde estou?',
  'Para onde vou?',
  'Preciso ajustar?',
  'Pronto para usar o dashboard',
]

async function mountStep() {
  const wrapper = mount(DashboardTourStep, { attachTo: document.body })
  await flushPromises()
  return wrapper
}

async function clickNext(wrapper: VueWrapper, times = 1) {
  for (let i = 0; i < times; i++) {
    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()
  }
}

describe('DashboardTourStep', () => {
  beforeEach(() => {
    mockCompleteDashboardTour.mockReset().mockResolvedValue(nextProgress)
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('tela 1: título, diagrama das 3 camadas com a seta de rolagem e a dica, foco no título', async () => {
    const wrapper = await mountStep()

    const heading = wrapper.get('h1')
    expect(heading.text()).toBe('Conheça o Dashboard GPS')
    expect(document.activeElement).toBe(heading.element)
    const diagram = wrapper.get('[data-testid="tour-layers-diagram"]')
    const layers = diagram.findAll('ol li')
    expect(layers).toHaveLength(3)
    expect(layers.map((l) => l.text())).toEqual([
      expect.stringContaining('Onde estou?'),
      expect.stringContaining('Para onde vou?'),
      expect.stringContaining('Preciso ajustar?'),
    ])
    expect(wrapper.get('[data-testid="tour-scroll-hint"]').text()).toBe(
      'Role verticalmente para navegar entre camadas',
    )
    expect(wrapper.text()).toContain('Tela 1 de 5')
  })

  it('Próximo nas telas 1 a 4 avança uma tela sem chamar a API e foca o novo título', async () => {
    const wrapper = await mountStep()

    for (let i = 1; i < TITLES.length; i++) {
      await clickNext(wrapper)
      const heading = wrapper.get('h1')
      expect(heading.text()).toBe(TITLES[i])
      expect(document.activeElement).toBe(heading.element)
      expect(wrapper.text()).toContain(`Tela ${i + 1} de 5`)
    }
    expect(mockCompleteDashboardTour).not.toHaveBeenCalled()
    expect(wrapper.emitted('done')).toBeUndefined()
  })

  it('cada camada tem o texto e a ilustração esquemática, sem imagens externas', async () => {
    const wrapper = await mountStep()

    await clickNext(wrapper)
    expect(wrapper.text()).toContain('5 métricas essenciais, 30 segundos para clareza total')
    expect(wrapper.findAll('[data-testid="tour-metric-card"]')).toHaveLength(5)
    expect(wrapper.text()).not.toMatch(/R\$/)

    await clickNext(wrapper)
    expect(wrapper.text()).toContain('Gráficos por categoria e tendências')
    expect(wrapper.get('[data-testid="tour-charts-illustration"]').findAll('svg')).toHaveLength(2)

    await clickNext(wrapper)
    expect(wrapper.text()).toContain('Todas as transações, filtros e edição')
    expect(wrapper.findAll('[data-testid="tour-list-row"]').length).toBeGreaterThan(0)

    await clickNext(wrapper)
    expect(wrapper.get('[data-testid="tour-weekly-checkin"]').text()).toBe(
      'Seu check-in semanal leva apenas 5 minutos',
    )
    expect(wrapper.findAll('img')).toHaveLength(0)
  })

  it('só tem Voltar e Próximo (sem Pular), com alvo de toque de 44px', async () => {
    const wrapper = await mountStep()

    const buttons = wrapper.findAll('button')
    expect(buttons.map((b) => b.text())).toEqual(['Voltar', 'Próximo'])
    for (const b of buttons) expect(b.classes()).toContain('min-h-[44px]')
    expect(wrapper.text()).not.toMatch(/Pular/)
  })

  it('Voltar na tela 1 emite back; nas outras volta uma tela', async () => {
    const wrapper = await mountStep()

    await clickNext(wrapper, 2)
    await wrapper.get('[data-testid="back-button"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('h1').text()).toBe('Onde estou?')
    expect(document.activeElement).toBe(wrapper.get('h1').element)
    expect(wrapper.emitted('back')).toBeUndefined()

    await wrapper.get('[data-testid="back-button"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('h1').text()).toBe('Conheça o Dashboard GPS')

    await wrapper.get('[data-testid="back-button"]').trigger('click')
    expect(wrapper.emitted('back')).toHaveLength(1)
    expect(mockCompleteDashboardTour).not.toHaveBeenCalled()
  })

  it('Próximo na tela 5 conclui o passo e emite done com o progresso', async () => {
    const wrapper = await mountStep()

    await clickNext(wrapper, 5)

    expect(mockCompleteDashboardTour).toHaveBeenCalledExactlyOnceWith()
    expect(wrapper.emitted('done')).toEqual([[nextProgress]])
  })

  it('duplo clique no Próximo final salva só uma vez e mantém os botões desabilitados no sucesso', async () => {
    const wrapper = await mountStep()
    await clickNext(wrapper, 4)
    const button = wrapper.get('[data-testid="next-button"]')

    void button.trigger('click')
    void button.trigger('click')
    await flushPromises()

    expect(mockCompleteDashboardTour).toHaveBeenCalledOnce()
    expect(wrapper.emitted('done')).toEqual([[nextProgress]])
    expect(button.attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="back-button"]').attributes('disabled')).toBeDefined()
  })

  it('falha da API emite o erro, continua na tela 5 e libera os botões para tentar de novo', async () => {
    mockCompleteDashboardTour.mockRejectedValueOnce(new Error('Network Error'))
    const wrapper = await mountStep()

    await clickNext(wrapper, 5)

    expect(wrapper.emitted('done')).toBeUndefined()
    expect(wrapper.emitted('error')?.[0]?.[0]).toContain('Não foi possível salvar seu progresso')
    expect(wrapper.find('[data-testid="tour-weekly-checkin"]').exists()).toBe(true)
    const button = wrapper.get('[data-testid="next-button"]')
    expect(button.attributes('disabled')).toBeUndefined()

    await clickNext(wrapper)
    expect(wrapper.emitted('done')).toEqual([[nextProgress]])
  })

  it('usa a mensagem do servidor quando houver (ex.: 409) e cai na genérica com detail vazio', async () => {
    mockCompleteDashboardTour
      .mockRejectedValueOnce({
        response: {
          status: 409,
          data: { detail: 'Conclua os passos anteriores antes de conhecer o Dashboard GPS.' },
        },
      })
      .mockRejectedValueOnce({ response: { status: 500, data: { detail: '' } } })
    const wrapper = await mountStep()

    await clickNext(wrapper, 6)

    const errors = wrapper.emitted('error') ?? []
    expect(errors[0]).toEqual(['Conclua os passos anteriores antes de conhecer o Dashboard GPS.'])
    expect(errors[1]?.[0]).toContain('Não foi possível salvar seu progresso')
  })

  it("Story 3.8: com initialScreen='last' abre na última tela; Voltar ali vai para a tela 4", async () => {
    const wrapper = mount(DashboardTourStep, {
      attachTo: document.body,
      props: { initialScreen: 'last' },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Seu check-in semanal leva apenas 5 minutos')

    await wrapper.get('[data-testid="back-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).not.toContain('Seu check-in semanal leva apenas 5 minutos')
    expect(wrapper.emitted('back')).toBeUndefined()
  })
})
