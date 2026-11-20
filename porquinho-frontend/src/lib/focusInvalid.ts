import { nextTick } from 'vue'

/** Depois do re-render com os erros, leva o foco ao primeiro campo inválido do formulário. */
export async function focusFirstInvalid(form: HTMLElement | null) {
  await nextTick()
  form?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
}
