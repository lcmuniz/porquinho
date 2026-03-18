# 🚀 Setup Local - Guia Rápido

Este guia explica como configurar e rodar a aplicação Porquinho localmente.

## 📁 Estrutura de Configuração (.env files)

```
porquinho/
├── .env                          ← Para Docker Compose (OPCIONAL)
├── START-DEV.bat                 ← Script para iniciar tudo! (Windows)
├── porquinho-frontend/
│   ├── .env                      ← Frontend Vite (OBRIGATÓRIO)
│   └── run-dev.bat              ← Script para rodar frontend
└── porquinho-backend/
    ├── run-dev.bat              ← Script para rodar backend
    └── .env.example             ← Apenas referência (não usado)
```

### ⚙️ Qual .env eu preciso?

| Arquivo | Quando usar? | Obrigatório? |
|---------|--------------|--------------|
| `porquinho/.env` | Docker Compose | ❌ Não (só se usar Docker) |
| `porquinho-frontend/.env` | Desenvolvimento local | ✅ **SIM** |
| `porquinho-backend/.env` | ❌ **NUNCA** - Backend não usa | ❌ Não existe |

**Backend usa variáveis de ambiente do sistema**, não arquivo `.env`.

---

## 🎯 Setup Rápido (5 minutos)

### 1. Configure o Frontend (.env obrigatório)

```bash
cd porquinho-frontend

# Se .env não existe, crie a partir do exemplo
copy .env.example .env

# Edite com suas credenciais do Supabase
notepad .env
```

**Conteúdo do `porquinho-frontend/.env`:**

```env
VITE_SUPABASE_URL=https://seu-projeto.supabase.co
VITE_SUPABASE_ANON_KEY=sua-anon-key-do-supabase
VITE_API_BASE_URL=http://localhost:8080
```

### 2. Configure o Backend (variáveis de ambiente)

**Opção A: Use o script (mais fácil)**

Edite o `.env` na raiz do projeto com as credenciais do Supabase:

```bash
cd porquinho
notepad .env
```

**Conteúdo do `porquinho/.env`:**

```env
SUPABASE_DB_URL=jdbc:postgresql://db.seu-projeto.supabase.co:5432/postgres
SUPABASE_DB_USERNAME=postgres
SUPABASE_DB_PASSWORD=sua-senha-do-supabase
SUPABASE_JWT_ISSUER_URI=https://seu-projeto.supabase.co/auth/v1
SUPABASE_JWT_JWK_SET_URI=https://seu-projeto.supabase.co/auth/v1/jwks
```

**Opção B: Configure na sua IDE (IntelliJ/Eclipse)**

Em vez de usar `.env`, configure as variáveis na Run Configuration da IDE.

---

## 🚀 Rodando a Aplicação

### Modo 1: Script Automático (MAIS FÁCIL) ⭐

**Um único comando inicia tudo:**

```bash
# Na raiz do projeto
START-DEV.bat
```

Isso vai:
- ✅ Iniciar Redis automaticamente (Docker)
- ✅ Abrir Backend em uma janela (porta 8080)
- ✅ Abrir Frontend em outra janela (porta 5173)

### Modo 2: Manual (2 terminais)

**Terminal 1 - Backend:**

```bash
cd porquinho-backend
run-dev.bat
```

**Terminal 2 - Frontend:**

```bash
cd porquinho-frontend
run-dev.bat
```

---

## 🔗 Acessar a Aplicação

Após iniciar:

- 🌐 **Frontend:** http://localhost:5173
- 🔌 **Backend:** http://localhost:8080
- ❤️ **Health Check:** http://localhost:8080/api/v1/auth/health

---

## 🧪 Testar o Registro

1. Abra http://localhost:5173/register
2. Preencha:
   - **Nome:** Seu Nome
   - **Email:** seu@email.com
   - **Senha:** MinhaSenh@123 (min 8 chars, 1 maiúscula, 1 minúscula, 1 número)
3. Clique em "Cadastrar com Email"
4. Verifique seu email para o link de verificação do Supabase

---

## 🐛 Troubleshooting

### Backend não conecta ao Supabase

```
Error: Connection refused to db.*.supabase.co
```

**Solução:**
- Verifique se o `SUPABASE_DB_URL` no `.env` está correto
- Formato: `jdbc:postgresql://db.seu-projeto.supabase.co:5432/postgres`
- Teste a conexão com um cliente PostgreSQL (DBeaver, pgAdmin)

### Frontend não encontra o Backend

```
Error: Network Error
```

**Solução:**
- Verifique se backend está rodando: http://localhost:8080/api/v1/auth/health
- Verifique `VITE_API_BASE_URL` no `porquinho-frontend/.env`
- Deve ser: `http://localhost:8080` (sem `/api` no final)

### Redis connection failed

Redis é **opcional** para desenvolvimento. Se quiser usar:

```bash
docker run -d -p 6379:6379 --name porquinho-redis redis:7-alpine
```

---

## 📦 Comandos Úteis

### Backend

```bash
# Rodar testes
cd porquinho-backend
mvnw test

# Compilar
mvnw clean install -DskipTests

# Rodar em dev
run-dev.bat
```

### Frontend

```bash
# Instalar dependências
cd porquinho-frontend
npm install

# Rodar em dev
npm run dev

# Build produção
npm run build

# Type check
npm run type-check
```

---

## 🔐 Onde encontrar credenciais do Supabase?

1. Acesse https://supabase.com/dashboard
2. Selecione seu projeto
3. Vá em **Settings → API**:
   - `SUPABASE_URL`: Project URL
   - `SUPABASE_ANON_KEY`: anon public
4. Vá em **Settings → Database**:
   - `SUPABASE_DB_URL`: Connection string (modo "URI")
   - `SUPABASE_DB_PASSWORD`: sua senha do projeto

---

## 🎓 Precisa de Ajuda?

- 📖 [Documentação do Supabase](https://supabase.com/docs)
- 🔧 [Spring Boot Docs](https://spring.io/projects/spring-boot)
- ⚡ [Vite Docs](https://vitejs.dev/)

---

**Próximos passos:** Execute `START-DEV.bat` e comece a desenvolver! 🚀
