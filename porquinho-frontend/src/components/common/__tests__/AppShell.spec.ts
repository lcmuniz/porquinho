import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AppShell from '../AppShell.vue'

const mockPush = vi.fn()
const mockSignOut = vi.fn()
const mockGetAdminAccess = vi.fn()

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRouter: () => ({ push: mockPush }),
  useRoute: () => ({ path: '/dashboard' }),
}))

vi.mock('@/services/adminService', () => ({
  getAdminAccess: () => mockGetAdminAccess(),
}))

vi.mock('@/services/supabase', () => ({
  supabase: { auth: { signOut: () => mockSignOut() } },
}))

function mountShell() {
  return mount(AppShell, { global: { stubs: { RouterLink: RouterLinkStub } } })
}

function linkByTestId(wrapper: ReturnType<typeof mountShell>, testid: string) {
  return wrapper.findAllComponents(RouterLinkStub).find((l) => l.attributes('data-testid') === testid)
}

describe('AppShell', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mockPush.mockReset()
    mockSignOut.mockReset().mockResolvedValue({ error: null })
    mockGetAdminAccess.mockReset().mockResolvedValue({ admin: false })
  })

  it.each([
    ['dashboard-link', '/dashboard', 'Início'],
    ['transactions-link', '/transacoes', 'Transações'],
    ['budget-link', '/orcamento', 'Orçamento'], // Story 6.3
    ['goals-link', '/metas', 'Metas'], // Story 8.1
    ['accounts-link', '/contas', 'Contas bancárias'], // Story 4.1
    ['cards-link', '/cartoes', 'Cartões de crédito'], // Story 4.2
    ['categories-link', '/categorias', 'Categorias'], // Story 6.1
    ['import-link', '/importar', 'Importar extrato'], // Story 5.1
    ['settings-link', '/configuracoes', 'Configurações'],
  ])('a navegação tem o link %s para %s', async (testid, to, label) => {
    const wrapper = mountShell()
    await flushPromises()

    const link = linkByTestId(wrapper, testid)
    expect(link?.props('to')).toBe(to)
    expect(link?.text()).toBe(label)
  })

  it('mostra o link Admin só para admin (Story 10.1)', async () => {
    const user = mountShell()
    await flushPromises()
    expect(user.find('[data-testid="admin-link"]').exists()).toBe(false)

    mockGetAdminAccess.mockResolvedValue({ admin: true })
    const admin = mountShell()
    await flushPromises()
    const link = linkByTestId(admin, 'admin-link')
    expect(link?.props('to')).toBe('/admin')
    expect(link?.text()).toBe('Admin')
  })

  it('esconde o link Admin quando a leitura do papel falha', async () => {
    mockGetAdminAccess.mockRejectedValue(new Error('rede'))
    const wrapper = mountShell()
    await flushPromises()
    expect(wrapper.find('[data-testid="admin-link"]').exists()).toBe(false)
  })

  it('Sair limpa o usuário local, faz signOut e vai para /login', async () => {
    localStorage.setItem('user', '{"id":"x"}')

    const wrapper = mountShell()
    await flushPromises()
    await wrapper.get('[data-testid="logout"]').trigger('click')
    await flushPromises()

    expect(localStorage.getItem('user')).toBeNull()
    expect(mockSignOut).toHaveBeenCalled()
    expect(mockPush).toHaveBeenCalledWith('/login')
  })

  it('Sair vai para /login mesmo se o signOut falhar', async () => {
    vi.spyOn(console, 'warn').mockImplementation(() => {})
    mockSignOut.mockRejectedValue(new Error('rede'))

    const wrapper = mountShell()
    await flushPromises()
    await wrapper.get('[data-testid="logout"]').trigger('click')
    await flushPromises()

    expect(mockPush).toHaveBeenCalledWith('/login')
  })

  it('folha "Mais" prende o foco com Tab/Shift+Tab e devolve o foco ao botão ao fechar', async () => {
    // <a> sem href não recebe foco no jsdom
    const FocusableLink = { props: ['to'], template: '<a href="#"><slot /></a>' }
    const wrapper = mount(AppShell, {
      global: { stubs: { RouterLink: FocusableLink } },
      attachTo: document.body,
    })
    await flushPromises()
    const moreButton = wrapper.get('[aria-controls="more-sheet"]')
    await moreButton.trigger('click')
    await flushPromises()

    const sheet = wrapper.get('#more-sheet')
    const items = sheet.findAll('a, button')
    const first = items[0]!.element as HTMLElement
    const last = items[items.length - 1]!.element as HTMLElement
    expect(document.activeElement).toBe(first)

    await sheet.trigger('keydown', { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(last)
    await sheet.trigger('keydown', { key: 'Tab' })
    expect(document.activeElement).toBe(first)

    await wrapper.get('.fixed.inset-0').trigger('keydown', { key: 'Escape' })
    await flushPromises()
    expect(wrapper.find('#more-sheet').exists()).toBe(false)
    expect(document.activeElement).toBe(moreButton.element)
    wrapper.unmount()
  })
})
