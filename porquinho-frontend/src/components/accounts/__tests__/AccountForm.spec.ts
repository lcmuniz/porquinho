import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AccountForm from '../AccountForm.vue'
import type { Account } from '@/types/account'

const mockCreate = vi.fn()

vi.mock('@/services/accountService', () => ({
  createAccount: (...a: unknown[]) => mockCreate(...a),
}))

function created(overrides: Partial<Account> = {}): Account {
  return {
    id: 'new',
    accountName: 'Inter',
    bankName: 'Inter',
    accountType: 'CHECKING',
    initialBalance: 500,
    currentBalance: 500,
    createdAt: '2026-11-03T12:00:00Z',
    ...overrides,
  }
}

function mountForm(props: Record<string, unknown> = {}) {
  return mount(AccountForm, { props, attachTo: document.body })
}

type Wrapper = ReturnType<typeof mountForm>

async function fill(wrapper: Wrapper, name: string, balance: string, bank = '') {
  await wrapper.get('[data-testid="account-name-input"]').setValue(name)
  await wrapper.get('[data-testid="bank-name-input"]').setValue(bank)
  await wrapper.get('[data-testid="initial-balance-input"]').setValue(balance)
}

async function submit(wrapper: Wrapper) {
  await wrapper.get('[data-testid="account-form"]').trigger('submit')
  await flushPromises()
}

const inputValue = (wrapper: Wrapper, testId: string) =>
  (wrapper.get(`[data-testid="${testId}"]`).element as HTMLInputElement).value

describe('AccountForm', () => {
  beforeEach(() => {
    mockCreate.mockReset().mockResolvedValue(created())
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('tem label em todos os campos e alvos de 44px', () => {
    const wrapper = mountForm()
    for (const id of ['account-name', 'bank-name', 'initial-balance']) {
      expect(wrapper.find(`label[for="${id}"]`).exists()).toBe(true)
      expect(wrapper.get(`#${id}`).classes()).toContain('min-h-[44px]')
    }
    expect(wrapper.get('[data-testid="add-account-button"]').classes()).toContain('min-h-[44px]')
  })

  it('envia os dados convertidos, emite created e limpa o formulário', async () => {
    const wrapper = mountForm()
    await fill(wrapper, ' Inter ', '500,00', 'Inter')
    await submit(wrapper)

    expect(mockCreate).toHaveBeenCalledWith({
      accountName: 'Inter',
      bankName: 'Inter',
      accountType: 'CHECKING',
      initialBalance: 500,
    })
    expect(wrapper.emitted('created')?.[0]).toEqual([created()])
    expect(inputValue(wrapper, 'account-name-input')).toBe('')
    expect(inputValue(wrapper, 'initial-balance-input')).toBe('')
  })

  it('validação local mostra o erro no campo e não chama a API', async () => {
    const wrapper = mountForm()
    await fill(wrapper, '', 'abc')
    await submit(wrapper)

    expect(wrapper.get('[data-testid="account-name-error"]').text()).toBe('Informe o nome da conta')
    expect(wrapper.get('#account-name').attributes('aria-describedby')).toBe('account-name-error')
    expect(wrapper.get('[data-testid="initial-balance-error"]').text()).toContain('1.234,56')
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('400 com errors aparece no campo, sem emitir failed', async () => {
    mockCreate.mockRejectedValue({
      response: { status: 400, data: { errors: { initialBalance: 'Saldo inválido' } } },
    })
    const wrapper = mountForm()
    await fill(wrapper, 'Inter', '1')
    await submit(wrapper)

    expect(wrapper.get('[data-testid="initial-balance-error"]').text()).toBe('Saldo inválido')
    expect(wrapper.emitted('failed')).toBeUndefined()
  })

  it('400 em accountType marca os radios e liga o erro por aria-describedby', async () => {
    mockCreate.mockRejectedValue({
      response: { status: 400, data: { errors: { accountType: 'Escolha o tipo da conta' } } },
    })
    const wrapper = mountForm()
    await fill(wrapper, 'Inter', '1')
    await submit(wrapper)

    expect(wrapper.get('[data-testid="account-type-error"]').text()).toBe('Escolha o tipo da conta')
    for (const type of ['CHECKING', 'SAVINGS']) {
      const radio = wrapper.get(`[data-testid="account-type-${type}"]`)
      expect(radio.attributes('aria-invalid')).toBe('true')
      expect(radio.attributes('aria-describedby')).toBe('account-type-error')
    }
  })

  it('outra falha emite failed com o detail e mantém os dados', async () => {
    mockCreate.mockRejectedValue({ response: { status: 500, data: { detail: 'Erro interno' } } })
    const wrapper = mountForm()
    await fill(wrapper, 'Inter', '500,00')
    await submit(wrapper)

    expect(wrapper.emitted('failed')?.[0]).toEqual(['Erro interno'])
    expect(inputValue(wrapper, 'account-name-input')).toBe('Inter')
    expect(inputValue(wrapper, 'initial-balance-input')).toBe('500,00')
  })

  it('falha de rede emite failed sem detail', async () => {
    mockCreate.mockRejectedValue(new Error('Network Error'))
    const wrapper = mountForm()
    await fill(wrapper, 'Inter', '1')
    await submit(wrapper)

    expect(wrapper.emitted('failed')?.[0]).toEqual([undefined])
  })

  it('disabled bloqueia o envio', async () => {
    const wrapper = mountForm({ disabled: true })
    await fill(wrapper, 'Inter', '1')
    await submit(wrapper)

    expect(wrapper.get('[data-testid="add-account-button"]').attributes('disabled')).toBeDefined()
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('informa o envio em andamento por v-model:submitting e usa os rótulos recebidos', async () => {
    let resolve: (a: Account) => void = () => {}
    mockCreate.mockReturnValue(new Promise<Account>((r) => (resolve = r)))
    const wrapper = mountForm({ submitLabel: 'Salvar conta', submittingLabel: 'Salvando...' })
    expect(wrapper.get('[data-testid="add-account-button"]').text()).toBe('Salvar conta')

    await fill(wrapper, 'Inter', '1')
    await wrapper.get('[data-testid="account-form"]').trigger('submit')
    expect(wrapper.emitted('update:submitting')?.[0]).toEqual([true])
    await flushPromises()
    const button = wrapper.get('[data-testid="add-account-button"]')
    expect(button.text()).toBe('Salvando...')
    expect(button.attributes('disabled')).toBeDefined()

    resolve(created())
    await flushPromises()
    const updates = wrapper.emitted('update:submitting') ?? []
    expect(updates[updates.length - 1]).toEqual([false])
  })

  it('focus() foca o campo de nome', () => {
    const wrapper = mountForm()
    ;(wrapper.vm as unknown as { focus: () => void }).focus()
    expect(document.activeElement?.id).toBe('account-name')
  })

  it('dois envios no mesmo tick chamam a API uma vez só', async () => {
    // Modelo controlado pelo pai: `submitting` só muda quando o pai re-renderiza
    const wrapper = mountForm({ submitting: false, 'onUpdate:submitting': () => {} })
    await fill(wrapper, 'Inter', '1')
    const form = wrapper.get('[data-testid="account-form"]')
    form.trigger('submit')
    form.trigger('submit')
    await flushPromises()

    expect(mockCreate).toHaveBeenCalledOnce()
  })

  it('emite attempt em toda tentativa, mesmo quando a validação local falha', async () => {
    const wrapper = mountForm()
    await fill(wrapper, '', '1')
    await submit(wrapper)

    expect(wrapper.emitted('attempt')).toHaveLength(1)
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('400 com campo conhecido e campo desconhecido mostra o erro no campo e emite failed com o detail', async () => {
    mockCreate.mockRejectedValue({
      response: {
        status: 400,
        data: {
          detail: 'Dados inválidos',
          errors: { initialBalance: 'Saldo inválido', currency: 'Campo desconhecido' },
        },
      },
    })
    const wrapper = mountForm()
    await fill(wrapper, 'Inter', '1')
    await submit(wrapper)

    expect(wrapper.get('[data-testid="initial-balance-error"]').text()).toBe('Saldo inválido')
    expect(wrapper.emitted('failed')?.[0]).toEqual(['Dados inválidos'])
  })

  it('400 só com campos desconhecidos emite failed com o detail', async () => {
    mockCreate.mockRejectedValue({
      response: {
        status: 400,
        data: { detail: 'Dados inválidos', errors: { currency: 'Campo desconhecido' } },
      },
    })
    const wrapper = mountForm()
    await fill(wrapper, 'Inter', '1')
    await submit(wrapper)

    expect(wrapper.emitted('failed')?.[0]).toEqual(['Dados inválidos'])
  })
})
