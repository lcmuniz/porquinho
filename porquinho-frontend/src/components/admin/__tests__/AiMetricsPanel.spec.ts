import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AiMetricsPanel from '../AiMetricsPanel.vue'
import type { AiBankDetail, AiMetrics } from '@/types/admin'

const mockGetAiMetrics = vi.fn()
const mockGetAiBankDetail = vi.fn()
const mockExportAiMetrics = vi.fn()
vi.mock('@/services/adminService', () => ({
  getAiMetrics: (...a: unknown[]) => mockGetAiMetrics(...a),
  getAiBankDetail: (...a: unknown[]) => mockGetAiBankDetail(...a),
  exportAiMetrics: (...a: unknown[]) => mockExportAiMetrics(...a),
}))

const mockDownloadBlob = vi.fn()
vi.mock('@/lib/download', () => ({ downloadBlob: (...a: unknown[]) => mockDownloadBlob(...a) }))

function metrics(overrides: Partial<AiMetrics> = {}): AiMetrics {
  return {
    from: '2026-10-19',
    to: '2026-11-17',
    target: 85,
    overall: { bank: 'TOTAL', classifications: 300, corrections: 45, accuracy: 85, status: 'GREEN' },
    monthChange: { current: 85, previous: 82.5, delta: 2.5 },
    banks: [
      { bank: 'ITAU', classifications: 100, corrections: 30, accuracy: 70, status: 'RED' },
      { bank: 'NUBANK', classifications: 100, corrections: 20, accuracy: 80, status: 'YELLOW' },
      { bank: 'UNKNOWN', classifications: 100, corrections: 5, accuracy: 95, status: 'GREEN' },
    ],
    months: ['2026-06', '2026-07', '2026-08', '2026-09', '2026-10', '2026-11'],
    trend: [
      { bank: 'ITAU', accuracy: [null, null, 72, 71, null, 70] },
      { bank: 'NUBANK', accuracy: [90, 88, 86, 84, 82, 80] },
    ],
    ...overrides,
  }
}

function detail(): AiBankDetail {
  return {
    bank: 'ITAU',
    from: '2026-10-19',
    to: '2026-11-17',
    types: [
      { key: 'PIX', classifications: 40, corrections: 20, accuracy: 50, status: 'RED' },
      { key: 'DEBIT', classifications: 60, corrections: 10, accuracy: 83.3, status: 'YELLOW' },
    ],
    confidence: [
      { key: '0-49', classifications: 30, corrections: 20, accuracy: 33.3, status: 'RED' },
      { key: '80-100', classifications: 70, corrections: 10, accuracy: 85.7, status: 'GREEN' },
    ],
    patterns: [{ pattern: 'uber trip', users: 3, usage: 7 }],
    suggestions: [
      'Revisar o prompt para Pix: precisão de 50,0 % em 40 classificações',
      "Incluir 'uber trip' como exemplo no prompt (3 usuários usam)",
    ],
  }
}

describe('AiMetricsPanel', () => {
  beforeEach(() => {
    mockGetAiMetrics.mockReset().mockResolvedValue(metrics())
    mockGetAiBankDetail.mockReset().mockResolvedValue(detail())
    mockExportAiMetrics.mockReset().mockResolvedValue(new Blob(['x']))
    mockDownloadBlob.mockReset()
  })

  it('mostra meta, precisão geral, variação, tabela com cores, barras e linhas mensais', async () => {
    const wrapper = mount(AiMetricsPanel)
    await flushPromises()

    expect(mockGetAiMetrics).toHaveBeenCalledWith({})
    expect(wrapper.get('[data-testid="ai-target"]').text()).toContain('Precisão alvo: > 85 %')
    expect(wrapper.get('[data-testid="ai-overall"]').text()).toContain('85 %')
    expect(wrapper.get('[data-testid="ai-month-change"]').text()).toContain('A precisão subiu 2,5 p.p. neste mês')
    const itau = wrapper.get('[data-testid="ai-row-ITAU"]')
    expect(itau.text()).toContain('Itaú')
    expect(itau.text()).toContain('70 %')
    expect(itau.classes()).toContain('text-red-700')
    expect(itau.get('[data-testid="ai-status"]').text()).toBe('Crítico')
    expect(wrapper.get('[data-testid="ai-row-NUBANK"]').classes()).toContain('text-yellow-900')
    expect(wrapper.get('[data-testid="ai-row-NUBANK"] [data-testid="ai-status"]').text()).toBe('Atenção')
    expect(wrapper.get('[data-testid="ai-row-UNKNOWN"]').text()).toContain('Não identificado')
    expect(wrapper.get('[data-testid="ai-row-UNKNOWN"]').classes()).toContain('text-gray-900')
    expect(wrapper.findAll('[data-testid="ai-chart-banks"] rect').map((r) => r.attributes('fill'))).toEqual([
      '#dc2626',
      '#ca8a04',
      '#16a34a',
    ])
    expect(wrapper.findAll('[data-testid="ai-chart-trend-series"]')).toHaveLength(2)
    expect(wrapper.get('[data-testid="ai-chart-trend-table"]').text()).toContain('11/26')
    expect(wrapper.get('[data-testid="ai-filter-from"]').element).toHaveProperty('value', '2026-10-19')
  })

  it('variação: queda, e "sem comparação" sem o mês anterior', async () => {
    mockGetAiMetrics.mockResolvedValueOnce(metrics({ monthChange: { current: 80, previous: 83, delta: -3 } }))
    const wrapper = mount(AiMetricsPanel)
    await flushPromises()
    expect(wrapper.get('[data-testid="ai-month-change"]').text()).toContain('A precisão caiu 3 p.p. neste mês')

    mockGetAiMetrics.mockResolvedValueOnce(metrics({ monthChange: { current: 80, previous: null, delta: null } }))
    await wrapper.get('[data-testid="ai-filters"]').trigger('submit')
    await flushPromises()
    expect(wrapper.get('[data-testid="ai-month-change"]').text()).toContain('Sem comparação')
  })

  it('sem classificações mostra "Nenhuma classificação no período"', async () => {
    mockGetAiMetrics.mockResolvedValue(metrics({ banks: [] }))
    const wrapper = mount(AiMetricsPanel)
    await flushPromises()
    expect(wrapper.get('[data-testid="ai-empty"]').text()).toBe('Nenhuma classificação no período')
  })

  it('clique no banco abre o detalhe com tipos, faixas, padrões e sugestões', async () => {
    const wrapper = mount(AiMetricsPanel)
    await flushPromises()
    await wrapper.get('[data-testid="ai-open-ITAU"]').trigger('click')
    await flushPromises()

    expect(mockGetAiBankDetail).toHaveBeenCalledWith({ bank: 'ITAU' })
    expect(wrapper.get('[data-testid="ai-open-ITAU"]').attributes('aria-expanded')).toBe('true')
    const types = wrapper.get('[data-testid="ai-detail-types"]')
    expect(types.findAll('tbody tr')[0]!.text()).toContain('Pix')
    expect(types.findAll('tbody tr')[0]!.classes()).toContain('text-red-700')
    expect(wrapper.get('[data-testid="ai-detail-confidence"]').text()).toContain('0 a 49')
    expect(wrapper.get('[data-testid="ai-detail-patterns"]').text()).toContain('uber trip · 3 usuários · 7 usos')
    expect(wrapper.get('[data-testid="ai-detail-suggestions"]').findAll('li')).toHaveLength(2)
  })

  it('os filtros mandam os parâmetros para a tabela, o detalhe e o CSV', async () => {
    const wrapper = mount(AiMetricsPanel)
    await flushPromises()
    await wrapper.get('[data-testid="ai-open-ITAU"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="ai-filter-from"]').setValue('2026-11-01')
    await wrapper.get('[data-testid="ai-filter-to"]').setValue('2026-11-10')
    await wrapper.get('[data-testid="ai-filter-bank"]').setValue('ITAU')
    mockGetAiMetrics.mockResolvedValue(metrics({ from: '2026-11-01', to: '2026-11-10' }))
    await wrapper.get('[data-testid="ai-filters"]').trigger('submit')
    await flushPromises()

    const period = { from: '2026-11-01', to: '2026-11-10', bank: 'ITAU' }
    expect(mockGetAiMetrics).toHaveBeenLastCalledWith(period)
    expect(mockGetAiBankDetail).toHaveBeenLastCalledWith(period)

    await wrapper.get('[data-testid="ai-export"]').trigger('click')
    await flushPromises()
    expect(mockExportAiMetrics).toHaveBeenCalledWith(period)
    expect(mockDownloadBlob).toHaveBeenCalledWith(expect.any(Blob), 'precisao_ia_2026-11-01_2026-11-10.csv')
  })

  it('falha no export mostra o erro junto do botão, sem o banner da carga', async () => {
    mockExportAiMetrics.mockRejectedValueOnce(new Error('rede'))
    const wrapper = mount(AiMetricsPanel)
    await flushPromises()

    await wrapper.get('[data-testid="ai-export"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="ai-export-error"]').text()).toBe('Não foi possível exportar o CSV.')
    expect(wrapper.find('[data-testid="ai-error"]').exists()).toBe(false)
  })

  it('erro de rede mostra a mensagem e tenta de novo', async () => {
    mockGetAiMetrics.mockRejectedValueOnce(new Error('rede'))
    const wrapper = mount(AiMetricsPanel)
    await flushPromises()
    expect(wrapper.get('[data-testid="ai-error"]').text()).toContain('Não foi possível carregar a precisão da IA.')

    await wrapper.get('[data-testid="ai-retry"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="ai-error"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="ai-table"]').exists()).toBe(true)
  })

  it('403 na carga, no detalhe ou no export sobe para a view', async () => {
    const wrapper = mount(AiMetricsPanel)
    await flushPromises()

    mockGetAiBankDetail.mockRejectedValueOnce({ response: { status: 403 } })
    await wrapper.get('[data-testid="ai-open-ITAU"]').trigger('click')
    await flushPromises()
    mockExportAiMetrics.mockRejectedValueOnce({ response: { status: 403 } })
    await wrapper.get('[data-testid="ai-export"]').trigger('click')
    await flushPromises()
    mockGetAiMetrics.mockRejectedValueOnce({ response: { status: 403 } })
    await wrapper.get('[data-testid="ai-filters"]').trigger('submit')
    await flushPromises()

    expect(wrapper.emitted('forbidden')).toHaveLength(3)
  })

  it('a resposta antiga do detalhe não sobrescreve a mais nova', async () => {
    let resolveOld!: (v: AiBankDetail) => void
    let resolveNew!: (v: AiBankDetail) => void
    mockGetAiBankDetail
      .mockReturnValueOnce(new Promise<AiBankDetail>((r) => (resolveOld = r)))
      .mockReturnValueOnce(new Promise<AiBankDetail>((r) => (resolveNew = r)))
    const wrapper = mount(AiMetricsPanel)
    await flushPromises()
    await wrapper.get('[data-testid="ai-open-ITAU"]').trigger('click')
    await wrapper.get('[data-testid="ai-open-NUBANK"]').trigger('click')

    resolveNew({ ...detail(), bank: 'NUBANK', suggestions: ['Nenhuma sugestão no período'] })
    await flushPromises()
    resolveOld(detail())
    await flushPromises()

    expect(wrapper.get('[data-testid="ai-detail-suggestions"]').text()).toBe('Nenhuma sugestão no período')
  })

  it('variação zero mostra o texto de estável', async () => {
    mockGetAiMetrics.mockResolvedValueOnce(metrics({ monthChange: { current: 85, previous: 85, delta: 0 } }))
    const wrapper = mount(AiMetricsPanel)
    await flushPromises()
    expect(wrapper.get('[data-testid="ai-month-change"]').text()).toContain('A precisão ficou estável neste mês')
  })

  it('filtrar por outro banco fecha o detalhe aberto', async () => {
    const wrapper = mount(AiMetricsPanel)
    await flushPromises()
    await wrapper.get('[data-testid="ai-open-ITAU"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="ai-detail"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="ai-open-ITAU"]').attributes('aria-controls')).toBe('ai-bank-detail')
    expect(wrapper.get('[data-testid="ai-open-NUBANK"]').attributes('aria-controls')).toBeUndefined()

    await wrapper.get('[data-testid="ai-filter-bank"]').setValue('NUBANK')
    await wrapper.get('[data-testid="ai-filters"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="ai-detail"]').exists()).toBe(false)
    expect(mockGetAiBankDetail).toHaveBeenCalledTimes(1)
  })
})
