# Accessibility Component Audit — shadcn-vue / reka-ui

**Data:** 2026-03-14
**Story:** 0-9 Setup Accessibility Foundation (WCAG 2.1 AA)
**Base library:** reka-ui ^2.9.2 (primitives headless acessiveis)

## Resumo

Os componentes shadcn-vue instalados neste projeto usam **reka-ui** como base headless,
que fornece ARIA roles e keyboard navigation nativamente, seguindo o padrao WAI-ARIA.

---

## Componentes Auditados

### Button

| Item | Status | Detalhes |
|------|--------|----------|
| ARIA role | ✅ | `role="button"` nativo via `<button>` HTML |
| `aria-disabled` | ✅ | Aplicado via prop `disabled` |
| Keyboard: Enter | ✅ | Aciona o click |
| Keyboard: Space | ✅ | Aciona o click |
| Focus visible | ✅ | Herda `*:focus-visible` do main.css |
| Touch target 44px | ✅ | Configurado via `min-height: 2.75rem` no main.css |

**Interacoes de teclado:**
- `Tab` — navega para o botao
- `Enter` / `Space` — aciona o botao
- `Shift+Tab` — volta o foco

---

### Input

| Item | Status | Detalhes |
|------|--------|----------|
| `<input>` semantico | ✅ | Elemento nativo HTML |
| `aria-label` / `aria-labelledby` | ⚠️ | Depende do uso correto no contexto (via `<label>`) |
| `aria-invalid` | ✅ | Suportado via reka-ui FormField |
| `aria-describedby` | ✅ | Suportado via reka-ui FormMessage |
| Keyboard navigation | ✅ | Nativo do browser |
| Focus visible | ✅ | Herda `*:focus-visible` |

**Nota:** Sempre associar `<label>` ao input com `for` / `htmlFor` ou usar `aria-label`.

---

### Card

| Item | Status | Detalhes |
|------|--------|----------|
| Elemento semantico | ✅ | Renderiza como `<div>` — considerar `<article>` ou `<section>` quando conteudo independente |
| Sem role especifico | ✅ | Correto — Card e container de apresentacao |
| CardTitle como heading | ✅ | Usa `<h3>` por padrao — verificar hierarquia no contexto de uso |

**Nota:** Use `<article>` quando o Card contem conteudo independente e semanticamente completo.

---

### Dialog (Modal)

| Item | Status | Detalhes |
|------|--------|----------|
| `role="dialog"` | ✅ | Fornecido pelo reka-ui DialogRoot |
| `aria-modal="true"` | ✅ | Fornecido pelo reka-ui |
| `aria-labelledby` | ✅ | Conectado ao DialogTitle automaticamente |
| `aria-describedby` | ✅ | Conectado ao DialogDescription automaticamente |
| Focus trap | ✅ | reka-ui implementa focus trap automatico |
| Keyboard: Esc | ✅ | Fecha o dialog |
| Keyboard: Tab | ✅ | Navega dentro do dialog (trapped) |

**Interacoes de teclado:**
- `Esc` — fecha o dialog
- `Tab` / `Shift+Tab` — navega entre elementos dentro do dialog
- Foco retorna ao elemento que abriu o dialog ao fechar

---

### Select

| Item | Status | Detalhes |
|------|--------|----------|
| `role="combobox"` | ✅ | Fornecido pelo reka-ui SelectRoot |
| `aria-expanded` | ✅ | Atualizado automaticamente |
| `aria-haspopup="listbox"` | ✅ | Correto para select |
| `role="listbox"` no dropdown | ✅ | Fornecido pelo SelectContent |
| `role="option"` nos itens | ✅ | Fornecido pelo SelectItem |
| `aria-selected` | ✅ | Atualizado no item selecionado |
| Keyboard: Enter/Space | ✅ | Abre o select |
| Keyboard: Arrow Up/Down | ✅ | Navega entre opcoes |
| Keyboard: Esc | ✅ | Fecha sem selecionar |

**Interacoes de teclado:**
- `Enter` / `Space` — abre o select
- `Arrow Down` / `Arrow Up` — navega entre opcoes
- `Enter` — seleciona a opcao em foco
- `Esc` — fecha o dropdown
- `Home` / `End` — vai para primeira / ultima opcao

---

## Resumo de ARIA Roles por Componente

| Componente | Role Principal | Focus Trap | Keyboard Nav |
|------------|---------------|-----------|--------------|
| Button | `button` (nativo) | Nao | Tab, Enter, Space |
| Input | `textbox` (nativo) | Nao | Tab, digitacao |
| Card | nenhum (apresentacao) | Nao | N/A |
| Dialog | `dialog` | Sim | Tab, Esc |
| Select | `combobox` → `listbox` | Sim (dropdown) | Tab, Enter, Arrows, Esc |

---

## Recomendacoes de Uso

1. **Button:** Usar `aria-label` quando o botao contem apenas icone (sem texto visivel)
2. **Input:** Sempre associar um `<label>` visivel ou `aria-label`
3. **Card com link:** Se o Card inteiro e clicavel, usar `role="link"` ou envolver em `<a>`
4. **Dialog:** Sempre incluir `DialogTitle` (pode ser `sr-only` se nao quiser exibir visualmente)
5. **Select:** Incluir `aria-label` no SelectTrigger quando nao houver label visivel proxima

---

## Referencia

- [reka-ui Accessibility](https://reka-ui.com/docs/overview/accessibility)
- [WAI-ARIA Authoring Practices](https://www.w3.org/WAI/ARIA/apg/)
- [WCAG 2.1 Level AA](https://www.w3.org/TR/WCAG21/)
