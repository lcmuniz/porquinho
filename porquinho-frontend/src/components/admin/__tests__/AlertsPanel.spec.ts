import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub, enableAutoUnmount } from '@vue/test-utils'
import AlertsPanel from '../AlertsPanel.vue'
import type { SystemAlert, SystemAlerts } from '@/types/admin'

const mockGetAlerts = vi.fn()
const mockResolveAlert = vi.fn()
vi.mock('@/services/adminService', () => ({
  getAlerts: () => mockGetAlerts(),
  resolveAlert: (...a: unknown[]) => mockResolveAlert(...a),
}))

function alert(overrides: Partial<SystemAlert> = {}): SystemAlert {
  return {
    id: 1,
    key: 'REDIS_DOWN',
    type: 'REDIS_DOWN',
    metric: 'Redis',
    bank: null,
    bankLabel: null,
    value: 0,
    threshold: 1,
    valueLabel: 'fora do ar',
    thresholdLabel: 'no ar',
    action: 'Verifique o container do Redis e a conexão do backend com ele.',
    tab: 'health',
    link: 'https://app.example.com/admin?tab=health',
    triggeredAt: '2026-11-19T13:20:00Z',
    normalizedAt: null,
    resolvedAt: null,
    ...overrides,
  }
}

function alerts(overrides: Partial<SystemAlerts> = {}): SystemAlerts {
  return {
    openCount: 1,
    emailConfigured: true,
    slackConfigured: false,
    alerts: [
      alert(),
      alert({
        id: 2,
        key: 'IMPORT_ERROR_RATE:ITAU',
        type: 'IMPORT_ERROR_RATE',
        metric: 'Taxa de erro de importação (24 h)',
        bank: 'ITAU',
        bankLabel: 'Itaú',
        valueLabel: '12,0 %',
        thresholdLabel: 'acima de 10 %',
        tab: 'imports',
        normalizedAt: '2026-11-19T14:00:00Z',
        resolvedAt: '2026-11-19T13:40:00Z',
      }),
    ],
    ...overrides,
  }
}

function mountPanel() {
  return mount(AlertsPanel, { global: { stubs: { RouterLink: RouterLinkStub } } })
}

enableAutoUnmount(afterEach)

describe('AlertsPanel', () => {
  beforeEach(() => {
    mockGetAlerts.mockReset().mockResolvedValue(alerts())
    mockResolveAlert.mockReset()
  })

  it('mostra métrica, banco, valor, limite, hora em SP, ação, link da aba e estado', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    const open = wrapper.get('[data-testid="alert-1"]').text()
    expect(open).toContain('Redis')
    expect(open).toContain('fora do ar')
    expect(open).toContain('no ar')
    expect(open).toContain('19/11/2026, 10:20')
    expect(open).toContain('Verifique o container do Redis')
    expect(wrapper.get('[data-testid="alert-state-1"]').text()).toBe('Aberto')

    const resolved = wrapper.get('[data-testid="alert-2"]').text()
    expect(resolved).toContain('Itaú')
    expect(resolved).toContain('12,0 %')
    expect(resolved).toContain('Normalizou em 19/11/2026, 11:00')
    expect(wrapper.get('[data-testid="alert-state-2"]').text()).toBe('Resolvido em 19/11/2026, 10:40')
    expect(wrapper.find('[data-testid="alert-resolve-2"]').exists()).toBe(false)
    expect(wrapper.findAllComponents(RouterLinkStub)[1]!.props('to')).toEqual({ name: 'admin', query: { tab: 'imports' } })
    expect(wrapper.emitted('count')).toEqual([[1]])
  })

  it('"Marcar como resolvido" mostra o carregando, vira resolvido e a contagem cai', async () => {
    let finish: (a: SystemAlert) => void = () => {}
    mockResolveAlert.mockReturnValue(new Promise((r) => (finish = r)))
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.get('[data-testid="alert-resolve-1"]').trigger('click')
    expect(mockResolveAlert).toHaveBeenCalledWith(1)
    expect(wrapper.get('[data-testid="alert-resolve-1"]').text()).toBe('Salvando...')
    expect(wrapper.get('[data-testid="alert-resolve-1"]').attributes('disabled')).toBeDefined()

    finish(alert({ resolvedAt: '2026-11-19T13:30:00Z' }))
    await flushPromises()
    expect(wrapper.get('[data-testid="alert-state-1"]').text()).toBe('Resolvido em 19/11/2026, 10:30')
    expect(wrapper.emitted('count')).toEqual([[1], [0]])
  })

  it('erro ao resolver mostra a mensagem e mantém aberto', async () => {
    mockResolveAlert.mockRejectedValue({ response: { status: 500 } })
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.get('[data-testid="alert-resolve-1"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="alerts-resolve-error"]').text()).toBe(
      'Não foi possível marcar o alerta como resolvido.',
    )
    expect(wrapper.get('[data-testid="alert-state-1"]').text()).toBe('Aberto')
  })

  it('403 ao carregar ou ao resolver emite forbidden', async () => {
    mockGetAlerts.mockRejectedValueOnce({ response: { status: 403 } })
    const first = mountPanel()
    await flushPromises()
    expect(first.emitted('forbidden')).toHaveLength(1)

    mockResolveAlert.mockRejectedValue({ response: { status: 403 } })
    const second = mountPanel()
    await flushPromises()
    await second.get('[data-testid="alert-resolve-1"]').trigger('click')
    await flushPromises()
    expect(second.emitted('forbidden')).toHaveLength(1)
  })

  it('erro ao carregar mostra "Tentar novamente"', async () => {
    mockGetAlerts.mockRejectedValueOnce(new Error('network'))
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.get('[data-testid="alerts-error"]').text()).toContain('Não foi possível carregar os alertas.')

    await wrapper.get('[data-testid="alerts-retry"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="alerts-table"]').exists()).toBe(true)
  })

  it('sem alertas mostra o vazio e avisa quando o e-mail não está configurado', async () => {
    mockGetAlerts.mockResolvedValue(alerts({ openCount: 0, alerts: [], emailConfigured: false }))
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.get('[data-testid="alerts-empty"]').text()).toBe('Nenhum alerta até agora.')
    expect(wrapper.get('[data-testid="alerts-email-off"]').text()).toContain('E-mail não configurado')
  })
})
