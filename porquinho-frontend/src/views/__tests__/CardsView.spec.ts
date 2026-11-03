import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { nextTick } from 'vue'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import CardsView from '../CardsView.vue'
import CardForm from '@/components/cards/CardForm.vue'
import type { CreditCard } from '@/types/creditCard'

const mockList = vi.fn()
const mockCreate = vi.fn()

vi.mock('@/services/cardService', () => ({
  listCards: (...a: unknown[]) => mockList(...a),
  createCard: (...a: unknown[]) => mockCreate(...a),
}))

function card(overrides: Partial<CreditCard> = {}): CreditCard {
  return {
    id: 'c1',
    cardName: 'Nubank Roxinho',
    bankName: 'Nubank',
    cardLimit: 5000,
    dueDay: 10,
    createdAt: '2026-11-01T12:00:00Z',
    ...overrides,
  }
}

async function mountView() {
  const wrapper = mount(CardsView, {
    attachTo: document.body,
    global: { stubs: { RouterLink: RouterLinkStub } },
  })
  await flushPromises()
  return wrapper
}

type Wrapper = Awaited<ReturnType<typeof mountView>>

const plain = (s: string) => s.replace(/\s/g, ' ')

const dialog = () => document.body.querySelector('[data-testid="add-card-dialog"]')

async function openDialog(wrapper: Wrapper) {
  await wrapper.get('[data-testid="open-add-card"]').trigger('click')
  await flushPromises()
  return wrapper.findComponent(CardForm)
}

type Form = Awaited<ReturnType<typeof openDialog>>

async function fillAndSave(form: Form, name: string, limit: string, dueDay: string, bank = '') {
  await form.get('[data-testid="card-name-input"]').setValue(name)
  await form.get('[data-testid="card-bank-name-input"]').setValue(bank)
  await form.get('[data-testid="card-limit-input"]').setValue(limit)
  await form.get('[data-testid="card-due-day-input"]').setValue(dueDay)
  await form.get('[data-testid="card-form"]').trigger('submit')
  await flushPromises()
}

describe('CardsView', () => {
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
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('lista os cartões com nome, banco, limite e vencimento, e mostra o limite total', async () => {
    mockList.mockResolvedValue([
      card(),
      card({ id: 'c2', cardName: 'Reserva', bankName: null, cardLimit: 250.5, dueDay: 3 }),
    ])
    const wrapper = await mountView()

    expect(wrapper.get('h1').text()).toBe('Cartões de crédito')
    const first = wrapper.get('[data-testid="card-item-c1"]')
    expect(first.text()).toContain('Nubank Roxinho')
    expect(plain(first.get('[data-testid="card-details"]').text())).toBe('Nubank · vence dia 10')
    expect(plain(first.get('[data-testid="card-limit"]').text())).toBe('R$ 5.000,00')
    const second = wrapper.get('[data-testid="card-item-c2"]')
    expect(plain(second.get('[data-testid="card-details"]').text())).toBe('vence dia 3')
    // Ordem da API
    const ids = wrapper.findAll('li').map((li) => li.attributes('data-testid'))
    expect(ids).toEqual(['card-item-c1', 'card-item-c2'])
    expect(plain(wrapper.get('[data-testid="cards-total"]').text())).toBe('R$ 5.250,50')
  })

  it('soma em centavos: 0,10 + 0,20 dá R$ 0,30', async () => {
    mockList.mockResolvedValue([
      card({ id: 'c1', cardLimit: 0.1 }),
      card({ id: 'c2', cardLimit: 0.2 }),
    ])
    const wrapper = await mountView()

    expect(plain(wrapper.get('[data-testid="cards-total"]').text())).toBe('R$ 0,30')
  })

  it('sem cartões mostra o estado vazio com o botão de adicionar', async () => {
    const wrapper = await mountView()

    const empty = wrapper.get('[data-testid="cards-empty"]')
    expect(empty.text()).toContain('Você ainda não tem cartões cadastrados')
    expect(empty.find('[data-testid="open-add-card"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="cards-total"]').exists()).toBe(false)
  })

  it('adicionar "Inter Gold" com R$ 3.000,00 e dia 15: aparece na lista, total soma, diálogo fecha e mostra sucesso', async () => {
    mockList.mockResolvedValue([card()])
    const wrapper = await mountView()

    const form = await openDialog(wrapper)
    expect(dialog()).not.toBeNull()
    expect(document.activeElement?.id).toBe('card-name')
    expect(document.body.querySelectorAll('#card-bank-suggestions option').length).toBeGreaterThan(
      0,
    )

    await fillAndSave(form, 'Inter Gold', '3.000,00', '15', 'Inter')

    expect(mockCreate).toHaveBeenCalledWith({
      cardName: 'Inter Gold',
      bankName: 'Inter',
      cardLimit: 3000,
      dueDay: 15,
    })
    const item = wrapper.get('[data-testid="card-item-new-Inter Gold"]')
    expect(item.text()).toContain('Inter Gold')
    expect(plain(item.get('[data-testid="card-details"]').text())).toBe('Inter · vence dia 15')
    expect(plain(wrapper.get('[data-testid="cards-total"]').text())).toBe('R$ 8.000,00')
    expect(dialog()).toBeNull()
    const success = wrapper.get('[data-testid="cards-success"]')
    expect(success.attributes('role')).toBe('status')
    expect(success.text()).toBe('Cartão adicionado com sucesso')
    expect(mockList).toHaveBeenCalledOnce()
  })

  it('adicionar a partir do estado vazio mostra a lista e o total', async () => {
    const wrapper = await mountView()
    const form = await openDialog(wrapper)
    await fillAndSave(form, 'Inter Gold', '3.000,00', '15')

    expect(wrapper.find('[data-testid="cards-empty"]').exists()).toBe(false)
    expect(plain(wrapper.get('[data-testid="cards-total"]').text())).toBe('R$ 3.000,00')
  })

  it.each(['0', '32'])(
    'dia "%s" mostra o erro no campo e o diálogo continua aberto',
    async (day) => {
      const wrapper = await mountView()
      const form = await openDialog(wrapper)
      await fillAndSave(form, 'Cartão', '100,00', day)

      expect(form.get('[data-testid="card-due-day-error"]').text()).toBe(
        'Informe um dia entre 1 e 31',
      )
      expect(mockCreate).not.toHaveBeenCalled()
      expect(dialog()).not.toBeNull()
    },
  )

  it('nome vazio ou limite inválido mostra o erro no campo e o diálogo continua aberto', async () => {
    const wrapper = await mountView()
    const form = await openDialog(wrapper)
    await fillAndSave(form, '', 'abc', '10')

    expect(form.get('[data-testid="card-name-error"]').text()).toBe('Informe o nome do cartão')
    expect(form.get('[data-testid="card-limit-error"]').text()).toContain('5.000,00')
    expect(mockCreate).not.toHaveBeenCalled()
    expect(dialog()).not.toBeNull()
  })

  it('400 com errors aparece no campo e o diálogo continua aberto', async () => {
    mockCreate.mockRejectedValue({
      response: {
        status: 400,
        data: {
          detail: 'Dados inválidos',
          errors: { dueDay: 'Informe um dia entre 1 e 31' },
        },
      },
    })
    const wrapper = await mountView()
    const form = await openDialog(wrapper)
    await fillAndSave(form, 'X', '1', '10')

    expect(form.get('[data-testid="card-due-day-error"]').text()).toBe(
      'Informe um dia entre 1 e 31',
    )
    expect(document.body.querySelector('[data-testid="add-card-error"]')).toBeNull()
    expect(dialog()).not.toBeNull()
  })

  it('falha de rede mostra a mensagem no diálogo e preserva os dados', async () => {
    mockCreate.mockRejectedValue(new Error('Network Error'))
    const wrapper = await mountView()
    const form = await openDialog(wrapper)
    await fillAndSave(form, 'Inter Gold', '3.000,00', '15')

    const error = document.body.querySelector('[data-testid="add-card-error"]')
    expect(error?.textContent?.trim()).toBe('Não foi possível salvar o cartão. Tente novamente.')
    expect(dialog()).not.toBeNull()
    const name = form.get('[data-testid="card-name-input"]').element as HTMLInputElement
    expect(name.value).toBe('Inter Gold')
    const limit = form.get('[data-testid="card-limit-input"]').element as HTMLInputElement
    expect(limit.value).toBe('3.000,00')
    expect(wrapper.get('[data-testid="cards-success"]').text()).toBe('')
  })

  it('falha com detail do servidor mostra a mensagem do servidor', async () => {
    mockCreate.mockRejectedValue({ response: { status: 500, data: { detail: 'Serviço fora' } } })
    const wrapper = await mountView()
    const form = await openDialog(wrapper)
    await fillAndSave(form, 'Inter', '1', '10')

    expect(document.body.querySelector('[data-testid="add-card-error"]')?.textContent?.trim()).toBe(
      'Serviço fora',
    )
  })

  it('Cancelar fecha o diálogo sem salvar', async () => {
    const wrapper = await mountView()
    await openDialog(wrapper)
    document.body.querySelector<HTMLButtonElement>('[data-testid="cancel-add-card"]')!.click()
    await flushPromises()

    expect(dialog()).toBeNull()
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('falha ao carregar mostra o aviso e "Tentar de novo" recarrega', async () => {
    mockList.mockRejectedValueOnce(new Error('Network Error')).mockResolvedValueOnce([card()])
    const wrapper = await mountView()

    const alert = wrapper.get('[data-testid="cards-load-error"]')
    expect(alert.attributes('role')).toBe('alert')
    expect(wrapper.find('[data-testid="open-add-card"]').exists()).toBe(false)

    await wrapper.get('[data-testid="cards-retry"]').trigger('click')
    await flushPromises()

    expect(mockList).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="cards-load-error"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="card-item-c1"]').exists()).toBe(true)
  })

  it('tem o link de volta ao dashboard', async () => {
    const wrapper = await mountView()
    const link = wrapper.getComponent(RouterLinkStub)
    expect(link.props('to')).toBe('/dashboard')
  })

  it('enquanto salva, Cancelar fica desabilitado e Esc ou update:open(false) não fecham', async () => {
    let resolveCreate: (c: CreditCard) => void = () => {}
    mockCreate.mockReturnValue(new Promise<CreditCard>((resolve) => (resolveCreate = resolve)))
    const wrapper = await mountView()
    const form = await openDialog(wrapper)
    await fillAndSave(form, 'Inter Gold', '3.000,00', '15')

    const cancel = document.body.querySelector<HTMLButtonElement>('[data-testid="cancel-add-card"]')
    expect(cancel?.disabled).toBe(true)
    document.activeElement?.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }),
    )
    await flushPromises()
    expect(dialog()).not.toBeNull()
    wrapper.findComponent({ name: 'Dialog' }).vm.$emit('update:open', false)
    await flushPromises()
    expect(dialog()).not.toBeNull()

    resolveCreate(card({ id: 'new-Inter Gold', cardName: 'Inter Gold', cardLimit: 3000 }))
    await flushPromises()
    expect(dialog()).toBeNull()
  })

  it('depois de uma falha, salvar de novo com sucesso tira o erro', async () => {
    mockCreate.mockRejectedValueOnce(new Error('Network Error'))
    let resolveCreate: (c: CreditCard) => void = () => {}
    mockCreate.mockReturnValueOnce(new Promise<CreditCard>((resolve) => (resolveCreate = resolve)))
    const wrapper = await mountView()
    const form = await openDialog(wrapper)
    await fillAndSave(form, 'Inter Gold', '3.000,00', '15')
    expect(document.body.querySelector('[data-testid="add-card-error"]')).not.toBeNull()

    await form.get('[data-testid="card-form"]').trigger('submit')
    await flushPromises()
    expect(document.body.querySelector('[data-testid="add-card-error"]')).toBeNull()

    resolveCreate(card({ id: 'new-Inter Gold', cardName: 'Inter Gold', cardLimit: 3000 }))
    await flushPromises()
    expect(dialog()).toBeNull()
    expect(wrapper.get('[data-testid="cards-success"]').text()).toBe(
      'Cartão adicionado com sucesso',
    )
  })

  it('reabrir o diálogo esconde a mensagem de sucesso', async () => {
    mockList.mockResolvedValue([card()])
    const wrapper = await mountView()
    await fillAndSave(await openDialog(wrapper), 'Inter Gold', '3.000,00', '15')
    expect(wrapper.get('[data-testid="cards-success"]').text()).not.toBe('')

    await openDialog(wrapper)
    expect(wrapper.get('[data-testid="cards-success"]').text()).toBe('')
  })

  it('a mensagem de sucesso some depois de 5 s', async () => {
    const wrapper = await mountView()
    await fillAndSave(await openDialog(wrapper), 'Inter Gold', '3.000,00', '15')
    vi.useFakeTimers()
    try {
      // O timer foi criado antes dos fake timers: recria salvando de novo
      await fillAndSave(await openDialog(wrapper), 'Itaú', '1,00', '5')
      expect(wrapper.get('[data-testid="cards-success"]').text()).not.toBe('')
      vi.advanceTimersByTime(5000)
      await nextTick()
      expect(wrapper.get('[data-testid="cards-success"]').text()).toBe('')
    } finally {
      vi.useRealTimers()
    }
  })

  it('ao fechar depois de salvar, o foco vai para o botão "Adicionar cartão" do topo', async () => {
    const wrapper = await mountView()
    await fillAndSave(await openDialog(wrapper), 'Inter Gold', '3.000,00', '15')
    await flushPromises()

    expect(document.activeElement?.getAttribute('data-testid')).toBe('open-add-card')
  })

  it('ao cancelar sem cartões, o foco vai para o título', async () => {
    const wrapper = await mountView()
    await openDialog(wrapper)
    document.body.querySelector<HTMLButtonElement>('[data-testid="cancel-add-card"]')!.click()
    await flushPromises()

    expect(document.activeElement).toBe(wrapper.get('h1').element)
  })

  it('a região de status fica sempre no DOM, vazia até haver mensagem', async () => {
    const wrapper = await mountView()
    const status = wrapper.get('[data-testid="cards-success"]')
    expect(status.attributes('role')).toBe('status')
    expect(status.text()).toBe('')
  })

  it('depois de uma falha, nova tentativa com campo inválido tira o aviso antigo', async () => {
    mockCreate.mockRejectedValueOnce(new Error('Network Error'))
    const wrapper = await mountView()
    const form = await openDialog(wrapper)
    await fillAndSave(form, 'Inter Gold', '3.000,00', '15')
    expect(document.body.querySelector('[data-testid="add-card-error"]')).not.toBeNull()

    await fillAndSave(form, 'Inter Gold', 'abc', '15')
    expect(form.find('[data-testid="card-limit-error"]').exists()).toBe(true)
    expect(document.body.querySelector('[data-testid="add-card-error"]')).toBeNull()
    expect(mockCreate).toHaveBeenCalledOnce()
  })

  it('sem envio em andamento, clicar fora (update:open(false)) fecha o diálogo', async () => {
    const wrapper = await mountView()
    await openDialog(wrapper)
    wrapper.findComponent({ name: 'Dialog' }).vm.$emit('update:open', false)
    await flushPromises()
    expect(dialog()).toBeNull()
  })

  it('reabrir depois de Cancelar mostra o formulário vazio e sem erros', async () => {
    const wrapper = await mountView()
    let form = await openDialog(wrapper)
    await fillAndSave(form, 'Inter Gold', 'abc', '15')
    expect(form.find('[data-testid="card-limit-error"]').exists()).toBe(true)
    document.body.querySelector<HTMLButtonElement>('[data-testid="cancel-add-card"]')!.click()
    await flushPromises()

    form = await openDialog(wrapper)
    expect((form.get('[data-testid="card-name-input"]').element as HTMLInputElement).value).toBe('')
    expect((form.get('[data-testid="card-limit-input"]').element as HTMLInputElement).value).toBe(
      '',
    )
    expect((form.get('[data-testid="card-due-day-input"]').element as HTMLInputElement).value).toBe(
      '',
    )
    expect(form.findAll('[data-testid$="-error"]')).toHaveLength(0)
  })
})
