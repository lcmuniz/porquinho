import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import CustomizeCategoriesStep from '../CustomizeCategoriesStep.vue'
import type { Category } from '@/types/category'

const mockListCategories = vi.fn()
const mockGetCategoryOptions = vi.fn()
const mockCreateCategory = vi.fn()
const mockUpdateCategory = vi.fn()
const mockDeleteCategory = vi.fn()
const mockGetCategoryUsage = vi.fn()
const mockCompleteCustomization = vi.fn()

vi.mock('@/services/categoryService', () => ({
  listCategories: (...a: unknown[]) => mockListCategories(...a),
  getCategoryOptions: (...a: unknown[]) => mockGetCategoryOptions(...a),
  createCategory: (...a: unknown[]) => mockCreateCategory(...a),
  updateCategory: (...a: unknown[]) => mockUpdateCategory(...a),
  deleteCategory: (...a: unknown[]) => mockDeleteCategory(...a),
  getCategoryUsage: (...a: unknown[]) => mockGetCategoryUsage(...a),
}))

vi.mock('@/services/onboardingService', () => ({
  completeCustomization: (...a: unknown[]) => mockCompleteCustomization(...a),
}))

function cat(
  id: string,
  categoryName: string,
  parentCategoryId: string | null,
  colorHex = '#3B82F6',
  iconName = 'house',
): Category {
  return {
    id,
    categoryName,
    parentCategoryId,
    colorHex,
    iconName,
    systemDefault: true,
    createdAt: '2026-11-02T12:00:00Z',
  }
}

// Fora de ordem de propósito: a tela ordena por nome em pt-BR
const CATEGORIES: Category[] = [
  cat('g2', 'Variáveis', null, '#9333EA', 'shuffle'),
  cat('c3', 'Lazer', 'g2', '#EC4899', 'party-popper'),
  cat('g1', 'Essenciais', null, '#2563EB', 'shield-check'),
  cat('c1', 'Saúde', 'g1', '#EF4444', 'heart-pulse'),
  cat('c2', 'Alimentação', 'g1', '#F97316', 'utensils'),
  cat('c4', 'Moradia', 'g1'),
]

const OPTIONS = {
  colors: ['#2563EB', '#3B82F6', '#64748B'],
  icons: ['shield-check', 'house', 'paw-print', 'gift'],
}

const PROGRESS = {
  currentStep: 6,
  stepsCompleted: [
    'tutorial_viewed',
    'accounts_added',
    'cards_skipped',
    'categories_suggested',
    'categories_customized',
  ],
  completed: false,
}

async function mountStep() {
  const wrapper = mount(CustomizeCategoriesStep, { attachTo: document.body })
  await flushPromises()
  return wrapper
}

type Wrapper = Awaited<ReturnType<typeof mountStep>>

function nameInput(wrapper: Wrapper, id: string) {
  return wrapper.get<HTMLInputElement>(`[data-testid="category-name-${id}"]`)
}

function bodyButton(testId: string) {
  const button = document.body.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)
  if (!button) throw new Error(`button ${testId} not found`)
  return button
}

function apiError(status: number, detail: string) {
  return { response: { status, data: { detail } } }
}

describe('CustomizeCategoriesStep', () => {
  beforeEach(() => {
    mockListCategories.mockReset().mockResolvedValue(CATEGORIES)
    mockGetCategoryOptions.mockReset().mockResolvedValue(OPTIONS)
    mockCreateCategory.mockReset()
    mockUpdateCategory.mockReset()
    mockDeleteCategory.mockReset().mockResolvedValue(undefined)
    mockGetCategoryUsage.mockReset().mockResolvedValue({ transactionCount: 0 })
    mockCompleteCustomization.mockReset().mockResolvedValue(PROGRESS)
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('mostra o skeleton enquanto carrega', async () => {
    mockListCategories.mockReturnValue(new Promise(() => {}))
    const wrapper = mount(CustomizeCategoriesStep)

    expect(wrapper.find('[data-testid="customize-skeleton"]').exists()).toBe(true)
  })

  it('lista grupos e categorias em ordem alfabética (pt-BR)', async () => {
    const wrapper = await mountStep()

    expect(wrapper.text()).toContain('Personalize suas categorias')
    const names = wrapper
      .findAll<HTMLInputElement>('input[data-testid^="category-name-"]')
      .map((i) => i.element.value)
    expect(names).toEqual(['Essenciais', 'Alimentação', 'Moradia', 'Saúde', 'Variáveis', 'Lazer'])
  })

  it('falha ao carregar mostra o aviso e tenta de novo', async () => {
    mockListCategories.mockRejectedValueOnce(new Error('offline'))
    const wrapper = await mountStep()

    expect(wrapper.find('[data-testid="customize-load-failed"]').exists()).toBe(true)
    await wrapper.get('[data-testid="retry-button"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="category-name-c3"]').exists()).toBe(true)
  })

  it('renomeia ao sair do campo e mostra o nome novo', async () => {
    mockUpdateCategory.mockResolvedValue(cat('c3', 'Diversão', 'g2', '#EC4899', 'party-popper'))
    const wrapper = await mountStep()

    await nameInput(wrapper, 'c3').setValue('  Diversão ')
    await nameInput(wrapper, 'c3').trigger('blur')
    await flushPromises()

    expect(mockUpdateCategory).toHaveBeenCalledWith('c3', { name: 'Diversão' })
    expect(nameInput(wrapper, 'c3').element.value).toBe('Diversão')
  })

  it('Enter salva (uma vez só) e Esc cancela', async () => {
    mockUpdateCategory.mockResolvedValue(cat('c4', 'Casa', 'g1'))
    const wrapper = await mountStep()

    await nameInput(wrapper, 'c4').setValue('Casa')
    nameInput(wrapper, 'c4').element.focus()
    await nameInput(wrapper, 'c4').trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(mockUpdateCategory).toHaveBeenCalledTimes(1)

    await nameInput(wrapper, 'c2').setValue('Comida')
    nameInput(wrapper, 'c2').element.focus()
    await nameInput(wrapper, 'c2').trigger('keydown', { key: 'Escape' })
    await flushPromises()
    expect(nameInput(wrapper, 'c2').element.value).toBe('Alimentação')
    expect(mockUpdateCategory).toHaveBeenCalledTimes(1)
  })

  it('nome repetido (409) volta ao valor anterior e mostra o erro', async () => {
    mockUpdateCategory.mockRejectedValue(apiError(409, 'Já existe uma categoria com esse nome'))
    const wrapper = await mountStep()

    await nameInput(wrapper, 'c4').setValue('Alimentação')
    await nameInput(wrapper, 'c4').trigger('blur')
    await flushPromises()

    expect(nameInput(wrapper, 'c4').element.value).toBe('Moradia')
    expect(wrapper.get('[data-testid="category-error-c4"]').text()).toBe(
      'Já existe uma categoria com esse nome',
    )
  })

  it('nome vazio não chama a API, volta ao anterior e avisa', async () => {
    const wrapper = await mountStep()

    await nameInput(wrapper, 'c4').setValue('   ')
    await nameInput(wrapper, 'c4').trigger('blur')
    await flushPromises()

    expect(mockUpdateCategory).not.toHaveBeenCalled()
    expect(nameInput(wrapper, 'c4').element.value).toBe('Moradia')
    expect(wrapper.get('[data-testid="category-error-c4"]').text()).toBe(
      'Informe o nome da categoria',
    )
  })

  it('o título do grupo também é editável', async () => {
    mockUpdateCategory.mockResolvedValue(cat('g1', 'Básicos', null, '#2563EB', 'shield-check'))
    const wrapper = await mountStep()

    await nameInput(wrapper, 'g1').setValue('Básicos')
    await nameInput(wrapper, 'g1').trigger('blur')
    await flushPromises()

    expect(mockUpdateCategory).toHaveBeenCalledWith('g1', { name: 'Básicos' })
    expect(nameInput(wrapper, 'g1').element.value).toBe('Básicos')
  })

  it('troca a cor e o ícone pelo popover e salva ao escolher', async () => {
    mockUpdateCategory
      .mockResolvedValueOnce(cat('c4', 'Moradia', 'g1', '#64748B', 'house'))
      .mockResolvedValueOnce(cat('c4', 'Moradia', 'g1', '#64748B', 'paw-print'))
    const wrapper = await mountStep()

    const trigger = wrapper.get('[data-testid="category-picker-trigger-c4"]')
    await trigger.trigger('click')
    await flushPromises()
    const picker = wrapper.get('[data-testid="category-picker-c4"]')
    expect(picker.attributes('role')).toBe('dialog')
    expect(trigger.attributes('aria-expanded')).toBe('true')
    // foco vai para a primeira opção (teclado)
    expect(document.activeElement?.getAttribute('data-testid')).toBe('color-option-#2563EB')

    await picker.get('[data-testid="color-option-#64748B"]').trigger('click')
    await flushPromises()
    await wrapper
      .get('[data-testid="category-picker-c4"] [data-testid="icon-option-paw-print"]')
      .trigger('click')
    await flushPromises()

    expect(mockUpdateCategory).toHaveBeenNthCalledWith(1, 'c4', { colorHex: '#64748B' })
    expect(mockUpdateCategory).toHaveBeenNthCalledWith(2, 'c4', { iconName: 'paw-print' })
    expect(wrapper.get('[data-testid="icon-option-paw-print"]').attributes('aria-pressed')).toBe(
      'true',
    )

    // Esc fecha o popover e devolve o foco ao botão
    await wrapper.get('[data-testid="category-item-c4"]').trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('[data-testid="category-picker-c4"]').exists()).toBe(false)
    expect(document.activeElement).toBe(trigger.element)
  })

  it('escolher a cor atual não chama a API', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="category-picker-trigger-c4"]').trigger('click')
    await wrapper.get('[data-testid="color-option-#3B82F6"]').trigger('click')
    await flushPromises()

    expect(mockUpdateCategory).not.toHaveBeenCalled()
  })

  it('adiciona uma categoria no grupo com a cor e o ícone do grupo', async () => {
    mockCreateCategory.mockResolvedValue(cat('c9', 'Pets', 'g1', '#2563EB', 'shield-check'))
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="add-category-g1"]').trigger('click')
    await wrapper.get('[data-testid="new-category-name"]').setValue(' Pets ')
    await wrapper.get('[data-testid="add-form-g1"]').trigger('submit')
    await flushPromises()

    expect(mockCreateCategory).toHaveBeenCalledWith({
      name: 'Pets',
      colorHex: '#2563EB',
      iconName: 'shield-check',
      parentId: 'g1',
    })
    expect(wrapper.find('[data-testid="add-form-g1"]').exists()).toBe(false)
    const names = wrapper
      .findAll<HTMLInputElement>('[data-testid="customize-group-g1"] input')
      .map((i) => i.element.value)
    expect(names).toEqual(['Essenciais', 'Alimentação', 'Moradia', 'Pets', 'Saúde'])
  })

  it('erro ao adicionar mantém o formulário com a mensagem', async () => {
    mockCreateCategory.mockRejectedValue(apiError(409, 'Já existe uma categoria com esse nome'))
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="add-category-g1"]').trigger('click')
    await wrapper.get('[data-testid="new-category-name"]').setValue('Moradia')
    await wrapper.get('[data-testid="add-form-g1"]').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-testid="add-category-error"]').text()).toBe(
      'Já existe uma categoria com esse nome',
    )
    expect(wrapper.find('[data-testid="add-form-g1"]').exists()).toBe(true)
  })

  it('excluir pede confirmação e só exclui depois de confirmar', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="category-delete-c4"]').trigger('click')
    await flushPromises()
    expect(document.body.textContent).toContain('Excluir "Moradia"?')
    expect(mockDeleteCategory).not.toHaveBeenCalled()

    bodyButton('cancel-delete-button').click()
    await flushPromises()
    expect(mockDeleteCategory).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="category-delete-c4"]').trigger('click')
    await flushPromises()
    bodyButton('confirm-delete-button').click()
    await flushPromises()

    expect(mockDeleteCategory).toHaveBeenCalledWith('c4', undefined)
    expect(wrapper.find('[data-testid="category-name-c4"]').exists()).toBe(false)
  })

  it('sem transações não mostra o seletor de destino', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="category-delete-c4"]').trigger('click')
    await flushPromises()

    expect(mockGetCategoryUsage).toHaveBeenCalledWith('c4')
    expect(document.body.querySelector('[data-testid="delete-target"]')).toBeNull()
    expect(bodyButton('confirm-delete-button').disabled).toBe(false)
  })

  it('falha ao buscar a contagem segue sem destino', async () => {
    mockGetCategoryUsage.mockRejectedValue(new Error('rede'))
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="category-delete-c4"]').trigger('click')
    await flushPromises()

    expect(document.body.querySelector('[data-testid="delete-target"]')).toBeNull()
    expect(bodyButton('confirm-delete-button').disabled).toBe(false)
    bodyButton('confirm-delete-button').click()
    await flushPromises()
    expect(mockDeleteCategory).toHaveBeenCalledWith('c4', undefined)
  })

  it('enquanto a contagem carrega, Excluir fica desabilitado', async () => {
    mockGetCategoryUsage.mockReturnValue(new Promise(() => {}))
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="category-delete-c4"]').trigger('click')
    await flushPromises()

    expect(bodyButton('confirm-delete-button').disabled).toBe(true)
  })

  it('com transações exige o destino e envia targetCategoryId', async () => {
    mockGetCategoryUsage.mockResolvedValue({ transactionCount: 3 })
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="category-delete-c4"]').trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('3 transações vão para:')
    const select = document.body.querySelector<HTMLSelectElement>('[data-testid="delete-target"]')!
    // só as outras filhas, sem grupos e sem a própria
    expect([...select.options].map((o) => o.value)).toEqual(['', 'c2', 'c3', 'c1'])
    expect(bodyButton('confirm-delete-button').disabled).toBe(true)

    select.value = 'c2'
    select.dispatchEvent(new Event('change'))
    await flushPromises()
    expect(bodyButton('confirm-delete-button').disabled).toBe(false)
    bodyButton('confirm-delete-button').click()
    await flushPromises()

    expect(mockDeleteCategory).toHaveBeenCalledWith('c4', 'c2')
    expect(wrapper.find('[data-testid="category-name-c4"]').exists()).toBe(false)
  })

  it('excluir a última categoria (409) mantém a lista e emite o erro', async () => {
    mockDeleteCategory.mockRejectedValue(apiError(409, 'Mantenha ao menos uma categoria'))
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="category-delete-c3"]').trigger('click')
    await flushPromises()
    bodyButton('confirm-delete-button').click()
    await flushPromises()

    expect(wrapper.find('[data-testid="category-name-c3"]').exists()).toBe(true)
    expect(wrapper.emitted('error')?.[0]).toEqual(['Mantenha ao menos uma categoria'])
  })

  it('só grupos vazios mostram o botão de excluir', async () => {
    mockListCategories.mockResolvedValue([
      ...CATEGORIES,
      cat('g3', 'Financeiro', null, '#059669', 'landmark'),
    ])
    const wrapper = await mountStep()

    expect(wrapper.find('[data-testid="category-delete-g1"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="category-delete-g3"]').exists()).toBe(true)
  })

  it('Próximo conclui o passo e emite done; duplo clique salva uma vez', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()

    expect(mockCompleteCustomization).toHaveBeenCalledTimes(1)
    expect(mockCompleteCustomization).toHaveBeenCalledWith(false)
    expect(wrapper.emitted('done')?.[0]).toEqual([PROGRESS])
  })

  it('Próximo espera o nome que está sendo salvo antes de avançar', async () => {
    let resolveUpdate: (c: Category) => void = () => {}
    mockUpdateCategory.mockReturnValue(new Promise<Category>((r) => (resolveUpdate = r)))
    const wrapper = await mountStep()

    await nameInput(wrapper, 'c3').setValue('Diversão')
    await nameInput(wrapper, 'c3').trigger('blur')
    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()
    expect(mockCompleteCustomization).not.toHaveBeenCalled()

    resolveUpdate(cat('c3', 'Diversão', 'g2'))
    await flushPromises()
    expect(mockCompleteCustomization).toHaveBeenCalledWith(false)
  })

  it('Pular personalização conclui com skipped=true', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="skip-button"]').trigger('click')
    await flushPromises()

    expect(mockCompleteCustomization).toHaveBeenCalledWith(true)
    expect(wrapper.emitted('done')).toHaveLength(1)
  })

  it('falha ao concluir libera os botões e emite o erro', async () => {
    mockCompleteCustomization.mockRejectedValue(new Error('offline'))
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('error')?.[0]?.[0]).toContain('Não foi possível salvar seu progresso')
    expect(wrapper.get('[data-testid="next-button"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.emitted('done')).toBeUndefined()
  })

  it('Voltar emite back', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="back-button"]').trigger('click')

    expect(wrapper.emitted('back')).toHaveLength(1)
  })

  it('alvos de toque têm ao menos 44px', async () => {
    const wrapper = await mountStep()

    expect(wrapper.get('[data-testid="category-picker-trigger-c4"]').classes()).toContain('h-11')
    expect(wrapper.get('[data-testid="category-delete-c4"]').classes()).toContain('h-11')
    expect(nameInput(wrapper, 'c4').classes()).toContain('min-h-[44px]')
    expect(wrapper.get('[data-testid="add-category-g1"]').classes()).toContain('min-h-[44px]')
  })

  // ---- Ajustes da revisão ----

  it('as cores têm nome em pt-BR no leitor de tela', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="category-picker-trigger-c4"]').trigger('click')

    expect(wrapper.get('[data-testid="color-option-#64748B"]').attributes('aria-label')).toBe(
      'Cinza',
    )
    expect(wrapper.get('[data-testid="color-option-#3B82F6"]').attributes('aria-label')).toBe(
      'Azul',
    )
  })

  it('enquanto uma cor é salva, as opções ficam indisponíveis (sem PATCH fora de ordem)', async () => {
    let resolve: (c: Category) => void = () => {}
    mockUpdateCategory.mockReturnValue(new Promise<Category>((r) => (resolve = r)))
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="category-picker-trigger-c4"]').trigger('click')
    await wrapper.get('[data-testid="color-option-#64748B"]').trigger('click')
    expect(wrapper.get('[data-testid="icon-option-gift"]').attributes('aria-disabled')).toBe('true')
    await wrapper.get('[data-testid="icon-option-gift"]').trigger('click')
    await wrapper.get('[data-testid="color-option-#2563EB"]').trigger('click')
    expect(mockUpdateCategory).toHaveBeenCalledTimes(1)

    resolve(cat('c4', 'Moradia', 'g1', '#64748B', 'house'))
    await flushPromises()
    expect(
      wrapper.get('[data-testid="icon-option-gift"]').attributes('aria-disabled'),
    ).toBeUndefined()
  })

  it('o foco saindo do popover fecha o popover', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="category-picker-trigger-c4"]').trigger('click')
    const outside = wrapper.get('[data-testid="category-name-c1"]').element
    await wrapper
      .get('[data-testid="color-option-#64748B"]')
      .trigger('focusout', { relatedTarget: outside })

    expect(wrapper.find('[data-testid="category-picker-c4"]').exists()).toBe(false)
  })

  it('o nome que o usuário voltou a digitar não é sobrescrito quando o save anterior termina', async () => {
    let resolve: (c: Category) => void = () => {}
    mockUpdateCategory.mockReturnValue(new Promise<Category>((r) => (resolve = r)))
    const wrapper = await mountStep()

    await nameInput(wrapper, 'c3').setValue('Diversão')
    await nameInput(wrapper, 'c3').trigger('blur')
    await nameInput(wrapper, 'c3').trigger('focus')
    await nameInput(wrapper, 'c3').setValue('Diversão e')
    resolve(cat('c3', 'Diversão', 'g2', '#EC4899', 'party-popper'))
    await flushPromises()

    expect(nameInput(wrapper, 'c3').element.value).toBe('Diversão e')
  })

  it('nome só com caracteres invisíveis não chama a API', async () => {
    const wrapper = await mountStep()

    await nameInput(wrapper, 'c4').setValue('\u200B\uFEFF')
    await nameInput(wrapper, 'c4').trigger('blur')
    await flushPromises()

    expect(mockUpdateCategory).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="category-error-c4"]').text()).toBe(
      'Informe o nome da categoria',
    )
  })

  it('Próximo não avança quando o save pendente falha', async () => {
    let reject: (e: unknown) => void = () => {}
    mockUpdateCategory.mockReturnValue(new Promise<Category>((_, r) => (reject = r)))
    const wrapper = await mountStep()

    await nameInput(wrapper, 'c3').setValue('Alimentação')
    await nameInput(wrapper, 'c3').trigger('blur')
    await wrapper.get('[data-testid="next-button"]').trigger('click')
    reject(apiError(409, 'Já existe uma categoria com esse nome'))
    await flushPromises()

    expect(mockCompleteCustomization).not.toHaveBeenCalled()
    expect(wrapper.emitted('done')).toBeUndefined()
    expect(wrapper.emitted('error')?.[0]?.[0]).toContain('Uma alteração não foi salva')
    expect(wrapper.get('[data-testid="category-error-c3"]').text()).toBe(
      'Já existe uma categoria com esse nome',
    )
    expect(wrapper.get('[data-testid="next-button"]').attributes('disabled')).toBeUndefined()
  })

  it('enquanto conclui, o formulário de nova categoria fica desabilitado', async () => {
    mockCompleteCustomization.mockReturnValue(new Promise(() => {}))
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="add-category-g1"]').trigger('click')
    await wrapper.get('[data-testid="new-category-name"]').setValue('Pets')
    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="new-category-name"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="save-add-button"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="add-form-g1"]').trigger('submit')
    await flushPromises()
    expect(mockCreateCategory).not.toHaveBeenCalled()
  })

  it('excluir uma categoria que já não existe (404) só a tira da lista', async () => {
    mockDeleteCategory.mockRejectedValue(apiError(404, 'Categoria não encontrada'))
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="category-delete-c4"]').trigger('click')
    await flushPromises()
    bodyButton('confirm-delete-button').click()
    await flushPromises()

    expect(wrapper.find('[data-testid="category-name-c4"]').exists()).toBe(false)
    expect(wrapper.emitted('error')).toBeUndefined()
  })
})
