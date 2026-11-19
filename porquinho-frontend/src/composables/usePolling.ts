import { onBeforeUnmount, onMounted } from 'vue'

/**
 * Retro do Epic 10 (A5/D2): roda `task` a cada `intervalMs` enquanto o componente está montado. Aba do navegador
 * escondida não consulta (cada chamada renova last_activity e manteria a sessão viva para sempre); ao voltar a ficar
 * visível consulta na hora, a não ser com `refreshOnVisible: false`. `stop` encerra de vez (ex.: 403).
 */
export function usePolling(task: () => void, intervalMs: number, options: { refreshOnVisible?: boolean } = {}) {
  let timer: ReturnType<typeof setInterval> | undefined

  function stop() {
    if (timer !== undefined) clearInterval(timer)
    timer = undefined
  }

  function onVisibilityChange() {
    if (document.visibilityState === 'visible' && timer !== undefined) task()
  }

  onMounted(() => {
    timer = setInterval(() => {
      if (document.visibilityState !== 'hidden') task()
    }, intervalMs)
    if (options.refreshOnVisible !== false) document.addEventListener('visibilitychange', onVisibilityChange)
  })

  onBeforeUnmount(() => {
    stop()
    document.removeEventListener('visibilitychange', onVisibilityChange)
  })

  return { stop }
}
