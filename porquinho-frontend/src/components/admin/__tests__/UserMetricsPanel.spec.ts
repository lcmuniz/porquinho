import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, enableAutoUnmount } from '@vue/test-utils'
import UserMetricsPanel from '../UserMetricsPanel.vue'
import type { UserMetrics } from '@/types/admin'

const mockGetUserMetrics = vi.fn()
const mockExportUserMetrics = vi.fn()
vi.mock('@/services/adminService', () => ({
  getUserMetrics: () => mockGetUserMetrics(),
  exportUserMetrics: () => mockExportUserMetrics(),
}))

const mockDownloadBlob = vi.fn()
vi.mock('@/lib/download', () => ({ downloadBlob: (...a: unknown[]) => mockDownloadBlob(...a) }))
vi.mock('@/lib/date', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/lib/date')>()),
  todaySaoPaulo: () => '2026-11-18',
}))

const months = Array.from({ length: 12 }, (_, i) => `2026-${String(i + 1).padStart(2, '0')}`)

function metrics(overrides: Partial<UserMetrics> = {}): UserMetrics {
  return {
    generatedAt: '2026-11-18T12:00:00Z',
    total: 1200,
    active24h: 80,
    active7d: 300,
    active30d: 700,
    trials: 150,
    paid: 400,
    churn: { rate: 5, target: 5, status: 'GREEN' },
    weeklyCheckins: 0.75,
    onboarding: { rate: 69.9, target: 70, status: 'YELLOW' },
    conversion: 42.5,
    adoption: [
      { feature: 'ACCOUNTS', users: 1100, rate: 91.7 },
      { feature: 'RECONCILIATIONS', users: 0, rate: 0 },
    ],
    months,
    growth: months.map((_, i) => 100 * (i + 1)),
    conversionByMonth: months.map((_, i) => (i === 0 ? null : 40)),
    weeks: Array.from({ length: 12 }, (_, i) => `2026-09-${String(i + 1).padStart(2, '0')}`),
    activeWeekly: Array(12).fill(300),
    activeMonthly: Array(12).fill(700),
    ...overrides,
  }
}

function setVisibility(state: 'visible' | 'hidden') {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state })
}

enableAutoUnmount(afterEach)

describe('UserMetricsPanel', () => {
  beforeEach(() => {
    mockGetUserMetrics.mockReset().mockResolvedValue(metrics())
    mockExportUserMetrics.mockReset().mockResolvedValue(new Blob(['x']))
    mockDownloadBlob.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
    setVisibility('visible')
  })

  it('mostra os sete cards, as metas com cor, o engajamento, os gráficos e a adoção', async () => {
    const wrapper = mount(UserMetricsPanel)
    await flushPromises()

    for (const key of ['total', 'active24h', 'active7d', 'active30d', 'trials', 'paid', 'churn']) {
      expect(wrapper.find(`[data-testid="users-card-${key}"]`).exists()).toBe(true)
    }
    expect(wrapper.get('[data-testid="users-card-total"]').text()).toContain('1.200')
    expect(wrapper.get('[data-testid="users-card-churn"]').text()).toContain('5,0 %')
    expect(wrapper.get('[data-testid="users-card-churn"]').text()).toContain('Meta: < 5 %')
    expect(wrapper.get('[data-testid="users-status-churn"]').text()).toBe('Dentro da meta')
    expect(wrapper.get('[data-testid="users-card-churn"]').classes()).toContain('border-green-300')
    expect(wrapper.get('[data-testid="users-card-onboarding"]').text()).toContain('69,9 %')
    expect(wrapper.get('[data-testid="users-card-onboarding"]').text()).toContain('Meta: 70 % ou mais')
    expect(wrapper.get('[data-testid="users-status-onboarding"]').text()).toBe('Atenção')
    expect(wrapper.get('[data-testid="users-card-checkins"]').text()).toContain('0,75')
    expect(wrapper.get('[data-testid="users-card-conversion"]').text()).toContain('42,5 %')
    expect(wrapper.find('[data-testid="users-status-total"]').exists()).toBe(false)

    expect(wrapper.findAll('[data-testid="users-chart-growth-series"] circle')).toHaveLength(12)
    expect(wrapper.findAll('[data-testid="users-chart-active-series"]')).toHaveLength(2)
    expect(wrapper.findAll('[data-testid="users-chart-conversion-series"] circle')).toHaveLength(11)
    expect(wrapper.get('[data-testid="users-chart-growth"]').text()).toContain('1.200')
    expect(wrapper.get('[data-testid="users-chart-active-table"]').text()).toContain('01/09')
    expect(wrapper.get('[data-testid="users-active-note"]').text()).toContain('segunda-feira em que a semana começa')
    expect(wrapper.get('[data-testid="users-chart-active"]').attributes('aria-label')).toContain('medidos no fim de cada')

    expect(wrapper.get('[data-testid="users-adoption-ACCOUNTS"]').text()).toContain('Contas')
    expect(wrapper.get('[data-testid="users-adoption-ACCOUNTS"]').text()).toContain('91,7 %')
    expect(wrapper.get('[data-testid="users-adoption-RECONCILIATIONS"]').text()).toContain('0,0 %')
    expect(wrapper.get('[data-testid="users-updated"]').text()).toContain('Atualiza a cada 5 minutos')
  })

  it('churn acima da meta fica vermelho e sem denominador mostra "sem dados"', async () => {
    mockGetUserMetrics.mockResolvedValueOnce(
      metrics({
        churn: { rate: 5.1, target: 5, status: 'RED' },
        onboarding: { rate: null, target: 70, status: 'UNKNOWN' },
        weeklyCheckins: null,
        conversion: null,
        adoption: [{ feature: 'GOALS', users: 0, rate: null }],
        conversionByMonth: Array(12).fill(null),
      }),
    )
    const wrapper = mount(UserMetricsPanel)
    await flushPromises()

    expect(wrapper.get('[data-testid="users-status-churn"]').text()).toBe('Crítico')
    expect(wrapper.get('[data-testid="users-card-onboarding"]').text()).toContain('sem dados')
    expect(wrapper.get('[data-testid="users-status-onboarding"]').text()).toBe('Sem dados')
    expect(wrapper.get('[data-testid="users-card-checkins"]').text()).toContain('sem dados')
    expect(wrapper.get('[data-testid="users-card-conversion"]').text()).toContain('sem dados')
    expect(wrapper.get('[data-testid="users-adoption-GOALS"]').text()).toContain('sem dados')
    expect(wrapper.get('[data-testid="users-chart-conversion-empty"]').text()).toBe(
      'Sem dados de conversão nos últimos 12 meses',
    )
  })

  it('exporta o CSV com o nome do dia e mostra o erro junto do botão', async () => {
    const wrapper = mount(UserMetricsPanel)
    await flushPromises()

    await wrapper.get('[data-testid="users-export"]').trigger('click')
    await flushPromises()
    expect(mockDownloadBlob).toHaveBeenCalledWith(expect.any(Blob), 'usuarios_2026-11-18.csv')

    mockExportUserMetrics.mockRejectedValueOnce(new Error('rede'))
    await wrapper.get('[data-testid="users-export"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="users-export-error"]').text()).toBe('Não foi possível exportar o CSV.')
    expect(wrapper.find('[data-testid="users-error"]').exists()).toBe(false)
  })

  it('erro de rede mostra a mensagem e tenta de novo', async () => {
    mockGetUserMetrics.mockRejectedValueOnce(new Error('rede'))
    const wrapper = mount(UserMetricsPanel)
    await flushPromises()
    expect(wrapper.get('[data-testid="users-error"]').text()).toContain('Não foi possível carregar as métricas de usuários.')

    await wrapper.get('[data-testid="users-retry"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="users-error"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="users-adoption"]').exists()).toBe(true)
  })

  it('403 na carga ou no export sobe para a view e para o poll', async () => {
    vi.useFakeTimers()
    const wrapper = mount(UserMetricsPanel)
    await flushPromises()
    mockExportUserMetrics.mockRejectedValueOnce({ response: { status: 403 } })
    await wrapper.get('[data-testid="users-export"]').trigger('click')
    await flushPromises()
    expect(wrapper.emitted('forbidden')).toHaveLength(1)

    await vi.advanceTimersByTimeAsync(10 * 60_000)
    expect(mockGetUserMetrics).toHaveBeenCalledTimes(1)

    mockGetUserMetrics.mockRejectedValueOnce({ response: { status: 403 } })
    const other = mount(UserMetricsPanel)
    await flushPromises()
    expect(other.emitted('forbidden')).toHaveLength(1)
  })

  it('busca de novo a cada 5 minutos e não consulta com a aba do navegador oculta', async () => {
    vi.useFakeTimers()
    mount(UserMetricsPanel)
    await flushPromises()
    expect(mockGetUserMetrics).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(5 * 60_000 - 1)
    expect(mockGetUserMetrics).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(mockGetUserMetrics).toHaveBeenCalledTimes(2)

    setVisibility('hidden')
    await vi.advanceTimersByTimeAsync(10 * 60_000)
    expect(mockGetUserMetrics).toHaveBeenCalledTimes(2)

    setVisibility('visible')
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(mockGetUserMetrics).toHaveBeenCalledTimes(3)
  })

  it('ao desmontar para o poll', async () => {
    vi.useFakeTimers()
    const wrapper = mount(UserMetricsPanel)
    await flushPromises()
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(10 * 60_000)
    document.dispatchEvent(new Event('visibilitychange'))
    expect(mockGetUserMetrics).toHaveBeenCalledTimes(1)
  })
})
