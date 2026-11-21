import { flushPromises } from '@vue/test-utils'

/** Digita AAAA-MM-DD no DateInput (dia, mês e ano, como o usuário); '' apaga. `field` é o elemento com o data-testid. */
export async function typeDate(field: HTMLElement, iso: string) {
  const segment = (part: string) => field.querySelector<HTMLElement>(`[data-reka-date-field-segment="${part}"]`)!
  const press = (el: HTMLElement, key: string) => el.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }))
  for (const part of ['day', 'month', 'year']) {
    const el = segment(part)
    el.focus()
    for (let i = 0; i < 5; i++) press(el, 'Backspace')
  }
  await flushPromises()
  if (!iso) return
  const [year, month, day] = iso.split('-')
  for (const [part, digits] of [['day', day!], ['month', month!], ['year', year!]] as const) {
    const el = segment(part)
    el.focus()
    for (const d of digits) press(el, d)
  }
  await flushPromises()
}

/** Valor (AAAA-MM-DD ou AAAA-MM) de um DateInput/MonthInput, pelo elemento com o data-testid. */
export const inputValue = (field: Element | null | undefined) => field?.getAttribute('data-value') ?? ''

/** Escolhe AAAA-MM no MonthInput como o usuário: abre a grade, vai até o ano e clica no mês. `trigger` tem o data-testid. */
export async function pickMonth(trigger: HTMLElement, value: string) {
  const [year, month] = value.split('-').map(Number) as [number, number]
  trigger.click()
  await flushPromises()
  const testid = trigger.getAttribute('data-testid')
  const picker = document.body.querySelector<HTMLElement>(`[data-testid="${testid}-picker"]`)!
  for (let i = 0; i < 50; i++) {
    const cell = picker.querySelector<HTMLElement>(`[data-value^="${year}-${String(month).padStart(2, '0')}-01"]`)
    if (cell) {
      cell.click()
      await flushPromises()
      return
    }
    const shown = Number(picker.querySelector('[data-value]')!.getAttribute('data-value')!.slice(0, 4))
    picker.querySelector<HTMLElement>(`[aria-label="${shown > year ? 'Ano anterior' : 'Próximo ano'}"]`)!.click()
    await flushPromises()
  }
  throw new Error(`mês ${value} não encontrado`)
}
