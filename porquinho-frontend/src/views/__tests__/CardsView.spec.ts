import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { nextTick } from 'vue'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import CardsView from '../CardsView.vue'
import CardForm from '@/components/cards/CardForm.vue'
import type { CreditCard } from '@/types/creditCard'

const mockList = vi.fn()
const mockCreate = vi.fn()
const mockUpdate = vi.fn()
const mockDelete = vi.fn()
const mockListDeleted = vi.fn()
const mockRestore = vi.fn()

vi.mock('@/services/cardService', () => ({
  listCards: (...a: unknown[]) => mockList(...a),
  createCard: (...a: unknown[]) => mockCreate(...a),
  updateCard: (...a: unknown[]) => mockUpdate(...a),
  deleteCard: (...a: unknown[]) => mockDelete(...a),
  listDeletedCards: (...a: unknown[]) => mockListDeleted(...a),
  restoreCard: (...a: unknown[]) => mockRestore(...a),
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

describe('CardsView — editar cartão (Story 4.3)', () => {
  const editDialog = () => document.body.querySelector('[data-testid="edit-card-dialog"]')
  const input = (form: Form, testid: string) =>
    form.get(`[data-testid="${testid}"]`).element as HTMLInputElement

  async function openEdit(wrapper: Wrapper, id = 'c1') {
    await wrapper.get(`[data-testid="edit-card-${id}"]`).trigger('click')
    await flushPromises()
    return wrapper.findComponent(CardForm)
  }

  beforeEach(() => {
    mockList
      .mockReset()
      .mockResolvedValue([
        card({ id: 'c1', cardName: 'Nubank', cardLimit: 5000, dueDay: 10 }),
        card({ id: 'c2', cardName: 'Inter', bankName: null, cardLimit: 1000, dueDay: 3 }),
      ])
    mockCreate.mockReset()
    mockUpdate.mockReset().mockImplementation(async (id: string, data) => ({
      ...card({ id }),
      cardName: data.cardName,
      bankName: data.bankName,
      cardLimit: data.cardLimit,
      dueDay: data.dueDay,
    }))
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('cada cartão tem o botão "Editar" com o nome no rótulo acessível e alvo de 44px', async () => {
    const wrapper = await mountView()
    const button = wrapper.get('[data-testid="edit-card-c2"]')
    expect(button.text()).toBe('Editar')
    expect(button.attributes('aria-label')).toBe('Editar Inter')
    expect(button.classes()).toContain('min-h-[44px]')
    expect(button.classes()).toContain('min-w-[44px]')
  })

  it('abre o diálogo "Editar cartão" com os valores atuais (limite no formato brasileiro) e foco no nome', async () => {
    const wrapper = await mountView()
    const form = await openEdit(wrapper)

    expect(editDialog()).not.toBeNull()
    expect(dialog()).toBeNull()
    expect(editDialog()?.textContent).toContain('Editar cartão')
    expect(input(form, 'card-name-input').value).toBe('Nubank')
    expect(input(form, 'card-bank-name-input').value).toBe('Nubank')
    expect(input(form, 'card-limit-input').value).toBe('5.000,00')
    expect(input(form, 'card-due-day-input').value).toBe('10')
    expect(form.get('[data-testid="save-card-button"]').text()).toBe('Salvar alterações')
    expect(document.activeElement?.id).toBe('card-name')
  })

  it('dia 10 → 20 e limite 6.000,00: lista, "vence dia 20", limite total, mensagem e foco', async () => {
    const wrapper = await mountView()
    expect(plain(wrapper.get('[data-testid="cards-total"]').text())).toBe('R$ 6.000,00')
    const form = await openEdit(wrapper)
    await form.get('[data-testid="card-limit-input"]').setValue('6.000,00')
    await form.get('[data-testid="card-due-day-input"]').setValue('20')
    await form.get('[data-testid="card-form"]').trigger('submit')
    await flushPromises()

    expect(mockUpdate).toHaveBeenCalledWith('c1', {
      cardName: 'Nubank',
      bankName: 'Nubank',
      cardLimit: 6000,
      dueDay: 20,
    })
    expect(mockCreate).not.toHaveBeenCalled()
    expect(editDialog()).toBeNull()
    const item = wrapper.get('[data-testid="card-item-c1"]')
    expect(plain(item.get('[data-testid="card-details"]').text())).toContain('vence dia 20')
    expect(plain(item.get('[data-testid="card-limit"]').text())).toBe('R$ 6.000,00')
    expect(plain(wrapper.get('[data-testid="cards-total"]').text())).toBe('R$ 7.000,00')
    expect(wrapper.get('[data-testid="cards-success"]').text()).toBe('Informações atualizadas')
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('edit-card-c1')
  })

  it('dia 0 não chama a API e mostra o erro no campo', async () => {
    const wrapper = await mountView()
    const form = await openEdit(wrapper)
    await form.get('[data-testid="card-due-day-input"]').setValue('0')
    await form.get('[data-testid="card-form"]').trigger('submit')
    await flushPromises()

    expect(mockUpdate).not.toHaveBeenCalled()
    expect(form.get('[data-testid="card-due-day-error"]').text()).toBe(
      'Informe um dia entre 1 e 31',
    )
    expect(editDialog()).not.toBeNull()
  })

  it('400 do servidor aparece no campo e o diálogo continua aberto', async () => {
    mockUpdate.mockRejectedValue({
      response: {
        status: 400,
        data: {
          detail: 'Informe um dia entre 1 e 31',
          errors: { dueDay: 'Informe um dia entre 1 e 31' },
        },
      },
    })
    const wrapper = await mountView()
    const form = await openEdit(wrapper)
    await form.get('[data-testid="card-form"]').trigger('submit')
    await flushPromises()

    expect(editDialog()).not.toBeNull()
    expect(form.get('[data-testid="card-due-day-error"]').text()).toBe(
      'Informe um dia entre 1 e 31',
    )
  })

  it('404 ao salvar fecha o diálogo, tira o cartão da lista e avisa', async () => {
    mockUpdate.mockRejectedValue({
      response: { status: 404, data: { detail: 'Cartão não encontrado' } },
    })
    const wrapper = await mountView()
    const form = await openEdit(wrapper)
    await form.get('[data-testid="card-form"]').trigger('submit')
    await flushPromises()

    expect(editDialog()).toBeNull()
    expect(wrapper.find('[data-testid="card-item-c1"]').exists()).toBe(false)
    expect(plain(wrapper.get('[data-testid="cards-total"]').text())).toBe('R$ 1.000,00')
    const notice = wrapper.get('[data-testid="cards-notice"]')
    expect(notice.text()).toBe('Este cartão não existe mais')
    expect(notice.attributes('role')).toBe('alert')
    expect(notice.classes()).toContain('bg-amber-50')
    expect(wrapper.get('[data-testid="cards-success"]').text()).toBe('')
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('open-add-card')
  })

  it('outra falha mostra a mensagem no diálogo, que continua aberto com os dados', async () => {
    mockUpdate.mockRejectedValue(new Error('Network Error'))
    const wrapper = await mountView()
    const form = await openEdit(wrapper)
    await form.get('[data-testid="card-limit-input"]').setValue('7.000,00')
    await form.get('[data-testid="card-form"]').trigger('submit')
    await flushPromises()

    expect(editDialog()).not.toBeNull()
    expect(document.body.querySelector('[data-testid="add-card-error"]')?.textContent).toContain(
      'Não foi possível salvar o cartão. Tente novamente.',
    )
    expect(input(form, 'card-limit-input').value).toBe('7.000,00')
  })

  it('enquanto salva, o botão mostra "Salvando..." e Cancelar fica desabilitado', async () => {
    let resolveUpdate: (c: CreditCard) => void = () => {}
    mockUpdate.mockReturnValue(new Promise<CreditCard>((resolve) => (resolveUpdate = resolve)))
    const wrapper = await mountView()
    const form = await openEdit(wrapper)
    await form.get('[data-testid="card-form"]').trigger('submit')
    await flushPromises()

    const save = form.get('[data-testid="save-card-button"]')
    expect(save.text()).toBe('Salvando...')
    expect(save.attributes('disabled')).toBeDefined()
    expect(
      document.body.querySelector<HTMLButtonElement>('[data-testid="cancel-add-card"]')?.disabled,
    ).toBe(true)

    resolveUpdate(card({ id: 'c1', cardName: 'Nubank' }))
    await flushPromises()
    expect(editDialog()).toBeNull()
  })

  it('cancelar a edição volta o foco para o "Editar" do cartão', async () => {
    const wrapper = await mountView()
    await openEdit(wrapper, 'c2')
    document.body.querySelector<HTMLButtonElement>('[data-testid="cancel-add-card"]')!.click()
    await flushPromises()

    expect(mockUpdate).not.toHaveBeenCalled()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('edit-card-c2')
  })

  it('depois de editar, "Adicionar cartão" abre o diálogo de criação vazio', async () => {
    const wrapper = await mountView()
    await openEdit(wrapper)
    document.body.querySelector<HTMLButtonElement>('[data-testid="cancel-add-card"]')!.click()
    await flushPromises()

    const form = await openDialog(wrapper)
    expect(dialog()?.textContent).toContain('Adicionar cartão')
    expect(input(form, 'card-name-input').value).toBe('')
    expect(input(form, 'card-limit-input').value).toBe('')
    expect(form.get('[data-testid="add-card-button"]').text()).toBe('Salvar cartão')
  })

  it('o aviso de item que não existe mais some ao abrir outro diálogo e não aparece sem 404', async () => {
    mockUpdate.mockRejectedValue({ response: { status: 404, data: {} } })
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="cards-notice"]').exists()).toBe(false)
    const form = await openEdit(wrapper)
    await form.get('[data-testid="card-form"]').trigger('submit')
    await flushPromises()
    expect(wrapper.find('[data-testid="cards-notice"]').exists()).toBe(true)

    await openEdit(wrapper, 'c2')
    expect(wrapper.find('[data-testid="cards-notice"]').exists()).toBe(false)
  })
})

describe('CardsView — remover e restaurar cartão (Story 4.4)', () => {
  const removeDialog = () => document.body.querySelector('[data-testid="remove-card-dialog"]')
  const bodyButton = (testid: string) =>
    document.body.querySelector<HTMLButtonElement>(`[data-testid="${testid}"]`)!

  async function openRemove(wrapper: Wrapper, id = 'c2') {
    await wrapper.get(`[data-testid="remove-card-${id}"]`).trigger('click')
    await flushPromises()
  }

  async function confirm() {
    bodyButton('confirm-remove-card').click()
    await flushPromises()
  }

  async function showRemoved(wrapper: Wrapper) {
    await wrapper.get('[data-testid="toggle-removed-cards"]').setValue(true)
    await flushPromises()
  }

  const roxinho = () =>
    card({
      id: 'c1',
      cardName: 'Nubank Roxinho',
      cardLimit: 5000,
      createdAt: '2026-11-01T12:00:00Z',
    })
  const inter = () =>
    card({
      id: 'c2',
      cardName: 'Inter Gold',
      bankName: 'Inter',
      cardLimit: 1000,
      dueDay: 5,
      createdAt: '2026-11-02T12:00:00Z',
    })

  beforeEach(() => {
    mockList.mockReset().mockResolvedValue([roxinho(), inter()])
    mockDelete.mockReset().mockResolvedValue(undefined)
    mockListDeleted.mockReset().mockResolvedValue([])
    mockRestore.mockReset()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('cada cartão tem "Remover" com o nome no rótulo acessível e alvo de 44px', async () => {
    const wrapper = await mountView()
    const button = wrapper.get('[data-testid="remove-card-c2"]')
    expect(button.text()).toBe('Remover')
    expect(button.attributes('aria-label')).toBe('Remover Inter Gold')
    expect(button.classes()).toContain('min-h-[44px]')
    expect(button.classes()).toContain('min-w-[44px]')
  })

  it('remover: confirmação, some da lista, total recalculado e "Cartão removido"; restaurar o traz de volta', async () => {
    const wrapper = await mountView()
    expect(plain(wrapper.get('[data-testid="cards-total"]').text())).toBe('R$ 6.000,00')
    await openRemove(wrapper)
    expect(removeDialog()?.textContent).toContain('Remover Inter Gold?')
    expect(removeDialog()?.textContent).toContain(
      'O cartão some da lista, mas o histórico é preservado. Você pode restaurá-lo depois.',
    )
    await confirm()

    expect(mockDelete).toHaveBeenCalledWith('c2')
    expect(removeDialog()).toBeNull()
    expect(wrapper.find('[data-testid="card-item-c2"]').exists()).toBe(false)
    expect(plain(wrapper.get('[data-testid="cards-total"]').text())).toBe('R$ 5.000,00')
    expect(wrapper.get('[data-testid="cards-success"]').text()).toBe('Cartão removido')

    mockListDeleted.mockResolvedValue([inter()])
    await showRemoved(wrapper)
    const section = wrapper.get('[data-testid="removed-cards"]')
    expect(section.get('h2').text()).toBe('Removidos')
    const removedItem = section.get('[data-testid="removed-card-item-c2"]')
    expect(removedItem.find('[data-testid="edit-card-c2"]').exists()).toBe(false)
    const restoreButton = removedItem.get('[data-testid="restore-card-c2"]')
    expect(restoreButton.attributes('aria-label')).toBe('Restaurar Inter Gold')
    expect(plain(wrapper.get('[data-testid="cards-total"]').text())).toBe('R$ 5.000,00')

    mockRestore.mockResolvedValue(inter())
    await restoreButton.trigger('click')
    await flushPromises()

    expect(mockRestore).toHaveBeenCalledWith('c2')
    expect(wrapper.find('[data-testid="removed-card-item-c2"]').exists()).toBe(false)
    expect(
      wrapper
        .get('[data-testid="cards-list"]')
        .findAll('li')
        .map((li) => li.attributes('data-testid')),
    ).toEqual(['card-item-c1', 'card-item-c2'])
    expect(plain(wrapper.get('[data-testid="cards-total"]').text())).toBe('R$ 6.000,00')
    expect(wrapper.get('[data-testid="cards-success"]').text()).toBe('Cartão restaurado')
    await flushPromises()
    expect(document.activeElement?.getAttribute('data-testid')).toBe('edit-card-c2')
  })

  it('o único cartão pode ser removido (sem mínimo) e restaurado a partir do estado vazio', async () => {
    mockList.mockResolvedValue([inter()])
    const wrapper = await mountView()
    await openRemove(wrapper)
    await confirm()

    expect(wrapper.find('[data-testid="cards-empty"]').exists()).toBe(true)
    expect(wrapper.get('label').text()).toContain('Mostrar removidos')
    mockListDeleted.mockResolvedValue([inter()])
    await showRemoved(wrapper)
    mockRestore.mockResolvedValue(inter())
    await wrapper.get('[data-testid="restore-card-c2"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="cards-empty"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="card-item-c2"]').exists()).toBe(true)
  })

  it('falha ao remover mostra a mensagem no diálogo e o cartão continua na lista', async () => {
    mockDelete.mockRejectedValue({ response: { status: 500, data: {} } })
    const wrapper = await mountView()
    await openRemove(wrapper)
    await confirm()

    expect(removeDialog()).not.toBeNull()
    expect(bodyButton('remove-card-error').textContent).toContain(
      'Não foi possível remover o cartão. Tente novamente.',
    )
    expect(wrapper.find('[data-testid="card-item-c2"]').exists()).toBe(true)
  })

  it('404 ao remover fecha o diálogo, avisa e recarrega as listas ativa e de removidos', async () => {
    mockDelete.mockRejectedValue({ response: { status: 404, data: {} } })
    mockList.mockResolvedValueOnce([roxinho(), inter()]).mockResolvedValueOnce([roxinho()])
    mockListDeleted.mockResolvedValueOnce([]).mockResolvedValueOnce([inter()])
    const wrapper = await mountView()
    await showRemoved(wrapper)
    await openRemove(wrapper)
    await confirm()

    expect(removeDialog()).toBeNull()
    expect(mockList).toHaveBeenCalledTimes(2)
    expect(mockListDeleted).toHaveBeenCalledTimes(2)
    expect(wrapper.find('[data-testid="card-item-c2"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="removed-card-item-c2"]').exists()).toBe(true)
    const notice = wrapper.get('[data-testid="cards-notice"]')
    expect(notice.text()).toBe('Este cartão já foi removido ou não existe mais')
    expect(notice.classes()).toContain('bg-amber-50')
  })

  it('cancelar não muda nada e devolve o foco ao "Remover" do cartão', async () => {
    const wrapper = await mountView()
    await openRemove(wrapper)
    bodyButton('cancel-remove-card').click()
    await flushPromises()

    expect(mockDelete).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="card-item-c2"]').exists()).toBe(true)
    expect(document.activeElement?.getAttribute('data-testid')).toBe('remove-card-c2')
  })

  it('enquanto remove, os botões ficam travados e o diálogo não fecha', async () => {
    let resolveDelete: () => void = () => {}
    mockDelete.mockReturnValue(new Promise<void>((resolve) => (resolveDelete = resolve)))
    const wrapper = await mountView()
    await openRemove(wrapper)
    await confirm()

    expect(bodyButton('confirm-remove-card').disabled).toBe(true)
    expect(bodyButton('confirm-remove-card').textContent?.trim()).toBe('Removendo...')
    expect(bodyButton('cancel-remove-card').disabled).toBe(true)
    wrapper.findAllComponents({ name: 'Dialog' })[1]!.vm.$emit('update:open', false)
    await flushPromises()
    expect(removeDialog()).not.toBeNull()

    resolveDelete()
    await flushPromises()
    expect(removeDialog()).toBeNull()
  })

  it('falha ao carregar os removidos mostra "Tentar de novo"', async () => {
    mockListDeleted.mockRejectedValueOnce(new Error('Network Error')).mockResolvedValue([inter()])
    mockList.mockResolvedValue([roxinho()])
    const wrapper = await mountView()
    await showRemoved(wrapper)
    expect(wrapper.get('[data-testid="removed-cards-load-error"]').attributes('role')).toBe('alert')
    await wrapper.get('[data-testid="removed-cards-retry"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="removed-card-item-c2"]').exists()).toBe(true)
  })

  it('404 ao restaurar avisa e recarrega as listas (o cartão já foi restaurado em outra aba)', async () => {
    mockListDeleted.mockResolvedValueOnce([inter()]).mockResolvedValueOnce([])
    mockList.mockResolvedValueOnce([roxinho()]).mockResolvedValueOnce([roxinho(), inter()])
    mockRestore.mockRejectedValue({ response: { status: 404, data: {} } })
    const wrapper = await mountView()
    await showRemoved(wrapper)
    await wrapper.get('[data-testid="restore-card-c2"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="removed-card-item-c2"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="card-item-c2"]').exists()).toBe(true)
    expect(plain(wrapper.get('[data-testid="cards-total"]').text())).toBe('R$ 6.000,00')
    expect(wrapper.get('[data-testid="cards-notice"]').text()).toBe(
      'Este cartão já foi restaurado ou não existe mais',
    )
  })

  it('o cartão restaurado volta na ordem da criação', async () => {
    mockList.mockResolvedValue([inter()])
    const wrapper = await mountView()
    mockListDeleted.mockResolvedValue([roxinho()])
    await showRemoved(wrapper)
    mockRestore.mockResolvedValue(roxinho())

    await wrapper.get('[data-testid="restore-card-c1"]').trigger('click')
    await flushPromises()

    expect(
      wrapper
        .get('[data-testid="cards-list"]')
        .findAll('li')
        .map((li) => li.attributes('data-testid')),
    ).toEqual(['card-item-c1', 'card-item-c2'])
  })

  it('remover com a seção aberta recarrega os removidos (o cartão aparece no topo)', async () => {
    const old = card({ id: 'c9', cardName: 'Antigo' })
    mockListDeleted.mockResolvedValueOnce([old]).mockResolvedValueOnce([inter(), old])
    const wrapper = await mountView()
    await showRemoved(wrapper)
    await openRemove(wrapper)
    await confirm()

    expect(mockListDeleted).toHaveBeenCalledTimes(2)
    expect(
      wrapper
        .get('[data-testid="removed-cards-list"]')
        .findAll('li')
        .map((li) => li.attributes('data-testid')),
    ).toEqual(['removed-card-item-c2', 'removed-card-item-c9'])
  })

  it('uma resposta atrasada dos removidos não sobrescreve a mais recente', async () => {
    let resolveFirst: (c: CreditCard[]) => void = () => {}
    mockListDeleted
      .mockReturnValueOnce(new Promise<CreditCard[]>((resolve) => (resolveFirst = resolve)))
      .mockResolvedValueOnce([inter()])
    const wrapper = await mountView()
    await showRemoved(wrapper)
    await wrapper.get('[data-testid="toggle-removed-cards"]').setValue(false)
    await showRemoved(wrapper)

    resolveFirst([card({ id: 'c9', cardName: 'Antigo' })])
    await flushPromises()

    expect(wrapper.find('[data-testid="removed-card-item-c2"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="removed-card-item-c9"]').exists()).toBe(false)
  })

  it('falha (500) ao restaurar mostra a mensagem, o item fica nos removidos e os botões voltam', async () => {
    mockListDeleted.mockResolvedValue([inter(), card({ id: 'c9', cardName: 'Antigo' })])
    mockRestore.mockRejectedValue({ response: { status: 500, data: {} } })
    mockList.mockResolvedValue([roxinho()])
    const wrapper = await mountView()
    await showRemoved(wrapper)
    await wrapper.get('[data-testid="restore-card-c2"]').trigger('click')
    await flushPromises()

    const notice = wrapper.get('[data-testid="cards-notice"]')
    expect(notice.text()).toBe('Não foi possível restaurar o cartão. Tente novamente.')
    expect(notice.attributes('role')).toBe('alert')
    expect(wrapper.find('[data-testid="removed-card-item-c2"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="card-item-c2"]').exists()).toBe(false)
    const restoreButton = wrapper.get('[data-testid="restore-card-c2"]')
    expect(restoreButton.text()).toBe('Restaurar')
    expect(restoreButton.attributes('disabled')).toBeUndefined()
    expect(wrapper.get('[data-testid="restore-card-c9"]').attributes('disabled')).toBeUndefined()
  })
})
