import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import AccountDeletionView from '../AccountDeletionView.vue'

const mockRequestDeletion = vi.fn()
const mockRouterPush = vi.fn()
const mockSupabaseSignOut = vi.fn()
const mockClearUser = vi.fn()

vi.mock('@/services/accountDeletionService', () => ({
  requestDeletion: (...args: unknown[]) => mockRequestDeletion(...args),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: (...args: unknown[]) => mockRouterPush(...args),
  }),
}))

vi.mock('@/services/supabase', () => ({
  supabase: {
    auth: {
      signOut: (...args: unknown[]) => mockSupabaseSignOut(...args),
    },
  },
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    clearUser: (...args: unknown[]) => mockClearUser(...args),
  }),
}))

const mockDeletionResponse = {
  id: '11111111-1111-1111-1111-111111111111',
  status: 'PENDING_PURGE' as const,
  requestedAt: '2026-05-15T10:00:00.000Z',
  scheduledPurgeAt: '2026-06-14T10:00:00.000Z',
}

describe('AccountDeletionView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockSupabaseSignOut.mockResolvedValue({ error: null })
    mockRouterPush.mockResolvedValue(undefined)
  })

  it('renders warning card and destructive button', () => {
    const wrapper = mount(AccountDeletionView)

    expect(wrapper.text()).toContain('Excluir minha conta permanentemente')
    expect(wrapper.text()).toContain('Esta acao iniciara o processo')
    expect(wrapper.text()).toContain('30 dias')
    expect(wrapper.find('[aria-label="Iniciar processo de exclusao da conta"]').exists()).toBe(true)
  })

  it('opens confirm dialog when clicking delete button', async () => {
    const wrapper = mount(AccountDeletionView, { attachTo: document.body })

    await wrapper.find('[aria-label="Iniciar processo de exclusao da conta"]').trigger('click')
    await flushPromises()

    expect(document.body.textContent).toContain('Confirmar exclusao de conta')
    expect(document.body.textContent).toContain('EXCLUIR MINHA CONTA')
    wrapper.unmount()
  })

  it('keeps confirm button disabled until exact phrase is typed', async () => {
    const wrapper = mount(AccountDeletionView, { attachTo: document.body })

    await wrapper.find('[aria-label="Iniciar processo de exclusao da conta"]').trigger('click')
    await flushPromises()

    const confirmBtn = Array.from(document.querySelectorAll('button')).find(
      (b) => b.getAttribute('aria-label') === 'Confirmar exclusao definitiva da conta'
    ) as HTMLButtonElement | undefined

    expect(confirmBtn?.disabled).toBe(true)

    const input = document.getElementById('confirm-input') as HTMLInputElement
    input.value = 'errado'
    input.dispatchEvent(new Event('input'))
    await flushPromises()
    expect(confirmBtn?.disabled).toBe(true)

    wrapper.unmount()
  })

  it('keeps button disabled when case differs', async () => {
    const wrapper = mount(AccountDeletionView, { attachTo: document.body })

    await wrapper.find('[aria-label="Iniciar processo de exclusao da conta"]').trigger('click')
    await flushPromises()

    const input = document.getElementById('confirm-input') as HTMLInputElement
    input.value = 'excluir minha conta'
    input.dispatchEvent(new Event('input'))
    await flushPromises()

    const confirmBtn = Array.from(document.querySelectorAll('button')).find(
      (b) => b.getAttribute('aria-label') === 'Confirmar exclusao definitiva da conta'
    ) as HTMLButtonElement | undefined

    expect(confirmBtn?.disabled).toBe(true)

    wrapper.unmount()
  })

  it('enables button with exact phrase including extra surrounding spaces', async () => {
    const wrapper = mount(AccountDeletionView, { attachTo: document.body })

    await wrapper.find('[aria-label="Iniciar processo de exclusao da conta"]').trigger('click')
    await flushPromises()

    const input = document.getElementById('confirm-input') as HTMLInputElement
    input.value = '  EXCLUIR MINHA CONTA  '
    input.dispatchEvent(new Event('input'))
    await flushPromises()

    const confirmBtn = Array.from(document.querySelectorAll('button')).find(
      (b) => b.getAttribute('aria-label') === 'Confirmar exclusao definitiva da conta'
    ) as HTMLButtonElement | undefined

    expect(confirmBtn?.disabled).toBe(false)

    wrapper.unmount()
  })

  it('on success: clears user, redirects to login, and signs out from supabase', async () => {
    mockRequestDeletion.mockResolvedValue(mockDeletionResponse)
    const wrapper = mount(AccountDeletionView, { attachTo: document.body })

    await wrapper.find('[aria-label="Iniciar processo de exclusao da conta"]').trigger('click')
    await flushPromises()

    const input = document.getElementById('confirm-input') as HTMLInputElement
    input.value = 'EXCLUIR MINHA CONTA'
    input.dispatchEvent(new Event('input'))
    await flushPromises()

    const confirmBtn = Array.from(document.querySelectorAll('button')).find(
      (b) => b.getAttribute('aria-label') === 'Confirmar exclusao definitiva da conta'
    ) as HTMLButtonElement | undefined

    confirmBtn?.click()
    await flushPromises()

    expect(mockRequestDeletion).toHaveBeenCalled()
    expect(mockClearUser).toHaveBeenCalled()
    expect(mockRouterPush).toHaveBeenCalledWith({ path: '/login', query: { msg: 'account-deletion-scheduled' } })
    expect(mockSupabaseSignOut).toHaveBeenCalled()

    wrapper.unmount()
  })

  it('shows rate limit error on 429', async () => {
    mockRequestDeletion.mockRejectedValue({ response: { status: 429 } })
    const wrapper = mount(AccountDeletionView, { attachTo: document.body })

    await wrapper.find('[aria-label="Iniciar processo de exclusao da conta"]').trigger('click')
    await flushPromises()

    const input = document.getElementById('confirm-input') as HTMLInputElement
    input.value = 'EXCLUIR MINHA CONTA'
    input.dispatchEvent(new Event('input'))
    await flushPromises()

    const confirmBtn = Array.from(document.querySelectorAll('button')).find(
      (b) => b.getAttribute('aria-label') === 'Confirmar exclusao definitiva da conta'
    ) as HTMLButtonElement | undefined
    confirmBtn?.click()
    await flushPromises()

    expect(wrapper.text()).toContain('Limite atingido')
    expect(mockClearUser).not.toHaveBeenCalled()
    expect(mockRouterPush).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('still redirects to login when supabase signOut rejects', async () => {
    mockRequestDeletion.mockResolvedValue(mockDeletionResponse)
    mockSupabaseSignOut.mockRejectedValue(new Error('network'))
    const wrapper = mount(AccountDeletionView, { attachTo: document.body })

    await wrapper.find('[aria-label="Iniciar processo de exclusao da conta"]').trigger('click')
    await flushPromises()

    const input = document.getElementById('confirm-input') as HTMLInputElement
    input.value = 'EXCLUIR MINHA CONTA'
    input.dispatchEvent(new Event('input'))
    await flushPromises()

    const confirmBtn = Array.from(document.querySelectorAll('button')).find(
      (b) => b.getAttribute('aria-label') === 'Confirmar exclusao definitiva da conta'
    ) as HTMLButtonElement | undefined
    confirmBtn?.click()
    await flushPromises()

    // The redirect happens before signOut, so it should still occur even if signOut rejects
    expect(mockClearUser).toHaveBeenCalled()
    expect(mockRouterPush).toHaveBeenCalledWith({ path: '/login', query: { msg: 'account-deletion-scheduled' } })

    wrapper.unmount()
  })
})
