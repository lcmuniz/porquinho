import { computed, ref } from 'vue'
import { listAccounts } from '@/services/accountService'
import type { Account } from '@/types/account'

/**
 * Contas ativas e o total. As cargas têm guarda de sequência: só a resposta da carga mais recente é aplicada, e uma
 * mudança local da lista (`splice`, `filter`, `push`) feita depois de a carga começar invalida a resposta, que leu o
 * servidor antes dela: a carga é descartada e refeita, para não perder a mudança local nem os dados novos.
 */
export function useAccountList() {
  const accounts = ref<Account[]>([])
  let request = 0
  let change = 0

  /** Soma em centavos inteiros: 0,10 + 0,20 dá exatamente 0,30. */
  const totalCents = computed(() =>
    accounts.value.reduce((sum, account) => sum + Math.round(account.currentBalance * 100), 0),
  )

  /** Mudança local da lista: a carga de contas em andamento descarta a resposta e busca de novo. */
  function discardPendingAccountLoads() {
    change++
  }

  /** Lê as contas ativas. A resposta de uma carga substituída por outra não é aplicada, e a falha dela é ignorada. */
  async function fetchAccounts() {
    const current = ++request
    for (;;) {
      const changeAtStart = change
      try {
        const result = await listAccounts()
        if (current !== request) return
        // Leu o servidor antes de uma mudança local: busca de novo
        if (changeAtStart !== change) continue
        accounts.value = result
        return
      } catch (err) {
        if (current === request) throw err
        return
      }
    }
  }

  /** Volta para a lista ativa na posição da criação (a API ordena da mais antiga para a mais nova). */
  function insertByCreation(account: Account) {
    const created = Date.parse(account.createdAt)
    const index = accounts.value.findIndex((x) => Date.parse(x.createdAt) > created)
    if (index < 0) accounts.value.push(account)
    else accounts.value.splice(index, 0, account)
  }

  return { accounts, totalCents, fetchAccounts, discardPendingAccountLoads, insertByCreation }
}
