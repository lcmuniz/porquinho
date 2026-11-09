import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub, type VueWrapper } from '@vue/test-utils'
import CategoriesView from '../CategoriesView.vue'
import type { Category } from '@/types/category'

const mockList = vi.fn()
const mockCreate = vi.fn()
const mockUpdate = vi.fn()

vi.mock('@/services/categoryService', () => ({
  listCategories: () => mockList(),
  getCategoryOptions: async () => ({ colors: ['#2563EB', '#EC4899'], icons: ['house', 'plane', 'folder'] }),
  createCategory: (...a: unknown[]) => mockCreate(...a),
  updateCategory: (...a: unknown[]) => mockUpdate(...a),
}))

function category(id: string, name: string, parentCategoryId: string | null, colorHex = '#2563EB'): Category {
  return {
    id,
    categoryName: name,
    parentCategoryId,
    colorHex,
    iconName: 'house',
    systemDefault: false,
    createdAt: '2026-11-09T12:00:00Z',
  }
}

const LIST = [
  category('g2', 'Variáveis', null, '#EC4899'),
  category('g1', 'Essenciais', null),
  category('c1', 'Moradia', 'g1'),
]

let wrapper: VueWrapper | null = null

async function mountView() {
  wrapper = mount(CategoriesView, { attachTo: document.body, global: { stubs: { RouterLink: RouterLinkStub } } })
  await flushPromises()
  return wrapper
}

const inDialog = <T extends Element = HTMLElement>(testId: string) =>
  document.body.querySelector<T>(`[data-testid="${testId}"]`)

async function openDialog(name: string) {
  await wrapper!.get('[data-testid="add-category"]').trigger('click')
  await flushPromises()
  const input = inDialog<HTMLInputElement>('category-name')!
  input.value = name
  input.dispatchEvent(new Event('input'))
  await flushPromises()
}

async function submit() {
  inDialog<HTMLFormElement>('category-form')!.dispatchEvent(new Event('submit'))
  await flushPromises()
}

describe('CategoriesView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockList.mockResolvedValue(LIST)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
  })

  it('lista os grupos por nome com as categorias de cada um', async () => {
    await mountView()
    const titles = wrapper!.findAll('h2').map((h) => h.text().trim())
    expect(titles).toEqual(['Essenciais', 'Variáveis'])
    expect(wrapper!.get('[data-testid="group-g1"]').text()).toContain('Moradia')
    expect(wrapper!.get('[data-testid="group-g2"]').text()).toContain('Nenhuma categoria neste grupo')
  })

  it('cria só com nome e grupo (primeiro grupo pré-selecionado), sem enviar cor nem ícone', async () => {
    await mountView()
    mockCreate.mockResolvedValue({ ...category('c9', 'Pets', 'g1'), iconName: 'folder' })
    await openDialog('  Pets ')
    expect(inDialog<HTMLSelectElement>('category-group')!.value).toBe('g1')
    expect(inDialog('color-option-auto')!.getAttribute('aria-pressed')).toBe('true')
    expect(inDialog('icon-option-default')!.getAttribute('aria-pressed')).toBe('true')

    await submit()

    expect(mockCreate).toHaveBeenCalledWith({ name: 'Pets', parentId: 'g1' })
    expect(inDialog('category-dialog')).toBeNull()
    expect(wrapper!.get('[data-testid="group-g1"]').text()).toContain('Pets')
    expect(wrapper!.get('[data-testid="categories-success"]').text()).toBe('Categoria criada')
  })

  it('envia a cor, o ícone e o grupo escolhidos', async () => {
    await mountView()
    mockCreate.mockResolvedValue(category('c9', 'Viagem', 'g2', '#EC4899'))
    await openDialog('Viagem')
    const select = inDialog<HTMLSelectElement>('category-group')!
    select.value = 'g2'
    select.dispatchEvent(new Event('change'))
    inDialog<HTMLButtonElement>('color-option-#EC4899')!.click()
    inDialog<HTMLButtonElement>('icon-option-plane')!.click()
    await flushPromises()

    await submit()

    expect(mockCreate).toHaveBeenCalledWith({ name: 'Viagem', parentId: 'g2', colorHex: '#EC4899', iconName: 'plane' })
    expect(wrapper!.get('[data-testid="group-g2"]').text()).toContain('Viagem')
  })

  it('mostra o 409 no campo nome e mantém o modal aberto', async () => {
    await mountView()
    mockCreate.mockRejectedValue({
      response: { status: 409, data: { detail: 'Já existe uma categoria com esse nome' } },
    })
    await openDialog('moradia')

    await submit()

    const error = inDialog('category-name-error')!
    expect(error.textContent).toContain('Já existe uma categoria com esse nome')
    expect(error.getAttribute('role')).toBe('alert')
    expect(inDialog('category-name')!.getAttribute('aria-describedby')).toBe('category-name-error')
    expect(inDialog('category-dialog')).not.toBeNull()
  })

  it('bloqueia nome vazio sem chamar a API', async () => {
    await mountView()
    await openDialog('   ')

    await submit()

    expect(mockCreate).not.toHaveBeenCalled()
    expect(inDialog('category-name-error')!.textContent).toContain('Informe o nome da categoria')
  })

  it('mostra outros erros da API no topo do modal', async () => {
    await mountView()
    mockCreate.mockRejectedValue({ response: { status: 400, data: { detail: 'Escolha uma cor da paleta' } } })
    await openDialog('Pets')

    await submit()

    expect(inDialog('category-form-error')!.textContent).toContain('Escolha uma cor da paleta')
    expect(inDialog('category-name-error')).toBeNull()
  })

  it('sem grupos mostra o estado vazio e deixa criar o primeiro grupo', async () => {
    mockList.mockResolvedValue([])
    await mountView()
    expect(wrapper!.get('[data-testid="categories-empty"]').text()).toContain('criar o primeiro grupo')
    expect(wrapper!.get('[data-testid="add-category"]').attributes('disabled')).toBeUndefined()
    await openDialog('Casa')
    expect(inDialog<HTMLSelectElement>('category-group')!.value).toBe('')
  })

  it('cria um grupo com "Nenhum (criar como grupo)" e ele vira opção no modal', async () => {
    await mountView()
    mockCreate.mockResolvedValue({ ...category('g3', 'Casa', null, '#3B82F6'), iconName: 'folder' })
    await openDialog('Casa')
    const select = inDialog<HTMLSelectElement>('category-group')!
    expect(select.options[0]!.text).toBe('Nenhum (criar como grupo)')
    select.value = ''
    select.dispatchEvent(new Event('change'))
    await flushPromises()
    expect(inDialog('category-dialog')!.textContent).toContain('o grupo recebe uma cor livre da paleta')
    expect(inDialog('category-dialog')!.textContent).toContain('Adicionar grupo')

    await submit()

    expect(mockCreate).toHaveBeenCalledWith({ name: 'Casa' })
    expect(wrapper!.get('[data-testid="categories-success"]').text()).toBe('Grupo criado')
    expect(wrapper!.find('[data-testid="group-g3"]').exists()).toBe(true)
    await openDialog('Pets')
    const values = [...inDialog<HTMLSelectElement>('category-group')!.options].map((o) => o.value)
    expect(values).toContain('g3')
  })

  it('move a categoria para outro grupo e devolve o foco ao botão Mover', async () => {
    await mountView()
    mockUpdate.mockResolvedValue(category('c1', 'Moradia', 'g2', '#EC4899'))
    await wrapper!.get('[data-testid="move-c1"]').trigger('click')
    await flushPromises()
    const select = inDialog<HTMLSelectElement>('move-group')!
    expect([...select.options].map((o) => o.value)).toEqual(['g2'])
    expect(select.value).toBe('g2')
    inDialog<HTMLFormElement>('move-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(mockUpdate).toHaveBeenCalledWith('c1', { parentId: 'g2' })
    expect(inDialog('move-dialog')).toBeNull()
    expect(wrapper!.get('[data-testid="group-g2"]').text()).toContain('Moradia')
    expect(wrapper!.get('[data-testid="group-g1"]').text()).toContain('Nenhuma categoria neste grupo')
    expect(wrapper!.get('[data-testid="categories-success"]').text()).toBe('Categoria movida para Variáveis')
    await new Promise((r) => setTimeout(r, 0))
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('move-c1')
  })

  it('ao mover para um grupo recolhido, ele é expandido', async () => {
    await mountView()
    await wrapper!.get('[data-testid="group-toggle-g2"]').trigger('click')
    mockUpdate.mockResolvedValue(category('c1', 'Moradia', 'g2'))
    await wrapper!.get('[data-testid="move-c1"]').trigger('click')
    await flushPromises()
    inDialog<HTMLFormElement>('move-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(wrapper!.get('[data-testid="group-toggle-g2"]').attributes('aria-expanded')).toBe('true')
    expect(wrapper!.get('#group-items-g2').isVisible()).toBe(true)
  })

  it('com um só grupo o botão Mover fica desabilitado', async () => {
    mockList.mockResolvedValue([category('g1', 'Essenciais', null), category('c1', 'Moradia', 'g1')])
    await mountView()
    expect(wrapper!.get('[data-testid="move-c1"]').attributes('disabled')).toBeDefined()
  })

  it('erro ao mover aparece no modal, que continua aberto', async () => {
    await mountView()
    mockUpdate.mockRejectedValue({
      response: { status: 400, data: { detail: 'Grupo inválido. Recarregue a página e tente novamente.' } },
    })
    await wrapper!.get('[data-testid="move-c1"]').trigger('click')
    await flushPromises()
    inDialog<HTMLFormElement>('move-form')!.dispatchEvent(new Event('submit'))
    await flushPromises()

    expect(inDialog('move-error')!.textContent).toContain('Grupo inválido')
    expect(inDialog('move-dialog')).not.toBeNull()
    expect(wrapper!.get('[data-testid="group-g1"]').text()).toContain('Moradia')
  })

  it('recolhe e expande um grupo com aria-expanded', async () => {
    await mountView()
    const toggle = wrapper!.get('[data-testid="group-toggle-g1"]')
    const items = wrapper!.get('#group-items-g1')
    expect(toggle.attributes('aria-expanded')).toBe('true')
    expect(toggle.attributes('aria-controls')).toBe('group-items-g1')
    expect(items.isVisible()).toBe(true)

    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('false')
    expect(items.isVisible()).toBe(false)

    await toggle.trigger('click')
    expect(toggle.attributes('aria-expanded')).toBe('true')
    expect(items.isVisible()).toBe(true)
  })

  it('a grade de ícones não repete a pasta, que já é o "Padrão"', async () => {
    await mountView()
    await openDialog('Pets')
    expect(inDialog('icon-option-folder')).toBeNull()
    expect(inDialog('icon-option-plane')).not.toBeNull()
  })

  it('falha ao carregar mostra Tentar de novo', async () => {
    mockList.mockRejectedValueOnce(new Error('offline'))
    await mountView()
    expect(wrapper!.find('[data-testid="categories-load-failed"]').exists()).toBe(true)
    await wrapper!.get('[data-testid="retry-button"]').trigger('click')
    await flushPromises()
    expect(wrapper!.get('[data-testid="group-g1"]').text()).toContain('Moradia')
  })
})
