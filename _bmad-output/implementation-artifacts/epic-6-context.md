# Epic 6 Context: Budget & Category System (Envelope Method)

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

Implementar o orçamento por envelope, que é o centro do produto. O usuário cria e organiza em hierarquia as suas categorias de gasto, define no começo do mês quanto aloca em cada uma e vê em tempo real quanto ainda pode gastar em cada envelope. Ele também pode realocar dinheiro entre categorias, ver o gasto deduzido sozinho quando uma transação é classificada, receber sinais de saldo baixo ou estourado, consultar o histórico de alocações e comparar o planejado com o real. O épico responde à pergunta "posso gastar R$ X na categoria Y?" e entrega os dados por categoria que o Dashboard GPS (Epic 7) e os relatórios (Epic 9) vão usar.

## Stories

- Story 6.1: Criar categorias de gasto personalizadas
- Story 6.2: Organizar categorias em grupos hierárquicos
- Story 6.3: Alocar orçamento mensal por categoria
- Story 6.4: Mostrar em tempo real o saldo disponível por categoria
- Story 6.5: Realocar dinheiro entre categorias
- Story 6.6: Deduzir da categoria automaticamente ao classificar uma transação
- Story 6.7: Indicadores visuais de categorias com saldo baixo ou negativo
- Story 6.8: Ver o histórico de alocações da categoria
- Story 6.9: Comparar o planejado com o real por categoria

## Requirements & Constraints

- **Categorias:** sem limite de quantidade por usuário. O nome é obrigatório e único por usuário. Cor e ícone (Lucide) são opcionais: se o usuário não escolher, a cor vem da paleta e o ícone é `folder`. Uma categoria nova aparece logo na lista e nos seletores de classificação de transações.
- **Hierarquia:** uma categoria não pode ser pai de si mesma e não pode haver ciclo. O épico fala em até 3 níveis, mas a Story 3.5 fixou profundidade 1: o pai tem de ser um grupo ativo do mesmo usuário, e um grupo com filhas não pode ser excluído. A 6.2 precisa decidir isso de forma explícita. Transações e alocações aceitam categoria pai ou filha. O total do pai é a soma das filhas mais a alocação do próprio pai.
- **Gasto (Spent):** soma apenas débitos de transações confirmadas do mês. Créditos não reduzem o orçamento. Ficam fora as transações `PENDING`/`APPROVED` de importação ainda não concluída, os pagamentos de fatura (`CARD_PAYMENT`) e as parcelas futuras (`is_future_installment=true`). Uma parcela futura só passa a contar no próprio mês quando uma importação a confirma.
- **Comprometido:** a partir do mês corrente, cada mês mostra à parte a soma das parcelas futuras com data naquele mês. Meses fechados não mostram comprometido (o corte é o dia 1 do mês corrente). Parcelas futuras de cartão removido ficam de fora e voltam a contar se o cartão for restaurado.
- **Alocação:** o total alocado deve ser menor ou igual ao total disponível nas contas. Se passar, aparece o aviso "Você está alocando mais do que tem disponível". A alocação pode ser editada durante o mês, e existe a opção "Repetir orçamento do mês anterior". Uma categoria sem alocação no mês não ganha registro de orçamento: o gasto é acompanhado, mas sem limite, e a tela mostra "Sem orçamento definido".
- **Realocação:** origem diferente do destino. O valor não pode passar do saldo restante da origem, e não se move dinheiro de uma categoria com saldo negativo. Cada realocação fica registrada para auditoria e histórico.
- **Status (mesma regra em todo o épico):** verde quando o restante é maior que 20% do alocado, amarelo entre 0 e 20%, vermelho quando o restante é negativo.
- **Desempenho:** saldos calculados em tempo real ao abrir a tela. O histórico carrega em menos de 1 s com 12 ou mais meses de dados.
- **Isolamento:** toda query filtra pelo `user_id` do JWT. Recurso de outro usuário ou removido retorna 404.
- **LGPD:** toda tabela nova (alocações, realocações) entra no export e no purge na mesma story que a cria.
- **Linguagem:** português claro e sem jargão. Mensagens previstas: "Categoria criada", "Orçamento mensal definido", "R$ X,XX movido de [A] para [B]", "Saldo baixo - R$ X,XX restantes" e "Categoria estourada! R$ X,XX acima do orçamento".

## Technical Decisions

- **Dados:** `budget_allocations` (usuário, categoria, `month_year` sempre no dia 1 do mês, valor alocado e, conforme o épico, gasto e restante) e `budget_reallocations` (origem, destino, valor, mês). Os nomes V19/V20 citados no épico já estão em uso: as migrations devem usar a próxima versão livre.
- **Gasto armazenado ou calculado:** a 6.4 calcula o gasto a partir das transações a cada leitura, e a 6.6 descreve um `spent_amount` incremental (somar e subtrair ao classificar, trocar de categoria ou apagar). Escolher uma fonte de verdade antes da 6.3/6.6. Se o valor for armazenado, todas as atualizações acontecem de forma atômica, na mesma transação do banco que a mudança da transação.
- **Cache Redis:** saldo por categoria com TTL de 30 s e chave `category:balance:{userId}:{categoryId}:{month}`. É invalidado quando uma transação é criada, editada, reclassificada ou removida, quando uma importação é concluída e quando uma alocação ou realocação é salva (na realocação, as duas categorias). Essas mesmas mudanças também invalidam o cache do Dashboard (`dashboard:layer*:{userId}`).
- **Convenções:** PK `id` UUID, `user_id` indexado, `deleted_at` (apenas soft delete), dinheiro em `DECIMAL(15,2)`, erros em RFC 7807, JSON em camelCase, camadas Controller → Service → Repository, recursos REST no plural (`/api/v1/categories`, ...). Os endpoints de categoria (`POST`/`PATCH`/`DELETE /api/v1/categories`) já existem desde a Story 3.5 e devem ser estendidos, não recriados.

## UX & Interaction Patterns

- Semáforo verde/amarelo/vermelho com Green 600 `#16A34A`, Yellow 600 `#CA8A04` e Red 600 `#DC2626`. O status nunca é indicado só pela cor: sempre vem acompanhado de ícone e texto.
- Tom calmo, sem alertas agressivos. A barra de progresso mostra gasto ÷ alocado em %, e o tooltip mostra os valores exatos.
- Categorias estouradas aparecem no topo da lista. O resumo mostra "X categorias saudáveis, Y baixas, Z estouradas".
- A hierarquia aparece com indentação e grupos que abrem e fecham, com totais acumulados no pai (também na comparação da 6.9).
- O histórico (6.8) tem tabela ordenável, gráfico de linhas alocado × gasto, filtro de período (3 m, 6 m, 1 ano, tudo), estatísticas e export CSV. A comparação (6.9) tem tabela ordenável (padrão: maior estouro no topo), gráfico de barras alocado × gasto, totais, filtros de status e de período e export CSV/PDF.

## Cross-Story Dependencies

- A 6.1 e a 6.2 estendem as categorias do Epic 3 (Stories 3.4 e 3.5).
- A 6.3 cria a base para 6.4–6.9. A 6.5 depende da 6.3 e da 6.4. A 6.7 reaproveita a regra de status da 6.4. A 6.8 e a 6.9 leem as alocações e os gastos.
- A 6.6 se liga aos pontos de escrita de transação do Epic 5: conclusão da importação (5.10), edição (5.12), inclusão manual (5.13), remoção e confirmação de parcela futura (5.7). Todos passam pelo núcleo único de transação.
- O "total disponível" usa os saldos de contas do Epic 4.
- O Dashboard GPS (Epic 7) consome o status e os saldos por categoria. O export de orçamento do Epic 9 usa estas tabelas.
