# Functional Requirements

## User Account Management

- **FR1:** Usuários podem se cadastrar usando Google OAuth
- **FR2:** Usuários podem se cadastrar usando email e senha
- **FR3:** Usuários podem fazer login usando qualquer método de autenticação configurado
- **FR4:** Usuários podem recuperar acesso via reset de senha por email
- **FR4:** Usuários podem trocar seu email de conta
- **FR4:** Usuários podem adicionar/remover métodos de autenticação
- **FR4:** Usuários podem visualizar sessões ativas e fazer logout remoto
- **FR4:** Usuários podem exportar todos os seus dados (LGPD compliance)
- **FR4:** Usuários podem solicitar exclusão permanente de conta e dados

## Subscription & Billing

- **FR4:** Novos usuários recebem automaticamente trial gratuito de 30 dias
- **FR4:** Usuários podem assinar plano mensal após trial
- **FR4:** Sistema envia lembretes 7 dias e 1 dia antes do fim do trial
- **FR4:** Usuários podem cancelar assinatura a qualquer momento (self-service)
- **FR4:** Usuários podem reativar assinatura cancelada
- **FR4:** Sistema processa pagamentos recorrentes automaticamente
- **FR4:** Sistema notifica usuários sobre falhas de pagamento e tenta cobrar novamente

## Onboarding & Setup

- **FR4:** Sistema guia novos usuários através de onboarding explicando conceito de orçamento por envelope
- **FR4:** Usuários podem adicionar múltiplas contas bancárias durante setup
- **FR4:** Usuários podem adicionar múltiplos cartões de crédito durante setup
- **FR4:** Sistema sugere categorias de gastos iniciais baseadas em padrões comuns
- **FR4:** Usuários podem personalizar categorias sugeridas durante setup
- **FR4:** Sistema guia primeira importação de arquivo bancário
- **FR4:** Sistema explica as 3 camadas do Dashboard GPS durante onboarding
- **FR4:** Usuários podem criar meta inicial (Reserva de Emergência) durante setup

## Account & Card Management

- **FR4:** Usuários podem cadastrar múltiplas contas bancárias com nome, tipo (corrente/poupança) e saldo inicial
- **FR4:** Usuários podem cadastrar múltiplos cartões de crédito com nome, limite e dia de vencimento
- **FR4:** Usuários podem editar informações de contas e cartões
- **FR4:** Usuários podem desativar ou remover contas e cartões
- **FR4:** Usuários podem registrar transferências entre contas próprias
- **FR4:** Usuários podem reconciliar saldo registrado com saldo real do banco
- **FR4:** Sistema mantém histórico de saldos por conta ao longo do tempo

## Transaction Import & Classification

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

## Budget & Category Management

- **FR4:** Usuários podem criar categorias personalizadas de gastos
- **FR4:** Usuários podem organizar categorias em grupos hierárquicos
- **FR4:** Usuários podem definir quanto dinheiro alocar para cada categoria no início do mês
- **FR4:** Sistema mostra saldo disponível em cada categoria em tempo real
- **FR4:** Usuários podem realocar dinheiro entre categorias quando necessário
- **FR4:** Quando uma transação é classificada, sistema deduz automaticamente da categoria correspondente
- **FR4:** Sistema sinaliza visualmente categorias com saldo baixo ou negativo
- **FR4:** Usuários podem visualizar histórico de alocações por categoria
- **FR4:** Sistema mostra comparação entre planejado e real por categoria

## Dashboard GPS - Layer 1 (Overview)

- **FR4:** Sistema apresenta 5 métricas essenciais em tela única (Camada 1)
- **FR4:** Dashboard Camada 1 mostra saldo total disponível em todas as contas
- **FR4:** Dashboard Camada 1 mostra status visual de categorias (verde/amarelo/vermelho)
- **FR4:** Dashboard Camada 1 mostra progresso da meta principal (Reserva de Emergência)
- **FR4:** Dashboard Camada 1 mostra gastos do mês vs planejado
- **FR4:** Dashboard Camada 1 mostra projeção de fim de mês baseado em ritmo atual
- **FR4:** Dashboard Camada 1 responde as 3 perguntas: Onde estou? Para onde vou? Preciso ajustar?

## Dashboard GPS - Layer 2 (Tactical)

- **FR4:** Usuários podem acessar Camada 2 para visão tática detalhada
- **FR4:** Dashboard Camada 2 mostra gráficos de gastos por categoria
- **FR4:** Dashboard Camada 2 mostra comparação planejado vs real por categoria
- **FR4:** Dashboard Camada 2 mostra tendências temporais (últimos 3/6/12 meses)
- **FR4:** Usuários podem fazer drill-down em categorias específicas

## Dashboard GPS - Layer 3 (Details)

- **FR4:** Usuários podem acessar Camada 3 para detalhes completos
- **FR4:** Dashboard Camada 3 mostra lista de todas as transações com filtros
- **FR4:** Usuários podem filtrar transações por período, categoria, conta, ou texto
- **FR4:** Usuários podem ordenar transações por data, valor, ou categoria
- **FR4:** Usuários podem editar transações individualmente na Camada 3
- **FR4:** Usuários podem exportar dados em formato CSV

## Financial Goals Management

- **FR4:** Usuários podem criar múltiplas metas financeiras simultâneas
- **FR4:** Para cada meta, usuários podem definir nome, valor alvo e prazo
- **FR4:** Usuários podem alocar recursos mensais específicos para cada meta
- **FR4:** Sistema visualiza progresso de cada meta com barra de preenchimento e curva temporal
- **FR4:** Sistema projeta data de cumprimento baseada em alocação mensal atual
- **FR4:** Usuários podem ajustar alocação entre metas
- **FR4:** Sistema sugere metas pré-configuradas (Reserva de Emergência, Férias, Compra Grande)
- **FR4:** Usuários podem marcar metas como concluídas
- **FR4:** Sistema mantém histórico de progresso de metas ao longo do tempo

## Reports & Data Export

- **FR4:** Usuários podem exportar relatório de transações em formato CSV
- **FR4:** Usuários podem exportar relatório de categorias e orçamento em formato CSV
- **FR4:** Usuários podem exportar todos os dados da conta para portabilidade (LGPD)

## Admin Operations (Post-MVP v1.1)

- **FR4:** Administradores podem visualizar métricas de health do sistema
- **FR4:** Administradores podem visualizar taxa de erro de importação por banco
- **FR4:** Administradores podem visualizar precisão da IA por banco
- **FR4:** Administradores podem visualizar número de usuários ativos
- **FR4:** Sistema envia alertas automáticos para administradores quando métricas críticas são violadas
