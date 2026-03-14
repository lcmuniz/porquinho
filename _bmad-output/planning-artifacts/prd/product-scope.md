# Product Scope

## MVP - Minimum Viable Product

**Essencial para provar o conceito e gerar valor imediato:**

**1. Sistema de Orçamento por Envelope**
- Criação e gerenciamento de categorias de gastos personalizadas
- Alocação de dinheiro disponível entre categorias no início do mês
- Consumo de categorias ao registrar gastos
- Realocação entre categorias quando necessário
- Visualização clara de quanto disponível em cada categoria

**2. Classificação Automática por IA**
- Importação de arquivos OFX e CSV de bancos brasileiros
- Parsing correto de formatos dos 5 principais bancos (Nubank, BB, Itaú, Bradesco, Caixa)
- Classificação automática de transações baseada em IA
- Sistema de aprendizado com feedback do usuário
- Suporte a parcelamento sem juros (detectar e dividir corretamente)
- Suporte a Pix (identificar origem/destino quando disponível)
- Interface de revisão e ajuste rápido de classificações (2 minutos máximo)

**3. Dashboard GPS Financeiro (3 Camadas)**

**Camada 1 - Visão Geral (90% do uso):**
- 5 métricas essenciais em 1 tela, visíveis em 30 segundos
- Responde: Onde estou? Para onde vou? Preciso ajustar?
- Status visual claro (verde/amarelo/vermelho)
- Acesso rápido às ações mais comuns

**Camada 2 - Visão Tática:**
- Detalhamento por área (gastos, metas, contas)
- Gráficos e visualizações por categoria
- Comparação planejado vs real
- Tendências temporais

**Camada 3 - Detalhes Completos:**
- Transações individuais
- Histórico completo
- Edição e ajustes finos
- Exportação de dados

**4. Sistema de Metas Financeiras**
- Cadastro de metas com valor alvo e prazo
- Alocação de recursos para metas
- Visualização de progresso (curvas subindo, barras enchendo)
- Projeção de cumprimento ("no ritmo atual, você alcança em X meses")
- Foco primário: Reserva de Emergência

**5. Insights Surpresa Semanais**
- Sistema de análise automática de padrões de gastos
- Descobertas semanais relevantes ("Você gastou R$ X em Y este mês - Z% do seu orçamento")
- Comparações vs média pessoal ("Você economizou R$ X vs sua média!")
- Alertas de desvios significativos
- Tom motivador, não punitivo

**6. Gestão de Contas e Cartões**
- Cadastro de múltiplas contas bancárias
- Cadastro de múltiplos cartões de crédito
- Controle de saldo atual por conta
- Reconciliação entre saldo registrado e saldo real
- Transferências entre contas

**7. Segurança e Compliance Mínimo (Fintech)**
- Criptografia de dados financeiros em repouso e trânsito
- Armazenamento seguro de informações sensíveis
- LGPD compliance básico: consentimento explícito, direito a exclusão, transparência sobre uso de dados
- Autenticação segura
- Logs de auditoria para operações sensíveis

**Fora do MVP (explicitamente excluído):**
- Open Banking automático (importação manual OFX/CSV suficiente para MVP)
- Mobile app (web desktop first, mobile responsivo básico aceitável)
- Multi-usuário com permissões diferentes (foco em usuário primário)
- Integração com investimentos (foco em fluxo de caixa, não patrimônio)
- Notificações WhatsApp (email suficiente para MVP)

## Growth Features (Post-MVP)

**Quando MVP validado e base de usuários crescendo:**

**1. Open Banking Automático**
- Integração direta com bancos via API Open Banking
- Sincronização automática sem upload manual de arquivos
- Atualização em tempo real de transações
- Elimina completamente o esforço de importação manual

**2. Integração com Investimentos**
- Importação de posições da B3 e corretoras
- Visão consolidada de patrimônio total (contas + investimentos)
- Tracking de rentabilidade de investimentos
- Visão de alocação de patrimônio

**3. Notificações WhatsApp**
- Alertas de desvios de orçamento via WhatsApp
- Lembretes de check-in semanal
- Insights surpresa via mensagem
- Interação básica via bot (consultar saldos)

**4. Mobile App Nativo**
- App iOS e Android para consulta rápida
- Classificação rápida de transações via mobile
- Notificações push
- Registro manual rápido de gastos em dinheiro

**5. Multi-Usuário Familiar**
- Diferentes permissões (admin vs visualização)
- Transparência familiar sobre situação financeira
- Múltiplos usuários gerenciando o mesmo orçamento
- Histórico de quem fez cada mudança

**6. Relatórios Avançados**
- Exportação para Excel/PDF
- Relatórios customizados
- Comparação entre períodos
- Análise de tendências de longo prazo

## Vision (Future)

**Quando produto maduro e liderança estabelecida:**

**1. IA de Assistente Financeiro Proativo**
- Recomendações personalizadas baseadas em comportamento
- Previsão de gastos futuros com alta precisão
- Alertas proativos antes de desvios ("Você está gastando mais que o normal em lazer")
- Sugestões de otimização ("Você poderia economizar R$ X fazendo Y")

**2. Planejamento Financeiro Avançado**
- Simulações de cenários ("E se eu aumentar economia em 10%?")
- Planejamento de aposentadoria com projeções
- Planejamento de grandes compras (casa, carro)
- Análise de viabilidade de objetivos de vida

**3. Comunidade e Gamificação**
- Sistema de conquistas e badges
- Desafios de economia
- Comparação anônima com peers similares
- Comunidade de usuários compartilhando dicas

**4. Integração com Contadores**
- Exportação formatada para declaração de Imposto de Renda
- Relatórios prontos para contador
- Organização de documentos fiscais
- Integração com sistemas de contabilidade

**5. Inteligência Preditiva**
- Machine learning avançado para prever comportamento futuro
- Identificação automática de oportunidades de economia
- Otimização automática de alocação de orçamento
- Alertas de risco financeiro antes que aconteçam
