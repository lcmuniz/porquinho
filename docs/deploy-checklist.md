# Checklist de deploy

Itens que o código não consegue garantir sozinho: ficam na configuração do Stripe Dashboard e nas variáveis do servidor.
Repita a seção do Stripe no modo **test** e no modo **live**, porque cada modo tem sua configuração.
Só marque um item depois de executar a verificação dele.

## 1. Variáveis do backend

- [ ] `STRIPE_SECRET_KEY` é a chave `sk_live_...` (em homologação, `sk_test_...`) da mesma conta onde o webhook foi criado.
- [ ] `STRIPE_WEBHOOK_SECRET` é o `whsec_...` do endpoint do item 2, e não o do `stripe listen` local.
- [ ] `FRONTEND_URL` é a URL pública do front (o Checkout volta para ela).

Como verificar: abra `/assinatura` e inicie um Checkout. Se a tela do Stripe abrir, a chave está ok. Se aparecer a mensagem
de pagamento indisponível (503), a chave está vazia ou errada.

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
