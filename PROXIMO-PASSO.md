# ⚡ Próximos Passos - Configure sua Senha do Supabase

## ✅ Arquivos .env Criados!

```
✅ porquinho/.env              (criado agora)
✅ porquinho-frontend/.env     (já existia)
```

---

## 🔐 Falta Apenas 1 Coisa: Senha do Banco de Dados

### Edite o arquivo `.env` na raiz:

```bash
notepad C:\Users\lcmuniz\Projects\porquinho\.env
```

Encontre a linha:
```env
SUPABASE_DB_PASSWORD=PREENCHA_AQUI_SUA_SENHA_DO_SUPABASE
```

Substitua por **sua senha real do Supabase**.

---

## 🔍 Como Encontrar sua Senha do Supabase?

### Opção 1: Você lembra a senha?
Use a senha que você criou quando configurou o projeto Supabase.

### Opção 2: Resetar senha
1. Acesse https://supabase.com/dashboard/project/vhkjyefwpwtlcyuznqgq/settings/database
2. Vá em **Settings → Database**
3. Clique em **Reset database password**
4. Copie a nova senha
5. Cole no arquivo `.env`

### Opção 3: Connection Pooler (alternativa)
Se não conseguir a senha do postgres direto, use o Connection Pooler:

1. Vá em https://supabase.com/dashboard/project/vhkjyefwpwtlcyuznqgq/settings/database
2. Em **Connection Pooling**, copie a connection string no modo **Transaction**
3. Formato: `postgresql://postgres.xxx:[SENHA]@xxx.pooler.supabase.com:6543/postgres`
4. Extraia a senha e atualize o `.env`

---

## ⚠️ Verificar Anon Key (Frontend)

Notei que sua `SUPABASE_ANON_KEY` termina com `.fakekey`.

Se for uma chave de teste, atualize para a chave real:

1. Acesse https://supabase.com/dashboard/project/vhkjyefwpwtlcyuznqgq/settings/api
2. Copie **anon public** key
3. Atualize em:
   - `porquinho-frontend/.env` → `VITE_SUPABASE_ANON_KEY`
   - `porquinho/.env` → `SUPABASE_ANON_KEY`

---

## 🚀 Depois de Configurar a Senha

Execute:

```bash
cd C:\Users\lcmuniz\Projects\porquinho
START-DEV.bat
```

Isso vai:
- ✅ Iniciar Redis automaticamente
- ✅ Abrir Backend (porta 8080)
- ✅ Abrir Frontend (porta 5173)

---

## ✅ Teste Rápido

Após iniciar, teste:

1. **Backend Health Check:**
   ```
   http://localhost:8080/api/v1/auth/health
   ```
   Deve retornar: "Auth service is running"

2. **Frontend:**
   ```
   http://localhost:5173/register
   ```
   Tente criar uma conta com email/password

---

## 🆘 Se der erro de conexão ao Supabase

Se o backend não conectar ao banco:

```
Error: Connection refused / Authentication failed
```

**Causa:** Senha incorreta no `.env`

**Solução:**
1. Verifique se a senha está correta
2. Tente resetar a senha no Supabase Dashboard
3. Verifique se o formato está correto (sem espaços, sem aspas)

---

## 📝 Resumo

1. ✅ Edite `porquinho/.env` e preencha `SUPABASE_DB_PASSWORD`
2. ✅ (Opcional) Verifique se `SUPABASE_ANON_KEY` está correto
3. ✅ Execute `START-DEV.bat`
4. ✅ Acesse http://localhost:5173

**Pronto para desenvolver!** 🚀
