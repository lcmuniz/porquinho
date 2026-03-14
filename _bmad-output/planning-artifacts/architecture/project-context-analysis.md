# Project Context Analysis

## Core Functionality (89 Functional Requirements)

**Gestão de Conta & Autenticação (FR1-10):**
- OAuth Google integration
- 2FA opcional
- LGPD compliance (direitos de acesso, exclusão, portabilidade)

**Assinatura SaaS (FR11-17):**
- Trial gratuito 30 dias
- Billing recorrente mensal
- Cancelamento self-service

**Onboarding Guiado (FR18-25):**
- Setup inicial: contas bancárias, cartões, categorias
- Primeira importação de transações

**Importação & Classificação IA (FR33-45):**
- Parsing OFX/CSV (5 principais bancos brasileiros)
- Classificação automática com 85%+ precisão
- Review e ajustes manuais

**Orçamento por Envelope (FR46-54):**
- Sistema de categorias e alocação
- Adaptado ao Brasil (parcelamento de cartão)

**Dashboard GPS - 3 Camadas (FR55-72):**
- Layer 1: "Onde estou?" (5 métricas principais, semáforo de saúde)
- Layer 2: "Para onde vou?" (metas, tendências)
- Layer 3: "Preciso ajustar?" (análise detalhada, recomendações)
- Progressive disclosure via scroll vertical

**Metas Financeiras (FR73-81):**
- Criação, tracking, visualização de progresso

**Reports & Export (FR82-84):**
- Relatórios customizáveis
- Export dados (LGPD compliance)

**Admin/Observability (FR85-89):**
- Health checks, monitoring, métricas operacionais

## Critical Non-Functional Requirements

**Performance (NFR1-10):**
- Dashboard GPS load time < 2 segundos
- Navegação entre camadas < 500ms
- Gráficos render < 1 segundo
- Page load < 3 segundos
- **Implicação Arquitetural:** Requer caching agressivo, query optimization, lazy loading, code splitting

**Security (NFR11-25):**
- Encryption: AES-256 (em repouso), TLS 1.3 (em trânsito)
- Passwords: bcrypt hashing
- Segregação total de dados entre usuários (NFR15)
- Rate limiting: 5 tentativas/minuto em login (NFR17)
- Audit logging: todas operações sensíveis com timestamp e IP (NFR20)
- LGPD: consentimento, acesso, exclusão, portabilidade (NFR23-25)
- **Implicação Arquitetural:** Security-by-design desde Sprint 0, não pode ser retrofit

**Scalability (NFR26-32):**
- Suporta 1.000+ usuários simultâneos
- Processamento eficiente de múltiplas transações por usuário
- **Implicação Arquitetural:** Horizontal scaling capability, connection pooling

**Reliability (NFR33-42):**
- Uptime 99.5%
- Backup diário automático
- Graceful degradation em falhas de serviços externos
- **Implicação Arquitetural:** Circuit breakers, retry policies, health monitoring

**Accessibility (NFR43-54):**
- WCAG 2.1 Level AA compliance obrigatório
- Keyboard navigation completa
- Screen reader support com ARIA
- Lighthouse score > 90
- **Implicação Arquitetural:** Accessibility como requirement de aceitação desde Sprint 0

**Integration (NFR55-61):**
- Parsing robusto OFX/CSV de múltiplos bancos
- OAuth providers (Google)
- Payment gateway (99.9% confiabilidade)
- **Implicação Arquitetural:** Abstraction layers para múltiplos formatos/providers

## UX Architectural Requirements

**Platform Strategy:**
- Desktop-first (≥1280px) para ritual semanal de 5 minutos
- Mobile-responsive (<768px) para consultas rápidas
- Progressive Web App (PWA) capabilities

**Design System:**
- Tailwind CSS + shadcn-vue components (copy-paste approach)
- Primary color: Purple #9333EA
- Typography: Inter font family
- Spacing: Dense mas com espaço para respirar
- WCAG 2.1 AA compliance built-in

**Performance Percebido:**
- "30 segundos até clareza total" (UX target)
- Semáforo de saúde aparece em < 1 segundo
- Animações smooth: 200-300ms transitions
- Feedback visual imediato em todas as interações

**Dashboard GPS Complexity:**
- 3 camadas hierárquicas com progressive disclosure
- Scroll vertical para navegação entre layers
- Real-time updates após classificação IA
- Visualizações com gráficos e curve animations

## Project Scale & Complexity

**Classification:**
- **Domain:** Fintech (regulated, high security)
- **Type:** SaaS B2C Web Application
- **Context:** Greenfield
- **Complexity:** Alta

**Complexity Drivers:**
1. **IA/ML Classification:** 85%+ precisão, treino com dados brasileiros (Pix, parcelamento)
2. **Multi-Bank Integration:** 5 formatos OFX/CSV diferentes, peculiaridades brasileiras
3. **Envelope Budgeting Brasileiro:** Parcelamento de cartão (1 compra → N envelopes)
4. **LGPD Compliance:** Audit trail, consent management, data portability
5. **Real-time Performance:** Dashboard < 2s com cálculos complexos em 3 camadas
6. **Security Requirements:** Fintech-grade encryption, segregação, rate limiting

**Success Targets:**
- 1.000 usuários pagantes em 6 meses
- Churn < 5%
- NPS > 50
- Check-in semanal: 80% dos usuários ativos

## Cross-Cutting Concerns

1. **Security & Audit Logging**
   - Permeia todas as camadas (autenticação → dados → logging)
   - Audit trail para compliance LGPD
   - IP tracking, timestamp em operações sensíveis

2. **IA Classification Engine**
   - Core differentiator do produto
   - Arquitetura independente/plugável
   - Hosted API (OpenAI/Claude)

3. **Data Import Pipeline**
   - Parsing → Normalization → Classification → Storage
   - Multi-format support (OFX, CSV)
   - Error handling robusto

4. **Multi-Bank Integration**
   - Abstraction layer para múltiplos formatos
   - Bank-specific adapters
   - Peculiaridades brasileiras (Pix, parcelamento)

5. **Subscription & Billing**
   - Stripe integration
   - Trial management
   - Webhook handling

6. **Observability**
   - Health checks (NFR85-89)
   - Metrics collection
   - Error tracking
   - Performance monitoring

7. **LGPD Compliance**
   - Consent management
   - Data lifecycle (retention, deletion)
   - Portability (export completo)
   - User rights (acesso, correção, exclusão)

## Regulatory & Compliance Requirements

**LGPD (Lei Geral de Proteção de Dados):**
- Classificação: Software de gestão financeira (não instituição financeira)
- Consentimento explícito obrigatório
- Direitos: acesso, correção, exclusão, portabilidade
- Audit trail completo
- Data residency: implicitamente Brasil

**Security Standards:**
- AES-256 encryption em repouso
- TLS 1.3 para dados em trânsito
- BCrypt para password hashing
- OWASP Top 10 protection obrigatório

**Accessibility Standards:**
- WCAG 2.1 Level AA compliance mandatório
- Não é opcional ou post-launch

**Not Required (but documented for clarity):**
- PCI DSS: Não aplicável (não processa/armazena dados de cartão)
- KYC/AML: Não aplicável (não movimenta dinheiro)
- Open Banking: Futuro (requer registro como TPP)

---
