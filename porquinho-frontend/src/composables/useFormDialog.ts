import { nextTick, ref } from 'vue'

interface Focusable {
  focus(): void
}

/**
 * Retro Epic 4 (G2): a máquina de diálogo das telas de contas e cartões. Não fecha no meio do envio (Esc ou clique
 * fora), abre com o foco no formulário e, ao fechar, devolve o foco a `returnFocus` (o botão que abriu, se ainda
 * existir, ou um alvo que sempre existe).
 */
export function useFormDialog(options: {
  form?: () => Focusable | null | undefined
  returnFocus: () => HTMLElement | null | undefined
}) {
  const open = ref(false)
  const submitting = ref(false)
  const error = ref<string | null>(null)

  function onOpenChange(value: boolean) {
    if (!value && submitting.value) return
    open.value = value
  }

  function onOpenAutoFocus(event: Event) {
    event.preventDefault()
    const form = options.form?.()
    if (form) form.focus()
    else void nextTick(() => options.form?.()?.focus())
  }

  function onCloseAutoFocus(event: Event) {
    event.preventDefault()
    void nextTick(() => options.returnFocus()?.focus())
  }

  return { open, submitting, error, onOpenChange, onOpenAutoFocus, onCloseAutoFocus }
}
