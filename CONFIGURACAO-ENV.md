# 📋 Configuração de Variáveis de Ambiente - Guia Definitivo

## 🎯 Resposta Rápida: Quantos .env eu preciso?

**Para desenvolvimento local:**

```
✅ porquinho-frontend/.env    ← OBRIGATÓRIO
✅ porquinho/.env              ← OBRIGATÓRIO (backend lê daqui via script)
❌ porquinho-backend/.env      ← NÃO EXISTE (Spring Boot não usa .env diretamente)
```

---

## 📊 Diagrama de Fluxo

```
┌─────────────────────────────────────────────────────────────┐
│                    DESENVOLVIMENTO LOCAL                     │
└─────────────────────────────────────────────────────────────┘

Terminal 1 (Backend):
┌──────────────────────────────────────────────────────────────┐
│ cd porquinho-backend                                         │
│ run-dev.bat                                                  │
│   │                                                          │
│   ├─> Lê ../env (raiz do projeto)                          │
│   │    ├─ SUPABASE_DB_URL                                   │
│   │    ├─ SUPABASE_DB_USERNAME                              │
│   │    ├─ SUPABASE_DB_PASSWORD                              │
│   │    ├─ SUPABASE_JWT_ISSUER_URI                          │
│   │    └─ SUPABASE_JWT_JWK_SET_URI                         │
│   │                                                          │
│   └─> Inicia Spring Boot (porta 8080)                      │
└──────────────────────────────────────────────────────────────┘

Terminal 2 (Frontend):
┌──────────────────────────────────────────────────────────────┐
│ cd porquinho-frontend                                        │
│ npm run dev                                                  │
│   │                                                          │
│   ├─> Lê .env (próprio diretório)                          │
│   │    ├─ VITE_SUPABASE_URL                                 │
│   │    ├─ VITE_SUPABASE_ANON_KEY                           │
│   │    └─ VITE_API_BASE_URL                                │
│   │                                                          │
│   └─> Inicia Vite (porta 5173)                             │
└──────────────────────────────────────────────────────────────┘
```

---

## 📁 Detalhamento de Cada Arquivo

### 1️⃣ `porquinho/.env` (Raiz)

**Usado por:**
- ✅ Script `porquinho-backend/run-dev.bat` (desenvolvimento)
- ✅ Docker Compose (produção)

**Conteúdo:**

```env
# ==============================================
# Configuração do Backend (Spring Boot)
# ==============================================

# Database Connection (Supabase PostgreSQL)
SUPABASE_DB_URL=jdbc:postgresql://db.xxxxxxxxxxxx.supabase.co:5432/postgres
SUPABASE_DB_USERNAME=postgres
SUPABASE_DB_PASSWORD=sua-senha-aqui

# JWT Validation (Supabase Auth)
SUPABASE_JWT_ISSUER_URI=https://xxxxxxxxxxxx.supabase.co/auth/v1
SUPABASE_JWT_JWK_SET_URI=https://xxxxxxxxxxxx.supabase.co/auth/v1/jwks

# Redis (opcional)
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# Usado também pelo docker-compose.yml:
SUPABASE_URL=https://xxxxxxxxxxxx.supabase.co
SUPABASE_ANON_KEY=sua-anon-key-aqui
VITE_API_BASE_URL=http://localhost/api
```

**Como preencher:**
1. Copie de `.env.example`: `copy .env.example .env`
2. Acesse [Supabase Dashboard](https://supabase.com/dashboard)
3. Vá em Settings → Database para `SUPABASE_DB_URL` e senha
4. Vá em Settings → API para `SUPABASE_URL` e `SUPABASE_ANON_KEY`

---

### 2️⃣ `porquinho-frontend/.env`

**Usado por:**
- ✅ Vite (desenvolvimento com `npm run dev`)
- ✅ Build de produção (`npm run build`)

**Conteúdo:**

```env
# ==============================================
# Configuração do Frontend (Vue + Vite)
# ==============================================

# Supabase Authentication
VITE_SUPABASE_URL=https://xxxxxxxxxxxx.supabase.co
VITE_SUPABASE_ANON_KEY=sua-anon-key-aqui

# Backend API
VITE_API_BASE_URL=http://localhost:8080
```

**Como preencher:**
1. Copie de `.env.example`: `copy .env.example .env`
2. Use as **mesmas credenciais** do Supabase do arquivo da raiz
3. **Importante:** `VITE_API_BASE_URL` aponta para o backend local (porta 8080)

**Nota:** Variáveis com prefixo `VITE_` são expostas no código do cliente!

---

### 3️⃣ `porquinho-backend/.env` ❌ NÃO EXISTE!

**Por que não existe?**

Spring Boot **não lê arquivos `.env` nativamente**. Ele usa:

1. `application.yml` / `application-dev.yml` (configuração base)
2. Variáveis de ambiente do sistema (sobrescreve valores do YAML)

**Por que existe `.env.example` então?**

Apenas como **documentação** para você saber quais variáveis são necessárias.

---

## 🚀 Como o Backend Acessa as Variáveis?

### Método 1: Via Script `run-dev.bat` (Recomendado)

O script `run-dev.bat` lê o arquivo `porquinho/.env` e exporta as variáveis:

```batch
porquinho-backend/run-dev.bat
  ↓
Lê ../env (raiz)
  ↓
Exporta como variáveis de ambiente
  ↓
Executa: mvnw spring-boot:run
  ↓
Spring Boot lê as variáveis exportadas
```

### Método 2: IDE (IntelliJ, Eclipse, VS Code)

Configure nas Run Configurations:

**IntelliJ IDEA:**
1. Run → Edit Configurations
2. Environment Variables → clique em 📁
3. Adicione cada variável:
   ```
   SUPABASE_DB_URL=jdbc:postgresql://...
   SUPABASE_DB_USERNAME=postgres
   SUPABASE_DB_PASSWORD=sua-senha
   ...
   ```

**VS Code:**
Crie `porquinho-backend/.vscode/launch.json`:

```json
{
  "configurations": [
    {
      "type": "java",
      "name": "Spring Boot Dev",
      "request": "launch",
      "mainClass": "com.porquinho.PorquinhoBackendApplication",
      "env": {
        "SPRING_PROFILES_ACTIVE": "dev",
        "SUPABASE_DB_URL": "jdbc:postgresql://db.xxx.supabase.co:5432/postgres",
        "SUPABASE_DB_USERNAME": "postgres",
        "SUPABASE_DB_PASSWORD": "sua-senha"
      }
    }
  ]
}
```

### Método 3: Linha de Comando (Manual)

```bash
cd porquinho-backend

# Windows (PowerShell)
$env:SUPABASE_DB_URL="jdbc:postgresql://..."
$env:SUPABASE_DB_USERNAME="postgres"
./mvnw spring-boot:run

# Windows (CMD)
set SUPABASE_DB_URL=jdbc:postgresql://...
set SUPABASE_DB_USERNAME=postgres
mvnw spring-boot:run

# Git Bash (Windows)
export SUPABASE_DB_URL="jdbc:postgresql://..."
export SUPABASE_DB_USERNAME="postgres"
./mvnw spring-boot:run
```

---

## 🎯 Checklist de Setup

### ✅ Primeira Execução

```bash
# 1. Configure o frontend
cd porquinho-frontend
copy .env.example .env
notepad .env  # Preencha com credenciais do Supabase

# 2. Configure o backend (via raiz)
cd ..
copy .env.example .env
notepad .env  # Preencha com credenciais do Supabase

# 3. Instale dependências do frontend
cd porquinho-frontend
npm install

# 4. Inicie tudo com um comando
cd ..
START-DEV.bat
```

### 📝 Valores Necessários do Supabase

Acesse https://supabase.com/dashboard → seu projeto:

| Variável | Onde encontrar |
|----------|----------------|
| `SUPABASE_URL` | Settings → API → Project URL |
| `SUPABASE_ANON_KEY` | Settings → API → anon public |
| `SUPABASE_DB_URL` | Settings → Database → Connection string → URI (trocar `postgresql://` por `jdbc:postgresql://`) |
| `SUPABASE_DB_PASSWORD` | A senha que você definiu ao criar o projeto |

**Formato correto do DB_URL:**

❌ Errado: `postgresql://postgres.xxx:pooler@...`
✅ Correto: `jdbc:postgresql://db.xxx.supabase.co:5432/postgres`

---

## 🔐 Segurança

### ⚠️ NUNCA commite arquivos .env!

Verifique se `.gitignore` tem:

```
.env
.env.local
.env.*.local
```

### ✅ Use .env.example como template

```bash
# Sempre mantenha .env.example atualizado (SEM credenciais reais)
# Outros desenvolvedores podem copiar:
copy .env.example .env
```

---

## 🆘 Problemas Comuns

### ❌ Backend não conecta ao Supabase

**Erro:**
```
java.net.ConnectException: Connection refused
```

**Solução:**
1. Verifique se `SUPABASE_DB_URL` está no formato correto: `jdbc:postgresql://db.xxx.supabase.co:5432/postgres`
2. Teste a conexão: `psql "postgresql://postgres:[senha]@db.xxx.supabase.co:5432/postgres"`
3. Verifique se o IP está permitido no Supabase (Settings → Database → Connection Pooling)

---

### ❌ JWT validation fails

**Erro:**
```
Invalid JWT signature
```

**Solução:**
1. Verifique `SUPABASE_JWT_ISSUER_URI`: deve ser `https://xxx.supabase.co/auth/v1`
2. Verifique `SUPABASE_JWT_JWK_SET_URI`: deve ser `https://xxx.supabase.co/auth/v1/jwks`
3. Certifique-se que o projeto Supabase está ativo

---

### ❌ Frontend não encontra variáveis

**Erro no console:**
```
VITE_SUPABASE_URL is undefined
```

**Solução:**
1. Verifique se `porquinho-frontend/.env` existe
2. Certifique-se que as variáveis começam com `VITE_`
3. **Reinicie o servidor Vite** após mudar o `.env`: `Ctrl+C` → `npm run dev`

---

## 📚 Resumo Final

```
┌─────────────────────────────────────────────────────────────┐
│                    CONFIGURAÇÃO MÍNIMA                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1️⃣  porquinho/.env                                         │
│      └─ Credenciais do Supabase para o backend             │
│                                                              │
│  2️⃣  porquinho-frontend/.env                               │
│      └─ Credenciais do Supabase + URL do backend           │
│                                                              │
│  3️⃣  Rodar: START-DEV.bat                                  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

**Dúvidas?** Execute `START-DEV.bat` e comece a desenvolver! 🚀
