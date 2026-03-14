# Epic List

## Epic 0: Project Foundation & Technical Setup

Estabelecer a fundação técnica do projeto (frontend Vue, backend Spring Boot, database PostgreSQL, design system Tailwind+shadcn-vue) para permitir desenvolvimento de features com arquitetura dual separada, caching Redis, e compliance de acessibilidade WCAG 2.1 AA.

**FRs covered:** Nenhum FR direto (requisitos de infraestrutura)
**Additional Requirements covered:** ARCH-REQ-1 a ARCH-REQ-35, UX-DR-1 a UX-DR-27

---

## Epic 1: User Authentication & Account Management

Usuários podem se cadastrar usando Google OAuth ou email/senha, fazer login de forma segura, habilitar 2FA opcional, recuperar senha, gerenciar sessões ativas, e exercer seus direitos LGPD (exportar e excluir dados).

**FRs covered:** FR1, FR2, FR3, FR4, FR5, FR6, FR7, FR8, FR9, FR10

---

## Epic 2: Subscription & Billing System

Usuários recebem trial gratuito de 30 dias automaticamente, podem assinar plano mensal após trial, gerenciar assinatura (cancelar/reativar) de forma self-service, e recebem lembretes e notificações sobre status de pagamento.

**FRs covered:** FR11, FR12, FR13, FR14, FR15, FR16, FR17

---

## Epic 3: Guided Onboarding Experience

Novos usuários são guiados através de onboarding interativo que explica o conceito de orçamento por envelope, permite configurar contas bancárias e cartões, sugerir e personalizar categorias iniciais, fazer primeira importação de transações, entender o Dashboard GPS de 3 camadas, e criar meta inicial de Reserva de Emergência.

**FRs covered:** FR18, FR19, FR20, FR21, FR22, FR23, FR24, FR25

---

## Epic 4: Financial Accounts & Cards Management

Usuários podem cadastrar e gerenciar múltiplas contas bancárias (corrente/poupança) e cartões de crédito, editar informações, desativar/remover contas, registrar transferências entre contas próprias, reconciliar saldos com valores reais do banco, e visualizar histórico de saldos ao longo do tempo.

**FRs covered:** FR26, FR27, FR28, FR29, FR30, FR31, FR32

---

## Epic 5: Transaction Import & AI Classification

Usuários podem fazer upload de arquivos OFX/CSV dos 5 principais bancos brasileiros (Nubank, BB, Itaú, Bradesco, Caixa) com detecção automática de formato, parsing robusto, detecção de duplicatas, identificação de Pix e parcelamento, classificação automática por IA, revisão e ajuste de classificações, aprendizado contínuo com correções, e edição/adição manual de transações.

**FRs covered:** FR33, FR34, FR35, FR36, FR37, FR38, FR39, FR40, FR41, FR42, FR43, FR44, FR45

---

## Epic 6: Budget & Category System (Envelope Method)

Usuários podem criar categorias personalizadas de gastos organizadas hierarquicamente, definir alocação mensal de orçamento por categoria, visualizar saldo disponível em tempo real, realocar dinheiro entre categorias quando necessário, ver dedução automática ao classificar transações, receber sinais visuais de categorias com saldo baixo/negativo, visualizar histórico de alocações, e comparar planejado vs real.

**FRs covered:** FR46, FR47, FR48, FR49, FR50, FR51, FR52, FR53, FR54

---

## Epic 7: Dashboard GPS - Complete Financial Overview

Usuários visualizam sua saúde financeira completa através de Dashboard GPS de 3 camadas navegável por scroll vertical: Layer 1 (Overview) com 5 métricas essenciais, semáforo de saúde, e resposta às 3 perguntas GPS em 30 segundos; Layer 2 (Tactical) com gráficos por categoria, tendências temporais, e drill-down; Layer 3 (Details) com lista completa de transações, filtros avançados, ordenação, edição, e exportação CSV. Performance crítica < 2s para Layer 1.

**FRs covered:** FR55, FR56, FR57, FR58, FR59, FR60, FR61, FR62, FR63, FR64, FR65, FR66, FR67, FR68, FR69, FR70, FR71, FR72
**UX Requirements covered:** UX-DR-28 a UX-DR-38 (componentes customizados)

---

## Epic 8: Financial Goals Management

Usuários podem criar múltiplas metas financeiras simultâneas com nome, valor alvo e prazo, alocar recursos mensais específicos por meta, visualizar progresso através de barras de preenchimento e curvas temporais, ver projeção automática de data de cumprimento baseada em alocação atual, ajustar alocação entre metas, usar metas pré-configuradas sugeridas, marcar metas como concluídas, e visualizar histórico de progresso.

**FRs covered:** FR73, FR74, FR75, FR76, FR77, FR78, FR79, FR80, FR81

---

## Epic 9: Reports & Data Export

Usuários podem exportar relatórios de transações em CSV, relatórios de categorias e orçamento em CSV, e exportar todos os dados da conta em formato portável para compliance LGPD e análise externa.

**FRs covered:** FR82, FR83, FR84

---

## Epic 10: Admin Operations & System Monitoring

Administradores podem visualizar métricas de health do sistema, taxa de erro de importação por banco, precisão da IA por banco, número de usuários ativos, e receber alertas automáticos quando métricas críticas são violadas para tomar ações proativas.

**FRs covered:** FR85, FR86, FR87, FR88, FR89
