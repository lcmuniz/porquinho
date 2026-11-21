import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import TransactionExportDialog, { type ExportInitial } from '../TransactionExportDialog.vue'
import { typeDate } from '@/test-utils/dateInput'

const mockExport = vi.fn()
vi.mock('@/services/dashboardService', () => ({
  exportDetails: (...a: unknown[]) => mockExport(...a),
}))

const INITIAL: ExportInitial = { preset: 'this-month', from: '', to: '', categoryIds: [], targets: [] }
const NO_FLAGS = { includeFutureInstallments: false, includeDeleted: false }

let wrapper: VueWrapper | null = null
let opener: HTMLButtonElement
/** O último `open` que o diálogo pediu (v-model). */
let isOpen = false

async function mountDialog(initial: ExportInitial = INITIAL) {
  opener.focus()
  wrapper = mount(TransactionExportDialog, {
    attachTo: document.body,
    props: {
      open: false,
      accounts: [{ id: 'a1', accountName: 'Nubank' }],
      cards: [{ id: 'k1', cardName: 'Roxinho' }],
      categories: [
        { id: 'g1', categoryName: 'Alimentação', parentCategoryId: null },
        { id: 'c1', categoryName: 'Mercado', parentCategoryId: 'g1' },
      ],
      initial,
      'onUpdate:open': (open: boolean) => {
        isOpen = open
        return wrapper!.setProps({ open })
      },
    } as never,
  })
  await wrapper.setProps({ open: true })
  isOpen = true
  await flushPromises()
  return wrapper
}

const $ = <T extends Element = HTMLElement>(testid: string) =>
  document.body.querySelector<T>(`[data-testid="${testid}"]`)

async function choose(preset: string) {
  const select = $<HTMLSelectElement>('export-period')!
  select.value = preset
  select.dispatchEvent(new Event('change'))
  await flushPromises()
}

async function submit() {
  $<HTMLFormElement>('export-form')!.dispatchEvent(new Event('submit'))
  await flushPromises()
}

const lastPeriod = () => {
  const { from, to } = mockExport.mock.lastCall![0]
  return [from, to]
}

describe('TransactionExportDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockExport.mockResolvedValue({ blob: new Blob(['x']), count: 3 })
    vi.stubGlobal('URL', { ...URL, createObjectURL: vi.fn(() => 'blob:csv'), revokeObjectURL: vi.fn() })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-11-09T15:00:00Z'))
    opener = document.createElement('button')
    document.body.appendChild(opener)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    opener.remove()
    vi.useRealTimers()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('os atalhos viram datas e o CSV sai sem busca, em date,desc', async () => {
    await mountDialog()
    const expected: Record<string, [string, string]> = {
      'this-month': ['2026-11-01', '2026-11-30'],
      'last-3-months': ['2026-09-01', '2026-11-30'],
      'last-year': ['2025-12-01', '2026-11-30'],
      all: ['0001-01-01', '9999-12-31'],
    }
    for (const [preset, period] of Object.entries(expected)) {
      await wrapper!.setProps({ open: true })
      await flushPromises()
      await choose(preset)
      await submit()
      expect(lastPeriod()).toEqual(period)
    }
    expect(mockExport.mock.lastCall).toEqual([
      { from: '0001-01-01', to: '9999-12-31', categoryIds: [], targets: [], q: '', sort: 'date', dir: 'desc' },
      NO_FLAGS,
    ])
  })

  it('manda contas, categorias e as duas opções; baixa transacoes_AAAA-MM-DD.csv, fecha e avisa a contagem', async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      expect(this.download).toBe('transacoes_2026-11-09.csv')
    })
    const w = await mountDialog({ ...INITIAL, categoryIds: ['c1'], targets: ['card:k1'] })
    for (const id of ['export-future', 'export-deleted']) {
      const box = $<HTMLInputElement>(id)!
      box.checked = true
      box.dispatchEvent(new Event('change'))
    }
    await submit()

    expect(mockExport).toHaveBeenCalledExactlyOnceWith(
      { from: '2026-11-01', to: '2026-11-30', categoryIds: ['c1'], targets: ['card:k1'], q: '', sort: 'date', dir: 'desc' },
      { includeFutureInstallments: true, includeDeleted: true },
    )
    expect(click).toHaveBeenCalledOnce()
    expect(w.emitted('exported')).toEqual([[3]])
    expect(isOpen).toBe(false)
  })

  it('personalizado inválido desabilita "Gerar CSV" com texto de ajuda', async () => {
    await mountDialog({ ...INITIAL, preset: 'custom', from: '2026-11-30', to: '2026-11-01' })
    const button = $<HTMLButtonElement>('export-submit')!
    expect(button.disabled).toBe(true)
    expect(button.getAttribute('aria-describedby')).toBe('export-period-help')
    expect($('export-period-help')!.textContent).toContain('datas válidas')

    await typeDate($('export-to')!, '')
    expect($<HTMLButtonElement>('export-submit')!.disabled).toBe(true)

    await typeDate($('export-to')!, '2026-12-15')
    expect($<HTMLButtonElement>('export-submit')!.disabled).toBe(false)
    await submit()
    expect(lastPeriod()).toEqual(['2026-11-30', '2026-12-15'])
  })

  it('falha mantém o diálogo aberto com o erro em role="alert"', async () => {
    mockExport.mockRejectedValue(new Error('500'))
    const w = await mountDialog()
    await submit()

    const error = $('export-error')!
    expect(error.getAttribute('role')).toBe('alert')
    expect(error.textContent).toContain('Não foi possível exportar. Tente de novo.')
    expect(isOpen).toBe(true)
    expect(w.emitted('exported')).toBeUndefined()
  })

  it('com o export em andamento, Cancelar fica desabilitado e Esc ou o X não fecham', async () => {
    let finish!: (value: { blob: Blob; count: number }) => void
    mockExport.mockReturnValue(new Promise((resolve) => (finish = resolve)))
    const w = await mountDialog()
    await submit()

    expect($<HTMLButtonElement>('export-cancel')!.disabled).toBe(true)
    expect($('export-submit')!.textContent?.trim()).toBe('Gerando…')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    document.body.querySelector<HTMLButtonElement>('[data-slot="dialog-close"]')!.click()
    await flushPromises()
    expect(isOpen).toBe(true)
    expect($('export-dialog')).not.toBeNull()

    finish({ blob: new Blob(['x']), count: 2 })
    await flushPromises()
    expect(isOpen).toBe(false)
    expect(w.emitted('exported')).toEqual([[2]])
  })

  it('o foco entra no período e volta ao botão que abriu', async () => {
    await mountDialog()
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(document.activeElement?.getAttribute('data-testid')).toBe('export-period')

    $<HTMLButtonElement>('export-cancel')!.click()
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await flushPromises()
    expect(isOpen).toBe(false)
    expect(document.activeElement).toBe(opener)
  })
})
