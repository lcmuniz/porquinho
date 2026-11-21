import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import BudgetExportDialog from '../BudgetExportDialog.vue'
import { inputValue, pickMonth } from '@/test-utils/dateInput'

const mockExport = vi.fn()
vi.mock('@/services/budgetService', () => ({
  exportBudgetReport: (...a: unknown[]) => mockExport(...a),
}))

let wrapper: VueWrapper | null = null
let opener: HTMLButtonElement
/** O último `open` que o diálogo pediu (v-model). */
let isOpen = false

async function mountDialog(initialMonth = '2026-11') {
  opener.focus()
  wrapper = mount(BudgetExportDialog, {
    attachTo: document.body,
    props: {
      open: false,
      initialMonth,
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

const setMonth = (testid: string, value: string) => pickMonth($(testid)!, value)

async function submit() {
  $<HTMLFormElement>('budget-export-form')!.dispatchEvent(new Event('submit'))
  await flushPromises()
}

describe('BudgetExportDialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockExport.mockResolvedValue(new Blob(['x']))
    vi.stubGlobal('URL', { ...URL, createObjectURL: vi.fn(() => 'blob:csv'), revokeObjectURL: vi.fn() })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    opener = document.createElement('button')
    document.body.appendChild(opener)
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    opener.remove()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('abre no mês inicial, sem opções; baixa orcamento_AAAA-MM.csv, fecha e avisa', async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      expect(this.download).toBe('orcamento_2026-11.csv')
    })
    const w = await mountDialog()
    expect(inputValue($('budget-export-from'))).toBe('2026-11')
    expect(inputValue($('budget-export-to'))).toBe('2026-11')
    expect($<HTMLInputElement>('budget-export-hierarchy')!.checked).toBe(false)
    expect($<HTMLInputElement>('budget-export-averages')!.checked).toBe(false)

    await submit()

    expect(mockExport).toHaveBeenCalledExactlyOnceWith({ from: '2026-11', to: '2026-11', hierarchy: false, averages: false })
    expect(click).toHaveBeenCalledOnce()
    expect(w.emitted('exported')).toEqual([[]])
    expect(isOpen).toBe(false)
  })

  it('num intervalo com as duas opções, manda tudo e nomeia o arquivo com os dois meses', async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      expect(this.download).toBe('orcamento_2026-09_2026-11.csv')
    })
    await mountDialog()
    await setMonth('budget-export-from', '2026-09')
    for (const id of ['budget-export-hierarchy', 'budget-export-averages']) {
      const box = $<HTMLInputElement>(id)!
      box.checked = true
      box.dispatchEvent(new Event('change'))
    }
    await submit()

    expect(mockExport).toHaveBeenCalledExactlyOnceWith({ from: '2026-09', to: '2026-11', hierarchy: true, averages: true })
    expect(click).toHaveBeenCalledOnce()
  })

  // O MonthInput não deixa apagar o mês depois de escolhido; o caso "vazio" saiu daqui.
  it('intervalo invertido ou acima de 12 meses desabilita "Gerar CSV" com texto de ajuda', async () => {
    await mountDialog()
    await setMonth('budget-export-from', '2026-12')
    const button = $<HTMLButtonElement>('budget-export-submit')!
    expect(button.disabled).toBe(true)
    expect(button.getAttribute('aria-describedby')).toBe('budget-export-help')
    expect($('budget-export-help')!.textContent?.trim()).toBe('Escolha até 12 meses, com o mês final a partir do inicial.')

    await setMonth('budget-export-from', '2025-11')
    expect($<HTMLButtonElement>('budget-export-submit')!.disabled).toBe(true)

    // 12 meses é o limite
    await setMonth('budget-export-from', '2025-12')
    expect($<HTMLButtonElement>('budget-export-submit')!.disabled).toBe(false)
    expect($('budget-export-help')).toBeNull()
    await submit()
    expect(mockExport.mock.lastCall![0]).toMatchObject({ from: '2025-12', to: '2026-11' })
  })

  it('falha mantém o diálogo aberto com o erro em role="alert"', async () => {
    mockExport.mockRejectedValue(new Error('500'))
    const w = await mountDialog()
    await submit()

    const error = $('budget-export-error')!
    expect(error.getAttribute('role')).toBe('alert')
    expect(error.textContent).toContain('Não foi possível exportar. Tente de novo.')
    expect(isOpen).toBe(true)
    expect(w.emitted('exported')).toBeUndefined()
  })

  it('com o export em andamento, Cancelar fica desabilitado e Esc ou o X não fecham', async () => {
    let finish!: (value: Blob) => void
    mockExport.mockReturnValue(new Promise((resolve) => (finish = resolve)))
    const w = await mountDialog()
    await submit()

    expect($<HTMLButtonElement>('budget-export-cancel')!.disabled).toBe(true)
    expect($('budget-export-submit')!.textContent?.trim()).toBe('Gerando…')
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    document.body.querySelector<HTMLButtonElement>('[data-slot="dialog-close"]')!.click()
    await flushPromises()
    expect(isOpen).toBe(true)
    expect($('budget-export-dialog')).not.toBeNull()

    finish(new Blob(['x']))
    await flushPromises()
    expect(isOpen).toBe(false)
    expect(w.emitted('exported')).toEqual([[]])
  })

  // Regressão: um portal montado junto com o diálogo fazia o FocusScope do reka roubar o foco inicial
  it('o foco entra no mês inicial', async () => {
    await mountDialog()
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(document.activeElement?.getAttribute('data-testid')).toBe('budget-export-from')
  })

  it('o foco volta ao botão que abriu', async () => {
    await mountDialog()
    await new Promise((resolve) => setTimeout(resolve, 0))

    $<HTMLButtonElement>('budget-export-cancel')!.click()
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))
    await flushPromises()
    expect(isOpen).toBe(false)
    expect(document.activeElement).toBe(opener)
  })
})
