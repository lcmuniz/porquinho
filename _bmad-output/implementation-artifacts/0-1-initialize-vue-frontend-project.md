# Story 0.1: Initialize Vue Frontend Project

Status: done

## Story

As a developer,
I want to initialize the Vue 3.5+ frontend project with Vite 6,
So that I have a working development environment for building the SPA.

## Acceptance Criteria

1. **Given** I have Node.js 22+ installed
   **When** I run `npm create vue@latest porquinho-frontend` with options: TypeScript (Yes), Router (Yes), Pinia (Yes), Vitest (Yes), E2E Playwright (Yes), ESLint (Yes), Prettier (Yes)
   **Then** A complete Vue 3.5+ project with Vite is created

2. **And** Running `npm run dev` starts the dev server on http://localhost:5173

3. **And** The project includes Vue Router, Pinia state management, TypeScript support

4. **And** ESLint and Prettier are configured for code quality

5. **And** Vitest is configured for unit testing

6. **And** Playwright is configured for E2E testing

## Tasks / Subtasks

- [x] Initialize Vue project with create-vue (AC: #1)
  - [x] Run `npm create vue@latest porquinho-frontend`
  - [x] Select TypeScript: Yes
  - [x] Select Vue Router: Yes
  - [x] Select Pinia: Yes
  - [x] Select Vitest: Yes
  - [x] Select E2E Testing: Playwright
  - [x] Select ESLint: Yes
  - [x] Select Prettier: Yes

- [x] Verify project structure (AC: #1, #3)
  - [x] Confirm all directories created (src/, public/, etc.)
  - [x] Verify package.json includes all selected dependencies
  - [x] Verify TypeScript config files exist (tsconfig.json)

- [x] Install dependencies and test dev server (AC: #2)
  - [x] Run `npm install` in porquinho-frontend directory
  - [x] Run `npm run dev` and verify server starts on port 5173
  - [x] Verify hot module replacement (HMR) works

- [x] Verify development tooling (AC: #4, #5, #6)
  - [x] Run `npm run lint` to verify ESLint works
  - [x] Run `npm run format` to verify Prettier works
  - [x] Run `npm run test:unit` to verify Vitest setup
  - [x] Run `npm run test:e2e` to verify Playwright setup (may need headless flag)

## Dev Notes

### Critical Technical Requirements

**Versions (DO NOT DEVIATE):**
- Node.js: 22.12+ minimum
- Vue: 3.5+ (latest from create-vue)
- Vite: 7.x (comes with create-vue)
- TypeScript: 5.x

**Command to Execute:**
```bash
npm create vue@latest porquinho-frontend
```

**Interactive Prompts - Exact Selections Required:**
- ✔ Add TypeScript? → **Yes**
- ✔ Add JSX Support? → **No** (não necessário para Vue SFC)
- ✔ Add Vue Router? → **Yes**
- ✔ Add Pinia? → **Yes**
- ✔ Add Vitest for Unit Testing? → **Yes**
- ✔ Add an End-to-End Testing Solution? → **Playwright** (não Cypress)
- ✔ Add ESLint for code quality? → **Yes**
- ✔ Add Prettier for code formatting? → **Yes**

### Expected Project Structure

After initialization, the `porquinho-frontend/` directory should contain:

```
porquinho-frontend/
├── src/
│   ├── assets/          # Static assets (images, fonts)
│   ├── components/      # Vue components (shadcn-vue será adicionado aqui depois)
│   ├── router/          # Vue Router config
│   ├── stores/          # Pinia stores (state management)
│   ├── views/           # Page-level components
│   ├── App.vue
│   └── main.ts
├── public/              # Static files (servidos na raiz)
├── e2e/                 # Playwright E2E tests
├── src/                 # Source code
├── eslint.config.ts     # ESLint configuration (flat config)
├── .oxlintrc.json       # OxLint configuration
├── .prettierrc.json     # Prettier configuration
├── tsconfig.json        # TypeScript configuration
├── vite.config.ts       # Vite configuration
├── vitest.config.ts     # Vitest configuration
├── playwright.config.ts # Playwright configuration
├── package.json         # Dependencies
└── README.md
```

### Project Context Alignment

**Location:** This creates the frontend project in a SEPARATE directory from the backend (dual architecture approach).

**Directory Naming:** Must be `porquinho-frontend` (NOT `frontend/`, NOT `client/`). This aligns with the dual-stack architecture decision where frontend and backend are independent projects.

**Workspace Setup:** After this story, the project root should have:
```
porquinho/
├── porquinho-frontend/    # This story creates this
├── porquinho-backend/     # Story 0.2 will create this
└── _bmad-output/          # Planning artifacts
```

### Technology Rationale (From Architecture)

**Why create-vue:**
- Official starter maintained by Vue core team
- Zero-config Vite setup
- Includes all modern development tools
- Perfect alignment with project stack

**Why These Selections:**
- **TypeScript:** Type safety throughout the application, catches errors early
- **Vue Router:** Required for SPA navigation (Dashboard GPS multi-layer navigation)
- **Pinia:** Modern state management (replaced Vuex), required for global state
- **Vitest:** Fast unit testing with Vite integration
- **Playwright:** Modern E2E testing, better than Cypress for our use case
- **ESLint + Prettier:** Code quality and consistency enforcement

**What Comes Next:**
- Story 0.4: Add Tailwind CSS + shadcn-vue components
- Story 0.7-0.8: Implement design tokens and typography system
- Story 0.9: Setup accessibility foundation (WCAG 2.1 AA)

### Development Experience Notes

**After Initialization:**
- Dev server will run on `http://localhost:5173` (Vite default)
- HMR (Hot Module Replacement) is instantaneous
- TypeScript errors will show in real-time in the browser and console
- ESLint will run on save if IDE is configured properly

**Performance:**
- Vite dev server starts in < 1 second
- HMR updates happen in < 100ms
- This is CRITICAL for the "fast iteration" development workflow

**DO NOT:**
- Modify the default Vite config yet (Tailwind comes in story 0.4)
- Add any UI components yet (shadcn-vue comes in story 0.4)
- Create any application-specific code yet (this is just initialization)
- Change the default port 5173 (backend will handle CORS for this port)

### Verification Checklist

Before marking this story as complete:

1. ✅ `porquinho-frontend/` directory exists
2. ✅ `package.json` contains all required dependencies (vue, vue-router, pinia, vite, vitest, playwright, eslint, prettier, typescript)
3. ✅ `npm run dev` starts server successfully on port 5173
4. ✅ Browser opens and shows default Vue welcome page
5. ✅ `npm run lint` executes without errors
6. ✅ `npm run test:unit` executes (even if no tests yet)
7. ✅ `npm run build` successfully creates `dist/` folder
8. ✅ TypeScript compilation works (no TS errors in default files)

### References

- [Source: \_bmad-output/planning-artifacts/epics.md#Epic 0: Story 0.1]
- [Source: \_bmad-output/planning-artifacts/architecture.md#Frontend Stack (create-vue + Tailwind + shadcn-vue)]
- [Source: \_bmad-output/planning-artifacts/architecture.md#Initialization Commands]

## Dev Agent Record

### Agent Model Used

Claude Sonnet 4.5 (global.anthropic.claude-sonnet-4-5-20250929-v1:0)

### Implementation Notes

**Execution Summary:**
1. Verified Node.js version: 22.22.0 ✓ (meets requirement of 22.12+)
2. Executed `npm create vue@latest porquinho-frontend` with all required flags:
   - --typescript, --router, --pinia, --vitest, --playwright, --eslint, --prettier
3. Project created successfully with create-vue 3.22.0
4. Installed 429 npm packages (0 vulnerabilities)
5. Verified all tooling works correctly

**Versions Installed:**
- Vue: 3.5.29
- Vite: 7.3.1
- TypeScript: 5.9.3
- Vue Router: 5.0.3
- Pinia: 3.0.4
- Vitest: 4.0.18
- Playwright: 1.58.2

**Testing Results:**
- Dev server: Started in 870ms on http://localhost:5173 ✓
- ESLint: 0 warnings, 0 errors (21 files, 116 rules) ✓
- Prettier: Formatting successful ✓
- Vitest: 1 unit test passed ✓
- Playwright: 3 E2E tests passed across all browsers (Chromium, Firefox, WebKit) ✓
- Build: TypeScript compilation + Vite build completed in 817ms ✓

**Project Structure Verified:**
All expected directories created: src/, public/, e2e/, with proper configuration files for TypeScript, Vite, Vitest, Playwright, ESLint, and Prettier.

### Completion Notes List

✅ All acceptance criteria satisfied:
1. Vue 3.5+ project with Vite 6 (actually Vite 7.3.1) created successfully
2. Dev server runs on http://localhost:5173 with instant HMR
3. Vue Router, Pinia, TypeScript all configured and working
4. ESLint and Prettier configured and functional
5. Vitest configured with passing tests
6. Playwright configured with E2E tests passing on all browsers

✅ All tasks completed:
- Project initialized with all correct selections
- Project structure verified
- Dependencies installed (429 packages)
- Dev server tested and confirmed working
- All development tooling verified (lint, format, test:unit, test:e2e, build)

**Ready for Next Steps:**
- Story 0.4: Add Tailwind CSS + shadcn-vue
- Story 0.7-0.8: Implement design tokens
- Story 0.9: Setup accessibility foundation

### Files Created/Modified

**All files created in porquinho-frontend/ directory:**

Configuration files:
- package.json, package-lock.json
- tsconfig.json, tsconfig.app.json, tsconfig.node.json, tsconfig.vitest.json
- vite.config.ts, vitest.config.ts
- eslint.config.ts, .oxlintrc.json
- playwright.config.ts
- .prettierrc.json, .editorconfig
- .gitignore, .gitattributes
- .vscode/extensions.json, .vscode/settings.json
- README.md, index.html, env.d.ts

Asset files:
- src/assets/base.css
- src/assets/main.css
- src/assets/logo.svg

Source files:
- src/main.ts
- src/App.vue
- src/router/index.ts
- src/stores/counter.ts
- src/views/HomeView.vue, src/views/AboutView.vue
- src/components/HelloWorld.vue, src/components/TheWelcome.vue, src/components/WelcomeItem.vue
- src/components/icons/IconCommunity.vue, IconDocumentation.vue, IconEcosystem.vue, IconSupport.vue, IconTooling.vue
- src/components/__tests__/HelloWorld.spec.ts

E2E tests:
- e2e/vue.spec.ts
- e2e/tsconfig.json

### Code Review Record

**Reviewer:** Claude Opus 4.6
**Date:** 2026-03-14

**Fixes Applied:**
1. [L1] Fixed `<html lang="">` to `<html lang="pt-BR">` in `index.html` (WCAG 2.1 AA - criterion 3.1.1)
2. [L2] Updated AC and Dev Notes: Vite 6.x -> Vite 7.x (reflects actual create-vue output)
3. [L3] Updated expected project structure: `.eslintrc.cjs` -> `eslint.config.ts` (flat config), added `.oxlintrc.json`
4. [L4] Added missing files to File List: `src/assets/base.css`, `src/assets/main.css`, `src/assets/logo.svg`

**Notes:**
- [M1] Projeto sem repositório git inicializado. Recomenda-se executar `git init` no diretório raiz `porquinho/` para controle de versão.
- Todos os Acceptance Criteria verificados e implementados corretamente.
- Todas as tasks marcadas [x] confirmadas como realmente concluídas.
