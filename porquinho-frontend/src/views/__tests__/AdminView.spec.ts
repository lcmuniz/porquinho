import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import AdminView from '../AdminView.vue'
import type { SystemHealth } from '@/types/admin'

const mockGetSystemHealth = vi.fn()
vi.mock('@/services/adminService', () => ({
  getSystemHealth: () => mockGetSystemHealth(),
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

function mountView() {
  return mount(AdminView, { global: { stubs: { RouterLink: RouterLinkStub } } })
}

describe('AdminView', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    mockGetSystemHealth.mockReset().mockResolvedValue(health())
  })

  afterEach(() => {
    vi.useRealTimers()
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

  it('erro de rede mostra a mensagem e mantém os dados anteriores', async () => {
    const wrapper = mountView()
    await flushPromises()
    mockGetSystemHealth.mockRejectedValue(new Error('rede'))

    await vi.advanceTimersByTimeAsync(30_000)

    expect(wrapper.get('[data-testid="admin-error"]').text()).toContain('Não foi possível carregar')
    expect(wrapper.find('[data-testid="card-uptime"]').exists()).toBe(true)
  })
})
