import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import GoalHistoryView from '../GoalHistoryView.vue'
import GoalHistoryChart from '@/components/goals/GoalHistoryChart.vue'
import type { GoalHistory } from '@/types/goal'

const mockGetHistory = vi.fn()

vi.mock('@/services/goalService', () => ({
  getGoalHistory: (...a: unknown[]) => mockGetHistory(...a),
}))

vi.mock('vue-router', async (importOriginal) => ({
  ...(await importOriginal<typeof import('vue-router')>()),
  useRoute: () => ({ params: { id: 'g1' } }),
}))

const plain = (s: string) => s.replace(/\s/g, ' ')

// Matriz: criada em jan, R$ 100 em jan e R$ 200 em mar (hoje)
function viagem(overrides: Partial<GoalHistory> = {}): GoalHistory {
  return {
    goalName: 'Viagem à Europa',
    targetAmount: 1000,
    createdAt: '2026-01-10T12:00:00Z',
    completedAt: null,
    entries: [
      { date: '2026-01-10', contributionAmount: null, amount: 0 },
      { date: '2026-01-15', contributionAmount: 100, amount: 100 },
      { date: '2026-02-01', contributionAmount: null, amount: 100 },
      { date: '2026-03-01', contributionAmount: null, amount: 100 },
      { date: '2026-03-05', contributionAmount: 200, amount: 300 },
    ],
    ...overrides,
  }
}

async function mountView() {
  const wrapper = mount(GoalHistoryView, {
    attachTo: document.body,
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
  await flushPromises()
  return wrapper
}

const cells = (wrapper: Awaited<ReturnType<typeof mountView>>) =>
  wrapper.findAll('[data-testid="history-row"]').map((r) => r.findAll('td').map((td) => plain(td.text())))

describe('GoalHistoryView', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-03-15T15:00:00Z'))
    mockGetHistory.mockReset().mockResolvedValue(viagem())
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
    document.body.innerHTML = ''
  })

  it('mostra gráfico, tabela com % do alvo e as estatísticas da meta ativa', async () => {
    const wrapper = await mountView()
    expect(mockGetHistory).toHaveBeenCalledWith('g1')
    expect(plain(wrapper.get('[data-testid="history-heading"]').text())).toBe('Histórico da meta — Viagem à Europa')
    expect(wrapper.findComponent(GoalHistoryChart).props('entries')).toHaveLength(5)
    expect(cells(wrapper)).toEqual([
      ['10/01/2026', '—', 'R$ 0,00', '0%'],
      ['15/01/2026', 'R$ 100,00', 'R$ 100,00', '10%'],
      ['01/02/2026', '—', 'R$ 100,00', '10%'],
      ['01/03/2026', '—', 'R$ 100,00', '10%'],
      ['05/03/2026', 'R$ 200,00', 'R$ 300,00', '30%'],
    ])
    expect(plain(wrapper.get('[data-testid="stat-avg-contribution"]').text())).toBe('R$ 100,00')
    expect(wrapper.get('[data-testid="stat-consistency"]').text()).toBe('2 de 3 meses com aporte')
    expect(wrapper.find('[data-testid="stat-time-to-complete"]').exists()).toBe(false)
  })

  it('meta nova: uma entrada com "—", R$ 0,00 e 0%', async () => {
    mockGetHistory.mockResolvedValue(
      viagem({
        createdAt: '2026-03-15T12:00:00Z',
        entries: [{ date: '2026-03-15', contributionAmount: null, amount: 0 }],
      }),
    )
    const wrapper = await mountView()
    expect(cells(wrapper)).toEqual([['15/03/2026', '—', 'R$ 0,00', '0%']])
    expect(wrapper.get('[data-testid="stat-consistency"]').text()).toBe('0 de 1 mês com aporte')
  })

  it('meta concluída: período até o mês de conclusão e "Tempo até concluir"', async () => {
    mockGetHistory.mockResolvedValue(viagem({ completedAt: '2026-03-05T12:00:00Z' }))
    const wrapper = await mountView()
    expect(wrapper.get('[data-testid="stat-time-to-complete"]').text()).toBe('1 mês')

    mockGetHistory.mockResolvedValue(
      viagem({ createdAt: '2026-01-10T12:00:00Z', completedAt: '2026-02-05T12:00:00Z' }),
    )
    wrapper.unmount()
    const again = await mountView()
    expect(again.get('[data-testid="stat-time-to-complete"]').text()).toBe('menos de 1 mês')
    // Período jan–fev: o aporte de março fica fora da consistência
    expect(again.get('[data-testid="stat-consistency"]').text()).toBe('1 de 2 meses com aporte')
  })

  it('404 mostra "Meta não encontrada" sem tabela', async () => {
    mockGetHistory.mockRejectedValue({ response: { status: 404 } })
    const wrapper = await mountView()
    expect(wrapper.get('[data-testid="history-not-found"]').text()).toContain('Meta não encontrada')
    expect(wrapper.find('[data-testid="history-table"]').exists()).toBe(false)
  })

  it('erro de rede mostra "Tentar de novo", que recarrega', async () => {
    mockGetHistory.mockRejectedValueOnce(new Error('network'))
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="history-load-error"]').exists()).toBe(true)
    await wrapper.get('[data-testid="history-retry"]').trigger('click')
    await flushPromises()
    expect(mockGetHistory).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="history-table"]').exists()).toBe(true)
  })

  it('exporta CSV com BOM, `;`, CRLF e uma linha por entrada', async () => {
    let blob: Blob | null = null
    URL.createObjectURL = vi.fn((b: Blob) => {
      blob = b
      return 'blob:x'
    })
    URL.revokeObjectURL = vi.fn()
    let download = ''
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      download = this.download
    })
    const wrapper = await mountView()
    await wrapper.get('[data-testid="export-csv"]').trigger('click')

    expect(download).toBe('historico-meta-viagem-a-europa.csv')
    const buffer = await new Promise<ArrayBuffer>((resolve) => {
      const reader = new FileReader()
      reader.onload = () => resolve(reader.result as ArrayBuffer)
      reader.readAsArrayBuffer(blob!)
    })
    const bytes = new Uint8Array(buffer)
    expect(Array.from(bytes.slice(0, 3))).toEqual([0xef, 0xbb, 0xbf])
    expect(new TextDecoder().decode(bytes.slice(3)).split('\r\n')).toEqual([
      'Data;Aporte;Total;% do alvo',
      '10/01/2026;;0,00;0%',
      '15/01/2026;100,00;100,00;10%',
      '01/02/2026;;100,00;10%',
      '01/03/2026;;100,00;10%',
      '05/03/2026;200,00;300,00;30%',
      '',
    ])
  })
})
