# Epic 5 Context: Transaction Import & AI Classification

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Permitir que o usuário importe extratos OFX/CSV dos 5 principais bancos brasileiros (Nubank, BB, Itaú, Bradesco, Caixa) para uma conta ou cartão já cadastrado, com detecção de formato e banco, parsing tolerante a erros, descarte de duplicatas e de transferências já registradas, identificação de Pix e de compras parceladas, classificação automática por IA, revisão rápida (o usuário só olha as exceções), aprendizado com as correções e edição/inclusão manual de transações. É a base de dados do produto: orçamento (Epic 6) e Dashboard GPS (Epic 7) dependem das transações criadas aqui, e a promessa central é reduzir a categorização de 20 minutos para cerca de 2 minutos de revisão.

## Stories

- Story 5.1: Upload de arquivos OFX dos 5 bancos
- Story 5.2: Upload de arquivos CSV dos 5 bancos
- Story 5.3: Detecção automática de formato e banco
- Story 5.4: Parsing de transações com tratamento robusto de erros
- Story 5.5: Detectar e descartar transações duplicadas
- Story 5.6: Identificar Pix e extrair detalhes
- Story 5.7: Detectar compras parceladas e criar parcelas futuras
- Story 5.8: Classificação automática com IA
- Story 5.9: Apresentar as transações classificadas para revisão
- Story 5.10: Revisar e ajustar classificações e concluir a importação
- Story 5.11: Aprender com as correções do usuário
- Story 5.12: Editar manualmente qualquer transação
- Story 5.13: Adicionar transações manualmente (despesas em dinheiro)

## Requirements & Constraints

- **Desempenho:** upload de até 10MB em < 5s (limite validado no frontend e no backend); parsing de 1 ano de transações em < 30s, incluindo duplicatas, Pix e parcelas (padrões pré-compilados, sem query por linha); IA classifica lote de 100 em < 3s (pendente de validação real até haver `ANTHROPIC_API_KEY`); feedback de progresso a cada 2s ("X de Y").
- **Detecção:** formato/banco com 95%+ de precisão; arquivos malformados ou layout desconhecido geram mensagem clara em português, sugerindo exportar em OFX quando o CSV não é reconhecido.
- **LGPD:** o conteúdo do arquivo nunca é gravado em disco, banco ou log (só em memória durante o processamento); logs levam apenas id da importação e motivo, sem dados pessoais; auditoria registra só contagens e metadados (nunca nome de arquivo). Toda tabela nova entra no export e no purge na mesma story que a cria (`transactions` apagada antes de `accounts`/`credit_cards`). CPF/CNPJ são mascarados antes do envio à IA, e a política de privacidade nomeia o provedor de IA como operador.
- **Precisão da IA:** meta de 85%+ após 1 mês de uso e 90%+ após 3 meses; o aprendizado por usuário é o mecanismo para chegar lá.
- **Isolamento:** toda query filtra por `user_id` do JWT; recurso de outro usuário ou removido retorna 404, sem escrever nada.
- **Acessibilidade:** confiança e Pix nunca indicados só por cor ou só por ícone; sempre com texto alternativo.
- **Testes:** cobertura unitária > 70% na lógica crítica (classificação, cálculos financeiros) e testes de integração do fluxo importar → classificar → revisar.
- **Fora do escopo:** atualização do orçamento da categoria (Epic 6), cache/camadas do Dashboard GPS e projeções de parcelas (Epic 7), métrica de precisão da IA (Epic 10).

## Technical Decisions

- **D1:** cada transação pertence a exatamente uma conta (`account_id`) ou um cartão (`credit_card_id`), com CHECK no banco. O mesmo vale para `transaction_imports`.
- **D2:** o usuário escolhe o destino antes do upload; o tipo de conta do OFX (ou o layout do CSV) só confirma. Destino incompatível (cartão × conta) é rejeitado no upload.
- **D3 (saldo):** só transações de conta com data posterior à data de referência alteram `current_balance`. A referência é a mais recente entre o último snapshot `initial`/`edit`/`reconciliation` e `last_reconciliation_date`, como data de São Paulo; no mesmo dia, não altera. Cartão nunca altera saldo de conta.
- **Regra de saldo única:** toda mudança em `current_balance` passa pelo `BalanceHistoryRecorder` na mesma transação (nunca salvar `AccountBalanceHistory` direto); limites via `MAX_BALANCE`/`fits()`; linhas de conta travadas com `PESSIMISTIC_WRITE`, em ordem de id.
- **D4:** lançamento que casa com transferência já registrada (mesma conta, ±1 dia, mesmo valor, sentido certo) não é importado e é contado à parte.
- **D5:** IA = Claude Haiku 5.5 (`claude-haiku-5-5`) via API da Anthropic, chave só no backend; lotes de até 100, categoria fora da lista do usuário é descartada; confiança >= 80 aplica a categoria, abaixo disso fica para revisão. IA indisponível não bloqueia: a transação fica "Não classificado" (confiança 0) e a importação segue para revisão.
- **D6:** parsers testados com amostras fictícias em `porquinho-backend/src/test/resources/import-samples/`; OFX é o formato principal, CSV validado quando houver arquivo real.
- **D7:** parcela importada que corresponde a uma parcela futura já criada a confirma, em vez de inserir outra.
- **D8:** pagamento de fatura é `kind = CARD_PAYMENT` (por palavra-chave na descrição); altera saldo pela D3, mas não conta como gasto/receita e não vai para a IA.
- **D9:** reconciliação continua sem transação de ajuste.
- **Ciclo de vida da importação:** `PROCESSING` → `REVIEW` → `COMPLETED` (ou `FAILED`/`DISCARDED`). Processamento em background, fora da thread da requisição; importações órfãs em `PROCESSING` viram `FAILED` no startup e a cada 5 min (mais velhas que 15 min). Transações ficam `PENDING` (ou `APPROVED` na revisão) e invisíveis em listas, totais e saldos até a conclusão, que grava `CONFIRMED`, `COMPLETED` e o efeito de saldo numa única transação, de forma idempotente.
- **Origem da classificação:** `AI`, `PATTERN`, `USER` ou `NONE`, com confiança 0–100. `PATTERN` e `USER` têm confiança 100. Parcelas futuras herdam categoria, origem e confiança da parcela do arquivo, não vão para a IA e não geram histórico.
- **Aprendizado:** padrões por usuário (descrição normalizada, sem datas, números, documentos e marcadores de parcela) aplicados antes da IA; padrão feito só de termos bancários genéricos não aprende nem casa; padrão cuja categoria foi apagada é ignorado; descartar a importação não desfaz o padrão.
- **Convenções:** PK `id` UUID, `user_id` indexado, `deleted_at` (só soft delete), dinheiro `DECIMAL(15,2)`, `amount > 0` com o sentido em `transaction_type` (DEBIT/CREDIT); migrations na próxima versão livre; erros em RFC 7807; JSON camelCase; camadas Controller → Service → Repository.
- **Endpoints-base:** `POST /api/v1/imports` (multipart: `file` + exatamente um de `accountId`/`creditCardId`), `GET /api/v1/imports` (20 mais recentes), `GET /api/v1/imports/{id}`.

## UX & Interaction Patterns

- Fluxo linear do ritual semanal: importar → revisar → dashboard. Upload por drag-and-drop ou botão em `/importar` (link no dashboard), com o destino escolhido antes.
- Mostrar a IA trabalhando ("Classificando transações com IA... X de Y"); a lista de `/importar` faz polling a cada 2s só enquanto há importação em processamento e para ao sair da página.
- Revisão em `/importar/:id/revisao`: agrupada por confiança (Alta 80%+ com check verde, Média 50–79% com alerta amarelo, Baixa <50% com bandeira vermelha), depois "Revisadas por você"; pagamentos de fatura à parte. "Aprovar todas de alta confiança", contagem "X de alta confiança, Y precisam de revisão", edição individual e em lote, "Continuar depois" e "Descartar importação". Parcelas futuras não aparecem na revisão; "Compra parcelada: 3/10 — restam 7 parcelas" as representa.
- Linguagem encorajadora ("Vamos revisar") e destaque só das exceções; o usuário pode corrigir facilmente, sem precisar revisar tudo.
- Mensagens de erro claras e acionáveis em português (arquivo grande, formato não suportado, arquivo incompleto, destino errado).
- `/transacoes` lista as transações confirmadas com ícone de Pix e marcador de parcela, edição, remoção, "Remover parcelas futuras desta compra" e "Adicionar transação".

## Cross-Story Dependencies

- Ordem de implementação: 5.1 → 5.3 (só OFX) → 5.2 (CSV, incluindo detecção de layout) → 5.4 → 5.5–5.7 (em sequência, antes de `REVIEW`) → 5.8 → 5.9 → 5.10 → 5.11 → 5.12/5.13.
- Funcionalidades que mudaram de story: ícone de Pix e texto de parcelamento foram para 5.9 e 5.12; progresso "X de Y" foi para 5.8; botão "Editar" da revisão foi para 5.10; a aplicação de padrões antes da IA chega com 5.11.
- Correções em 5.10 e 5.12 alimentam o aprendizado de 5.11; 5.12 e 5.13 reaplicam a D3 (diferença ou reversão de saldo via `BalanceHistoryRecorder`).
- Depende do Epic 4 (contas, cartões, transferências da Story 4.5, reconciliação da 4.6, histórico de saldo), do Epic 3 (categorias da 3.4; o passo 6 do onboarding, `ImportStep.vue`, usa o mesmo upload e registra `first_import_completed` em `POST /api/v1/onboarding/me/import` ao concluir a primeira importação, na 5.10) e do Epic 1 (export/purge LGPD, auditoria).
- Os Epics 6 e 7 consomem as transações confirmadas e ignoram as parcelas futuras de cartão removido.
