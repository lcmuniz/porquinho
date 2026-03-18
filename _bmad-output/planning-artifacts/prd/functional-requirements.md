# Functional Requirements

## User Account Management

- **FR1:** Usuários podem se cadastrar usando Google OAuth
- **FR2:** Usuários podem se cadastrar usando email e senha
- **FR3:** Usuários podem fazer login usando qualquer método de autenticação configurado
- **FR4:** Usuários podem recuperar acesso via reset de senha por email
- **FR5:** Usuários podem visualizar sessões ativas e fazer logout remoto
- **FR6:** Usuários podem exportar todos os seus dados (LGPD compliance)
- **FR7:** Usuários podem solicitar exclusão permanente de conta e dados

## Subscription & Billing

- **FR8:** Novos usuários recebem automaticamente trial gratuito de 30 dias
- **FR9:** Usuários podem assinar plano mensal após trial
- **FR10:** Sistema envia lembretes 7 dias e 1 dia antes do fim do trial
- **FR11:** Usuários podem cancelar assinatura a qualquer momento (self-service)
- **FR12:** Usuários podem reativar assinatura cancelada
- **FR13:** Sistema processa pagamentos recorrentes automaticamente
- **FR14:** Sistema notifica usuários sobre falhas de pagamento e tenta cobrar novamente

## Onboarding & Setup

- **FR15:** Sistema guia novos usuários através de onboarding explicando conceito de orçamento por envelope
- **FR16:** Usuários podem adicionar múltiplas contas bancárias durante setup
- **FR17:** Usuários podem adicionar múltiplos cartões de crédito durante setup
- **FR18:** Sistema sugere categorias de gastos iniciais baseadas em padrões comuns
- **FR19:** Usuários podem personalizar categorias sugeridas durante setup
- **FR20:** Sistema guia primeira importação de arquivo bancário
- **FR21:** Sistema explica as 3 camadas do Dashboard GPS durante onboarding
- **FR22:** Usuários podem criar meta inicial (Reserva de Emergência) durante setup

## Account & Card Management

- **FR23:** Usuários podem cadastrar múltiplas contas bancárias com nome, tipo (corrente/poupança) e saldo inicial
- **FR24:** Usuários podem cadastrar múltiplos cartões de crédito com nome, limite e dia de vencimento
- **FR25:** Usuários podem editar informações de contas e cartões
- **FR26:** Usuários podem desativar ou remover contas e cartões
- **FR27:** Usuários podem registrar transferências entre contas próprias
- **FR28:** Usuários podem reconciliar saldo registrado com saldo real do banco
- **FR29:** Sistema mantém histórico de saldos por conta ao longo do tempo

## Transaction Import & Classification

- **FR30:** Usuários podem fazer upload de arquivos OFX dos 5 principais bancos brasileiros (Nubank, BB, Itaú, Bradesco, Caixa)
- **FR31:** Usuários podem fazer upload de arquivos CSV dos 5 principais bancos brasileiros
- **FR32:** Sistema detecta automaticamente formato e banco do arquivo importado
- **FR33:** Sistema faz parsing de transações com tratamento robusto de erros
- **FR34:** Sistema detecta e sinaliza transações duplicadas
- **FR35:** Sistema identifica transações Pix e extrai origem/destino quando disponível
- **FR36:** Sistema detecta compras parceladas e divide corretamente entre meses futuros
- **FR37:** Sistema classifica transações automaticamente usando IA
- **FR38:** Sistema apresenta transações classificadas para revisão do usuário
- **FR39:** Usuários podem revisar e ajustar classificações sugeridas pela IA
- **FR40:** Sistema aprende com correções do usuário para melhorar classificações futuras
- **FR41:** Usuários podem editar manualmente qualquer transação (data, valor, descrição, categoria)
- **FR42:** Usuários podem adicionar transações manualmente (para gastos em dinheiro)

## Budget & Category Management

- **FR43:** Usuários podem criar categorias personalizadas de gastos
- **FR44:** Usuários podem organizar categorias em grupos hierárquicos
- **FR45:** Usuários podem definir quanto dinheiro alocar para cada categoria no início do mês
- **FR46:** Sistema mostra saldo disponível em cada categoria em tempo real
- **FR47:** Usuários podem realocar dinheiro entre categorias quando necessário
- **FR48:** Quando uma transação é classificada, sistema deduz automaticamente da categoria correspondente
- **FR49:** Sistema sinaliza visualmente categorias com saldo baixo ou negativo
- **FR50:** Usuários podem visualizar histórico de alocações por categoria
- **FR51:** Sistema mostra comparação entre planejado e real por categoria

## Dashboard GPS - Layer 1 (Overview)

- **FR52:** Sistema apresenta 5 métricas essenciais em tela única (Camada 1)
- **FR53:** Dashboard Camada 1 mostra saldo total disponível em todas as contas
- **FR54:** Dashboard Camada 1 mostra status visual de categorias (verde/amarelo/vermelho)
- **FR55:** Dashboard Camada 1 mostra progresso da meta principal (Reserva de Emergência)
- **FR56:** Dashboard Camada 1 mostra gastos do mês vs planejado
- **FR57:** Dashboard Camada 1 mostra projeção de fim de mês baseado em ritmo atual
- **FR58:** Dashboard Camada 1 responde as 3 perguntas: Onde estou? Para onde vou? Preciso ajustar?

## Dashboard GPS - Layer 2 (Tactical)

- **FR59:** Usuários podem acessar Camada 2 para visão tática detalhada
- **FR60:** Dashboard Camada 2 mostra gráficos de gastos por categoria
- **FR61:** Dashboard Camada 2 mostra comparação planejado vs real por categoria
- **FR62:** Dashboard Camada 2 mostra tendências temporais (últimos 3/6/12 meses)
- **FR63:** Usuários podem fazer drill-down em categorias específicas

## Dashboard GPS - Layer 3 (Details)

- **FR64:** Usuários podem acessar Camada 3 para detalhes completos
- **FR65:** Dashboard Camada 3 mostra lista de todas as transações com filtros
- **FR66:** Usuários podem filtrar transações por período, categoria, conta, ou texto
- **FR67:** Usuários podem ordenar transações por data, valor, ou categoria
- **FR68:** Usuários podem editar transações individualmente na Camada 3
- **FR69:** Usuários podem exportar dados em formato CSV

## Financial Goals Management

- **FR70:** Usuários podem criar múltiplas metas financeiras simultâneas
- **FR71:** Para cada meta, usuários podem definir nome, valor alvo e prazo
- **FR72:** Usuários podem alocar recursos mensais específicos para cada meta
- **FR73:** Sistema visualiza progresso de cada meta com barra de preenchimento e curva temporal
- **FR74:** Sistema projeta data de cumprimento baseada em alocação mensal atual
- **FR75:** Usuários podem ajustar alocação entre metas
- **FR76:** Sistema sugere metas pré-configuradas (Reserva de Emergência, Férias, Compra Grande)
- **FR77:** Usuários podem marcar metas como concluídas
- **FR78:** Sistema mantém histórico de progresso de metas ao longo do tempo

## Reports & Data Export

- **FR79:** Usuários podem exportar relatório de transações em formato CSV
- **FR80:** Usuários podem exportar relatório de categorias e orçamento em formato CSV
- **FR81:** Usuários podem exportar todos os dados da conta para portabilidade (LGPD)

## Admin Operations (Post-MVP v1.1)

- **FR82:** Administradores podem visualizar métricas de health do sistema
- **FR83:** Administradores podem visualizar taxa de erro de importação por banco
- **FR84:** Administradores podem visualizar precisão da IA por banco
- **FR85:** Administradores podem visualizar número de usuários ativos
- **FR86:** Sistema envia alertas automáticos para administradores quando métricas críticas são violadas
