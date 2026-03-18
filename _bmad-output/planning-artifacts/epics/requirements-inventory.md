# Requirements Inventory

## Functional Requirements

**User Account Management:**
- **FR1:** Usuários podem se cadastrar usando Google OAuth
- **FR2:** Usuários podem se cadastrar usando email e senha
- **FR3:** Usuários podem fazer login usando qualquer método de autenticação configurado
- **FR4:** Usuários podem recuperar acesso via reset de senha por email
- **FR4:** Usuários podem trocar seu email de conta
- **FR4:** Usuários podem adicionar/remover métodos de autenticação
- **FR4:** Usuários podem visualizar sessões ativas e fazer logout remoto
- **FR4:** Usuários podem exportar todos os seus dados (LGPD compliance)
- **FR4:** Usuários podem solicitar exclusão permanente de conta e dados

**Subscription & Billing:**
- **FR4:** Novos usuários recebem automaticamente trial gratuito de 30 dias
- **FR4:** Usuários podem assinar plano mensal após trial
- **FR4:** Sistema envia lembretes 7 dias e 1 dia antes do fim do trial
- **FR4:** Usuários podem cancelar assinatura a qualquer momento (self-service)
- **FR4:** Usuários podem reativar assinatura cancelada
- **FR4:** Sistema processa pagamentos recorrentes automaticamente
- **FR4:** Sistema notifica usuários sobre falhas de pagamento e tenta cobrar novamente

**Onboarding & Setup:**
- **FR4:** Sistema guia novos usuários através de onboarding explicando conceito de orçamento por envelope
- **FR4:** Usuários podem adicionar múltiplas contas bancárias durante setup
- **FR4:** Usuários podem adicionar múltiplos cartões de crédito durante setup
- **FR4:** Sistema sugere categorias de gastos iniciais baseadas em padrões comuns
- **FR4:** Usuários podem personalizar categorias sugeridas durante setup
- **FR4:** Sistema guia primeira importação de arquivo bancário
- **FR4:** Sistema explica as 3 camadas do Dashboard GPS durante onboarding
- **FR4:** Usuários podem criar meta inicial (Reserva de Emergência) durante setup

**Account & Card Management:**
- **FR4:** Usuários podem cadastrar múltiplas contas bancárias com nome, tipo (corrente/poupança) e saldo inicial
- **FR4:** Usuários podem cadastrar múltiplos cartões de crédito com nome, limite e dia de vencimento
- **FR4:** Usuários podem editar informações de contas e cartões
- **FR4:** Usuários podem desativar ou remover contas e cartões
- **FR4:** Usuários podem registrar transferências entre contas próprias
- **FR4:** Usuários podem reconciliar saldo registrado com saldo real do banco
- **FR4:** Sistema mantém histórico de saldos por conta ao longo do tempo

**Transaction Import & Classification:**
- **FR4:** Usuários podem fazer upload de arquivos OFX dos 5 principais bancos brasileiros (Nubank, BB, Itaú, Bradesco, Caixa)
- **FR4:** Usuários podem fazer upload de arquivos CSV dos 5 principais bancos brasileiros
- **FR4:** Sistema detecta automaticamente formato e banco do arquivo importado
- **FR4:** Sistema faz parsing de transações com tratamento robusto de erros
- **FR4:** Sistema detecta e sinaliza transações duplicadas
- **FR4:** Sistema identifica transações Pix e extrai origem/destino quando disponível
- **FR4:** Sistema detecta compras parceladas e divide corretamente entre meses futuros
- **FR4:** Sistema classifica transações automaticamente usando IA
- **FR4:** Sistema apresenta transações classificadas para revisão do usuário
- **FR4:** Usuários podem revisar e ajustar classificações sugeridas pela IA
- **FR4:** Sistema aprende com correções do usuário para melhorar classificações futuras
- **FR4:** Usuários podem editar manualmente qualquer transação (data, valor, descrição, categoria)
- **FR4:** Usuários podem adicionar transações manualmente (para gastos em dinheiro)

**Budget & Category Management:**
- **FR4:** Usuários podem criar categorias personalizadas de gastos
- **FR4:** Usuários podem organizar categorias em grupos hierárquicos
- **FR4:** Usuários podem definir quanto dinheiro alocar para cada categoria no início do mês
- **FR4:** Sistema mostra saldo disponível em cada categoria em tempo real
- **FR4:** Usuários podem realocar dinheiro entre categorias quando necessário
- **FR4:** Quando uma transação é classificada, sistema deduz automaticamente da categoria correspondente
- **FR4:** Sistema sinaliza visualmente categorias com saldo baixo ou negativo
- **FR4:** Usuários podem visualizar histórico de alocações por categoria
- **FR4:** Sistema mostra comparação entre planejado e real por categoria

**Dashboard GPS - Layer 1 (Overview):**
- **FR4:** Sistema apresenta 5 métricas essenciais em tela única (Camada 1)
- **FR4:** Dashboard Camada 1 mostra saldo total disponível em todas as contas
- **FR4:** Dashboard Camada 1 mostra status visual de categorias (verde/amarelo/vermelho)
- **FR4:** Dashboard Camada 1 mostra progresso da meta principal (Reserva de Emergência)
- **FR4:** Dashboard Camada 1 mostra gastos do mês vs planejado
- **FR4:** Dashboard Camada 1 mostra projeção de fim de mês baseado em ritmo atual
- **FR4:** Dashboard Camada 1 responde as 3 perguntas: Onde estou? Para onde vou? Preciso ajustar?

**Dashboard GPS - Layer 2 (Tactical):**
- **FR4:** Usuários podem acessar Camada 2 para visão tática detalhada
- **FR4:** Dashboard Camada 2 mostra gráficos de gastos por categoria
- **FR4:** Dashboard Camada 2 mostra comparação planejado vs real por categoria
- **FR4:** Dashboard Camada 2 mostra tendências temporais (últimos 3/6/12 meses)
- **FR4:** Usuários podem fazer drill-down em categorias específicas

**Dashboard GPS - Layer 3 (Details):**
- **FR4:** Usuários podem acessar Camada 3 para detalhes completos
- **FR4:** Dashboard Camada 3 mostra lista de todas as transações com filtros
- **FR4:** Usuários podem filtrar transações por período, categoria, conta, ou texto
- **FR4:** Usuários podem ordenar transações por data, valor, ou categoria
- **FR4:** Usuários podem editar transações individualmente na Camada 3
- **FR4:** Usuários podem exportar dados em formato CSV

**Financial Goals Management:**
- **FR4:** Usuários podem criar múltiplas metas financeiras simultâneas
- **FR4:** Para cada meta, usuários podem definir nome, valor alvo e prazo
- **FR4:** Usuários podem alocar recursos mensais específicos para cada meta
- **FR4:** Sistema visualiza progresso de cada meta com barra de preenchimento e curva temporal
- **FR4:** Sistema projeta data de cumprimento baseada em alocação mensal atual
- **FR4:** Usuários podem ajustar alocação entre metas
- **FR4:** Sistema sugere metas pré-configuradas (Reserva de Emergência, Férias, Compra Grande)
- **FR4:** Usuários podem marcar metas como concluídas
- **FR4:** Sistema mantém histórico de progresso de metas ao longo do tempo

**Reports & Data Export:**
- **FR4:** Usuários podem exportar relatório de transações em formato CSV
- **FR4:** Usuários podem exportar relatório de categorias e orçamento em formato CSV
- **FR4:** Usuários podem exportar todos os dados da conta para portabilidade (LGPD)

**Admin Operations (Post-MVP v1.1):**
- **FR4:** Administradores podem visualizar métricas de health do sistema
- **FR4:** Administradores podem visualizar taxa de erro de importação por banco
- **FR4:** Administradores podem visualizar precisão da IA por banco
- **FR4:** Administradores podem visualizar número de usuários ativos
- **FR4:** Sistema envia alertas automáticos para administradores quando métricas críticas são violadas

## NonFunctional Requirements

**Performance:**

**Response Time Requirements:**
- **NFR1:** Dashboard GPS Camada 1 carrega em < 2 segundos (requisito crítico do diferencial do produto)
- **NFR2:** Navegação entre camadas do Dashboard GPS ocorre em < 500ms (percepção instantânea)
- **NFR3:** Gráficos interativos na Camada 2 renderizam em < 1 segundo
- **NFR4:** Initial page load (first contentful paint) ocorre em < 3 segundos

**Processing Time Requirements:**
- **NFR4:** Upload de arquivo OFX/CSV (até 10MB) completa em < 5 segundos
- **NFR4:** Parsing de arquivo OFX/CSV (1 ano de transações) completa em < 30 segundos
- **NFR4:** IA classifica lote de 100 transações em < 3 segundos
- **NFR4:** Sistema atualiza feedback de progresso de importação a cada 2 segundos

**Database Performance:**
- **NFR4:** Dashboard GPS mantém performance < 2s mesmo com 5 anos de histórico de transações
- **NFR4:** Queries de transações com filtros retornam em < 1 segundo para até 10.000 registros

**Security:**

**Data Protection:**
- **NFR4:** Todos os dados financeiros sensíveis (transações, saldos, contas) são criptografados em repouso usando AES-256
- **NFR4:** Todas as comunicações HTTPS usam TLS 1.3
- **NFR4:** Senhas de usuários são hasheadas usando bcrypt com salt
- **NFR4:** Tokens de sessão são armazenados em httpOnly cookies (não LocalStorage)
- **NFR4:** Dados de diferentes usuários são completamente segregados (um usuário nunca acessa dados de outro)

**Authentication & Authorization:**
- **NFR4:** Sessions expiram após 7 dias de inatividade
- **NFR4:** Rate limiting implementado em endpoints de autenticação (máximo 5 tentativas de login por minuto)
- **NFR4:** Password reset tokens expiram após 1 hora

**Security Monitoring:**
- **NFR4:** Todas as operações sensíveis (login, mudança de senha, exclusão de conta, exportação de dados) são registradas em logs de auditoria com timestamp e IP
- **NFR4:** Tentativas de acesso não autorizado são detectadas e bloqueadas automaticamente
- **NFR4:** Sistema é testado contra vulnerabilidades OWASP Top 10 (SQL Injection, XSS, CSRF, etc.)

**Compliance:**
- **NFR4:** Sistema atende todos os requisitos da LGPD (consentimento, direito a exclusão, portabilidade)
- **NFR4:** Backups automáticos são criptografados
- **NFR4:** Sistema mantém logs de consentimento do usuário

**Scalability:**

**User Load:**
- **NFR4:** Sistema suporta 1.000 usuários simultâneos sem degradação de performance > 10%
- **NFR4:** Sistema escala horizontalmente para suportar 10x crescimento de usuários (10.000) sem refactor arquitetural
- **NFR4:** Performance de resposta se mantém estável com crescimento de 50% ao mês

**Data Growth:**
- **NFR4:** Sistema processa eficientemente queries mesmo com 100.000+ transações por usuário
- **NFR4:** Storage escalável para suportar crescimento de dados sem limites artificiais

**Infrastructure:**
- **NFR4:** Arquitetura cloud permite provisionamento automático de recursos baseado em demanda
- **NFR4:** Sistema pode escalar verticalmente e horizontalmente sem downtime

**Reliability & Availability:**

**Uptime:**
- **NFR4:** Sistema mantém 99.5% uptime mensal (máximo ~3.6 horas de downtime por mês)
- **NFR4:** Manutenções planejadas ocorrem fora de horários de pico e são comunicadas com 72h de antecedência

**Data Integrity:**
- **NFR4:** Zero perda de dados em caso de falha (backup automático diário)
- **NFR4:** Backups são testados mensalmente para garantir recuperação funcional
- **NFR4:** Retenção de backups de 30 dias mínimo
- **NFR4:** Recovery Time Objective (RTO): < 4 horas
- **NFR4:** Recovery Point Objective (RPO): < 24 horas

**Error Handling:**
- **NFR4:** Sistema lida gracefully com falhas de serviços externos (bancos, Stripe, OAuth)
- **NFR4:** Mensagens de erro são claras e incluem sugestões de ação quando possível
- **NFR4:** Sistema se recupera automaticamente de falhas transientes

**Accessibility:**

**WCAG 2.1 Level AA Compliance:**
- **NFR4:** Contraste de cores mínimo 4.5:1 para texto normal, 3:1 para texto grande
- **NFR4:** Toda funcionalidade é acessível via teclado sem armadilhas de foco
- **NFR4:** Ordem de foco (tab order) é lógica e previsível
- **NFR4:** Markup HTML semântico válido em todas as páginas
- **NFR4:** Roles ARIA apropriados implementados em componentes customizados
- **NFR4:** Sistema é compatível com screen readers (NVDA, JAWS, VoiceOver)
- **NFR4:** Gráficos e visualizações incluem alternativas textuais descritivas
- **NFR4:** Lighthouse accessibility audit score > 90

**Usability:**
- **NFR4:** Linguagem clara e simples, evitando jargão técnico desnecessário
- **NFR4:** Labels claros em todos os campos de formulário
- **NFR4:** Mensagens de erro claras com sugestões de correção
- **NFR4:** Navegação consistente entre páginas

**Integration:**

**Banking File Formats:**
- **NFR4:** Sistema suporta formatos OFX e CSV dos 5 principais bancos brasileiros com parsing robusto
- **NFR4:** Detecção automática de formato de arquivo com 95%+ de precisão
- **NFR4:** Sistema trata gracefully arquivos malformados com mensagens de erro claras

**Payment Processing:**
- **NFR4:** Integração com Stripe processa pagamentos com 99.9% de confiabilidade
- **NFR4:** Webhooks de pagamento são processados com retry automático em caso de falha

**Authentication Providers:**
- **NFR4:** OAuth integrations (Google) seguem padrões oficiais e best practices
- **NFR4:** Falhas de OAuth providers são tratadas com fallback para login tradicional

**API Design (futuro):**
- **NFR4:** APIs futuras seguem padrões REST com documentação OpenAPI
- **NFR4:** Rate limiting implementado para proteger infraestrutura

**Maintainability:**

**Code Quality:**
- **NFR4:** Código segue style guides definidos para cada linguagem
- **NFR4:** Cobertura de testes unitários > 70% para lógica crítica (IA classificação, cálculos financeiros)
- **NFR4:** Testes de integração cobrem fluxos principais (onboarding, importação, classificação)

**Monitoring & Observability:**
- **NFR4:** Sistema envia métricas de performance para monitoring (Datadog, Sentry ou similar)
- **NFR4:** Erros críticos geram alertas automáticos para equipe
- **NFR4:** Logs estruturados permitem debugging eficiente
- **NFR4:** Dashboard de métricas de negócio (usuários ativos, precisão IA, taxa de erro por banco)

**Deployment:**
- **NFR4:** CI/CD pipeline automatizado para deploy contínuo
- **NFR4:** Rollback de deploy pode ser executado em < 15 minutos
- **NFR4:** Zero-downtime deployments quando possível

**Usability:**

**Onboarding:**
- **NFR4:** 70%+ dos novos usuários completam onboarding completo
- **NFR4:** Tempo médio de onboarding < 20 minutos

**Core User Experience:**
- **NFR4:** Check-in semanal completo (importar + revisar + ver dashboard) é completável em < 5 minutos
- **NFR4:** Interface é responsiva e funcional em desktop (1280px+), tablet (768px+) e mobile (< 768px)
- **NFR4:** Sistema funciona nos 4 principais navegadores modernos (Chrome, Firefox, Safari, Edge - últimas 2 versões)

## Additional Requirements

**Starter Template (Architecture):**
- **ARCH-REQ-1:** Frontend deve ser inicializado usando `npm create vue@latest` (Vue 3.5+ com Vite 6)
- **ARCH-REQ-2:** Backend deve ser inicializado usando Spring Initializr (Spring Boot 3.4.x ou 4.0.3 com JDK 21 LTS)
- **ARCH-REQ-3:** Arquitetura dual separada: Frontend Vue.js SPA + Backend Java Spring Boot REST API

**Database & Data Management:**
- **ARCH-REQ-4:** PostgreSQL 16+ via Supabase como database principal
- **ARCH-REQ-5:** Flyway para database migrations (SQL-based, versionamento explícito)
- **ARCH-REQ-6:** Supabase Auth service para Google OAuth built-in
- **ARCH-REQ-7:** Row Level Security (RLS) para segregação completa de dados entre usuários
- **ARCH-REQ-8:** Soft deletes obrigatórios (deleted_at, deleted_by) para compliance LGPD
- **ARCH-REQ-9:** Todas as queries devem filtrar por user_id do JWT + deleted_at IS NULL

**Caching & Performance:**
- **ARCH-REQ-10:** Redis para caching de Dashboard GPS
- **ARCH-REQ-11:** Dashboard Layer 1: TTL 30s, key pattern `dashboard:layer1:{userId}`
- **ARCH-REQ-12:** Dashboard Layer 2: TTL 5min, key pattern `dashboard:layer2:{userId}`
- **ARCH-REQ-13:** Dashboard Layer 3: TTL 15min, key pattern `dashboard:layer3:{userId}`
- **ARCH-REQ-14:** Invalidar cache em: transaction classify, budget allocate

**Infrastructure & Deployment:**
- **ARCH-REQ-15:** Deployment em VPS Hostinger usando Dokploy (Docker orchestration)
- **ARCH-REQ-16:** Frontend deve ser containerizado com Nginx (multi-stage build)
- **ARCH-REQ-17:** Backend deve ser containerizado com OpenJDK 21
- **ARCH-REQ-18:** Redis container para cache
- **ARCH-REQ-19:** Nginx reverse proxy para roteamento
- **ARCH-REQ-20:** CI/CD pipeline automatizado para deploy contínuo

**Security Implementation:**
- **ARCH-REQ-21:** Spring Security + OAuth2 Client para autenticação
- **ARCH-REQ-22:** JWT validation usando Supabase issuer URI
- **ARCH-REQ-23:** GlobalExceptionHandler com RFC 7807 Problem Details para erros
- **ARCH-REQ-24:** CORS configurado para domínio específico (não wildcard)
- **ARCH-REQ-25:** Audit logging para todas operações sensíveis

**Code Structure & Patterns:**
- **ARCH-REQ-26:** Backend: Controller → Service → Repository → Database (nunca pular camadas)
- **ARCH-REQ-27:** Frontend: Views → Components → Composables → Stores → Services → API
- **ARCH-REQ-28:** Database naming: snake_case (user_id, created_at)
- **ARCH-REQ-29:** JSON/API naming: camelCase (userId, createdAt)
- **ARCH-REQ-30:** Code naming: PascalCase (classes), camelCase (methods/variables)
- **ARCH-REQ-31:** API resources: Plural (/transactions, /goals, /categories)

**Project Setup Priority:**
- **ARCH-REQ-32:** Story 0 (Project Setup) deve inicializar ambos projetos (frontend e backend)
- **ARCH-REQ-33:** Configurar Supabase project e obter connection strings
- **ARCH-REQ-34:** Criar Flyway migrations iniciais (V1-V8) para todas as tabelas principais
- **ARCH-REQ-35:** Configurar environment variables (.env) para ambos projetos

## UX Design Requirements

**Design System Implementation:**
- **UX-DR1:** Instalar e configurar Tailwind CSS + PostCSS no projeto Vue
- **UX-DR2:** Instalar shadcn-vue CLI e inicializar com estilo "Default" e cor base Purple
- **UX-DR3:** Configurar tailwind.config.js com design tokens: cores, tipografia, espaçamento, breakpoints
- **UX-DR4:** Copiar componentes base do shadcn-vue: Button, Input, Card, Select, Dialog, Dropdown, Tooltip

**Color System Implementation:**
- **UX-DR5:** Implementar paleta de cores neutras (Gray 50-900) para 80% da UI
- **UX-DR6:** Implementar cor primária Purple 600 (#9333EA) para CTAs, links e elementos interativos
- **UX-DR7:** Implementar cores de estado semáforo: Green 600, Yellow 600, Red 600
- **UX-DR8:** Implementar 6 cores de gráficos vibrantes (Chart 1-6) para visualizações de dados
- **UX-DR9:** Validar contraste de cores WCAG 2.1 AA (mínimo 4.5:1 para texto normal)

**Typography Implementation:**
- **UX-DR10:** Configurar fonte Inter (sans-serif) como typeface principal via Google Fonts ou local
- **UX-DR11:** Implementar type scale usando Tailwind defaults (text-xs a text-5xl)
- **UX-DR12:** Definir weights: 400 (regular), 500 (medium), 600 (semibold), 700 (bold)
- **UX-DR13:** Estabelecer hierarquia tipográfica por tamanho + weight (não cor)

**Spacing & Layout:**
- **UX-DR14:** Usar base de espaçamento 4px (sistema Tailwind padrão)
- **UX-DR15:** Aplicar filosofia "denso mas respirável": Cards com p-6, gap-4
- **UX-DR16:** Dashboard camadas com py-12 de espaçamento entre camadas
- **UX-DR17:** Border radius padrão: rounded-lg (12px)

**Responsive Design Implementation:**
- **UX-DR18:** Desktop (≥1280px) como layout primário: multi-column em camadas 2-3
- **UX-DR19:** Tablet (768-1279px): Single column com stack progressivo
- **UX-DR20:** Mobile (<768px): Single column com priorização de semáforo + gráfico principal

**Accessibility Implementation:**
- **UX-DR21:** Garantir todos touch targets ≥44x44px para mobile
- **UX-DR22:** Implementar keyboard navigation completa (tab order lógico, sem focus traps)
- **UX-DR23:** Adicionar ARIA roles apropriados em componentes customizados
- **UX-DR24:** Implementar markup HTML semântico válido
- **UX-DR25:** Garantir screen reader support (testar com NVDA/JAWS/VoiceOver)
- **UX-DR26:** Adicionar alternativas textuais descritivas para gráficos e visualizações
- **UX-DR27:** Atingir Lighthouse accessibility score > 90

**Componentes Customizados (MVP Priority):**
- **UX-DR28:** Criar componente Financial Health Semaphore (status 🟢🟡🔴 sticky no topo)
- **UX-DR29:** Criar componente Dashboard GPS Container (scroll vertical com 3 camadas progressivas)
- **UX-DR30:** Criar componente Wealth Growth Chart (curva de patrimônio animada)
- **UX-DR31:** Criar componente Goal Progress Bar (barra de meta com milestones visuais)
- **UX-DR32:** Criar componente AI Classification Badge (mostra nível de confiança da IA)
- **UX-DR33:** Criar componente Insight Reveal Card (card para insights surpresa semanais)

**Dashboard GPS Experience:**
- **UX-DR34:** Implementar navegação por scroll vertical (não tabs ou clicks excessivos)
- **UX-DR35:** Layer 1 deve responder às 3 perguntas em ≤30 segundos de visualização
- **UX-DR36:** Implementar transitions suaves entre camadas (< 500ms conforme NFR2)
- **UX-DR37:** Semáforo de saúde financeira deve ser sticky no topo durante scroll
- **UX-DR38:** Gráficos devem dominar o espaço (80%), UI discreta (20%)

**Visual Design Philosophy:**
- **UX-DR39:** Aplicar filosofia "UI Neutra, Dados Vibrantes" em toda interface
- **UX-DR40:** Minimizar chrome de UI e componentes ornamentais
- **UX-DR41:** Priorizar visualizações de dados sobre texto descritivo
- **UX-DR42:** Manter espaçamento generoso para evitar sensação de "muito cheio"

**Interaction Patterns:**
- **UX-DR43:** Implementar drag-and-drop para upload de arquivos OFX/CSV
- **UX-DR44:** Feedback de progresso atualizado a cada 2 segundos durante importação (NFR8)
- **UX-DR45:** Toast notifications para erros com Axios interceptors
- **UX-DR46:** Loading states com skeleton screens (não spinners isolados)

## FR Coverage Map

**Epic 0: Project Foundation & Technical Setup**
- ARCH-REQ-1 a ARCH-REQ-35: Todos os requisitos de arquitetura
- UX-DR-1 a UX-DR-27: Foundation de design system, cores, tipografia, acessibilidade

**Epic 1: User Authentication & Account Management**
- FR1: Cadastro com Google OAuth
- FR2: Cadastro com email e senha
- FR3: Login com qualquer método configurado
- FR4: Recuperação de senha por email
- FR4: Troca de email de conta
- FR4: Adicionar/remover métodos de autenticação
- FR4: Visualizar sessões ativas e logout remoto
- FR4: Exportar dados (LGPD compliance)
- FR4: Solicitar exclusão permanente de conta e dados

**Epic 2: Subscription & Billing System**
- FR4: Trial gratuito automático de 30 dias
- FR4: Assinatura de plano mensal
- FR4: Lembretes antes do fim do trial
- FR4: Cancelamento self-service de assinatura
- FR4: Reativação de assinatura cancelada
- FR4: Processamento automático de pagamentos recorrentes
- FR4: Notificações de falhas de pagamento e retry

**Epic 3: Guided Onboarding Experience**
- FR4: Onboarding com explicação de orçamento por envelope
- FR4: Adicionar múltiplas contas bancárias durante setup
- FR4: Adicionar múltiplos cartões durante setup
- FR4: Sugestão de categorias iniciais
- FR4: Personalização de categorias sugeridas
- FR4: Primeira importação guiada de arquivo bancário
- FR4: Explicação das 3 camadas do Dashboard GPS
- FR4: Criação de meta inicial (Reserva de Emergência)

**Epic 4: Financial Accounts & Cards Management**
- FR4: Cadastrar múltiplas contas bancárias
- FR4: Cadastrar múltiplos cartões de crédito
- FR4: Editar informações de contas e cartões
- FR4: Desativar ou remover contas e cartões
- FR4: Registrar transferências entre contas próprias
- FR4: Reconciliar saldo registrado com saldo real
- FR4: Manter histórico de saldos ao longo do tempo

**Epic 5: Transaction Import & AI Classification**
- FR4: Upload de arquivos OFX dos 5 principais bancos brasileiros
- FR4: Upload de arquivos CSV dos 5 principais bancos brasileiros
- FR4: Detecção automática de formato e banco
- FR4: Parsing de transações com tratamento de erros
- FR4: Detecção e sinalização de duplicatas
- FR4: Identificação de transações Pix
- FR4: Detecção de compras parceladas
- FR4: Classificação automática de transações usando IA
- FR4: Apresentação de transações classificadas para revisão
- FR4: Revisão e ajuste de classificações pela IA
- FR4: Aprendizado com correções do usuário
- FR4: Edição manual de qualquer transação
- FR4: Adição manual de transações (dinheiro)

**Epic 6: Budget & Category System (Envelope Method)**
- FR4: Criação de categorias personalizadas
- FR4: Organização de categorias em hierarquia
- FR4: Definição de alocação mensal por categoria
- FR4: Visualização de saldo disponível em tempo real
- FR4: Realocação de dinheiro entre categorias
- FR4: Dedução automática ao classificar transações
- FR4: Sinalização visual de categorias com saldo baixo/negativo
- FR4: Visualização de histórico de alocações
- FR4: Comparação entre planejado e real por categoria

**Epic 7: Dashboard GPS - Complete Financial Overview**
- FR4: Apresentação de 5 métricas essenciais (Camada 1)
- FR4: Saldo total disponível em todas as contas
- FR4: Status visual de categorias (verde/amarelo/vermelho)
- FR4: Progresso da meta principal (Reserva de Emergência)
- FR4: Gastos do mês vs planejado
- FR4: Projeção de fim de mês baseado em ritmo atual
- FR4: Resposta às 3 perguntas GPS
- FR4: Acesso a Camada 2 para visão tática
- FR4: Gráficos de gastos por categoria
- FR4: Comparação planejado vs real por categoria
- FR4: Tendências temporais (3/6/12 meses)
- FR4: Drill-down em categorias específicas
- FR4: Acesso a Camada 3 para detalhes completos
- FR4: Lista de todas as transações com filtros
- FR4: Filtros por período, categoria, conta, texto
- FR4: Ordenação por data, valor, categoria
- FR4: Edição individual de transações na Camada 3
- FR4: Exportação de dados em CSV
- UX-DR-28 a UX-DR-38: Componentes customizados do Dashboard GPS

**Epic 8: Financial Goals Management**
- FR4: Criação de múltiplas metas financeiras
- FR4: Definição de nome, valor alvo e prazo por meta
- FR4: Alocação de recursos mensais específicos por meta
- FR4: Visualização de progresso com barra e curva temporal
- FR4: Projeção de data de cumprimento
- FR4: Ajuste de alocação entre metas
- FR4: Sugestão de metas pré-configuradas
- FR4: Marcação de metas como concluídas
- FR4: Histórico de progresso de metas

**Epic 9: Reports & Data Export**
- FR4: Exportação de relatório de transações em CSV
- FR4: Exportação de relatório de categorias e orçamento em CSV
- FR4: Exportação completa de dados da conta (LGPD)

**Epic 10: Admin Operations & System Monitoring**
- FR4: Visualização de métricas de health do sistema
- FR4: Visualização de taxa de erro de importação por banco
- FR4: Visualização de precisão da IA por banco
- FR4: Visualização de número de usuários ativos
- FR4: Alertas automáticos para métricas críticas
