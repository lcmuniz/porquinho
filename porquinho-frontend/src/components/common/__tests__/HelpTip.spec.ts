import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import HelpTip from '../HelpTip.vue'

let wrapper: VueWrapper | null = null

function mountTip() {
  wrapper = mount(HelpTip, {
    attachTo: document.body,
    props: { term: 'comprometido', testid: 'committed' },
    slots: { default: 'Parcelas que ainda vão cair no mês.' },
  })
  return wrapper
}

const content = () => document.body.querySelector<HTMLElement>('[data-testid="help-committed-content"]')

describe('HelpTip', () => {
  beforeEach(() => {
    // a seta do popover mede o próprio tamanho; o jsdom não tem ResizeObserver
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        unobserve() {}
        disconnect() {}
      },
    )
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
  })

  it('o ícone é um botão com rótulo para leitor de tela e a explicação começa fechada', () => {
    const button = mountTip().get('[data-testid="help-committed"]')
    expect(button.element.tagName).toBe('BUTTON')
    expect(button.attributes('type')).toBe('button')
    expect(button.attributes('aria-label')).toBe('O que é comprometido?')
    expect(button.attributes('aria-expanded')).toBe('false')
    expect(content()).toBeNull()
  })

  it('abre a explicação num diálogo rotulado pelo termo e fecha com Esc devolvendo o foco', async () => {
    const w = mountTip()
    const button = w.get('[data-testid="help-committed"]')
    ;(button.element as HTMLElement).focus()
    await button.trigger('click')
    await flushPromises()

    expect(button.attributes('aria-expanded')).toBe('true')
    expect(content()!.textContent).toContain('Parcelas que ainda vão cair no mês.')
    expect(content()!.getAttribute('role')).toBe('dialog')
    expect(content()!.getAttribute('aria-label')).toBe('comprometido')

    content()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await flushPromises()
    expect(content()).toBeNull()
    expect(document.activeElement).toBe(button.element)
  })
})
