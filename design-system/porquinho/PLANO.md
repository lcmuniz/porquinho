# Plano de design — redesign "novo-design"

Trabalha dentro de `MASTER.md`. Feito com a skill `frontend-design` em 2026-11-20.

## Assunto, público, trabalho

- **Assunto:** orçamento por envelope para famílias brasileiras. O nome e a marca vêm do cofrinho.
- **Público:** quem já desistiu de planilha e de app de finanças. Usa no celular, num check-in semanal de 5 minutos.
- **Trabalho principal da interface:** responder "onde estou, para onde vou, preciso ajustar?" sem esforço de leitura.

## Tokens (resumo do MASTER)

| Nome | Hex | Papel |
|------|-----|-------|
| Tinta | `#0F172A` | texto e números |
| Névoa | `#F5F7FA` | fundo da página |
| Azul-cofre | `#1E40AF` | marca, ação, navegação ativa |
| Verde-rendimento | `#047857` | entrada, progresso, meta no caminho |
| Âmbar-alerta | `#B45309` | categoria perto do limite |
| Vermelho-estouro | `#B91C1C` | estouro, erro, saldo negativo |

**Tipo:** Plus Jakarta Sans numa família só. Títulos em 700 com tracking levemente negativo (−0.01em). Corpo em 400. Todo número usa `tabular-nums`, então as colunas de valores alinham pela vírgula. A escala tem piso de 13px: `text-xs` foi redefinido para 13px, e nenhum texto do app fica abaixo disso.

## Layout

Shell único para todas as telas logadas:

```
≥1024px
┌────────────┬──────────────────────────────────────┐
│ ● porquinho│  Título da página            [ação]  │
│            │                                      │
│ Início     │  ┌──────────────┐ ┌───────┐          │
│ Transações │  │ conteúdo     │ │       │          │
│ Orçamento  │  └──────────────┘ └───────┘          │
│ Metas      │                                      │
│ ─────────  │                                      │
│ Contas     │                                      │
│ Cartões    │                                      │
│ Categorias │                                      │
│ Importar   │                                      │
│            │                                      │
│ Configurações                                     │
│ Sair       │                                      │
└────────────┴──────────────────────────────────────┘

<1024px
┌─────────────────────────────┐
│ ● porquinho                 │  topbar
├─────────────────────────────┤
│ conteúdo em 1 coluna        │
│ 16px de margem lateral      │
├─────────────────────────────┤
│ Início Transações Orçam. Metas Mais │ barra inferior fixa
└─────────────────────────────┘
```

- Alinhamento à esquerda em tudo. Os números ficam alinhados à direita nas listas e tabelas, onde a leitura é por coluna.
- A navegação tem dois grupos com significado: "dia a dia" (Início, Transações, Orçamento, Metas) e "estrutura" (Contas, Cartões, Categorias, Importar). Um divisor separa os grupos porque eles são de fato diferentes, não por decoração.
- As configurações (alerta por e-mail, sessões, exportar dados, assinatura, excluir conta) saem do rodapé do dashboard e vão para uma tela própria, `/configuracoes`. O dashboard fica só com o "GPS".

## Princípios deste produto

1. **O valor é o herói.** Em cada cartão, o número vem antes e maior que o rótulo. Rótulo em 14px `--muted-foreground`, valor em 28–32px/700.
2. **Vermelho é raro.** Gasto normal fica em tinta. Só estouro, erro e saldo negativo são vermelhos. Assim o vermelho volta a significar "olhe aqui".
3. **Uma ousadia só:** o semáforo de saúde financeira no topo do dashboard vira uma faixa larga, com a cor do estado e uma frase direta ("Você está dentro do orçamento em 9 de 11 categorias"). Ele é o primeiro elemento da tela. Todo o resto é sóbrio.
4. **Sem cromo de template:** nada de rótulos em CAIXA ALTA acima de títulos, nada de "→" em botões, nada de numeração decorativa (os passos do onboarding mantêm números porque são uma sequência real).

## Revisão contra o brief (antes de construir)

| Parte do plano | Parece default genérico? | Decisão |
|----------------|---------------------------|---------|
| Fundo creme + serifa | Não usado | — |
| Kit SaaS de cartões idênticos com sombra cinza | **Sim, risco.** O app já é feito de cartões. | Cartões sem sombra (borda 1px apenas) e raio diferente por hierarquia: 12px em cartões, 10px em controles, pílula em badges/progresso. O semáforo é uma faixa, não um cartão. |
| Primária azul genérica (`#3B82F6`) | Seria default | Usei o azul-800 `#1E40AF` da linha *Personal Finance Tracker*: mais fechado, de "cofre", com contraste 8.7:1. Sem gradiente. |
| Fonte Inter | Default de qualquer SaaS | Troquei por Plus Jakarta Sans, mais redonda e amigável, coerente com o "cofrinho", e com números tabulares. |
| Eyebrows em CAIXA ALTA, meta com "·" | Tell de template | Proibido no app. Busco e removo `uppercase tracking-wide` dos rótulos. |
| Barra inferior no mobile | Padrão de app, mas justificado | O uso é no celular. 5 destinos é o limite recomendado (ux `bottom-nav-limit`). |

## Execução

1. `main.css`: novos tokens. As escalas `gray`, `purple`, `green`, `red`, `amber` e `yellow` do Tailwind são remapeadas para a paleta do MASTER. As ~1.500 classes existentes herdam o novo visual sem tocar em cada view. `purple-*` passa a ser a escala azul-cofre (nome legado, valor novo).
2. Fonte Plus Jakarta Sans auto-hospedada, `tabular-nums` global, piso de 13px.
3. `AppShell.vue` com sidebar e topbar/barra inferior. As rotas logadas usam o shell.
4. `SettingsView.vue` (`/configuracoes`) recebe as configurações do dashboard.
5. Ajuste fino por tela: cabeçalhos de página, cartões de valor, listas que viram cartões no mobile, cores de gráfico.
