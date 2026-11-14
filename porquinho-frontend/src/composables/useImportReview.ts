import { computed, ref, type Component } from 'vue'
import { CircleCheck, Flag, TriangleAlert, UserCheck } from 'lucide-vue-next'
import { plural } from '@/lib/plural'
import { getImport, listImportTransactions } from '@/services/transactionImportService'
import type { ImportTransaction, TransactionImport } from '@/types/transactionImport'

/** Carga, grupos por confiança e seleção da revisão de uma importação (Stories 5.9 e 5.10). */
export type GroupKey = 'high' | 'medium' | 'low' | 'reviewed' | 'card-payment'

const HIGH_CONFIDENCE = 80
const MEDIUM_CONFIDENCE = 50

const GROUP_TITLES: Record<GroupKey, string> = {
  high: 'Alta confiança',
  medium: 'Média confiança',
  low: 'Baixa confiança',
  reviewed: 'Revisadas por você',
  'card-payment': 'Pagamento de fatura',
}

interface ApiError {
  response?: { status?: number }
}

/** Sem categoria, ou sem confiança numa linha REGULAR (dados anteriores à Story 5.8). */
function isUnclassified(row: ImportTransaction): boolean {
  return row.category === null || row.confidence === null
}

function groupOf(row: ImportTransaction): GroupKey {
  if (row.kind === 'CARD_PAYMENT') return 'card-payment'
  if (row.source === 'USER') return 'reviewed'
  if (isUnclassified(row)) return 'low'
  const confidence = row.confidence ?? 0
  if (confidence >= HIGH_CONFIDENCE) return 'high'
  return confidence >= MEDIUM_CONFIDENCE ? 'medium' : 'low'
}

/** Ícone e texto, nunca só a cor (WCAG). CARD_PAYMENT não tem indicador. */
export function indicator(row: ImportTransaction): { icon: Component; text: string; class: string } | null {
  const group = groupOf(row)
  if (group === 'card-payment') return null
  if (group === 'reviewed') return { icon: UserCheck, text: 'Revisada por você', class: 'text-purple-700' }
  if (isUnclassified(row)) return { icon: Flag, text: 'Não classificado', class: 'text-red-700' }
  const text = `${GROUP_TITLES[group]} (${row.confidence}%)`
  if (group === 'high') return { icon: CircleCheck, text, class: 'text-green-700' }
  if (group === 'medium') return { icon: TriangleAlert, text, class: 'text-yellow-700' }
  return { icon: Flag, text, class: 'text-red-700' }
}

export function useImportReview(importId: () => string) {
  const transactionImport = ref<TransactionImport | null>(null)
  const transactions = ref<ImportTransaction[]>([])
  const loading = ref(true)
  const loadFailed = ref(false)
  const notFound = ref(false)
  const selected = ref(new Set<string>())

  const groups = computed(() =>
    (Object.keys(GROUP_TITLES) as GroupKey[]).map((key) => ({
      key,
      title: GROUP_TITLES[key],
      rows: transactions.value.filter((row) => groupOf(row) === key),
    })),
  )

  const visibleGroups = computed(() => groups.value.filter((group) => group.rows.length > 0))

  function countOf(key: GroupKey): number {
    return groups.value.find((group) => group.key === key)?.rows.length ?? 0
  }

  const countLabel = computed(() => {
    const high = plural(countOf('high'), 'transação de alta confiança', 'transações de alta confiança')
    const review = plural(countOf('medium') + countOf('low'), 'precisa de revisão', 'precisam de revisão')
    return `${high}, ${review}`
  })

  /** Linhas que o "Aprovar todas" ainda alteraria. */
  const pendingHigh = computed(
    () => transactions.value.filter((row) => groupOf(row) === 'high' && row.reviewStatus === 'PENDING').length,
  )

  /** CARD_PAYMENT fica de fora: não tem categoria nem revisão. */
  const regularRows = computed(() => transactions.value.filter((row) => row.kind === 'REGULAR'))
  const unreviewedCount = computed(() => regularRows.value.filter((row) => row.reviewStatus === 'PENDING').length)
  const uncategorizedCount = computed(() => regularRows.value.filter((row) => row.category === null).length)

  /** Sequência das cargas: só a resposta da carga mais recente é aplicada. */
  let request = 0

  async function load() {
    const current = ++request
    // Recarga com a tela já montada não troca a lista pelo esqueleto: foco e rolagem ficam onde estão
    loading.value = transactionImport.value === null
    loadFailed.value = false
    notFound.value = false
    try {
      const id = importId()
      const [found, rows] = await Promise.all([getImport(id), listImportTransactions(id)])
      if (current !== request) return
      transactionImport.value = found
      transactions.value = rows
      const selectable = new Set(rows.filter((row) => row.kind === 'REGULAR').map((row) => row.id))
      selected.value = new Set([...selected.value].filter((id) => selectable.has(id)))
    } catch (err) {
      if (current !== request) return
      // 400 só vem de um id malformado na URL: para o usuário, é uma importação que não existe
      const status = (err as ApiError)?.response?.status
      if (status === 404 || status === 400) notFound.value = true
      else loadFailed.value = true
    } finally {
      if (current === request) loading.value = false
    }
  }

  /** Outra importação na mesma página: esquece a anterior antes de carregar. */
  function reset() {
    transactionImport.value = null
    transactions.value = []
    selected.value = new Set()
  }

  function toggleRow(id: string) {
    if (!selected.value.delete(id)) selected.value.add(id)
  }

  return {
    transactionImport,
    transactions,
    loading,
    loadFailed,
    notFound,
    selected,
    visibleGroups,
    countLabel,
    pendingHigh,
    unreviewedCount,
    uncategorizedCount,
    load,
    reset,
    toggleRow,
  }
}
