import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { nextTick } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import TransferForm from '../TransferForm.vue'
import { toISODate, todayInSaoPaulo } from '@/lib/goalPlan'
import type { Account } from '@/types/account'
import type { Transfer } from '@/types/transfer'

const mockCreate = vi.fn()

vi.mock('@/services/transferService', () => ({
  createTransfer: (...a: unknown[]) => mockCreate(...a),
}))

function account(id: string, name: string, balance: number): Account {
  return {
    id,
    accountName: name,
    bankName: null,
    accountType: 'CHECKING',
    initialBalance: balance,
    currentBalance: balance,
    createdAt: '2026-11-01T12:00:00Z',
    lastReconciliationDate: null,
  }
}

const ACCOUNTS = [account('a1', 'Nubank', 1234.56), account('a2', 'Inter', 600)]

const created: Transfer = {
  id: 't1',
  fromAccountId: 'a1',
  fromAccountName: 'Nubank',
  toAccountId: 'a2',
  toAccountName: 'Inter',
  amount: 200,
  transferDate: '2026-11-03',
  description: 'Reserva',
  createdAt: '2026-11-03T15:00:00Z',
}

function mountForm(props: Record<string, unknown> = {}) {
  return mount(TransferForm, { props: { accounts: ACCOUNTS, ...props }, attachTo: document.body })
}

type Wrapper = ReturnType<typeof mountForm>

async function submit(wrapper: Wrapper) {
  await wrapper.get('[data-testid="transfer-form"]').trigger('submit')
  await flushPromises()
}

const today = toISODate(todayInSaoPaulo())

describe('TransferForm', () => {
  beforeEach(() => {
    mockCreate.mockReset().mockResolvedValue(created)
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('tem label em todos os campos e alvos de 44px', () => {
    const wrapper = mountForm()
    for (const id of [
      'transfer-from',
      'transfer-to',
      'transfer-amount',
      'transfer-date',
      'transfer-description',
    ]) {
      expect(wrapper.find(`label[for="${id}"]`).exists()).toBe(true)
      expect(wrapper.get(`#${id}`).classes()).toContain('min-h-[44px]')
    }
    expect(wrapper.get('[data-testid="save-transfer-button"]').classes()).toContain('min-h-[44px]')
  })

  it('usa <select> nativo e <input type="date"> com padrão e máximo hoje', () => {
    const wrapper = mountForm()
    expect(wrapper.get('[data-testid="transfer-from-select"]').element.tagName).toBe('SELECT')
    const date = wrapper.get('[data-testid="transfer-date-input"]').element as HTMLInputElement
    expect(date.type).toBe('date')
    expect(date.value).toBe(today)
    expect(date.max).toBe(today)
  })

  it('envia valor no formato brasileiro, a data e a descrição sem espaços e emite created', async () => {
    const wrapper = mountForm()
    await wrapper.get('[data-testid="transfer-amount-input"]').setValue('1.200,50')
    await wrapper.get('[data-testid="transfer-date-input"]').setValue('2026-10-31')
    await wrapper.get('[data-testid="transfer-description-input"]').setValue('  Reserva ')
    await submit(wrapper)

    expect(mockCreate).toHaveBeenCalledWith({
      id: expect.stringMatching(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}$/),
      fromAccountId: 'a1',
      toAccountId: 'a2',
      amount: 1200.5,
      transferDate: '2026-10-31',
      description: 'Reserva',
    })
    expect(wrapper.emitted('attempt')).toHaveLength(1)
    expect(wrapper.emitted('created')?.[0]).toEqual([created])
  })

  it('descrição vazia vai como null', async () => {
    const wrapper = mountForm()
    await wrapper.get('[data-testid="transfer-amount-input"]').setValue('10')
    await wrapper.get('[data-testid="transfer-description-input"]').setValue('   ')
    await submit(wrapper)

    expect(mockCreate.mock.calls[0]![0].description).toBeNull()
  })

  it('valor vazio, negativo, com 3 casas ou 0 mostra o erro no campo sem chamar a API', async () => {
    const wrapper = mountForm()
    const cases: [string, string][] = [
      ['', 'Informe o valor da transferência'],
      ['0,00', 'O valor precisa ser maior que zero'],
      ['-10', 'O valor precisa ser maior que zero'],
      ['10,123', 'Digite o valor no formato 1.234,56 (até 2 casas depois da vírgula)'],
    ]
    for (const [value, message] of cases) {
      await wrapper.get('[data-testid="transfer-amount-input"]').setValue(value)
      await submit(wrapper)
      expect(wrapper.get('[data-testid="transfer-amount-error"]').text()).toBe(message)
      expect(wrapper.get('[data-testid="transfer-amount-input"]').attributes('aria-invalid')).toBe(
        'true',
      )
    }
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('mesma conta nos dois lados mostra "Escolha contas diferentes" no destino', async () => {
    const wrapper = mountForm()
    await wrapper.get('[data-testid="transfer-to-select"]').setValue('a1')
    await wrapper.get('[data-testid="transfer-amount-input"]').setValue('10')
    await submit(wrapper)

    expect(wrapper.get('[data-testid="transfer-to-error"]').text()).toBe(
      'Escolha contas diferentes',
    )
    expect(wrapper.get('[data-testid="transfer-to-select"]').attributes('aria-describedby')).toBe(
      'transfer-to-error',
    )
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('data vazia ou futura mostra o erro no campo', async () => {
    const wrapper = mountForm()
    await wrapper.get('[data-testid="transfer-amount-input"]').setValue('10')
    await wrapper.get('[data-testid="transfer-date-input"]').setValue('')
    await submit(wrapper)
    expect(wrapper.get('[data-testid="transfer-date-error"]').text()).toBe(
      'Informe a data da transferência',
    )

    await wrapper.get('[data-testid="transfer-date-input"]').setValue('2999-12-31')
    await submit(wrapper)
    expect(wrapper.get('[data-testid="transfer-date-error"]').text()).toBe(
      'A data não pode estar no futuro',
    )
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('400 com errors por campo aparece nos campos; campo desconhecido também emite failed', async () => {
    mockCreate.mockRejectedValue({
      response: {
        status: 400,
        data: {
          detail: 'O valor precisa ser maior que zero',
          errors: { amount: 'O valor precisa ser maior que zero', outro: 'Algo' },
        },
      },
    })
    const wrapper = mountForm()
    await wrapper.get('[data-testid="transfer-amount-input"]').setValue('10')
    await submit(wrapper)

    expect(wrapper.get('[data-testid="transfer-amount-error"]').text()).toBe(
      'O valor precisa ser maior que zero',
    )
    expect(wrapper.emitted('failed')?.[0]).toEqual(['O valor precisa ser maior que zero', 400])
  })

  it('404 e falhas sem errors vão para o pai em failed', async () => {
    mockCreate.mockRejectedValue({
      response: { status: 404, data: { detail: 'Conta não encontrada' } },
    })
    const wrapper = mountForm()
    await wrapper.get('[data-testid="transfer-amount-input"]').setValue('10')
    await submit(wrapper)

    expect(wrapper.emitted('failed')?.[0]).toEqual(['Conta não encontrada', 404])
    expect(wrapper.emitted('created')).toBeUndefined()
  })

  it('disabled bloqueia o envio', async () => {
    const wrapper = mountForm({ disabled: true })
    await wrapper.get('[data-testid="transfer-amount-input"]').setValue('10')
    await submit(wrapper)

    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('focus() foca a conta de origem', () => {
    const wrapper = mountForm()
    ;(wrapper.vm as unknown as { focus: () => void }).focus()
    expect(document.activeElement?.id).toBe('transfer-from')
  })

  it('reenvios do mesmo formulário usam o mesmo id; outro formulário usa outro', async () => {
    mockCreate.mockRejectedValueOnce(new Error('Network Error'))
    const wrapper = mountForm()
    await wrapper.get('[data-testid="transfer-amount-input"]').setValue('10')
    await submit(wrapper)
    await submit(wrapper)
    const other = mountForm()
    await other.get('[data-testid="transfer-amount-input"]').setValue('10')
    await submit(other)

    const ids = mockCreate.mock.calls.map((call) => call[0].id)
    expect(ids[1]).toBe(ids[0])
    expect(ids[2]).not.toBe(ids[0])
  })

  it('"hoje" é calculado na validação e no máximo, não só ao abrir', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    try {
      vi.setSystemTime(new Date('2026-11-03T12:00:00-03:00'))
      const wrapper = mountForm()
      expect(
        (wrapper.get('[data-testid="transfer-date-input"]').element as HTMLInputElement).max,
      ).toBe('2026-11-03')

      // Passa da meia-noite em São Paulo com o formulário aberto
      vi.setSystemTime(new Date('2026-11-04T00:30:00-03:00'))
      await wrapper.get('[data-testid="transfer-amount-input"]').setValue('10')
      await wrapper.get('[data-testid="transfer-date-input"]').setValue('2026-11-04')
      await submit(wrapper)

      expect(wrapper.find('[data-testid="transfer-date-error"]').exists()).toBe(false)
      expect(mockCreate.mock.calls[0]![0].transferDate).toBe('2026-11-04')
      expect(
        (wrapper.get('[data-testid="transfer-date-input"]').element as HTMLInputElement).max,
      ).toBe('2026-11-04')
    } finally {
      vi.useRealTimers()
    }
  })

  it('se a conta escolhida some da lista, outra disponível é escolhida', async () => {
    const wrapper = mountForm()
    await wrapper.setProps({ accounts: [ACCOUNTS[0]!, account('a3', 'BB', 10)] })
    await nextTick()

    expect(
      (wrapper.get('[data-testid="transfer-from-select"]').element as HTMLSelectElement).value,
    ).toBe('a1')
    expect(
      (wrapper.get('[data-testid="transfer-to-select"]').element as HTMLSelectElement).value,
    ).toBe('a3')
  })
})
