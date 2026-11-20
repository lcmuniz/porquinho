import { describe, it, expect, afterEach } from 'vitest'
import { focusFirstInvalid } from '../focusInvalid'

describe('focusFirstInvalid', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('foca o primeiro campo com aria-invalid="true" do formulário', async () => {
    document.body.innerHTML = `
      <form>
        <input id="ok" />
        <input id="a" aria-invalid="true" />
        <input id="b" aria-invalid="true" />
      </form>`
    await focusFirstInvalid(document.querySelector('form'))
    expect(document.activeElement?.id).toBe('a')
  })

  it('sem formulário ou sem campo inválido, não muda o foco', async () => {
    document.body.innerHTML = '<form><input id="ok" /></form>'
    await focusFirstInvalid(null)
    await focusFirstInvalid(document.querySelector('form'))
    expect(document.activeElement).toBe(document.body)
  })
})
