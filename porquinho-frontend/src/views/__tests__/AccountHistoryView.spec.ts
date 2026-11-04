import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import AccountHistoryView from '../AccountHistoryView.vue'
import BalanceChart from '@/components/accounts/BalanceChart.vue'
import type { BalanceHistory } from '@/types/account'

const mockGetHistory = vi.fn()

vi.mock('@/services/accountService', () => ({
  getBalanceHistory: (...a: unknown[]) => mockGetHistory(...a),
}))

// Rota reativa: o teste troca o id para conferir a recarga
vi.mock('vue-router', async (importOriginal) => {
  const { reactive } = await import('vue')
  const route = reactive({ params: { id: 'a1' } })
  return {
    ...(await importOriginal<typeof import('vue-router')>()),
    useRoute: () => route,
    __route: route,
  }
})

async function testRoute() {
  const mod = (await import('vue-router')) as unknown as { __route: { params: { id: string } } }
  return mod.__route
}

const plain = (s: string) => s.replace(/\s/g, ' ')

// Critério de aceite: Nubank 1.000,00 → edição 1.200,00 → transferência de 200,00 → reconciliação 1.050,00
function nubank(overrides: Partial<BalanceHistory> = {}): BalanceHistory {
  return {
    account: { id: 'a1', accountName: 'Nubank', currentBalance: 1050 },
    periodStart: '2026-08-04T15:00:00Z',
    points: [
      { date: '2026-10-01T15:00:00Z', balance: 1000, event: 'initial' },
      { date: '2026-10-10T15:00:00Z', balance: 1200, event: 'edit' },
      { date: '2026-10-20T15:00:00Z', balance: 1000, event: 'transfer' },
      { date: '2026-11-03T15:00:00Z', balance: 1050, event: 'reconciliation' },
    ],
    ...overrides,
  }
}

async function mountView() {
  const wrapper = mount(AccountHistoryView, {
    attachTo: document.body,
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
  await flushPromises()
  return wrapper
}

describe('AccountHistoryView', () => {
  beforeEach(async () => {
    mockGetHistory.mockReset().mockResolvedValue(nubank())
    ;(await testRoute()).params.id = 'a1'
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('carrega 3 meses por padrão e mostra título, gráfico com 4 pontos e tabela com 4 linhas', async () => {
    const wrapper = await mountView()
    expect(mockGetHistory).toHaveBeenCalledWith('a1', '3m')
    expect(plain(wrapper.get('[data-testid="history-heading"]').text())).toBe(
      'Histórico de saldo — Nubank',
    )
    expect(
      wrapper.findComponent(BalanceChart).findAll('[data-testid^="chart-point-"]'),
    ).toHaveLength(4)
    expect(wrapper.findAll('[data-testid="history-row"]')).toHaveLength(4)
  })

  it('a tabela vem da mais recente para a mais antiga, com "Reconciliação R$ 1.050,00" no topo', async () => {
    const wrapper = await mountView()
    const headers = wrapper.findAll('th').map((th) => th.text())
    expect(headers).toEqual(['Data', 'Evento', 'Saldo'])
    const rows = wrapper.findAll('[data-testid="history-row"]').map((r) => plain(r.text()))
    expect(rows[0]).toContain('03/11/2026')
    expect(rows[0]).toContain('Reconciliação')
    expect(rows[0]).toContain('R$ 1.050,00')
    expect(rows[3]).toContain('Saldo inicial')
  })

  it('ao focar um ponto mostra data, saldo e tipo', async () => {
    const wrapper = await mountView()
    await wrapper.get('[data-testid="chart-point-3"]').trigger('focus')
    const tooltip = plain(wrapper.get('[data-testid="chart-tooltip"]').text())
    expect(tooltip).toContain('03/11/2026')
    expect(tooltip).toContain('R$ 1.050,00')
    expect(tooltip).toContain('Reconciliação')
  })

  it('tem o link "Voltar para contas"', async () => {
    const wrapper = await mountView()
    const link = wrapper.getComponent(RouterLinkStub)
    expect(link.props('to')).toBe('/contas')
    expect(link.text()).toBe('Voltar para contas')
  })

  it('o seletor marca o período com aria-pressed e recarrega ao trocar', async () => {
    const wrapper = await mountView()
    const labels = wrapper
      .get('[data-testid="range-selector"]')
      .findAll('button')
      .map((b) => b.text())
    expect(labels).toEqual(['30 dias', '3 meses', '6 meses', '1 ano', 'Tudo'])
    expect(wrapper.get('[data-testid="range-3m"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-testid="range-30d"]').attributes('aria-pressed')).toBe('false')

    mockGetHistory.mockResolvedValue(
      nubank({
        points: [{ date: '2026-09-01T15:00:00Z', balance: 1050, event: 'reconciliation' }],
      }),
    )
    await wrapper.get('[data-testid="range-30d"]').trigger('click')
    await flushPromises()

    expect(mockGetHistory).toHaveBeenLastCalledWith('a1', '30d')
    expect(wrapper.get('[data-testid="range-30d"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-testid="range-3m"]').attributes('aria-pressed')).toBe('false')
    // 30 dias sem eventos recentes: o último saldo conhecido é o único ponto
    expect(wrapper.findAll('[data-testid^="chart-point-"]')).toHaveLength(1)
    expect(wrapper.findAll('[data-testid="history-row"]')).toHaveLength(1)
  })

  it('só a resposta do último período escolhido é aplicada', async () => {
    const wrapper = await mountView()
    let resolveSlow: (h: BalanceHistory) => void = () => {}
    mockGetHistory.mockReturnValueOnce(new Promise<BalanceHistory>((r) => (resolveSlow = r)))
    mockGetHistory.mockResolvedValueOnce(nubank({ points: [] }))

    await wrapper.get('[data-testid="range-6m"]').trigger('click')
    await wrapper.get('[data-testid="range-all"]').trigger('click')
    await flushPromises()
    resolveSlow(nubank())
    await flushPromises()

    expect(wrapper.find('[data-testid="history-empty"]').exists()).toBe(true)
  })

  it('mostra o carregando enquanto busca', async () => {
    mockGetHistory.mockReturnValue(new Promise(() => {}))
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="history-skeleton"]').attributes('aria-busy')).toBe('true')
  })

  it('erro mostra "Tentar de novo", que busca de novo', async () => {
    mockGetHistory.mockRejectedValueOnce({ response: { status: 500 } })
    const wrapper = await mountView()
    expect(wrapper.get('[data-testid="history-load-error"]').attributes('role')).toBe('alert')
    await wrapper.get('[data-testid="history-retry"]').trigger('click')
    await flushPromises()
    expect(mockGetHistory).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="history-load-error"]').exists()).toBe(false)
    expect(wrapper.findAll('[data-testid="history-row"]')).toHaveLength(4)
  })

  it('404 mostra "Esta conta não existe mais" com o link de volta e sem seletor', async () => {
    mockGetHistory.mockRejectedValue({
      response: { status: 404, data: { detail: 'Conta não encontrada' } },
    })
    const wrapper = await mountView()
    const notFound = wrapper.get('[data-testid="history-not-found"]')
    expect(notFound.text()).toContain('Esta conta não existe mais')
    expect(notFound.findComponent(RouterLinkStub).props('to')).toBe('/contas')
    expect(wrapper.find('[data-testid="range-selector"]').exists()).toBe(false)
  })

  it('período sem pontos mostra "Nenhuma movimentação neste período"', async () => {
    mockGetHistory.mockResolvedValue(nubank({ points: [] }))
    const wrapper = await mountView()
    expect(wrapper.get('[data-testid="history-empty"]').text()).toBe(
      'Nenhuma movimentação neste período',
    )
    expect(wrapper.find('[data-testid="balance-chart"]').exists()).toBe(false)
  })

  it('saldo negativo fica em vermelho na tabela', async () => {
    mockGetHistory.mockResolvedValue(
      nubank({
        points: [
          { date: '2026-10-01T15:00:00Z', balance: 100, event: 'initial' },
          { date: '2026-10-02T15:00:00Z', balance: -50, event: 'edit' },
        ],
      }),
    )
    const wrapper = await mountView()
    const balances = wrapper.findAll('[data-testid="history-row-balance"]')
    expect(plain(balances[0]!.text())).toBe('-R$ 50,00')
    expect(balances[0]!.classes()).toContain('text-red-600')
    expect(balances[1]!.classes()).not.toContain('text-red-600')
  })

  it('400 (id malformado) também mostra "Esta conta não existe mais"', async () => {
    mockGetHistory.mockRejectedValue({ response: { status: 400 } })
    const wrapper = await mountView()
    expect(wrapper.get('[data-testid="history-not-found"]').text()).toContain(
      'Esta conta não existe mais',
    )
    expect(wrapper.find('[data-testid="history-load-error"]').exists()).toBe(false)
  })

  it('trocar o id da rota recarrega e limpa o 404 anterior', async () => {
    mockGetHistory.mockRejectedValueOnce({ response: { status: 404 } })
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="history-not-found"]').exists()).toBe(true)

    mockGetHistory.mockResolvedValue(
      nubank({ account: { id: 'a2', accountName: 'Inter', currentBalance: 10 } }),
    )
    ;(await testRoute()).params.id = 'a2'
    await flushPromises()

    expect(mockGetHistory).toHaveBeenLastCalledWith('a2', '3m')
    expect(wrapper.find('[data-testid="history-not-found"]').exists()).toBe(false)
    expect(plain(wrapper.get('[data-testid="history-heading"]').text())).toBe(
      'Histórico de saldo — Inter',
    )
  })

  it('passa o início do período ao gráfico', async () => {
    const wrapper = await mountView()
    expect(wrapper.findComponent(BalanceChart).props('periodStart')).toBe('2026-08-04T15:00:00Z')
  })
})
