import { describe, it, expect, vi, afterEach } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'
import { usePolling } from '../usePolling'
import { useExportDialog } from '../useExportDialog'
import { useSequencedList } from '../useSequencedList'

function setVisibility(state: 'visible' | 'hidden') {
  Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => state })
}

function withSetup<T>(setup: () => T) {
  let result!: T
  const wrapper = mount(
    defineComponent({
      setup() {
        result = setup()
        return () => h('div')
      },
    }),
  )
  return { result, wrapper }
}

describe('usePolling', () => {
  afterEach(() => {
    vi.useRealTimers()
    setVisibility('visible')
  })

  it('roda no intervalo só com a aba visível, recarrega ao voltar e para ao desmontar', async () => {
    vi.useFakeTimers()
    const task = vi.fn()
    const { wrapper } = withSetup(() => usePolling(task, 1000))

    await vi.advanceTimersByTimeAsync(1000)
    expect(task).toHaveBeenCalledTimes(1)

    setVisibility('hidden')
    await vi.advanceTimersByTimeAsync(3000)
    expect(task).toHaveBeenCalledTimes(1)

    setVisibility('visible')
    document.dispatchEvent(new Event('visibilitychange'))
    expect(task).toHaveBeenCalledTimes(2)

    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(3000)
    document.dispatchEvent(new Event('visibilitychange'))
    expect(task).toHaveBeenCalledTimes(2)
  })

  it('depois de stop não roda mais; sem refreshOnVisible não recarrega ao voltar', async () => {
    vi.useFakeTimers()
    const stopped = vi.fn()
    const quiet = vi.fn()
    const { result, wrapper } = withSetup(() => usePolling(stopped, 1000))
    const other = withSetup(() => usePolling(quiet, 1000, { refreshOnVisible: false }))

    result.stop()
    await vi.advanceTimersByTimeAsync(2000)
    document.dispatchEvent(new Event('visibilitychange'))
    expect(stopped).not.toHaveBeenCalled()
    expect(quiet).toHaveBeenCalledTimes(2)

    wrapper.unmount()
    other.wrapper.unmount()
  })
})

describe('useExportDialog sem diálogo', () => {
  it('run passa o erro a onError e devolve false; no sucesso devolve true', async () => {
    const { result, wrapper } = withSetup(() => useExportDialog())
    const onError = vi.fn()
    const err = new Error('rede')

    expect(await result.run(() => Promise.reject(err), onError)).toBe(false)
    expect(onError).toHaveBeenCalledWith(err)
    expect(result.failed.value).toBe(true)

    expect(await result.run(() => Promise.resolve(), onError)).toBe(true)
    expect(onError).toHaveBeenCalledTimes(1)
    expect(result.exporting.value).toBe(false)
    wrapper.unmount()
  })
})

describe('useSequencedList', () => {
  it('só a falha da carga mais recente chega a onError', async () => {
    const rejects: ((err: unknown) => void)[] = []
    const onError = vi.fn()
    const list = useSequencedList<number>(() => new Promise((_, reject) => rejects.push(reject)), onError)

    const first = list.load()
    const second = list.load()
    rejects[0]!('velha')
    rejects[1]!('nova')
    await Promise.all([first, second])
    await flushPromises()

    expect(onError).toHaveBeenCalledTimes(1)
    expect(onError).toHaveBeenCalledWith('nova')
    expect(list.loadFailed.value).toBe(true)
    expect(list.loading.value).toBe(false)
  })
})
