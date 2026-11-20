import { ref, type Ref } from 'vue'

/**
 * Lista carregada do servidor em que só a resposta da carga mais recente é aplicada (removidas, transferências).
 * `quiet` recarrega sem trocar a lista pelo "Carregando…". `onError` recebe a falha da carga mais recente (quem precisa
 * distinguir 403 ou 400).
 */
export function useSequencedList<T>(fetch: () => Promise<T[]>, onError?: (err: unknown) => void) {
  const items = ref<T[]>([]) as Ref<T[]>
  const loading = ref(false)
  const loadFailed = ref(false)
  let request = 0

  async function load(quiet = false) {
    const current = ++request
    if (!quiet) loading.value = true
    loadFailed.value = false
    try {
      const result = await fetch()
      if (current === request) items.value = result
    } catch (err) {
      if (current !== request) return
      loadFailed.value = true
      onError?.(err)
    } finally {
      if (current === request) loading.value = false
    }
  }

  /** Uma carga em andamento leu o servidor antes de uma mudança local: a resposta dela é descartada. */
  function discard() {
    request++
  }

  return { items, loading, loadFailed, load, discard }
}
