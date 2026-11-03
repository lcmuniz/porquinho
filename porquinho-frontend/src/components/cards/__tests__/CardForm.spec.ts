import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import CardForm from '../CardForm.vue'
import type { CreditCard } from '@/types/creditCard'

const mockCreate = vi.fn()
const mockUpdate = vi.fn()

vi.mock('@/services/cardService', () => ({
  createCard: (...a: unknown[]) => mockCreate(...a),
  updateCard: (...a: unknown[]) => mockUpdate(...a),
}))

function created(overrides: Partial<CreditCard> = {}): CreditCard {
  return {
    id: 'new',
    cardName: 'Inter Gold',
    bankName: 'Inter',
    cardLimit: 3000,
    dueDay: 15,
    createdAt: '2026-11-03T12:00:00Z',
    ...overrides,
  }
}

function mountForm(props: Record<string, unknown> = {}) {
  return mount(CardForm, { props, attachTo: document.body })
}

type Wrapper = ReturnType<typeof mountForm>

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

const inputValue = (wrapper: Wrapper, testId: string) =>
  (wrapper.get(`[data-testid="${testId}"]`).element as HTMLInputElement).value

describe('CardForm', () => {
  beforeEach(() => {
    mockCreate.mockReset().mockResolvedValue(created())
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('tem label em todos os campos, alvos de 44px e sugestões de banco', () => {
    const wrapper = mountForm()
    for (const id of ['card-name', 'card-bank-name', 'card-limit', 'card-due-day']) {
      expect(wrapper.find(`label[for="${id}"]`).exists()).toBe(true)
      expect(wrapper.get(`#${id}`).classes()).toContain('min-h-[44px]')
    }
    expect(wrapper.get('[data-testid="add-card-button"]').classes()).toContain('min-h-[44px]')
    expect(wrapper.findAll('#card-bank-suggestions option').length).toBeGreaterThan(0)
  })

  it('envia os dados convertidos, emite created e limpa o formulário', async () => {
    const wrapper = mountForm()
    await fill(wrapper, ' Inter Gold ', '3.000,00', '15', 'Inter')
    await submit(wrapper)

    expect(mockCreate).toHaveBeenCalledWith({
      cardName: 'Inter Gold',
      bankName: 'Inter',
      cardLimit: 3000,
      dueDay: 15,
    })
    expect(wrapper.emitted('created')?.[0]).toEqual([created()])
    expect(inputValue(wrapper, 'card-name-input')).toBe('')
    expect(inputValue(wrapper, 'card-limit-input')).toBe('')
    expect(inputValue(wrapper, 'card-due-day-input')).toBe('')
  })

  it.each(['0', '32'])('dia "%s" mostra o erro no campo e não chama a API', async (day) => {
    const wrapper = mountForm()
    await fill(wrapper, 'Cartão', '100,00', day)
    await submit(wrapper)

    expect(wrapper.get('[data-testid="card-due-day-error"]').text()).toBe(
      'Informe um dia entre 1 e 31',
    )
    expect(wrapper.get('#card-due-day').attributes('aria-describedby')).toBe(
      'card-due-day-error card-due-day-hint',
    )
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('nome vazio e limite inválido mostram o erro no campo e não chamam a API', async () => {
    const wrapper = mountForm()
    await fill(wrapper, '', 'abc', '10')
    await submit(wrapper)

    expect(wrapper.get('[data-testid="card-name-error"]').text()).toBe('Informe o nome do cartão')
    expect(wrapper.get('#card-name').attributes('aria-describedby')).toBe('card-name-error')
    expect(wrapper.get('[data-testid="card-limit-error"]').text()).toContain('5.000,00')
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('400 com errors aparece no campo, sem emitir failed', async () => {
    mockCreate.mockRejectedValue({
      response: { status: 400, data: { errors: { cardLimit: 'Limite inválido' } } },
    })
    const wrapper = mountForm()
    await fill(wrapper, 'Inter', '1', '10')
    await submit(wrapper)

    expect(wrapper.get('[data-testid="card-limit-error"]').text()).toBe('Limite inválido')
    expect(wrapper.emitted('failed')).toBeUndefined()
  })

  it('outra falha emite failed com o detail e mantém os dados', async () => {
    mockCreate.mockRejectedValue({ response: { status: 500, data: { detail: 'Erro interno' } } })
    const wrapper = mountForm()
    await fill(wrapper, 'Inter', '3.000,00', '15')
    await submit(wrapper)

    expect(wrapper.emitted('failed')?.[0]).toEqual(['Erro interno'])
    expect(inputValue(wrapper, 'card-name-input')).toBe('Inter')
    expect(inputValue(wrapper, 'card-limit-input')).toBe('3.000,00')
    expect(inputValue(wrapper, 'card-due-day-input')).toBe('15')
  })

  it('falha de rede emite failed sem detail', async () => {
    mockCreate.mockRejectedValue(new Error('Network Error'))
    const wrapper = mountForm()
    await fill(wrapper, 'Inter', '1', '10')
    await submit(wrapper)

    expect(wrapper.emitted('failed')?.[0]).toEqual([undefined])
  })

  it('disabled bloqueia o envio', async () => {
    const wrapper = mountForm({ disabled: true })
    await fill(wrapper, 'Inter', '1', '10')
    await submit(wrapper)

    expect(wrapper.get('[data-testid="add-card-button"]').attributes('disabled')).toBeDefined()
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('informa o envio em andamento por v-model:submitting e usa os rótulos recebidos', async () => {
    let resolve: (c: CreditCard) => void = () => {}
    mockCreate.mockReturnValue(new Promise<CreditCard>((r) => (resolve = r)))
    const wrapper = mountForm({ submitLabel: 'Salvar cartão', submittingLabel: 'Salvando...' })
    expect(wrapper.get('[data-testid="add-card-button"]').text()).toBe('Salvar cartão')

    await fill(wrapper, 'Inter', '1', '10')
    await wrapper.get('[data-testid="card-form"]').trigger('submit')
    expect(wrapper.emitted('update:submitting')?.[0]).toEqual([true])
    await flushPromises()
    const button = wrapper.get('[data-testid="add-card-button"]')
    expect(button.text()).toBe('Salvando...')
    expect(button.attributes('disabled')).toBeDefined()

    resolve(created())
    await flushPromises()
    const updates = wrapper.emitted('update:submitting') ?? []
    expect(updates[updates.length - 1]).toEqual([false])
  })

  it('focus() foca o campo de nome', () => {
    const wrapper = mountForm()
    ;(wrapper.vm as unknown as { focus: () => void }).focus()
    expect(document.activeElement?.id).toBe('card-name')
  })

  it('dois envios no mesmo tick chamam a API uma vez só', async () => {
    // Modelo controlado pelo pai: `submitting` só muda quando o pai re-renderiza
    const wrapper = mountForm({ submitting: false, 'onUpdate:submitting': () => {} })
    await fill(wrapper, 'Inter', '1', '10')
    const form = wrapper.get('[data-testid="card-form"]')
    form.trigger('submit')
    form.trigger('submit')
    await flushPromises()

    expect(mockCreate).toHaveBeenCalledOnce()
  })

  it('emite attempt em toda tentativa, mesmo quando a validação local falha', async () => {
    const wrapper = mountForm()
    await fill(wrapper, '', '1', '10')
    await submit(wrapper)

    expect(wrapper.emitted('attempt')).toHaveLength(1)
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('400 com campo conhecido e campo desconhecido mostra o erro no campo e emite failed com o detail', async () => {
    mockCreate.mockRejectedValue({
      response: {
        status: 400,
        data: {
          detail: 'Dados inválidos',
          errors: { cardLimit: 'Limite inválido', closingDay: 'Campo desconhecido' },
        },
      },
    })
    const wrapper = mountForm()
    await fill(wrapper, 'Inter', '1', '10')
    await submit(wrapper)

    expect(wrapper.get('[data-testid="card-limit-error"]').text()).toBe('Limite inválido')
    expect(wrapper.emitted('failed')?.[0]).toEqual(['Dados inválidos'])
  })

  it('400 só com campos desconhecidos emite failed com o detail', async () => {
    mockCreate.mockRejectedValue({
      response: {
        status: 400,
        data: { detail: 'Dados inválidos', errors: { closingDay: 'Campo desconhecido' } },
      },
    })
    const wrapper = mountForm()
    await fill(wrapper, 'Inter', '1', '10')
    await submit(wrapper)

    expect(wrapper.emitted('failed')?.[0]).toEqual(['Dados inválidos'])
  })

  it('nome ou banco com mais de 100 caracteres mostram o erro no campo', async () => {
    const wrapper = mountForm()
    await fill(wrapper, 'a'.repeat(101), '1', '10', 'b'.repeat(101))
    await submit(wrapper)

    expect(wrapper.get('[data-testid="card-name-error"]').text()).toBe(
      'Use no máximo 100 caracteres no nome do cartão',
    )
    expect(wrapper.get('#card-bank-name-error').text()).toBe(
      'Use no máximo 100 caracteres no nome do banco',
    )
    expect(wrapper.get('#card-bank-name').attributes('aria-describedby')).toBe(
      'card-bank-name-error',
    )
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('limite vazio pede "use 0,00" e limite negativo é recusado', async () => {
    const wrapper = mountForm()
    await fill(wrapper, 'Cartão', '', '10')
    await submit(wrapper)
    expect(wrapper.get('[data-testid="card-limit-error"]').text()).toBe(
      'Informe o limite do cartão (use 0,00 se não souber)',
    )

    await fill(wrapper, 'Cartão', '-10,00', '10')
    await submit(wrapper)
    expect(wrapper.get('[data-testid="card-limit-error"]').text()).toBe(
      'O limite não pode ser negativo',
    )
    expect(mockCreate).not.toHaveBeenCalled()
  })
})

describe('CardForm — edição (Story 4.3)', () => {
  const editing: CreditCard = {
    id: 'c1',
    cardName: 'Nubank',
    bankName: 'Nubank',
    cardLimit: 5000,
    dueDay: 10,
    createdAt: '2026-11-01T12:00:00Z',
  }

  beforeEach(() => {
    mockCreate.mockReset()
    mockUpdate
      .mockReset()
      .mockImplementation(async (id: string, data) => ({ ...editing, ...data, id }))
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('começa preenchido com os valores atuais (limite no formato brasileiro)', () => {
    const wrapper = mountForm({ card: editing, submitLabel: 'Salvar alterações' })
    expect(inputValue(wrapper, 'card-name-input')).toBe('Nubank')
    expect(inputValue(wrapper, 'card-bank-name-input')).toBe('Nubank')
    expect(inputValue(wrapper, 'card-limit-input')).toBe('5.000,00')
    expect(inputValue(wrapper, 'card-due-day-input')).toBe('10')
    expect(wrapper.get('[data-testid="save-card-button"]').text()).toBe('Salvar alterações')
  })

  it('salvar chama PUT e emite updated', async () => {
    const wrapper = mountForm({ card: editing })
    await wrapper.get('[data-testid="card-limit-input"]').setValue('6.000,00')
    await wrapper.get('[data-testid="card-due-day-input"]').setValue('20')
    await submit(wrapper)

    expect(mockCreate).not.toHaveBeenCalled()
    expect(mockUpdate).toHaveBeenCalledWith('c1', {
      cardName: 'Nubank',
      bankName: 'Nubank',
      cardLimit: 6000,
      dueDay: 20,
    })
    const updated = wrapper.emitted('updated')?.[0]?.[0] as CreditCard
    expect(updated.dueDay).toBe(20)
    expect(wrapper.emitted('created')).toBeUndefined()
  })

  it('valores inválidos (dia 32, limite negativo) não chamam a API', async () => {
    const wrapper = mountForm({ card: editing })
    await wrapper.get('[data-testid="card-limit-input"]').setValue('-1,00')
    await wrapper.get('[data-testid="card-due-day-input"]').setValue('32')
    await submit(wrapper)

    expect(mockUpdate).not.toHaveBeenCalled()
    expect(wrapper.get('[data-testid="card-due-day-error"]').text()).toBe(
      'Informe um dia entre 1 e 31',
    )
    expect(wrapper.find('[data-testid="card-limit-error"]').exists()).toBe(true)
  })

  it('404 emite not-found (e não failed)', async () => {
    mockUpdate.mockRejectedValue({
      response: { status: 404, data: { detail: 'Cartão não encontrado' } },
    })
    const wrapper = mountForm({ card: editing })
    await submit(wrapper)

    expect(wrapper.emitted('notFound')).toHaveLength(1)
    expect(wrapper.emitted('failed')).toBeUndefined()
  })

  it('400 do servidor aparece no campo', async () => {
    mockUpdate.mockRejectedValue({
      response: { status: 400, data: { errors: { cardLimit: 'O limite não pode ser negativo' } } },
    })
    const wrapper = mountForm({ card: editing })
    await submit(wrapper)

    expect(wrapper.get('[data-testid="card-limit-error"]').text()).toBe(
      'O limite não pode ser negativo',
    )
    expect(wrapper.emitted('failed')).toBeUndefined()
  })
})
