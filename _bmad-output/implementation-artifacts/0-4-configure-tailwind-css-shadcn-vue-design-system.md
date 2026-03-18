# Story 0.4: Configurar Tailwind CSS e shadcn-vue Design System

Status: done

## Story

As a developer,
I want to install and configure Tailwind CSS and shadcn-vue components,
so that I have a complete design system ready for building UI.

## Acceptance Criteria

1. **Given** o projeto frontend Vue está inicializado
   **When** eu instalo Tailwind CSS com `npm install -D tailwindcss postcss autoprefixer @tailwindcss/vite`
   **Then** Tailwind está configurado via `tailwind.config.js` e `postcss.config.js`

2. **And** executar `npx shadcn-vue@latest init` configura shadcn-vue com style "Default" e base color "Purple"

3. **And** o diretório de componentes é criado em `src/components/ui/`

4. **And** componentes base (Button, Input, Card, Select, Dialog) são instalados via `npx shadcn-vue@latest add`

5. **And** diretivas Tailwind (`@tailwind base`, `components`, `utilities`) são adicionadas ao arquivo CSS principal

6. **And** executar o dev server mostra que os estilos Tailwind estão funcionando

7. **And** componentes shadcn-vue renderizam corretamente com cor de destaque roxo

## Tasks / Subtasks

- [x] Instalar dependências Tailwind CSS (AC: #1)
  - [x] `npm install -D tailwindcss postcss autoprefixer @tailwindcss/vite`
  - [x] ~~Criar `tailwind.config.js`~~ — não necessário no Tailwind v4 (CSS-first approach)
  - [x] ~~Criar `postcss.config.js`~~ — não necessário com `@tailwindcss/vite` plugin
  - [x] Adicionar plugin `@tailwindcss/vite` ao `vite.config.ts`

- [x] Configurar CSS principal com diretivas Tailwind (AC: #5)
  - [x] Substituir conteúdo de `src/assets/main.css` com diretivas Tailwind
  - [x] Remover `src/assets/base.css` (substituído por Tailwind reset)

- [x] Inicializar shadcn-vue (AC: #2, #3)
  - [x] `npx shadcn-vue@latest init` com opções: TypeScript, style "Default", base color "Purple"
  - [x] Verificar que `components.json` foi criado na raiz do projeto
  - [x] Verificar que `src/lib/utils.ts` foi criado com utilitário `cn()`
  - [x] Verificar que CSS variables de shadcn-vue foram adicionadas ao CSS

- [x] Instalar componentes base shadcn-vue (AC: #4)
  - [x] `npx shadcn-vue@latest add button`
  - [x] `npx shadcn-vue@latest add input`
  - [x] `npx shadcn-vue@latest add card`
  - [x] `npx shadcn-vue@latest add select`
  - [x] `npx shadcn-vue@latest add dialog`
  - [x] Verificar que componentes estão em `src/components/ui/`

- [x] Atualizar vite.config.ts com alias e plugin (AC: #1)
  - [x] Adicionar `@tailwindcss/vite` plugin ao array de plugins

- [x] Criar página de teste para validação visual (AC: #6, #7)
  - [x] Atualizar `App.vue` com componentes shadcn-vue para validação
  - [x] Testar Button, Input, Card com estilos Tailwind
  - [x] Confirmar cor primária roxa aparece corretamente
  - [x] Confirmar que `npm run dev` mostra estilos Tailwind funcionando

- [x] Garantir que testes existentes continuam passando (AC: #6)
  - [x] `npm run type-check` passa sem erros
  - [x] `npm run build` compila sem erros

## Dev Notes

### CRITICAL: Estado Atual do Frontend

O projeto frontend (`porquinho-frontend/`) já está inicializado (Story 0.1) com:
- **Vue 3.5.29** + **Vite 7.3.1** + **TypeScript 5.9.3**
- **Pinia 3.0.4** + **Vue Router 5.0.3**
- **Vitest 4.0.18** + **Playwright 1.58.2**
- **ESLint 10** + **Prettier 3.8.1** + **oxlint 1.50.0**
- Alias `@/` já configurado em `tsconfig.app.json` e `vite.config.ts`

**NÃO há** Tailwind CSS, PostCSS ou shadcn-vue instalados ainda.

### CRITICAL: Versões Compatíveis

O projeto usa **Vite 7** e **Vue 3.5**. Verificar compatibilidade:
- **Tailwind CSS 4.x** é a versão mais recente — usa abordagem CSS-first (sem `tailwind.config.js` por padrão). Verificar se shadcn-vue já suporta Tailwind v4. Se não, usar **Tailwind CSS 3.x**.
- **shadcn-vue** é a versão Vue do shadcn/ui — usar `shadcn-vue@latest` (não `shadcn-ui`).
- shadcn-vue é construído sobre **Radix Vue** (não Radix UI que é React).
- Dependência adicional provável: `class-variance-authority`, `clsx`, `tailwind-merge` (instalados automaticamente pelo shadcn-vue init).

**IMPORTANTE:** Se o shadcn-vue init pedir pelo framework ou configuração, responder:
- Framework: Vue
- TypeScript: Yes
- Style: Default
- Base Color: Slate (neutro) — a cor primária roxa será configurada via CSS variables
- Global CSS: `src/assets/main.css`
- Components path: `src/components/ui`
- Utils path: `src/lib/utils`

### Arquivos Existentes que Serão Modificados

| Arquivo | Ação | Notas |
|---------|------|-------|
| `porquinho-frontend/package.json` | MODIFY | Novas dependências adicionadas via npm install |
| `porquinho-frontend/vite.config.ts` | MODIFY | Adicionar `@tailwindcss/vite` plugin |
| `porquinho-frontend/src/assets/main.css` | REPLACE | Diretivas Tailwind + CSS variables shadcn-vue |
| `porquinho-frontend/src/assets/base.css` | DELETE | Substituído pelo reset/base do Tailwind |
| `porquinho-frontend/src/App.vue` | MODIFY | Atualizar para usar componentes shadcn-vue |

### Arquivos Novos que Serão Criados

| Arquivo | Notas |
|---------|-------|
| `porquinho-frontend/tailwind.config.js` | Configuração Tailwind (se Tailwind v3) |
| `porquinho-frontend/postcss.config.js` | PostCSS com plugins |
| `porquinho-frontend/components.json` | Configuração shadcn-vue (gerado pelo init) |
| `porquinho-frontend/src/lib/utils.ts` | Utilitário `cn()` para merge de classes |
| `porquinho-frontend/src/components/ui/button/` | Componente Button |
| `porquinho-frontend/src/components/ui/input/` | Componente Input |
| `porquinho-frontend/src/components/ui/card/` | Componente Card |
| `porquinho-frontend/src/components/ui/select/` | Componente Select |
| `porquinho-frontend/src/components/ui/dialog/` | Componente Dialog |

### vite.config.ts — Estado Esperado Após Esta Story

```typescript
import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
})
```

### main.css — Estado Esperado Após Esta Story

O conteúdo exato depende da saída do `shadcn-vue init`, mas deve incluir:

```css
@tailwind base;
@tailwind components;
@tailwind utilities;

@layer base {
  :root {
    --background: 0 0% 100%;
    --foreground: 240 10% 3.9%;
    --card: 0 0% 100%;
    --card-foreground: 240 10% 3.9%;
    --popover: 0 0% 100%;
    --popover-foreground: 240 10% 3.9%;
    --primary: 270 91% 56%;      /* Purple 600: #9333EA */
    --primary-foreground: 0 0% 100%;
    --secondary: 240 4.8% 95.9%;
    --secondary-foreground: 240 5.9% 10%;
    --muted: 240 4.8% 95.9%;
    --muted-foreground: 240 3.8% 46.1%;
    --accent: 240 4.8% 95.9%;
    --accent-foreground: 240 5.9% 10%;
    --destructive: 0 84.2% 60.2%;
    --destructive-foreground: 0 0% 98%;
    --border: 240 5.9% 90%;
    --input: 240 5.9% 90%;
    --ring: 270 91% 56%;         /* Purple 600 para focus ring */
    --radius: 0.75rem;           /* rounded-lg = 12px */
  }
}
```

**NOTA:** Os valores HSL exatos acima são aproximações. O `shadcn-vue init` gerará seus próprios valores. O ponto crítico é que `--primary` e `--ring` devem corresponder ao **Purple 600 (#9333EA)** ou próximo. Se o init gerar valores diferentes, ajustar manualmente `--primary` para HSL de `#9333EA` → `270 91% 56%`.

### Padrões de Componentes Frontend (Obrigatório)

De acordo com a arquitetura do projeto:
- Componentes shadcn-vue ficam em `src/components/ui/` — copy-paste, não dependência externa
- Componentes custom do projeto ficam organizados por feature: `src/components/dashboard/`, `src/components/transactions/`, etc.
- Componentes PascalCase: `Button.vue`, `Card.vue`, `DashboardLayer1.vue`
- Composables: `src/composables/useXxx.ts`

### Design System — Cores do Projeto (Referência para Stories Futuras)

A configuração completa de design tokens é feita na Story 0.7. Esta story apenas configura o setup base. Cores de referência:
- **Primary Purple 600:** #9333EA
- **Neutral Grays:** Zinc/Slate (Gray 50-900)
- **Success Green 600:** #16A34A
- **Warning Yellow 600:** #CA8A04
- **Danger Red 600:** #DC2626

### Learnings da Story 0.3 (Anterior)

- ~~`mise exec` é necessário para garantir a versão correta de ferramentas (Node.js via mise)~~ **⚠️ OBSOLETE (2026-03-17):** Node.js/NPM versions are now managed correctly, no need for `mise exec`
- ~~Sempre usar `mise exec -- npm ...` se comandos npm falharem com versão errada do Node~~ **⚠️ OBSOLETE:** Just use `npm` directly
- Arquivos sensíveis devem estar no `.gitignore` antes de serem criados
- Testar build/typecheck DEPOIS de cada mudança significativa para detectar erros cedo

### Project Structure Notes

- O path alias `@/` mapeia para `./src/` — já configurado em `tsconfig.app.json` e `vite.config.ts`
- A estrutura de componentes segue: `src/components/ui/` para shadcn-vue, `src/components/{feature}/` para componentes de negócio
- Não remover componentes de exemplo (`HelloWorld.vue`, etc.) nesta story — serão removidos quando componentes reais os substituírem

### References

- [Source: _bmad-output/planning-artifacts/epics/epic-0-project-foundation-technical-setup.md#Story 0.4]
- [Source: _bmad-output/planning-artifacts/architecture/technical-preferences.md#Frontend]
- [Source: _bmad-output/planning-artifacts/architecture/implementation-patterns-consistency-rules.md#Frontend Vue TypeScript]
- [Source: _bmad-output/planning-artifacts/architecture/project-structure-boundaries.md#Frontend Vue]
- [Source: _bmad-output/planning-artifacts/ux-design-specification/design-system-foundation.md#Design System Choice]
- [Source: _bmad-output/planning-artifacts/ux-design-specification/visual-design-foundation.md#Color System]
- [Source: _bmad-output/implementation-artifacts/0-3-setup-postgresql-database-with-flyway.md#Completion Notes]

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-6

### Debug Log References

- shadcn-vue init falhou inicialmente com "No import alias found in your tsconfig.json" — o alias `@/` estava apenas no `tsconfig.app.json`. Solução: adicionado `compilerOptions.paths` ao `tsconfig.json` raiz.
- shadcn-vue 2.4.3 usa **Tailwind CSS v4** (`tailwindcss: '^4.1.17'`) — abordagem CSS-first com `@import "tailwindcss"` ao invés de diretivas `@tailwind`. Não são necessários `tailwind.config.js` nem `postcss.config.js`.
- CSS variables usam formato **oklch** (Tailwind v4), não HSL. Purple 600 (#9333EA) = `oklch(0.558 0.244 292.717)`.
- eslint `vue/multi-word-component-names` disparou para componentes shadcn-vue (Button, Card, etc.) — resolvido com exceção no `eslint.config.ts` para `src/components/ui/**/*.vue`.
- shadcn-vue init escolheu "Neutral" como base color (a opção "Purple" como base color não existe no CLI v2.4.3 — a cor primária roxa foi configurada manualmente via CSS variables `--primary` e `--ring`).

### Completion Notes List

- Tailwind CSS **v4.2.1** instalado via `npm install -D tailwindcss postcss autoprefixer @tailwindcss/vite`
- Plugin `@tailwindcss/vite` adicionado ao `vite.config.ts`
- `src/assets/main.css` substituído com `@import "tailwindcss"` (abordagem Tailwind v4 CSS-first)
- `src/assets/base.css` removido (substituído pelo reset do Tailwind)
- `tsconfig.json` atualizado com `compilerOptions.paths` para suporte ao alias `@/` pelo shadcn-vue init
- `npx shadcn-vue@latest init` executado — gerou `components.json` e `src/lib/utils.ts`
- CSS variables geradas pelo init em formato oklch; `--primary` e `--ring` ajustados para Purple 600 (`oklch(0.558 0.244 292.717)`)
- Componentes base instalados em `src/components/ui/`: Button, Input, Card (com sub-componentes), Select, Dialog — 35 arquivos no total
- `App.vue` atualizado com componentes shadcn-vue para validação visual
- `eslint.config.ts` atualizado com exceção `vue/multi-word-component-names` para `src/components/ui/`
- `npm run build` — 640 módulos transformados, build em 4.23s ✅
- `npm run type-check` — sem erros ✅
- `npm run test:unit -- --run` — 1 teste, 1 passou ✅
- `npm run lint` — 0 warnings, 0 errors ✅

### File List

- porquinho-frontend/package.json (MODIFIED — novas dependências)
- porquinho-frontend/package-lock.json (MODIFIED — atualizado pelo npm)
- porquinho-frontend/vite.config.ts (MODIFIED — plugin @tailwindcss/vite)
- porquinho-frontend/tsconfig.json (MODIFIED — compilerOptions.paths para alias @/)
- porquinho-frontend/eslint.config.ts (MODIFIED — exceção vue/multi-word-component-names para ui/)
- porquinho-frontend/components.json (CREATED — config shadcn-vue)
- porquinho-frontend/src/assets/main.css (REPLACED — Tailwind v4 + CSS variables shadcn-vue + Purple 600)
- porquinho-frontend/src/assets/base.css (DELETED)
- porquinho-frontend/src/lib/utils.ts (CREATED — utilitário cn())
- porquinho-frontend/src/App.vue (MODIFIED — usa componentes shadcn-vue)
- porquinho-frontend/src/components/ui/button/Button.vue (CREATED)
- porquinho-frontend/src/components/ui/button/index.ts (CREATED)
- porquinho-frontend/src/components/ui/input/Input.vue (CREATED)
- porquinho-frontend/src/components/ui/input/index.ts (CREATED)
- porquinho-frontend/src/components/ui/card/Card.vue (CREATED)
- porquinho-frontend/src/components/ui/card/CardAction.vue (CREATED)
- porquinho-frontend/src/components/ui/card/CardContent.vue (CREATED)
- porquinho-frontend/src/components/ui/card/CardDescription.vue (CREATED)
- porquinho-frontend/src/components/ui/card/CardFooter.vue (CREATED)
- porquinho-frontend/src/components/ui/card/CardHeader.vue (CREATED)
- porquinho-frontend/src/components/ui/card/CardTitle.vue (CREATED)
- porquinho-frontend/src/components/ui/card/index.ts (CREATED)
- porquinho-frontend/src/components/ui/select/Select.vue (CREATED)
- porquinho-frontend/src/components/ui/select/SelectContent.vue (CREATED)
- porquinho-frontend/src/components/ui/select/SelectGroup.vue (CREATED)
- porquinho-frontend/src/components/ui/select/SelectItem.vue (CREATED)
- porquinho-frontend/src/components/ui/select/SelectItemText.vue (CREATED)
- porquinho-frontend/src/components/ui/select/SelectLabel.vue (CREATED)
- porquinho-frontend/src/components/ui/select/SelectScrollDownButton.vue (CREATED)
- porquinho-frontend/src/components/ui/select/SelectScrollUpButton.vue (CREATED)
- porquinho-frontend/src/components/ui/select/SelectSeparator.vue (CREATED)
- porquinho-frontend/src/components/ui/select/SelectTrigger.vue (CREATED)
- porquinho-frontend/src/components/ui/select/SelectValue.vue (CREATED)
- porquinho-frontend/src/components/ui/select/index.ts (CREATED)
- porquinho-frontend/src/components/ui/dialog/Dialog.vue (CREATED)
- porquinho-frontend/src/components/ui/dialog/DialogClose.vue (CREATED)
- porquinho-frontend/src/components/ui/dialog/DialogContent.vue (CREATED)
- porquinho-frontend/src/components/ui/dialog/DialogDescription.vue (CREATED)
- porquinho-frontend/src/components/ui/dialog/DialogFooter.vue (CREATED)
- porquinho-frontend/src/components/ui/dialog/DialogHeader.vue (CREATED)
- porquinho-frontend/src/components/ui/dialog/DialogOverlay.vue (CREATED)
- porquinho-frontend/src/components/ui/dialog/DialogScrollContent.vue (CREATED)
- porquinho-frontend/src/components/ui/dialog/DialogTitle.vue (CREATED)
- porquinho-frontend/src/components/ui/dialog/DialogTrigger.vue (CREATED)
- porquinho-frontend/src/components/ui/dialog/index.ts (CREATED)
