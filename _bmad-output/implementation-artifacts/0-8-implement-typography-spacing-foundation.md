# Story 0.8: Implement Typography & Spacing Foundation

Status: done

## Story

As a developer,
I want to configure typography (Inter font) and spacing system,
so that the UI has consistent text hierarchy and layout spacing.

## Acceptance Criteria

1. **Given** Tailwind CSS is configured with design tokens
   **When** I add Inter font from Google Fonts or local files
   **Then** Inter is defined as default font family in the CSS theme
   **And** Font weights are configured: 400 (regular), 500 (medium), 600 (semibold), 700 (bold)

2. **Given** Inter font is loaded
   **When** I verify the type scale
   **Then** Type scale uses Tailwind defaults: text-xs (12px) to text-5xl (48px)
   **And** Line heights are optimized: 1.0-1.3 for headings, 1.5 for body text

3. **Given** Typography tokens are defined
   **When** I verify spacing configuration
   **Then** Spacing base is 4px (Tailwind default rem-based system)
   **And** Border radius default is rounded-lg (12px) — already configured in Story 0-7

4. **Given** All typography and spacing tokens are applied
   **When** I create a typography demo section on the /design-system page
   **Then** Common spacing patterns are documented visually: Cards (p-6, gap-4), Dashboard layers (py-12)
   **And** Typography renders correctly across all target browsers

## Tasks / Subtasks

- [x] Task 1: Adicionar fonte Inter ao projeto (AC: #1)
  - [x] 1.1 Instalar `@fontsource/inter` via npm (auto-hosted, sem dependencia de Google Fonts CDN)
  - [x] 1.2 Importar os weights necessarios no `main.ts`: 400, 500, 600, 700
  - [x] 1.3 Registrar Inter como font-family padrao no `@theme inline` do `main.css`
  - [x] 1.4 Verificar: `npm run build` compila sem erros

- [x] Task 2: Configurar tokens de tipografia no CSS (AC: #1, #2)
  - [x] 2.1 Adicionar `--font-sans: 'Inter', ui-sans-serif, system-ui, sans-serif` no `@theme inline`
  - [x] 2.2 Verificar que font-weights 400/500/600/700 funcionam com classes Tailwind (`font-normal`, `font-medium`, `font-semibold`, `font-bold`)
  - [x] 2.3 Verificar que type scale padrao Tailwind (text-xs a text-5xl) funciona corretamente com Inter
  - [x] 2.4 NAO customizar line-heights — usar os defaults do Tailwind que ja atendem a UX spec

- [x] Task 3: Documentar spacing patterns na pagina /design-system (AC: #3, #4)
  - [x] 3.1 Criar componente `TypographyShowcase.vue` em `src/components/` com demonstracao de:
    - Type scale completo (text-xs a text-5xl) com nomes e tamanhos
    - Font weights (400, 500, 600, 700) com labels
    - Hierarquia do Dashboard GPS (metricas 4xl bold, titulos xl semibold, labels sm medium, body base regular)
  - [x] 3.2 Criar componente `SpacingShowcase.vue` em `src/components/` com demonstracao de:
    - Spacing scale visual (4px, 8px, 12px, 16px, 24px, 32px, 48px, 64px)
    - Padroes de uso comum: Cards (p-6, gap-4), Dashboard layers (py-12), Dense (p-3, gap-2)
    - Border radius visual (sm=4px, md=8px, lg=12px, xl=16px, full)
  - [x] 3.3 Adicionar TypographyShowcase e SpacingShowcase ao DesignSystemView.vue
  - [x] 3.4 `npm run build` compila sem erros
  - [x] 3.5 `npm run test:unit -- --run` passa sem regressoes

- [x] Task 4: Criar testes unitarios (AC: #4)
  - [x] 4.1 Criar `TypographyShowcase.spec.ts` verificando que o componente renderiza todas as escalas
  - [x] 4.2 Criar `SpacingShowcase.spec.ts` verificando que o componente renderiza os padroes de spacing
  - [x] 4.3 Todos os testes existentes continuam passando

## Dev Notes

### CRITICAL: Tailwind CSS v4 — CSS-first approach

O projeto usa **Tailwind CSS v4** com `@import "tailwindcss"` — NAO existe `tailwind.config.js`. Toda configuracao e feita via CSS no `main.css` usando `@theme inline`.

### Como Adicionar Font Family no Tailwind v4

No Tailwind v4, para definir a font-family padrao:

```css
@theme inline {
  --font-sans: 'Inter', ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}
```

Isso faz com que `font-sans` seja a fonte padrao para todas as classes Tailwind que usam sans-serif. O `@layer base` ja aplica `font-sans` ao body via `text-foreground`.

### Fontsource vs Google Fonts CDN

Usar `@fontsource/inter` ao inves de Google Fonts CDN porque:
- **Self-hosted**: sem dependencia de CDN externo, melhor para performance e privacidade
- **Tree-shakeable**: importar apenas os weights necessarios
- **Consistente**: mesma versao sempre, sem surpresas de atualizacao

Instalacao:
```bash
npm install @fontsource/inter
```

Import no `main.ts` (ANTES do import do main.css):
```typescript
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@fontsource/inter/700.css'
```

### O que JA EXISTE no main.css (NAO duplicar)

- Todas as cores (gray scale, state colors, purple scale, chart colors, semantic tokens) — Story 0-7
- Radius tokens (`--radius-sm`, `--radius-md`, `--radius-lg`, `--radius-xl`) — ja configurados
- `@layer base` com estilos globais para body
- Formato OKLCH para todas as cores

### O que MODIFICAR no main.css

Apenas ADICIONAR ao `@theme inline`:
```css
--font-sans: 'Inter', ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
```

NAO precisa adicionar tokens de spacing — Tailwind v4 ja tem spacing scale padrao de 4px que corresponde exatamente ao que a UX spec pede.

NAO precisa adicionar tokens de font-size ou line-height — Tailwind v4 ja tem type scale padrao que corresponde a UX spec:
- text-xs: 12px/1.5
- text-sm: 14px/1.5
- text-base: 16px/1.5
- text-lg: 18px/1.5 (UX spec diz 1.5)
- text-xl: 20px/1.4 (UX spec diz 1.4)
- text-2xl: 24px/1.3 (UX spec diz 1.3)
- text-3xl: 30px/1.2 (UX spec diz 1.2)
- text-4xl: 36px/1.1 (UX spec diz 1.1)
- text-5xl: 48px/1.0 (UX spec diz 1.0)

Os defaults do Tailwind sao proximos o suficiente. NAO customizar.

### Principios Tipograficos da UX Spec

1. **Numeros Dominam Visualmente**: Valores financeiros usam bold (700) e tamanhos grandes (4xl). Labels usam medium (500) e tamanhos pequenos (sm).
2. **Hierarquia por Tamanho e Weight, NAO Cor**: Usar tamanho + weight para hierarquia, manter UI neutra.
3. **Line-Height Generoso**: 1.5 para body, reduz para 1.0-1.3 em headings.
4. **Sem ALL CAPS para Body Text**: ALL CAPS apenas para labels muito pequenos (badges). Sentence case para tudo mais.

### Hierarquia do Dashboard GPS (referencia para showcase)

```
Semaforo Status: text-2xl font-semibold
Grafico Titulo: text-xl font-semibold
Valores de Metricas: text-4xl font-bold (numeros grandes e impactantes)
Labels de Metricas: text-sm font-medium
Respostas GPS: text-base font-normal
Recomendacoes: text-base font-medium
```

### Spacing Patterns da UX Spec (referencia para showcase)

```
Cards/Containers: p-6 (padding 24px)
Entre Cards: gap-4 ou gap-6 (16-24px)
Secoes do Dashboard: mb-8 (margin-bottom 32px)
Entre Camadas GPS: py-12 (padding vertical 48px)
Componentes Densos (tabelas): p-3, gap-2
Componentes Normais (cards, forms): p-6, gap-4
Componentes Espacosos (landing): p-8, gap-8
```

### Border Radius (referencia para showcase)

```
sm: 4px (inputs, badges) — rounded-sm
md: 8px (buttons, cards padrao) — rounded-md
lg: 12px (cards principais, modals) — rounded-lg (DEFAULT, ja configurado --radius: 0.75rem)
xl: 16px (graficos, containers grandes) — rounded-xl
full: 9999px (avatars, pills) — rounded-full
```

### Anti-Patterns a Evitar

- NAO criar `tailwind.config.js` — o projeto usa Tailwind v4 CSS-first
- NAO customizar type scale ou line-heights — os defaults atendem a UX spec
- NAO customizar spacing scale — Tailwind default 4px base e o que a UX spec pede
- NAO adicionar fonte via `<link>` no index.html — usar @fontsource para self-hosting
- NAO substituir o `main.css` inteiro — apenas ADICIONAR o token de font-family
- NAO duplicar tokens de radius que ja existem
- NAO usar fontes hardcoded em componentes — sempre usar classes Tailwind (`font-sans`, `font-bold`, etc.)

### Aprendizados da Story 0-7 (Design Tokens & Color System)

- Expandir `main.css` adicionando ao `@theme inline`, nunca substituir
- Componentes de demonstracao seguem padrao: criar componente + view wrapper + rota
- Testes unitarios com `@vue/test-utils` mount + verificacao de render
- Build de producao deve compilar sem erros
- A rota `/design-system` e `DesignSystemView.vue` ja existem — apenas adicionar novos componentes

### Project Structure Notes

Arquivos a serem modificados/criados:
```
porquinho-frontend/
├── src/
│   ├── assets/
│   │   └── main.css              # MODIFICAR (adicionar --font-sans ao @theme inline)
│   ├── components/
│   │   ├── TypographyShowcase.vue  # CRIAR (demonstracao de tipografia)
│   │   ├── SpacingShowcase.vue     # CRIAR (demonstracao de spacing)
│   │   └── __tests__/
│   │       ├── TypographyShowcase.spec.ts  # CRIAR
│   │       └── SpacingShowcase.spec.ts     # CRIAR
│   ├── views/
│   │   └── DesignSystemView.vue  # MODIFICAR (adicionar novos showcases)
│   └── main.ts                   # MODIFICAR (adicionar imports @fontsource/inter)
├── package.json                  # MODIFICAR (nova dependencia @fontsource/inter)
```

### References

- [Source: _bmad-output/planning-artifacts/ux-design-specification/visual-design-foundation.md#Typography System] - Type scale, weights, principios
- [Source: _bmad-output/planning-artifacts/ux-design-specification/visual-design-foundation.md#Spacing & Layout Foundation] - Spacing patterns, grid, border radius
- [Source: _bmad-output/planning-artifacts/ux-design-specification/design-system-foundation.md#Design Tokens] - Token categories
- [Source: _bmad-output/planning-artifacts/architecture/implementation-patterns-consistency-rules.md] - Frontend naming conventions, component patterns
- [Source: _bmad-output/implementation-artifacts/0-7-implement-design-tokens-color-system.md] - Previous story patterns, main.css structure, @theme approach

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6 (1M context)

### Debug Log References

Nenhum bloqueio encontrado. Implementacao direta seguindo Dev Notes.

### Completion Notes List

- Instalado `@fontsource/inter` (self-hosted, sem dependencia CDN)
- Importados weights 400/500/600/700 no `main.ts` antes do `main.css`
- Adicionado `--font-sans` ao `@theme inline` do `main.css` conforme padrao Tailwind v4 CSS-first
- Criado `TypographyShowcase.vue`: escala tipografica completa (text-xs a text-5xl), 4 pesos, hierarquia Dashboard GPS
- Criado `SpacingShowcase.vue`: escala de spacing visual (4px a 64px), padroes de uso (Cards, GPS, Denso, Espacoso), border radius (sm a full)
- Ambos os componentes adicionados ao `DesignSystemView.vue`
- Build de producao compila sem erros (652 modulos, fontes Inter incluidas no bundle)
- 17 testes passam (4 novos + 13 existentes), sem regressoes
- Nao foram customizados type scale, line-heights ou spacing — defaults Tailwind v4 atendem a UX spec

### File List

- porquinho-frontend/package.json (modificado — nova dependencia @fontsource/inter)
- porquinho-frontend/package-lock.json (modificado — lock de @fontsource/inter)
- porquinho-frontend/src/main.ts (modificado — imports @fontsource/inter 400/500/600/700)
- porquinho-frontend/src/assets/main.css (modificado — token --font-sans no @theme inline)
- porquinho-frontend/src/components/TypographyShowcase.vue (criado)
- porquinho-frontend/src/components/SpacingShowcase.vue (criado)
- porquinho-frontend/src/views/DesignSystemView.vue (modificado — adicionados TypographyShowcase e SpacingShowcase)
- porquinho-frontend/src/components/__tests__/TypographyShowcase.spec.ts (criado)
- porquinho-frontend/src/components/__tests__/SpacingShowcase.spec.ts (criado)

## Change Log

- 2026-03-14: Implementada Story 0.8 — fonte Inter auto-hosted via @fontsource/inter, token --font-sans no Tailwind v4, componentes TypographyShowcase e SpacingShowcase criados, adicionados ao /design-system. 17 testes passando.
