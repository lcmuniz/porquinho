import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ImportStep from '../ImportStep.vue'

const mockCompleteImport = vi.fn()

vi.mock('@/services/onboardingService', () => ({
  completeImport: (...a: unknown[]) => mockCompleteImport(...a),
}))

const nextProgress = {
  currentStep: 7,
  stepsCompleted: [
    'tutorial_viewed',
    'accounts_added',
    'cards_added',
    'categories_suggested',
    'categories_customized',
    'import_skipped',
  ],
  completed: false,
}

const BANKS = ['Nubank', 'Banco do Brasil', 'Itaú', 'Bradesco', 'Caixa']

async function mountStep() {
  const wrapper = mount(ImportStep, { attachTo: document.body })
  await flushPromises()
  return wrapper
}

describe('ImportStep', () => {
  beforeEach(() => {
    mockCompleteImport.mockReset().mockResolvedValue(nextProgress)
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('mostra o título, o texto do extrato, o aviso de "em breve" e foca o título', async () => {
    const wrapper = await mountStep()

    const heading = wrapper.get('h1')
    expect(heading.text()).toBe('Importe suas transações')
    expect(document.activeElement).toBe(heading.element)
    expect(wrapper.text()).toContain(
      'Baixe o extrato do seu banco em formato OFX ou CSV e faça upload aqui',
    )
    expect(wrapper.get('[data-testid="import-coming-soon"]').text()).toContain(
      'A importação de arquivos chega em breve. Por enquanto, siga para o próximo passo — você poderá importar depois.',
    )
  })

  it('lista os 5 bancos em seções expansíveis nativas, fechadas, com passos e sem links externos', async () => {
    const wrapper = await mountStep()

    const details = wrapper.findAll('details')
    expect(details).toHaveLength(5)
    expect(details.map((d) => d.get('summary').text())).toEqual(BANKS)
    for (const d of details) {
      expect((d.element as HTMLDetailsElement).open).toBe(false)
      expect(d.findAll('ol li').length).toBeGreaterThan(0)
      expect(d.text()).toMatch(/OFX ou CSV/)
    }
    expect(wrapper.findAll('a')).toHaveLength(0)
    // Sem upload nesta story
    expect(wrapper.find('input[type="file"]').exists()).toBe(false)
  })

  it('abre as instruções de um banco ao clicar no resumo', async () => {
    const wrapper = await mountStep()
    const nubank = wrapper.get<HTMLDetailsElement>('[data-testid="bank-guide-nubank"]')

    await nubank.get('summary').trigger('click')

    expect(nubank.element.open).toBe(true)
  })

  it('tem só os botões Voltar e "Importar depois", com alvo de toque de 44px', async () => {
    const wrapper = await mountStep()

    const buttons = wrapper.findAll('button')
    expect(buttons.map((b) => b.text())).toEqual(['Voltar', 'Importar depois'])
    for (const b of buttons) expect(b.classes()).toContain('min-h-[44px]')
  })

  it('"Importar depois" conclui o passo e emite done com o progresso', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="import-later-button"]').trigger('click')
    await flushPromises()

    expect(mockCompleteImport).toHaveBeenCalledExactlyOnceWith()
    expect(wrapper.emitted('done')).toEqual([[nextProgress]])
  })

  it('duplo clique em "Importar depois" salva só uma vez e mantém os botões desabilitados no sucesso', async () => {
    const wrapper = await mountStep()
    const button = wrapper.get('[data-testid="import-later-button"]')

    void button.trigger('click')
    void button.trigger('click')
    await flushPromises()

    expect(mockCompleteImport).toHaveBeenCalledOnce()
    expect(wrapper.emitted('done')).toEqual([[nextProgress]])
    expect(button.attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="back-button"]').attributes('disabled')).toBeDefined()
  })

  it('falha da API emite o erro e libera os botões para tentar de novo', async () => {
    mockCompleteImport.mockRejectedValueOnce(new Error('Network Error'))
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="import-later-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('done')).toBeUndefined()
    expect(wrapper.emitted('error')?.[0]?.[0]).toContain('Não foi possível salvar seu progresso')
    const button = wrapper.get('[data-testid="import-later-button"]')
    expect(button.attributes('disabled')).toBeUndefined()

    await button.trigger('click')
    await flushPromises()
    expect(wrapper.emitted('done')).toEqual([[nextProgress]])
  })

  it('usa a mensagem do servidor quando houver (ex.: 409)', async () => {
    mockCompleteImport.mockRejectedValueOnce({
      response: {
        status: 409,
        data: { detail: 'Conclua os passos anteriores antes de importar suas transações.' },
      },
    })
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="import-later-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('error')).toEqual([
      ['Conclua os passos anteriores antes de importar suas transações.'],
    ])
  })

  it('detail vazio do servidor cai na mensagem genérica', async () => {
    mockCompleteImport.mockRejectedValueOnce({ response: { status: 500, data: { detail: '' } } })
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="import-later-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('error')?.[0]?.[0]).toContain('Não foi possível salvar seu progresso')
  })

  it('Voltar emite back sem salvar', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="back-button"]').trigger('click')

    expect(wrapper.emitted('back')).toHaveLength(1)
    expect(mockCompleteImport).not.toHaveBeenCalled()
  })
})
