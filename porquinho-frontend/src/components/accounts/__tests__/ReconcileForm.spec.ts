import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ReconcileForm from '../ReconcileForm.vue'
import type { Account, ReconciliationResult } from '@/types/account'

const mockReconcile = vi.fn()

vi.mock('@/services/accountService', () => ({
  reconcileAccount: (...a: unknown[]) => mockReconcile(...a),
}))

const NUBANK: Account = {
  id: 'a1',
  accountName: 'Nubank',
  bankName: null,
  accountType: 'CHECKING',
  initialBalance: 1000,
  currentBalance: 1000,
  createdAt: '2026-11-01T12:00:00Z',
  lastReconciliationDate: null,
  version: 0,
}

const result: ReconciliationResult = {
  account: { ...NUBANK, currentBalance: 1050, lastReconciliationDate: '2026-11-03T15:00:00Z' },
  reconciliation: {
    id: 'r1',
    accountId: 'a1',
    previousBalance: 1000,
    bankBalance: 1050,
    difference: 50,
    reason: null,
    reconciledAt: '2026-11-03T15:00:00Z',
  },
}

function mountForm(props: Record<string, unknown> = {}) {
  return mount(ReconcileForm, { props: { account: NUBANK, ...props }, attachTo: document.body })
}

type Wrapper = ReturnType<typeof mountForm>

const plain = (s: string) => s.replace(/\s/g, ' ').trim()

function difference(wrapper: Wrapper) {
  return wrapper.get('[data-testid="reconcile-difference-value"]')
}

async function typeBank(wrapper: Wrapper, value: string) {
  await wrapper.get('[data-testid="reconcile-bank-balance-input"]').setValue(value)
}

describe('ReconcileForm', () => {
  beforeEach(() => {
    mockReconcile.mockReset().mockResolvedValue(result)
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('mostra o saldo no porquinho, labels e alvos de 44px', () => {
    const wrapper = mountForm()
    expect(plain(wrapper.get('[data-testid="reconcile-current-balance"]').text())).toBe(
      'Saldo no porquinho: R$ 1.000,00',
    )
    for (const id of ['reconcile-bank-balance', 'reconcile-reason']) {
      expect(wrapper.find(`label[for="${id}"]`).exists()).toBe(true)
      expect(wrapper.get(`#${id}`).classes()).toContain('min-h-[44px]')
    }
    expect(wrapper.get('[data-testid="reconcile-confirm-button"]').classes()).toContain(
      'min-h-[44px]',
    )
  })

  it('banco mostra mais: diferença positiva com sinal, pergunta e dois botões', async () => {
    const wrapper = mountForm()
    await typeBank(wrapper, '1.050,00')

    expect(plain(difference(wrapper).text())).toBe('+R$ 50,00')
    expect(difference(wrapper).classes()).not.toContain('text-red-600')
    expect(wrapper.get('[data-testid="reconcile-question"]').text()).toBe('Deseja ajustar o saldo?')
    expect(wrapper.get('[data-testid="reconcile-adjust-button"]').text()).toBe('Ajustar saldo')
    expect(wrapper.get('[data-testid="reconcile-later-button"]').text()).toBe('Investigar depois')
    expect(wrapper.find('[data-testid="reconcile-confirm-button"]').exists()).toBe(false)
    // Só os dois botões: sem "Cancelar" duplicado
    expect(wrapper.find('[data-testid="cancel-reconcile"]').exists()).toBe(false)
    expect(wrapper.findAll('button').map((b) => b.text())).toEqual([
      'Ajustar saldo',
      'Investigar depois',
    ])
  })

  it('banco mostra menos: diferença negativa em vermelho', async () => {
    const wrapper = mountForm()
    await typeBank(wrapper, '980')

    expect(plain(difference(wrapper).text())).toBe('-R$ 20,00')
    expect(difference(wrapper).classes()).toContain('text-red-600')
  })

  it('saldos iguais: diferença zero e "Confirmar reconciliação"', async () => {
    const wrapper = mountForm()
    await typeBank(wrapper, '1.000,00')

    expect(plain(difference(wrapper).text())).toBe('R$ 0,00')
    expect(wrapper.find('[data-testid="reconcile-question"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="reconcile-confirm-button"]').text()).toBe(
      'Confirmar reconciliação',
    )
    expect(wrapper.get('[data-testid="cancel-reconcile"]').text()).toBe('Cancelar')
  })

  it('"Cancelar" (diferença zero) emite dismiss sem chamar a API', async () => {
    const wrapper = mountForm()
    await typeBank(wrapper, '1.000,00')
    await wrapper.get('[data-testid="cancel-reconcile"]').trigger('click')

    expect(wrapper.emitted('dismiss')).toHaveLength(1)
    expect(mockReconcile).not.toHaveBeenCalled()
  })

  it('diferença atualiza enquanto digita e some com valor inválido', async () => {
    const wrapper = mountForm()
    expect(difference(wrapper).text()).toBe('—')
    await typeBank(wrapper, '1.100')
    expect(plain(difference(wrapper).text())).toBe('+R$ 100,00')
    await typeBank(wrapper, '10,123')
    expect(difference(wrapper).text()).toBe('—')
  })

  it('"Ajustar saldo" envia o saldo e o motivo sem espaços e emite reconciled', async () => {
    const wrapper = mountForm()
    await typeBank(wrapper, '1.050,00')
    await wrapper.get('[data-testid="reconcile-reason-input"]').setValue('  Tarifa estornada ')
    await wrapper.get('[data-testid="reconcile-form"]').trigger('submit')
    await flushPromises()

    expect(mockReconcile).toHaveBeenCalledTimes(1)
    expect(mockReconcile).toHaveBeenCalledWith('a1', {
      bankBalance: 1050,
      reason: 'Tarifa estornada',
    })
    expect(wrapper.emitted('reconciled')?.[0]).toEqual([result])
  })

  it('"Confirmar reconciliação" envia com diferença zero e motivo vazio vira null', async () => {
    const wrapper = mountForm()
    await typeBank(wrapper, '1000')
    await wrapper.get('[data-testid="reconcile-reason-input"]').setValue('   ')
    await wrapper.get('[data-testid="reconcile-form"]').trigger('submit')
    await flushPromises()

    expect(mockReconcile).toHaveBeenCalledWith('a1', { bankBalance: 1000, reason: null })
  })

  it('aceita saldo do banco negativo', async () => {
    const wrapper = mountForm()
    await typeBank(wrapper, '-30,00')
    expect(plain(difference(wrapper).text())).toBe('-R$ 1.030,00')
    await wrapper.get('[data-testid="reconcile-form"]').trigger('submit')
    await flushPromises()

    expect(mockReconcile).toHaveBeenCalledWith('a1', { bankBalance: -30, reason: null })
  })

  it('"Investigar depois" emite dismiss sem chamar a API', async () => {
    const wrapper = mountForm()
    await typeBank(wrapper, '1.050,00')
    await wrapper.get('[data-testid="reconcile-later-button"]').trigger('click')

    expect(wrapper.emitted('dismiss')).toHaveLength(1)
    expect(mockReconcile).not.toHaveBeenCalled()
  })

  it('saldo vazio ou com 3 casas: erro no campo, sem chamar a API', async () => {
    const wrapper = mountForm()
    await wrapper.get('[data-testid="reconcile-form"]').trigger('submit')
    expect(wrapper.get('[data-testid="reconcile-bank-balance-error"]').text()).toBe(
      'Informe o saldo no banco',
    )
    const input = wrapper.get('[data-testid="reconcile-bank-balance-input"]')
    expect(input.attributes('aria-invalid')).toBe('true')
    expect(input.attributes('aria-describedby')).toContain('reconcile-bank-balance-error')

    await typeBank(wrapper, '10,123')
    await wrapper.get('[data-testid="reconcile-form"]').trigger('submit')
    expect(wrapper.get('[data-testid="reconcile-bank-balance-error"]').text()).toContain('1.234,56')
    expect(mockReconcile).not.toHaveBeenCalled()
    expect(wrapper.emitted('attempt')).toHaveLength(2)
  })

  it('mais de 13 dígitos antes da vírgula: mesma mensagem do backend, sem chamar a API', async () => {
    const wrapper = mountForm()
    await typeBank(wrapper, '12.345.678.901.234,00')
    await wrapper.get('[data-testid="reconcile-form"]').trigger('submit')

    expect(wrapper.get('[data-testid="reconcile-bank-balance-error"]').text()).toBe(
      'Informe o saldo com até 2 casas decimais e no máximo 13 dígitos antes da vírgula',
    )
    expect(mockReconcile).not.toHaveBeenCalled()

    await typeBank(wrapper, '9.999.999.999.999,99')
    await wrapper.get('[data-testid="reconcile-form"]').trigger('submit')
    await flushPromises()
    expect(mockReconcile).toHaveBeenCalledWith('a1', {
      bankBalance: 9999999999999.99,
      reason: null,
    })
  })

  it('mostra os erros 400 do backend nos campos', async () => {
    mockReconcile.mockRejectedValueOnce({
      response: {
        status: 400,
        data: {
          detail: 'Dados inválidos',
          errors: { reason: 'Use no máximo 200 caracteres no motivo' },
        },
      },
    })
    const wrapper = mountForm()
    await typeBank(wrapper, '1000')
    await wrapper.get('[data-testid="reconcile-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.get('[data-testid="reconcile-reason-error"]').text()).toBe(
      'Use no máximo 200 caracteres no motivo',
    )
    expect(wrapper.emitted('failed')).toBeUndefined()
  })

  it('404 e outras falhas vão para o pai com detail e status', async () => {
    mockReconcile.mockRejectedValueOnce({
      response: { status: 404, data: { detail: 'Conta não encontrada' } },
    })
    const wrapper = mountForm()
    await typeBank(wrapper, '1000')
    await wrapper.get('[data-testid="reconcile-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.emitted('failed')?.[0]).toEqual(['Conta não encontrada', 404])
    expect(
      (wrapper.get('[data-testid="reconcile-bank-balance-input"]').element as HTMLInputElement)
        .value,
    ).toBe('1000')
  })

  it('trava durante o envio: um segundo envio não chama a API de novo', async () => {
    let resolve: (value: ReconciliationResult) => void = () => {}
    mockReconcile.mockReturnValueOnce(new Promise((r) => (resolve = r)))
    const wrapper = mountForm()
    await typeBank(wrapper, '1.050,00')
    await wrapper.get('[data-testid="reconcile-form"]').trigger('submit')
    await wrapper.get('[data-testid="reconcile-form"]').trigger('submit')

    expect(mockReconcile).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="reconcile-adjust-button"]').text()).toBe('Salvando...')
    expect(
      wrapper.get('[data-testid="reconcile-later-button"]').attributes('disabled'),
    ).toBeDefined()

    resolve(result)
    await flushPromises()
    expect(wrapper.emitted('reconciled')).toHaveLength(1)
  })

  it('focus() foca o campo do saldo do banco', () => {
    const wrapper = mountForm()
    wrapper.vm.focus()
    expect(document.activeElement?.id).toBe('reconcile-bank-balance')
  })
})
