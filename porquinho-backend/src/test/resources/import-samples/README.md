# Amostras de extrato (fictícias)

Arquivos para desenvolver e testar a importação do Epic 5. **Todos os dados são inventados**: nomes, CPFs mascarados, CNPJ, agências, contas e valores. Nenhum arquivo veio de um banco.

Gerados por `porquinho-backend/scripts/gerar-amostras-extrato.py` (`python3 -I scripts/gerar-amostras-extrato.py src/test/resources/import-samples`). Para mudar uma amostra, edite o gerador e gere de novo; não edite os arquivos à mão.

## Grau de confiança no formato

- **OFX:** segue o padrão OFX 1.0.2 (SGML) que os bancos brasileiros exportam: cabeçalho `OFXHEADER:100`, `CHARSET:1252`, datas `AAAAMMDDHHMMSS[-3:BRT]`, `FITID` por lançamento. Nubank e Caixa fecham as tags; BB, Itaú e Bradesco não fecham as tags-folha (SGML), como costuma acontecer nos arquivos reais. Confiança alta na estrutura; os textos de `MEMO` são aproximações.
- **CSV:** cada banco tem layout próprio e sem documentação pública. Os layouts abaixo foram montados a partir de projetos open-source e do conhecimento disponível em 2026-11, **sem arquivo real para conferir**. Trate como hipótese: ao conseguir um export real, compare com a amostra, ajuste o gerador e o parser.

| Arquivo | Banco | Layout | Encoding | Confiança |
|---|---|---|---|---|
| `nubank-conta.csv` | Nubank (conta) | `Data,Valor,Identificador,Descrição`; data DD/MM/AAAA; valor com ponto e sinal | UTF-8 | média |
| `nubank-cartao.csv` | Nubank (cartão) | `date,title,amount`; data AAAA-MM-DD; compra positiva, pagamento/estorno negativo; "Parcela 3/10" no título | UTF-8 | média |
| `bb-conta.csv` | Banco do Brasil | `"Data","Dependencia Origem","Histórico","Data do Balancete","Número do documento","Valor",`; linhas "Saldo Anterior" e "S A L D O" (não são lançamentos) | ISO-8859-1 | baixa-média |
| `itau-conta.csv` | Itaú | sem cabeçalho; `DD/MM/AAAA;descrição;valor` com vírgula decimal | cp1252 | baixa |
| `bradesco-conta.csv` | Bradesco | linhas de título antes do cabeçalho; `Data;Histórico;Docto.;Crédito (R$);Débito (R$);Saldo (R$)`; data DD/MM/AA; milhar com ponto; linha "Total" e rodapé | cp1252 | baixa |
| `caixa-conta.csv` | Caixa | `"Conta";"Data_Mov";"Nr_Doc";"Historico";"Valor";"Deb_Cred"`; data AAAAMMDD; valor sem sinal, `D`/`C` | cp1252 | baixa |

## O que cada conjunto exercita

- **Mesmo mês em dois formatos** (outubro de 2026): para cada banco, o OFX e o CSV têm os mesmos lançamentos. Importar os dois na mesma conta deve detectar todos como duplicatas (Story 5.5).
- **Transferência entre contas próprias:** no BB, "Pix - Enviado" de R$ 2.000,00 em 01/10 para Joana Pereira Lima; no Nubank, "Transferência recebida pelo Pix - JOANA PEREIRA LIMA" de R$ 2.000,00 no mesmo dia. Se a transferência foi registrada na Story 4.5, a importação não a trata como gasto ou receita (decisão D4).
- **Pagamento de fatura** (D8): "Pagamento da fatura - Cartão Nubank" de R$ 1.243,67 em 18/10 em `nubank-conta.csv`/`.ofx` e "Pagamento recebido" do mesmo valor em `nubank-cartao.csv`; também `ITAU UNIBANCO CARTAO VISA` (Itaú conta) × `PAGAMENTO EFETUADO` (Itaú cartão), `Pagamento Cartão de Crédito` (BB) e `GASTOS CARTAO DE CREDITO BRADESCARD` (Bradesco).
- **Pix** (Story 5.6): formatos longos do Nubank (nome, CPF mascarado `•••.432.198-••`, CNPJ, banco/agência/conta), curtos do Itaú (`PIX TRANSF CARLOS 03/10`, `PIX QRS IFOOD`), BB (`Pix - Enviado`/`Pix - Recebido` com nome), Bradesco (`TRANSFERENCIA PIX DES:`/`REM:`) e Caixa (`ENVIO PIX`/`PIX RECEBIDO`, sem nome).
- **Parcelas** (Story 5.7): `nubank-cartao.csv` (`Parcela 3/10`, `Parcela 2/12`) e `itau-cartao.ofx` (`MAGAZINELUIZA  03/10`, `MERCADOLIVRE*LOJA 01/03`, `ANUIDADE DIFERENCIADA 05/12`).
- **Cartão de crédito:** `nubank-cartao.csv` e `itau-cartao.ofx` (`CREDITCARDMSGSRSV1`/`CCSTMTRS`), incluindo pagamento da fatura e estorno.
- **Erros** (NFR57):
  - `erro-ofx-truncado.ofx`: OFX cortado no meio;
  - `erro-nao-e-extrato.ofx`: extensão .ofx com texto comum;
  - `erro-ofx-linhas-invalidas.ofx`: OFX de conta Itaú (SGML, cp1252) com data inválida, `TRNAMT` ausente, `TRNAMT` "abc" e "0.00" entre lançamentos bons (2 válidos, um com vírgula decimal e acento; 4 pulados) (Story 5.4);
  - `erro-csv-banco-desconhecido.csv`: CSV válido de layout desconhecido;
  - `erro-csv-linhas-invalidas.csv`: layout Nubank com data inválida, valor inválido, valor ausente e data ausente entre linhas boas (2 linhas válidas, 4 puladas);
  - `erro-arquivo-vazio.csv`.
