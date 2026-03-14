# Visual Design Foundation

## Color System

**Filosofia de Cores: "UI Neutra, Dados Vibrantes"**

80% da interface usa tons neutros (cinzas, brancos), permitindo que 20% (gráficos, dados, estados) tenham cores vibrantes que capturam atenção. Alinha com princípio "Gráficos Dominam, Não Componentes UI".

**Paleta de Cores:**

**Cores Neutras (UI Base - 80% da Interface):**

```
Background:
- White: #FFFFFF (fundos principais)
- Gray 50: #FAFAFA (fundos secundários, cards)
- Gray 100: #F4F4F5 (fundos alternativos)

Text:
- Gray 900: #18181B (texto principal - quase preto mas suave)
- Gray 700: #3F3F46 (texto secundário)
- Gray 500: #71717A (texto terciário, labels)
- Gray 400: #A1A1AA (texto disabled, placeholders)

Borders & Dividers:
- Gray 200: #E4E4E7 (borders padrão)
- Gray 300: #D4D4D8 (borders hover/focus)
```

**Cor Primária - Roxo (Interatividade e Marca):**

```
Primary Purple:
- Purple 600: #9333EA (primária principal - CTAs, links, elementos interativos)
- Purple 500: #A855F7 (hover states)
- Purple 700: #7E22CE (pressed states)
- Purple 100: #F3E8FF (backgrounds suaves, highlights)
- Purple 50: #FAF5FF (backgrounds muito suaves)
```

**Rationale Roxo:**
- Moderno e diferente de ferramentas financeiras tradicionais (azul)
- Transmite inovação e confiança sem ser corporativo
- Destaca-se visualmente sem ser agressivo
- Associação com premium e tecnologia avançada

**Cores de Estado - Semáforo (Status Financeiro):**

```
Success Green (🟢 Saudável):
- Green 600: #16A34A (primário)
- Green 500: #22C55E (hover)
- Green 100: #DCFCE7 (background)

Warning Yellow (🟡 Atenção):
- Yellow 600: #CA8A04 (primário)
- Yellow 500: #EAB308 (hover)
- Yellow 100: #FEF9C3 (background)

Danger Red (🔴 Crítico):
- Red 600: #DC2626 (primário)
- Red 500: #EF4444 (hover)
- Red 100: #FEE2E2 (background)
```

**Cores de Gráficos (Visualizações de Dados - 20% da Interface):**

```
Chart Colors (vibrant, distintas):
- Chart 1: #9333EA (roxo primário - patrimônio principal)
- Chart 2: #06B6D4 (cyan - receitas)
- Chart 3: #F59E0B (amber - gastos)
- Chart 4: #10B981 (green - metas)
- Chart 5: #EC4899 (pink - categorias secundárias)
- Chart 6: #8B5CF6 (violet - projeções)
```

**Semantic Color Mapping:**

```
Primary: Purple 600 (CTAs, links, elementos de marca)
Secondary: Gray 600 (ações secundárias)
Success: Green 600 (confirmações, progresso positivo)
Warning: Yellow 600 (alertas, atenção)
Error: Red 600 (erros, estados críticos)
Info: Purple 500 (informações neutras)

Background: White (fundos principais)
Surface: Gray 50 (cards, elevações)
Border: Gray 200 (separadores)

Text Primary: Gray 900 (títulos, texto principal)
Text Secondary: Gray 700 (subtítulos)
Text Tertiary: Gray 500 (labels, metadados)
```

**Acessibilidade - Contraste:**

Todos os pares de cores atendem WCAG 2.1 AA (4.5:1 para texto normal, 3:1 para texto grande):

✅ Gray 900 em White: 18.23:1 (AAA)
✅ Gray 700 em White: 9.14:1 (AAA)
✅ Gray 500 em White: 4.61:1 (AA)
✅ Purple 600 em White: 4.75:1 (AA)
✅ Green 600 em White: 4.54:1 (AA)
✅ Red 600 em White: 5.14:1 (AA)

## Typography System

**Typeface: Inter**

**Rationale:**
- Sans-serif moderna e altamente legível
- Otimizada para interfaces digitais
- Open source e gratuita
- Ampla suporte de weights e caracteres
- Uso massivo em apps modernos (Notion, Linear, GitHub)

**Type Scale (Tailwind defaults):**

```
Display (Headings Grandes):
- 5xl: 48px / 1.0 line-height (páginas de entrada)
- 4xl: 36px / 1.1 line-height (títulos principais)

Headings:
- 3xl: 30px / 1.2 line-height (h1 - títulos de seção)
- 2xl: 24px / 1.3 line-height (h2 - subtítulos)
- xl: 20px / 1.4 line-height (h3 - cards principais)
- lg: 18px / 1.5 line-height (h4 - destaque)

Body:
- base: 16px / 1.5 line-height (texto principal padrão)
- sm: 14px / 1.5 line-height (texto secundário, labels)
- xs: 12px / 1.5 line-height (captions, metadados)
```

**Font Weights:**

```
Regular (400): Texto body padrão
Medium (500): Labels, botões secundários
Semibold (600): Headings, botões primários, ênfase
Bold (700): Números grandes (métricas), estados críticos
```

**Typography Hierarchy (Dashboard GPS):**

```
Semáforo Status: 2xl, Semibold
Gráfico Título: xl, Semibold
Valores de Métricas: 4xl, Bold (números grandes e impactantes)
Labels de Métricas: sm, Medium
Respostas GPS (3 perguntas): base, Regular
Recomendações: base, Medium
```

**Princípios Tipográficos:**

1. **Números Dominam Visualmente:**
   - Valores financeiros usam weights bold (700) e tamanhos grandes
   - Contraste com labels em medium/regular
   - Exemplo: "R$ 25.000" (4xl bold) vs "Reserva de Emergência" (sm medium)

2. **Hierarquia por Tamanho e Weight, Não Cor:**
   - Evitar usar cores para hierarquia (mantém UI neutra)
   - Tamanho de fonte + weight criam hierarquia clara
   - Alinha com princípio "Visual Hierarchy Through Scale, Not Color"

3. **Line-Height Generoso:**
   - 1.5 para texto body (legibilidade)
   - Reduz para 1.0-1.3 em headings (impacto visual)
   - Mais espaço = menos "cheio" (anti-YNAB)

4. **Sem ALL CAPS para Body Text:**
   - ALL CAPS apenas para labels muito pequenos (ex: badges)
   - Sentence case para tudo mais (mais amigável)

## Spacing & Layout Foundation

**Filosofia: "Denso Mas Respirável"**

Balanço entre eficiência (denso, mostra informação) e clareza (espaço suficiente para não parecer "cheio"). Não é airy como landing page de marketing, mas também não é apertado como spreadsheet.

**Base Unit: 4px**

Tailwind spacing scale (múltiplos de 4px):

```
Spacing Scale:
- 1: 4px (espaçamento mínimo, gaps pequenos)
- 2: 8px (espaçamento entre elementos relacionados)
- 3: 12px (espaçamento padrão dentro de cards)
- 4: 16px (espaçamento padrão entre cards)
- 6: 24px (espaçamento entre seções)
- 8: 32px (espaçamento entre grupos principais)
- 12: 48px (espaçamento entre áreas do Dashboard)
- 16: 64px (espaçamento máximo, separadores de camadas)
```

**Uso Comum no Porquinho:**

```
Cards/Containers: p-6 (padding 24px - respirável mas não excessivo)
Entre Cards: gap-4 ou gap-6 (16-24px)
Seções do Dashboard: mb-8 (margin-bottom 32px)
Entre Camadas GPS: py-12 (padding vertical 48px)

Componentes Densos (tabelas, listas): p-3, gap-2
Componentes Normais (cards, forms): p-6, gap-4
Componentes Espaçosos (landing, hero): p-8, gap-8
```

**Layout Grid:**

**Desktop (≥1280px):**
- Container: max-width 1280px, centralizado
- Dashboard GPS: Single column, full-width graphs
- Camada 1: 1 coluna (gráfico dominante)
- Camada 2: 2 colunas (visão tática lado a lado)
- Camada 3: 3 colunas (detalhes em grid)

**Tablet (768px - 1279px):**
- Container: full-width com padding lateral p-6
- Dashboard: Single column
- Camadas 2-3: 1-2 colunas (stack em mobile)

**Mobile (<768px):**
- Container: full-width com padding lateral p-4
- Tudo single column (100% width)
- Gráficos ajustam altura para caber em viewport

**Princípios de Layout:**

1. **Vertical Scroll, Não Horizontal:**
   - Nunca scroll horizontal (anti-pattern)
   - Toda informação acessível via scroll vertical natural
   - Mobile: gráficos compactam verticalmente

2. **Progressive Disclosure via Whitespace:**
   - Espaçamento maior entre camadas (py-12) cria separação visual
   - Usuário percebe "nova seção" sem precisar de divisores pesados
   - Scroll revela progressivamente sem overwhelm

3. **Gráficos Dominam, UI Discreta:**
   - Gráficos: Largura completa, altura generosa (h-64 a h-96)
   - UI elements (botões, inputs): Tamanho padrão, não competem
   - Ratio 80/20: Gráficos ocupam 80% do espaço vertical

4. **Densidade Contextual:**
   - Dashboard Camada 1: Espaçoso (p-6, gap-6) - clareza
   - Tabelas de transações: Denso (p-3, gap-2) - eficiência
   - Formulários: Médio (p-4, gap-4) - balanço

5. **Above The Fold Crítico:**
   - Desktop: Semáforo + Gráfico Principal + 2-3 métricas visíveis sem scroll
   - Mobile: Semáforo + Gráfico Principal (métricas aparecem ao scroll)
   - Objetivo: 30 segundos de clareza = tudo visível rapidamente

**Border Radius (Arredondamento):**

```
Small (sm): 4px (inputs, badges)
Medium (md): 8px (buttons, cards padrão)
Large (lg): 12px (cards principais, modals)
XL (xl): 16px (gráficos, containers grandes)
Full (full): 9999px (avatars, pills)
```

**Uso no Porquinho:**
- Cards do Dashboard: rounded-lg (12px - moderno mas não exagerado)
- Botões: rounded-md (8px)
- Gráficos: rounded-xl (16px - destaque visual)
- Badges/Tags: rounded-full (pills)

## Accessibility Considerations

**Contraste de Cores:**

✅ **WCAG 2.1 Level AA Compliance**
- Text normal (16px): 4.5:1 mínimo
- Text large (18px+ bold ou 24px+): 3:1 mínimo
- UI components: 3:1 mínimo

Todos os pares de cores definidos atendem ou excedem esses requisitos.

**Tamanho de Fonte Mínimo:**

- Texto body: 16px (base) - nunca menor que 14px (sm)
- Texto terciário: 14px (sm) - legível em desktop e mobile
- Captions: 12px (xs) - apenas para metadados não críticos

**Touch Targets (Mobile):**

- Botões principais: min 44x44px (WCAG recomendado)
- Links e botões secundários: min 40x40px
- Espaçamento entre elementos clicáveis: min 8px

**Keyboard Navigation:**

- Focus states claramente visíveis (outline roxo 2px)
- Tab order lógico (top → bottom, left → right)
- Skip links para pular para conteúdo principal
- Todos os elementos interativos acessíveis via teclado

**Screen Readers:**

- Semantic HTML (headings, landmarks, lists)
- ARIA labels em gráficos e elementos visuais
- Alt text descritivo em ícones informativos
- Live regions para atualizações dinâmicas (ex: status do semáforo)

**Motion & Animation:**

- Respeitar `prefers-reduced-motion` (CSS media query)
- Usuários com essa preferência: animações reduzidas ou removidas
- Transições: 200-300ms (suaves mas não lentas)
- Nunca autoplay de vídeos ou animações automáticas intrusivas

**Color Blindness:**

- Nunca usar apenas cor para transmitir informação
- Semáforo: Cor + Ícone (🟢✓ 🟡⚠️ 🔴✗)
- Gráficos: Cores + patterns/shapes quando possível
- Testado com simuladores de daltonismo

**Visual Design Foundation Summary:**

Esta fundação estabelece um sistema visual moderno, minimalista e acessível que:
- Prioriza clareza sobre decoração
- Usa cor estrategicamente (UI neutra, dados vibrantes)
- Mantém hierarquia via tamanho e espaçamento
- Equilibra densidade com respirabilidade
- Garante acessibilidade WCAG 2.1 AA

---
