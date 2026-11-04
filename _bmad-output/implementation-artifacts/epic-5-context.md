# Epic 5 Context: Transaction Import & AI Classification

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Trazer as transações do usuário para dentro do porquinho. O usuário envia o extrato OFX ou CSV do banco (Nubank, BB, Itaú, Bradesco, Caixa) para uma conta ou um cartão. O sistema detecta o banco, lê os lançamentos, descarta duplicatas e transferências já registradas, marca Pix e parcelas, e classifica cada transação numa categoria do usuário, primeiro pelos padrões aprendidos e depois pela IA. O usuário revisa, corrige e conclui. Também dá para editar e lançar transações à mão. Este é o momento "aha" do produto: a IA acertando a maior parte sozinha. A meta é 85%+ de precisão após 1 mês de uso. Os Epics 6 (orçamento) e 7 (dashboard) dependem da tabela `transactions` criada aqui.

## Stories

- Story 5.1: Upload de OFX (registra a importação em `PROCESSING`; nada roda em segundo plano ainda, decisão 1A)
- Story 5.2: Upload de CSV
- Story 5.3: Detecção de formato e banco (e o processamento em segundo plano com armazenamento temporário do arquivo, decisão 1A da 5.1)
- Story 5.4: Parsing com tratamento de erros (cria `transactions`)
- Story 5.5: Duplicatas e transferências já registradas
- Story 5.6: Pix
- Story 5.7: Parcelas e parcelas futuras
- Story 5.8: Classificação automática por IA
- Story 5.9: Tela de revisão
- Story 5.10: Ajustes e conclusão da importação (e o passo 6 do onboarding ligado ao upload, decisão 2A da 5.1)
- Story 5.11: Aprendizado com as correções
- Story 5.12: Editar transação (`/transacoes`)
- Story 5.13: Adicionar transação manual

## Requirements & Constraints

- Texto do épico revisado em 2026-11-04 com as decisões D1–D9 (`_bmad-output/planning-artifacts/epics/epic-5-transaction-import-ai-classification.md`). Leia o bloco de decisões no topo antes de qualquer story.
- **D1:** cada transação pertence a uma conta **ou** a um cartão (CHECK no banco). **D2:** o destino é escolhido antes do upload; um OFX de cartão em conta (ou o contrário) é rejeitado.
- **D3, saldo:** só transações de conta com data posterior à data de referência do saldo alteram `current_balance`. A data de referência é a do último snapshot `initial`, `edit` ou `reconciliation`. Isso fica em `affects_balance` e só tem efeito ao concluir a importação (5.10) ou ao lançar e editar à mão (5.12/5.13). Cartão nunca mexe em saldo de conta.
- **D4:** um lançamento que corresponde a uma transferência da Story 4.5 não é importado. **D8:** pagamento de fatura é `kind=CARD_PAYMENT`: altera o saldo, mas não é gasto nem receita, e não vai para a IA.
- **D7:** a parcela que chega num extrato posterior confirma a parcela futura já criada, em vez de duplicá-la.
- **D9:** reconciliação continua sem transação de ajuste.
- **Desempenho:** upload de 10MB em < 5 s (NFR5), parsing de 1 ano em < 30 s (NFR6), lote de 100 classificado em < 3 s (NFR7), progresso a cada 2 s (NFR8). Detecção de formato 95%+ (NFR56). Erros claros com sugestão (NFR57).
- O processamento roda em segundo plano e a tela consulta o status da importação. As transações ficam `PENDING` (fora de listas, totais e saldo) até "Concluir importação". Uma importação não concluída pode ser retomada ou descartada.
- **Arquivo:** nunca é guardado depois de processado. Logs sem dados pessoais (sem descrição, nome ou documento).
- Valores sempre positivos, com `transaction_type` DEBIT/CREDIT. No cartão, compra = DEBIT e pagamento/estorno = CREDIT.

## Technical Decisions

- **Migrations:** próxima versão livre (V25 em 2026-11-04). Os números V15–V18 do texto original já existem com outro conteúdo. Migration aplicada nunca é editada (o devtools aplica no Supabase de dev no primeiro teste).
- **Convenções:** PK `id` UUID, `user_id` indexado, `deleted_at`, `DECIMAL(15,2)`, `ProblemDetail` (RFC 7807), API em `/api/v1/*`. O backend é organizado por camadas (`com.porquinho.controller/service/repository/entity/dto`), não por feature.
- **Saldo:** qualquer mudança de `accounts.current_balance` passa pelo `BalanceHistoryRecorder` na mesma transação, com evento `transaction` (já aceito pelo CHECK da V20), conta bloqueada (`PESSIMISTIC_WRITE`) e limites `MAX_BALANCE`/`fits()`. É a regra 8 de `architecture/implementation-patterns-consistency-rules.md`.
- **IA:** Claude Haiku 5.5 (`claude-haiku-5-5`) pela API da Anthropic, só no backend (chave em variável de ambiente, nunca no frontend). CPF e CNPJ são mascarados antes do envio. A resposta é validada contra as categorias do usuário. Timeout ou erro deixa a transação "Não classificado". A política de privacidade passa a citar o provedor de IA. Para a integração, consultar a skill `claude-api` (SDK Java, structured outputs) antes de escrever o cliente.
- **LGPD:** cada story que cria tabela ou coluna com dado do usuário (`transaction_imports`, `transactions`, `ai_classification_history`, `user_classification_patterns`) inclui essa tabela no export (`DataExportService`) e no purge (`AccountDeletionService`), na ordem certa das FKs, e testa o conjunto de chaves do export (lição F3 da retro do Epic 2).
- **Amostras de teste:** `porquinho-backend/src/test/resources/import-samples/`, geradas por `porquinho-backend/scripts/gerar-amostras-extrato.py`. São fictícias. O OFX tem confiança alta; os layouts CSV vão de baixa a média (ver o README). O OFX é o formato principal.
- **Acesso:** rotas novas ficam atrás do `SubscriptionAccessFilter` (402 sem assinatura). O passo 6 do onboarding usa o mesmo endpoint de upload.
- **Fora do épico:** orçamento da categoria (Epic 6), cache e Camadas do dashboard (Epic 7), métrica de precisão da IA (Epic 10).

## UX & Interaction Patterns

- Upload por arrastar e soltar, com escolha da conta ou do cartão antes do envio. Progresso visível ("X de Y transações processadas", "Classificando transações com IA... X de Y").
- Revisão agrupada por confiança (alta, média, baixa), com ícone **e** texto, nunca só cor (WCAG 2.1 AA). Marcadores de Pix e de parcela. "Pagamento de fatura" aparece separado.
- "Aprovar todas de alta confiança", edição individual e em lote, "Concluir importação" e "Descartar importação".
- Erros não culpam o usuário e dizem a ação: "Não reconhecemos o formato deste CSV. Tente exportar o extrato em OFX."
- Interface em pt-BR, valores em BRL, datas DD/MM/AAAA. Skeleton screens em vez de spinners isolados.

## Cross-Story Dependencies

- A 5.1 cria `transaction_imports`, `POST/GET /api/v1/imports` e a página `/importar` (`ImportView.vue`), que a 5.2 estende para CSV. O `ImportStep.vue` do onboarding (item adiado da 3.6) só é ligado ao upload na 5.10 (decisão 2A).
- A 5.3 depende de 5.1/5.2. A 5.4 cria `transactions`, que as stories seguintes usam. Na ordem do processamento, 5.5 → 5.6 → 5.7 → 5.8 rodam em sequência dentro da mesma importação.
- A 5.8 usa os padrões da 5.11 se existirem. Antes da 5.11, ela só chama a IA.
- A 5.10 aplica o efeito no saldo (D3) e depende do `BalanceHistoryRecorder` do Epic 4. A 5.11 é alimentada pela 5.10 e pela 5.12.
- A 5.12/5.13 criam a página `/transacoes`, que o Epic 7 (Camada 3) vai reaproveitar ou substituir.
- O Epic 6 vai ligar `transactions.category_id` ao orçamento. O Epic 4 já tem `accounts`, `credit_cards`, `transfers` e `account_balance_history`.
