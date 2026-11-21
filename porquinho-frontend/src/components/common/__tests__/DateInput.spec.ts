import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import DateInput from '../DateInput.vue'
import { typeDate } from '@/test-utils/dateInput'

function host(initial = '') {
  const value = ref(initial)
  const Host = defineComponent({
    components: { DateInput },
    setup: () => ({ value }),
    template: '<DateInput v-model="value" testid="d" />',
  })
  return { wrapper: mount(Host, { attachTo: document.body }), value }
}

describe('DateInput', () => {
  it('mostra dd/mm/aaaa em pt-BR, sem depender do idioma do navegador', () => {
    const { wrapper } = host('2026-11-28')
    const text = wrapper.get('[data-testid="d"]').text().replace(/\s/g, '')
    expect(text.startsWith('28/11/2026')).toBe(true)
    wrapper.unmount()
  })

  it('vazio mostra dd/mm/aaaa como dica', () => {
    const { wrapper } = host('')
    expect(wrapper.get('[data-testid="d"]').text().replace(/\s/g, '')).toContain('dd/mm/aaaa')
    wrapper.unmount()
  })

  it('digitar dia, mês e ano emite AAAA-MM-DD', async () => {
    const { wrapper, value } = host('')
    await typeDate(wrapper.get('[data-testid="d"]').element as HTMLElement, '2026-11-05')
    expect(value.value).toBe('2026-11-05')
    wrapper.unmount()
  })

  // Regressão: o diálogo só fecha com Esc que não teve preventDefault
  it('Esc num segmento não fica com defaultPrevented', () => {
    const { wrapper } = host('2026-11-28')
    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true })
    wrapper.get('[data-testid="d-day"]').element.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(false)
    wrapper.unmount()
  })
})
