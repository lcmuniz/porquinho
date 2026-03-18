# 🔍 Checklist - Verificar Projeto Supabase

## 1️⃣ Verificar Status do Projeto

1. Acesse: https://supabase.com/dashboard/project/vhkjyefwpwtlcyuznqgq
2. Verifique se o projeto está **ATIVO** ou **PAUSADO**

**Se estiver pausado:**
- Clique em "Restore project" ou "Resume"
- Aguarde alguns minutos para o projeto reiniciar
- Tente conectar novamente

---

## 2️⃣ Verificar Connection String Correta

### Opção A: Direct Connection (atual - com problema)

Seu `.env` atual:
```
SUPABASE_DB_URL=jdbc:postgresql://db.vhkjyefwpwtlcyuznqgq.supabase.co:5432/postgres
```

**Este formato pode não funcionar se:**
- Projeto está pausado
- IPv6 está causando problemas
- Firewall bloqueando porta 5432

### Opção B: Connection Pooling (RECOMENDADO)

1. Acesse: https://supabase.com/dashboard/project/vhkjyefwpwtlcyuznqgq/settings/database
2. Role até **Connection Pooling**
3. Selecione modo **Transaction**
4. Copie a connection string no formato: `postgresql://postgres.xxx:[senha]@xxx.pooler.supabase.com:6543/postgres`

**Converta para formato JDBC:**
```
postgresql://postgres.xxx:[senha]@aws-0-us-east-1.pooler.supabase.com:6543/postgres
↓
jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:6543/postgres?user=postgres.xxx&password=[senha]
```

**Exemplo:**
```env
# Connection Pooling (modo Transaction)
SUPABASE_DB_URL=jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:6543/postgres?user=postgres.vhkjyefwpwtlcyuznqgq&password=facial-slurp-jockey
```

---

## 3️⃣ Teste de Conexão Manual

### Teste 1: Ping do hostname

```bash
# Git Bash
ping db.vhkjyefwpwtlcyuznqgq.supabase.co

# Se retornar "Unknown host" → projeto pausado ou hostname incorreto
# Se retornar IPs → conexão de rede OK
```

### Teste 2: Tentar connection pooler

```bash
ping aws-0-us-east-1.pooler.supabase.com
```

### Teste 3: Conectar com psql (se tiver instalado)

```bash
# Direct connection
psql "postgresql://postgres:facial-slurp-jockey@db.vhkjyefwpwtlcyuznqgq.supabase.co:5432/postgres"

# Connection pooling
psql "postgresql://postgres.vhkjyefwpwtlcyuznqgq:facial-slurp-jockey@aws-0-us-east-1.pooler.supabase.com:6543/postgres"
```

---

## 4️⃣ Verificar Configuração no Dashboard

1. Acesse: https://supabase.com/dashboard/project/vhkjyefwpwtlcyuznqgq/settings/database
2. Verifique se:
   - ✅ **Database Password** está correto
   - ✅ **Connection String** está disponível
   - ✅ **Host** aparece corretamente

---

## 5️⃣ Solução Alternativa: Usar Supabase Edge Functions (API)

Se a conexão direta ao PostgreSQL não funcionar, você pode:

1. Usar apenas Supabase Auth (já está funcionando no frontend)
2. Criar uma API no Supabase (Edge Functions ou PostgREST)
3. Backend chama a API em vez de conectar diretamente ao banco

**Mas primeiro, tente as opções acima!**

---

## ✅ Próximos Passos

1. **Verifique se projeto está ativo** no dashboard
2. **Tente usar Connection Pooling** (mais estável)
3. **Teste conexão manualmente** com ping/psql
4. **Atualize o `.env`** com a string correta
5. **Tente rodar novamente:** `START-DEV.bat`

---

## 📝 Referências

- [Supabase Database Settings](https://supabase.com/dashboard/project/vhkjyefwpwtlcyuznqgq/settings/database)
- [Supabase Connection Pooling Docs](https://supabase.com/docs/guides/database/connecting-to-postgres#connection-pooler)
