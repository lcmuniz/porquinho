# Domain-Specific Requirements

## Compliance & Regulatory

**LGPD (Lei Geral de Proteção de Dados):**
- Consentimento explícito e granular para coleta e uso de dados financeiros
- Direito a acesso: usuário pode exportar todos os seus dados
- Direito a exclusão: usuário pode solicitar remoção completa de dados
- Direito a portabilidade: usuário pode exportar dados em formato legível
- Transparência: política de privacidade clara sobre uso de dados
- Base legal: execução de contrato e interesse legítimo para melhorias do serviço
- Retenção de dados: definir política de retenção (ex: dados mantidos enquanto conta ativa)

**Classificação como Software de Gestão Financeira:**
- Produto não é instituição financeira (não movimenta dinheiro)
- Não requer autorização do Banco Central
- Não processa pagamentos (sem necessidade de PCI DSS)
- Apenas visualiza e classifica dados financeiros importados pelo usuário

**Futuro - Open Banking (quando implementado):**
- Registro como TPP (Third Party Provider) no Banco Central
- Certificado digital para APIs Open Banking
- Conformidade com padrões técnicos do Open Banking Brasil
- Consentimento específico para cada instituição e tipo de dado

## Technical Constraints

**Segurança de Dados Financeiros:**
- **Criptografia em repouso:** Todos os dados financeiros sensíveis (transações, saldos, contas) criptografados no banco de dados usando AES-256
- **Criptografia em trânsito:** TLS 1.3 para todas as comunicações HTTPS
- **Armazenamento de credenciais:** Nunca armazenar senhas bancárias. Se Open Banking no futuro, tokens OAuth com refresh token seguro
- **Segregação de dados:** Isolamento completo entre usuários - um usuário nunca pode acessar dados de outro
- **Logs de auditoria:** Todas as operações sensíveis (login, mudança de senha, exclusão de conta, exportação de dados) registradas com timestamp e IP

**Proteção contra Ataques:**
- **SQL Injection:** Prepared statements e ORMs para queries
- **XSS (Cross-Site Scripting):** Sanitização de inputs, CSP headers
- **CSRF (Cross-Site Request Forgery):** Tokens CSRF em todas as operações state-changing
- **Brute force:** Rate limiting em login e APIs sensíveis
- **Session hijacking:** Session tokens seguros, HttpOnly cookies, regeneração após login

**Backup e Recuperação:**
- Backups automáticos diários com retenção de 30 dias
- Backups criptografados
- Teste de recuperação mensal para garantir integridade
- Zero perda de dados financeiros em caso de falha

**Performance:**
- Dashboard GPS < 2 segundos mesmo com 5 anos de transações
- Importação OFX/CSV processada em < 30 segundos para arquivos de até 1 ano
- IA de classificação responde em < 3 segundos para lote de 100 transações

## Integration Requirements

**Bancos Brasileiros (MVP):**
- Suporte a formatos OFX e CSV dos 5 principais bancos:
  - Nubank (OFX, CSV)
  - Banco do Brasil (OFX, CSV)
  - Itaú (OFX, CSV)
  - Bradesco (OFX, CSV)
  - Caixa (OFX, CSV)
- Detecção automática de formato e banco
- Parsing robusto com tratamento de erros
- Validação de integridade de dados importados

**Peculiaridades Brasileiras:**
- **Pix:** Identificar transações Pix e extrair origem/destino quando disponível no campo descrição
- **Parcelamento sem juros:** Detectar compras parceladas e dividir corretamente entre meses
- **Múltiplas moedas:** Suporte a R$ (BRL) como moeda primária
- **Formatos de data brasileiros:** DD/MM/YYYY

**Futuro - Open Banking:**
- Integração com APIs padronizadas do Open Banking Brasil
- Sincronização automática de transações
- Atualização em tempo real

**Futuro - Investimentos:**
- Integração com B3 (CEI - Canal Eletrônico do Investidor)
- Integração com corretoras via API quando disponível

## Risk Mitigations

**Risco: Vazamento de dados financeiros sensíveis**
- **Mitigação:**
  - Criptografia em repouso e trânsito
  - Auditorias de segurança regulares
  - Penetration testing antes do lançamento
  - Monitoramento de acessos suspeitos
  - Plano de resposta a incidentes documentado

**Risco: Mudança de formato de arquivos bancários sem aviso**
- **Mitigação:**
  - Monitoramento de taxa de erro de parsing por banco
  - Alertas automáticos quando taxa de erro > 5%
  - Testes com arquivos reais de múltiplos períodos
  - Versionamento de parsers para rollback rápido
  - Comunicação proativa com usuários afetados

**Risco: IA de classificação com baixa precisão leva a decisões erradas**
- **Mitigação:**
  - Interface de revisão sempre visível (usuário deve validar)
  - Métricas de precisão monitoradas por banco e categoria
  - Feedback loop explícito: usuário corrige, IA aprende
  - Nunca executar ações automáticas financeiras sem confirmação
  - Retreino contínuo do modelo com dados validados

**Risco: Perda de dados de transações**
- **Mitigação:**
  - Backups automáticos diários criptografados
  - Retenção de 30 dias
  - Testes mensais de recuperação
  - Redundância de storage
  - Export periódico automático para usuário (opcional)

**Risco: Usuário esquece senha e perde acesso**
- **Mitigação:**
  - Fluxo de recuperação de senha via email
  - 2FA opcional para maior segurança
  - Backup codes gerados no cadastro

**Risco: Não conformidade com LGPD**
- **Mitigação:**
  - Revisão legal de política de privacidade
  - Implementação de todos os direitos (acesso, exclusão, portabilidade)
  - Logs de consentimento
  - Processo documentado para solicitações de titulares
  - DPO (Data Protection Officer) designado ou consultoria
