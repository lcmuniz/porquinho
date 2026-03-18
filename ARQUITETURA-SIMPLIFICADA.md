# 🏗️ Arquitetura Híbrida - Supabase Auth + Backend Sync

**Data da Mudança:** 2026-03-17
**Decisão:** Usar Supabase para autenticação + Backend para features de segurança/negócio

---

## 🎯 Arquitetura Híbrida (DECISÃO FINAL)

### ✅ Arquitetura Atual

```
Frontend
   ↓
Supabase Auth (auth.users)  ←── Autoridade para autenticação
   ↓                             - JWT tokens
   ↓                             - Password hashing (bcrypt)
   ↓                             - Email verification
   ↓                             - OAuth providers
   ↓
Backend Database (users)     ←── Features de segurança/negócio
                                 - Account locking (failed attempts)
                                 - Audit logs (login history)
                                 - Rate limiting per-user
                                 - Business data
```

**Responsabilidades:**

| Responsabilidade | Supabase Auth | Backend DB |
|-----------------|---------------|------------|
| **Autenticação** | ✅ Autoridade | ❌ Não |
| **JWT Tokens** | ✅ Emite e valida | ❌ Apenas valida |
| **Password Hash** | ✅ bcrypt | ❌ Nunca vê senha |
| **Email Verification** | ✅ Automático | ❌ Não |
| **OAuth (Google)** | ✅ Nativo | ❌ Não |
| **Account Locking** | ❌ Não | ✅ failed_login_attempts |
| **Audit Logs** | ⚠️ Básico | ✅ Customizado |
| **Rate Limiting** | ❌ Não | ✅ Por usuário |
| **Business Data** | ⚠️ user_metadata | ✅ Tabelas dedicadas |

**Benefícios:**
- ✅ Autenticação robusta (Supabase)
- ✅ Segurança avançada (backend)
- ✅ Audit logs detalhados
- ✅ Sincronização leve (lazy)

---

## 📊 Onde Ficam os Dados

### Supabase `auth.users`

Armazena TUDO sobre o usuário:

```sql
SELECT
  id,                    -- UUID único
  email,                 -- Email do usuário
  encrypted_password,    -- Senha (bcrypt automático)
  email_confirmed_at,    -- Timestamp de verificação
  raw_user_meta_data,    -- JSON com dados customizados
  created_at,
  updated_at
FROM auth.users;
```

### Supabase `user_metadata` (JSON)

Dados específicos da aplicação:

```json
{
  "name": "Nome do Usuário",
  "lgpd_consent_at": "2026-03-16T12:00:00Z"
}
```

**Como acessar no código:**
```typescript
const { data: { user } } = await supabase.auth.getUser()
console.log(user.user_metadata.name)
console.log(user.user_metadata.lgpd_consent_at)
```

---

## 🔄 Sincronização de Usuários

### Quando Ocorre

1. **No Registro (RegisterView.vue):**
   ```typescript
   // Após Supabase criar usuário
   await supabase.auth.signUp(...)

   // Sincroniza para backend (com JWT no header)
   await authService.registerWithEmail({ email, name })
   ```

2. **No Primeiro Login (LoginView.vue):**
   ```typescript
   // Se usuário não existe no backend (404)
   if (backendError.response?.status === 404) {
     // Lazy sync: cria usuário no backend
     await authService.registerWithEmail({ email, name })
   }
   ```

### Por Que Sincronizar?

✅ **Account Locking** - 5 tentativas falhas → 15 min bloqueio
✅ **Audit Logs** - Rastreio completo de logins/ações
✅ **Rate Limiting** - Proteção por usuário (não só IP)
✅ **Business Data** - Subscriptions, billing, preferences

### Tabelas Backend

- ✅ `users` - **USADA** para account locking e referências
- ✅ `audit_logs` - **USADA** para logs de segurança
- ✅ Sincronização **leve** (apenas email + name)
- ✅ Falha na sincronização **não bloqueia** usuário

---

## 🔐 Segurança e Conformidade

### LGPD (NFR25)
✅ **Antes:** Armazenado em `users.lgpd_consent_at` no backend
✅ **Depois:** Armazenado em `user_metadata.lgpd_consent_at` no Supabase

**Código:**
```typescript
await supabase.auth.signUp({
  email,
  password,
  options: {
    data: {
      name: 'Nome',
      lgpd_consent_at: new Date().toISOString() // NFR25
    }
  }
})
```

### Password Hashing (NFR13)
✅ **Antes:** Supabase hash + backend valida
✅ **Depois:** Supabase hash (bcrypt automático)

**Nada muda aqui - Supabase sempre gerenciou as senhas!**

### Audit Logs (NFR20)
⚠️ **Antes:** Backend criava audit logs
✅ **Depois:** Usar Supabase Audit Logs nativos

**Como acessar:**
1. Supabase Dashboard → Authentication → Logs
2. Ou via Postgres: `SELECT * FROM auth.audit_log_entries;`

---

## 📝 Fluxo de Registro Atual

### 1. Usuário Preenche Formulário
```
Nome: João Silva
Email: joao@example.com
Senha: SenhaForte123
```

### 2. Frontend Chama Supabase
```typescript
const { data, error } = await supabase.auth.signUp({
  email: 'joao@example.com',
  password: 'SenhaForte123',
  options: {
    emailRedirectTo: 'http://localhost:5173/auth/verify-email',
    data: {
      name: 'João Silva',
      lgpd_consent_at: '2026-03-17T12:00:00Z'
    }
  }
})
```

### 3. Supabase Cria Usuário
- ✅ Hash da senha com bcrypt
- ✅ Envia email de verificação
- ✅ Retorna JWT token
- ✅ Armazena user_metadata

### 4. Frontend Sincroniza Backend
```typescript
// Com JWT no Authorization header
await authService.registerWithEmail({
  email: 'joao@example.com',
  name: 'João Silva'
})
```

### 5. Backend Cria Registro
```sql
INSERT INTO users (id, email, name, auth_provider, created_at)
VALUES (
  'supabase-uuid',  -- Mesmo UUID do Supabase
  'joao@example.com',
  'João Silva',
  'EMAIL',
  NOW()
)
```

### 6. Frontend Redireciona
```typescript
router.push('/auth/verify-email')
```

### 7. Usuário Verifica Email
- Clica no link recebido por email
- Supabase marca `email_confirmed_at`
- Redirect para aplicação

---

## 🚀 Próximos Passos (Futuro)

### Adicionar Dados de Negócio

**Opção A:** Supabase `public.profiles` (dados simples, acesso direto)
```sql
-- No Supabase SQL Editor
CREATE TABLE public.profiles (
  id UUID REFERENCES auth.users PRIMARY KEY,
  subscription_tier TEXT DEFAULT 'free',
  trial_ends_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT NOW()
);
```

**Opção B:** Backend tables (lógica complexa, segurança)
```sql
-- No backend Spring Boot + Flyway
CREATE TABLE subscriptions (
  id UUID PRIMARY KEY,
  user_id UUID REFERENCES users(id),
  plan TEXT NOT NULL,
  status TEXT NOT NULL,
  billing_cycle TEXT,
  next_billing_date DATE,
  created_at TIMESTAMP DEFAULT NOW()
);
```

**Decisão:** Depende da complexidade
- Preferências UI → Supabase profiles
- Billing/payments → Backend tables

---

## 📊 Comparação Final

| Item | Supabase-Only | Híbrido (ATUAL) |
|------|---------------|-----------------|
| **Tabelas de usuário** | 1 (auth.users) | 2 (auth.users + backend users) |
| **Autoridade de auth** | ✅ Supabase | ✅ Supabase |
| **Account locking** | ❌ Não | ✅ Sim (backend) |
| **Audit logs detalhados** | ⚠️ Básico | ✅ Completo (backend) |
| **Rate limiting per-user** | ❌ Não | ✅ Sim (backend) |
| **Sincronização** | Não | Sim (leve, lazy) |
| **Complexidade** | Baixa | Média |
| **Segurança** | Boa | ✅ Excelente |
| **LGPD compliance** | ✅ Sim | ✅ Sim |
| **Tempo de registro** | ~200ms | ~300ms |

---

## ✅ Conclusão

A arquitetura híbrida oferece o melhor dos dois mundos:

**Do Supabase:**
- ✅ Autenticação robusta e confiável
- ✅ JWT token management
- ✅ Password hashing automático (bcrypt)
- ✅ Email verification nativo
- ✅ OAuth providers (Google, etc)

**Do Backend:**
- ✅ Account locking (brute force protection)
- ✅ Audit logs customizados
- ✅ Rate limiting avançado
- ✅ Business logic complexa
- ✅ Integrações externas

**Trade-offs Aceitos:**
- ⚠️ Sincronização leve (não-bloqueante)
- ⚠️ ~100ms extra no registro (aceitável)
- ⚠️ Dois sistemas para manter (mas separação clara)

**Resultado:** Segurança máxima + Flexibilidade + Compliance! 🎉
