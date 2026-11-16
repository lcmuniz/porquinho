import { ref, watch } from 'vue'

/**
 * Retro Epic 9 (A3): a máquina dos diálogos de export em CSV (transações e orçamento). Ao abrir, guarda quem tinha o
 * foco, limpa o erro e recomeça o formulário com `reset`; ao fechar, devolve o foco a quem abriu (sem DialogTrigger, o
 * Reka não sabe para onde voltar). Com o download em andamento o diálogo não fecha.
 */
export function useExportDialog(options: {
  open: () => boolean
  setOpen: (open: boolean) => void
  reset: () => void
}) {
  const exporting = ref(false)
  const failed = ref(false)
  let opener: HTMLElement | null = null

  watch(
    options.open,
    (open) => {
      if (!open) return
      opener = document.activeElement as HTMLElement | null
      failed.value = false
      options.reset()
    },
    { immediate: true },
  )

  function returnFocus(event: Event) {
    event.preventDefault()
    opener?.focus()
  }

  function onOpenChange(open: boolean) {
    if (!exporting.value) options.setOpen(open)
  }

  /** Roda o export; no sucesso fecha o diálogo e devolve true, na falha mostra o erro e devolve false. */
  async function run(task: () => Promise<void>): Promise<boolean> {
    if (exporting.value) return false
    exporting.value = true
    failed.value = false
    try {
      await task()
      options.setOpen(false)
      return true
    } catch {
      failed.value = true
      return false
    } finally {
      exporting.value = false
    }
  }

  return { exporting, failed, returnFocus, onOpenChange, run }
}
