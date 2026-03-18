import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    // Rotas publicas (acessiveis sem autenticacao)
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { requiresAuth: false },
    },
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
      path: '/forgot-password',
      name: 'forgot-password',
      component: () => import('../views/ForgotPasswordView.vue'),
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
      path: '/onboarding',
      name: 'onboarding',
      component: () => import('../views/OnboardingView.vue'),
      meta: { requiresAuth: true },
    },

    // Rotas de desenvolvimento (sem restricao)
    {
      path: '/about',
      name: 'about',
      component: () => import('../views/AboutView.vue'),
    },
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
