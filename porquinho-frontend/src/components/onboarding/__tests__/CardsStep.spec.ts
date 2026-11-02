import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import CardsStep from '../CardsStep.vue'
import type { CreditCard } from '@/types/creditCard'

const mockList = vi.fn()
const mockCreate = vi.fn()
const mockDelete = vi.fn()
const mockCompleteCards = vi.fn()

vi.mock('@/services/cardService', () => ({
  listCards: (...a: unknown[]) => mockList(...a),
  createCard: (...a: unknown[]) => mockCreate(...a),
  deleteCard: (...a: unknown[]) => mockDelete(...a),
}))

vi.mock('@/services/onboardingService', () => ({
  completeCards: (...a: unknown[]) => mockCompleteCards(...a),
}))

function card(overrides: Partial<CreditCard> = {}): CreditCard {
  return {
    id: 'c1',
    cardName: 'Nubank Roxinho',
    bankName: 'Nubank',
    cardLimit: 5000,
    dueDay: 10,
    createdAt: '2026-11-02T12:00:00Z',
    ...overrides,
  }
}

const nextProgress = {
  currentStep: 4,
  stepsCompleted: ['tutorial_viewed', 'accounts_added', 'cards_added'],
  completed: false,
}

async function mountStep() {
  const wrapper = mount(CardsStep, { attachTo: document.body })
  await flushPromises()
  return wrapper
}

type Wrapper = Awaited<ReturnType<typeof mountStep>>

async function fill(wrapper: Wrapper, name: string, limit: string, dueDay: string, bank = '') {
  await wrapper.get('[data-testid="card-name-input"]').setValue(name)
  await wrapper.get('[data-testid="card-bank-name-input"]').setValue(bank)
  await wrapper.get('[data-testid="card-limit-input"]').setValue(limit)
  await wrapper.get('[data-testid="card-due-day-input"]').setValue(dueDay)
}

async function submit(wrapper: Wrapper) {
  await wrapper.get('[data-testid="card-form"]').trigger('submit')
  await flushPromises()
}

const plain = (s: string) => s.replace(/\s/g, ' ')

describe('CardsStep', () => {
  beforeEach(() => {
    mockList.mockReset().mockResolvedValue([])
    mockCreate.mockReset().mockImplementation(async (input) => ({
      ...card(),
      id: `new-${input.cardName}`,
      cardName: input.cardName,
      bankName: input.bankName,
      cardLimit: input.cardLimit,
      dueDay: input.dueDay,
    }))
    mockDelete.mockReset().mockResolvedValue(undefined)
    mockCompleteCards.mockReset().mockResolvedValue(nextProgress)
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('mostra o título, a lista vazia, Próximo habilitado (cartões são opcionais) e as sugestões de banco', async () => {
    const wrapper = await mountStep()

    expect(wrapper.text()).toContain('Adicione seus cartões de crédito')
    expect(wrapper.find('[data-testid="cards-empty"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="next-button"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('[data-testid="skip-button"]').text()).toContain('Pular por agora')
    const options = wrapper.findAll('#card-bank-suggestions option').map((o) => o.attributes('value'))
    expect(options).toContain('Nubank')
    const dueDay = wrapper.get('[data-testid="card-due-day-input"]')
    expect(dueDay.attributes('type')).toBe('number')
    expect(dueDay.attributes('min')).toBe('1')
    expect(dueDay.attributes('max')).toBe('31')
  })

  it('adiciona "Nubank Roxinho" com R$ 5.000,00 e vencimento dia 10 e mostra na lista', async () => {
    const wrapper = await mountStep()
    await fill(wrapper, '  Nubank Roxinho ', '5.000,00', '10', 'Nubank')
    await submit(wrapper)

    expect(mockCreate).toHaveBeenCalledWith({
      cardName: 'Nubank Roxinho',
      bankName: 'Nubank',
      cardLimit: 5000,
      dueDay: 10,
    })
    const item = wrapper.get('[data-testid="card-item-new-Nubank Roxinho"]')
    expect(item.text()).toContain('Nubank Roxinho')
    expect(plain(item.get('[data-testid="card-details"]').text())).toBe('Nubank · vence dia 10')
    expect(plain(item.get('[data-testid="card-limit"]').text())).toBe('R$ 5.000,00')
    expect(item.text()).toContain('Remover')
    // formulário limpo
    expect((wrapper.get('[data-testid="card-name-input"]').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('[data-testid="card-due-day-input"]').element as HTMLInputElement).value).toBe(
      '',
    )
  })

  it('envia banco vazio como null e aceita limite zero', async () => {
    const wrapper = await mountStep()
    await fill(wrapper, 'Reserva', '0', '31')
    await submit(wrapper)

    expect(mockCreate).toHaveBeenCalledWith({
      cardName: 'Reserva',
      bankName: null,
      cardLimit: 0,
      dueDay: 31,
    })
    expect(plain(wrapper.get('[data-testid="card-details"]').text())).toBe('vence dia 31')
  })

  it.each(['0', '32', '10.5', ''])('dia "%s" mostra "Informe um dia entre 1 e 31"', async (day) => {
    const wrapper = await mountStep()
    await fill(wrapper, 'Cartão', '100,00', day)
    await submit(wrapper)

    expect(wrapper.get('[data-testid="card-due-day-error"]').text()).toBe('Informe um dia entre 1 e 31')
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('limite negativo, inválido ou vazio mostra a mensagem no campo', async () => {
    const wrapper = await mountStep()
    await fill(wrapper, 'Cartão', '-10,00', '10')
    await submit(wrapper)
    expect(wrapper.get('[data-testid="card-limit-error"]').text()).toBe('O limite não pode ser negativo')

    await fill(wrapper, 'Cartão', '12,345', '10')
    await submit(wrapper)
    expect(wrapper.get('[data-testid="card-limit-error"]').text()).toContain('5.000,00')

    await fill(wrapper, 'Cartão', '', '10')
    await submit(wrapper)
    expect(wrapper.get('[data-testid="card-limit-error"]').text()).toContain('Informe o limite')

    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('nome vazio mostra "Informe o nome do cartão" e não chama a API', async () => {
    const wrapper = await mountStep()
    await fill(wrapper, '   ', '10,00', '5')
    await submit(wrapper)

    expect(wrapper.get('[data-testid="card-name-error"]').text()).toBe('Informe o nome do cartão')
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('erro 400 do backend aparece no campo', async () => {
    mockCreate.mockRejectedValue({
      response: {
        status: 400,
        data: {
          detail: 'Informe um dia entre 1 e 31',
          errors: { dueDay: 'Informe um dia entre 1 e 31' },
        },
      },
    })
    const wrapper = await mountStep()
    await fill(wrapper, 'Cartão', '1', '10')
    await submit(wrapper)

    expect(wrapper.get('[data-testid="card-due-day-error"]').text()).toBe('Informe um dia entre 1 e 31')
    expect(wrapper.emitted('error')).toBeUndefined()
  })

  it('400 sem campo conhecido mostra o detail da API', async () => {
    mockCreate.mockRejectedValue({
      response: {
        status: 400,
        data: { detail: 'Dados inválidos. Confira os campos e tente novamente.' },
      },
    })
    const wrapper = await mountStep()
    await fill(wrapper, 'Cartão', '1', '10')
    await submit(wrapper)

    expect(wrapper.emitted('error')?.[0]).toEqual([
      'Dados inválidos. Confira os campos e tente novamente.',
    ])
  })

  it('falha de rede ao adicionar emite erro', async () => {
    mockCreate.mockRejectedValue(new Error('Network Error'))
    const wrapper = await mountStep()
    await fill(wrapper, 'Cartão', '1', '10')
    await submit(wrapper)

    expect(wrapper.emitted('error')?.[0]?.[0]).toContain('Não foi possível adicionar o cartão')
  })

  it('erro do dia entra no aria-describedby do campo', async () => {
    const wrapper = await mountStep()
    const input = wrapper.get('[data-testid="card-due-day-input"]')
    expect(input.attributes('aria-describedby')).toBe('card-due-day-hint')

    await fill(wrapper, 'Cartão', '1', '40')
    await submit(wrapper)

    expect(input.attributes('aria-describedby')).toBe('card-due-day-error card-due-day-hint')
    expect(input.attributes('aria-invalid')).toBe('true')
  })

  it('lista cartões já salvos e Remover tira da lista', async () => {
    mockList.mockResolvedValue([card()])
    const wrapper = await mountStep()
    expect(wrapper.get('[data-testid="cards-list"]').text()).toContain('Nubank Roxinho')

    await wrapper.get('[data-testid="remove-card-c1"]').trigger('click')
    await flushPromises()

    expect(mockDelete).toHaveBeenCalledWith('c1')
    expect(wrapper.find('[data-testid="card-item-c1"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="cards-empty"]').exists()).toBe(true)
  })

  it('Remover com 404 tira da lista sem erro', async () => {
    mockList.mockResolvedValue([card()])
    mockDelete.mockRejectedValue({ response: { status: 404 } })
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="remove-card-c1"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="card-item-c1"]').exists()).toBe(false)
    expect(wrapper.emitted('error')).toBeUndefined()
  })

  it('Remover com 500 mantém o cartão e emite erro', async () => {
    mockList.mockResolvedValue([card()])
    mockDelete.mockRejectedValue({ response: { status: 500 } })
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="remove-card-c1"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="card-item-c1"]').exists()).toBe(true)
    expect(wrapper.emitted('error')?.[0]?.[0]).toContain('Não foi possível remover o cartão')
  })

  it('Próximo com cartões chama completeCards(false) e emite done', async () => {
    mockList.mockResolvedValue([card()])
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()

    expect(mockCompleteCards).toHaveBeenCalledWith(false)
    expect(wrapper.emitted('done')?.[0]).toEqual([nextProgress])
  })

  it('Próximo sem cartões também avança (completeCards(false))', async () => {
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()

    expect(mockCompleteCards).toHaveBeenCalledWith(false)
    expect(wrapper.emitted('done')).toHaveLength(1)
  })

  it('Pular chama completeCards(true) sem criar cartão; duplo clique salva uma vez', async () => {
    const wrapper = await mountStep()

    const skip = wrapper.get('[data-testid="skip-button"]')
    skip.trigger('click')
    skip.trigger('click')
    await flushPromises()

    expect(mockCompleteCards).toHaveBeenCalledOnce()
    expect(mockCompleteCards).toHaveBeenCalledWith(true)
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('falha ao salvar o progresso emite o detail da API e não emite done', async () => {
    mockCompleteCards.mockRejectedValue({
      response: { status: 409, data: { detail: 'Conclua o tutorial e as contas antes.' } },
    })
    const wrapper = await mountStep()

    await wrapper.get('[data-testid="next-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.emitted('error')?.[0]).toEqual(['Conclua o tutorial e as contas antes.'])
    expect(wrapper.emitted('done')).toBeUndefined()
  })

  it('Voltar emite back', async () => {
    const wrapper = await mountStep()
    await wrapper.get('[data-testid="back-button"]').trigger('click')
    expect(wrapper.emitted('back')).toHaveLength(1)
  })

  it('falha ao carregar a lista emite erro e ainda permite adicionar e avançar', async () => {
    mockList.mockRejectedValue(new Error('Network Error'))
    const wrapper = await mountStep()

    expect(wrapper.emitted('error')?.[0]?.[0]).toContain('Não foi possível carregar seus cartões')
    expect(wrapper.get('[data-testid="add-card-button"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('[data-testid="next-button"]').attributes('disabled')).toBeUndefined()
  })

  it('não adiciona enquanto a lista inicial carrega, e a resposta tardia não apaga nada', async () => {
    let resolveList: (value: CreditCard[]) => void = () => {}
    mockList.mockReturnValue(new Promise<CreditCard[]>((resolve) => (resolveList = resolve)))
    const wrapper = mount(CardsStep, { attachTo: document.body })
    await flushPromises()

    expect(wrapper.get('[data-testid="add-card-button"]').attributes('disabled')).toBeDefined()
    await fill(wrapper, 'Nubank', '1', '10')
    await submit(wrapper)
    expect(mockCreate).not.toHaveBeenCalled()

    resolveList([card({ id: 'old', cardName: 'Antigo' })])
    await flushPromises()
    await submit(wrapper)

    expect(wrapper.find('[data-testid="card-item-old"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="card-item-new-Nubank"]').exists()).toBe(true)
  })
})
