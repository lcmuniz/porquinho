# Design System Foundation

## Design System Choice

**Escolha: Tailwind CSS + Shadcn/UI**

**Stack de Design:**
- **Tailwind CSS** - Utility-first CSS framework para estilização
- **Shadcn/UI** - Biblioteca de componentes copiáveis (não dependência externa)
- **Radix UI** - Primitives headless para acessibilidade (base do Shadcn)
- **Lucide Icons** - Ícones modernos e consistentes

**Abordagem:** Hybrid customizable system - componentes prontos com flexibilidade total de customização.

## Rationale for Selection

**1. Velocidade de Desenvolvimento (Crítico para MVP)**

- Time já tem experiência com Tailwind (zero curva de aprendizado)
- Shadcn/UI fornece componentes prontos para copiar diretamente no projeto
- Não precisa configurar biblioteca de componentes externa
- Permite lançar rápido e iterar com feedback de usuários reais

**2. Flexibilidade de Identidade Visual**

- Projeto ainda não tem logo, cores, tipografia definidos
- Tailwind permite iterar design tokens rapidamente via `tailwind.config.js`
- Shadcn componentes são copiados para o projeto = customização ilimitada
- Pode evoluir identidade visual organicamente durante MVP sem refactor

**3. Alinhamento com Princípios de UX**

- **Visual-First:** Tailwind favorece utility classes que tornam fácil criar layouts dominados por visual
- **Simplicidade Visual:** Não impõe "opinião" forte de design (vs Material que sempre parece Material)
- **Evitar "Muito Cheio":** Utilities tornam trivial criar espaçamento generoso e hierarquia clara
- **Minimalismo:** Shadcn componentes têm estética clean e moderna por padrão

**4. Alinhamento com Inspiração (Instagram/YouTube)**

- Instagram/YouTube usam design limpo, espaçoso, sem chrome excessivo
- Tailwind + Shadcn favorecem esse estilo naturalmente
- Fácil criar scroll vertical fluido e hierarquia de 3 camadas

**5. Acessibilidade Built-In**

- Shadcn/UI construído sobre Radix UI (primitives acessíveis)
- ARIA labels, keyboard navigation, focus management incluídos
- Atende requisito de acessibilidade sem esforço extra

**6. Performance**

- Tailwind gera apenas CSS usado (tree-shaking automático)
- Shadcn componentes são copiados = sem bundle de biblioteca externa
- Bundle final menor, carregamento mais rápido

**7. Manutenibilidade**

- Componentes no projeto = controle total, sem breaking changes de biblioteca
- Tailwind tem comunidade massiva e documentação excelente
- Fácil de contratar desenvolvedores que sabem Tailwind

## Implementation Approach

**Fase 1: Setup Inicial (Sprint 0)**

1. **Configurar Tailwind CSS:**
   ```
   - Instalar Tailwind + PostCSS
   - Configurar tailwind.config.js com design tokens base
   - Setup de cores, tipografia, espaçamento, breakpoints
   ```

2. **Instalar Shadcn/UI CLI:**
   ```
   - npx shadcn-ui@latest init
   - Configurar tema base (zinc/slate para cinzas neutros)
   - Escolher estilo: "Default" (pode mudar depois)
   ```

3. **Copiar Componentes Base:**
   ```
   - Button, Input, Card, Select, Dialog
   - Componentes usados em 80% das telas
   - Customizar conforme necessário
   ```

**Fase 2: Componentes Customizados (Sprint 1-2)**

Criar componentes específicos do porquinho que não existem em Shadcn:

1. **Financial Health Semaphore (🟢🟡🔴)**
   - Componente visual único do porquinho
   - Estados: Saudável / Atenção / Crítico
   - Animação suave de transição entre estados

2. **Dashboard GPS Layers**
   - Container de scroll vertical
   - Layer 1, 2, 3 com progressive disclosure
   - Transições fluidas entre camadas

3. **Wealth Growth Chart**
   - Gráfico de linha de patrimônio ao longo do tempo
   - Animação de curva subindo
   - Área preenchida abaixo da linha

4. **Goal Progress Bar**
   - Barra de meta com animação de preenchimento
   - Milestone markers visuais
   - Celebração quando atinge 100%

5. **AI Classification Badge**
   - Badge mostrando confiança da IA
   - Tooltip explicando raciocínio
   - Estados: Alta confiança / Média / Baixa

6. **Insight Reveal Card**
   - Card de insight surpresa com reveal progressivo
   - Animação de "descoberta"
   - Tom visual de "descoberta científica"

**Fase 3: Design Tokens (Sprint 2)**

Definir tokens no `tailwind.config.js`:

1. **Colors:**
   ```
   - Primary: [a definir] - CTA, elementos interativos
   - Success: Verde (semáforo, progresso positivo)
   - Warning: Amarelo (semáforo, atenção)
   - Danger: Vermelho (semáforo, crítico)
   - Neutral: Zinc/Slate (textos, backgrounds)
   - Charts: Paleta de 5-6 cores para gráficos
   ```

2. **Typography:**
   ```
   - Font Family: [a definir] - Sans serif moderna e legível
   - Scale: Tailwind default (text-xs até text-9xl)
   - Weights: 400 (regular), 500 (medium), 600 (semibold), 700 (bold)
   ```

3. **Spacing:**
   ```
   - Usar Tailwind default (4px base)
   - Espaçamento generoso (p-6, p-8 comum)
   - Evitar densidade excessiva (problema YNAB)
   ```

4. **Shadows & Borders:**
   ```
   - Shadows sutis (não material design exagerado)
   - Borders finos (1px) para separação
   - Border-radius moderado (rounded-lg padrão)
   ```

**Fase 4: Component Library Interna (Contínuo)**

- Documentar componentes customizados em Storybook (opcional)
- Criar guia de uso para time
- Evoluir design system organicamente baseado em necessidades reais

## Customization Strategy

**Princípios de Customização:**

1. **Start Simple, Evolve Organically**
   - Começar com defaults do Shadcn (estilo clean)
   - Customizar apenas quando necessidade real surgir
   - Evitar over-engineering de design system no MVP

2. **Visual Hierarchy Through Scale, Not Color**
   - Usar tamanho de fonte/espaço para hierarquia (não cores gritantes)
   - Alinha com princípio "Interface Calma, Sem Estresse"
   - Inspiração: Instagram usa hierarquia de tamanho efetivamente

3. **Animation for Celebration, Not Distraction**
   - Animações para celebrar progresso (curva subindo, meta atingida)
   - Transições suaves mas não lentas (200-300ms)
   - Evitar animações gratuitas que distraem

4. **Gráficos Dominam, Não Componentes UI**
   - Componentes UI devem ser discretos (não competir com gráficos)
   - Backgrounds neutros, borders sutis
   - Gráficos recebem cores vibrantes, UI fica neutra

5. **Customizar Para Casos de Uso, Não Estética**
   - Customizar Button quando precisa de estado específico (ex: "AI working")
   - Não customizar só para "parecer diferente"
   - Cada customização deve resolver necessidade real de UX

**Componentes que NÃO Customizaremos (Usar Default):**

- Form inputs (Input, Select, Checkbox) - defaults são ótimos
- Dialog/Modal - funcionalidade padrão suficiente
- Dropdown Menu - comportamento padrão esperado
- Tooltip - não precisa ser único

**Componentes que Customizaremos Profundamente:**

- Dashboard GPS Layers (único do porquinho)
- Charts e visualizações (coração do produto)
- Semáforo de saúde (identidade visual)
- Insight cards (experiência de descoberta)
- Goal progress bars (satisfação emocional)

**Evolução Futura:**

- **Mês 1-2 (MVP):** Shadcn defaults + componentes custom essenciais
- **Mês 3-4:** Refinar paleta de cores baseado em feedback
- **Mês 5-6:** Adicionar micro-interações baseado em analytics
- **Mês 7+:** Evolução contínua baseada em necessidades reais

**Constraints de Customização:**

- Manter acessibilidade (não quebrar ARIA, keyboard nav)
- Manter performance (não adicionar animações pesadas)
- Manter consistência (não criar 10 variações de Button)
- Documentar customizações para time futuro
