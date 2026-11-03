import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AccountsStep from '../AccountsStep.vue'
import type { Account } from '@/types/account'

const mockList = vi.fn()
const mockCreate = vi.fn()
const mockDelete = vi.fn()
const mockCompleteAccounts = vi.fn()

vi.mock('@/services/accountService', () => ({
  listAccounts: (...a: unknown[]) => mockList(...a),
  createAccount: (...a: unknown[]) => mockCreate(...a),
  deleteAccount: (...a: unknown[]) => mockDelete(...a),
}))

vi.mock('@/services/onboardingService', () => ({
  completeAccounts: (...a: unknown[]) => mockCompleteAccounts(...a),
}))

function account(overrides: Partial<Account> = {}): Account {
  return {
    id: 'a1',
    accountName: 'Nubank',
    bankName: 'Nubank',
    accountType: 'CHECKING',
    initialBalance: 1234.56,
    currentBalance: 1234.56,
    createdAt: '2026-11-02T12:00:00Z',
    ...overrides,
  }
}

const nextProgress = {
  currentStep: 3,
  stepsCompleted: ['tutorial_viewed', 'accounts_added'],
  completed: false,
}

async function mountStep() {
  const wrapper = mount(AccountsStep, { attachTo: document.body })
  await flushPromises()
  return wrapper
}

type Wrapper = Awaited<ReturnType<typeof mountStep>>

async function fill(wrapper: Wrapper, name: string, balance: string, bank = '') {
  await wrapper.get('[data-testid="account-name-input"]').setValue(name)
  await wrapper.get('[data-testid="bank-name-input"]').setValue(bank)
  await wrapper.get('[data-testid="initial-balance-input"]').setValue(balance)
}

async function submit(wrapper: Wrapper) {
  await wrapper.get('[data-testid="account-form"]').trigger('submit')
  await flushPromises()
}

const plain = (s: string) => s.replace(/\s/g, ' ')

describe('AccountsStep', () => {
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
    mockDelete.mockReset().mockResolvedValue(undefined)
    mockCompleteAccounts.mockReset().mockResolvedValue(nextProgress)
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('mostra o título, a lista vazia, Próximo desabilitado e as sugestões de banco', async () => {
    const wrapper = await mountStep()

    expect(wrapper.text()).toContain('Adicione suas contas bancárias')
    expect(wrapper.find('[data-testid="accounts-empty"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="next-button"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="skip-button"]').text()).toContain('Pular por agora')
    const options = wrapper.findAll('#bank-suggestions option').map((o) => o.attributes('value'))
    expect(options).toContain('Nubank')
    expect(options).toContain('Banco do Brasil')
  })

  it('adiciona "Nubank" com R$ 1.234,56 e mostra na lista', async () => {
    const wrapper = await mountStep()
    await fill(wrapper, '  Nubank ', '1.234,56', 'Nubank')
    await submit(wrapper)

    expect(mockCreate).toHaveBeenCalledWith({
      accountName: 'Nubank',
      bankName: 'Nubank',
      accountType: 'CHECKING',
      initialBalance: 1234.56,
    })
    const item = wrapper.get('[data-testid="account-item-new-Nubank"]')
    expect(item.text()).toContain('Nubank')
    expect(item.text()).toContain('Conta corrente')
    expect(plain(item.get('[data-testid="account-balance"]').text())).toBe('R$ 1.234,56')
    // formulário limpo e Próximo habilitado
    expect(
      (wrapper.get('[data-testid="account-name-input"]').element as HTMLInputElement).value,
    ).toBe('')
    expect(wrapper.get('[data-testid="next-button"]').attributes('disabled')).toBeUndefined()
  })

  it('envia poupança e banco vazio como null', async () => {
    const wrapper = await mountStep()
    await fill(wrapper, 'Reserva', '500')
    await wrapper.get('[data-testid="account-type-SAVINGS"]').setValue(true)
    await submit(wrapper)

    expect(mockCreate).toHaveBeenCalledWith({
      accountName: 'Reserva',
      bankName: null,
      accountType: 'SAVINGS',
      initialBalance: 500,
    })
    expect(wrapper.text()).toContain('Poupança')
  })

  it('saldo negativo é aceito e aparece em vermelho', async () => {
    const wrapper = await mountStep()
    await fill(wrapper, 'Itaú', '-150,00')
    await submit(wrapper)

    expect(mockCreate).toHaveBeenCalledWith(expect.objectContaining({ initialBalance: -150 }))
    const balance = wrapper.get('[data-testid="account-balance"]')
    expect(balance.classes()).toContain('text-red-600')
    expect(plain(balance.text())).toBe('-R$ 150,00')
  })

  it('nome vazio mostra "Informe o nome da conta" e não chama a API', async () => {
    const wrapper = await mountStep()
    await fill(wrapper, '   ', '10,00')
    await submit(wrapper)

    expect(wrapper.get('[data-testid="account-name-error"]').text()).toBe('Informe o nome da conta')
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('saldo inválido ou vazio mostra a mensagem no campo', async () => {
    const wrapper = await mountStep()
    await fill(wrapper, 'Nubank', '12,345')
    await submit(wrapper)
    expect(wrapper.get('[data-testid="initial-balance-error"]').text()).toContain('1.234,56')

    await fill(wrapper, 'Nubank', '')
    await submit(wrapper)
    expect(wrapper.get('[data-testid="initial-balance-error"]').text()).toContain(
      'Informe o saldo inicial',
    )
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('erro 400 do backend aparece no campo', async () => {
    mockCreate.mockRejectedValue({
      response: {
        status: 400,
        data: {
          detail: 'Informe o nome da conta',
          errors: { accountName: 'Informe o nome da conta' },
        },
      },
    })
    const wrapper = await mountStep()
    await fill(wrapper, 'X', '1')
    await submit(wrapper)

    expect(wrapper.get('[data-testid="account-name-error"]').text()).toBe('Informe o nome da conta')
    expect(wrapper.emitted('error')).toBeUndefined()
  })

  it('falha de rede ao adicionar emite erro', async () => {
    mockCreate.mockRejectedValue(new Error('Network Error'))
    const wrapper = await mountStep()
    await fill(wrapper, 'Nubank', '1')
    await submit(wrapper)

    expect(wrapper.emitted('error')?.[0]?.[0]).toContain('Não foi possível adicionar a conta')
  })

  it('lista as contas já salvas e Remover tira da lista; Próximo desabilita ao zerar', async () => {
    mockList.mockResolvedValue([account()])
    const wrapper = await mountStep()

    expect(wrapper.get('[data-testid="accounts-list"]').text()).toContain('Nubank')
    expect(wrapper.get('[data-testid="next-button"]').attributes('disabled')).toBeUndefined()

    await wrapper.get('[data-testid="remove-account-a1"]').trigger('click')
    await flushPromises()

    expect(mockDelete).toHaveBeenCalledWith('a1')
    expect(wrapper.find('[data-testid="account-item-a1"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="next-button"]').attributes('disabled')).toBeDefined()
  })

  it('Próximo com contas conclui o passo sem pular e emite done', async () => {
    mockList.mockResolvedValue([account()])
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()

    expect(mockCompleteAccounts).toHaveBeenCalledWith(false)
    expect(wrapper.emitted('done')?.[0]).toEqual([nextProgress])
  })

  it('Pular por agora sem contas conclui o passo pulando', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="skip-button"]').trigger('click')
    await flushPromises()

    expect(mockCompleteAccounts).toHaveBeenCalledWith(true)
    expect(wrapper.emitted('done')).toHaveLength(1)
  })

  it('duplo clique em Próximo salva só uma vez', async () => {
    mockList.mockResolvedValue([account()])
    const wrapper = await mountStep()

    const next = wrapper.get('[data-testid="next-button"]')
    void next.trigger('click')
    void next.trigger('click')
    await flushPromises()

    expect(mockCompleteAccounts).toHaveBeenCalledOnce()
  })

  it('409 ao concluir mostra a mensagem do backend e não avança', async () => {
    mockList.mockResolvedValue([account()])
    mockCompleteAccounts.mockRejectedValue({
      response: { status: 409, data: { detail: 'Adicione ao menos uma conta para continuar' } },
    })
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('error')?.[0]).toEqual(['Adicione ao menos uma conta para continuar'])
    expect(wrapper.emitted('done')).toBeUndefined()
  })

  it('Voltar emite back', async () => {
    const wrapper = await mountStep()
    await wrapper.get('[data-testid="back-button"]').trigger('click')

    expect(wrapper.emitted('back')).toHaveLength(1)
  })

  it('falha ao carregar a lista emite erro e ainda permite adicionar', async () => {
    mockList.mockRejectedValue(new Error('Network Error'))
    const wrapper = await mountStep()

    expect(wrapper.emitted('error')?.[0]?.[0]).toContain('Não foi possível carregar suas contas')
    await fill(wrapper, 'Nubank', '1')
    await submit(wrapper)
    expect(mockCreate).toHaveBeenCalledOnce()
  })

  it('Remover com 404 tira da lista sem erro e desabilita Próximo ao zerar', async () => {
    mockList.mockResolvedValue([account()])
    mockDelete.mockRejectedValue({ response: { status: 404 } })
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="remove-account-a1"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="account-item-a1"]').exists()).toBe(false)
    expect(wrapper.emitted('error')).toBeUndefined()
    expect(wrapper.get('[data-testid="next-button"]').attributes('disabled')).toBeDefined()
  })

  it('Remover com 500 mantém a conta e emite erro', async () => {
    mockList.mockResolvedValue([account()])
    mockDelete.mockRejectedValue({ response: { status: 500 } })
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="remove-account-a1"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="account-item-a1"]').exists()).toBe(true)
    expect(wrapper.emitted('error')?.[0]?.[0]).toContain('Não foi possível remover a conta')
  })

  it('não adiciona enquanto a lista inicial carrega, e a resposta tardia não apaga nada', async () => {
    let resolveList: (value: Account[]) => void = () => {}
    mockList.mockReturnValue(new Promise<Account[]>((resolve) => (resolveList = resolve)))
    const wrapper = mount(AccountsStep, { attachTo: document.body })
    await flushPromises()

    expect(wrapper.get('[data-testid="add-account-button"]').attributes('disabled')).toBeDefined()
    await fill(wrapper, 'Nubank', '1')
    await submit(wrapper)
    expect(mockCreate).not.toHaveBeenCalled()

    resolveList([account({ id: 'old', accountName: 'Antiga' })])
    await flushPromises()
    expect(wrapper.get('[data-testid="add-account-button"]').attributes('disabled')).toBeUndefined()
    await submit(wrapper)
    expect(mockCreate).toHaveBeenCalledOnce()
    expect(wrapper.find('[data-testid="account-item-old"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="account-item-new-Nubank"]').exists()).toBe(true)
  })

  it('com a lista sem carregar, Próximo fica liberado e o 409 do backend aparece', async () => {
    mockList.mockRejectedValue(new Error('Network Error'))
    mockCompleteAccounts.mockRejectedValue({
      response: { status: 409, data: { detail: 'Adicione ao menos uma conta para continuar' } },
    })
    const wrapper = await mountStep()

    const next = wrapper.get('[data-testid="next-button"]')
    expect(next.attributes('disabled')).toBeUndefined()
    await next.trigger('click')
    await flushPromises()

    expect(mockCompleteAccounts).toHaveBeenCalledWith(false)
    expect(wrapper.emitted('error')?.[1]).toEqual(['Adicione ao menos uma conta para continuar'])
  })

  it('400 sem erro em campo conhecido emite o detail da API', async () => {
    mockCreate.mockRejectedValue({
      response: { status: 400, data: { detail: 'Dados inválidos', errors: { other: 'x' } } },
    })
    const wrapper = await mountStep()
    await fill(wrapper, 'Nubank', '1')
    await submit(wrapper)

    expect(wrapper.emitted('error')?.[0]).toEqual(['Dados inválidos'])
    expect(wrapper.find('[data-testid="account-name-error"]').exists()).toBe(false)
  })

  it('erro do saldo entra no aria-describedby do campo', async () => {
    const wrapper = await mountStep()
    const input = wrapper.get('[data-testid="initial-balance-input"]')
    expect(input.attributes('aria-describedby')).toBe('initial-balance-hint')

    await fill(wrapper, 'Nubank', 'abc')
    await submit(wrapper)

    expect(input.attributes('aria-describedby')).toBe('initial-balance-error initial-balance-hint')
    expect(wrapper.get('#initial-balance-error').text()).toContain('1.234,56')
  })

  it('enquanto a conta é criada, Próximo, Pular e Remover ficam desabilitados', async () => {
    mockList.mockResolvedValue([account()])
    let resolveCreate: (a: Account) => void = () => {}
    mockCreate.mockReturnValue(new Promise<Account>((resolve) => (resolveCreate = resolve)))
    const wrapper = await mountStep()
    await fill(wrapper, 'Inter', '1')
    await submit(wrapper)

    for (const id of ['next-button', 'skip-button', 'remove-account-a1']) {
      expect(wrapper.get(`[data-testid="${id}"]`).attributes('disabled')).toBeDefined()
    }
    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()
    expect(mockCompleteAccounts).not.toHaveBeenCalled()

    resolveCreate(account({ id: 'new' }))
    await flushPromises()
    expect(wrapper.get('[data-testid="next-button"]').attributes('disabled')).toBeUndefined()
  })
})
