# Project Scoping & Phased Development

## MVP Strategy & Philosophy

**MVP Approach:** **Experience MVP**

**Justificativa:**
- Produto financeiro exige confiança desde o primeiro uso
- Dashboard GPS motivador é o diferencial core - precisa funcionar bem desde o início
- Usuários comparam com YNAB e outras ferramentas - experiência precisa ser superior, não apenas funcional
- Classificação automática por IA precisa ser precisa (85%+) ou frustra ao invés de ajudar
- Trial de 30 dias exige que usuário sinta valor real para converter

**Trade-off aceito:**
- Desenvolvimento mais longo que "problem-solving MVP"
- Maior investimento inicial em design e polimento
- Benefício: maior chance de retenção e conversão trial→paid

**Princípio central:** "Melhor lançar com menos features mas experiência excelente do que muitas features com experiência frustrante"

## MVP Scope Adjustments

**Baseado na validação estratégica, ajustes ao escopo original:**

**✅ Mantido no MVP:**
1. Sistema de Orçamento por Envelope (essencial - core concept)
2. Classificação Automática por IA (essencial - reduz esforço)
3. Dashboard GPS Financeiro 3 Camadas (essencial - diferencial core)
4. Sistema de Metas Financeiras - **múltiplas metas** (não apenas reserva)
5. Gestão de Contas e Cartões (essencial - funcionalidade base)
6. Segurança e Compliance Fintech (essencial - não negociável)

**🔄 Movido para v1.1 (Post-MVP Imediato):**
- **Insights Surpresa Semanais** → Pode ser adicionado após validação inicial
  - Motivo: Dashboard GPS já entrega visualização motivadora
  - Insights são enhancement, não essencial para provar conceito
  - Permite lançar mais rápido mantendo experiência core sólida

**🔄 Movido para Post-MVP:**
- **Admin Dashboard completo** → Adicionar quando houver usuários reais para monitorar
  - Motivo: Sem usuários, não há o que monitorar
  - MVP pode usar ferramentas externas (Sentry, Datadog) temporariamente
  - Funcionalidades essenciais de admin podem ser via scripts/queries diretos

## MVP Feature Set (Phase 1)

**Core User Journeys Supported:**
- ✅ Usuário primário: Onboarding → Setup → Importação → Classificação → Dashboard GPS → Metas
- ✅ Check-in semanal: Importar → Revisar → Ver progresso
- ⚠️ Familiar observador: Apenas via login compartilhado (sem permissões diferenciadas)
- ❌ Admin interno: Via ferramentas externas + scripts temporários

**Must-Have Capabilities (MVP):**

**1. Sistema de Orçamento por Envelope:**
- Criação de categorias personalizadas
- Alocação de dinheiro entre categorias
- Consumo de categorias ao gastar
- Realocação entre categorias
- Visualização de saldo disponível por categoria
- Histórico de alocações

**2. Classificação Automática por IA:**
- Importação OFX/CSV (5 principais bancos brasileiros)
- Parsing robusto com tratamento de erros
- Classificação automática com 85%+ de precisão após 1 mês
- Interface de revisão rápida (2 minutos para semana)
- Feedback loop: usuário corrige → IA aprende
- Suporte a Pix e parcelamento sem juros
- Detecção de duplicatas

**3. Dashboard GPS Financeiro (3 Camadas):**
- **Camada 1:** 5 métricas essenciais, visão em 30 segundos
  - Saldo total disponível
  - Status de categorias (verde/amarelo/vermelho)
  - Progresso de meta principal (reserva)
  - Gastos do mês vs planejado
  - Projeção fim de mês
- **Camada 2:** Visão tática
  - Gastos por categoria (gráficos)
  - Comparação planejado vs real
  - Tendências temporais
  - Drill-down por categoria
- **Camada 3:** Detalhes completos
  - Transações individuais
  - Histórico completo filtrado
  - Edição manual de transações
  - Exportação de dados

**4. Sistema de Metas Financeiras (Múltiplas Metas):**
- Criar múltiplas metas simultâneas
- Definir valor alvo e prazo
- Alocar recursos mensais para cada meta
- Visualização de progresso (barras, curvas)
- Projeção de cumprimento ("no ritmo atual, alcança em X meses")
- Ajustar alocação entre metas
- Metas pré-configuradas sugeridas: Reserva de Emergência, Férias, Compra Grande

**5. Gestão de Contas e Cartões:**
- Cadastro de múltiplas contas bancárias
- Cadastro de múltiplos cartões de crédito
- Controle de saldo por conta
- Transferências entre contas
- Reconciliação de saldo (conferir com banco)
- Suporte a diferentes tipos: Conta Corrente, Poupança, Cartão de Crédito

**6. Segurança e Compliance Fintech:**
- Autenticação social (Google, Facebook) + email/senha
- Criptografia AES-256 em repouso
- TLS 1.3 em trânsito
- Logs de auditoria
- LGPD compliance: consentimento, direito a exclusão, portabilidade
- Backup automático diário
- Session management seguro (JWT)

**7. Onboarding Guiado:**
- Explicação do conceito de orçamento por envelope
- Setup de contas e cartões
- Sugestão de categorias iniciais
- Primeira importação guiada
- Tutorial do Dashboard GPS (3 camadas)
- Setup de meta principal (Reserva de Emergência)

**Explicitamente FORA do MVP:**
- ❌ Insights surpresa semanais (v1.1)
- ❌ Admin dashboard completo (Post-MVP)
- ❌ Open Banking automático (Growth phase)
- ❌ Integração com investimentos (Growth phase)
- ❌ Mobile app nativo (Growth phase)
- ❌ Multi-usuário com permissões (Growth phase)
- ❌ Notificações WhatsApp (Growth phase)
- ❌ Relatórios avançados (Growth phase)

## Post-MVP Features

**Phase 1.1 (Immediate Post-MVP - 1-2 meses após lançamento):**

**Objetivo:** Adicionar elementos de engajamento sem comprometer estabilidade MVP

**Features:**
- **Insights Surpresa Semanais:**
  - Análise automática de padrões ("Você gastou R$ X em Y")
  - Comparações vs média pessoal
  - Alertas de desvios significativos
  - Tom motivador, não punitivo

- **Admin Dashboard Básico:**
  - Métricas de health do sistema (uptime, performance)
  - Taxa de erro de importação por banco
  - Precisão da IA por banco
  - Usuários ativos
  - Alertas críticos

**Phase 2 (Growth - 6-12 meses após MVP):**

**Objetivo:** Reduzir fricção e expandir funcionalidades

**Features:**
- **Open Banking Automático:**
  - Sincronização automática sem upload manual
  - Integração com APIs Open Banking Brasil
  - Reduz esforço de importação a zero

- **Mobile App Nativo:**
  - Consulta rápida de saldos
  - Classificação rápida via mobile
  - Notificações push
  - Registro manual de gastos em dinheiro

- **Multi-Usuário Familiar:**
  - Permissões diferenciadas (admin vs visualização)
  - Transparência familiar
  - Histórico de mudanças por usuário

- **Notificações WhatsApp:**
  - Alertas de desvios de orçamento
  - Lembretes de check-in
  - Insights via mensagem

**Phase 3 (Expansion - 12-24 meses após MVP):**

**Objetivo:** Transformar em plataforma financeira completa

**Features:**
- **Integração com Investimentos:**
  - Importação de B3 e corretoras
  - Visão consolidada de patrimônio
  - Tracking de rentabilidade

- **IA de Assistente Financeiro Proativo:**
  - Recomendações personalizadas
  - Previsão de gastos futuros
  - Alertas proativos

- **Planejamento Financeiro Avançado:**
  - Simulações de cenários
  - Planejamento de aposentadoria
  - Análise de viabilidade de objetivos

- **Comunidade e Gamificação:**
  - Sistema de conquistas
  - Desafios de economia
  - Comparação anônima com peers

## Risk Mitigation Strategy

**Technical Risks:**

**Risco #1: IA de Classificação com baixa precisão**
- **Probabilidade:** Média-Alta
- **Impacto:** Alto (frustra usuários, abandono)
- **Mitigação:**
  - Começar com dataset de treino robusto (nomenclaturas brasileiras)
  - Implementar interface de revisão excepcional (fácil e rápida)
  - Nunca executar ações automáticas sem confirmação
  - Monitorar precisão em produção e retreinar continuamente
  - Meta: 85%+ após 1 mês
- **Fallback:** Se precisão < 75%, adicionar regras heurísticas complementares
- **Validação:** Teste com datasets reais de 3 bancos principais antes do lançamento

**Risco #2: Performance do Dashboard GPS com muitos dados**
- **Probabilidade:** Média
- **Impacto:** Alto (requisito crítico: < 2s)
- **Mitigação:**
  - Otimizações desde o início (lazy loading, memoization, virtual scrolling)
  - Testes de carga com 5 anos de transações
  - CDN para assets, compression
  - Database indexing apropriado
- **Fallback:** Limitar visualizações a períodos menores se necessário
- **Validação:** Testes de performance antes do lançamento com datasets reais

**Risco #3: Parsing OFX/CSV com formatos variados**
- **Probabilidade:** Média
- **Impacto:** Médio (bloqueia importação para alguns usuários)
- **Mitigação:**
  - Focar nos 5 principais bancos com testes robustos
  - Tratamento de erro gracioso com mensagens claras
  - Sistema de versionamento de parsers para rollback rápido
  - Monitoramento de taxa de erro por banco
- **Fallback:** Permitir entrada manual temporária se parsing falhar
- **Validação:** Testar com arquivos reais de múltiplos períodos e variações

**Market Risks:**

**Risco #1: Usuários não entendem conceito de orçamento por envelope**
- **Probabilidade:** Média
- **Impacto:** Alto (abandono no onboarding)
- **Mitigação:**
  - Onboarding didático com exemplos práticos brasileiros
  - Vídeos tutoriais curtos
  - Usar metáfora do "porquinho" (cofrinho)
  - Sugestões de categorias pré-configuradas
- **Fallback:** Modo simplificado sem envelope (apenas categorização)
- **Validação:** Taxa de conclusão de onboarding > 70%

**Risco #2: Taxa de conversão trial→paid abaixo do esperado**
- **Probabilidade:** Média
- **Impacto:** Alto (modelo de negócio depende disso)
- **Mitigação:**
  - MVP entrega valor claro em 30 dias (4 check-ins)
  - Onboarding que mostra quick wins
  - Emails de nurturing durante trial
  - Métricas de engajamento para identificar usuários em risco
- **Fallback:** Ajustar duração do trial ou introduzir tier gratuito limitado
- **Validação:** Meta: 15% conversão (acompanhar desde early adopters)

**Risco #3: Competição reage rápido copiando dashboard GPS**
- **Probabilidade:** Baixa-Média
- **Impacto:** Médio (perde diferenciação)
- **Mitigação:**
  - Dashboard GPS requer design thinking profundo (difícil copiar bem)
  - IA contextual brasileira cria barreira de dados
  - Velocidade de iteração com feedback de usuários
  - Construir marca e comunidade forte
- **Fallback:** Continuar inovando em outras áreas (IA proativa, insights)
- **Validação:** Monitorar competição continuamente

**Resource Risks:**

**Risco #1: Time menor que o ideal**
- **Probabilidade:** Baixa (sem prazo rígido)
- **Impacto:** Médio (atrasa lançamento)
- **Mitigação:**
  - Sem prazo rígido permite ajustar velocidade
  - Escopo MVP já enxuto e focado
  - Pode usar serviços terceiros (Stripe, auth providers)
- **Fallback:** Reduzir escopo: simplificar dashboard (2 camadas), apenas Reserva de Emergência
- **Validação:** N/A (flexibilidade de timeline)

**Risco #2: Custos de infraestrutura crescem rápido**
- **Probabilidade:** Baixa
- **Impacto:** Médio
- **Mitigação:**
  - Arquitetura cloud escalável (pay-as-you-grow)
  - Otimizações de performance desde o início
  - Monitoramento de custos vs receita
- **Fallback:** Otimizar queries, caching, reduzir custos de IA (modelos menores)
- **Validação:** Projeções de custo para 100, 1K, 10K usuários

## Resource Requirements

**MVP Team (Ideal):**
- 1 Full-stack Developer (foco backend + infra)
- 1 Frontend Developer (foco UI/UX + dashboard)
- 1 ML/Data Engineer (foco IA classificação)
- 1 Product/Design (part-time - UX, validação)

**MVP Team (Mínimo viável):**
- 1 Full-stack Developer com experiência em ML
- 1 Frontend Developer com eye para design
- Product/Design: o próprio founder (Luiz)

**Timeline Estimado (sem prazo rígido):**
- MVP Development: 4-6 meses (team ideal) ou 6-9 meses (team mínimo)
- Beta Testing: 1-2 meses
- Launch: Após validação beta

**Tecnologias Chave:**
- **Frontend:** React/Vue (SPA), Chart libraries, State management
- **Backend:** Node.js ou Python (FastAPI), PostgreSQL
- **ML:** Python (scikit-learn, TensorFlow/PyTorch para IA classificação)
- **Infra:** Cloud (AWS/GCP), Docker, CI/CD
- **Billing:** Stripe
- **Auth:** OAuth libraries (Google, Facebook)
- **Monitoring:** Sentry, Datadog ou similar
