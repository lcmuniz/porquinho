import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, ref } from 'vue'
import MonthInput from '../MonthInput.vue'
import { inputValue, pickMonth } from '@/test-utils/dateInput'

function host(initial = '') {
  const value = ref(initial)
  const Host = defineComponent({
    components: { MonthInput },
    setup: () => ({ value }),
    template: '<label for="m">Mês</label><MonthInput id="m" v-model="value" testid="m" />',
  })
  return { wrapper: mount(Host, { attachTo: document.body }), value }
}

describe('MonthInput', () => {
  it('mostra mm/aaaa e, vazio, a dica mm/aaaa', () => {
    const filled = host('2026-09')
    expect(filled.wrapper.get('[data-testid="m"]').text()).toBe('09/2026')
    expect(inputValue(filled.wrapper.get('[data-testid="m"]').element)).toBe('2026-09')
    filled.wrapper.unmount()
    const empty = host('')
    expect(empty.wrapper.get('[data-testid="m"]').text()).toBe('mm/aaaa')
    empty.wrapper.unmount()
  })

  it('escolher outro ano e mês na grade emite AAAA-MM e fecha', async () => {
    const { wrapper, value } = host('2026-09')
    await pickMonth(wrapper.get('[data-testid="m"]').element as HTMLElement, '2024-01')
    expect(value.value).toBe('2024-01')
    expect(document.body.querySelector('[data-testid="m-picker"]')).toBeNull()
    wrapper.unmount()
  })

  it('o rótulo aponta para o botão (label for)', () => {
    const { wrapper } = host('2026-09')
    expect((wrapper.get('label').element as HTMLLabelElement).control).toBe(wrapper.get('[data-testid="m"]').element)
    wrapper.unmount()
  })
})
