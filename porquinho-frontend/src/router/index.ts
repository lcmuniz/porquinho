import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // Raiz: dashboard (guard manda para /login se nao autenticado)
    {
      path: '/',
      redirect: { name: 'dashboard' },
    },

    // Rotas publicas (acessiveis sem autenticacao)
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/auth/forgot-password',
      name: 'forgot-password',
      component: () => import('../views/ForgotPasswordView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/auth/reset-password',
      name: 'reset-password',
      component: () => import('../views/ResetPasswordView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/auth/callback',
      name: 'auth-callback',
      component: () => import('../views/AuthCallbackView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/auth/verify-email',
      name: 'verify-email',
      component: () => import('../views/EmailVerificationView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/terms',
      name: 'terms',
      component: () => import('../views/TermsView.vue'),
      meta: { requiresAuth: false },
    },
    {
      path: '/privacy',
      name: 'privacy',
      component: () => import('../views/PrivacyView.vue'),
      meta: { requiresAuth: false },
    },

    // Rotas protegidas (requerem autenticacao)
    {
      path: '/dashboard',
      name: 'dashboard',
      component: () => import('../views/DashboardView.vue'),
      meta: { requiresAuth: true },
    },
    {
      // Story 4.1: contas bancárias (lista, saldo total e cadastro)
      path: '/contas',
      name: 'accounts',
      component: () => import('../views/AccountsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      // Story 4.7: histórico de saldo da conta (gráfico e tabela)
      path: '/contas/:id/historico',
      name: 'account-history',
      component: () => import('../views/AccountHistoryView.vue'),
      meta: { requiresAuth: true },
    },
    {
      // Story 4.2: cartões de crédito (lista, limite total e cadastro)
      path: '/cartoes',
      name: 'cards',
      component: () => import('../views/CardsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      // Story 5.1: envio de extrato OFX para uma conta ou um cartão
      path: '/importar',
      name: 'import',
      component: () => import('../views/ImportView.vue'),
      meta: { requiresAuth: true },
    },
    {
      // Story 5.9: revisão das transações classificadas de uma importação
      path: '/importar/:id/revisao',
      name: 'import-review',
      component: () => import('../views/ImportReviewView.vue'),
      meta: { requiresAuth: true },
    },
    {
      // Story 5.12: transações confirmadas, com edição e remoção
      path: '/transacoes',
      name: 'transactions',
      component: () => import('../views/TransactionsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      // Story 6.1: categorias de gasto (lista por grupo e criação)
      path: '/categorias',
      name: 'categories',
      component: () => import('../views/CategoriesView.vue'),
      meta: { requiresAuth: true },
    },
    {
      // Story 6.3: orçamento mensal por categoria
      path: '/orcamento',
      name: 'budget',
      component: () => import('../views/BudgetView.vue'),
      meta: { requiresAuth: true },
    },
    {
      // Story 6.8: histórico de alocações de uma categoria (gráfico, tabela e CSV)
      path: '/orcamento/categorias/:id/historico',
      name: 'category-budget-history',
      component: () => import('../views/CategoryBudgetHistoryView.vue'),
      meta: { requiresAuth: true },
    },
    {
      // Story 2.2: plano mensal (Stripe Checkout). Retorno do Checkout: ?status=sucesso|cancelado
      path: '/assinatura',
      name: 'subscription',
      component: () => import('../views/SubscriptionView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/onboarding',
      name: 'onboarding',
      component: () => import('../views/OnboardingView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/auth/sessions',
      name: 'sessions',
      component: () => import('../views/SessionsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/auth/data-export',
      name: 'data-export',
      component: () => import('../views/DataExportView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/auth/account-deletion',
      name: 'account-deletion',
      component: () => import('../views/AccountDeletionView.vue'),
      meta: { requiresAuth: true },
    },

    // Rotas de desenvolvimento (sem restricao)
    {
      path: '/design-system',
      name: 'design-system',
      component: () => import('../views/DesignSystemView.vue'),
    },
  ],
})

// Navigation guard: redireciona para /login se rota requer auth e usuario nao esta logado
router.beforeEach((to) => {
  // Check if user is authenticated (authStore saves user in localStorage with key 'user')
  const isAuthenticated = !!localStorage.getItem('user')

  if (to.meta.requiresAuth && !isAuthenticated) {
    return { name: 'login' }
  }

  // Redireciona usuario ja autenticado para /dashboard se tentar acessar /login ou /register
  if ((to.name === 'login' || to.name === 'register') && isAuthenticated) {
    return { name: 'dashboard' }
  }
})

export default router
