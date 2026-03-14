# Functional Requirements

## User Account Management

- **FR1:** Usuários podem se cadastrar usando Google OAuth
- **FR2:** Usuários podem se cadastrar usando email e senha
- **FR3:** Usuários podem fazer login usando qualquer método de autenticação configurado
- **FR4:** Usuários podem ativar autenticação de dois fatores (2FA) opcional
- **FR5:** Usuários podem recuperar acesso via reset de senha por email
- **FR6:** Usuários podem trocar seu email de conta
- **FR7:** Usuários podem adicionar/remover métodos de autenticação
- **FR8:** Usuários podem visualizar sessões ativas e fazer logout remoto
- **FR9:** Usuários podem exportar todos os seus dados (LGPD compliance)
- **FR10:** Usuários podem solicitar exclusão permanente de conta e dados

## Subscription & Billing

- **FR11:** Novos usuários recebem automaticamente trial gratuito de 30 dias
- **FR12:** Usuários podem assinar plano mensal após trial
- **FR13:** Sistema envia lembretes 7 dias e 1 dia antes do fim do trial
- **FR14:** Usuários podem cancelar assinatura a qualquer momento (self-service)
- **FR15:** Usuários podem reativar assinatura cancelada
- **FR16:** Sistema processa pagamentos recorrentes automaticamente
- **FR17:** Sistema notifica usuários sobre falhas de pagamento e tenta cobrar novamente

## Onboarding & Setup

- **FR18:** Sistema guia novos usuários através de onboarding explicando conceito de orçamento por envelope
- **FR19:** Usuários podem adicionar múltiplas contas bancárias durante setup
- **FR20:** Usuários podem adicionar múltiplos cartões de crédito durante setup
- **FR21:** Sistema sugere categorias de gastos iniciais baseadas em padrões comuns
- **FR22:** Usuários podem personalizar categorias sugeridas durante setup
- **FR23:** Sistema guia primeira importação de arquivo bancário
- **FR24:** Sistema explica as 3 camadas do Dashboard GPS durante onboarding
- **FR25:** Usuários podem criar meta inicial (Reserva de Emergência) durante setup

## Account & Card Management

- **FR26:** Usuários podem cadastrar múltiplas contas bancárias com nome, tipo (corrente/poupança) e saldo inicial
- **FR27:** Usuários podem cadastrar múltiplos cartões de crédito com nome, limite e dia de vencimento
- **FR28:** Usuários podem editar informações de contas e cartões
- **FR29:** Usuários podem desativar ou remover contas e cartões
- **FR30:** Usuários podem registrar transferências entre contas próprias
- **FR31:** Usuários podem reconciliar saldo registrado com saldo real do banco
- **FR32:** Sistema mantém histórico de saldos por conta ao longo do tempo

## Transaction Import & Classification

- **FR33:** Usuários podem fazer upload de arquivos OFX dos 5 principais bancos brasileiros (Nubank, BB, Itaú, Bradesco, Caixa)
- **FR34:** Usuários podem fazer upload de arquivos CSV dos 5 principais bancos brasileiros
- **FR35:** Sistema detecta automaticamente formato e banco do arquivo importado
- **FR36:** Sistema faz parsing de transações com tratamento robusto de erros
- **FR37:** Sistema detecta e sinaliza transações duplicadas
- **FR38:** Sistema identifica transações Pix e extrai origem/destino quando disponível
- **FR39:** Sistema detecta compras parceladas e divide corretamente entre meses futuros
- **FR40:** Sistema classifica transações automaticamente usando IA
- **FR41:** Sistema apresenta transações classificadas para revisão do usuário
- **FR42:** Usuários podem revisar e ajustar classificações sugeridas pela IA
- **FR43:** Sistema aprende com correções do usuário para melhorar classificações futuras
- **FR44:** Usuários podem editar manualmente qualquer transação (data, valor, descrição, categoria)
- **FR45:** Usuários podem adicionar transações manualmente (para gastos em dinheiro)

## Budget & Category Management

- **FR46:** Usuários podem criar categorias personalizadas de gastos
- **FR47:** Usuários podem organizar categorias em grupos hierárquicos
- **FR48:** Usuários podem definir quanto dinheiro alocar para cada categoria no início do mês
- **FR49:** Sistema mostra saldo disponível em cada categoria em tempo real
- **FR50:** Usuários podem realocar dinheiro entre categorias quando necessário
- **FR51:** Quando uma transação é classificada, sistema deduz automaticamente da categoria correspondente
- **FR52:** Sistema sinaliza visualmente categorias com saldo baixo ou negativo
- **FR53:** Usuários podem visualizar histórico de alocações por categoria
- **FR54:** Sistema mostra comparação entre planejado e real por categoria

## Dashboard GPS - Layer 1 (Overview)

- **FR55:** Sistema apresenta 5 métricas essenciais em tela única (Camada 1)
- **FR56:** Dashboard Camada 1 mostra saldo total disponível em todas as contas
- **FR57:** Dashboard Camada 1 mostra status visual de categorias (verde/amarelo/vermelho)
- **FR58:** Dashboard Camada 1 mostra progresso da meta principal (Reserva de Emergência)
- **FR59:** Dashboard Camada 1 mostra gastos do mês vs planejado
- **FR60:** Dashboard Camada 1 mostra projeção de fim de mês baseado em ritmo atual
- **FR61:** Dashboard Camada 1 responde as 3 perguntas: Onde estou? Para onde vou? Preciso ajustar?

## Dashboard GPS - Layer 2 (Tactical)

- **FR62:** Usuários podem acessar Camada 2 para visão tática detalhada
- **FR63:** Dashboard Camada 2 mostra gráficos de gastos por categoria
- **FR64:** Dashboard Camada 2 mostra comparação planejado vs real por categoria
- **FR65:** Dashboard Camada 2 mostra tendências temporais (últimos 3/6/12 meses)
- **FR66:** Usuários podem fazer drill-down em categorias específicas

## Dashboard GPS - Layer 3 (Details)

- **FR67:** Usuários podem acessar Camada 3 para detalhes completos
- **FR68:** Dashboard Camada 3 mostra lista de todas as transações com filtros
- **FR69:** Usuários podem filtrar transações por período, categoria, conta, ou texto
- **FR70:** Usuários podem ordenar transações por data, valor, ou categoria
- **FR71:** Usuários podem editar transações individualmente na Camada 3
- **FR72:** Usuários podem exportar dados em formato CSV

## Financial Goals Management

- **FR73:** Usuários podem criar múltiplas metas financeiras simultâneas
- **FR74:** Para cada meta, usuários podem definir nome, valor alvo e prazo
- **FR75:** Usuários podem alocar recursos mensais específicos para cada meta
- **FR76:** Sistema visualiza progresso de cada meta com barra de preenchimento e curva temporal
- **FR77:** Sistema projeta data de cumprimento baseada em alocação mensal atual
- **FR78:** Usuários podem ajustar alocação entre metas
- **FR79:** Sistema sugere metas pré-configuradas (Reserva de Emergência, Férias, Compra Grande)
- **FR80:** Usuários podem marcar metas como concluídas
- **FR81:** Sistema mantém histórico de progresso de metas ao longo do tempo

## Reports & Data Export

- **FR82:** Usuários podem exportar relatório de transações em formato CSV
- **FR83:** Usuários podem exportar relatório de categorias e orçamento em formato CSV
- **FR84:** Usuários podem exportar todos os dados da conta para portabilidade (LGPD)

## Admin Operations (Post-MVP v1.1)

- **FR85:** Administradores podem visualizar métricas de health do sistema
- **FR86:** Administradores podem visualizar taxa de erro de importação por banco
- **FR87:** Administradores podem visualizar precisão da IA por banco
- **FR88:** Administradores podem visualizar número de usuários ativos
- **FR89:** Sistema envia alertas automáticos para administradores quando métricas críticas são violadas
