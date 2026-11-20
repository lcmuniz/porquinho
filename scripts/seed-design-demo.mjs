#!/usr/bin/env node
// Seed do usuário de demonstração para screenshots do redesign (design-demo+porquinho@eficaztech.cloud).
//
// Uso: node scripts/seed-design-demo.mjs
//   API_BASE (opcional): base da API; padrão é a homologação. Ex.: API_BASE=http://localhost:8080 para o backend local.
//
// Re-executável: se o usuário já existe, apaga só os dados de domínio DELE (filtro user_id em toda escrita via
// PostgREST) e recria tudo. Credenciais em .env.design-demo (gerado na primeira execução, gitignored).
// O que a API não deixa fazer (datas passadas de saldo, conta, metas) é ajustado via PostgREST com a service role,
// sempre filtrando pelo user_id deste usuário.

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { randomBytes, randomUUID } from 'node:crypto';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const API_BASE = (process.env.API_BASE || 'https://porquinho-staging.apps.eficaztech.cloud').replace(/\/$/, '');
const EMAIL = 'design-demo+porquinho@eficaztech.cloud';
const START = '2026-07-21'; // primeiro dia com movimento; contas "criadas" em 20/07
const REFERENCE = '2026-07-20T12:00:00-03:00';
const TODAY = '2026-11-20';

// ---------- env ----------
function readEnv(path) {
  const out = {};
  for (const line of readFileSync(path, 'utf8').split('\n')) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
    if (m) out[m[1]] = m[2].replace(/^["']|["']$/g, '');
  }
  return out;
}
const backendEnv = readEnv(join(ROOT, 'porquinho-backend/.env'));
const SUPABASE_URL = backendEnv.SUPABASE_PROJECT_URL;
const SERVICE_KEY = backendEnv.SUPABASE_SERVICE_ROLE_KEY;
assert.ok(SUPABASE_URL && SERVICE_KEY, 'SUPABASE_PROJECT_URL e SUPABASE_SERVICE_ROLE_KEY precisam estar no .env do backend');

const credPath = join(ROOT, '.env.design-demo');
let PASSWORD;
if (existsSync(credPath)) {
  PASSWORD = readEnv(credPath).DESIGN_DEMO_PASSWORD;
} else {
  PASSWORD = 'Pq!' + randomBytes(18).toString('base64url') + '9a';
  writeFileSync(credPath, `# Usuário de demonstração (screenshots do redesign). Não versionar.\nDESIGN_DEMO_EMAIL=${EMAIL}\nDESIGN_DEMO_PASSWORD=${PASSWORD}\n`);
}
assert.ok(PASSWORD, 'senha ausente em .env.design-demo');

// ---------- http ----------
async function http(method, url, { headers = {}, body, form } = {}) {
  const res = await fetch(url, {
    method,
    headers: { ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}), ...headers },
    body: form ?? (body !== undefined ? JSON.stringify(body) : undefined),
  });
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  return { status: res.status, ok: res.ok, data };
}
function must(r, what) {
  if (!r.ok) throw new Error(`${what}: HTTP ${r.status} ${typeof r.data === 'string' ? r.data : JSON.stringify(r.data)}`);
  return r.data;
}

const svcHeaders = { apikey: SERVICE_KEY, Authorization: `Bearer ${SERVICE_KEY}` };
let USER_ID = null;
// PostgREST com service role: toda escrita exige o filtro do user_id deste usuário.
async function rest(method, path, body, prefer = 'return=representation') {
  if (method !== 'GET' && method !== 'POST') {
    assert.ok(USER_ID && path.includes(`user_id=eq.${USER_ID}`), `escrita sem filtro de user_id: ${path}`);
  }
  if (method === 'POST') {
    const rows = Array.isArray(body) ? body : [body];
    assert.ok(rows.every((r) => r.user_id === USER_ID), 'insert com user_id de outro usuário');
  }
  return must(await http(method, `${SUPABASE_URL}/rest/v1/${path}`, { headers: { ...svcHeaders, Prefer: prefer }, body }),
    `${method} ${path}`);
}

let TOKEN = null;
async function api(method, path, body, what) {
  return must(await http(method, `${API_BASE}/api/v1${path}`, { headers: { Authorization: `Bearer ${TOKEN}` }, body }),
    what || `${method} ${path}`);
}

async function login() {
  const r = await http('POST', `${SUPABASE_URL}/auth/v1/token?grant_type=password`, {
    headers: { apikey: SERVICE_KEY }, body: { email: EMAIL, password: PASSWORD },
  });
  if (!r.ok) return null;
  TOKEN = r.data.access_token;
  USER_ID = r.data.user.id;
  return USER_ID;
}

// ---------- dados determinísticos ----------
let seed = 20261120;
const rnd = () => ((seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648);
const between = (a, b) => Math.round((a + rnd() * (b - a)) * 100) / 100;
const pick = (arr) => arr[Math.floor(rnd() * arr.length)];
const pad = (n) => String(n).padStart(2, '0');
const MONTHS = [[2026, 7], [2026, 8], [2026, 9], [2026, 10], [2026, 11]];
const daysIn = (y, m) => new Date(y, m, 0).getDate();
function day(y, m, d) {
  const s = `${y}-${pad(m)}-${pad(Math.min(d, daysIn(y, m)))}`;
  return s >= START && s <= TODAY ? s : null;
}
const ym = (y, m) => `${y}-${pad(m)}`;

// Grupos e filhas (1 nível). Financeiro vem do onboarding; aqui só garante que exista.
const CATEGORIES = [
  ['Moradia', 'house', '#3B82F6', [['Aluguel'], ['Condomínio'], ['Energia', 'zap'], ['Internet e telefone', 'smartphone']]],
  ['Alimentação', 'utensils', '#F97316', [['Mercado', 'shopping-cart'], ['Restaurantes'], ['Delivery'], ['Padaria e café', 'coffee']]],
  ['Transporte', 'car', '#0EA5E9', [['Combustível', 'fuel'], ['App de transporte'], ['Estacionamento']]],
  ['Saúde', 'heart-pulse', '#EF4444', [['Farmácia'], ['Plano de saúde'], ['Academia', 'dumbbell']]],
  ['Lazer', 'party-popper', '#EC4899', [['Streaming', 'repeat'], ['Cinema e shows'], ['Viagens', 'plane']]],
  ['Educação', 'graduation-cap', '#8B5CF6', [['Cursos'], ['Livros']]],
  ['Compras', 'shopping-cart', '#F59E0B', [['Vestuário', 'shirt'], ['Eletrônicos', 'smartphone'], ['Presentes', 'gift']]],
  ['Renda', 'wallet', '#10B981', [['Salário'], ['Freelance'], ['Rendimentos', 'trending-up']]],
  ['Financeiro', 'landmark', '#059669', [['Investimentos', 'trending-up'], ['Reserva de Emergência', 'piggy-bank']]],
];

function buildTransactions() {
  const tx = []; // {date, description, amount, type, cat, acc|card, uncategorized?}
  const add = (date, description, amount, cat, where, type = 'DEBIT', extra = {}) => {
    if (date) tx.push({ date, description, amount, cat, type, ...where, ...extra });
  };
  const NU = { acc: 'nubank' }, ITAU = { acc: 'itau' }, POUP = { acc: 'poupanca' }, XP = { acc: 'xp' };
  const CNU = { card: 'roxinho' }, CITAU = { card: 'itaucard' };
  for (const [y, m] of MONTHS) {
    const nov = m === 11;
    // fixas
    add(day(y, m, 5), 'SALARIO ACME TECNOLOGIA LTDA', 8750, 'Salário', NU, 'CREDIT');
    add(day(y, m, 10), 'PIX ENVIADO IMOBILIARIA LAR FELIZ - ALUGUEL', 2300, 'Aluguel', NU);
    add(day(y, m, 10), 'BOLETO CONDOMINIO RESIDENCIAL VILLAGE', 680, 'Condomínio', ITAU);
    add(day(y, m, 12), 'ENEL DISTRIBUICAO SP', between(178, 262), 'Energia', ITAU);
    add(day(y, m, 15), 'VIVO FIBRA', 119.9, 'Internet e telefone', CITAU);
    add(day(y, m, 8), 'UNIMED SEGUROS SAUDE', 489, 'Plano de saúde', NU);
    add(day(y, m, 6), 'SMARTFIT', 129.9, 'Academia', CNU);
    add(day(y, m, 3), 'NETFLIX.COM', 55.9, 'Streaming', CNU);
    add(day(y, m, 7), 'SPOTIFY', 21.9, 'Streaming', CNU);
    add(day(y, m, 14), 'ALURA CURSOS ONLINE', 89.9, 'Cursos', CITAU);
    add(day(y, m, 28), 'RENDIMENTO POUPANCA', between(48, 62), 'Rendimentos', POUP, 'CREDIT');
    add(day(y, m, 28), 'RENDIMENTO CDB XP', between(150, 210), 'Rendimentos', XP, 'CREDIT');
    add(day(y, m, 18), `PIX RECEBIDO - ${pick(['JOAO P SILVA', 'STUDIO MOVA DESIGN', 'ANA L COSTA ME'])}`,
      between(1200, 2600), 'Freelance', ITAU, 'CREDIT');
    // variáveis (novembro mais carregado em Delivery e Mercado para estourar/encostar no orçamento)
    const n = (base) => (m === 7 ? Math.ceil(base / 3) : base);
    for (let i = 0; i < n(nov ? 6 : 5); i++)
      add(day(y, m, 1 + Math.floor(rnd() * 28)), pick(['SUPERMERCADO PAO DE ACUCAR', 'CARREFOUR HIPER', 'ASSAI ATACADISTA', 'HORTIFRUTI NATURAL DA TERRA']),
        between(85, nov ? 330 : 290), 'Mercado', rnd() < 0.5 ? CNU : NU);
    for (let i = 0; i < n(3); i++)
      add(day(y, m, 1 + Math.floor(rnd() * 28)), pick(['OUTBACK STEAKHOUSE', 'MADERO BURGER', 'COCO BAMBU', 'SUSHI GARDEN']), between(90, 260), 'Restaurantes', CITAU);
    for (let i = 0; i < n(nov ? 10 : 6); i++)
      add(day(y, m, 1 + Math.floor(rnd() * 28)), pick(['IFOOD *IFOOD', 'IFD*RAPPI BRASIL', 'IFOOD *IFOOD']), between(38, 98), 'Delivery', CNU);
    for (let i = 0; i < n(5); i++)
      add(day(y, m, 1 + Math.floor(rnd() * 28)), pick(['PADARIA BELLA PAULISTA', 'STARBUCKS PAULISTA', 'CAFE CULTURA']), between(12, 45), 'Padaria e café', NU);
    for (let i = 0; i < n(2); i++)
      add(day(y, m, 1 + Math.floor(rnd() * 28)), pick(['POSTO SHELL VILA MARIANA', 'AUTO POSTO IPIRANGA']), between(180, 260), 'Combustível', CITAU);
    for (let i = 0; i < n(6); i++)
      add(day(y, m, 1 + Math.floor(rnd() * 28)), pick(['UBER *TRIP', 'UBER *TRIP', '99 *POP']), between(14, 48), 'App de transporte', CNU);
    add(day(y, m, 9 + Math.floor(rnd() * 15)), 'ESTAPAR ESTACIONAMENTOS', between(22, 40), 'Estacionamento', CITAU);
    for (let i = 0; i < n(2); i++)
      add(day(y, m, 1 + Math.floor(rnd() * 28)), pick(['DROGA RAIA', 'DROGASIL']), between(28, 150), 'Farmácia', CNU);
    add(day(y, m, 16 + Math.floor(rnd() * 10)), pick(['CINEMARK SHOPPING', 'INGRESSE *SHOW']), between(60, 220), 'Cinema e shows', CITAU);
    if (m !== 7) add(day(y, m, 20 + Math.floor(rnd() * 8)), pick(['LOJAS RENNER', 'ZARA BRASIL', 'C&A MODAS']), between(120, 380), 'Vestuário', CNU);
    if (m === 9 || m === 11) add(day(y, m, 11), 'AMAZON MARKETPLACE LIVROS', between(45, 120), 'Livros', CNU);
  }
  // pontuais
  add('2026-10-02', 'LATAM AIRLINES BRASIL', 1480, 'Viagens', CITAU);
  add('2026-10-03', 'BOOKING.COM HOTEL', 960, 'Viagens', CITAU);
  add('2026-08-22', 'KABUM COMERCIO ELETRONICO', 649.9, 'Eletrônicos', CNU);
  add('2026-09-14', 'UDEMY CURSO', 34.9, 'Cursos', CNU);
  add('2026-10-12', 'PIX ENVIADO - PRESENTE DIA DAS CRIANCAS', 150, 'Presentes', NU);
  add('2026-11-01', 'ESTORNO IFOOD', 42.5, 'Delivery', CNU, 'CREDIT');
  // sem categoria (criadas com categoria e depois limpas)
  add('2026-08-19', 'PIX ENVIADO - MARIA S OLIVEIRA', 120, 'Presentes', NU, 'DEBIT', { uncategorized: true });
  add('2026-09-23', 'COMPRA DEBITO 4521 LOJA 24H', 37.8, 'Mercado', NU, 'DEBIT', { uncategorized: true });
  add('2026-10-21', 'PIX RECEBIDO - CARLOS ALMEIDA', 250, 'Freelance', ITAU, 'CREDIT', { uncategorized: true });
  add('2026-11-04', 'PAG*MERCADOPAGO SERVICOS', 89.9, 'Vestuário', CNU, 'DEBIT', { uncategorized: true });
  add('2026-11-13', 'PIX ENVIADO - RAFAEL T', 60, 'Presentes', NU, 'DEBIT', { uncategorized: true });
  add('2026-11-17', 'TARIFA PACOTE SERVICOS', 34.5, 'Mercado', ITAU, 'DEBIT', { uncategorized: true });
  return tx.sort((a, b) => a.date.localeCompare(b.date));
}

// Extrato sintético no layout CSV do Nubank (conta), importado pela API normal.
function importCsv() {
  const rows = [
    ['03/11/2026', '-23.40', 'Compra no débito - PADARIA DONA DEOLA'],
    ['04/11/2026', '-18.90', 'Compra no débito - UBER *TRIP'],
    ['06/11/2026', '-212.37', 'Compra no débito - SUPERMERCADO PAO DE ACUCAR'],
    ['07/11/2026', '-31.50', 'Compra no débito - STARBUCKS PAULISTA'],
    ['09/11/2026', '-27.90', 'Compra no débito - DROGA RAIA'],
    ['11/11/2026', '-64.80', 'Compra no débito - IFOOD *IFOOD'],
    ['12/11/2026', '-75.00', 'Transferência enviada pelo Pix - JULIANA M PEREIRA'],
    ['14/11/2026', '-148.22', 'Compra no débito - CARREFOUR HIPER'],
    ['16/11/2026', '-16.75', 'Compra no débito - 99 *POP'],
    ['17/11/2026', '-52.30', 'Compra no débito - OUTBACK STEAKHOUSE'],
  ];
  return 'Data,Valor,Identificador,Descrição\n' + rows.map(([d, v, desc]) => `${d},${v},${randomUUID()},${desc}`).join('\n') + '\n';
}
const IMPORT_CATEGORY = [
  [/PADARIA|STARBUCKS/, 'Padaria e café'], [/UBER|99 \*POP/, 'App de transporte'], [/SUPERMERCADO|CARREFOUR/, 'Mercado'],
  [/DROGA/, 'Farmácia'], [/IFOOD/, 'Delivery'], [/OUTBACK/, 'Restaurantes'], // o Pix fica sem categoria de propósito
];

const BUDGET = {
  Aluguel: 2300, 'Condomínio': 680, Energia: 240, 'Internet e telefone': 120, Mercado: 1650, Restaurantes: 450,
  Delivery: 480, 'Padaria e café': 160, 'Combustível': 450, 'App de transporte': 220, Estacionamento: 40,
  'Farmácia': 200, 'Plano de saúde': 489, Academia: 130, Streaming: 80, 'Cinema e shows': 150, Cursos: 100, 'Vestuário': 300,
};

const GOALS = [
  { name: 'Reserva de emergência', targetAmount: 30000, targetDate: '2027-12-31', monthlyAllocation: 1000, template: 'EMERGENCY_FUND',
    description: 'Seis meses de custo de vida guardados.', contributions: [['2026-08', 1500], ['2026-09', 1000], ['2026-10', 1000], ['2026-11', 1200]] },
  { name: 'Viagem para o Chile', targetAmount: 12000, targetDate: '2027-07-15', monthlyAllocation: 800, template: 'VACATION',
    description: 'Santiago e Atacama nas férias de julho.', contributions: [['2026-08', 800], ['2026-09', 800], ['2026-10', 600], ['2026-11', 800]] },
  { name: 'Troca do carro', targetAmount: 45000, targetDate: '2028-06-30', monthlyAllocation: 1200, template: 'BIG_PURCHASE',
    description: 'Entrada para um carro novo.', contributions: [['2026-08', 2000], ['2026-09', 1200], ['2026-10', 1200]] },
  { name: 'Notebook novo', targetAmount: 7500, targetDate: '2026-12-20', monthlyAllocation: 1500, template: 'BIG_PURCHASE',
    description: 'MacBook para trabalho e freelas.', contributions: [['2026-08', 1500], ['2026-09', 1500], ['2026-10', 1500], ['2026-11', 1500]] },
];

// ---------- passos ----------
async function ensureUser() {
  if (await login()) { console.log(`Usuário existente ${USER_ID}`); return false; }
  const r = await http('POST', `${SUPABASE_URL}/auth/v1/admin/users`, {
    headers: svcHeaders, body: { email: EMAIL, password: PASSWORD, email_confirm: true, user_metadata: { full_name: 'Marina Duarte' } },
  });
  must(r, 'criar usuário no Supabase');
  assert.ok(await login(), 'login falhou logo após criar o usuário');
  console.log(`Usuário criado ${USER_ID}`);
  return true;
}

async function wipeUserData() {
  const f = `user_id=eq.${USER_ID}`;
  for (const t of ['budget_reallocations', 'budget_allocations', 'user_classification_patterns', 'ai_classification_history',
    'transactions', 'transaction_imports', 'transfers', 'reconciliation_history', 'account_balance_history', 'accounts',
    'credit_cards', 'goal_progress_history', 'goal_allocations', 'goals']) {
    await rest('DELETE', `${t}?${f}`, undefined, 'return=minimal');
  }
  await rest('DELETE', `categories?${f}&parent_category_id=not.is.null`, undefined, 'return=minimal');
  await rest('DELETE', `categories?${f}`, undefined, 'return=minimal');
  console.log('Dados antigos do usuário de demo apagados');
}

async function backdateAccount(id) {
  await rest('PATCH', `accounts?id=eq.${id}&user_id=eq.${USER_ID}`, { created_at: REFERENCE }, 'return=minimal');
  await rest('PATCH', `account_balance_history?account_id=eq.${id}&user_id=eq.${USER_ID}&created_by_event=eq.initial`,
    { snapshot_date: REFERENCE, created_at: REFERENCE }, 'return=minimal');
}

async function main() {
  console.log(`API: ${API_BASE}`);
  const created = await ensureUser();
  must(await http('POST', `${API_BASE}/api/v1/auth/register/email`, { headers: { Authorization: `Bearer ${TOKEN}` } }), 'register/email');
  await http('POST', `${API_BASE}/api/v1/auth/login`, { headers: { Authorization: `Bearer ${TOKEN}` } });
  const sub = await api('GET', '/subscriptions/me');
  console.log(`Assinatura: ${JSON.stringify(sub)}`);
  if (!created) await wipeUserData();

  let onb = await api('GET', '/onboarding/me');
  if (!onb.completed && onb.currentStep <= 1) await api('POST', '/onboarding/me/tutorial', { skipped: true });

  // contas
  const ACCS = {
    nubank: { accountName: 'Nubank', bankName: 'Nubank', accountType: 'CHECKING', initialBalance: 2350 },
    itau: { accountName: 'Itaú Personnalité', bankName: 'Itaú', accountType: 'CHECKING', initialBalance: 1180 },
    poupanca: { accountName: 'Poupança', bankName: 'Caixa', accountType: 'SAVINGS', initialBalance: 8400 },
    xp: { accountName: 'Investimentos XP', bankName: 'XP Investimentos', accountType: 'SAVINGS', initialBalance: 15200 },
  };
  const acc = {};
  for (const [k, body] of Object.entries(ACCS)) {
    acc[k] = (await api('POST', '/accounts', body)).id;
    await backdateAccount(acc[k]);
  }
  onb = await api('GET', '/onboarding/me');
  if (!onb.completed && onb.currentStep === 2) await api('POST', '/onboarding/me/accounts', { skipped: false });

  const card = {};
  card.roxinho = (await api('POST', '/credit-cards', { cardName: 'Nubank Roxinho', bankName: 'Nubank', cardLimit: 8000, dueDay: 10 })).id;
  card.itaucard = (await api('POST', '/credit-cards', { cardName: 'Itaú Click Platinum', bankName: 'Itaú', cardLimit: 5500, dueDay: 25 })).id;
  for (const id of Object.values(card)) await rest('PATCH', `credit_cards?id=eq.${id}&user_id=eq.${USER_ID}`, { created_at: REFERENCE }, 'return=minimal');

  onb = await api('GET', '/onboarding/me');
  if (!onb.completed && onb.currentStep === 3) await api('POST', '/onboarding/me/cards', { skipped: false });
  onb = await api('GET', '/onboarding/me');
  if (!onb.completed && onb.currentStep === 4) await api('POST', '/onboarding/me/categories', { keys: ['investments', 'emergency_fund'] });

  // categorias: reaproveita as que já existem pelo nome
  const cat = {};
  const existing = await api('GET', '/categories');
  const byName = (name, parent) => existing.find((c) => c.categoryName === name && (parent === undefined || (c.parentCategoryId === null) === (parent === null)));
  for (const [group, icon, color, children] of CATEGORIES) {
    const g = byName(group, null) || await api('POST', '/categories', { name: group, iconName: icon, colorHex: color, parentId: null });
    for (const [child, childIcon] of children) {
      const c = existing.find((x) => x.categoryName === child)
        || await api('POST', '/categories', { name: child, iconName: childIcon || icon, colorHex: color, parentId: g.id });
      cat[child] = c.id;
    }
  }

  onb = await api('GET', '/onboarding/me');
  if (!onb.completed && onb.currentStep === 5) await api('POST', '/onboarding/me/customization', { skipped: true });
  onb = await api('GET', '/onboarding/me');
  if (!onb.completed && onb.currentStep === 6) await api('POST', '/onboarding/me/import', {});
  onb = await api('GET', '/onboarding/me');
  if (!onb.completed && onb.currentStep === 7) await api('POST', '/onboarding/me/dashboard-tour', {});
  onb = await api('GET', '/onboarding/me');
  if (!onb.completed) onb = await api('POST', '/onboarding/me/finish', { skipped: true });
  assert.ok(onb.completed, `onboarding não concluiu: ${JSON.stringify(onb)}`);

  // transações
  const txs = buildTransactions();
  let uncategorized = 0;
  for (const t of txs) {
    assert.ok(cat[t.cat], `categoria desconhecida ${t.cat}`);
    const body = { date: t.date, description: t.description, amount: t.amount, type: t.type, categoryId: cat[t.cat],
      accountId: t.acc ? acc[t.acc] : null, creditCardId: t.card ? card[t.card] : null };
    const row = await api('POST', '/transactions', body, `transação ${t.date} ${t.description}`);
    if (t.uncategorized) {
      await api('PUT', `/transactions/${row.id}`, { ...body, categoryId: null });
      uncategorized++;
    }
  }
  console.log(`${txs.length} transações manuais (${uncategorized} sem categoria)`);

  // transferências (antes da importação: valores distintos dos do extrato)
  const transfers = [];
  for (const [y, m] of MONTHS.slice(1)) {
    transfers.push([acc.nubank, acc.xp, 1500, `${ym(y, m)}-06`, 'Aplicação CDB']);
    transfers.push([acc.nubank, acc.poupanca, 400, `${ym(y, m)}-06`, 'Guardar na poupança']);
  }
  transfers.push([acc.itau, acc.nubank, 900, '2026-09-20', 'Acerto entre contas']);
  for (const [from, to, amount, date, description] of transfers) {
    await api('POST', '/transfers', { id: randomUUID(), fromAccountId: from, toAccountId: to, amount, transferDate: date, description });
  }
  console.log(`${transfers.length} transferências`);

  // importação de extrato (CSV sintético no layout do Nubank)
  try {
    const form = new FormData();
    form.append('file', new Blob([importCsv()], { type: 'text/csv' }), 'NU_extrato_novembro_2026.csv');
    form.append('accountId', acc.nubank);
    const imp = must(await http('POST', `${API_BASE}/api/v1/imports`, { headers: { Authorization: `Bearer ${TOKEN}` }, form }), 'importação');
    let status = imp.status;
    for (let i = 0; i < 60 && status === 'PROCESSING'; i++) {
      await new Promise((r) => setTimeout(r, 2000));
      status = (await api('GET', `/imports/${imp.id}`)).status;
    }
    assert.equal(status, 'REVIEW', `importação terminou em ${status}`);
    const rows = await api('GET', `/imports/${imp.id}/transactions`);
    const groups = {};
    for (const r of rows) {
      const hit = IMPORT_CATEGORY.find(([re]) => re.test(r.description));
      if (hit) (groups[hit[1]] ||= []).push(r.id);
    }
    for (const [name, ids] of Object.entries(groups)) {
      await api('PATCH', `/imports/${imp.id}/transactions/category`, { transactionIds: ids, categoryId: cat[name] });
    }
    const done = await api('POST', `/imports/${imp.id}/complete`);
    console.log(`Importação concluída: ${JSON.stringify(done)}`);
  } catch (e) {
    console.warn(`Importação não concluída: ${e.message}`);
  }

  // orçamentos (mês corrente e anteriores)
  for (const [y, m] of MONTHS) {
    const month = ym(y, m);
    const current = await api('GET', `/budgets/${month}`);
    const allocations = Object.entries(BUDGET).map(([name, amount]) => ({ categoryId: cat[name], amount }));
    await api('PUT', `/budgets/${month}`, { version: current.version, allocations });
  }
  console.log('Orçamentos de jul a nov salvos');

  // metas com histórico retroativo
  const today = TODAY;
  for (const g of GOALS) {
    const goal = await api('POST', '/goals', { name: g.name, targetAmount: g.targetAmount, targetDate: g.targetDate,
      monthlyAllocation: g.monthlyAllocation, description: g.description, template: g.template });
    const gf = `goal_id=eq.${goal.id}&user_id=eq.${USER_ID}`;
    await rest('PATCH', `goals?id=eq.${goal.id}&user_id=eq.${USER_ID}`, { created_at: '2026-07-25T10:00:00-03:00' }, 'return=minimal');
    await rest('PATCH', `goal_progress_history?${gf}&contribution_amount=is.null`, { snapshot_date: '2026-07-25', created_at: '2026-07-25T10:00:00-03:00' }, 'return=minimal');
    let total = 0;
    for (const [month, amount] of g.contributions) {
      await api('POST', `/goals/${goal.id}/contributions`, { amount });
      total += amount;
      const when = `${month}-07`;
      await rest('PATCH', `goal_progress_history?${gf}&snapshot_date=eq.${today}&contribution_amount=not.is.null`,
        { snapshot_date: when, created_at: `${when}T09:00:00-03:00` }, 'return=minimal');
      if (month !== '2026-11') {
        await rest('PATCH', `goal_allocations?${gf}&month_year=eq.2026-11-01`, { month_year: `${month}-01`, created_at: `${when}T09:00:00-03:00` }, 'return=minimal');
      }
      // snapshot mensal do dia 1º seguinte, como o job faria
      const [yy, mm] = month.split('-').map(Number);
      const next = mm === 12 ? `${yy + 1}-01-01` : `${yy}-${pad(mm + 1)}-01`;
      if (next <= today) {
        await rest('POST', 'goal_progress_history', { goal_id: goal.id, user_id: USER_ID, amount_snapshot: total, snapshot_date: next,
          contribution_amount: null, created_at: `${next}T00:05:00-03:00` }, 'return=minimal');
      }
    }
  }
  console.log(`${GOALS.length} metas com aportes`);

  // histórico de saldo: recria um ponto por dia com movimento, a partir do saldo atual
  for (const [k, id] of Object.entries(acc)) {
    const [a] = await rest('GET', `accounts?select=current_balance,initial_balance&id=eq.${id}&user_id=eq.${USER_ID}`);
    const rows = await rest('GET', `transactions?select=transaction_date,amount,transaction_type&account_id=eq.${id}&user_id=eq.${USER_ID}&affects_balance=is.true&deleted_at=is.null`);
    const trs = await rest('GET', `transfers?select=from_account_id,to_account_id,amount,transfer_date&user_id=eq.${USER_ID}&deleted_at=is.null&or=(from_account_id.eq.${id},to_account_id.eq.${id})`);
    const delta = {};
    for (const r of rows) delta[r.transaction_date] = (delta[r.transaction_date] || 0) + (r.transaction_type === 'CREDIT' ? 1 : -1) * Number(r.amount);
    for (const t of trs) delta[t.transfer_date] = (delta[t.transfer_date] || 0) + (t.to_account_id === id ? 1 : -1) * Number(t.amount);
    const sum = Object.values(delta).reduce((s, v) => s + v, 0);
    const expected = Math.round((Number(a.initial_balance) + sum) * 100) / 100;
    if (Math.abs(expected - Number(a.current_balance)) > 0.009) console.warn(`Conta ${k}: inicial+movimento=${expected} ≠ atual=${a.current_balance}`);
    await rest('DELETE', `account_balance_history?account_id=eq.${id}&user_id=eq.${USER_ID}&created_by_event=in.(transaction,transfer)`, undefined, 'return=minimal');
    let balance = Number(a.initial_balance);
    const points = Object.keys(delta).sort().map((date) => {
      balance = Math.round((balance + delta[date]) * 100) / 100;
      const at = `${date}T20:00:00-03:00`;
      return { account_id: id, user_id: USER_ID, balance_snapshot: balance, snapshot_date: at, created_by_event: 'transaction', created_at: at };
    });
    if (points.length) await rest('POST', 'account_balance_history', points, 'return=minimal');
    console.log(`Conta ${ACCS[k].accountName}: saldo ${a.current_balance}, ${points.length} pontos de histórico`);
  }

  await verify();
}

async function verify() {
  assert.ok(await login(), 'login final falhou');
  const checks = {
    'dashboard layer1': await api('GET', '/dashboard/layer1'),
    'dashboard layer2': await api('GET', '/dashboard/layer2'),
    'dashboard health': await api('GET', '/dashboard/health'),
    'transações nov': await api('GET', '/transactions?month=2026-11'),
    'transações out': await api('GET', '/transactions?month=2026-10'),
    'orçamento nov': await api('GET', '/budgets/2026-11'),
    metas: await api('GET', '/goals'),
    contas: await api('GET', '/accounts'),
    cartões: await api('GET', '/credit-cards'),
    transferências: await api('GET', '/transfers'),
    importações: await api('GET', '/imports'),
    categorias: await api('GET', '/categories'),
  };
  const size = (v) => (Array.isArray(v) ? `${v.length} itens` : `${Object.keys(v || {}).length} campos`);
  for (const [k, v] of Object.entries(checks)) console.log(`  ${k}: ${size(v)}`);
  const budget = checks['orçamento nov'];
  const over = budget.categories.filter((c) => Number(c.spent) > Number(c.allocated));
  console.log(`  orçamento nov: alocado ${budget.totalAllocated}, ${over.length} categoria(s) acima do limite`);
  const accHist = await api('GET', `/accounts/${checks.contas[0].id}/balance-history?range=6m`);
  console.log(`  histórico de saldo (${checks.contas[0].accountName}): ${JSON.stringify(accHist).length} bytes`);
}

main().catch((e) => { console.error(e); process.exit(1); });
