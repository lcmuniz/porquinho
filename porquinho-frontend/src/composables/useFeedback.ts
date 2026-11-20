import { onBeforeUnmount, ref } from 'vue'

/** A mensagem de sucesso some sozinha depois de alguns segundos. */
const SUCCESS_TIMEOUT_MS = 5000
/** Com uma ação (como "Desfazer"), a mensagem fica mais tempo, para dar tempo de usá-la. */
const ACTION_TIMEOUT_MS = 8000

/** Ação opcional ao lado da mensagem de sucesso, como o "Desfazer" (critique de 20/11/2026). */
export interface FeedbackAction {
  label: string
  run: () => void
}

/**
 * Retro Epic 4 (G2): mensagem de sucesso que some sozinha e aviso de item que não existe mais (404) ou que mudou em
 * outra tela (409), comuns às telas de contas e cartões.
 * Critique de 20/11/2026: a faixa verde é o único padrão de sucesso do app; pode levar uma ação ("Desfazer"), e o
 * tempo pausa enquanto o mouse ou o foco estão nela.
 */
export function useFeedback() {
  const successMessage = ref<string | null>(null)
  const successAction = ref<FeedbackAction | null>(null)
  const notice = ref<string | null>(null)
  let timer: ReturnType<typeof setTimeout> | undefined

  function setSuccess(message: string | null, action: FeedbackAction | null = null) {
    successMessage.value = message
    successAction.value = message ? action : null
    resumeSuccess()
  }

  function pauseSuccess() {
    clearTimeout(timer)
  }

  function resumeSuccess() {
    clearTimeout(timer)
    if (!successMessage.value) return
    timer = setTimeout(
      () => {
        successMessage.value = null
        successAction.value = null
      },
      successAction.value ? ACTION_TIMEOUT_MS : SUCCESS_TIMEOUT_MS,
    )
  }

  /** Toda ação nova começa sem sucesso nem aviso. */
  function clearFeedback() {
    setSuccess(null)
    notice.value = null
  }

  onBeforeUnmount(() => clearTimeout(timer))
  return { successMessage, successAction, notice, setSuccess, pauseSuccess, resumeSuccess, clearFeedback }
}
