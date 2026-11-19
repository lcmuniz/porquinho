import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { reactive } from 'vue'
import { mount, flushPromises, RouterLinkStub, enableAutoUnmount } from '@vue/test-utils'
import AdminView from '../AdminView.vue'
import type { SystemHealth } from '@/types/admin'

const mockGetSystemHealth = vi.fn()
const mockGetImportMetrics = vi.fn()
const mockGetAiMetrics = vi.fn()
const mockGetUserMetrics = vi.fn()
const mockGetAlerts = vi.fn()
const route = reactive<{ query: Record<string, string> }>({ query: {} })
const mockReplace = vi.fn((to: { query: Record<string, string> }) => {
  route.query = to.query
})
vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRoute: () => route,
  useRouter: () => ({ replace: mockReplace }),
}))
vi.mock('@/services/adminService', () => ({
  getSystemHealth: () => mockGetSystemHealth(),
  getImportMetrics: (...a: unknown[]) => mockGetImportMetrics(...a),
  getImportBankDetail: vi.fn(),
  exportImportErrors: vi.fn(),
  getAiMetrics: (...a: unknown[]) => mockGetAiMetrics(...a),
  getAiBankDetail: vi.fn(),
  exportAiMetrics: vi.fn(),
  getUserMetrics: () => mockGetUserMetrics(),
  exportUserMetrics: vi.fn(),
  getAlerts: () => mockGetAlerts(),
  resolveAlert: vi.fn(),
}))

function health(overrides: Partial<SystemHealth> = {}): SystemHealth {
  return {
    generatedAt: '2026-11-18T12:00:00Z',
    uptime: { last24h: 100, last30d: 99.8, target: 99.5, status: 'GREEN' },
    errors: { last24h: 3, last7d: 10, last30d: 20, errorRate24h: 0.5, status: 'GREEN' },
    responseTime: { avgMs24h: 120, status: 'GREEN' },
    endpoints: [{ method: 'GET', uri: '/api/v1/accounts', count: 10, avgMs: 85, errors: 1, status: 'GREEN' }],
    hourly: Array.from({ length: 24 }, (_, i) => ({
      hour: new Date(Date.UTC(2026, 10, 17, 13 + i)).toISOString(),
      requests: 10,
      errors: i === 23 ? 2 : 0,
      avgMs: 100,
    })),
    database: { active: 2, idle: 3, max: 20, waiting: 0, poolStatus: 'GREEN', slowQueries: 0, slowQueriesStatus: 'GREEN' },
    redis: { up: true, hitRate: 80, usedMemoryBytes: 2 * 1024 * 1024, maxMemoryBytes: null, status: 'GREEN' },
    sessions: { active: 4, status: 'GREEN' },
    ...overrides,
  }
}

function setVisibility(state: 'visible' | 'hidden') {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state })
}

function mountView() {
  return mount(AdminView, { global: { stubs: { RouterLink: RouterLinkStub } } })
}

// Views montadas em outros testes deixariam o listener de visibilitychange no document
enableAutoUnmount(afterEach)

describe('AdminView', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    route.query = {}
    mockReplace.mockClear()
    mockGetAlerts.mockReset().mockResolvedValue({ openCount: 0, emailConfigured: true, slackConfigured: false, alerts: [] })
    mockGetSystemHealth.mockReset().mockResolvedValue(health())
    mockGetImportMetrics.mockReset().mockResolvedValue({ from: '2026-10-19', to: '2026-11-17', target: 5, banks: [] })
    mockGetAiMetrics.mockReset().mockResolvedValue({
      from: '2026-10-19',
      to: '2026-11-17',
      target: 85,
      overall: { bank: 'TOTAL', classifications: 0, corrections: 0, accuracy: null, status: 'UNKNOWN' },
      monthChange: { current: null, previous: null, delta: null },
      banks: [],
      months: ['2026-06', '2026-07', '2026-08', '2026-09', '2026-10', '2026-11'],
      trend: [],
    })
    mockGetUserMetrics.mockReset().mockResolvedValue({
      generatedAt: '2026-11-18T12:00:00Z',
      total: 3,
      active24h: 1,
      active7d: 2,
      active30d: 3,
      trials: 1,
      paid: 1,
      churn: { rate: null, target: 5, status: 'UNKNOWN' },
      weeklyCheckins: null,
      onboarding: { rate: 66.7, target: 70, status: 'YELLOW' },
      conversion: null,
      adoption: [],
      months: Array.from({ length: 12 }, (_, i) => `2026-${String(i + 1).padStart(2, '0')}`),
      growth: Array(12).fill(3),
      conversionByMonth: Array(12).fill(null),
      weeks: Array.from({ length: 12 }, (_, i) => `2026-09-${String(i + 1).padStart(2, '0')}`),
      activeWeekly: Array(12).fill(2),
      activeMonthly: Array(12).fill(3),
    })
  })

  afterEach(() => {
    vi.useRealTimers()
    setVisibility('visible')
  })

  it('mostra cards com status, tabela por endpoint e gráficos', async () => {
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="card-uptime"]').text()).toContain('99,8 %')
    expect(wrapper.get('[data-testid="card-uptime"]').text()).toContain('Meta: 99,5 %')
    expect(wrapper.get('[data-testid="status-uptime"]').text()).toBe('Saudável')
    expect(wrapper.get('[data-testid="card-errors"]').text()).toContain('0,5 %')
    expect(wrapper.get('[data-testid="card-errors"]').text()).toContain('24 h: 3 · 7 dias: 10 · 30 dias: 20')
    expect(wrapper.get('[data-testid="endpoint-status"]').text()).toBe('Saudável')
    expect(wrapper.get('[data-testid="card-redis"]').text()).toContain('Hit rate: 80 %')
    expect(wrapper.get('[data-testid="card-sessions"]').text()).toContain('4')
    expect(wrapper.get('[data-testid="endpoints-table"]').text()).toContain('GET /api/v1/accounts')
    expect(wrapper.find('[data-testid="chart-time"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="chart-errors"]').exists()).toBe(true)
  })

  it('sem snapshots mostra "sem dados" e Redis fora do ar em vermelho', async () => {
    mockGetSystemHealth.mockResolvedValue(
      health({
        uptime: { last24h: null, last30d: null, target: 99.5, status: 'UNKNOWN' },
        hourly: [],
        redis: { up: false, hitRate: null, usedMemoryBytes: null, maxMemoryBytes: null, status: 'RED' },
      }),
    )
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="card-uptime"]').text()).toContain('sem dados')
    expect(wrapper.get('[data-testid="status-uptime"]').text()).toBe('Sem dados')
    expect(wrapper.get('[data-testid="card-redis"]').text()).toContain('fora do ar')
    expect(wrapper.get('[data-testid="status-redis"]').text()).toBe('Crítico')
    expect(wrapper.find('[data-testid="chart-time-empty"]').exists()).toBe(true)
  })

  it('atualiza a cada 30 segundos e limpa o intervalo ao sair', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(mockGetSystemHealth).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(30_000)
    expect(mockGetSystemHealth).toHaveBeenCalledTimes(2)

    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(60_000)
    expect(mockGetSystemHealth).toHaveBeenCalledTimes(2)
  })

  it('mostra "Acesso restrito" no 403 e para de atualizar', async () => {
    mockGetSystemHealth.mockRejectedValue({ response: { status: 403, data: { title: 'Acesso restrito' } } })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="admin-forbidden"]').text()).toContain('Acesso restrito')
    await vi.advanceTimersByTimeAsync(30_000)
    expect(mockGetSystemHealth).toHaveBeenCalledTimes(1)
  })

  it('não dispara nova carga enquanto a anterior está pendente', async () => {
    let resolve!: (value: SystemHealth) => void
    mockGetSystemHealth.mockReturnValue(new Promise<SystemHealth>((r) => (resolve = r)))
    mountView()

    await vi.advanceTimersByTimeAsync(60_000)
    expect(mockGetSystemHealth).toHaveBeenCalledTimes(1)

    resolve(health())
    await flushPromises()
    await vi.advanceTimersByTimeAsync(30_000)
    expect(mockGetSystemHealth).toHaveBeenCalledTimes(2)
  })

  it('com erro e nova tentativa pendente, não mostra "Carregando..." e desabilita o botão', async () => {
    mockGetSystemHealth.mockRejectedValue(new Error('rede'))
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.get('[data-testid="admin-retry"]').attributes('disabled')).toBeUndefined()

    mockGetSystemHealth.mockReturnValue(new Promise(() => {}))
    await wrapper.get('[data-testid="admin-retry"]').trigger('click')

    expect(wrapper.get('[data-testid="admin-retry"]').attributes('disabled')).toBeDefined()
    expect(wrapper.text()).not.toContain('Carregando...')
    expect(wrapper.find('[data-testid="admin-error"]').exists()).toBe(true)
  })

  it('dia com requisições e zero erros mostra o gráfico de erros', async () => {
    mockGetSystemHealth.mockResolvedValue(
      health({
        hourly: Array.from({ length: 24 }, (_, i) => ({
          hour: new Date(Date.UTC(2026, 10, 17, 13 + i)).toISOString(),
          requests: 10,
          errors: 0,
          avgMs: 100,
        })),
      }),
    )
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[data-testid="chart-errors"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="chart-errors-empty"]').exists()).toBe(false)
  })

  it('formata o tempo arredondando antes de escolher ms ou s', async () => {
    mockGetSystemHealth.mockResolvedValue(
      health({
        responseTime: { avgMs24h: 999.6, status: 'GREEN' },
        endpoints: [{ method: 'GET', uri: '/api/v1/x', count: 1, avgMs: 999.4, errors: 0, status: 'GREEN' }],
      }),
    )
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="card-response"]').text()).toContain('1 s')
    expect(wrapper.get('[data-testid="card-response"]').text()).not.toContain('1000 ms')
    expect(wrapper.get('[data-testid="endpoints-table"]').text()).toContain('999 ms')
  })

  it('com a aba escondida não consulta no intervalo', async () => {
    mountView()
    await flushPromises()
    setVisibility('hidden')

    await vi.advanceTimersByTimeAsync(90_000)

    expect(mockGetSystemHealth).toHaveBeenCalledTimes(1)
  })

  it('ao voltar a aba consulta na hora e remove o listener ao sair', async () => {
    const wrapper = mountView()
    await flushPromises()
    setVisibility('hidden')
    await vi.advanceTimersByTimeAsync(30_000)

    setVisibility('visible')
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(mockGetSystemHealth).toHaveBeenCalledTimes(2)

    wrapper.unmount()
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(mockGetSystemHealth).toHaveBeenCalledTimes(2)
  })

  it('erro de rede mostra a mensagem e mantém os dados anteriores', async () => {
    const wrapper = mountView()
    await flushPromises()
    mockGetSystemHealth.mockRejectedValue(new Error('rede'))

    await vi.advanceTimersByTimeAsync(30_000)

    expect(wrapper.get('[data-testid="admin-error"]').text()).toContain('Não foi possível carregar')
    expect(wrapper.find('[data-testid="card-uptime"]').exists()).toBe(true)
  })

  it('troca para a aba Importações e o poll da saúde para fora dela', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.get('[data-testid="tab-health"]').attributes('aria-current')).toBe('page')

    // A aba já aberta não recarrega
    await wrapper.get('[data-testid="tab-health"]').trigger('click')
    await flushPromises()
    expect(mockGetSystemHealth).toHaveBeenCalledTimes(1)

    await wrapper.get('[data-testid="tab-imports"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="tab-imports"]').attributes('aria-current')).toBe('page')
    expect(wrapper.get('[data-testid="tab-health"]').attributes('aria-current')).toBeUndefined()
    expect(mockGetImportMetrics).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[data-testid="card-uptime"]').exists()).toBe(false)

    await vi.advanceTimersByTimeAsync(90_000)
    setVisibility('visible')
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(mockGetSystemHealth).toHaveBeenCalledTimes(1)

    await wrapper.get('[data-testid="tab-health"]').trigger('click')
    await flushPromises()
    expect(mockGetSystemHealth).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(30_000)
    expect(mockGetSystemHealth).toHaveBeenCalledTimes(3)
  })

  it('403 na aba Importações mostra "Acesso restrito"', async () => {
    mockGetImportMetrics.mockRejectedValue({ response: { status: 403 } })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="tab-imports"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="admin-forbidden"]').text()).toContain('Acesso restrito')
  })

  it('aba IA mostra a precisão da IA sem o poll da saúde', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="tab-ai"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="tab-ai"]').text()).toBe('IA')
    expect(wrapper.get('[data-testid="tab-ai"]').attributes('aria-current')).toBe('page')
    expect(mockGetAiMetrics).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="ai-empty"]').text()).toBe('Nenhuma classificação no período')
    await vi.advanceTimersByTimeAsync(60_000)
    expect(mockGetSystemHealth).toHaveBeenCalledTimes(1)
  })

  it('403 na aba IA mostra "Acesso restrito"', async () => {
    mockGetAiMetrics.mockRejectedValue({ response: { status: 403 } })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="tab-ai"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="admin-forbidden"]').text()).toContain('Acesso restrito')
  })

  it('aba Usuários mostra as métricas de usuários, atualiza a cada 5 minutos e sem o poll da saúde', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="tab-users"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="tab-users"]').text()).toBe('Usuários')
    expect(wrapper.get('[data-testid="tab-users"]').attributes('aria-current')).toBe('page')
    expect(wrapper.get('[data-testid="users-card-total"]').text()).toContain('3')
    expect(mockGetUserMetrics).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(5 * 60_000)
    expect(mockGetUserMetrics).toHaveBeenCalledTimes(2)
    expect(mockGetSystemHealth).toHaveBeenCalledTimes(1)

    // Fora da aba o painel desmonta e para de consultar
    await wrapper.get('[data-testid="tab-ai"]').trigger('click')
    await vi.advanceTimersByTimeAsync(10 * 60_000)
    expect(mockGetUserMetrics).toHaveBeenCalledTimes(2)
  })

  it('403 na aba Usuários mostra "Acesso restrito"', async () => {
    mockGetUserMetrics.mockRejectedValue({ response: { status: 403 } })
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="tab-users"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="admin-forbidden"]').text()).toContain('Acesso restrito')
  })

  it('?tab=imports abre a aba Importações sem carregar a saúde', async () => {
    route.query = { tab: 'imports' }
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="tab-imports"]').attributes('aria-current')).toBe('page')
    expect(mockGetImportMetrics).toHaveBeenCalledTimes(1)
    expect(mockGetSystemHealth).not.toHaveBeenCalled()
  })

  it('link do alerta por banco abre a aba filtrada no banco e no dia; trocar de aba à mão descarta o filtro', async () => {
    route.query = { tab: 'imports', bank: 'ITAU', from: '2026-11-19', to: '2026-11-19' }
    const wrapper = mountView()
    await flushPromises()

    expect(mockGetImportMetrics).toHaveBeenCalledWith({ from: '2026-11-19', to: '2026-11-19', bank: 'ITAU' })
    expect(wrapper.get('[data-testid="filter-bank"]').element).toHaveProperty('value', 'ITAU')

    await wrapper.get('[data-testid="tab-ai"]').trigger('click')
    await flushPromises()
    expect(mockReplace).toHaveBeenCalledWith({ query: { tab: 'ai' } })
    expect(mockGetAiMetrics).toHaveBeenCalledWith({})
  })

  it('o link interno de um alerta da IA (query muda com a tela aberta) abre a aba IA filtrada', async () => {
    route.query = { tab: 'alerts' }
    mountView()
    await flushPromises()

    route.query = { tab: 'ai', bank: 'NUBANK', from: '2026-11-18', to: '2026-11-18' }
    await flushPromises()
    expect(mockGetAiMetrics).toHaveBeenCalledWith({ from: '2026-11-18', to: '2026-11-18', bank: 'NUBANK' })
  })

  it('?tab= desconhecida cai na saúde', async () => {
    route.query = { tab: 'xyz' }
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.get('[data-testid="tab-health"]').attributes('aria-current')).toBe('page')
  })

  it('trocar de aba atualiza a query e mudar a query troca a aba', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="tab-ai"]').trigger('click')
    expect(mockReplace).toHaveBeenCalledWith({ query: { tab: 'ai' } })

    route.query = { tab: 'users' }
    await flushPromises()
    expect(wrapper.get('[data-testid="tab-users"]').attributes('aria-current')).toBe('page')
  })

  it('com alerta aberto mostra o aviso no topo e a contagem na aba; o aviso leva à aba Alertas', async () => {
    mockGetAlerts.mockResolvedValue({ openCount: 2, emailConfigured: true, slackConfigured: false, alerts: [] })
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.get('[data-testid="admin-open-alerts"]').text()).toContain('Há 2 alertas abertos.')
    expect(wrapper.get('[data-testid="tab-alerts"]').text()).toBe('Alertas (2)')

    await wrapper.get('[data-testid="admin-open-alerts-go"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="tab-alerts"]').attributes('aria-current')).toBe('page')
    expect(wrapper.find('[data-testid="admin-open-alerts"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="alerts-empty"]').exists()).toBe(true)
  })

  it('fora da aba de saúde a contagem do aviso atualiza a cada 30 segundos, sem a saúde', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="tab-imports"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="admin-open-alerts"]').exists()).toBe(false)
    const calls = mockGetAlerts.mock.calls.length

    mockGetAlerts.mockResolvedValue({ openCount: 1, emailConfigured: true, slackConfigured: false, alerts: [] })
    await vi.advanceTimersByTimeAsync(30_000)
    expect(mockGetAlerts).toHaveBeenCalledTimes(calls + 1)
    expect(mockGetSystemHealth).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="admin-open-alerts"]').text()).toContain('Há 1 alerta aberto.')
  })

  it('sem alerta aberto não mostra o aviso', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.find('[data-testid="admin-open-alerts"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="tab-alerts"]').text()).toBe('Alertas')
  })

  it('403 nos alertas mostra "Acesso restrito"', async () => {
    mockGetAlerts.mockRejectedValue({ response: { status: 403 } })
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.get('[data-testid="admin-forbidden"]').text()).toContain('Acesso restrito')
  })
})
