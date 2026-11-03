import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { nextTick } from 'vue'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import AccountsView from '../AccountsView.vue'
import AccountForm from '@/components/accounts/AccountForm.vue'
import type { Account } from '@/types/account'

const mockList = vi.fn()
const mockCreate = vi.fn()

vi.mock('@/services/accountService', () => ({
  listAccounts: (...a: unknown[]) => mockList(...a),
  createAccount: (...a: unknown[]) => mockCreate(...a),
}))

function account(overrides: Partial<Account> = {}): Account {
  return {
    id: 'a1',
    accountName: 'Nubank',
    bankName: 'Nubank',
    accountType: 'CHECKING',
    initialBalance: 1000,
    currentBalance: 1000,
    createdAt: '2026-11-01T12:00:00Z',
    ...overrides,
  }
}

async function mountView() {
  const wrapper = mount(AccountsView, {
    attachTo: document.body,
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
  await flushPromises()
  return wrapper
}

type Wrapper = Awaited<ReturnType<typeof mountView>>

const plain = (s: string) => s.replace(/\s/g, ' ')

const dialog = () => document.body.querySelector('[data-testid="add-account-dialog"]')

async function openDialog(wrapper: Wrapper) {
  await wrapper.get('[data-testid="open-add-account"]').trigger('click')
  await flushPromises()
  return wrapper.findComponent(AccountForm)
}

type Form = Awaited<ReturnType<typeof openDialog>>

async function fillAndSave(form: Form, name: string, balance: string, bank = '') {
  await form.get('[data-testid="account-name-input"]').setValue(name)
  await form.get('[data-testid="bank-name-input"]').setValue(bank)
  await form.get('[data-testid="initial-balance-input"]').setValue(balance)
  await form.get('[data-testid="account-form"]').trigger('submit')
  await flushPromises()
}

describe('AccountsView', () => {
  beforeEach(() => {
    mockList.mockReset().mockResolvedValue([])
    mockCreate.mockReset().mockImplementation(async (input) => ({
      ...account(),
      id: `new-${input.accountName}`,
      accountName: input.accountName,
      bankName: input.bankName,
      accountType: input.accountType,
      initialBalance: input.initialBalance,
      currentBalance: input.initialBalance,
    }))
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('lista as contas com nome, banco, tipo e saldo, e mostra o total', async () => {
    mockList.mockResolvedValue([
      account(),
      account({
        id: 'a2',
        accountName: 'Reserva',
        bankName: null,
        accountType: 'SAVINGS',
        currentBalance: 250.5,
      }),
    ])
    const wrapper = await mountView()

    expect(wrapper.get('h1').text()).toBe('Contas bancárias')
    const first = wrapper.get('[data-testid="account-item-a1"]')
    expect(first.text()).toContain('Nubank')
    expect(first.text()).toContain('Conta corrente')
    expect(plain(first.get('[data-testid="account-balance"]').text())).toBe('R$ 1.000,00')
    const second = wrapper.get('[data-testid="account-item-a2"]')
    expect(second.text()).toContain('Poupança')
    // Ordem da API
    const ids = wrapper.findAll('li').map((li) => li.attributes('data-testid'))
    expect(ids).toEqual(['account-item-a1', 'account-item-a2'])
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 1.250,50')
    expect(wrapper.get('[data-testid="accounts-total"]').classes()).not.toContain('text-red-600')
  })

  it('soma em centavos: 0,10 + 0,20 dá R$ 0,30', async () => {
    mockList.mockResolvedValue([
      account({ id: 'a1', currentBalance: 0.1 }),
      account({ id: 'a2', currentBalance: 0.2 }),
    ])
    const wrapper = await mountView()

    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 0,30')
  })

  it('total e saldo negativos aparecem em vermelho', async () => {
    mockList.mockResolvedValue([
      account({ id: 'a1', currentBalance: 50 }),
      account({ id: 'a2', currentBalance: -200 }),
    ])
    const wrapper = await mountView()

    const total = wrapper.get('[data-testid="accounts-total"]')
    expect(plain(total.text())).toBe('-R$ 150,00')
    expect(total.classes()).toContain('text-red-600')
    expect(
      wrapper.get('[data-testid="account-item-a2"] [data-testid="account-balance"]').classes(),
    ).toContain('text-red-600')
  })

  it('sem contas mostra o estado vazio com o botão de adicionar', async () => {
    const wrapper = await mountView()

    const empty = wrapper.get('[data-testid="accounts-empty"]')
    expect(empty.text()).toContain('Você ainda não tem contas cadastradas')
    expect(empty.find('[data-testid="open-add-account"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="accounts-total"]').exists()).toBe(false)
  })

  it('adicionar "Inter" com R$ 500,00: aparece na lista, total soma, diálogo fecha e mostra sucesso', async () => {
    mockList.mockResolvedValue([account()])
    const wrapper = await mountView()

    const form = await openDialog(wrapper)
    expect(dialog()).not.toBeNull()
    expect(document.activeElement?.id).toBe('account-name')
    expect(document.body.querySelectorAll('#bank-suggestions option').length).toBeGreaterThan(0)

    await fillAndSave(form, 'Inter', '500,00', 'Inter')

    expect(mockCreate).toHaveBeenCalledWith({
      accountName: 'Inter',
      bankName: 'Inter',
      accountType: 'CHECKING',
      initialBalance: 500,
    })
    expect(wrapper.get('[data-testid="account-item-new-Inter"]').text()).toContain('Inter')
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 1.500,00')
    expect(dialog()).toBeNull()
    const success = wrapper.get('[data-testid="accounts-success"]')
    expect(success.attributes('role')).toBe('status')
    expect(success.text()).toBe('Conta adicionada com sucesso')
    expect(mockList).toHaveBeenCalledOnce()
  })

  it('adicionar a partir do estado vazio mostra a lista e o total', async () => {
    const wrapper = await mountView()
    const form = await openDialog(wrapper)
    await fillAndSave(form, 'Inter', '500,00')

    expect(wrapper.find('[data-testid="accounts-empty"]').exists()).toBe(false)
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 500,00')
  })

  it('nome vazio ou saldo inválido mostra o erro no campo e o diálogo continua aberto', async () => {
    const wrapper = await mountView()
    const form = await openDialog(wrapper)
    await fillAndSave(form, '', 'abc')

    expect(form.get('[data-testid="account-name-error"]').text()).toBe('Informe o nome da conta')
    expect(form.get('[data-testid="initial-balance-error"]').text()).toContain('1.234,56')
    expect(mockCreate).not.toHaveBeenCalled()
    expect(dialog()).not.toBeNull()
  })

  it('400 com errors aparece no campo e o diálogo continua aberto', async () => {
    mockCreate.mockRejectedValue({
      response: {
        status: 400,
        data: { detail: 'Dados inválidos', errors: { accountName: 'Informe o nome da conta' } },
      },
    })
    const wrapper = await mountView()
    const form = await openDialog(wrapper)
    await fillAndSave(form, 'X', '1')

    expect(form.get('[data-testid="account-name-error"]').text()).toBe('Informe o nome da conta')
    expect(document.body.querySelector('[data-testid="add-account-error"]')).toBeNull()
    expect(dialog()).not.toBeNull()
  })

  it('falha de rede mostra a mensagem no diálogo e preserva os dados', async () => {
    mockCreate.mockRejectedValue(new Error('Network Error'))
    const wrapper = await mountView()
    const form = await openDialog(wrapper)
    await fillAndSave(form, 'Inter', '500,00')

    const error = document.body.querySelector('[data-testid="add-account-error"]')
    expect(error?.textContent?.trim()).toBe('Não foi possível salvar a conta. Tente novamente.')
    expect(dialog()).not.toBeNull()
    const name = form.get('[data-testid="account-name-input"]').element as HTMLInputElement
    expect(name.value).toBe('Inter')
    expect(wrapper.get('[data-testid="accounts-success"]').text()).toBe('')
  })

  it('falha com detail do servidor mostra a mensagem do servidor', async () => {
    mockCreate.mockRejectedValue({ response: { status: 500, data: { detail: 'Serviço fora' } } })
    const wrapper = await mountView()
    const form = await openDialog(wrapper)
    await fillAndSave(form, 'Inter', '1')

    expect(
      document.body.querySelector('[data-testid="add-account-error"]')?.textContent?.trim(),
    ).toBe('Serviço fora')
  })

  it('Cancelar fecha o diálogo sem salvar', async () => {
    const wrapper = await mountView()
    await openDialog(wrapper)
    document.body.querySelector<HTMLButtonElement>('[data-testid="cancel-add-account"]')!.click()
    await flushPromises()

    expect(dialog()).toBeNull()
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('falha ao carregar mostra o aviso e "Tentar de novo" recarrega', async () => {
    mockList.mockRejectedValueOnce(new Error('Network Error')).mockResolvedValueOnce([account()])
    const wrapper = await mountView()

    const alert = wrapper.get('[data-testid="accounts-load-error"]')
    expect(alert.attributes('role')).toBe('alert')
    expect(wrapper.find('[data-testid="open-add-account"]').exists()).toBe(false)

    await wrapper.get('[data-testid="accounts-retry"]').trigger('click')
    await flushPromises()

    expect(mockList).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="accounts-load-error"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="account-item-a1"]').exists()).toBe(true)
  })

  it('tem o link de volta ao dashboard', async () => {
    const wrapper = await mountView()
    const link = wrapper.getComponent(RouterLinkStub)
    expect(link.props('to')).toBe('/dashboard')
  })

  it('enquanto salva, Cancelar fica desabilitado e Esc ou update:open(false) não fecham', async () => {
    let resolveCreate: (a: Account) => void = () => {}
    mockCreate.mockReturnValue(new Promise<Account>((resolve) => (resolveCreate = resolve)))
    const wrapper = await mountView()
    const form = await openDialog(wrapper)
    await fillAndSave(form, 'Inter', '500,00')

    const cancel = document.body.querySelector<HTMLButtonElement>(
      '[data-testid="cancel-add-account"]',
    )
    expect(cancel?.disabled).toBe(true)
    document.activeElement?.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }),
    )
    await flushPromises()
    expect(dialog()).not.toBeNull()
    wrapper.findComponent({ name: 'Dialog' }).vm.$emit('update:open', false)
    await flushPromises()
    expect(dialog()).not.toBeNull()

    resolveCreate(account({ id: 'new-Inter', accountName: 'Inter', currentBalance: 500 }))
    await flushPromises()
    expect(dialog()).toBeNull()
  })

  it('depois de uma falha, salvar de novo com sucesso tira o erro', async () => {
    mockCreate.mockRejectedValueOnce(new Error('Network Error'))
    let resolveCreate: (a: Account) => void = () => {}
    mockCreate.mockReturnValueOnce(new Promise<Account>((resolve) => (resolveCreate = resolve)))
    const wrapper = await mountView()
    const form = await openDialog(wrapper)
    await fillAndSave(form, 'Inter', '500,00')
    expect(document.body.querySelector('[data-testid="add-account-error"]')).not.toBeNull()

    await form.get('[data-testid="account-form"]').trigger('submit')
    await flushPromises()
    expect(document.body.querySelector('[data-testid="add-account-error"]')).toBeNull()

    resolveCreate(account({ id: 'new-Inter', accountName: 'Inter', currentBalance: 500 }))
    await flushPromises()
    expect(dialog()).toBeNull()
    expect(wrapper.get('[data-testid="accounts-success"]').text()).toBe(
      'Conta adicionada com sucesso',
    )
  })

  it('reabrir o diálogo esconde a mensagem de sucesso', async () => {
    mockList.mockResolvedValue([account()])
    const wrapper = await mountView()
    await fillAndSave(await openDialog(wrapper), 'Inter', '500,00')
    expect(wrapper.get('[data-testid="accounts-success"]').text()).not.toBe('')

    await openDialog(wrapper)
    expect(wrapper.get('[data-testid="accounts-success"]').text()).toBe('')
  })

  it('a mensagem de sucesso some depois de 5 s', async () => {
    const wrapper = await mountView()
    await fillAndSave(await openDialog(wrapper), 'Inter', '500,00')
    vi.useFakeTimers()
    try {
      // O timer foi criado antes dos fake timers: recria salvando de novo
      await fillAndSave(await openDialog(wrapper), 'Itaú', '1,00')
      expect(wrapper.get('[data-testid="accounts-success"]').text()).not.toBe('')
      vi.advanceTimersByTime(5000)
      await nextTick()
      expect(wrapper.get('[data-testid="accounts-success"]').text()).toBe('')
    } finally {
      vi.useRealTimers()
    }
  })

  it('ao fechar depois de salvar, o foco vai para o botão "Adicionar conta" do topo', async () => {
    const wrapper = await mountView()
    await fillAndSave(await openDialog(wrapper), 'Inter', '500,00')
    await flushPromises()

    expect(document.activeElement?.getAttribute('data-testid')).toBe('open-add-account')
  })

  it('ao cancelar sem contas, o foco vai para o título', async () => {
    const wrapper = await mountView()
    await openDialog(wrapper)
    document.body.querySelector<HTMLButtonElement>('[data-testid="cancel-add-account"]')!.click()
    await flushPromises()

    expect(document.activeElement).toBe(wrapper.get('h1').element)
  })

  it('a região de status fica sempre no DOM, vazia até haver mensagem', async () => {
    const wrapper = await mountView()
    const status = wrapper.get('[data-testid="accounts-success"]')
    expect(status.attributes('role')).toBe('status')
    expect(status.text()).toBe('')
  })

  it('depois de uma falha, nova tentativa com campo inválido tira o aviso antigo', async () => {
    mockCreate.mockRejectedValueOnce(new Error('Network Error'))
    const wrapper = await mountView()
    const form = await openDialog(wrapper)
    await fillAndSave(form, 'Inter', '500,00')
    expect(document.body.querySelector('[data-testid="add-account-error"]')).not.toBeNull()

    await fillAndSave(form, 'Inter', 'abc')
    expect(form.find('[data-testid="initial-balance-error"]').exists()).toBe(true)
    expect(document.body.querySelector('[data-testid="add-account-error"]')).toBeNull()
    expect(mockCreate).toHaveBeenCalledOnce()
  })

  it('sem envio em andamento, clicar fora (update:open(false)) fecha o diálogo', async () => {
    const wrapper = await mountView()
    await openDialog(wrapper)
    wrapper.findComponent({ name: 'Dialog' }).vm.$emit('update:open', false)
    await flushPromises()
    expect(dialog()).toBeNull()
  })

  it('reabrir depois de Cancelar mostra o formulário vazio e sem erros', async () => {
    const wrapper = await mountView()
    let form = await openDialog(wrapper)
    await fillAndSave(form, 'Inter', 'abc')
    expect(form.find('[data-testid="initial-balance-error"]').exists()).toBe(true)
    document.body.querySelector<HTMLButtonElement>('[data-testid="cancel-add-account"]')!.click()
    await flushPromises()

    form = await openDialog(wrapper)
    expect((form.get('[data-testid="account-name-input"]').element as HTMLInputElement).value).toBe(
      '',
    )
    expect(
      (form.get('[data-testid="initial-balance-input"]').element as HTMLInputElement).value,
    ).toBe('')
    expect(form.findAll('[data-testid$="-error"]')).toHaveLength(0)
  })
})
