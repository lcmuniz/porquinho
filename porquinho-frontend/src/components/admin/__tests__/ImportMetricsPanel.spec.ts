import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ImportMetricsPanel from '../ImportMetricsPanel.vue'
import type { ImportBankDetail, ImportMetrics } from '@/types/admin'

const mockGetImportMetrics = vi.fn()
const mockGetImportBankDetail = vi.fn()
const mockExportImportErrors = vi.fn()
vi.mock('@/services/adminService', () => ({
  getImportMetrics: (...a: unknown[]) => mockGetImportMetrics(...a),
  getImportBankDetail: (...a: unknown[]) => mockGetImportBankDetail(...a),
  exportImportErrors: (...a: unknown[]) => mockExportImportErrors(...a),
}))

const mockDownloadBlob = vi.fn()
vi.mock('@/lib/download', () => ({ downloadBlob: (...a: unknown[]) => mockDownloadBlob(...a) }))

function metrics(overrides: Partial<ImportMetrics> = {}): ImportMetrics {
  return {
    from: '2026-10-19',
    to: '2026-11-17',
    target: 5,
    banks: [
      { bank: 'NUBANK', total: 10, successes: 7, failures: 3, errorRate: 30, status: 'RED' },
      { bank: 'ITAU', total: 50, successes: 49, failures: 1, errorRate: 2, status: 'GREEN' },
      { bank: 'UNKNOWN', total: 0, successes: 0, failures: 0, errorRate: null, status: 'UNKNOWN' },
    ],
    ...overrides,
  }
}

function detail(): ImportBankDetail {
  return {
    bank: 'NUBANK',
    from: '2026-10-19',
    to: '2026-11-17',
    errorTypes: [
      { type: 'FORMAT', count: 2 },
      { type: 'INTERRUPTED', count: 1 },
    ],
    recent: [
      {
        createdAt: '2026-11-17T12:00:00Z',
        bank: 'NUBANK',
        fileType: 'CSV',
        status: 'FAILED',
        errorType: 'FORMAT',
        message: 'Formato de CSV não reconhecido.',
      },
    ],
    trend: Array.from({ length: 30 }, (_, i) => ({
      date: `2026-10-${String(19 + (i % 12)).padStart(2, '0')}`,
      total: i === 29 ? 4 : 0,
      failures: i === 29 ? 3 : 0,
      errorRate: i === 29 ? 75 : null,
    })),
  }
}

describe('ImportMetricsPanel', () => {
  beforeEach(() => {
    mockGetImportMetrics.mockReset().mockResolvedValue(metrics())
    mockGetImportBankDetail.mockReset().mockResolvedValue(detail())
    mockExportImportErrors.mockReset().mockResolvedValue(new Blob(['x']))
    mockDownloadBlob.mockReset()
  })

  it('mostra tabela por banco, meta de 5 % e banco acima da meta em vermelho', async () => {
    const wrapper = mount(ImportMetricsPanel)
    await flushPromises()

    expect(mockGetImportMetrics).toHaveBeenCalledWith({})
    expect(wrapper.get('[data-testid="imports-target"]').text()).toContain('Taxa de erro alvo: < 5 %')
    const nubank = wrapper.get('[data-testid="bank-row-NUBANK"]')
    expect(nubank.text()).toContain('Nubank')
    expect(nubank.text()).toContain('30 %')
    expect(nubank.classes()).toContain('text-red-700')
    expect(wrapper.get('[data-testid="bank-row-ITAU"]').classes()).not.toContain('text-red-700')
    expect(wrapper.get('[data-testid="bank-row-UNKNOWN"]').text()).toContain('Não identificado')
    expect(wrapper.get('[data-testid="bank-row-UNKNOWN"]').text()).toContain('sem dados')
    expect(wrapper.findAll('[data-testid="chart-banks"] rect').map((r) => r.attributes('fill'))).toEqual([
      '#dc2626',
      '#16a34a',
    ])
    expect(wrapper.get('[data-testid="filter-from"]').element).toHaveProperty('value', '2026-10-19')
  })

  it('sem importações mostra "Nenhuma importação no período"', async () => {
    mockGetImportMetrics.mockResolvedValue(metrics({ banks: [] }))
    const wrapper = mount(ImportMetricsPanel)
    await flushPromises()

    expect(wrapper.get('[data-testid="imports-empty"]').text()).toBe('Nenhuma importação no período')
  })

  it('clique no banco abre o detalhe com tipos de erro, recentes e 30 dias', async () => {
    const wrapper = mount(ImportMetricsPanel)
    await flushPromises()
    await wrapper.get('[data-testid="bank-open-NUBANK"]').trigger('click')
    await flushPromises()

    expect(mockGetImportBankDetail).toHaveBeenCalledWith({ bank: 'NUBANK', status: 'ALL' })
    expect(wrapper.get('[data-testid="bank-open-NUBANK"]').attributes('aria-controls')).toBe('bank-detail')
    expect(wrapper.get('[data-testid="bank-open-ITAU"]').attributes('aria-controls')).toBeUndefined()
    expect(wrapper.get('[data-testid="bank-open-UNKNOWN"]').attributes('aria-controls')).toBeUndefined()
    expect(wrapper.get('[data-testid="detail-error-types"]').text()).toContain('Problema de formato: 2')
    expect(wrapper.get('[data-testid="detail-error-types"]').text()).toContain('Interrompida: 1')
    expect(wrapper.get('[data-testid="detail-recent"]').text()).toContain('Formato de CSV não reconhecido.')
    expect(wrapper.get('[data-testid="chart-trend-table"]').findAll('tbody tr')).toHaveLength(30)
    // Só o último dia (75 %) passa da meta
    const fills = wrapper.findAll('[data-testid="chart-trend"] rect').map((r) => r.attributes('fill'))
    expect(fills).toEqual(['#dc2626'])
  })

  it('os filtros mandam os parâmetros e o status vale para o detalhe e o CSV', async () => {
    const wrapper = mount(ImportMetricsPanel)
    await flushPromises()
    await wrapper.get('[data-testid="bank-open-NUBANK"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="filter-from"]').setValue('2026-11-01')
    await wrapper.get('[data-testid="filter-to"]').setValue('2026-11-10')
    await wrapper.get('[data-testid="filter-bank"]').setValue('NUBANK')
    await wrapper.get('[data-testid="filter-status"]').setValue('FAILED')
    mockGetImportMetrics.mockResolvedValue(metrics({ from: '2026-11-01', to: '2026-11-10' }))
    await wrapper.get('[data-testid="import-filters"]').trigger('submit')
    await flushPromises()

    const period = { from: '2026-11-01', to: '2026-11-10', bank: 'NUBANK' }
    expect(mockGetImportMetrics).toHaveBeenLastCalledWith(period)
    expect(mockGetImportBankDetail).toHaveBeenLastCalledWith({ ...period, status: 'FAILED' })

    await wrapper.get('[data-testid="import-export"]').trigger('click')
    await flushPromises()
    expect(mockExportImportErrors).toHaveBeenCalledWith({ ...period, status: 'FAILED' })
    expect(mockDownloadBlob).toHaveBeenCalledWith(expect.any(Blob), 'erros_importacao_2026-11-01_2026-11-10.csv')
  })

  it('erro de rede mostra a mensagem e tenta de novo', async () => {
    mockGetImportMetrics.mockRejectedValueOnce(new Error('rede'))
    const wrapper = mount(ImportMetricsPanel)
    await flushPromises()

    expect(wrapper.get('[data-testid="imports-error"]').text()).toContain('Não foi possível carregar as importações.')
    await wrapper.get('[data-testid="imports-retry"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="imports-error"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="imports-table"]').exists()).toBe(true)
  })

  it('400 avisa filtro inválido e 403 sobe para a view', async () => {
    mockGetImportMetrics.mockRejectedValueOnce({ response: { status: 400 } })
    const wrapper = mount(ImportMetricsPanel)
    await flushPromises()
    expect(wrapper.get('[data-testid="imports-error"]').text()).toContain('Filtro inválido')

    mockGetImportMetrics.mockRejectedValueOnce({ response: { status: 403 } })
    await wrapper.get('[data-testid="imports-retry"]').trigger('click')
    await flushPromises()
    expect(wrapper.emitted('forbidden')).toHaveLength(1)
  })

  it('falha no detalhe mostra o erro e 403 sobe para a view', async () => {
    mockGetImportBankDetail.mockRejectedValueOnce(new Error('rede'))
    const wrapper = mount(ImportMetricsPanel)
    await flushPromises()
    await wrapper.get('[data-testid="bank-open-NUBANK"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="detail-error"]').text()).toContain('Não foi possível carregar o detalhe do banco.')
    expect(wrapper.get('[data-testid="bank-open-NUBANK"]').attributes('aria-expanded')).toBe('true')

    mockGetImportBankDetail.mockRejectedValueOnce({ response: { status: 403 } })
    await wrapper.get('[data-testid="bank-open-NUBANK"]').trigger('click')
    await flushPromises()
    expect(wrapper.emitted('forbidden')).toHaveLength(1)
  })

  it('trend: dia acima da meta em vermelho e os outros em verde', async () => {
    const d = detail()
    d.trend = d.trend.map((p, i) => (i === 0 ? { ...p, total: 10, failures: 1, errorRate: 10 } : i === 1 ? { ...p, total: 50, failures: 1, errorRate: 2 } : p))
    mockGetImportBankDetail.mockResolvedValue(d)
    const wrapper = mount(ImportMetricsPanel)
    await flushPromises()
    await wrapper.get('[data-testid="bank-open-NUBANK"]').trigger('click')
    await flushPromises()

    const fills = wrapper.findAll('[data-testid="chart-trend"] rect').map((r) => r.attributes('fill'))
    expect(fills).toEqual(['#dc2626', '#16a34a', '#dc2626'])
  })

  it('a resposta antiga do detalhe não sobrescreve a mais nova', async () => {
    let resolveOld!: (v: ImportBankDetail) => void
    let resolveNew!: (v: ImportBankDetail) => void
    mockGetImportBankDetail
      .mockReturnValueOnce(new Promise<ImportBankDetail>((r) => (resolveOld = r)))
      .mockReturnValueOnce(new Promise<ImportBankDetail>((r) => (resolveNew = r)))
    const wrapper = mount(ImportMetricsPanel)
    await flushPromises()
    await wrapper.get('[data-testid="bank-open-NUBANK"]').trigger('click')
    await wrapper.get('[data-testid="bank-open-ITAU"]').trigger('click')

    resolveNew({ ...detail(), bank: 'ITAU', errorTypes: [{ type: 'UNEXPECTED', count: 9 }] })
    await flushPromises()
    resolveOld(detail())
    await flushPromises()

    expect(wrapper.get('[data-testid="detail-error-types"]').text()).toContain('Erro inesperado: 9')
    expect(wrapper.get('[data-testid="detail-error-types"]').text()).not.toContain('Problema de formato')
  })

  it('filtrar por outro banco fecha o detalhe aberto', async () => {
    const wrapper = mount(ImportMetricsPanel)
    await flushPromises()
    await wrapper.get('[data-testid="bank-open-NUBANK"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="bank-detail"]').exists()).toBe(true)

    await wrapper.get('[data-testid="filter-bank"]').setValue('ITAU')
    await wrapper.get('[data-testid="import-filters"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="bank-detail"]').exists()).toBe(false)
    expect(mockGetImportBankDetail).toHaveBeenCalledTimes(1)
  })

  it('falha no export mostra o erro junto do botão e some no export seguinte', async () => {
    mockExportImportErrors.mockRejectedValueOnce(new Error('rede'))
    const wrapper = mount(ImportMetricsPanel)
    await flushPromises()

    await wrapper.get('[data-testid="import-export"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-testid="export-error"]').text()).toBe('Não foi possível exportar o CSV.')
    expect(wrapper.get('[data-testid="export-error"]').attributes('role')).toBe('alert')
    expect(wrapper.find('[data-testid="imports-error"]').exists()).toBe(false)

    await wrapper.get('[data-testid="import-export"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-testid="export-error"]').exists()).toBe(false)
    expect(mockDownloadBlob).toHaveBeenCalledTimes(1)
  })

  it('mesmo banco: o detalhe do "Filtrar" mais novo vence a carga anterior', async () => {
    let resolveOld!: (v: ImportBankDetail) => void
    let resolveNew!: (v: ImportBankDetail) => void
    mockGetImportBankDetail
      .mockReturnValueOnce(new Promise<ImportBankDetail>((r) => (resolveOld = r)))
      .mockReturnValueOnce(new Promise<ImportBankDetail>((r) => (resolveNew = r)))
    const wrapper = mount(ImportMetricsPanel)
    await flushPromises()
    await wrapper.get('[data-testid="bank-open-NUBANK"]').trigger('click')
    await wrapper.get('[data-testid="import-filters"]').trigger('submit')
    await flushPromises()

    resolveNew({ ...detail(), errorTypes: [{ type: 'UNEXPECTED', count: 9 }] })
    await flushPromises()
    resolveOld(detail())
    await flushPromises()

    expect(wrapper.get('[data-testid="detail-error-types"]').text()).toContain('Erro inesperado: 9')
  })
})
