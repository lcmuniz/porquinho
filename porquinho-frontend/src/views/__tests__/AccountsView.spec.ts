import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { nextTick } from 'vue'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import AccountsView from '../AccountsView.vue'
import AccountForm from '@/components/accounts/AccountForm.vue'
import ReconcileForm from '@/components/accounts/ReconcileForm.vue'
import TransferForm from '@/components/accounts/TransferForm.vue'
import { toISODate, todayInSaoPaulo } from '@/lib/goalPlan'
import type { Account, ReconciliationResult } from '@/types/account'
import type { Transfer } from '@/types/transfer'

const mockList = vi.fn()
const mockCreate = vi.fn()
const mockUpdate = vi.fn()
const mockDelete = vi.fn()
const mockListDeleted = vi.fn()
const mockRestore = vi.fn()
const mockReconcile = vi.fn()

vi.mock('@/services/accountService', () => ({
  listAccounts: (...a: unknown[]) => mockList(...a),
  createAccount: (...a: unknown[]) => mockCreate(...a),
  updateAccount: (...a: unknown[]) => mockUpdate(...a),
  deleteAccount: (...a: unknown[]) => mockDelete(...a),
  listDeletedAccounts: (...a: unknown[]) => mockListDeleted(...a),
  restoreAccount: (...a: unknown[]) => mockRestore(...a),
  reconcileAccount: (...a: unknown[]) => mockReconcile(...a),
}))

const mockListTransfers = vi.fn()
const mockCreateTransfer = vi.fn()

vi.mock('@/services/transferService', () => ({
  listTransfers: (...a: unknown[]) => mockListTransfers(...a),
  createTransfer: (...a: unknown[]) => mockCreateTransfer(...a),
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
    lastReconciliationDate: null,
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

beforeEach(() => {
  mockListTransfers.mockReset().mockResolvedValue([])
  mockCreateTransfer.mockReset()
})

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

describe('AccountsView — editar conta (Story 4.3)', () => {
  const editDialog = () => document.body.querySelector('[data-testid="edit-account-dialog"]')
  const input = (form: Form, testid: string) =>
    form.get(`[data-testid="${testid}"]`).element as HTMLInputElement

  async function openEdit(wrapper: Wrapper, id = 'a1') {
    await wrapper.get(`[data-testid="edit-account-${id}"]`).trigger('click')
    await flushPromises()
    return wrapper.findComponent(AccountForm)
  }

  beforeEach(() => {
    mockList.mockReset().mockResolvedValue([
      account({ id: 'a1', accountName: 'Nubank', currentBalance: 100, initialBalance: 100 }),
      account({
        id: 'a2',
        accountName: 'Itaú',
        bankName: null,
        accountType: 'SAVINGS',
        currentBalance: 50,
        initialBalance: 50,
      }),
    ])
    mockCreate.mockReset()
    mockUpdate.mockReset().mockImplementation(async (id: string, data) => ({
      ...account({ id, initialBalance: 100 }),
      accountName: data.accountName,
      bankName: data.bankName,
      accountType: data.accountType,
      currentBalance: data.currentBalance,
    }))
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('cada conta tem o botão "Editar" com o nome no rótulo acessível e alvo de 44px', async () => {
    const wrapper = await mountView()
    const button = wrapper.get('[data-testid="edit-account-a2"]')
    expect(button.text()).toBe('Editar')
    expect(button.attributes('aria-label')).toBe('Editar Itaú')
    expect(button.classes()).toContain('min-h-[44px]')
    expect(button.classes()).toContain('min-w-[44px]')
  })

  it('abre o diálogo "Editar conta" com os valores atuais (saldo no formato brasileiro) e foco no nome', async () => {
    mockList.mockResolvedValue([
      account({
        id: 'a1',
        accountName: 'Itaú',
        bankName: null,
        accountType: 'SAVINGS',
        currentBalance: -1234.5,
      }),
    ])
    const wrapper = await mountView()
    const form = await openEdit(wrapper)

    expect(editDialog()).not.toBeNull()
    expect(dialog()).toBeNull()
    expect(editDialog()?.textContent).toContain('Editar conta')
    expect(input(form, 'account-name-input').value).toBe('Itaú')
    expect(input(form, 'bank-name-input').value).toBe('')
    expect(input(form, 'account-type-SAVINGS').checked).toBe(true)
    expect(input(form, 'initial-balance-input').value).toBe('-1.234,50')
    expect(form.get('[data-testid="save-account-button"]').text()).toBe('Salvar alterações')
    expect(document.activeElement?.id).toBe('account-name')
  })

  it('Nubank 100,00 → Nubank PJ 250,00: lista, total (+150,00), mensagem e foco no "Editar"', async () => {
    const wrapper = await mountView()
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 150,00')
    const form = await openEdit(wrapper)
    await form.get('[data-testid="account-name-input"]').setValue('Nubank PJ')
    await form.get('[data-testid="initial-balance-input"]').setValue('250,00')
    await form.get('[data-testid="account-form"]').trigger('submit')
    await flushPromises()

    expect(mockUpdate).toHaveBeenCalledWith('a1', {
      accountName: 'Nubank PJ',
      bankName: 'Nubank',
      accountType: 'CHECKING',
      currentBalance: 250,
    })
    expect(mockCreate).not.toHaveBeenCalled()
    expect(editDialog()).toBeNull()
    const item = wrapper.get('[data-testid="account-item-a1"]')
    expect(item.text()).toContain('Nubank PJ')
    expect(plain(item.get('[data-testid="account-balance"]').text())).toBe('R$ 250,00')
    expect(wrapper.findAll('li').map((li) => li.attributes('data-testid'))).toEqual([
      'account-item-a1',
      'account-item-a2',
    ])
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 300,00')
    expect(wrapper.get('[data-testid="accounts-success"]').text()).toBe('Informações atualizadas')
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('edit-account-a1')
  })

  it('400 do servidor no saldo aparece no campo e o diálogo continua aberto', async () => {
    mockUpdate.mockRejectedValue({
      response: {
        status: 400,
        data: {
          detail: 'Informe o saldo com até 2 casas decimais',
          errors: { currentBalance: 'Informe o saldo com até 2 casas decimais' },
        },
      },
    })
    const wrapper = await mountView()
    const form = await openEdit(wrapper)
    await form.get('[data-testid="account-form"]').trigger('submit')
    await flushPromises()

    expect(editDialog()).not.toBeNull()
    expect(form.get('[data-testid="initial-balance-error"]').text()).toBe(
      'Informe o saldo com até 2 casas decimais',
    )
    expect(document.body.querySelector('[data-testid="add-account-error"]')).toBeNull()
  })

  it('valores inválidos não chamam a API (nome vazio, saldo com 3 casas)', async () => {
    const wrapper = await mountView()
    const form = await openEdit(wrapper)
    await form.get('[data-testid="account-name-input"]').setValue('  ')
    await form.get('[data-testid="initial-balance-input"]').setValue('10,123')
    await form.get('[data-testid="account-form"]').trigger('submit')
    await flushPromises()

    expect(mockUpdate).not.toHaveBeenCalled()
    expect(form.get('[data-testid="account-name-error"]').text()).toBe('Informe o nome da conta')
    expect(form.find('[data-testid="initial-balance-error"]').exists()).toBe(true)
    expect(editDialog()).not.toBeNull()
  })

  it('saldo vazio na edição pede o saldo atual', async () => {
    const wrapper = await mountView()
    const form = await openEdit(wrapper)
    await form.get('[data-testid="initial-balance-input"]').setValue('')
    await form.get('[data-testid="account-form"]').trigger('submit')
    await flushPromises()

    expect(form.get('[data-testid="initial-balance-error"]').text()).toBe(
      'Informe o saldo atual (use 0,00 se não souber)',
    )
  })

  it('404 ao salvar fecha o diálogo, tira a conta da lista e avisa', async () => {
    mockUpdate.mockRejectedValue({
      response: { status: 404, data: { detail: 'Conta não encontrada' } },
    })
    const wrapper = await mountView()
    const form = await openEdit(wrapper)
    await form.get('[data-testid="account-form"]').trigger('submit')
    await flushPromises()

    expect(editDialog()).toBeNull()
    expect(wrapper.find('[data-testid="account-item-a1"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="account-item-a2"]').exists()).toBe(true)
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 50,00')
    const notice = wrapper.get('[data-testid="accounts-notice"]')
    expect(notice.text()).toBe('Esta conta não existe mais')
    expect(notice.attributes('role')).toBe('alert')
    expect(notice.classes()).toContain('bg-amber-50')
    expect(wrapper.get('[data-testid="accounts-success"]').text()).toBe('')
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('open-add-account')
  })

  it('outra falha mostra a mensagem no diálogo, que continua aberto com os dados', async () => {
    mockUpdate.mockRejectedValue(new Error('Network Error'))
    const wrapper = await mountView()
    const form = await openEdit(wrapper)
    await form.get('[data-testid="account-name-input"]').setValue('Nubank PJ')
    await form.get('[data-testid="account-form"]').trigger('submit')
    await flushPromises()

    expect(editDialog()).not.toBeNull()
    expect(document.body.querySelector('[data-testid="add-account-error"]')?.textContent).toContain(
      'Não foi possível salvar a conta. Tente novamente.',
    )
    expect(input(form, 'account-name-input').value).toBe('Nubank PJ')
    expect(wrapper.get('[data-testid="account-item-a1"]').text()).toContain('Nubank')
    expect(wrapper.get('[data-testid="account-item-a1"]').text()).not.toContain('Nubank PJ')
  })

  it('enquanto salva, o botão mostra "Salvando...", Cancelar fica desabilitado e o diálogo não fecha', async () => {
    let resolveUpdate: (a: Account) => void = () => {}
    mockUpdate.mockReturnValue(new Promise<Account>((resolve) => (resolveUpdate = resolve)))
    const wrapper = await mountView()
    const form = await openEdit(wrapper)
    await form.get('[data-testid="account-form"]').trigger('submit')
    await flushPromises()

    const save = form.get('[data-testid="save-account-button"]')
    expect(save.text()).toBe('Salvando...')
    expect(save.attributes('disabled')).toBeDefined()
    expect(
      document.body.querySelector<HTMLButtonElement>('[data-testid="cancel-add-account"]')
        ?.disabled,
    ).toBe(true)
    wrapper.findComponent({ name: 'Dialog' }).vm.$emit('update:open', false)
    await flushPromises()
    expect(editDialog()).not.toBeNull()

    resolveUpdate(account({ id: 'a1', accountName: 'Nubank', currentBalance: 100 }))
    await flushPromises()
    expect(editDialog()).toBeNull()
  })

  it('cancelar a edição volta o foco para o "Editar" da conta e não muda nada', async () => {
    const wrapper = await mountView()
    const form = await openEdit(wrapper, 'a2')
    await form.get('[data-testid="account-name-input"]').setValue('Outro nome')
    document.body.querySelector<HTMLButtonElement>('[data-testid="cancel-add-account"]')!.click()
    await flushPromises()

    expect(mockUpdate).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="account-item-a2"]').text()).toContain('Itaú')
    expect(document.activeElement?.getAttribute('data-testid')).toBe('edit-account-a2')
  })

  it('depois de editar, "Adicionar conta" abre o diálogo de criação vazio', async () => {
    const wrapper = await mountView()
    await openEdit(wrapper)
    document.body.querySelector<HTMLButtonElement>('[data-testid="cancel-add-account"]')!.click()
    await flushPromises()

    const form = await openDialog(wrapper)
    expect(dialog()?.textContent).toContain('Adicionar conta')
    expect(input(form, 'account-name-input').value).toBe('')
    expect(input(form, 'initial-balance-input').value).toBe('')
    expect(form.get('[data-testid="add-account-button"]').text()).toBe('Salvar conta')
  })

  it('o aviso de item que não existe mais some ao abrir outro diálogo e não aparece sem 404', async () => {
    mockUpdate.mockRejectedValue({ response: { status: 404, data: {} } })
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="accounts-notice"]').exists()).toBe(false)
    const form = await openEdit(wrapper)
    await form.get('[data-testid="account-form"]').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[data-testid="accounts-notice"]').exists()).toBe(true)

    await openEdit(wrapper, 'a2')
    expect(wrapper.find('[data-testid="accounts-notice"]').exists()).toBe(false)
  })
})

describe('AccountsView — remover e restaurar conta (Story 4.4)', () => {
  const removeDialog = () => document.body.querySelector('[data-testid="remove-account-dialog"]')
  const bodyButton = (testid: string) =>
    document.body.querySelector<HTMLButtonElement>(`[data-testid="${testid}"]`)!

  async function openRemove(wrapper: Wrapper, id = 'a2') {
    await wrapper.get(`[data-testid="remove-account-${id}"]`).trigger('click')
    await flushPromises()
  }

  async function confirm() {
    bodyButton('confirm-remove-account').click()
    await flushPromises()
  }

  async function showRemoved(wrapper: Wrapper) {
    await wrapper.get('[data-testid="toggle-removed-accounts"]').setValue(true)
    await flushPromises()
  }

  const nubank = () =>
    account({
      id: 'a1',
      accountName: 'Nubank',
      currentBalance: 100,
      createdAt: '2026-11-01T12:00:00Z',
    })
  const bb = () =>
    account({
      id: 'a2',
      accountName: 'BB',
      bankName: 'Banco do Brasil',
      currentBalance: 50,
      createdAt: '2026-11-02T12:00:00Z',
    })

  beforeEach(() => {
    mockList.mockReset().mockResolvedValue([nubank(), bb()])
    mockDelete.mockReset().mockResolvedValue(undefined)
    mockListDeleted.mockReset().mockResolvedValue([])
    mockRestore.mockReset()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('cada conta tem "Remover" com o nome no rótulo acessível e alvo de 44px', async () => {
    const wrapper = await mountView()
    const button = wrapper.get('[data-testid="remove-account-a2"]')
    expect(button.text()).toBe('Remover')
    expect(button.attributes('aria-label')).toBe('Remover BB')
    expect(button.classes()).toContain('min-h-[44px]')
    expect(button.classes()).toContain('min-w-[44px]')
  })

  it('abre a confirmação com o nome e o texto do histórico preservado', async () => {
    const wrapper = await mountView()
    await openRemove(wrapper)

    expect(removeDialog()?.textContent).toContain('Remover BB?')
    expect(removeDialog()?.textContent).toContain(
      'A conta some da lista, mas o histórico é preservado. Você pode restaurá-la depois.',
    )
    expect(bodyButton('cancel-remove-account').textContent?.trim()).toBe('Cancelar')
    expect(bodyButton('confirm-remove-account').textContent?.trim()).toBe('Remover')
    expect(mockDelete).not.toHaveBeenCalled()
  })

  it('remover "BB": some da lista, total recalculado e "Conta removida"; restaurar a traz de volta', async () => {
    const wrapper = await mountView()
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 150,00')
    await openRemove(wrapper)
    await confirm()

    expect(mockDelete).toHaveBeenCalledWith('a2')
    expect(removeDialog()).toBeNull()
    expect(wrapper.find('[data-testid="account-item-a2"]').exists()).toBe(false)
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 100,00')
    const success = wrapper.get('[data-testid="accounts-success"]')
    expect(success.attributes('role')).toBe('status')
    expect(success.text()).toBe('Conta removida')
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('open-add-account')

    // "Mostrar removidas" carrega a seção com "BB" e "Restaurar"
    mockListDeleted.mockResolvedValue([bb()])
    await showRemoved(wrapper)
    expect(mockListDeleted).toHaveBeenCalledOnce()
    const section = wrapper.get('[data-testid="removed-accounts"]')
    expect(section.get('h2').text()).toBe('Removidas')
    const removedItem = section.get('[data-testid="removed-account-item-a2"]')
    expect(removedItem.text()).toContain('BB')
    expect(removedItem.find('[data-testid="edit-account-a2"]').exists()).toBe(false)
    const restoreButton = removedItem.get('[data-testid="restore-account-a2"]')
    expect(restoreButton.text()).toBe('Restaurar')
    expect(restoreButton.attributes('aria-label')).toBe('Restaurar BB')
    expect(restoreButton.classes()).toContain('min-h-[44px]')
    // Removidas não entram no total
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 100,00')

    mockRestore.mockResolvedValue(bb())
    await restoreButton.trigger('click')
    await flushPromises()

    expect(mockRestore).toHaveBeenCalledWith('a2')
    expect(wrapper.find('[data-testid="removed-account-item-a2"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="removed-accounts-empty"]').text()).toBe(
      'Nenhuma conta removida',
    )
    expect(
      wrapper
        .get('[data-testid="accounts-list"]')
        .findAll('li')
        .map((li) => li.attributes('data-testid')),
    ).toEqual(['account-item-a1', 'account-item-a2'])
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 150,00')
    expect(wrapper.get('[data-testid="accounts-success"]').text()).toBe('Conta restaurada')
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('edit-account-a2')
  })

  it('a conta restaurada volta na ordem da criação', async () => {
    mockList.mockResolvedValue([bb()])
    const wrapper = await mountView()
    mockListDeleted.mockResolvedValue([nubank()])
    await showRemoved(wrapper)
    mockRestore.mockResolvedValue(nubank())

    await wrapper.get('[data-testid="restore-account-a1"]').trigger('click')
    await flushPromises()

    expect(
      wrapper
        .get('[data-testid="accounts-list"]')
        .findAll('li')
        .map((li) => li.attributes('data-testid')),
    ).toEqual(['account-item-a1', 'account-item-a2'])
  })

  it('remover com a seção aberta recarrega as removidas (a conta aparece no topo)', async () => {
    const old = account({ id: 'a9', accountName: 'Antiga' })
    mockListDeleted.mockResolvedValueOnce([old]).mockResolvedValueOnce([bb(), old])
    const wrapper = await mountView()
    await showRemoved(wrapper)
    await openRemove(wrapper)
    await confirm()

    expect(mockListDeleted).toHaveBeenCalledTimes(2)

    expect(
      wrapper
        .get('[data-testid="removed-accounts-list"]')
        .findAll('li')
        .map((li) => li.attributes('data-testid')),
    ).toEqual(['removed-account-item-a2', 'removed-account-item-a9'])
  })

  it('409 da última conta mostra a mensagem do servidor no diálogo e a conta continua ativa', async () => {
    mockList.mockResolvedValue([nubank()])
    mockDelete.mockRejectedValue({
      response: { status: 409, data: { detail: 'Mantenha ao menos uma conta ativa' } },
    })
    const wrapper = await mountView()
    await openRemove(wrapper, 'a1')
    await confirm()

    expect(removeDialog()).not.toBeNull()
    const error = bodyButton('remove-account-error')
    expect(error.getAttribute('role')).toBe('alert')
    expect(error.textContent?.trim()).toBe('Mantenha ao menos uma conta ativa')
    expect(wrapper.find('[data-testid="account-item-a1"]').exists()).toBe(true)
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 100,00')
    expect(wrapper.get('[data-testid="accounts-success"]').text()).toBe('')

    bodyButton('cancel-remove-account').click()
    await flushPromises()
    expect(removeDialog()).toBeNull()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('remove-account-a1')
  })

  it('outra falha mostra a mensagem genérica no diálogo', async () => {
    mockDelete.mockRejectedValue(new Error('Network Error'))
    const wrapper = await mountView()
    await openRemove(wrapper)
    await confirm()

    expect(bodyButton('remove-account-error').textContent).toContain(
      'Não foi possível remover a conta. Tente novamente.',
    )
    expect(wrapper.find('[data-testid="account-item-a2"]').exists()).toBe(true)
  })

  it('404 ao remover fecha o diálogo, avisa e recarrega as listas ativa e de removidas', async () => {
    mockDelete.mockRejectedValue({ response: { status: 404, data: {} } })
    // Em outra aba, "BB" foi removida e "Itaú" foi restaurada
    mockList
      .mockResolvedValueOnce([nubank(), bb()])
      .mockResolvedValueOnce([nubank(), account({ id: 'a3', accountName: 'Itaú' })])
    mockListDeleted.mockResolvedValueOnce([]).mockResolvedValueOnce([bb()])
    const wrapper = await mountView()
    await showRemoved(wrapper)
    await openRemove(wrapper)
    await confirm()

    expect(removeDialog()).toBeNull()
    expect(mockList).toHaveBeenCalledTimes(2)
    expect(mockListDeleted).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="account-item-a2"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="account-item-a3"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="removed-account-item-a2"]').exists()).toBe(true)
    const notice = wrapper.get('[data-testid="accounts-notice"]')
    expect(notice.text()).toBe('Esta conta já foi removida ou não existe mais')
    expect(notice.classes()).toContain('bg-amber-50')
    expect(wrapper.get('[data-testid="accounts-success"]').text()).toBe('')
  })

  it('cancelar não muda nada e devolve o foco ao "Remover" da conta', async () => {
    const wrapper = await mountView()
    await openRemove(wrapper)
    bodyButton('cancel-remove-account').click()
    await flushPromises()

    expect(mockDelete).not.toHaveBeenCalled()
    expect(removeDialog()).toBeNull()
    expect(wrapper.find('[data-testid="account-item-a2"]').exists()).toBe(true)
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 150,00')
    expect(document.activeElement?.getAttribute('data-testid')).toBe('remove-account-a2')
  })

  it('enquanto remove, os botões ficam travados, mostra "Removendo..." e o diálogo não fecha', async () => {
    let resolveDelete: () => void = () => {}
    mockDelete.mockReturnValue(new Promise<void>((resolve) => (resolveDelete = resolve)))
    const wrapper = await mountView()
    await openRemove(wrapper)
    await confirm()

    expect(bodyButton('confirm-remove-account').disabled).toBe(true)
    expect(bodyButton('confirm-remove-account').textContent?.trim()).toBe('Removendo...')
    expect(bodyButton('cancel-remove-account').disabled).toBe(true)
    wrapper.findAllComponents({ name: 'Dialog' })[1]!.vm.$emit('update:open', false)
    await flushPromises()
    expect(removeDialog()).not.toBeNull()

    resolveDelete()
    await flushPromises()
    expect(removeDialog()).toBeNull()
    expect(mockDelete).toHaveBeenCalledOnce()
  })

  it('desmarcar "Mostrar removidas" esconde a seção; falha ao carregar mostra "Tentar de novo"', async () => {
    mockListDeleted.mockRejectedValueOnce(new Error('Network Error')).mockResolvedValue([bb()])
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="removed-accounts"]').exists()).toBe(false)
    expect(wrapper.get('label').text()).toContain('Mostrar removidas')

    await showRemoved(wrapper)
    expect(wrapper.get('[data-testid="removed-accounts-load-error"]').attributes('role')).toBe(
      'alert',
    )
    await wrapper.get('[data-testid="removed-accounts-retry"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="removed-account-item-a2"]').exists()).toBe(true)

    await wrapper.get('[data-testid="toggle-removed-accounts"]').setValue(false)
    expect(wrapper.find('[data-testid="removed-accounts"]').exists()).toBe(false)
  })

  it('enquanto restaura, os botões "Restaurar" ficam travados', async () => {
    let resolveRestore: (a: Account) => void = () => {}
    mockRestore.mockReturnValue(new Promise<Account>((resolve) => (resolveRestore = resolve)))
    mockListDeleted.mockResolvedValue([bb(), account({ id: 'a9', accountName: 'Antiga' })])
    mockList.mockResolvedValue([nubank()])
    const wrapper = await mountView()
    await showRemoved(wrapper)

    await wrapper.get('[data-testid="restore-account-a2"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="restore-account-a2"]').text()).toBe('Restaurando...')
    expect(wrapper.get('[data-testid="restore-account-a9"]').attributes('disabled')).toBeDefined()
    await wrapper.get('[data-testid="restore-account-a9"]').trigger('click')
    expect(mockRestore).toHaveBeenCalledOnce()

    resolveRestore(bb())
    await flushPromises()
    expect(wrapper.get('[data-testid="restore-account-a9"]').attributes('disabled')).toBeUndefined()
  })

  it('404 ao restaurar avisa e recarrega as listas (a conta já foi restaurada em outra aba)', async () => {
    mockListDeleted.mockResolvedValueOnce([bb()]).mockResolvedValueOnce([])
    mockRestore.mockRejectedValue({ response: { status: 404, data: {} } })
    mockList.mockResolvedValueOnce([nubank()]).mockResolvedValueOnce([nubank(), bb()])
    const wrapper = await mountView()
    await showRemoved(wrapper)
    await wrapper.get('[data-testid="restore-account-a2"]').trigger('click')
    await flushPromises()

    expect(mockList).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="removed-account-item-a2"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="account-item-a2"]').exists()).toBe(true)
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 150,00')
    expect(wrapper.get('[data-testid="accounts-notice"]').text()).toBe(
      'Esta conta já foi restaurada ou não existe mais',
    )
  })

  it('uma resposta atrasada das removidas não sobrescreve a mais recente', async () => {
    let resolveFirst: (a: Account[]) => void = () => {}
    mockListDeleted
      .mockReturnValueOnce(new Promise<Account[]>((resolve) => (resolveFirst = resolve)))
      .mockResolvedValueOnce([bb()])
    const wrapper = await mountView()
    await showRemoved(wrapper)
    await wrapper.get('[data-testid="toggle-removed-accounts"]').setValue(false)
    await showRemoved(wrapper)
    expect(wrapper.find('[data-testid="removed-account-item-a2"]').exists()).toBe(true)

    resolveFirst([account({ id: 'a9', accountName: 'Antiga' })])
    await flushPromises()

    expect(wrapper.find('[data-testid="removed-account-item-a2"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="removed-account-item-a9"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="removed-accounts-loading"]').exists()).toBe(false)
  })

  it('falha (500) ao restaurar mostra a mensagem, o item fica nos removidos e os botões voltam', async () => {
    mockListDeleted.mockResolvedValue([bb(), account({ id: 'a9', accountName: 'Antiga' })])
    mockRestore.mockRejectedValue({ response: { status: 500, data: {} } })
    mockList.mockResolvedValue([nubank()])
    const wrapper = await mountView()
    await showRemoved(wrapper)
    await wrapper.get('[data-testid="restore-account-a2"]').trigger('click')
    await flushPromises()

    const notice = wrapper.get('[data-testid="accounts-notice"]')
    expect(notice.text()).toBe('Não foi possível restaurar a conta. Tente novamente.')
    expect(notice.attributes('role')).toBe('alert')
    expect(wrapper.find('[data-testid="removed-account-item-a2"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="account-item-a2"]').exists()).toBe(false)
    const restoreButton = wrapper.get('[data-testid="restore-account-a2"]')
    expect(restoreButton.text()).toBe('Restaurar')
    expect(restoreButton.attributes('disabled')).toBeUndefined()
    expect(wrapper.get('[data-testid="restore-account-a9"]').attributes('disabled')).toBeUndefined()
  })
})

describe('AccountsView — transferências (Story 4.5)', () => {
  const transferDialog = () => document.body.querySelector('[data-testid="transfer-dialog"]')
  const bodyButton = (testid: string) =>
    document.body.querySelector<HTMLButtonElement>(`[data-testid="${testid}"]`)!
  const today = toISODate(todayInSaoPaulo())

  const nubank = () => account({ id: 'a1', accountName: 'Nubank', currentBalance: 1234.56 })
  const inter = () =>
    account({
      id: 'a2',
      accountName: 'Inter',
      bankName: 'Inter',
      currentBalance: 600,
      createdAt: '2026-11-02T12:00:00Z',
    })

  function transfer(overrides: Partial<Transfer> = {}): Transfer {
    return {
      id: 't1',
      fromAccountId: 'a1',
      fromAccountName: 'Nubank',
      toAccountId: 'a2',
      toAccountName: 'Inter',
      amount: 200,
      transferDate: '2026-11-03',
      description: null,
      createdAt: '2026-11-03T15:00:00Z',
      ...overrides,
    }
  }

  async function openTransfer(wrapper: Wrapper) {
    await wrapper.get('[data-testid="open-transfer"]').trigger('click')
    await flushPromises()
    return wrapper.findComponent(TransferForm)
  }

  type TForm = Awaited<ReturnType<typeof openTransfer>>

  async function fillAndSubmit(form: TForm, amount: string, from = 'a1', to = 'a2') {
    await form.get('[data-testid="transfer-from-select"]').setValue(from)
    await form.get('[data-testid="transfer-to-select"]').setValue(to)
    await form.get('[data-testid="transfer-amount-input"]').setValue(amount)
    await form.get('[data-testid="transfer-form"]').trigger('submit')
    await flushPromises()
  }

  beforeEach(() => {
    mockList.mockReset().mockResolvedValue([nubank(), inter()])
    mockCreateTransfer.mockImplementation(async (input) =>
      transfer({
        fromAccountId: input.fromAccountId,
        toAccountId: input.toAccountId,
        amount: input.amount,
        transferDate: input.transferDate,
        description: input.description,
      }),
    )
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('com menos de 2 contas ativas o botão "Transferir" fica oculto', async () => {
    mockList.mockResolvedValue([nubank()])
    const wrapper = await mountView()

    expect(wrapper.find('[data-testid="open-transfer"]').exists()).toBe(false)
  })

  it('com 2 contas o botão "Transferir" aparece com alvo de 44px', async () => {
    const wrapper = await mountView()
    const button = wrapper.get('[data-testid="open-transfer"]')

    expect(button.text()).toBe('Transferir')
    expect(button.classes()).toContain('min-h-[44px]')
  })

  it('sem transferências mostra o estado vazio da seção', async () => {
    const wrapper = await mountView()

    const section = wrapper.get('[data-testid="recent-transfers"]')
    expect(section.get('h2').text()).toBe('Transferências recentes')
    expect(section.get('[data-testid="transfers-empty"]').text()).toBe(
      'Nenhuma transferência registrada',
    )
  })

  it('lista as transferências com data, origem → destino, valor e descrição, na ordem da API', async () => {
    mockListTransfers.mockResolvedValue([
      transfer({ id: 't2', amount: 50, transferDate: '2026-11-03', description: 'Reserva' }),
      transfer({
        id: 't1',
        fromAccountId: 'a2',
        fromAccountName: 'Inter',
        toAccountId: 'a1',
        toAccountName: 'Nubank',
        transferDate: '2026-11-01',
      }),
    ])
    const wrapper = await mountView()

    const items = wrapper.get('[data-testid="transfers-list"]').findAll('li')
    expect(items.map((li) => li.attributes('data-testid'))).toEqual([
      'transfer-item-t2',
      'transfer-item-t1',
    ])
    expect(items[0]!.get('[data-testid="transfer-accounts"]').text()).toBe('Nubank → Inter')
    expect(items[0]!.get('[data-testid="transfer-date"]').text()).toBe('03/11/2026')
    expect(items[0]!.get('[data-testid="transfer-description"]').text()).toBe('Reserva')
    expect(plain(items[0]!.get('[data-testid="transfer-amount"]').text())).toBe('R$ 50,00')
    expect(items[1]!.get('[data-testid="transfer-accounts"]').text()).toBe('Inter → Nubank')
    expect(items[1]!.find('[data-testid="transfer-description"]').exists()).toBe(false)
  })

  it('abre "Nova transferência" com as contas ativas (nome e saldo), data de hoje e foco na origem', async () => {
    const wrapper = await mountView()
    const form = await openTransfer(wrapper)

    expect(transferDialog()?.textContent).toContain('Nova transferência')
    const options = form.get('[data-testid="transfer-from-select"]').findAll('option')
    expect(options.map((o) => plain(o.text()))).toEqual([
      'Nubank — R$ 1.234,56',
      'Inter — R$ 600,00',
    ])
    expect(
      (form.get('[data-testid="transfer-from-select"]').element as HTMLSelectElement).value,
    ).toBe('a1')
    expect(
      (form.get('[data-testid="transfer-to-select"]').element as HTMLSelectElement).value,
    ).toBe('a2')
    const date = form.get('[data-testid="transfer-date-input"]').element as HTMLInputElement
    expect(date.value).toBe(today)
    expect(date.max).toBe(today)
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('transfer-from-select')
  })

  it('R$ 200,00 de Nubank para Inter: saldos, total igual, mensagem e seção atualizada', async () => {
    const wrapper = await mountView()
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 1.834,56')
    const form = await openTransfer(wrapper)
    mockListTransfers.mockResolvedValue([transfer()])
    // Os saldos vêm do servidor depois da transferência
    mockList.mockResolvedValue([
      { ...nubank(), currentBalance: 1034.56 },
      { ...inter(), currentBalance: 800 },
    ])
    await fillAndSubmit(form, '200,00')

    expect(mockCreateTransfer).toHaveBeenCalledWith({
      id: expect.any(String),
      fromAccountId: 'a1',
      toAccountId: 'a2',
      amount: 200,
      transferDate: today,
      description: null,
    })
    expect(transferDialog()).toBeNull()
    expect(
      plain(wrapper.get('[data-testid="account-item-a1"] [data-testid="account-balance"]').text()),
    ).toBe('R$ 1.034,56')
    expect(
      plain(wrapper.get('[data-testid="account-item-a2"] [data-testid="account-balance"]').text()),
    ).toBe('R$ 800,00')
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 1.834,56')
    const success = wrapper.get('[data-testid="accounts-success"]')
    expect(success.attributes('role')).toBe('status')
    expect(success.text()).toBe('Transferência registrada')
    expect(mockList).toHaveBeenCalledTimes(2)
    expect(mockListTransfers).toHaveBeenCalledTimes(2)
    const item = wrapper.get('[data-testid="transfer-item-t1"]')
    expect(item.get('[data-testid="transfer-accounts"]').text()).toBe('Nubank → Inter')
    expect(plain(item.get('[data-testid="transfer-amount"]').text())).toBe('R$ 200,00')
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('open-transfer')
  })

  it('saldo da origem negativo fica em vermelho', async () => {
    mockList.mockResolvedValue([account({ id: 'a1', currentBalance: 100 }), inter()])
    const wrapper = await mountView()
    const form = await openTransfer(wrapper)
    mockList.mockResolvedValue([
      account({ id: 'a1', currentBalance: -50 }),
      { ...inter(), currentBalance: 750 },
    ])
    await fillAndSubmit(form, '150')

    const balance = wrapper.get('[data-testid="account-item-a1"] [data-testid="account-balance"]')
    expect(plain(balance.text())).toBe('-R$ 50,00')
    expect(balance.classes()).toContain('text-red-600')
  })

  it('mesma conta, valor 0 ou data futura mostram o erro no campo e nenhum saldo muda', async () => {
    const wrapper = await mountView()
    const form = await openTransfer(wrapper)

    await fillAndSubmit(form, '10', 'a1', 'a1')
    expect(form.get('[data-testid="transfer-to-error"]').text()).toBe('Escolha contas diferentes')
    expect(form.get('[data-testid="transfer-to-select"]').attributes('aria-invalid')).toBe('true')

    await fillAndSubmit(form, '0')
    expect(form.get('[data-testid="transfer-amount-error"]').text()).toBe(
      'O valor precisa ser maior que zero',
    )

    await fillAndSubmit(form, '10,123')
    expect(form.find('[data-testid="transfer-amount-error"]').exists()).toBe(true)

    await form.get('[data-testid="transfer-date-input"]').setValue('2999-01-01')
    await fillAndSubmit(form, '10')
    expect(form.get('[data-testid="transfer-date-error"]').text()).toBe(
      'A data não pode estar no futuro',
    )

    expect(mockCreateTransfer).not.toHaveBeenCalled()
    expect(transferDialog()).not.toBeNull()
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 1.834,56')
    expect(
      plain(wrapper.get('[data-testid="account-item-a1"] [data-testid="account-balance"]').text()),
    ).toBe('R$ 1.234,56')
  })

  it('400 do servidor aparece no campo (contas iguais no destino) e o diálogo continua aberto', async () => {
    mockCreateTransfer.mockRejectedValue({
      response: {
        status: 400,
        data: {
          detail: 'Escolha contas diferentes',
          errors: { differentAccounts: 'Escolha contas diferentes' },
        },
      },
    })
    const wrapper = await mountView()
    const form = await openTransfer(wrapper)
    await fillAndSubmit(form, '10')

    expect(form.get('[data-testid="transfer-to-error"]').text()).toBe('Escolha contas diferentes')
    expect(transferDialog()).not.toBeNull()
    expect(document.body.querySelector('[data-testid="transfer-error"]')).toBeNull()
  })

  it('404 mostra "Conta não encontrada" no diálogo, que continua aberto, e recarrega as contas', async () => {
    mockCreateTransfer.mockRejectedValue({
      response: { status: 404, data: { detail: 'Conta não encontrada' } },
    })
    mockList.mockResolvedValueOnce([nubank(), inter(), account({ id: 'a3', accountName: 'BB' })])
    const wrapper = await mountView()
    const form = await openTransfer(wrapper)
    // Inter foi removida em outra aba: some das opções depois do 404
    mockList.mockResolvedValue([
      nubank(),
      account({ id: 'a3', accountName: 'BB', currentBalance: 600 }),
    ])
    await fillAndSubmit(form, '10')

    expect(mockList).toHaveBeenCalledTimes(2)
    const options = form.get('[data-testid="transfer-to-select"]').findAll('option')
    expect(options.map((o) => o.attributes('value'))).toEqual(['a1', 'a3'])
    expect(
      (form.get('[data-testid="transfer-to-select"]').element as HTMLSelectElement).value,
    ).toBe('a3')

    const error = document.body.querySelector('[data-testid="transfer-error"]')
    expect(error?.textContent?.trim()).toBe('Conta não encontrada')
    expect(error?.getAttribute('role')).toBe('alert')
    expect(transferDialog()).not.toBeNull()
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 1.834,56')
    expect(wrapper.get('[data-testid="accounts-success"]').text()).toBe('')
  })

  it('nova tentativa reaproveita o id; reabrir o diálogo gera outro', async () => {
    mockCreateTransfer.mockRejectedValueOnce(new Error('Network Error'))
    const wrapper = await mountView()
    let form = await openTransfer(wrapper)
    await fillAndSubmit(form, '10')
    await fillAndSubmit(form, '10')

    const [first, retry] = mockCreateTransfer.mock.calls.map((call) => call[0].id as string)
    expect(first).toMatch(/^[0-9a-f-]{36}$/)
    expect(retry).toBe(first)

    form = await openTransfer(wrapper)
    await fillAndSubmit(form, '10')
    expect(mockCreateTransfer.mock.calls[2]![0].id).not.toBe(first)
  })

  it('editar, remover ou restaurar uma conta recarrega as transferências', async () => {
    mockUpdate.mockReset().mockResolvedValue({ ...inter(), accountName: 'Inter PJ' })
    mockDelete.mockReset().mockResolvedValue(undefined)
    mockRestore.mockReset().mockResolvedValue(inter())
    mockListDeleted.mockReset().mockResolvedValue([])
    const wrapper = await mountView()
    expect(mockListTransfers).toHaveBeenCalledTimes(1)

    await wrapper.get('[data-testid="edit-account-a2"]').trigger('click')
    await flushPromises()
    const accountForm = wrapper.findComponent(AccountForm)
    await accountForm.get('[data-testid="account-name-input"]').setValue('Inter PJ')
    await accountForm.get('[data-testid="account-form"]').trigger('submit')
    await flushPromises()
    expect(mockListTransfers).toHaveBeenCalledTimes(2)

    await wrapper.get('[data-testid="remove-account-a2"]').trigger('click')
    await flushPromises()
    bodyButton('confirm-remove-account').click()
    await flushPromises()
    expect(mockListTransfers).toHaveBeenCalledTimes(3)

    mockListDeleted.mockResolvedValue([inter()])
    await wrapper.get('[data-testid="toggle-removed-accounts"]').setValue(true)
    await flushPromises()
    await wrapper.get('[data-testid="restore-account-a2"]').trigger('click')
    await flushPromises()
    expect(mockListTransfers).toHaveBeenCalledTimes(4)
  })

  it('falha de rede mostra a mensagem genérica e preserva os dados digitados', async () => {
    mockCreateTransfer.mockRejectedValue(new Error('Network Error'))
    const wrapper = await mountView()
    const form = await openTransfer(wrapper)
    await fillAndSubmit(form, '10,50')

    expect(document.body.querySelector('[data-testid="transfer-error"]')?.textContent?.trim()).toBe(
      'Não foi possível registrar a transferência. Tente novamente.',
    )
    expect(
      (form.get('[data-testid="transfer-amount-input"]').element as HTMLInputElement).value,
    ).toBe('10,50')
  })

  it('enquanto salva, mostra "Salvando...", Cancelar fica desabilitado e o diálogo não fecha', async () => {
    let resolve: (t: Transfer) => void = () => {}
    mockCreateTransfer.mockReturnValue(new Promise<Transfer>((r) => (resolve = r)))
    const wrapper = await mountView()
    const form = await openTransfer(wrapper)
    await fillAndSubmit(form, '10')

    expect(bodyButton('save-transfer-button').disabled).toBe(true)
    expect(bodyButton('save-transfer-button').textContent?.trim()).toBe('Salvando...')
    expect(bodyButton('cancel-transfer').disabled).toBe(true)
    wrapper.findAllComponents({ name: 'Dialog' })[2]!.vm.$emit('update:open', false)
    await flushPromises()
    expect(transferDialog()).not.toBeNull()

    // Segundo envio durante o primeiro é ignorado
    await form.get('[data-testid="transfer-form"]').trigger('submit')
    resolve(transfer({ amount: 10 }))
    await flushPromises()
    expect(transferDialog()).toBeNull()
    expect(mockCreateTransfer).toHaveBeenCalledOnce()
  })

  it('Cancelar fecha sem transferir e devolve o foco ao "Transferir"', async () => {
    const wrapper = await mountView()
    await openTransfer(wrapper)
    bodyButton('cancel-transfer').click()
    await flushPromises()
    await nextTick()

    expect(transferDialog()).toBeNull()
    expect(mockCreateTransfer).not.toHaveBeenCalled()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('open-transfer')
  })

  it('falha ao carregar as transferências mostra "Tentar de novo", que recarrega', async () => {
    mockListTransfers.mockRejectedValueOnce(new Error('down'))
    const wrapper = await mountView()

    expect(wrapper.get('[data-testid="transfers-load-error"]').attributes('role')).toBe('alert')
    mockListTransfers.mockResolvedValue([transfer()])
    await wrapper.get('[data-testid="transfers-retry"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="transfer-item-t1"]').exists()).toBe(true)
  })
})

describe('AccountsView — reconciliar com o banco (Story 4.6)', () => {
  const reconcileDialog = () => document.body.querySelector('[data-testid="reconcile-dialog"]')
  const bodyButton = (testid: string) =>
    document.body.querySelector<HTMLButtonElement>(`[data-testid="${testid}"]`)!
  const RECONCILED_AT = '2026-11-03T15:00:00Z'

  const nubank = () => account({ id: 'a1', accountName: 'Nubank', currentBalance: 1000 })
  const inter = () =>
    account({
      id: 'a2',
      accountName: 'Inter',
      bankName: 'Inter',
      currentBalance: 500,
      createdAt: '2026-11-02T12:00:00Z',
    })

  function result(bankBalance: number, previous = 1000): ReconciliationResult {
    return {
      account: { ...nubank(), currentBalance: bankBalance, lastReconciliationDate: RECONCILED_AT },
      reconciliation: {
        id: 'r1',
        accountId: 'a1',
        previousBalance: previous,
        bankBalance,
        difference: bankBalance - previous,
        reason: null,
        reconciledAt: RECONCILED_AT,
      },
    }
  }

  async function openReconcile(wrapper: Wrapper, id = 'a1') {
    await wrapper.get(`[data-testid="reconcile-account-${id}"]`).trigger('click')
    await flushPromises()
    return wrapper.findComponent(ReconcileForm)
  }

  type RForm = Awaited<ReturnType<typeof openReconcile>>

  async function fill(form: RForm, bank: string) {
    await form.get('[data-testid="reconcile-bank-balance-input"]').setValue(bank)
  }

  async function submit(form: RForm) {
    await form.get('[data-testid="reconcile-form"]').trigger('submit')
    await flushPromises()
  }

  beforeEach(() => {
    mockList.mockReset().mockResolvedValue([nubank(), inter()])
    mockReconcile.mockReset().mockImplementation(async (_id, input) => result(input.bankBalance))
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('cada conta tem "Reconciliar" com o nome no rótulo acessível e alvo de 44px', async () => {
    const wrapper = await mountView()
    const button = wrapper.get('[data-testid="reconcile-account-a1"]')

    expect(button.text()).toBe('Reconciliar')
    expect(button.attributes('aria-label')).toBe('Reconciliar Nubank')
    expect(button.classes()).toContain('min-h-[44px]')
    expect(wrapper.get('[data-testid="reconcile-account-a2"]').attributes('aria-label')).toBe(
      'Reconciliar Inter',
    )
  })

  it('mostra "Reconciliada em dd/mm/aaaa" (fuso de São Paulo) só quando houver data', async () => {
    mockList.mockResolvedValue([
      account({ id: 'a1', lastReconciliationDate: '2026-11-04T01:00:00Z' }),
      inter(),
    ])
    const wrapper = await mountView()

    expect(
      wrapper.get('[data-testid="account-item-a1"] [data-testid="account-reconciled-at"]').text(),
    ).toBe('Reconciliada em 03/11/2026')
    expect(
      wrapper
        .find('[data-testid="account-item-a2"] [data-testid="account-reconciled-at"]')
        .exists(),
    ).toBe(false)
  })

  it('abre "Reconciliar Nubank" com o saldo no porquinho e foco no saldo do banco', async () => {
    const wrapper = await mountView()
    await openReconcile(wrapper)

    expect(reconcileDialog()?.textContent).toContain('Reconciliar Nubank')
    expect(
      plain(
        document.body.querySelector('[data-testid="reconcile-current-balance"]')!.textContent!,
      ).trim(),
    ).toBe('Saldo no porquinho: R$ 1.000,00')
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('reconcile-bank-balance-input')
  })

  it('Nubank 1.000,00 → banco 1.050,00, "Ajustar saldo": saldo, data, total e "Saldo reconciliado"', async () => {
    const wrapper = await mountView()
    const form = await openReconcile(wrapper)
    await fill(form, '1.050,00')
    expect(plain(form.get('[data-testid="reconcile-difference-value"]').text())).toBe('+R$ 50,00')
    expect(form.get('[data-testid="reconcile-question"]').text()).toBe('Deseja ajustar o saldo?')
    // Depois da reconciliação a lista vem do servidor
    mockList.mockResolvedValue([
      { ...nubank(), currentBalance: 1050, lastReconciliationDate: RECONCILED_AT },
      inter(),
    ])
    await submit(form)

    expect(mockReconcile).toHaveBeenCalledWith('a1', { bankBalance: 1050, reason: null })
    expect(reconcileDialog()).toBeNull()
    expect(
      plain(wrapper.get('[data-testid="account-item-a1"] [data-testid="account-balance"]').text()),
    ).toBe('R$ 1.050,00')
    expect(
      wrapper.get('[data-testid="account-item-a1"] [data-testid="account-reconciled-at"]').text(),
    ).toBe('Reconciliada em 03/11/2026')
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 1.550,00')
    const success = wrapper.get('[data-testid="accounts-success"]')
    expect(success.attributes('role')).toBe('status')
    expect(success.text()).toBe('Saldo reconciliado')
    expect(mockList).toHaveBeenCalledTimes(2)
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('reconcile-account-a1')
  })

  it('banco mostra menos: diferença −20,00 em vermelho; ajuste leva a 980,00', async () => {
    const wrapper = await mountView()
    const form = await openReconcile(wrapper)
    await fill(form, '980')
    const difference = form.get('[data-testid="reconcile-difference-value"]')
    expect(plain(difference.text())).toBe('-R$ 20,00')
    expect(difference.classes()).toContain('text-red-600')
    mockList.mockResolvedValue([{ ...nubank(), currentBalance: 980 }, inter()])
    await submit(form)

    expect(
      plain(wrapper.get('[data-testid="account-item-a1"] [data-testid="account-balance"]').text()),
    ).toBe('R$ 980,00')
  })

  it('saldos iguais: "Confirmar reconciliação" registra e mostra a data', async () => {
    const wrapper = await mountView()
    const form = await openReconcile(wrapper)
    await fill(form, '1.000,00')
    expect(form.find('[data-testid="reconcile-question"]').exists()).toBe(false)
    expect(form.get('[data-testid="reconcile-confirm-button"]').text()).toBe(
      'Confirmar reconciliação',
    )
    mockList.mockResolvedValue([{ ...nubank(), lastReconciliationDate: RECONCILED_AT }, inter()])
    await submit(form)

    expect(mockReconcile).toHaveBeenCalledWith('a1', { bankBalance: 1000, reason: null })
    expect(reconcileDialog()).toBeNull()
    expect(wrapper.get('[data-testid="accounts-success"]').text()).toBe('Saldo reconciliado')
    expect(wrapper.find('[data-testid="account-reconciled-at"]').exists()).toBe(true)
  })

  it('"Investigar depois" fecha sem mudar nada', async () => {
    const wrapper = await mountView()
    const form = await openReconcile(wrapper)
    await fill(form, '1.050,00')
    bodyButton('reconcile-later-button').click()
    await flushPromises()

    expect(reconcileDialog()).toBeNull()
    expect(mockReconcile).not.toHaveBeenCalled()
    expect(
      plain(wrapper.get('[data-testid="account-item-a1"] [data-testid="account-balance"]').text()),
    ).toBe('R$ 1.000,00')
    expect(wrapper.get('[data-testid="accounts-success"]').text()).toBe('')
    expect(mockList).toHaveBeenCalledTimes(1)
  })

  it('saldo do banco vazio mostra o erro no campo e o diálogo continua aberto', async () => {
    const wrapper = await mountView()
    const form = await openReconcile(wrapper)
    await submit(form)

    expect(form.get('[data-testid="reconcile-bank-balance-error"]').text()).toBe(
      'Informe o saldo no banco',
    )
    expect(reconcileDialog()).not.toBeNull()
    expect(mockReconcile).not.toHaveBeenCalled()
  })

  it('404: fecha o diálogo, avisa e recarrega as contas', async () => {
    mockReconcile.mockRejectedValueOnce({
      response: { status: 404, data: { detail: 'Conta não encontrada' } },
    })
    const wrapper = await mountView()
    const form = await openReconcile(wrapper)
    await fill(form, '1.050,00')
    mockList.mockResolvedValue([inter()])
    await submit(form)

    expect(reconcileDialog()).toBeNull()
    expect(wrapper.get('[data-testid="accounts-notice"]').text()).toBe('Esta conta não existe mais')
    expect(mockList).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="account-item-a1"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="account-item-a2"]').exists()).toBe(true)
  })

  it('recarga falha depois de reconciliar: a linha mostra a conta devolvida pelo POST, total e aviso', async () => {
    const wrapper = await mountView()
    const form = await openReconcile(wrapper)
    await fill(form, '1.050,00')
    mockList.mockRejectedValue(new Error('network'))
    await submit(form)

    expect(reconcileDialog()).toBeNull()
    expect(
      plain(wrapper.get('[data-testid="account-item-a1"] [data-testid="account-balance"]').text()),
    ).toBe('R$ 1.050,00')
    expect(
      wrapper.get('[data-testid="account-item-a1"] [data-testid="account-reconciled-at"]').text(),
    ).toBe('Reconciliada em 03/11/2026')
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 1.550,00')
    expect(wrapper.get('[data-testid="accounts-success"]').text()).toBe('Saldo reconciliado')
    expect(wrapper.get('[data-testid="accounts-notice"]').text()).toBe(
      'Não foi possível atualizar os saldos. Recarregue a página.',
    )
  })

  it('404 com recarga falhando: a conta sai da lista mesmo assim', async () => {
    mockReconcile.mockRejectedValueOnce({
      response: { status: 404, data: { detail: 'Conta não encontrada' } },
    })
    const wrapper = await mountView()
    const form = await openReconcile(wrapper)
    await fill(form, '1.050,00')
    mockList.mockRejectedValue(new Error('network'))
    await submit(form)

    expect(reconcileDialog()).toBeNull()
    expect(wrapper.find('[data-testid="account-item-a1"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="account-item-a2"]').exists()).toBe(true)
    expect(plain(wrapper.get('[data-testid="accounts-total"]').text())).toBe('R$ 500,00')
    expect(wrapper.find('[data-testid="accounts-notice"]').exists()).toBe(true)
  })

  it('outra falha mostra a mensagem no diálogo, que continua aberto com os dados', async () => {
    mockReconcile.mockRejectedValueOnce(new Error('network'))
    const wrapper = await mountView()
    const form = await openReconcile(wrapper)
    await fill(form, '1.050,00')
    await submit(form)

    expect(
      document.body.querySelector('[data-testid="reconcile-error"]')?.textContent?.trim(),
    ).toBe('Não foi possível reconciliar a conta. Tente novamente.')
    expect(reconcileDialog()).not.toBeNull()
    expect(
      (form.get('[data-testid="reconcile-bank-balance-input"]').element as HTMLInputElement).value,
    ).toBe('1.050,00')
  })

  it('saldo mudou em outra aba: a tela mostra o que o servidor devolveu', async () => {
    // Tela com 1.000,00; o servidor tinha 900,00 e calcula a diferença a partir dele
    mockReconcile.mockResolvedValueOnce(result(1050, 900))
    const wrapper = await mountView()
    const form = await openReconcile(wrapper)
    await fill(form, '1.050,00')
    mockList.mockResolvedValue([{ ...nubank(), currentBalance: 1050 }, inter()])
    await submit(form)

    expect(mockList).toHaveBeenCalledTimes(2)
    expect(
      plain(wrapper.get('[data-testid="account-item-a1"] [data-testid="account-balance"]').text()),
    ).toBe('R$ 1.050,00')
  })

  it('enquanto envia, mostra "Salvando...", os botões travam e o diálogo não fecha', async () => {
    let resolve: (r: ReconciliationResult) => void = () => {}
    mockReconcile.mockReturnValue(new Promise<ReconciliationResult>((r) => (resolve = r)))
    const wrapper = await mountView()
    const form = await openReconcile(wrapper)
    await fill(form, '1.050,00')
    await submit(form)

    expect(bodyButton('reconcile-adjust-button').disabled).toBe(true)
    expect(bodyButton('reconcile-adjust-button').textContent?.trim()).toBe('Salvando...')
    expect(bodyButton('reconcile-later-button').disabled).toBe(true)
    // Diferença ≠ 0: só "Ajustar saldo" e "Investigar depois"
    expect(document.body.querySelector('[data-testid="cancel-reconcile"]')).toBeNull()
    wrapper.findAllComponents({ name: 'Dialog' })[3]!.vm.$emit('update:open', false)
    await flushPromises()
    expect(reconcileDialog()).not.toBeNull()

    await form.get('[data-testid="reconcile-form"]').trigger('submit')
    resolve(result(1050))
    await flushPromises()
    expect(reconcileDialog()).toBeNull()
    expect(mockReconcile).toHaveBeenCalledOnce()
  })

  it('Cancelar fecha sem reconciliar e devolve o foco ao "Reconciliar" da conta', async () => {
    const wrapper = await mountView()
    await openReconcile(wrapper, 'a2')
    bodyButton('cancel-reconcile').click()
    await flushPromises()
    await nextTick()

    expect(reconcileDialog()).toBeNull()
    expect(mockReconcile).not.toHaveBeenCalled()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('reconcile-account-a2')
  })
})
