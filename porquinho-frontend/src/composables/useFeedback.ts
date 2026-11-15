import { onBeforeUnmount, ref } from 'vue'

/** A mensagem de sucesso some sozinha depois de alguns segundos. */
const SUCCESS_TIMEOUT_MS = 5000

/**
 * Retro Epic 4 (G2): mensagem de sucesso que some sozinha e aviso de item que não existe mais (404) ou que mudou em
 * outra tela (409), comuns às telas de contas e cartões.
 */
export function useFeedback() {
  const successMessage = ref<string | null>(null)
  const notice = ref<string | null>(null)
  let timer: ReturnType<typeof setTimeout> | undefined

  function setSuccess(message: string | null) {
    clearTimeout(timer)
    successMessage.value = message
    if (message) timer = setTimeout(() => (successMessage.value = null), SUCCESS_TIMEOUT_MS)
  }

  /** Toda ação nova começa sem sucesso nem aviso. */
  function clearFeedback() {
    setSuccess(null)
    notice.value = null
  }

  onBeforeUnmount(() => clearTimeout(timer))
  return { successMessage, notice, setSuccess, clearFeedback }
}
