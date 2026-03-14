# Guia de Testes de Acessibilidade — porquinho

**Story:** 0-9 Setup Accessibility Foundation (WCAG 2.1 AA)
**Atualizado em:** 2026-03-14

---

## Ferramentas Configuradas

| Ferramenta | Tipo | Uso |
|------------|------|-----|
| `@axe-core/playwright` | Automatizado E2E | Deteccao de violacoes WCAG no CI |
| axe DevTools (extensao browser) | Manual | Inspecao durante desenvolvimento |
| Lighthouse CLI | Automatizado/Manual | Score de acessibilidade por pagina |
| NVDA (Windows) | Manual | Teste com leitor de tela |
| JAWS (Windows) | Manual | Teste com leitor de tela (corporativo/governo) |
| VoiceOver (macOS/iOS) | Manual | Teste com leitor de tela |

---

## 1. Testes Automatizados com axe-core

### Executar testes de acessibilidade E2E

```bash
# Rodar apenas os testes de acessibilidade
npm run test:a11y

# Rodar todos os testes E2E (inclui a11y)
npm run test:e2e
```

### O que e testado automaticamente

- Pagina `/design-system`: sem violacoes WCAG 2.1 A e AA
- Pagina `/` (home): sem violacoes WCAG 2.1 A e AA
- Skip link como primeiro elemento focusavel
- Tamanho minimo de touch targets (44px) em botoes

### Adicionar teste a11y para nova pagina

```typescript
// e2e/accessibility.spec.ts
import AxeBuilder from '@axe-core/playwright'

test('nova pagina tem sem violacoes a11y', async ({ page }) => {
  await page.goto('/nova-rota')
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa'])
    .analyze()
  expect(results.violations).toEqual([])
})
```

---

## 2. Auditoria com Lighthouse CLI

### Instalacao (uma vez)

```bash
npm install -g lighthouse
```

### Executar auditoria

```bash
# 1. Inicie o servidor de desenvolvimento
npm run dev

# 2. Em outro terminal, execute o Lighthouse
lighthouse http://localhost:5173/design-system \
  --only-categories=accessibility \
  --output=json \
  --output-path=./lighthouse-a11y-report.json

# Ou para report HTML
lighthouse http://localhost:5173/design-system \
  --only-categories=accessibility \
  --output=html \
  --output-path=./lighthouse-a11y-report.html
```

### Score minimo esperado

- **Acessibilidade:** > 90 (requisito AC #6 da Story 0-9)

---

## 3. Testes com Leitor de Tela

### NVDA (Windows) — Gratuito

**Download:** https://www.nvaccess.org/download/

**Setup rapido:**
1. Instalar NVDA
2. Abrir o browser (Chrome ou Firefox recomendados)
3. Iniciar NVDA: `Ctrl+Alt+N`

**Atalhos essenciais para testar:**

| Atalho | Acao |
|--------|------|
| `Insert+F7` | Lista todos os elementos da pagina |
| `H` | Navega entre headings |
| `B` | Navega entre botoes |
| `F` | Navega entre campos de formulario |
| `L` | Navega entre links |
| `Tab` | Proximo elemento interativo |
| `Shift+Tab` | Elemento interativo anterior |
| `Insert+Space` | Ativa elemento em foco |

**Checklist de teste com NVDA:**

- [ ] Carregar a pagina — NVDA anuncia o titulo da pagina?
- [ ] Pressionar Tab — Skip link e anunciado e funciona?
- [ ] Navegar com `H` — Headings tem hierarquia logica (h1 → h2 → h3)?
- [ ] Abrir Select — NVDA anuncia "combobox" e opcoes?
- [ ] Abrir Dialog — NVDA anuncia o titulo do dialog? Foco vai para o dialog?
- [ ] Fechar Dialog com Esc — Foco retorna ao elemento original?
- [ ] Formularios — Todos os inputs tem labels anunciadas?

---

### JAWS (Windows) — Pago / Corporativo

**Download/trial:** https://www.freedomscientific.com/products/software/jaws/

**Setup rapido:**
1. Instalar JAWS (requer licenca ou trial de 40 minutos)
2. Abrir o browser (Chrome ou Edge recomendados com JAWS)
3. JAWS inicia automaticamente ao fazer login (ou via atalho no Desktop)

**Atalhos essenciais para testar:**

| Atalho | Acao |
|--------|------|
| `Insert+F6` | Lista todos os headings da pagina |
| `Insert+F7` | Lista todos os links da pagina |
| `H` | Navega entre headings |
| `B` | Navega entre botoes |
| `F` | Navega entre campos de formulario |
| `Tab` | Proximo elemento interativo |
| `Shift+Tab` | Elemento interativo anterior |
| `Enter` | Ativa link ou botao |
| `Insert+F1` | Ajuda de contexto |

**Checklist de teste com JAWS:**

- [ ] Carregar a pagina — JAWS anuncia o titulo da pagina (`<title>`)?
- [ ] Pressionar Tab — Skip link e anunciado como "Pular para o conteudo principal, link"?
- [ ] Navegar com `H` — Headings tem hierarquia logica (h1 → h2 → h3)?
- [ ] Abrir Select — JAWS anuncia "combobox" e numero de opcoes?
- [ ] Abrir Dialog — JAWS anuncia `role="dialog"` e o titulo do dialog?
- [ ] Fechar Dialog com Esc — Foco retorna ao elemento original?
- [ ] Formularios — Todos os inputs tem labels anunciadas corretamente?

---

### VoiceOver (macOS)

**Ativar:** `Cmd+F5`
**Desativar:** `Cmd+F5`

**Atalhos essenciais:**

| Atalho | Acao |
|--------|------|
| `VO+A` | Ler pagina inteira (VO = Ctrl+Option) |
| `VO+H` | Navegar por headings |
| `VO+J` | Ir para proximo form element |
| `Tab` | Proximo elemento interativo |
| `VO+Space` | Ativar elemento |

**VoiceOver no iOS:**

- Ativar em: Ajustes → Acessibilidade → VoiceOver
- Deslizar para direita — proximo elemento
- Duplo toque — ativar elemento
- Deslizar 3 dedos — rolar a pagina

---

### VoiceOver (iOS) — Teste Mobile

**Checklist especifico para mobile:**

- [ ] Touch targets >= 44x44px — elementos sao faceis de tocar?
- [ ] Pinch to zoom nao esta desabilitado (`user-scalable=no`)?
- [ ] Conteudo acessivel em orientacoes portrait e landscape?
- [ ] Contraste suficiente em tela com brilho reduzido?

---

## 4. Validacao de Contraste de Cores

### Ferramenta axe DevTools (Chrome/Firefox)

1. Instalar extensao [axe DevTools](https://www.deque.com/axe/devtools/)
2. Abrir DevTools (`F12`)
3. Ir para aba "axe DevTools"
4. Clicar "Scan ALL of my page"
5. Verificar categoria "Color Contrast"

### Verificacao manual com Chrome DevTools

1. Abrir DevTools → Aba "Elements"
2. Selecionar elemento de texto
3. Clicar no color swatch no painel CSS
4. Chrome exibe o ratio de contraste automaticamente

### Requisitos WCAG 2.1 AA

| Tipo de texto | Ratio minimo |
|---------------|-------------|
| Texto normal (< 18pt / < 14pt bold) | 4.5:1 |
| Texto grande (>= 18pt / >= 14pt bold) | 3:1 |
| Componentes UI e graficos | 3:1 |

---

## 5. Teste de Navegacao por Teclado (Procedure)

### Procedure completo para cada pagina nova

1. **Abrir a pagina** sem usar o mouse
2. **Pressionar Tab** — verificar se skip link aparece
3. **Ativar skip link** (Enter) — verificar se foco vai para `#main-content`
4. **Continuar com Tab** — verificar se tab order e logico (esquerda para direita, cima para baixo)
5. **Verificar focus indicator** — outline roxo visivel em todos os elementos?
6. **Testar modais/dropdowns** — foco fica preso dentro do componente?
7. **Fechar componentes** — foco retorna para elemento original?
8. **Verificar formularios** — Enter submete? Tab navega entre campos?

---

## 6. Checklist de Validacao Continua

Use este checklist ao adicionar novas features:

### Desenvolvimento

- [ ] HTML semantico usado (headings, lists, nav, main, section, article)
- [ ] Todos os inputs tem labels associadas
- [ ] Imagens tem `alt` text descritivo (ou `alt=""` para decorativas)
- [ ] Icones sem texto tem `aria-label`
- [ ] Links tem texto descritivo (nao apenas "clique aqui")
- [ ] Tab order e logico sem CSS `order` artificial

### Componentes

- [ ] Componentes shadcn-vue usados sem remover ARIA attributes
- [ ] Custom components seguem WAI-ARIA patterns
- [ ] Focus management em modais e drawers

### Testes

- [ ] `npm run test:a11y` passa sem violations
- [ ] Testado com teclado (Tab, Enter, Esc, Arrow keys)
- [ ] Score Lighthouse >= 90 na pagina nova

---

## Referencias

- [WCAG 2.1 Quick Reference](https://www.w3.org/WAI/WCAG21/quickref/)
- [WAI-ARIA Authoring Practices Guide](https://www.w3.org/WAI/ARIA/apg/)
- [axe-core Rules](https://github.com/dequelabs/axe-core/blob/develop/doc/rule-descriptions.md)
- [reka-ui Accessibility](https://reka-ui.com/docs/overview/accessibility)
- [Deque University](https://dequeuniversity.com/)
