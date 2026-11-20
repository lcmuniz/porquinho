<!-- impeccable:product-schema 1 -->
# PRODUCT.md: porquinho

## Platform

web: SPA responsiva. O uso principal é no desktop, e o celular (mobile web) também precisa funcionar bem.

## Users

- Pessoa ou casal brasileiro que organiza as finanças da casa. Tem renda estável, mas o dinheiro não sobra no fim do mês. Muitos já tentaram o YNAB ou outras ferramentas e desistiram porque dava trabalho demais e o progresso não aparecia.
- O uso típico é um ritual semanal de cerca de 5 minutos, no fim de semana, quase sempre no desktop. No celular, a pessoa faz consultas rápidas.
- Há também um papel de admin, com um painel interno de métricas, IA e saúde do sistema.

## Product Purpose

Tornar o controle financeiro pessoal recompensador em vez de penoso. O usuário deve conseguir responder em poucos minutos três perguntas: "onde estou?", "para onde vou?" e "preciso ajustar a rota?". O objetivo concreto é manter o uso por meses e formar a reserva de emergência e as metas.

## Positioning

É o "YNAB brasileiro com IA":
- orçamento por envelope ("gastar olhando o software, não o saldo do banco");
- adaptado ao Brasil: OFX/CSV de bancos brasileiros, parcelamento sem juros, Pix, várias contas e cartões;
- IA que classifica as transações importadas e aprende os padrões do usuário;
- Dashboard "GPS Financeiro" em 3 camadas que mostra o progresso.

O preço é em reais: R$ 19,90/mês, com 30 dias de teste.

## Operating Context

- É um app de operação. O sucesso é concluir o ritual: importar, revisar a classificação, conferir o orçamento e ver o dashboard.
- As telas centrais são dashboard, transações, importação, orçamento, metas, contas e cartões, além de configurações, assinatura e exportação de dados.
- O usuário volta toda semana. Por isso, previsibilidade e leitura rápida de números valem mais que expressão visual.

## Capabilities and Constraints

- Acessibilidade WCAG 2.1 AA é obrigatória (NFR). Vale para contraste, teclado, leitor de tela e alvos de toque de 44px.
- LGPD: os dados são financeiros e sensíveis. Consentimento, exportação de dados (portabilidade) e exclusão de conta precisam estar visíveis e fáceis de achar.
- A assinatura é via Stripe. Quem não converte tem o acesso encerrado e os dados apagados depois do prazo de retenção.
- Decisão em aberto: dark mode. O app hoje declara apenas `color-scheme: light`, e o tema escuro não foi definido como restrição nem como requisito.

## Brand Commitments

- Tom sem julgamento. Nunca culpar o usuário pelos gastos. Insights falam a língua da descoberta ("você gastou R$ 1.200 em delivery"), não a do sermão.
- Alertas são calmos e informativos, nunca agressivos. Erros dizem o que aconteceu e o que fazer.
- O sentimento-alvo é controle sem estresse: empoderamento ativo junto com tranquilidade.
- Confiança na IA: o usuário precisa entender o que ela fez e poder corrigir com facilidade.

## Evidence on Hand

- Product brief: `_bmad-output/planning-artifacts/product-brief-porquinho-2026-03-13.md`
- PRD (jornadas, NFRs e escopo): `_bmad-output/planning-artifacts/prd/`
- UX spec (resposta emocional e padrões): `_bmad-output/planning-artifacts/ux-design-specification/`
- Design system atual: `design-system/porquinho/MASTER.md` e `design-system/porquinho/PLANO.md`

## Product Principles

1. Pouco esforço, muito retorno visível. Cada tela mostra o progresso, não só o registro do passado.
2. Visão em 30 segundos. A camada 1 do dashboard responde ao essencial, e os detalhes ficam a um clique.
3. A IA trabalha e o usuário confirma. A IA classifica, o usuário revisa as exceções, e a correção é sempre simples.
4. O envelope é a verdade. O orçamento por categoria orienta as decisões, mais do que o saldo bancário.
5. O Brasil vem por padrão: R$, datas pt-BR, parcelamento, Pix e bancos locais.

## Accessibility & Inclusion

- WCAG 2.1 AA. A cor nunca carrega significado sozinha: status sempre vem com ícone e texto.
- Navegação completa por teclado, com foco visível, e `prefers-reduced-motion` respeitado.
- Valores monetários em `tabular-nums`, legíveis em telas pequenas. O texto mínimo é de 13px.
- A linguagem é simples, sem jargão financeiro desnecessário.
