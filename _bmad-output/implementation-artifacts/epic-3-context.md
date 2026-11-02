# Epic 3 Context: Guided Onboarding Experience

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Levar o usuário recém-cadastrado (já com trial ativo) por um fluxo guiado de 8 passos que termina com a conta pronta para uso. O fluxo explica o orçamento por envelope, cadastra contas bancárias e cartões, sugere e personaliza categorias, faz a primeira importação de extrato, apresenta o Dashboard GPS de 3 camadas e cria a meta de Reserva de Emergência. O onboarding é o ponto de maior risco de abandono do produto. A primeira importação, com a IA classificando a maior parte das transações sozinha, é o momento que decide se o usuário fica. As metas são 70%+ de conclusão e tempo médio abaixo de 20 minutos.

## Stories

- Story 3.1: Boas-vindas e tutorial do conceito de orçamento por envelope
- Story 3.2: Adicionar contas bancárias no setup
- Story 3.3: Adicionar cartões de crédito no setup
- Story 3.4: Sugerir categorias de gastos iniciais
- Story 3.5: Personalizar as categorias sugeridas
- Story 3.6: Primeira importação de transações guiada
- Story 3.7: Explicar a navegação em 3 camadas do Dashboard GPS
- Story 3.8: Criar a meta inicial de Reserva de Emergência

## Requirements & Constraints

- Fluxo linear de 8 passos com indicador "Passo X de 8", botões Próximo/Voltar e progresso persistido no servidor (`current_step`, `steps_completed`, `is_completed`), para o usuário retomar de onde parou.
- Pular é permitido em quase todos os passos (tutorial, cartões, personalização, importação, meta). Usuários avançados não podem ficar presos. Exceções: o passo 2 exige ao menos 1 conta, e se o usuário pular é criada a conta genérica "Minha Conta Principal" com saldo 0. O passo 4 exige ao menos 1 categoria marcada.
- Pular o tutorial pede confirmação, mas o usuário segue para o passo 2.
- Conta bancária: tipo `checking`/`savings`. O saldo inicial é copiado para o saldo atual na criação. Cartão: `due_day` entre 1 e 31. Cartões são opcionais.
- Categorias sugeridas, todas marcadas por padrão: Essenciais (Moradia, Alimentação, Transporte, Saúde), Variáveis (Lazer, Educação, Vestuário, Assinaturas) e Financeiro (Investimentos, Reserva de Emergência). Cada uma tem nome, ícone e cor.
- Personalização: renomear, trocar cor e ícone, excluir com confirmação e adicionar categoria nova, com auto-save imediato.
- Meta de Reserva de Emergência: nome pré-preenchido, valor-alvo sugerido de R$ 25.000 (editável), data-alvo sugerida para daqui a 12 meses e alocação mensal calculada como valor ÷ meses restantes (editável). A sugestão de R$ 25.000 é a renda mensal da persona. A jornada do usuário fala em 6 meses de despesas (R$ 120.000), então vale confirmar o valor na spec.
- Ao concluir, `is_completed=true`, redirecionamento para a Camada 1 do Dashboard GPS e uma mensagem de sucesso ("Parabéns! Sua conta está configurada").
- Conclusão do tutorial e do onboarding devem ser rastreadas para medir a taxa de conclusão. A ferramenta de analytics ainda não foi definida.
- Linguagem simples e sem jargão, labels claros em todos os campos e mensagens de erro com sugestão de correção. Todo o texto da interface fica em pt-BR.
- Fluxos de onboarding precisam de testes de integração.
- Todo dado criado aqui pertence ao usuário do JWT e é filtrado por ele.

## Technical Decisions

- Backend: pacote `onboarding/` com tabela `onboarding_progress`. Frontend: Vue 3 + Pinia + shadcn-vue/Tailwind, componentes em `components/onboarding/`. A rota `/onboarding` (requiresAuth) e um `OnboardingView.vue` placeholder já existem, e o cadastro (Epic 1) já redireciona para lá.
- Este épico cria tabelas que épicos posteriores vão reutilizar: `accounts` (Epic 4), `credit_cards` (Epic 4), `categories` (Epic 6, com hierarquia via `parent_category_id` e `is_system_default`) e `goals` (Epic 8). O modelo e a API precisam servir também para o uso fora do onboarding.
- Os números de migration do épico (V7–V11) estão desatualizados porque já existem até V14. Use a próxima versão Flyway livre (V15+). Os épicos seguintes também citam números fixos (V12+) que vão colidir.
- Convenções de tabela: PK UUID, colunas de auditoria, soft delete (`deleted_at`), FK `user_id` indexada. Dinheiro em `DECIMAL(15,2)` no banco e como number no JSON. Datas em UTC/ISO 8601.
- API REST em `/api/v1/*`, erros em RFC 7807 (`ProblemDetail`) e toasts de erro via interceptors do Axios.
- LGPD: toda tabela nova com FK para `users` precisa entrar no `purgeUser` da exclusão de conta e no export de dados. Na retro do Epic 2, colunas novas ficaram fora do export. A regra vale para cada story que cria tabela ou coluna.
- O acesso ao app depende do status da assinatura, verificado a cada requisição. No onboarding o trial já está ativo.

## UX & Interaction Patterns

- Tutoriais (passos 1 e 7) com 3–5 telas, ilustrações e diagramas simples. O passo 7 mostra as 3 camadas ("Onde estou?", "Para onde vou?", "Preciso ajustar?"), a navegação por scroll vertical e a mensagem de check-in semanal de 5 minutos. Os documentos de UX pedem onboarding mínimo, sem tour forçado, e por isso o tutorial deve ser curto e sempre pulável.
- Importação: área de drag-and-drop, progresso atualizado a cada 2 s, IA visivelmente trabalhando ("Classificando 45 de 120...") e resultado impactante ("X transações importadas", com prévia de 5–10 classificadas). Erros não culpam o usuário e dizem o motivo e a ação ("Formato não reconhecido, tente exportar em OFX").
- Interface calma e espaçada, com skeleton screens em vez de spinners isolados. Progresso é celebrado: barra da meta em 0% com data projetada e confete ou mensagem ao concluir.
- Ícones de categoria vêm do Lucide. A cor sai de uma paleta predefinida.

## Cross-Story Dependencies

- A 3.1 cria `onboarding_progress`, usado por todas as outras para avançar o passo.
- A 3.5 edita as categorias criadas na 3.4.
- A 3.6 depende da lógica de importação do Epic 5 (detecção de formato, parsing OFX/CSV, duplicatas e classificação por IA), que ainda não existe. Também precisa das contas da 3.2 e das categorias da 3.4/3.5. É preciso decidir na spec se a 3.6 espera o Epic 5 ou entrega só o passo com "Pular por agora".
- A 3.7 explica o Dashboard GPS (Epic 7), e a 3.8 redireciona para a Camada 1. Se o dashboard ainda não existir, use screenshots ou mockups e um destino provisório.
- O Epic 1 já leva ao `/onboarding` após o cadastro. Os Epics 4, 6 e 8 estendem as tabelas criadas aqui.
