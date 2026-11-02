import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import CategoriesStep from '../CategoriesStep.vue'
import type { CategorySuggestionGroup } from '@/types/category'

const mockGetSuggestions = vi.fn()
const mockCompleteCategories = vi.fn()
const mockListCategories = vi.fn()

vi.mock('@/services/categoryService', () => ({
  getCategorySuggestions: (...a: unknown[]) => mockGetSuggestions(...a),
  listCategories: (...a: unknown[]) => mockListCategories(...a),
}))

vi.mock('@/services/onboardingService', () => ({
  completeCategories: (...a: unknown[]) => mockCompleteCategories(...a),
}))

const SUGGESTIONS: CategorySuggestionGroup[] = [
  {
    key: 'essentials',
    name: 'Essenciais',
    iconName: 'shield-check',
    colorHex: '#2563EB',
    suggestions: [
      { key: 'housing', name: 'Moradia', iconName: 'house', colorHex: '#3B82F6' },
      { key: 'food', name: 'Alimentação', iconName: 'utensils', colorHex: '#F97316' },
      { key: 'transport', name: 'Transporte', iconName: 'car', colorHex: '#0EA5E9' },
      { key: 'health', name: 'Saúde', iconName: 'heart-pulse', colorHex: '#EF4444' },
    ],
  },
  {
    key: 'variable',
    name: 'Variáveis',
    iconName: 'shuffle',
    colorHex: '#9333EA',
    suggestions: [
      { key: 'leisure', name: 'Lazer', iconName: 'party-popper', colorHex: '#EC4899' },
      { key: 'education', name: 'Educação', iconName: 'graduation-cap', colorHex: '#8B5CF6' },
      { key: 'clothing', name: 'Vestuário', iconName: 'shirt', colorHex: '#F59E0B' },
      { key: 'subscriptions', name: 'Assinaturas', iconName: 'repeat', colorHex: '#6366F1' },
    ],
  },
  {
    key: 'financial',
    name: 'Financeiro',
    iconName: 'landmark',
    colorHex: '#059669',
    suggestions: [
      { key: 'investments', name: 'Investimentos', iconName: 'trending-up', colorHex: '#10B981' },
      {
        key: 'emergency_fund',
        name: 'Reserva de Emergência',
        iconName: 'piggy-bank',
        colorHex: '#14B8A6',
      },
    ],
  },
]

const ALL_KEYS = SUGGESTIONS.flatMap((g) => g.suggestions.map((s) => s.key))

const nextProgress = {
  currentStep: 5,
  stepsCompleted: ['tutorial_viewed', 'accounts_added', 'cards_added', 'categories_suggested'],
  completed: false,
}

async function mountStep() {
  const wrapper = mount(CategoriesStep, { attachTo: document.body })
  await flushPromises()
  return wrapper
}

type Wrapper = Awaited<ReturnType<typeof mountStep>>

function checkbox(wrapper: Wrapper, key: string) {
  return wrapper.get<HTMLInputElement>(`[data-testid="category-checkbox-${key}"]`)
}

async function uncheck(wrapper: Wrapper, ...keys: string[]) {
  for (const key of keys) await checkbox(wrapper, key).setValue(false)
}

function nextButton(wrapper: Wrapper) {
  return wrapper.get<HTMLButtonElement>('[data-testid="next-button"]')
}

describe('CategoriesStep', () => {
  beforeEach(() => {
    mockGetSuggestions.mockReset().mockResolvedValue(SUGGESTIONS)
    mockCompleteCategories.mockReset().mockResolvedValue(nextProgress)
    mockListCategories.mockReset().mockResolvedValue([])
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('mostra o título, os 3 grupos como títulos e as 10 sugestões marcadas, sem botão Pular', async () => {
    const wrapper = await mountStep()

    expect(wrapper.text()).toContain('Categorias de gastos sugeridas')
    for (const group of ['essentials', 'variable', 'financial']) {
      expect(wrapper.find(`[data-testid="category-group-${group}"]`).exists()).toBe(true)
    }
    const legends = wrapper.findAll('legend').map((l) => l.text())
    expect(legends).toEqual(['Essenciais', 'Variáveis', 'Financeiro'])
    // Grupos sem checkbox próprio: só as 10 sugestões
    expect(wrapper.findAll('input[type="checkbox"]')).toHaveLength(10)
    for (const key of ALL_KEYS) expect(checkbox(wrapper, key).element.checked).toBe(true)
    expect(wrapper.text()).toContain('Reserva de Emergência')
    expect(wrapper.find('[data-testid="skip-button"]').exists()).toBe(false)
    expect(nextButton(wrapper).element.disabled).toBe(false)
    expect(wrapper.find('[data-testid="categories-none-hint"]').exists()).toBe(false)
  })

  it('cada card tem checkbox nativo com label, ícone e alvo de pelo menos 44px', async () => {
    const wrapper = await mountStep()

    const card = wrapper.get('[data-testid="category-card-housing"]')
    expect(card.element.tagName).toBe('LABEL')
    expect(card.attributes('for')).toBe('category-housing')
    expect(card.classes()).toContain('min-h-[44px]')
    expect(card.find('input#category-housing[type="checkbox"]').exists()).toBe(true)
    expect(card.find('svg').exists()).toBe(true)
    expect(card.text()).toContain('Moradia')
  })

  it('clicar no card (label) desmarca e marca de novo', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="category-card-leisure"]').trigger('click')
    expect(checkbox(wrapper, 'leisure').element.checked).toBe(false)

    await wrapper.get('[data-testid="category-card-leisure"]').trigger('click')
    expect(checkbox(wrapper, 'leisure').element.checked).toBe(true)
  })

  it('Próximo envia só as marcadas, na ordem do catálogo, e emite done com o progresso', async () => {
    const wrapper = await mountStep()
    await uncheck(wrapper, 'clothing', 'subscriptions')

    await nextButton(wrapper).trigger('click')
    await flushPromises()

    expect(mockCompleteCategories).toHaveBeenCalledWith(
      ALL_KEYS.filter((k) => k !== 'clothing' && k !== 'subscriptions'),
    )
    expect(wrapper.emitted('done')).toEqual([[nextProgress]])
  })

  it('sem nenhuma marcada, Próximo fica desabilitado com a dica', async () => {
    const wrapper = await mountStep()
    await uncheck(wrapper, ...ALL_KEYS)

    expect(nextButton(wrapper).element.disabled).toBe(true)
    const hint = wrapper.get('[data-testid="categories-none-hint"]')
    expect(hint.text()).toBe('Escolha ao menos uma categoria')
    expect(nextButton(wrapper).attributes('aria-describedby')).toBe('categories-none-hint')

    await nextButton(wrapper).trigger('click')
    await flushPromises()
    expect(mockCompleteCategories).not.toHaveBeenCalled()

    await checkbox(wrapper, 'health').setValue(true)
    expect(nextButton(wrapper).element.disabled).toBe(false)
    expect(wrapper.find('[data-testid="categories-none-hint"]').exists()).toBe(false)
  })

  it('duplo clique em Próximo salva uma vez só', async () => {
    let resolve: (v: unknown) => void = () => {}
    mockCompleteCategories.mockReturnValue(new Promise((r) => (resolve = r)))
    const wrapper = await mountStep()

    await nextButton(wrapper).trigger('click')
    await nextButton(wrapper).trigger('click')
    expect(nextButton(wrapper).text()).toContain('Salvando...')
    expect(nextButton(wrapper).element.disabled).toBe(true)
    resolve(nextProgress)
    await flushPromises()

    expect(mockCompleteCategories).toHaveBeenCalledOnce()
  })

  it('falha ao salvar mostra o erro da API e libera os botões', async () => {
    mockCompleteCategories.mockRejectedValue({
      response: {
        status: 409,
        data: { detail: 'Conclua os passos anteriores antes de escolher suas categorias.' },
      },
    })
    const wrapper = await mountStep()

    await nextButton(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.emitted('error')).toEqual([
      ['Conclua os passos anteriores antes de escolher suas categorias.'],
    ])
    expect(wrapper.emitted('done')).toBeUndefined()
    expect(nextButton(wrapper).element.disabled).toBe(false)
  })

  it('falha de rede ao salvar usa a mensagem padrão', async () => {
    mockCompleteCategories.mockRejectedValue(new Error('Network Error'))
    const wrapper = await mountStep()

    await nextButton(wrapper).trigger('click')
    await flushPromises()

    expect(wrapper.emitted('error')?.[0]?.[0]).toContain('Não foi possível salvar suas categorias')
  })

  it('mostra skeleton enquanto carrega as sugestões, com Próximo desabilitado', async () => {
    let resolve: (v: unknown) => void = () => {}
    mockGetSuggestions.mockReturnValue(new Promise((r) => (resolve = r)))
    const wrapper = mount(CategoriesStep, { attachTo: document.body })
    await flushPromises()

    expect(wrapper.find('[data-testid="categories-skeleton"]').exists()).toBe(true)
    expect(nextButton(wrapper).element.disabled).toBe(true)
    expect(wrapper.find('[data-testid="categories-none-hint"]').exists()).toBe(false)

    resolve(SUGGESTIONS)
    await flushPromises()
    expect(wrapper.find('[data-testid="categories-skeleton"]').exists()).toBe(false)
    expect(nextButton(wrapper).element.disabled).toBe(false)
  })

  it('falha ao carregar as sugestões mostra o aviso na tela (sem toast) e permite tentar de novo', async () => {
    mockGetSuggestions.mockRejectedValueOnce(new Error('Network Error'))
    const wrapper = await mountStep()

    expect(wrapper.emitted('error')).toBeUndefined()
    expect(wrapper.get('[data-testid="categories-load-failed"]').text()).toContain(
      'Não foi possível carregar as sugestões',
    )
    expect(nextButton(wrapper).element.disabled).toBe(true)

    await wrapper.get('[data-testid="retry-button"]').trigger('click')
    await flushPromises()

    expect(mockGetSuggestions).toHaveBeenCalledTimes(2)
    expect(wrapper.findAll('input[type="checkbox"]')).toHaveLength(10)
    expect(nextButton(wrapper).element.disabled).toBe(false)
  })

  it('Voltar emite back sem salvar', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="back-button"]').trigger('click')

    expect(wrapper.emitted('back')).toHaveLength(1)
    expect(mockCompleteCategories).not.toHaveBeenCalled()
  })

  it('foca o título ao aparecer', async () => {
    const wrapper = await mountStep()

    expect(document.activeElement).toBe(wrapper.get('h1').element)
  })

  it('só leitura (Voltar do passo 5): checkboxes desabilitados, marca as que o usuário tem e Próximo usa o POST', async () => {
    mockListCategories.mockResolvedValue([
      { id: 'g1', categoryName: 'Essenciais', parentCategoryId: null },
      { id: 'c1', categoryName: 'Moradia', parentCategoryId: 'g1' },
    ])
    const wrapper = mount(CategoriesStep, { attachTo: document.body, props: { readOnly: true } })
    await flushPromises()

    expect(wrapper.find('[data-testid="categories-read-only"]').exists()).toBe(true)
    expect(checkbox(wrapper, 'housing').element.disabled).toBe(true)
    expect(checkbox(wrapper, 'housing').element.checked).toBe(true)
    expect(checkbox(wrapper, 'food').element.checked).toBe(false)

    await nextButton(wrapper).trigger('click')
    await flushPromises()
    expect(mockCompleteCategories).toHaveBeenCalledWith(['housing'])
    expect(wrapper.emitted('done')?.[0]).toEqual([nextProgress])
  })

  it('fora do modo só leitura não lê as categorias do usuário', async () => {
    const wrapper = await mountStep()

    expect(mockListCategories).not.toHaveBeenCalled()
    expect(checkbox(wrapper, 'housing').element.disabled).toBe(false)
  })
})
