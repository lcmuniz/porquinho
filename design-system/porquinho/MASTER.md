# Design System — Porquinho (MASTER)

> **Regra:** ao construir uma página, veja primeiro `design-system/porquinho/pages/<pagina>.md`.
> Se existir, ele **sobrepõe** este arquivo. Senão, siga este arquivo.

Gerado com a skill `ui-ux-pro-max` em 2026-11-20. Fontes no banco da skill:
- Estilo: **Minimalism & Swiss Style** (consulta `personal finance budgeting fintech app`, dials variance 4, motion 3, density 7).
- Paleta: linha **Personal Finance Tracker** ("trust blue + profit green"), adaptada para modo claro como padrão. As superfícies claras vêm da linha *Banking/Traditional Finance*.
- Tipografia: **Plus Jakarta Sans**, família única. Vem da linha *Enterprise SaaS Mobile* ("finance mobile apps, legible").

Descartado: a sugestão inicial da skill (dourado + roxo sobre navy escuro, IBM Plex). O fundo escuro como padrão e o roxo como CTA batem com o anti-padrão "AI purple gradients". O tom ficaria sério demais para o público, que são famílias brasileiras fazendo um check-in semanal de 5 minutos.

---

## Princípios

1. **Número primeiro.** A tela responde "onde estou?" em 30 segundos. Valores em reais são o elemento de maior peso visual da tela, nunca o rótulo.
2. **Legibilidade acima de densidade.** Nenhum texto abaixo de 13px. Valores monetários com `tabular-nums` e pelo menos 15px.
3. **Cor com significado.** Azul = ação e marca. Verde = entrada, progresso, meta no caminho. Vermelho = saída acima do previsto e erro. Âmbar = atenção. Cor nunca é o único sinal: sempre há sinal (+/−), ícone ou texto.
4. **Calma, não alarme.** Gasto comum não é vermelho. Despesa aparece em cor neutra; o vermelho fica reservado para estouro de orçamento, saldo negativo e erro.
5. **Superfícies planas.** Bordas de 1px e sombra mínima. Sem glassmorphism, sem gradientes decorativos.

---

## Cores

Os tokens ficam em `porquinho-frontend/src/assets/main.css` (variáveis CSS + `@theme inline` do Tailwind 4). Componentes usam só tokens, nunca hex.

### Modo claro (padrão)

| Papel | Token | Hex | Contraste |
|------|-------|-----|-----------|
| Fundo da página | `--background` | `#F5F7FA` | — |
| Cartão | `--card` | `#FFFFFF` | — |
| Texto | `--foreground` | `#0F172A` | 17.9:1 no cartão |
| Texto secundário | `--muted-foreground` | `#475569` | 7.6:1 no cartão |
| Superfície suave | `--muted` | `#EEF2F7` | — |
| Borda | `--border` | `#E2E8F0` | — |
| Primária (marca/ação) | `--primary` | `#1E40AF` | 8.7:1 com branco |
| Primária suave | `--primary-subtle` | `#E8EEFC` | — |
| Positivo (entrada/progresso) | `--success` | `#047857` | 5.5:1 no cartão |
| Positivo suave | `--success-subtle` | `#E3F5EE` | — |
| Atenção | `--warning` | `#B45309` | 5.0:1 no cartão |
| Atenção suave | `--warning-subtle` | `#FDF1E1` | — |
| Erro/estouro | `--error` | `#B91C1C` | 6.5:1 no cartão |
| Erro suave | `--error-subtle` | `#FDECEC` | — |
| Foco | `--ring` | `#2563EB` | — |

### Modo escuro

| Papel | Hex |
|------|-----|
| Fundo | `#0B1220` |
| Cartão | `#111A2E` |
| Texto | `#F1F5F9` |
| Texto secundário | `#A3B1C6` |
| Superfície suave | `#1A2438` |
| Borda | `#24324A` |
| Primária | `#7AA2F7` (texto sobre ela: `#0B1220`) |
| Positivo | `#34D399` |
| Atenção | `#FBBF24` |
| Erro | `#F87171` |

### Gráficos (ordem fixa por série)

`#1E40AF` azul · `#059669` verde · `#D97706` âmbar · `#0284C7` céu · `#E11D48` rosa · `#64748B` ardósia.
Legenda sempre visível. Cada série também é identificada por rótulo, nunca só pela cor.

---

## Tipografia

- **Família:** Plus Jakarta Sans (variável, auto-hospedada via `@fontsource-variable/plus-jakarta-sans`), com fallback `ui-sans-serif, system-ui`.
- **Números:** `font-variant-numeric: tabular-nums` em todo valor monetário, percentual e data em tabela (classe utilitária `.num`).

| Uso | Tamanho / altura | Peso |
|-----|------------------|------|
| Título da página | 24px / 32px (28px ≥ md) | 700 |
| Título de seção/cartão | 16px / 24px | 600 |
| Valor-destaque (KPI) | 28px / 36px (32px ≥ md) | 700, tabular |
| Valor em linha/tabela | 15px / 22px | 600, tabular |
| Corpo | 15px / 24px | 400 |
| Rótulo / meta | 14px / 20px | 500 |
| Badge / legenda | 13px / 18px | 600 |

**Proibido:** texto de 12px ou menos, cinza sobre cinza, rótulo só como placeholder.

---

## Espaçamento e forma

- Escala de 4px: 4 · 8 · 12 · 16 · 24 · 32 · 48.
- Padding de cartão: 16px (mobile) / 24px (≥ md). Gap entre cartões: 16px / 24px.
- Raio: 12px nos cartões, 10px em botões e inputs, 999px em badges e barras de progresso.
- Sombra: só `0 1px 2px rgb(15 23 42 / 0.06)` nos cartões. Modais usam `0 16px 40px rgb(15 23 42 / 0.18)`.
- Largura máxima do conteúdo: 1200px.

## Layout e navegação

- **≥ lg (1024px):** sidebar fixa de 248px à esquerda com logo, navegação primária agrupada e menu do usuário no rodapé. O conteúdo fica à direita.
- **< lg:** topbar com logo e menu do usuário, mais uma **barra inferior** com 5 destinos (Início, Transações, Orçamento, Metas, Mais). "Mais" abre uma folha com o restante.
- Sem scroll horizontal da página em 390px. Tabelas largas viram lista de cartões no mobile.
- Alvos de toque ≥ 44×44px, com 8px entre alvos.

## Componentes

- **Botão primário:** fundo `--primary`, texto branco, 44px de altura, peso 600, raio 10px. O hover escurece 8%. Sem `translateY`.
- **Botão secundário:** fundo `--card`, borda `--border`, texto `--foreground`.
- **Botão destrutivo:** fundo `--error`, texto branco. Exige confirmação.
- **Cartão:** `--card`, borda 1px `--border`, raio 12px, sombra mínima. O cartão só é clicável quando a ação é óbvia, e nesse caso ganha um hover na borda.
- **Input:** 44px, borda `--border`, foco com anel de 3px `--ring` a 25%. Rótulo visível acima e erro abaixo do campo.
- **Badge de status:** fundo `*-subtle`, texto na cor sólida, 13px/600, com ícone ou texto que explique o estado.
- **Barra de progresso:** trilho `--muted` de 8px, preenchimento verde (no caminho), âmbar (≥ 90%) ou vermelho (> 100%). Mostra o percentual em texto ao lado.
- **Valor monetário:** `R$ 1.234,56`, tabular. Entrada com prefixo `+` em `--success`. Saída com `−` em `--foreground`.
- **Estado vazio:** ícone Lucide de 40px, uma frase do que falta e um CTA.

## Movimento

- Transições de 150–200ms `ease-out` em cor, borda e opacidade. Nada de animar largura ou altura.
- Barras de progresso podem animar o preenchimento por `transform: scaleX` em 400ms.
- `prefers-reduced-motion` desliga tudo (já está no `main.css`).

## Ícones

Só Lucide (`lucide-vue-next`), 20px na navegação e 16–18px inline, com `stroke-width` 2. Sem emoji como ícone. Ícone decorativo leva `aria-hidden="true"`. Botão só com ícone leva `aria-label`.

---

## Anti-padrões

- ❌ Gradientes roxo/rosa, glassmorphism, sombras fortes
- ❌ Despesa comum em vermelho
- ❌ Texto ≤ 12px, números sem `tabular-nums`
- ❌ Emoji como ícone
- ❌ Hover que desloca o layout
- ❌ Foco invisível
- ❌ Scroll horizontal no mobile

## Checklist de entrega

- [ ] Contraste de texto ≥ 4.5:1 nos dois temas
- [ ] Foco visível em todo elemento interativo
- [ ] `cursor-pointer` em todo clicável
- [ ] Valores com `.num` (tabular)
- [ ] Sem scroll horizontal em 390px; layout validado em 390 e 1440
- [ ] Estados vazio, carregando e erro em toda lista
- [ ] `prefers-reduced-motion` respeitado
