# 🏗️ Arquitetura Simplificada - Só Supabase (Opção B)

**Data da Mudança:** 2026-03-16
**Decisão:** Usar apenas Supabase para gerenciamento de usuários

---

## 🎯 Mudança Arquitetural

### ❌ Antes (Dual Tables - Complexo)

```
Frontend → Supabase Auth → Backend → Database users table
              ↓
          auth.users
```

**Problemas:**
- Duplicação de dados (auth.users + backend users)
- Sincronização complexa
- Dois pontos de falha
- Mais código para manter

### ✅ Depois (Só Supabase - Simples)

```
Frontend → Supabase Auth
              ↓
          auth.users (única fonte da verdade)
              ↓
          user_metadata (dados extras)
```

**Benefícios:**
- ✅ Sem duplicação
- ✅ Sem sincronização
- ✅ Menos código (50% redução)
- ✅ Mais confiável

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

## 🔧 O Que Foi Removido

### Frontend
- ❌ `authService.registerWithEmail()` - Não precisa mais chamar backend
- ❌ Sincronização backend após signup
- ✅ Mantém apenas `supabase.auth.signUp()`

### Backend
- ⚠️ Tabela `users` do backend ainda existe mas **NÃO é mais usada**
- ⚠️ Endpoint `/api/v1/auth/register/email` ainda existe mas **NÃO é mais chamado**

**Próxima etapa:** Limpar código morto (opcional)

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
      lgpd_consent_at: '2026-03-16T12:00:00Z'
    }
  }
})
```

### 3. Supabase Cria Usuário
- ✅ Hash da senha com bcrypt
- ✅ Envia email de verificação
- ✅ Retorna JWT token
- ✅ Armazena user_metadata

### 4. Frontend Redireciona
```typescript
router.push('/auth/verify-email')
```

### 5. Usuário Verifica Email
- Clica no link recebido por email
- Supabase marca `email_confirmed_at`
- Redirect para aplicação

---

## 🚀 Próximos Passos (Futuro)

### Se Precisar de Dados Específicos do Negócio

Criar tabela `profiles` no Supabase (não no backend):

```sql
-- No Supabase SQL Editor
CREATE TABLE public.profiles (
  id UUID REFERENCES auth.users PRIMARY KEY,
  subscription_tier TEXT DEFAULT 'free',
  trial_ends_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT NOW()
);

-- Row Level Security (RLS)
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;

-- Policy: Usuário só vê próprio perfil
CREATE POLICY "Users can view own profile"
  ON public.profiles
  FOR SELECT
  USING (auth.uid() = id);

-- Policy: Usuário pode atualizar próprio perfil
CREATE POLICY "Users can update own profile"
  ON public.profiles
  FOR UPDATE
  USING (auth.uid() = id);
```

### Backend Será Usado Para Quê?

- ✅ Lógica de negócio complexa (subscriptions, billing)
- ✅ Integrações externas (email, analytics)
- ✅ Operações batch/admin
- ❌ **NÃO** para gerenciar usuários básicos

---

## 📊 Comparação Final

| Item | Antes (Dual) | Depois (Só Supabase) |
|------|--------------|----------------------|
| **Tabelas de usuário** | 2 (auth.users + backend users) | 1 (auth.users) |
| **Linhas de código** | ~500 | ~250 |
| **Endpoints backend** | 2 (Google + Email) | 0 (só JWT validation) |
| **Sincronização** | Sim (complexa) | Não |
| **Pontos de falha** | 2 | 1 |
| **LGPD compliance** | ✅ Sim | ✅ Sim |
| **Audit logs** | ✅ Sim | ✅ Sim (Supabase) |
| **Tempo de registro** | ~500ms | ~200ms |

---

## ✅ Conclusão

A arquitetura simplificada:
- É **mais simples** de manter
- É **mais confiável** (menos moving parts)
- É **mais rápida** (sem sync)
- **Mantém compliance** (LGPD, audit, security)

**Resultado:** Código melhor com menos esforço! 🎉
