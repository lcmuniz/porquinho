# Checklist de deploy

Itens que o código não consegue garantir sozinho: configuração do Stripe Dashboard, variáveis do servidor e decisões pendentes antes de produção.
Repita a seção do Stripe no modo **test** e no modo **live**, porque cada modo tem sua configuração.
Só marque um item depois de executar a verificação dele.

## 1. Variáveis do backend

- [ ] `STRIPE_SECRET_KEY` é a chave `sk_live_...` (em homologação, `sk_test_...`) da mesma conta onde o webhook foi criado.
- [ ] `STRIPE_WEBHOOK_SECRET` é o `whsec_...` do endpoint do item 2, e não o do `stripe listen` local.
- [ ] `FRONTEND_URL` é a URL pública do front (o Checkout volta para ela).

Como verificar: abra `/assinatura` e inicie um Checkout. Se a tela do Stripe abrir, a chave está ok. Se aparecer a mensagem
de pagamento indisponível (503), a chave está vazia ou errada.

### E-mail (Resend)

- [ ] `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD` e `MAIL_FROM` preenchidos. Sem `SMTP_HOST` ou sem `MAIL_FROM`, a retenção de quem não converte para de mandar avisos e, por isso, não apaga ninguém que ainda não foi avisado: esses dados ficam guardados além dos 30 dias até o e-mail voltar. Quem já recebeu o aviso de 1 dia continua sendo apagado no prazo. Sem `SMTP_HOST` ou `MAIL_FROM`, lembretes de trial e alertas de orçamento também não são marcados e saem quando o e-mail volta, se ainda estiverem no prazo (alertas de um mês já encerrado se perdem).

- [ ] `EMAIL_UNSUBSCRIBE_SECRET` preenchido com um valor aleatório longo (ex.: `openssl rand -base64 32`) e estável entre deploys. Ele assina os links de descadastro do alerta de orçamento (cabeçalho `List-Unsubscribe` e link no corpo). Vazio, o backend sorteia um segredo a cada subida e os links dos e-mails já enviados passam a dar "link inválido" depois de um restart. Trocar o valor invalida todos os links já enviados.
- [ ] `APP_BASE_URL` é a URL pública que chega na API (o mesmo domínio do front atrás do nginx). O link de descadastro aponta para `<APP_BASE_URL>/api/v1/email/unsubscribe`.

Como verificar o descadastro: no log da subida não aparece `EMAIL_UNSUBSCRIBE_SECRET empty`. Num alerta de orçamento recebido no Gmail, "Mostrar original" lista `List-Unsubscribe` e `List-Unsubscribe-Post: List-Unsubscribe=One-Click`, e o DKIM sai `PASS`. O link do corpo abre `/descadastro`, e o botão desliga a opção em Configurações da conta.

Como verificar: no log da subida não aparece `SMTP not configured (SMTP_HOST empty)` nem `MAIL_FROM empty`, e o e-mail de exportação pronta (Dashboard → "Exportar meus dados", em `/auth/data-export`) chega na caixa de entrada. A recuperação de senha não serve de teste: quem manda esse e-mail é o Supabase. Com o e-mail desligado, o job diário de retenção também loga `E-mail not configured: N retention warning(s) postponed`. Credencial errada (ou From recusado) não aparece na subida, só no envio: o sinal é `Failed to send` no log e falhas no resumo do job de retenção. Ela não apaga ninguém sem aviso: o aviso só é marcado quando o SMTP aceita o envio, é tentado de novo a cada execução e o purge espera.

## 2. Webhook endpoint

Dashboard → Developers → Webhooks → Add endpoint.

- [ ] URL: `https://<domínio>/api/v1/webhooks/stripe`
- [ ] Eventos assinados, os quatro e só eles:
  - `checkout.session.completed`: ativa a assinatura depois do Checkout
  - `invoice.paid`: registra cada cobrança mensal (sem ele, nenhuma renovação é registrada)
  - `invoice.payment_failed`: marca a falha e envia o e-mail de cada tentativa
  - `customer.subscription.deleted`: encerra o acesso quando o Stripe cancela a assinatura
- [ ] O signing secret desse endpoint é o que está em `STRIPE_WEBHOOK_SECRET`.

Como verificar:

```bash
stripe webhook_endpoints list --api-key <sk_live_ou_sk_test>
```

A saída deve listar a URL acima com `enabled_events` igual aos quatro eventos. Depois, em Dashboard → Webhooks → endpoint →
"Send test event", envie `invoice.payment_failed`. A resposta deve ser `200`: o backend ignora a assinatura desconhecida,
mas responde 200 a esse evento. Se vier `400`, o `STRIPE_WEBHOOK_SECRET` não confere. Não use `invoice.paid` nesse teste,
porque a primeira fatura de uma assinatura desconhecida recebe `503` de propósito (o backend espera o Checkout chegar).

O parser aceita o formato de invoice da API 2026 (`parent.subscription_details.subscription`) e o anterior
(`subscription`), então a versão da API do endpoint não importa.

## 3. Novas tentativas de cobrança (Story 2.7)

Dashboard → Settings → Billing → Revenue recovery → Retries.

- [ ] Smart Retries desligado: use o agendamento customizado.
- [ ] Duas novas tentativas: **3 dias** e **10 dias** depois da primeira falha.
- [ ] Depois da última tentativa: **cancelar a assinatura** (não "deixar past due" nem "marcar como unpaid").

Por que importa: o backend entende que a tentativa foi a última quando o `invoice.payment_failed` chega sem
`next_payment_attempt`. Nesse caso o e-mail sai sem link de pagamento, e o acesso só acaba quando chega o
`customer.subscription.deleted`. Se a regra for outra, o usuário fica em "pagamento falhou" para sempre.

Como verificar (modo test, com test clock):

1. Dashboard → Billing → Test clocks → crie um clock e um cliente nele.
2. Crie uma assinatura de R$ 19,90/mês com **1 dia de trial** para esse cliente, com o cartão `4000 0000 0000 0341`. Esse
   cartão é aceito no cadastro, mas toda cobrança falha. O trial faz a primeira fatura ser de R$ 0, que o backend aceita
   com 200. Sem trial, essa fatura receberia 503 e seria reenviada por dias.
3. Avance o clock até o fim do trial. A cobrança falha, e a fatura deve mostrar a próxima tentativa em +3 dias.
4. Avance mais 3 dias. A próxima tentativa deve aparecer em +7 dias, ou seja, 10 dias depois da primeira falha.
5. Avance mais 7 dias. A assinatura deve ficar `canceled`, e o endpoint deve ter recebido `customer.subscription.deleted`
   com resposta `200`.

Esse cliente não existe no Porquinho: o backend ignora os eventos dele e responde 200. O teste valida só a configuração do
Stripe. O fluxo com usuário real está no roteiro manual do Epic 2 (2.7).

## 4. Importação de extratos (Epic 5)

Pendências da retro do Epic 5 (X3, W1 a W5). Os itens marcados com **Decisão** precisam de uma escolha antes de
produção. Os outros precisam de uma conferência.

### 4.1 Decisão: transferência que repete uma transação importada (X3, W1)

Hoje, quem importa um extrato com uma TED e depois registra a mesma TED como transferência (Story 4.5) fica com o valor
contado duas vezes no saldo. O mesmo acontece se a transferência for registrada entre o upload e a conclusão da
importação. A detecção de duplicatas só roda no processamento do arquivo, e `TransferService` não consulta
`transactions`.

- [ ] Escolher uma opção:
  - (a) ao registrar a transferência, procurar a transação importada que casa (mesma conta, valor e data próxima) e
    vinculá-la à transferência, sem lançar de novo;
  - (b) só avisar o usuário que existe uma transação parecida e deixar que ele decida;
  - (c) aceitar o risco e explicar na ajuda que a transação importada deve ser excluída à mão.

### 4.2 Decisão: FITID reutilizado pelo banco (W2)

O passo 1 da detecção de duplicatas descarta toda linha cujo FITID já existe, sem conferir valor nem tipo. Se um banco
reutilizar o FITID para lançamentos diferentes, um lançamento real some da importação.

- [ ] Conferir em extratos OFX reais (vários meses do mesmo banco) se algum FITID se repete com valor ou tipo diferente.
- [ ] Se repetir: exigir no passo 1 o mesmo valor e o mesmo tipo além do FITID. Se não repetir: registrar o resultado
  e manter como está.

### 4.3 Conferência com extratos reais (W3)

As regras de parcelas foram escritas sem arquivo real. Para cada banco aceito, use uma fatura de cartão real que tenha
compra parcelada.

- [ ] O marcador de parcela está num formato reconhecido. Formatos como `PARCELA 03 DE 10`, `03 DE 10` ou `3/10` com um
  dígito não são reconhecidos: a compra entraria como lançamento comum, sem parcelas futuras.
- [ ] A data de cada parcela no arquivo é a data da cobrança, não a da compra original. Se for a da compra, a
  parcela vai para o mês errado, e cada fatura pode abrir um grupo novo com parcelas futuras repetidas.
- [ ] Depois de importar duas faturas seguidas, a mesma compra aparece como um grupo só, com as futuras certas.

### 4.4 Decisão jurídica: política de privacidade (W4)

`PrivacyView.vue` precisa de revisão jurídica antes de produção:

- [ ] Transferência internacional de dados (LGPD art. 33): a Anthropic recebe as descrições das transações para
  classificar. Stripe e Resend também processam dados fora do Brasil, e o Supabase também, se a região do projeto não
  for São Paulo.
- [ ] Prazo de retenção de cada tipo de dado. Para pagamentos, a decisão já tomada (retro Epic 2, item 7) é esta: o
  Porquinho apaga o histórico de pagamentos junto com a conta, e as faturas continuam no Stripe, que é onde fica o
  registro de cobrança. O texto deve dizer isso.
- [ ] Data de "Última atualização" fixa. Hoje é `new Date()`, que mostra sempre o dia em que a página é aberta.
- [ ] A seção 10 promete aviso por e-mail em mudanças significativas, e esse envio não existe. Implementar ou tirar a
  promessa do texto.

### 4.5 Chave da Anthropic (W5)

Com a variável vazia, o backend avisa na subida (`ANTHROPIC_API_KEY not set: imported transactions stay unclassified`).
Com uma chave errada, ele sobe sem nenhum aviso, e tudo o que for importado fica "Não classificado".

- [ ] A chave responde:

  ```bash
  curl -s -o /dev/null -w '%{http_code}\n' https://api.anthropic.com/v1/models \
    -H "x-api-key: $ANTHROPIC_API_KEY" -H "anthropic-version: 2023-06-01"
  ```

  O resultado deve ser `200`. Se vier `401`, a chave está errada.
- [ ] No servidor, importe um extrato pequeno. As transações devem chegar com categoria, e o log não pode ter
  `AI batch of ... failed: com.anthropic.errors.UnauthorizedException`.
- [ ] Decisão: validar a chave também na subida do backend (uma chamada a `/v1/models` com log de erro), ou ficar só
  com esta conferência manual.

## 5. Painel de admin (Story 10.1)

Não há tela para promover administrador. O primeiro admin é promovido direto no banco (SQL Editor do Supabase), depois
que a pessoa já tem conta no Porquinho:

```sql
UPDATE users SET is_admin = true WHERE lower(email) = lower('admin@exemplo.com') AND deleted_at IS NULL;
```

- [ ] O `UPDATE` acima afetou 1 linha.
- [ ] Logado com essa conta, o Dashboard mostra o link "Admin" e `/admin` abre a aba "Saúde do sistema".
- [ ] Logado com outra conta, o link não aparece e `/admin` mostra "Acesso restrito".

Para tirar o papel, rode o mesmo `UPDATE` com `is_admin = false`. O papel é lido do banco a cada chamada, então vale na
hora, sem novo login.

O uptime do painel é a fração dos minutos em que o backend gravou o snapshot: mede o backend no ar e gravando no
banco (banco fora também conta como fora), não o nginx nem o frontend. Ele só começa a contar depois do primeiro
snapshot (1 por minuto após o deploy); antes disso mostra "sem dados".

### 5.1 Alertas de métricas críticas (Story 10.5)

- [ ] Opcional: `SLACK_WEBHOOK_URL` com a URL de um Incoming Webhook do Slack. Vazio, os alertas vão só por e-mail
  (para todo admin) e para a aba "Alertas" do `/admin`. A URL é segredo: não aparece no log.
- [ ] Sem SMTP (`SMTP_HOST`/`MAIL_FROM`), o alerta é gravado e aparece no painel, que avisa "E-mail não configurado".

- [ ] `FRONTEND_URL` com o endereço público do frontend: o link do e-mail e do Slack é `{FRONTEND_URL}/admin?tab=...`.
  Sem ela, o link aponta para `http://localhost:5173`.

O job roda a cada 5 minutos, então o alerta pode levar até 5 minutos para sair. Como verificar: com o Redis parado
por alguns minutos, os admins recebem o e-mail "Porquinho - Alerta: Redis" e a aba "Alertas" mostra o alerta aberto.
Repetir o teste dentro de 1 hora não alerta de novo: há no máximo um alerta por métrica por hora, e um alerta só volta
depois que a métrica normaliza (Redis de pé por uma execução). Queda total do backend não gera alerta (o job cai junto):
para isso use um monitor externo do healthcheck.
