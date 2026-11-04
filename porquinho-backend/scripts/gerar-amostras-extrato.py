import os, sys, uuid, random
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
random.seed(42)

def w(name, text, enc="utf-8", bom=False):
    data = text.encode(enc)
    if bom: data = b"\xef\xbb\xbf" + data
    open(os.path.join(OUT, name), "wb").write(data)

def brl(v, thousands=True):
    s = f"{abs(v):,.2f}" if thousands else f"{abs(v):.2f}"
    s = s.replace(",", "X").replace(".", ",").replace("X", ".")
    return ("-" if v < 0 else "") + s

# (dia, descrição canônica, valor) — outubro/2026, valores negativos = saída
def dt(d): return f"2026-10-{d:02d}"

# ---------- OFX ----------
def ofx_header():
    return ("OFXHEADER:100\nDATA:OFXSGML\nVERSION:102\nSECURITY:NONE\nENCODING:USASCII\n"
            "CHARSET:1252\nCOMPRESSION:NONE\nOLDFILEUID:NONE\nNEWFILEUID:NONE\n\n")

def ofx_bank(org, fid, bankid, branch, acct, txs, closing, balance, card=False):
    c = (lambda tag: f"</{tag}>") if closing else (lambda tag: "")
    L = [ofx_header(), "<OFX>", "<SIGNONMSGSRSV1>", "<SONRS>", "<STATUS>",
         f"<CODE>0{c('CODE')}", f"<SEVERITY>INFO{c('SEVERITY')}", "</STATUS>",
         f"<DTSERVER>20261031120000[-3:BRT]{c('DTSERVER')}", f"<LANGUAGE>POR{c('LANGUAGE')}",
         "<FI>", f"<ORG>{org}{c('ORG')}", f"<FID>{fid}{c('FID')}", "</FI>", "</SONRS>", "</SIGNONMSGSRSV1>"]
    if card:
        L += ["<CREDITCARDMSGSRSV1>", "<CCSTMTTRNRS>", f"<TRNUID>1{c('TRNUID')}", "<STATUS>",
              f"<CODE>0{c('CODE')}", f"<SEVERITY>INFO{c('SEVERITY')}", "</STATUS>", "<CCSTMTRS>",
              f"<CURDEF>BRL{c('CURDEF')}", "<CCACCTFROM>", f"<ACCTID>{acct}{c('ACCTID')}", "</CCACCTFROM>"]
    else:
        L += ["<BANKMSGSRSV1>", "<STMTTRNRS>", f"<TRNUID>1{c('TRNUID')}", "<STATUS>",
              f"<CODE>0{c('CODE')}", f"<SEVERITY>INFO{c('SEVERITY')}", "</STATUS>", "<STMTRS>",
              f"<CURDEF>BRL{c('CURDEF')}", "<BANKACCTFROM>", f"<BANKID>{bankid}{c('BANKID')}",
              f"<BRANCHID>{branch}{c('BRANCHID')}", f"<ACCTID>{acct}{c('ACCTID')}",
              f"<ACCTTYPE>CHECKING{c('ACCTTYPE')}", "</BANKACCTFROM>"]
    L += ["<BANKTRANLIST>", f"<DTSTART>20261001000000[-3:BRT]{c('DTSTART')}",
          f"<DTEND>20261031000000[-3:BRT]{c('DTEND')}"]
    for i, (d, memo, v, fitid) in enumerate(txs):
        L += ["<STMTTRN>", f"<TRNTYPE>{'CREDIT' if v > 0 else 'DEBIT'}{c('TRNTYPE')}",
              f"<DTPOSTED>202610{d:02d}000000[-3:BRT]{c('DTPOSTED')}",
              f"<TRNAMT>{v:.2f}{c('TRNAMT')}", f"<FITID>{fitid}{c('FITID')}",
              f"<CHECKNUM>{100000+i}{c('CHECKNUM')}", f"<MEMO>{memo}{c('MEMO')}", "</STMTTRN>"]
    L += ["</BANKTRANLIST>", "<LEDGERBAL>", f"<BALAMT>{balance:.2f}{c('BALAMT')}",
          f"<DTASOF>20261031000000[-3:BRT]{c('DTASOF')}", "</LEDGERBAL>"]
    L += (["</CCSTMTRS>", "</CCSTMTTRNRS>", "</CREDITCARDMSGSRSV1>"] if card
          else ["</STMTRS>", "</STMTTRNRS>", "</BANKMSGSRSV1>"])
    L += ["</OFX>", ""]
    return "\n".join(L)

def fit(prefix, n): return [f"{prefix}{i:04d}" for i in range(1, n + 1)]

# ---------- Nubank conta ----------
nu = [
 (1, "Transferência recebida pelo Pix - JOANA PEREIRA LIMA - •••.432.198-•• - BCO DO BRASIL S.A. (0001) Agência: 1234 Conta: 98765-4", 2000.00),
 (3, "Compra no débito - IFOOD *RESTAURANTE SABOR", -54.90),
 (4, "Transferência enviada pelo Pix - CARLOS EDUARDO SOUZA - •••.876.543-•• - ITAÚ UNIBANCO S.A. (0341) Agência: 4321 Conta: 11223-3", -150.00),
 (6, "Pagamento de boleto efetuado - ENEL DISTRIBUICAO SP", -187.43),
 (9, "Compra no débito - UBER *TRIP", -23.70),
 (12, "Transferência enviada pelo Pix - MERCADO BOM PRECO LTDA - 12.345.678/0001-90 - CAIXA ECONOMICA FEDERAL (0104) Agência: 0001 Conta: 4455-6", -312.85),
 (15, "Transferência recebida pelo Pix - ANA BEATRIZ COSTA - •••.111.222-•• - NU PAGAMENTOS - IP (0260) Agência: 1 Conta: 7788990-1", 85.00),
 (18, "Pagamento da fatura - Cartão Nubank", -1243.67),
 (21, "Compra no débito - DROGASIL 1234", -67.80),
 (27, "Resgate RDB", 500.00),
]
nu_ids = [str(uuid.UUID(int=random.getrandbits(128))) for _ in nu]
csv = "Data,Valor,Identificador,Descrição\n" + "".join(
    f"{d:02d}/10/2026,{v:.2f},{i},\"{m}\"\n" if "," in m else f"{d:02d}/10/2026,{v:.2f},{i},{m}\n"
    for (d, m, v), i in zip(nu, nu_ids))
w("nubank-conta.csv", csv)
w("nubank-conta.ofx", ofx_bank("NU PAGAMENTOS S.A.", "260", "0260", "0001", "7788990-1",
    [(d, m, v, i) for (d, m, v), i in zip(nu, nu_ids)], True, 2845.12), "cp1252", )

# ---------- Nubank cartão (CSV) ----------
nuc = [
 ("2026-09-28", "Amazon Marketplace", 129.90),
 ("2026-09-30", "Netflix.Com", 44.90),
 ("2026-10-02", "Magazine Luiza - Parcela 3/10", 189.90),
 ("2026-10-05", "Ifood *Ifood", 72.40),
 ("2026-10-08", "Posto Shell Av Paulista", 210.00),
 ("2026-10-11", "Smart Fit - Parcela 2/12", 119.90),
 ("2026-10-14", "Pagamento recebido", -1243.67),
 ("2026-10-16", "Estorno - Amazon Marketplace", -129.90),
 ("2026-10-20", "Spotify", 21.90),
]
w("nubank-cartao.csv", "date,title,amount\n" + "".join(f"{d},{t},{a:.2f}\n" for d, t, a in nuc))

# ---------- Banco do Brasil (CSV clássico, ISO-8859-1) ----------
bb = [
 (1, "Pagto Salario", "01/10 SALARIO EMPRESA FICTICIA LTDA", 9850.00),
 (1, "Pix - Enviado", "01/10 09:15 Joana Pereira Lima", -2000.00),   # transferência p/ Nubank próprio
 (2, "Pagamento de Boleto", "SABESP", -98.32),
 (5, "Compra com Cartão", "05/10 13:42 PADARIA PAO QUENTE", -18.50),
 (7, "Pix - Recebido", "07/10 18:03 Roberto Alves", 300.00),
 (10, "Pagamento Cartão de Crédito", "OUROCARD VISA", -2104.55),
 (13, "Tarifa Pacote de Serviços", "Cobrança referente 10/2026", -42.90),
 (16, "Pix - Enviado", "16/10 20:11 Condominio Edificio Aurora", -850.00),
 (22, "Compra com Cartão", "22/10 11:05 POSTO IPIRANGA", -180.00),
 (30, "BB Rende Fácil", "Rende Facil", 12.47),
]
lines = ['"Data","Dependencia Origem","Histórico","Data do Balancete","Número do documento","Valor",',
         '"30/09/2026","","Saldo Anterior","","0","5230.18",']
for i, (d, h, det, v) in enumerate(bb):
    lines.append(f'"{d:02d}/10/2026","","{h} - {det}","","{90000+i}","{v:.2f}",')
bb_final = round(5230.18 + sum(v for *_, v in bb), 2)
lines.append(f'"31/10/2026","","S A L D O","","0","{bb_final:.2f}",')
w("bb-conta.csv", "\r\n".join(lines) + "\r\n", "iso-8859-1")
w("bb-conta.ofx", ofx_bank("Banco do Brasil", "001", "001", "1234-5", "98765-4",
    [(d, f"{h} {det}"[:32].upper(), v, f) for (d, h, det, v), f in zip(bb, fit("2026100100", len(bb)))],
    False, bb_final), "cp1252")

# ---------- Itaú (CSV sem cabeçalho, ';', cp1252) ----------
it = [
 (2, "SISPAG SALARIO EMPRESA FICT", 7420.00),
 (3, "PIX TRANSF CARLOS 03/10", -150.00),
 (4, "PIX QRS IFOOD 04/10", -61.30),
 (6, "TAR MAXICONTA 09/26", -59.00),
 (9, "DA  VIVO FIXO 0012345", -119.99),
 (11, "PIX TRANSF MARIA S11/10", -400.00),
 (14, "TED 237.1234JOAO P SILVA", 1200.00),
 (18, "ITAU UNIBANCO CARTAO VISA", -1876.40),
 (23, "INT PAG TIT BANCO 341", -230.15),
 (29, "REND PAGO APLIC AUT MAIS", 8.21),
]
w("itau-conta.csv", "".join(f"{d:02d}/10/2026;{m};{brl(v, False)}\r\n" for d, m, v in it), "cp1252")
w("itau-conta.ofx", ofx_bank("Itau Unibanco S.A.", "341", "0341", "1234", "56789-0",
    [(d, m, v, f) for (d, m, v), f in zip(it, fit("2026100200", len(it)))], False, 6982.37), "cp1252")

# ---------- Itaú cartão (OFX de cartão, com parcelas) ----------
itc = [
 (1, "MAGAZINELUIZA  03/10", -189.90),
 (3, "CASASBAHIA     07/12", -249.00),
 (5, "RAIA DROGASIL", -45.60),
 (8, "MERCADOLIVRE*LOJA 01/03", -333.33),
 (12, "UBER *TRIP HELP.UBER.COM", -18.90),
 (15, "PAGAMENTO EFETUADO", 1876.40),
 (19, "PADARIA REAL", -27.30),
 (25, "ANUIDADE DIFERENCIADA 05/12", -35.00),
]
w("itau-cartao.ofx", ofx_bank("Itau Unibanco S.A.", "341", "0341", "", "4321XXXXXXXX9876",
    [(d, m, v, f) for (d, m, v), f in zip(itc, fit("2026100300", len(itc)))], False, -899.03, card=True), "cp1252")

# ---------- Bradesco (CSV com cabeçalho extra, crédito/débito separados, cp1252) ----------
br = [
 (1, "SALDO ANTERIOR", None, None),
 (3, "TRANSFERENCIA PIX DES: CARLOS EDUARDO SOUZA 03/10", None, 150.00),
 (5, "PAGTO ELETRON COBRANCA CONDOMINIO", None, 720.00),
 (5, "CREDITO DE SALARIO EMPRESA FICTICIA", 6100.00, None),
 (8, "VISA ELECTRON PADARIA DO ZE", None, 22.40),
 (12, "TRANSFERENCIA PIX REM: LUCIA HELENA MOTA 12/10", 250.00, None),
 (15, "CESTA B.EXPRESSO4", None, 49.90),
 (19, "GASTOS CARTAO DE CREDITO BRADESCARD", None, 1534.22),
 (24, "DEB.AUTOR. CLARO S.A.", None, 89.99),
 (28, "RENTAB.INVEST FACILCRED", 5.33, None),
]
lines = ["Extrato de: Agência: 1234 | Conta: 0012345-6", "",
         "Data;Histórico;Docto.;Crédito (R$);Débito (R$);Saldo (R$)"]
saldo = 3120.50
for i, (d, h, cr, db) in enumerate(br):
    if cr is None and db is None:
        lines.append(f"30/09/2026;{h};;;;{brl(saldo)}"); continue
    saldo += (cr or 0) - (db or 0)
    lines.append(f"{d:02d}/10/26;{h};{1000+i};{brl(cr) if cr else ''};{brl(db) if db else ''};{brl(saldo)}")
tot_cr = sum(cr or 0 for _, _, cr, _ in br); tot_db = sum(db or 0 for _, _, _, db in br)
lines += ["", f"Total;;;{brl(tot_cr)};{brl(tot_db)};", "Os dados acima têm como base 31/10/2026 às 10:12"]
w("bradesco-conta.csv", "\r\n".join(lines) + "\r\n", "cp1252")
w("bradesco-conta.ofx", ofx_bank("BANCO BRADESCO S/A", "237", "0237", "1234", "0012345-6",
    [(d, h, (cr or 0) - (db or 0), f) for (d, h, cr, db), f in zip(br[1:], fit("2026100400", len(br) - 1))],
    False, saldo), "cp1252")

# ---------- Caixa (CSV ';' com Deb_Cred, data AAAAMMDD) ----------
cx = [
 (2, "CRED SALARIO", 4380.00),
 (3, "ENVIO PIX", -200.00),
 (6, "COMPRA ELO", -134.27),
 (8, "PIX RECEBIDO", 75.00),
 (10, "DEB AUT CEMIG", -156.80),
 (14, "PAG BOLETO", -410.00),
 (17, "SAQUE ATM", -100.00),
 (21, "ENVIO PIX", -60.00),
 (26, "CRED JUROS POUP", 9.12),
 (30, "TAR CESTA SERV", -32.50),
]
lines = ['"Conta";"Data_Mov";"Nr_Doc";"Historico";"Valor";"Deb_Cred"']
for i, (d, h, v) in enumerate(cx):
    lines.append(f'"00000000044556";"202610{d:02d}";"{700000+i:06d}";"{h}";"{abs(v):.2f}";"{"C" if v > 0 else "D"}"')
w("caixa-conta.csv", "\r\n".join(lines) + "\r\n", "cp1252")
w("caixa-conta.ofx", ofx_bank("CAIXA ECONOMICA FEDERAL", "104", "104", "0001", "4455-6",
    [(d, h, v, f) for (d, h, v), f in zip(cx, fit("2026100500", len(cx)))], True, 7470.73), "cp1252")

# ---------- Casos de erro ----------
full = ofx_bank("NU PAGAMENTOS S.A.", "260", "0260", "0001", "7788990-1",
    [(d, m, v, i) for (d, m, v), i in zip(nu, nu_ids)], True, 2845.12)
w("erro-ofx-truncado.ofx", full[: len(full) // 2], "cp1252")
w("erro-csv-linhas-invalidas.csv",
  "Data,Valor,Identificador,Descrição\n"
  "01/10/2026,2000.00,a1b2c3d4-0000-0000-0000-000000000001,Transferência recebida pelo Pix - JOANA PEREIRA LIMA\n"
  "32/10/2026,-54.90,a1b2c3d4-0000-0000-0000-000000000002,Compra no débito - data inválida\n"
  "04/10/2026,abc,a1b2c3d4-0000-0000-0000-000000000003,Valor inválido\n"
  "06/10/2026,,a1b2c3d4-0000-0000-0000-000000000004,Valor ausente\n"
  ",-23.70,a1b2c3d4-0000-0000-0000-000000000005,Data ausente\n"
  "09/10/2026,-23.70,a1b2c3d4-0000-0000-0000-000000000006,Compra no débito - UBER *TRIP\n")
w("erro-csv-banco-desconhecido.csv", "when;what;how much\n2026-10-01;Coffee;-3.50\n2026-10-02;Book;-20.00\n")
w("erro-arquivo-vazio.csv", "")
w("erro-nao-e-extrato.ofx", "Este arquivo tem extensão .ofx mas é texto comum.\n")
