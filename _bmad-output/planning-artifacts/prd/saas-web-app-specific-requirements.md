# SaaS Web App Specific Requirements

## Project-Type Overview

O **porquinho** é um **SaaS B2C (Software as a Service Business-to-Consumer)** implementado como **Web Application moderna**. Foca em usuários individuais com modelo de trial gratuito seguido de assinatura paga. A arquitetura prioriza experiência fluida de usuário através de SPA (Single Page Application), com dashboard interativo que responde instantaneamente às ações.

**Características principais do tipo de projeto:**
- **Delivery model:** Cloud-based, acesso via browser
- **Target platform:** Web desktop (primário), responsivo mobile (secundário)
- **Interação:** Rich UI com navegação fluida entre camadas do Dashboard GPS
- **Modelo de negócio:** Trial gratuito + Assinatura mensal recorrente
- **Tenant model:** Single-tenant (cada usuário tem dados isolados, não compartilha entre contas)

## Technical Architecture Considerations

**1. Arquitetura Frontend - Single Page Application (SPA)**

**Decisão:** SPA para experiência fluida e responsiva

**Justificativa:**
- Dashboard GPS com 3 camadas requer navegação instantânea sem reload de página
- Transições suaves entre visualizações (Camada 1 → 2 → 3) melhoram experiência
- Importação OFX/CSV com feedback em tempo real durante processamento
- Gráficos e visualizações interativas (curvas, barras de progresso) exigem renderização dinâmica
- Estado da aplicação mantido durante sessão (filtros, seleções, drill-downs)

**Implicações técnicas:**
- Framework moderno (React, Vue, ou similar) para gerenciamento de estado
- Client-side routing para navegação entre seções
- Code splitting para otimizar carregamento inicial
- State management para sincronização entre componentes (Dashboard, Metas, Transações)
- Lazy loading de componentes pesados (gráficos, relatórios)

**Trade-offs:**
- ✅ Experiência fluida, transições instantâneas, UI responsiva
- ❌ Complexidade inicial maior, SEO exige estratégia específica
- ❌ Requer JavaScript habilitado (aceitável para aplicação financeira moderna)

**2. Suporte de Navegadores**

**Target browsers (últimas 2 versões):**
- Chrome/Chromium (prioridade 1 - navegador mais usado)
- Firefox
- Safari (macOS)
- Edge (Chromium-based)

**Não suportados explicitamente no MVP:**
- Internet Explorer (obsoleto)
- Navegadores mobile antigos (< 2 anos)

**Estratégia de compatibilidade:**
- Feature detection (não browser detection)
- Polyfills automáticos via Babel/Webpack para features modernas
- Testes em navegadores principais antes de cada release
- Mensagem clara se navegador não suportado

**Recursos modernos assumidos:**
- ES6+ JavaScript
- CSS Grid e Flexbox
- Fetch API
- LocalStorage/SessionStorage
- Web Crypto API (para criptografia client-side quando necessário)

**3. SEO Strategy**

**Páginas que precisam de SEO:**
- Landing page pública (/)
- Página de features (/features)
- Pricing (/pricing)
- Blog (se houver)
- Páginas de help/docs públicas

**Páginas sem SEO (requer autenticação):**
- Dashboard GPS
- Transações
- Metas
- Contas
- Settings

**Abordagem técnica:**
- **Server-Side Rendering (SSR)** ou **Static Generation** para páginas públicas
- **Client-Side Rendering** para aplicação autenticada (SPA)
- Meta tags apropriadas (title, description, og:tags)
- Sitemap.xml para páginas públicas
- robots.txt configurado
- Schema.org markup para rich snippets (FinancialProduct)

**Palavras-chave alvo (para páginas públicas):**
- "controle financeiro pessoal"
- "orçamento pessoal Brasil"
- "gestão financeira pessoal"
- "alternativa YNAB Brasil"
- "app financeiro brasileiro"

**4. Real-Time Communication**

**MVP: Não necessário**
- Importação OFX/CSV é processo síncrono com feedback de progresso via polling
- Dashboard atualiza sob demanda (usuário faz refresh manual)
- Insights surpresa gerados durante check-in semanal

**Futuro (Post-MVP com Open Banking):**
- WebSocket ou Server-Sent Events para sincronização automática de transações
- Notificações push quando novas transações disponíveis
- Atualização real-time de saldo quando transação entra

**Decisão técnica no MVP:**
- HTTP polling se necessário (ex: progresso de importação)
- Evitar complexidade de WebSockets até haver necessidade real
- Arquitetura deve permitir adicionar real-time sem refactor completo

**5. Accessibility (Acessibilidade)**

**Target: WCAG 2.1 Level AA**

**Justificativa:**
- Produto financeiro deve ser acessível ao máximo de usuários
- Nível AA é balanceado (não excessivamente complexo, cobertura boa)
- Compliance pode ser requisito regulatório futuro

**Requisitos específicos WCAG 2.1 AA:**

**Perceptível:**
- Contraste de cores mínimo 4.5:1 para texto normal, 3:1 para texto grande
- Gráficos e visualizações não dependem apenas de cor (usar padrões, texturas, labels)
- Alternativas textuais para gráficos (descrições, tabelas alternativas)
- Vídeos de onboarding com legendas

**Operável:**
- Toda funcionalidade acessível via teclado (sem necessidade de mouse)
- Ordem de foco lógica (tab order)
- Sem armadilhas de teclado
- Tempo suficiente para ler e usar conteúdo (sem timeouts agressivos)
- Navegação consistente entre páginas

**Compreensível:**
- Linguagem clara e simples (evitar jargão desnecessário)
- Labels claros em campos de formulário
- Mensagens de erro claras e sugestões de correção
- Navegação previsível

**Robusto:**
- Markup HTML semântico válido
- Roles ARIA apropriados para componentes customizados
- Compatibilidade com screen readers (NVDA, JAWS, VoiceOver)

**Ferramentas de teste:**
- Lighthouse accessibility audit (score > 90)
- axe DevTools para detecção automática
- Testes manuais com screen reader
- Testes de navegação apenas por teclado

**Priorização no MVP:**
- ✅ Contraste de cores, navegação por teclado, HTML semântico (essencial)
- ✅ Labels e alternativas textuais (essencial)
- ⚠️ Descrições detalhadas de gráficos complexos (pode ser iterativo)

**6. Subscription Model & Monetization**

**Modelo: Trial gratuito + Assinatura paga**

**Estrutura do modelo:**
- **Trial gratuito:** 30 dias completos, acesso a todas as funcionalidades MVP
- **Após trial:** Assinatura mensal recorrente obrigatória
- **Sem tier gratuito permanente** (diferente de freemium)

**Justificativa do modelo trial (não freemium):**
- Produto requer uso consistente semanal para gerar valor
- Trial de 30 dias suficiente para 4 check-ins semanais e validar valor
- Evita usuários "free riders" que não usam consistentemente
- Receita previsível via assinaturas recorrentes

**Planos de assinatura (MVP):**
- **Plano único:** R$ [a definir - referência: YNAB ~$99/ano = ~R$ 500/ano = ~R$ 42/mês]
- **Billing:** Mensal
- **Opção anual com desconto:** Post-MVP (ex: 10-20% desconto)

**Funcionalidades do trial:**
- Acesso completo a todas as features MVP
- Sem limitação de transações, contas, ou categorias
- Sem marca d'água ou limitações artificiais
- Email de lembrete 7 dias antes do fim do trial
- Email de lembrete 1 dia antes do fim do trial

**Experiência de conversão:**
- Trial expira → Usuário vê tela de upgrade amigável
- Histórico de dados preservado após conversão
- Nenhuma perda de dados se não converter (dados mantidos por 30 dias adicionais)
- Call-to-action discreto durante trial (não agressivo)

**Requisitos técnicos:**
- Sistema de billing integrado (Stripe recomendado para mercado brasileiro)
- Gerenciamento de status de assinatura (trial, active, canceled, expired)
- Webhook para eventos de pagamento
- Faturamento automático recorrente
- Self-service para cancelamento e reativação
- Tratamento de falha de pagamento (retry, notificação)

**Métricas de monetização (do Product Brief):**
- Taxa de conversão trial→paid: target 15%
- Churn mensal: target < 5%
- 1.000 usuários pagantes em 12 meses

**7. Authentication & User Management**

**Método primário: Social Login**

**Decisão:** Priorizar social login para reduzir fricção no cadastro

**Providers suportados (MVP):**
- **Google** (prioridade 1 - mais usado, confiável)
- **Facebook** (prioridade 2 - cobertura adicional)
- **Email/senha** (fallback para quem não quer social login)

**Justificativa social login:**
- Reduz fricção no cadastro (1 clique vs preencher formulário)
- Elimina necessidade de criar/lembrar senha
- Verificação de email automática (OAuth providers já verificam)
- Menor taxa de abandono no signup

**Fluxo de autenticação:**

**Cadastro (Signup):**
1. Landing page → Botão "Começar trial gratuito"
2. Tela de signup com opções:
   - "Continuar com Google" (botão primário)
   - "Continuar com Facebook"
   - "Usar email e senha" (link discreto)
3. Após OAuth: solicitar nome completo se não disponível
4. Criar conta → Redirecionar para onboarding

**Login:**
1. Tela de login com mesmas opções de signup
2. OAuth ou email/senha
3. Redirecionar para dashboard

**Segurança:**
- **Session management:** JWT tokens com refresh token
- **Session duration:** 7 dias (renovado automaticamente)
- **2FA (Two-Factor Authentication):** Opcional (não obrigatório no MVP)
  - Via app authenticator (Google Authenticator, Authy)
  - Backup codes para recuperação
- **Password requirements** (se usar email/senha):
  - Mínimo 12 caracteres
  - Complexidade: letras, números, caracteres especiais
  - Verificação contra senhas vazadas (Have I Been Pwned API)
- **Password reset:** Via email com token temporário (válido 1 hora)

**Integração com OAuth providers:**
- Usar bibliotecas oficiais (Google OAuth, Facebook Login SDK)
- Solicitar apenas permissões essenciais (email, nome, foto perfil)
- Política de privacidade clara sobre dados obtidos via OAuth
- Permitir desvincular OAuth e adicionar senha tradicional

**Account management:**
- Trocar email
- Adicionar/remover métodos de autenticação
- Ativar/desativar 2FA
- Ver sessões ativas e fazer logout remoto
- Exportar dados (LGPD compliance)
- Deletar conta permanentemente

## Implementation Considerations

**1. Performance Targets**

**Dashboard GPS:**
- Initial load: < 3 segundos (first contentful paint)
- Camada 1 render: < 2 segundos (requisito crítico do produto)
- Navegação entre camadas: < 500ms (percepção instantânea)
- Gráficos interativos: render em < 1 segundo

**Importação OFX/CSV:**
- Upload: < 5 segundos para arquivos até 10MB
- Parsing: < 30 segundos para arquivo de 1 ano de transações
- IA classificação: < 3 segundos para lote de 100 transações
- Feedback de progresso: atualização a cada 2 segundos

**Otimizações necessárias:**
- Lazy loading de componentes pesados
- Virtual scrolling para listas longas de transações
- Debouncing em filtros e buscas
- Memoization de cálculos complexos
- CDN para assets estáticos
- Compression (gzip/brotli)

**2. Responsive Design**

**Desktop first, mobile secondary:**
- **Desktop (1280px+):** Experiência otimizada, 3 camadas visíveis
- **Tablet (768-1279px):** Adaptado, navegação ajustada
- **Mobile (< 768px):** Simplificado, foco em consultas rápidas

**MVP mobile:**
- Interface responsiva básica
- Funcionalidades principais acessíveis
- Não otimizado para registro/classificação complexa (desktop preferível)

**Futuro (mobile app nativo):**
- Experiência otimizada para mobile
- Notificações push
- Registro rápido de gastos

**3. Progressive Web App (PWA) Considerations**

**MVP: Não implementar PWA completo**
- Adiciona complexidade sem valor claro no início
- Web app responsivo suficiente

**Futuro (considerar para post-MVP):**
- Service worker para offline caching básico
- Manifest para "Add to Home Screen"
- Melhora percepção de app nativo sem desenvolver mobile app
- Notificações push via web (alternativa a WhatsApp)

**4. Data Sync & Offline Support**

**MVP: Online-only**
- Requer conexão para todas as operações
- Mensagem clara se offline

**Futuro (quando justificado):**
- Caching de dashboard para visualização offline
- Queue de ações para sincronizar quando online
- Conflict resolution para edições offline

**5. Browser Storage**

**LocalStorage/SessionStorage:**
- Preferências de UI (tema, visualizações favoritas)
- Estado temporário de formulários
- Cache de dados não sensíveis

**Não armazenar:**
- Tokens de autenticação em LocalStorage (usar httpOnly cookies)
- Dados financeiros sensíveis sem criptografia
- Senhas ou credenciais

**IndexedDB (futuro):**
- Cache de transações para performance
- Suporte offline robusto
- Sync com servidor quando online
