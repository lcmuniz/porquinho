import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import OnboardingView from '../OnboardingView.vue'
import type { OnboardingProgress } from '@/types/onboarding'

const mockGetMyOnboarding = vi.fn()
const mockCompleteTutorial = vi.fn()
const mockCompleteAccounts = vi.fn()
const mockListAccounts = vi.fn()
const mockCompleteCards = vi.fn()
const mockListCards = vi.fn()
const mockCompleteCategories = vi.fn()
const mockGetCategorySuggestions = vi.fn()
const mockCompleteCustomization = vi.fn()
const mockListCategories = vi.fn()
const mockGetCategoryOptions = vi.fn()
const mockPush = vi.fn()
const mockReplace = vi.fn()

vi.mock('@/services/onboardingService', () => ({
  getMyOnboarding: (...args: unknown[]) => mockGetMyOnboarding(...args),
  completeTutorial: (...args: unknown[]) => mockCompleteTutorial(...args),
  completeAccounts: (...args: unknown[]) => mockCompleteAccounts(...args),
  completeCards: (...args: unknown[]) => mockCompleteCards(...args),
  completeCategories: (...args: unknown[]) => mockCompleteCategories(...args),
  completeCustomization: (...args: unknown[]) => mockCompleteCustomization(...args),
}))

vi.mock('@/services/categoryService', () => ({
  getCategorySuggestions: (...args: unknown[]) => mockGetCategorySuggestions(...args),
  listCategories: (...args: unknown[]) => mockListCategories(...args),
  getCategoryOptions: (...args: unknown[]) => mockGetCategoryOptions(...args),
  createCategory: vi.fn(),
  updateCategory: vi.fn(),
  deleteCategory: vi.fn(),
}))

vi.mock('@/services/cardService', () => ({
  listCards: (...args: unknown[]) => mockListCards(...args),
  createCard: vi.fn(),
  deleteCard: vi.fn(),
}))

vi.mock('@/services/accountService', () => ({
  listAccounts: (...args: unknown[]) => mockListAccounts(...args),
  createAccount: vi.fn(),
  deleteAccount: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: (...args: unknown[]) => mockPush(...args),
    replace: (...args: unknown[]) => mockReplace(...args),
  }),
}))

const SUGGESTIONS = [
  {
    key: 'essentials',
    name: 'Essenciais',
    iconName: 'shield-check',
    colorHex: '#2563EB',
    suggestions: [
      { key: 'housing', name: 'Moradia', iconName: 'house', colorHex: '#3B82F6' },
      { key: 'food', name: 'Alimentação', iconName: 'utensils', colorHex: '#F97316' },
    ],
  },
]

const STEP_FIVE_DONE = [
  'tutorial_viewed',
  'accounts_added',
  'cards_skipped',
  'categories_suggested',
]

const MY_CATEGORIES = [
  {
    id: 'g1',
    categoryName: 'Essenciais',
    parentCategoryId: null,
    colorHex: '#2563EB',
    iconName: 'shield-check',
    systemDefault: true,
    createdAt: '2026-11-02T12:00:00Z',
  },
  {
    id: 'c1',
    categoryName: 'Moradia',
    parentCategoryId: 'g1',
    colorHex: '#3B82F6',
    iconName: 'house',
    systemDefault: true,
    createdAt: '2026-11-02T12:00:00Z',
  },
]

function progress(overrides: Partial<OnboardingProgress> = {}): OnboardingProgress {
  return { currentStep: 1, stepsCompleted: [], completed: false, ...overrides }
}

async function mountView() {
  const wrapper = mount(OnboardingView, { attachTo: document.body })
  await flushPromises()
  return wrapper
}

async function goToLastScreen(wrapper: Awaited<ReturnType<typeof mountView>>) {
  for (let i = 0; i < 4; i++) {
    await wrapper.get('[data-testid="next-button"]').trigger('click')
  }
}

function bodyButton(testId: string) {
  const button = document.body.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)
  if (!button) throw new Error(`button ${testId} not found`)
  return button
}

describe('OnboardingView', () => {
  beforeEach(() => {
    mockGetMyOnboarding.mockReset().mockResolvedValue(progress())
    mockCompleteTutorial
      .mockReset()
      .mockResolvedValue(progress({ currentStep: 2, stepsCompleted: ['tutorial_viewed'] }))
    mockCompleteAccounts
      .mockReset()
      .mockResolvedValue(
        progress({ currentStep: 3, stepsCompleted: ['tutorial_viewed', 'accounts_added'] }),
      )
    mockListAccounts.mockReset().mockResolvedValue([])
    mockCompleteCards.mockReset().mockResolvedValue(
      progress({
        currentStep: 4,
        stepsCompleted: ['tutorial_viewed', 'accounts_added', 'cards_added'],
      }),
    )
    mockListCards.mockReset().mockResolvedValue([])
    mockCompleteCategories.mockReset().mockResolvedValue(
      progress({
        currentStep: 5,
        stepsCompleted: [
          'tutorial_viewed',
          'accounts_added',
          'cards_added',
          'categories_suggested',
        ],
      }),
    )
    mockGetCategorySuggestions.mockReset().mockResolvedValue(SUGGESTIONS)
    mockListCategories.mockReset().mockResolvedValue(MY_CATEGORIES)
    mockGetCategoryOptions
      .mockReset()
      .mockResolvedValue({ colors: ['#2563EB', '#3B82F6'], icons: ['shield-check', 'house'] })
    mockCompleteCustomization
      .mockReset()
      .mockResolvedValue(
        progress({ currentStep: 6, stepsCompleted: [...STEP_FIVE_DONE, 'categories_customized'] }),
      )
    mockPush.mockReset().mockResolvedValue(undefined)
    mockReplace.mockReset().mockResolvedValue(undefined)
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('mostra as boas-vindas, o indicador "Passo 1 de 8", Próximo e Pular tutorial', async () => {
    const wrapper = await mountView()

    expect(mockGetMyOnboarding).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('Bem-vindo ao porquinho!')
    expect(wrapper.get('[data-testid="onboarding-step-indicator"]').text()).toContain(
      'Passo 1 de 8',
    )
    expect(wrapper.get('[data-testid="next-button"]').text()).toContain('Próximo')
    expect(wrapper.get('[data-testid="skip-button"]').text()).toContain('Pular tutorial')
    expect(wrapper.find('[data-testid="back-button"]').exists()).toBe(false)
  })

  it('avança pelas 4 telas do tutorial e volta com Voltar', async () => {
    const wrapper = await mountView()

    await wrapper.get('[data-testid="next-button"]').trigger('click')
    expect(wrapper.text()).toContain('O que é o orçamento por envelope?')
    await wrapper.get('[data-testid="next-button"]').trigger('click')
    expect(wrapper.text()).toContain('Como funciona no porquinho')
    await wrapper.get('[data-testid="next-button"]').trigger('click')
    expect(wrapper.text()).toContain('Por que funciona?')
    await wrapper.get('[data-testid="next-button"]').trigger('click')
    expect(wrapper.text()).toContain('Pronto para começar?')
    expect(wrapper.get('[data-testid="onboarding-step-indicator"]').text()).toContain(
      'Passo 1 de 8',
    )

    await wrapper.get('[data-testid="back-button"]').trigger('click')
    expect(wrapper.text()).toContain('Por que funciona?')
    expect(mockCompleteTutorial).not.toHaveBeenCalled()
  })

  it('duplo clique em Próximo na última tela salva só uma vez', async () => {
    const wrapper = await mountView()
    await goToLastScreen(wrapper)

    const next = wrapper.get('[data-testid="next-button"]')
    // Mesmo tick: o botão ainda não foi re-renderizado como desabilitado
    void next.trigger('click')
    void next.trigger('click')
    await flushPromises()

    expect(mockCompleteTutorial).toHaveBeenCalledOnce()
    expect(wrapper.find('[data-testid="accounts-step"]').exists()).toBe(true)
  })

  it('Próximo na última tela conclui o tutorial e leva ao passo 2 (contas)', async () => {
    const wrapper = await mountView()
    await goToLastScreen(wrapper)

    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()

    expect(mockCompleteTutorial).toHaveBeenCalledWith(false)
    expect(mockPush).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Adicione suas contas bancárias')
    expect(wrapper.get('[data-testid="onboarding-step-indicator"]').text()).toContain(
      'Passo 2 de 8',
    )
  })

  it('Pular pede confirmação e só pula depois de confirmar', async () => {
    const wrapper = await mountView()

    await wrapper.get('[data-testid="skip-button"]').trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('Pular o tutorial?')
    expect(mockCompleteTutorial).not.toHaveBeenCalled()

    bodyButton('confirm-skip-button').click()
    await flushPromises()

    expect(mockCompleteTutorial).toHaveBeenCalledWith(true)
    expect(mockPush).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Adicione suas contas bancárias')
  })

  it('cancelar a confirmação mantém o usuário no tutorial', async () => {
    const wrapper = await mountView()

    await wrapper.get('[data-testid="skip-button"]').trigger('click')
    await flushPromises()
    bodyButton('cancel-skip-button').click()
    await flushPromises()

    expect(mockCompleteTutorial).not.toHaveBeenCalled()
    expect(mockPush).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Bem-vindo ao porquinho!')
  })

  it('redireciona para o dashboard quando o onboarding já foi concluído', async () => {
    mockGetMyOnboarding.mockResolvedValue(progress({ completed: true, currentStep: 8 }))
    const wrapper = await mountView()

    expect(mockReplace).toHaveBeenCalledWith('/dashboard')
    expect(wrapper.text()).not.toContain('Bem-vindo ao porquinho!')
  })

  it('mostra o passo 2 quando o tutorial já foi feito (passo 2)', async () => {
    mockGetMyOnboarding.mockResolvedValue(
      progress({ currentStep: 2, stepsCompleted: ['tutorial_skipped'] }),
    )
    const wrapper = await mountView()

    expect(mockReplace).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="onboarding-step-indicator"]').text()).toContain(
      'Passo 2 de 8',
    )
    expect(wrapper.text()).toContain('Adicione suas contas bancárias')
    expect(wrapper.text()).not.toContain('Bem-vindo ao porquinho!')
    expect(mockListAccounts).toHaveBeenCalledOnce()
  })

  it('mostra o passo 3 (cartões) quando as contas já foram concluídas', async () => {
    mockGetMyOnboarding.mockResolvedValue(
      progress({ currentStep: 3, stepsCompleted: ['tutorial_viewed', 'accounts_added'] }),
    )
    const wrapper = await mountView()

    expect(mockReplace).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="onboarding-step-indicator"]').text()).toContain(
      'Passo 3 de 8',
    )
    expect(wrapper.text()).toContain('Adicione seus cartões de crédito')
    expect(mockListCards).toHaveBeenCalledOnce()
  })

  it('mostra o passo 4 (categorias) quando os cartões já foram concluídos', async () => {
    mockGetMyOnboarding.mockResolvedValue(
      progress({
        currentStep: 4,
        stepsCompleted: ['tutorial_viewed', 'accounts_added', 'cards_skipped'],
      }),
    )
    const wrapper = await mountView()

    expect(mockReplace).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="onboarding-step-indicator"]').text()).toContain(
      'Passo 4 de 8',
    )
    expect(wrapper.text()).toContain('Categorias de gastos sugeridas')
    expect(mockGetCategorySuggestions).toHaveBeenCalledOnce()
  })

  it('mostra o passo 5 (personalizar) quando as categorias já foram escolhidas', async () => {
    mockGetMyOnboarding.mockResolvedValue(
      progress({ currentStep: 5, stepsCompleted: STEP_FIVE_DONE }),
    )
    const wrapper = await mountView()

    expect(mockReplace).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="onboarding-step-indicator"]').text()).toContain(
      'Passo 5 de 8',
    )
    expect(wrapper.text()).toContain('Personalize suas categorias')
    expect(mockListCategories).toHaveBeenCalledOnce()
  })

  it('redireciona para o dashboard a partir do passo 6', async () => {
    mockGetMyOnboarding.mockResolvedValue(
      progress({ currentStep: 6, stepsCompleted: [...STEP_FIVE_DONE, 'categories_customized'] }),
    )
    await mountView()

    expect(mockReplace).toHaveBeenCalledWith('/dashboard')
  })

  it('concluir o passo 5 leva ao dashboard', async () => {
    mockGetMyOnboarding.mockResolvedValue(
      progress({ currentStep: 5, stepsCompleted: STEP_FIVE_DONE }),
    )
    const wrapper = await mountView()

    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()

    expect(mockCompleteCustomization).toHaveBeenCalledWith(false)
    expect(mockPush).toHaveBeenCalledWith('/dashboard')
  })

  it('Voltar no passo 5 mostra as categorias sugeridas; Próximo ali volta à personalização', async () => {
    mockGetMyOnboarding.mockResolvedValue(
      progress({ currentStep: 5, stepsCompleted: STEP_FIVE_DONE }),
    )
    const wrapper = await mountView()

    await wrapper.get('[data-testid="back-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Categorias de gastos sugeridas')
    expect(wrapper.get('[data-testid="onboarding-step-indicator"]').text()).toContain(
      'Passo 4 de 8',
    )
    expect(mockCompleteCategories).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="categories-read-only"]').exists()).toBe(true)
    expect(
      wrapper.get<HTMLInputElement>('[data-testid="category-checkbox-housing"]').element.disabled,
    ).toBe(true)

    // O POST das categorias é idempotente e devolve o passo 5
    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()

    expect(mockPush).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Personalize suas categorias')
  })

  it('concluir o passo 3 leva ao passo 4 (categorias)', async () => {
    mockGetMyOnboarding.mockResolvedValue(
      progress({ currentStep: 3, stepsCompleted: ['tutorial_viewed', 'accounts_added'] }),
    )
    const wrapper = await mountView()

    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()

    expect(mockCompleteCards).toHaveBeenCalledWith(false)
    expect(mockPush).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Categorias de gastos sugeridas')
    expect(wrapper.get('[data-testid="onboarding-step-indicator"]').text()).toContain(
      'Passo 4 de 8',
    )
  })

  it('concluir o passo 4 cria as categorias marcadas e leva ao passo 5', async () => {
    mockGetMyOnboarding.mockResolvedValue(
      progress({
        currentStep: 4,
        stepsCompleted: ['tutorial_viewed', 'accounts_added', 'cards_added'],
      }),
    )
    const wrapper = await mountView()

    await wrapper.get('[data-testid="category-checkbox-food"]').setValue(false)
    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()

    expect(mockCompleteCategories).toHaveBeenCalledWith(['housing'])
    expect(mockPush).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Personalize suas categorias')
    expect(wrapper.get('[data-testid="onboarding-step-indicator"]').text()).toContain(
      'Passo 5 de 8',
    )
  })

  it('Voltar no passo 4 mostra os cartões sem mexer no progresso; Próximo ali volta às categorias', async () => {
    mockGetMyOnboarding.mockResolvedValue(
      progress({
        currentStep: 4,
        stepsCompleted: ['tutorial_viewed', 'accounts_added', 'cards_added'],
      }),
    )
    const wrapper = await mountView()

    await wrapper.get('[data-testid="back-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Adicione seus cartões de crédito')
    expect(wrapper.get('[data-testid="onboarding-step-indicator"]').text()).toContain(
      'Passo 3 de 8',
    )
    expect(mockCompleteCards).not.toHaveBeenCalled()
    expect(mockCompleteCategories).not.toHaveBeenCalled()

    // O POST dos cartões é idempotente e devolve o passo 4
    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()

    expect(mockCompleteCards).toHaveBeenCalledWith(false)
    expect(mockPush).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Categorias de gastos sugeridas')
  })

  it('Voltar no passo 3 mostra as contas sem mexer no progresso; Próximo ali volta aos cartões', async () => {
    mockGetMyOnboarding.mockResolvedValue(
      progress({ currentStep: 3, stepsCompleted: ['tutorial_viewed', 'accounts_added'] }),
    )
    mockListAccounts.mockResolvedValue([
      {
        id: 'a1',
        accountName: 'Nubank',
        bankName: null,
        accountType: 'CHECKING',
        initialBalance: 10,
        currentBalance: 10,
        createdAt: '2026-11-02T12:00:00Z',
      },
    ])
    const wrapper = await mountView()

    await wrapper.get('[data-testid="back-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Adicione suas contas bancárias')
    expect(wrapper.get('[data-testid="onboarding-step-indicator"]').text()).toContain(
      'Passo 2 de 8',
    )
    expect(mockCompleteAccounts).not.toHaveBeenCalled()
    expect(mockCompleteCards).not.toHaveBeenCalled()

    // O POST das contas é idempotente e devolve o passo 3
    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()

    expect(mockCompleteAccounts).toHaveBeenCalledWith(false)
    expect(mockPush).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Adicione seus cartões de crédito')
    expect(wrapper.get('[data-testid="onboarding-step-indicator"]').text()).toContain(
      'Passo 3 de 8',
    )
  })

  it('Voltar no passo 2 mostra o tutorial sem mexer no progresso; ao terminá-lo volta ao passo 2', async () => {
    mockGetMyOnboarding.mockResolvedValue(
      progress({ currentStep: 2, stepsCompleted: ['tutorial_viewed'] }),
    )
    const wrapper = await mountView()

    await wrapper.get('[data-testid="back-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Pronto para começar?')
    expect(wrapper.get('[data-testid="onboarding-step-indicator"]').text()).toContain(
      'Passo 1 de 8',
    )
    expect(mockCompleteTutorial).not.toHaveBeenCalled()
    expect(mockCompleteAccounts).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()

    expect(mockCompleteTutorial).toHaveBeenCalledWith(false)
    expect(wrapper.text()).toContain('Adicione suas contas bancárias')
  })

  it('concluir o passo 2 leva ao passo 3 (cartões)', async () => {
    mockGetMyOnboarding.mockResolvedValue(
      progress({ currentStep: 2, stepsCompleted: ['tutorial_viewed'] }),
    )
    const wrapper = await mountView()

    await wrapper.get('[data-testid="skip-button"]').trigger('click')
    await flushPromises()

    expect(mockCompleteAccounts).toHaveBeenCalledWith(true)
    expect(mockPush).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Adicione seus cartões de crédito')
    expect(wrapper.get('[data-testid="onboarding-step-indicator"]').text()).toContain(
      'Passo 3 de 8',
    )
  })

  it('mostra erro e continua na tela quando salvar falha', async () => {
    mockCompleteTutorial.mockRejectedValue({ response: { status: 500 } })
    const wrapper = await mountView()
    await goToLastScreen(wrapper)

    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="onboarding-error"]').text()).toContain(
      'Não foi possível salvar',
    )
    expect(mockPush).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('Pronto para começar?')
    expect(wrapper.get('[data-testid="next-button"]').attributes('disabled')).toBeUndefined()
  })

  it('mostra erro e ainda exibe o tutorial quando carregar o progresso falha', async () => {
    mockGetMyOnboarding.mockRejectedValue(new Error('Network Error'))
    const wrapper = await mountView()

    expect(wrapper.get('[data-testid="onboarding-error"]').text()).toContain(
      'Não foi possível carregar',
    )
    expect(wrapper.text()).toContain('Bem-vindo ao porquinho!')
    expect(mockReplace).not.toHaveBeenCalled()
  })
})
