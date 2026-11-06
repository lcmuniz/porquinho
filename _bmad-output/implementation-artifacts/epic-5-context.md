# Epic 5 Context: Transaction Import & AI Classification

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Permitir que o usuário importe extratos OFX/CSV dos 5 principais bancos brasileiros (Nubank, BB, Itaú, Bradesco, Caixa) para uma conta ou um cartão já cadastrados, com detecção de banco, parsing tolerante a falhas, descarte de duplicatas e de transferências já registradas, identificação de Pix e parcelamentos, classificação automática por IA e revisão rápida antes de confirmar. Também cobre a edição e a inclusão manual de transações. É o núcleo do ritual semanal (importar, revisar, ver o dashboard): a IA precisa classificar a maior parte sozinha (meta de 85%+ de precisão após um mês) para o usuário revisar só as exceções.

## Stories

- Story 5.1: Upload de arquivos OFX
- Story 5.2: Upload de arquivos CSV
- Story 5.3: Detecção automática de formato e banco (só OFX; feita antes da 5.2, que trouxe o CSV)
- Story 5.4: Parsing de transações com tratamento de erros
- Story 5.5: Detecção de duplicatas
- Story 5.6: Identificação de Pix e extração de detalhes
- Story 5.7: Detecção de compras parceladas
- Story 5.8: Classificação automática por IA
- Story 5.9: Tela de revisão das transações classificadas
- Story 5.10: Ajuste das classificações e conclusão da importação
- Story 5.11: Aprendizado com as correções do usuário
- Story 5.12: Edição manual de transações
- Story 5.13: Inclusão manual de transações (gastos em dinheiro)

## Requirements & Constraints

- Upload de até 10MB (validado no frontend e no backend) em < 5s; parsing de 1 ano de transações em < 30s, incluindo duplicatas, Pix e parcelas; lote de 100 transações classificado pela IA em < 3s; progresso atualizado a cada 2s.
- Detecção de formato/banco com 95%+ de precisão (100% nas amostras OFX). Arquivos malformados geram mensagens claras em português, que orientam o usuário (por exemplo, sugerir exportar em OFX quando o CSV não é reconhecido).
- LGPD: o conteúdo do arquivo fica só em memória e nunca vai para disco, banco ou log. Logs e auditoria carregam só ids, contagens e motivos, nunca nome de arquivo nem dados pessoais. Toda tabela nova entra no export e no purge de dados na mesma story que a cria (`transactions` é apagada antes de `accounts`/`credit_cards`). A política de privacidade cita o provedor de IA como operador das descrições.
- Transações importadas ficam `PENDING` e não aparecem em listas, totais ou saldos até a importação ser concluída. Concluir a mesma importação duas vezes, ou em paralelo, não muda nada na segunda vez.
- A IA nunca toma ação financeira sozinha: abaixo de 80% de confiança a transação vai para revisão. Com a IA fora do ar, as transações ficam "Não classificado" e a importação chega à revisão do mesmo jeito.
- Isolamento por usuário: recurso de outro usuário ou removido responde 404 sem gravar nada. Os padrões de aprendizado valem só para o usuário que os criou.
- Fora do épico: atualizar o orçamento da categoria (Epic 6), cache e camadas do Dashboard GPS e projeções de parcelas (Epic 7), métrica de precisão da IA (Epic 10).

## Technical Decisions

- **D1:** toda transação pertence a exatamente uma conta (`account_id`) ou um cartão (`credit_card_id`), garantido por CHECK. O mesmo vale para `transaction_imports`.
- **D2:** o destino é escolhido antes do upload. O OFX só confirma ou sugere, e um OFX de cartão (`CCSTMTRS`) enviado para uma conta, ou o contrário, é rejeitado.
- **D3 (saldo):** só transações de conta com data posterior à data de referência do saldo (último snapshot `initial`, `edit` ou `reconciliation`) alteram `current_balance`, registrado em `affects_balance`. Transações de cartão nunca mexem em saldo de conta. Toda mudança de saldo passa pelo `BalanceHistoryRecorder` na mesma transação (snapshot do tipo `transaction`), com lock `PESSIMISTIC_WRITE` nas contas em ordem de id, e os limites de valor saem de `MAX_BALANCE`/`fits()`.
- **D4:** um lançamento que corresponde a uma transferência já registrada (mesma conta, data ±1 dia, mesmo valor, sentido certo) não é importado e é contado à parte.
- **D5 (IA):** Claude Haiku 5.5 (`claude-haiku-5-5`) pela API da Anthropic, chamado do backend (a chave nunca vai ao frontend). CPF/CNPJ são mascarados antes do envio. Os padrões do usuário são aplicados antes e não chamam a IA. `CARD_PAYMENT` não vai para a IA. Uma categoria devolvida que não está na lista do usuário é descartada.
- **D6:** não há arquivos reais. Os parsers são escritos e testados com as amostras fictícias de `porquinho-backend/src/test/resources/import-samples/` (ver o README). O OFX é o formato principal.
- **D7:** uma parcela importada que corresponde a uma parcela futura já criada confirma essa parcela em vez de inserir outra. Todas as parcelas de uma compra compartilham `installment_group_id`.
- **D8:** o pagamento de fatura é `kind = CARD_PAYMENT`. Altera o saldo da conta pela D3, mas não conta como gasto nem como receita.
- **D9:** a reconciliação continua sem transação de ajuste.
- **Story 5.4 – 1A (Luiz, 2026-11-04):** gravadas as transações, a importação vai para `REVIEW` (só a partir de `PROCESSING`) e a consulta de 2 s para. `PROCESSING` significa processamento ativo: a varredura de órfãs (subida e 15 min) falha toda `PROCESSING` antiga, com ou sem `bank_name`. As 5.5–5.8 entram antes do `REVIEW`.
- **Story 5.4 – 2A (Luiz, 2026-11-04):** sem progresso real na 5.4; enquanto `PROCESSING` com banco, a lista mostra "Banco detectado: <banco> — lendo as transações..." e segue consultando. A coluna de progresso ("X de Y", NFR8) entra na 5.8.
- **Story 5.4 – 3A (Luiz, 2026-11-04):** `kind = CARD_PAYMENT` marcado na leitura por palavra-chave na descrição (sem acento, maiúsculas): "PAGAMENTO DA FATURA", "PAGAMENTO EFETUADO", "PAGAMENTO RECEBIDO", "ITAU UNIBANCO CARTAO", "GASTOS CARTAO DE CREDITO". Na conta só vale para `DEBIT`; no cartão só para `CREDIT`.
- **Story 5.2 – 1A (Luiz, 2026-11-05):** sem escolha manual do banco. No CSV o layout reconhecido define o banco; no OFX o banco não muda a leitura (`OTHER` continua "Banco não identificado"). O AC "Detectamos [Banco]. Está correto?" saiu do épico.
- **Story 5.2 – 2A (Luiz, 2026-11-05):** layout e destino do CSV são verificados no envio, como o OFX: layout desconhecido → 400 "Não reconhecemos o formato deste CSV. Tente exportar o extrato em OFX."; layout de cartão em conta ou de conta em cartão → 400 com as mensagens de destino errado da 5.1. Nada é gravado. Banco gravado e leitura das linhas continuam em segundo plano. A palavra-chave "PAGAMENTO CARTAO DE CREDITO" entrou na lista da 3A (vale para OFX e CSV).
- **Story 5.5 – 1A (Luiz, 2026-11-05):** se todos os lançamentos forem duplicatas ou transferências (0 gravados), a importação vai direto para `COMPLETED` (mesma guarda de `PROCESSING`), com o rótulo "Nenhuma transação nova — X duplicatas detectadas e ignoradas" (e "· N transferências já registradas" quando N > 0).
- **Story 5.5 – 2A (Luiz, 2026-11-05):** o rótulo de `REVIEW` é uma linha só, com sufixos que aparecem quando a contagem passa de 0 ("· 3 duplicatas detectadas e ignoradas · 1 transferência já registrada"), singular/plural pelo `plural()` existente.
- **Story 5.5 – 4A (Luiz, 2026-11-05):** duplicata = mesmo `external_id` no mesmo destino; senão, semelhança (data ±1 dia, mesmo valor e tipo, descrição com Levenshtein ≥ 90% ou prefixo de 10+ caracteres, por causa do MEMO cortado do BB) só quando falta id de um dos lados ou quando os ids diferem e os formatos diferem (OFX × CSV). No mesmo formato, id diferente é transação diferente. Pareamento um-para-um; lançamentos do mesmo arquivo não se comparam. Transferências da D4 ficam em `transfers_found`. A verificação roda sob advisory lock por destino, na mesma transação do update com guarda e do `saveAll`; a auditoria leva só `import_id` e contagens.
- **Story 5.7 – 1A (Luiz, 2026-11-05):** o texto "Compra parcelada: 3/10 — restam 7 parcelas" foi para a 5.9 e a 5.12, junto com a exposição de `isInstallment`/`installmentNumber`/`installmentTotal`/`isFutureInstallment` na API. A 5.7 entrega só os dados (`InstallmentDetector` + `InstallmentPlanner`).
- **Story 5.7 – 2A (Luiz, 2026-11-05):** a parcela futura confirmada por uma importação nova conta como duplicata dela e continua ligada à importação que a criou. As parcelas futuras não são candidatas da 5.5. Gasto do mês (Epics 6/7) ignora `is_future_installment = true`.
- **Story 5.7 – 3A (Luiz, 2026-11-05, revista no loop 3):** a parcela entra no grupo com valor diferente até o maior entre R$ 0,05 e R$ 0,01 × (total − 1), inclusive (centavos de arredondamento); a confirmação grava o valor do arquivo.
- **Story 5.7 – 4A (Luiz, 2026-11-05):** a parcela só entra num grupo se a data estiver a até 20 dias da esperada para o número. Compra repetida (anuidade do ano seguinte) abre outro grupo.
- **Story 5.7 – 5A (Luiz, 2026-11-05):** a 5.7 não detecta estorno, antecipação nem cartão apagado. As Epics 6 e 7 ignoram futuras de cartão apagado, que voltam a contar se o cartão for restaurado. A 5.12 ganha a ação "remover parcelas futuras desta compra", para o usuário tratar estorno e antecipação.
- **Story 5.8 – 1A (Luiz, 2026-11-06):** as parcelas futuras criadas pela importação herdam categoria, origem e confiança da parcela do arquivo no mesmo grupo e na mesma importação. Não vão para a IA, não contam em Y e não geram histórico. A futura confirmada por outra importação continua com a importação que a criou (5.7 – 2A) e não volta à classificação.
- **Story 5.8 – 2A (Luiz, 2026-11-06):** cada lote de 100 sai em 4 chamadas simultâneas de até 25 (NFR7). Numa chamada só, 100 levaram de 5,5 a 6,7 s; em paralelo, cerca de 2 s depois da primeira rodada.
- **Parcelas futuras na 5.9 (pendente):** as futuras da 5.7 têm o `import_id` da importação e `review_status = PENDING`. A 5.9 precisa decidir se aparecem na lista de revisão e no "Aprovar todas". A 5.10 precisa tratar no descarte a parcela confirmada por outra importação (ver deferred-work).
- Pipeline em segundo plano, fora da thread da requisição: detecção, parsing, duplicatas, Pix, parcelas, classificação e, por fim, status `REVIEW`. Status da importação: `PROCESSING`/`REVIEW`/`COMPLETED`/`FAILED`/`DISCARDED` (`COMPLETED` também quando nada novo foi gravado, 5.5 – 1A). Importações presas em `PROCESSING` (sem mudança em `updated_at`) são tratadas na subida do backend e por uma varredura periódica: com linhas gravadas vão para `REVIEW`, sem linhas para `FAILED` (5.8).
- Valores sempre positivos (`DECIMAL(15,2)`), com o sentido em `transaction_type` (DEBIT/CREDIT). No cartão, compras são DEBIT e pagamentos/estornos são CREDIT.
- Tabelas novas do épico: `transaction_imports`, `transactions`, `ai_classification_history`, `user_classification_patterns`. Convenções: PK UUID `id`, `user_id` indexado, `created_at`/`updated_at`/`deleted_at`, soft delete, migrations na próxima versão livre do Flyway (V25 em diante).
- API REST em `/api/v1/imports` e `/api/v1/transactions` (recursos no plural, JSON em camelCase), erros em RFC 7807 com mensagens em português.

## UX & Interaction Patterns

- `/importar` (link a partir do dashboard): escolha do destino, zona de arrastar e soltar ou botão "Enviar arquivo", barra de progresso, lista das importações recentes com o status de cada uma. O polling para quando o usuário sai da página.
- Mostrar a IA trabalhando ("Classificando transações com IA... X de Y") e um resumo final com as contagens de importadas, duplicadas e linhas com erro.
- Revisão em `/importar/:id/revisao`: agrupada por confiança (Alta 80%+, Média 50–79%, Baixa <50%), com ícone e texto além da cor (WCAG). Marcadores de Pix e de parcela, "Pagamento de fatura" à parte, "Aprovar todas de alta confiança", edição em lote, retomar ou descartar a importação. O foco fica nas exceções, com linguagem encorajadora ("Vamos revisar").
- `/transacoes` para editar e adicionar transações manualmente. O passo 6 do onboarding (`ImportStep.vue`) reusa o mesmo componente e o mesmo endpoint de upload.

## Cross-Story Dependencies

- Ordem do pipeline: 5.1 → 5.3 → 5.4 → 5.5 → 5.6 → 5.7 → 5.8 → 5.9 → 5.10. A 5.2 (CSV) entra depois da 5.3/5.4: layout e destino verificados no envio (2A), banco e leitura das linhas no mesmo processamento em segundo plano; sem escolha manual do banco (1A).
- A 5.11 passa a aplicar os padrões do usuário antes da chamada à IA da 5.8 (`user_classification_patterns` não existia na 5.8). A 5.10 e a 5.12 alimentam a 5.11.
- A 5.10 conclui o onboarding: registra `first_import_completed` via `POST /api/v1/onboarding/me/import` (adiado da Story 3.6).
- Depende do que já existe: categorias (Story 3.4), contas, cartões, transferências e histórico de saldo (Epic 4), export e purge LGPD (Stories 1.6/1.7) e auditoria.

## Regras do projeto que valem para todas as stories

- Migration aplicada nunca é editada; mudança de schema sempre em nova versão do Flyway (V28 é a última em 2026-11-06).
- Backend em camadas `com.porquinho.controller/service/repository/entity/dto`; erros em `ProblemDetail` (RFC 7807) com mensagem em pt-BR.
- Rotas novas ficam atrás do `SubscriptionAccessFilter` (402 sem assinatura).
- Toda tabela ou coluna nova com dado do usuário entra no export (`DataExportService`) e no purge (`AccountDeletionService`) na ordem das FKs, e o teste confere o conjunto de chaves do export (lição F3 da retro do Epic 2).
- Amostras de teste são fictícias e geradas por `porquinho-backend/scripts/gerar-amostras-extrato.py`; nunca commitar extrato real.
- IA (5.8): consultar a skill `claude-api` (SDK Java, structured outputs) antes de escrever o cliente.
- Datas do OFX são sempre AAAAMMDD; formatos alternativos de data só valem para o CSV (5.2).
- A varredura de órfãs usa `updated_at` (15 min), atualizado a cada lote da classificação (5.8). Quem acrescentar etapa longa ao pipeline precisa manter `updated_at` em dia.
