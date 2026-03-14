# Core User Experience

## Defining Experience

**O Coração do Produto:**

O **porquinho** é fundamentalmente uma plataforma de **acompanhamento visual de crescimento de patrimônio e alcance de objetivos financeiros**. O produto não é sobre "controlar gastos" ou "categorizar transações" - essas são ferramentas para o verdadeiro objetivo: ver progresso tangível em direção a metas financeiras.

**A Ação Central:**

O **Ritual Semanal de 5 Minutos** é o mecanismo que alimenta o coração do produto. Esse ritual acontece aos sábados de manhã, durante o café, e consiste em:

1. **Importar** arquivos OFX/CSV do banco (drag-and-drop)
2. **Revisar** classificações automáticas da IA (2 minutos, apenas exceções)
3. **Navegar** Dashboard GPS (scroll vertical intuitivo)
4. **Ver progresso** de patrimônio e metas (satisfação visual)
5. **Receber insight** surpresa semanal (curiosidade e descoberta)

**Sucesso = Ritual Semanal Consistente.** Se o usuário volta toda semana para fazer o ritual, o produto está funcionando. Se abandona o ritual, o produto falhou.

**Modo Secundário - Consulta Rápida:**

Durante a semana, usuário consulta rapidamente (30 segundos, mobile/desktop):
- "Posso gastar R$X na categoria Y?"
- Semáforo de saúde financeira (🟢🟡🔴)
- Saldo disponível da categoria específica

Consulta rápida **não substitui** o ritual - ela **complementa** criando consciência financeira diária sem exigir comprometimento de tempo.

## Platform Strategy

**Desktop-First para Ritual Semanal:**

- **Plataforma Primária:** Web desktop (navegador)
- **Input Method:** Mouse/keyboard tradicional
- **Context:** Sábado de manhã, casa, tranquilidade, tela grande
- **Conexão:** Sempre online (sem requisitos offline)
- **Rationale:** Gestão financeira séria requer espaço visual e foco - desktop é ideal para ritual de 5 minutos

**Mobile-Optimized para Consulta Rápida:**

- **Plataforma Secundária:** Web responsive (não app nativo no MVP)
- **Input Method:** Touch
- **Context:** Durante a semana, ad-hoc, antes de compras
- **Uso:** Consulta de saldo de categoria e semáforo de saúde
- **Rationale:** Mobile não tenta replicar ritual completo - foca em responder "posso gastar?" rapidamente

**Device-Specific Capabilities:**

- **Notificações Web:** Lembrete semanal no sábado de manhã
  - "Seu insight semanal está pronto 📊"
  - Tom: Convite positivo, não cobrança
- **Drag-and-Drop:** Arrastar arquivo OFX/CSV direto do Windows Explorer para navegador
- **Atalhos de Teclado:** Para power users acelerar ritual (futuro)

**Not in MVP:**
- App nativo mobile
- Funcionalidade offline
- Câmera/scan de documentos

## Effortless Interactions

**Pontos de Dor Eliminados:**

O porquinho elimina os 5 maiores pontos de frustração com ferramentas financeiras:

1. **Categorização Manual Demorada** → IA classifica 80%+ automaticamente
2. **Não Entender "O Que Fazer Agora"** → Dashboard GPS responde 3 perguntas claramente
3. **Trabalhar Para a Ferramenta** → IA trabalha, usuário apenas decide
4. **Não Ver Recompensa Visual** → Dashboard celebra progresso com animações satisfatórias
5. **Entrada Manual Constante** → Importação automática de arquivos

**Interações Zero-Esforço:**

As seguintes interações devem ser completamente sem atrito, naturais, sem precisar pensar:

- **Importação de OFX/CSV:** Drag-and-drop direto do Windows Explorer, sem diálogos complexos
- **Revisão de Classificações IA:** Aprovar em lote, foco apenas em exceções (20% não classificados)
- **Navegação Dashboard GPS:** Scroll vertical intuitivo, sem clicks, hierarquia visual clara
- **Ver Progresso de Metas:** Atualização instantânea, animações satisfatórias (curvas subindo, barras enchendo)
- **Consulta Rápida de Categoria:** Resposta em 1 segundo, semáforo visual imediato

**Feedback Imediato:**

Toda ação do usuário tem resposta visual instantânea:
- Arquivo importado → "X transações importadas, Y já classificadas"
- Classificação ajustada → Gráficos atualizam imediatamente
- Meta ajustada → Projeção recalcula em tempo real
- Scroll no dashboard → Transições suaves, progressive disclosure

## Critical Success Moments

Estes são os momentos **make-or-break** que determinam se usuário continua ou abandona:

**Momento #1: Primeira Importação (Onboarding)**

- **Quando:** Setup inicial, primeira sessão
- **O Que Acontece:** IA classifica 80% das transações automaticamente
- **Reação Esperada:** "Caralho, isso realmente funciona!"
- **Por Que É Crítico:** Se usuário precisa classificar manualmente 100 transações na primeira sessão, abandona achando que vai dar muito trabalho
- **Design Implication:** IA precisa impressionar imediatamente, mostrar trabalho feito

**Momento #2: Primeiro Insight Surpresa (Semana 1)**

- **Quando:** Final do primeiro ritual semanal
- **O Que Acontece:** "Você gastou R$1.200 em delivery - 35% dos seus gastos com alimentação"
- **Reação Esperada:** "Eu não tinha ideia! Isso é revelador!"
- **Por Que É Crítico:** Se insight for genérico ou óbvio, usuário não vê valor único, não volta
- **Design Implication:** Insight precisa ser específico, surpreendente, acionável

**Momento #3: Ver Curva de Patrimônio Subindo (Semana 4)**

- **Quando:** ~1 mês de uso consistente
- **O Que Acontece:** Dashboard mostra reserva crescendo pela primeira vez (ex: R$3.000)
- **Reação Esperada:** "Está funcionando! Meu esforço está valendo a pena!"
- **Por Que É Crítico:** Se usuário não SENTE progresso tangível, perde motivação e desiste
- **Design Implication:** Visualização de crescimento precisa ser celebratória, visceral, emocionante

**Momento #4: Primeira Emergência (Mês 2)**

- **Quando:** Surge despesa inesperada (ex: carro quebra)
- **O Que Acontece:** Usuário consulta porquinho, vê que tem R$6.000 em reserva
- **Reação Esperada:** "Pela primeira vez uma emergência não me desesperou. Eu SABIA que tinha o dinheiro."
- **Por Que É Crítico:** Momento de validação do sistema - disciplina financeira se paga na prática
- **Design Implication:** Reserva de emergência precisa estar visível, acessível, clara

**Se Qualquer Momento Falhar:**

- Momento #1 falha → Abandono imediato (esforço > expectativa)
- Momento #2 falha → Não volta na semana seguinte (sem valor único percebido)
- Momento #3 falha → Desiste em 1-2 meses (sem recompensa emocional)
- Momento #4 falha → Perde confiança no sistema (valor não se concretiza)

## Experience Principles

Estes princípios guiam todas as decisões de design do porquinho:

**1. Progresso Visual É a Recompensa**

- O coração do produto é ver crescimento de patrimônio e alcance de objetivos
- Dashboard GPS não é "relatório passivo", é "celebração ativa de progresso"
- Animações, curvas subindo, barras de meta enchendo = satisfação visceral
- Usuário precisa **SENTIR** que está evoluindo, não apenas saber racionalmente
- Design decision: Priorizar visualizações dinâmicas sobre tabelas estáticas

**2. Ritual de 5 Minutos, Não Trabalho Diário**

- Sucesso = ritual semanal consistente aos sábados
- Tudo otimizado para experiência tranquila de fim de semana
- Zero atrito: drag-and-drop, scroll intuitivo, IA trabalha automaticamente
- Notificação semanal é "convite positivo", não cobrança ou culpa
- Design decision: Comprimir tempo de ritual, não expandir engagement diário

**3. IA Trabalha, Usuário Decide**

- Eliminar maior ponto de dor: categorização manual demorada
- IA classifica 80%+ automaticamente, usuário apenas revisa exceções
- Transparência: mostrar que IA "trabalhou pelo usuário" gera confiança
- Usuário se sente "no controle" sem fazer trabalho pesado
- Design decision: Expor trabalho da IA claramente, não esconder mágica

**4. Clareza de Ação, Sempre**

- Eliminar frustração: "não sei o que fazer agora"
- Dashboard responde 3 perguntas: "Onde estou? Para onde vou? Preciso ajustar?"
- Insights sempre acompanhados de ação construtiva clara e específica
- Semáforo de saúde (🟢🟡🔴) dá resposta instantânea sem análise
- Design decision: Toda tela deve responder "o que isso significa para mim?" explicitamente

**5. Momentos "Aha" São Críticos**

- 4 momentos make-or-break: Primeira importação → Primeiro insight → Curva subindo → Primeira emergência
- Cada momento precisa entregar satisfação emocional tangível
- Falhar em qualquer um = alto risco de abandono permanente
- Design decision: Priorizar ruthlessly experiência desses 4 momentos sobre tudo
