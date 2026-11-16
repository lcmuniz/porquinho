import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import { supabase } from './supabase'

/** Leva ao /login guardando a página atual (só se ela exige login: no /login ou em telas públicas não faz nada). */
function goToLogin(query: Record<string, string> = {}) {
  const current = router.currentRoute.value
  if (!current.meta.requiresAuth) return
  void router.push({ name: 'login', query: { redirectTo: current.fullPath, ...query } })
}

/**
 * Sessão que o servidor não aceita mais (refresh falhou, conta em exclusão): limpa o usuário local, encerra a sessão
 * do Supabase só neste navegador e volta ao /login.
 */
export function endSession(query: Record<string, string> = {}) {
  useAuthStore().clearUser()
  goToLogin(query)
  supabase.auth.signOut({ scope: 'local' }).catch(() => {
    // Sem rede ou sessão já inválida: o usuário local já foi limpo e o guard bloqueia as rotas protegidas.
  })
}

let registered = false

/**
 * Registra uma vez o ouvinte de sessão do Supabase. SIGNED_OUT (logout em outra aba, refresh token revogado) limpa o
 * usuário e volta ao /login. TOKEN_REFRESHED não exige nada: o interceptor do axios lê a sessão a cada requisição.
 */
export function registerAuthListener() {
  if (registered) return
  registered = true
  supabase.auth.onAuthStateChange((event) => {
    if (event === 'SIGNED_OUT') {
      useAuthStore().clearUser()
      goToLogin()
    }
  })
}
