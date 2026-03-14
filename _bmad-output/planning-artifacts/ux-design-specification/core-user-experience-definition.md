# Core User Experience Definition

## Defining Experience

**"Dashboard GPS que responde 'onde estou, para onde vou, preciso ajustar' em 30 segundos"**

Esta é a experiência definitiva do porquinho - a interação central que, se acertarmos, faz tudo mais funcionar. Quando usuários descrevem o porquinho para amigos, dirão:

> "É um app que te mostra em 30 segundos onde você está financeiramente, para onde está indo, e se precisa ajustar alguma coisa."

**Por Que Esta é a Experiência Definitiva:**

1. **Resolve o Core Problem:** Usuários abandonam ferramentas financeiras porque "não sabem o que fazer agora" (Design Challenge #1). Dashboard GPS responde explicitamente.

2. **Diferencial Competitivo:** Competidores mostram "relatórios do passado". Porquinho mostra "navegação para o futuro" (Design Opportunity #2).

3. **Alinha com Coração do Produto:** O coração é "acompanhamento visual de crescimento de patrimônio e alcance de objetivos". Dashboard GPS é o veículo para isso.

4. **Momento Crítico de Sucesso:** "Ver Curva de Patrimônio Subindo" (Momento #3) acontece aqui. Se Dashboard não celebra progresso visualmente, usuário desiste.

5. **Satisfaz Todas as Emoções Primárias:**
   - Empoderamento (sabe onde está)
   - Tranquilidade (vê que está no caminho)
   - Curiosidade (descobre padrões)
   - Satisfação (vê progresso tangível)

**Metáfora do GPS:**

GPS de carro funciona porque mostra posição atual, rota traçada, e avisa quando recalcular. Dashboard GPS Financeiro funciona igual:
- **Posição Atual:** Status financeiro agora vs planejado
- **Rota Traçada:** Projeção de metas e trajetória de patrimônio
- **Recalcular:** Alertas e recomendações de ajuste

**Nota Crítica:** GPS é conceito **implícito**, não explícito visualmente. Interface não mostra ícone de GPS ou usa linguagem de navegação literal. Mas **funciona** como GPS - usuário tem orientação clara sem precisar interpretar.

## User Mental Model

**Como Usuários Resolvem Isso Hoje:**

**YNAB e Ferramentas Tradicionais:**
- Olham tabelas densas com muitas colunas de números
- Tentam interpretar: "O que esses números significam para mim?"
- Procuram por padrões manualmente em dados históricos
- **Resultado:** Confusão, paralisia de análise, abandono

**Problema do Modelo Mental Atual:**
- Ferramentas assumem que usuário quer "ver todos os dados"
- Mas usuário quer "saber o que fazer agora"
- Interface otimizada para análise profunda, não orientação rápida

**Modelo Mental do Porquinho:**

Usuário não quer ser analista financeiro. Usuário quer ser motorista seguindo GPS:
- GPS não mostra todas as ruas da cidade
- GPS mostra: onde você está, próximo passo, ETA para destino
- **Analogia:** Dashboard GPS mostra: status atual, próxima ação, progresso para meta

**Expectativas do Usuário:**

1. **Velocidade:** "Não tenho tempo para analisar planilhas. Quero resposta em 30 segundos."
2. **Clareza:** "Me diga O QUE fazer, não me faça descobrir."
3. **Visual:** "Mostre gráficos, não tabelas. Quero VER se estou melhorando."
4. **Orientação:** "Me guie, não me julgue."

**Onde Usuários Esperam Confusão (Precisamos Evitar):**

- **Jargão Financeiro:** Termos como "allocation", "budget variance" confundem
- **Números Sem Contexto:** "R$ 3.000" significa o quê? Bom ou ruim?
- **Muitas Métricas:** 20 números na tela = não sei o que é importante
- **Hierarquia Confusa:** Tudo parece igualmente importante

**Expectativa de Como Deve Funcionar:**

Usuário espera que Dashboard GPS seja como:
- **Instagram Feed:** Scroll vertical natural, visual-first
- **Google Maps:** Resposta clara e orientação sem interpretação
- **Netflix Home:** Personalizado para mim, relevante agora

## Success Criteria

**O Dashboard GPS é perfeito quando:**

**1. Velocidade - 30 Segundos para Clareza Total**

- **Métrica:** Do momento que abre app até entender situação completa ≤ 30 segundos
- **Como Medir:** Time-to-comprehension em testes de usabilidade
- **Indicador de Falha:** Se usuário precisa >30s ou faz perguntas como "o que isso significa?"

**2. Clareza Visual Instantânea**

- **Semáforo no Topo:** Responde "estou bem?" em 1 segundo (🟢🟡🔴)
- **Gráfico de Patrimônio Dominante:** 80% do espaço above-the-fold
- **5 Métricas Essenciais:** Visíveis sem scroll, hierarquia de tamanho clara
- **Indicador de Sucesso:** Usuário em teste diz "ah, entendi" em <5 segundos

**3. Responde 3 Perguntas Explicitamente**

Dashboard deve responder textualmente (não deixar usuário deduzir):

**Pergunta 1: "Onde estou?"**
- Resposta: Status atual vs planejado
- Exemplo: "Você gastou R$ 12.500 de R$ 15.000 planejados este mês"
- Visual: Semáforo + gráfico mostrando posição atual

**Pergunta 2: "Para onde vou?"**
- Resposta: Projeção de metas e trajetória
- Exemplo: "No ritmo atual, você atingirá reserva de R$ 120k em 18 meses"
- Visual: Curva de patrimônio com projeção futura tracejada

**Pergunta 3: "Preciso ajustar?"**
- Resposta: Alertas ou confirmação de que está ok
- Exemplo: "Tudo certo! Continue assim." ou "Atenção: categoria Lazer 20% acima do planejado"
- Visual: Cards de recomendação ou check verde

**Indicador de Sucesso:** Usuário consegue verbalizar respostas às 3 perguntas após ver dashboard

**4. Zero Interpretação Necessária**

- **Números Têm Contexto:** "R$ 3.000 de R$ 120.000 (2.5%)" > apenas "R$ 3.000"
- **Cores Universais:** Verde=bom, Amarelo=atenção, Vermelho=crítico (sem ambiguidade)
- **Labels Claros:** "Reserva de Emergência" > "Emergency Fund" ou "Colchão"
- **Sem Jargão:** Linguagem natural portuguesa, não termos financeiros técnicos

**Indicador de Sucesso:** Usuário em teste não faz perguntas interpretativas ("o que significa X?")

**5. Satisfação Visual de Progresso**

- **Curvas Subindo:** Gráfico de patrimônio tem animação suave ao carregar
- **Barras Enchendo:** Progresso de metas visual e celebratório
- **Feedback Positivo:** Quando está indo bem, dashboard celebra (não apenas neutro)

**Indicador de Sucesso:** Usuário em teste expressa satisfação emocional ao ver progresso

**6. Acionável**

- Dashboard não é apenas informativo, é orientador
- Se há problema, mostra ação clara: "Reduza gastos com Lazer em R$ 300"
- Se está bem, reforça: "Continue assim! Você está X% acima da meta"

**Indicador de Sucesso:** Usuário sabe exatamente o que fazer (ou não fazer) após ver dashboard

## Novel vs. Established UX Patterns

**Análise: Combinação de Padrões Estabelecidos com Twist Único**

**Padrões Estabelecidos (Não Precisam Educação):**

1. **Scroll Vertical (Instagram/Twitter)**
   - Usuários já sabem: scroll para baixo = mais conteúdo
   - Não precisa tutorial
   - Porquinho usa: Camada 1 → 2 → 3 via scroll

2. **Semáforo de Status (Universal)**
   - 🟢🟡🔴 é universalmente compreendido
   - Trânsito, sistemas, apps de saúde
   - Porquinho usa: Saúde financeira no topo

3. **Gráficos de Linha para Tendências (Universal)**
   - Gráfico de linha = mudança ao longo do tempo
   - Ação, finanças, fitness apps
   - Porquinho usa: Curva de patrimônio

4. **Progressive Disclosure (Comum em Apps)**
   - Ver resumo primeiro, drill down para detalhes
   - Gmail, Notion, Slack
   - Porquinho usa: 3 camadas de hierarquia

**Twist Único (Inovação Dentro de Padrões Familiares):**

1. **"Dashboard GPS" - Metáfora de Orientação**
   - **Novel:** Ferramentas financeiras mostram "relatórios", não "navegação"
   - **Familiar:** Metáfora de GPS é universalmente compreendida
   - **Educação:** Mínima - usuário entende que é orientação, não análise
   - **Risco:** Baixo - funciona como GPS mesmo sem explicar metáfora

2. **Responder 3 Perguntas Explicitamente**
   - **Novel:** Dashboards tradicionais mostram métricas, usuário deduz respostas
   - **Porquinho:** Dashboard RESPONDE perguntas textualmente
   - **Exemplo:** Não só mostrar gráfico, mas escrever "No ritmo atual, você atingirá meta em X meses"
   - **Educação:** Zero - resposta textual é mais clara que deduzir de gráfico
   - **Risco:** Zero - aumenta clareza, não adiciona complexidade

3. **Camada 1: "90% do Uso, 5 Métricas Apenas"**
   - **Novel:** Ferramentas mostram 20+ métricas na home
   - **Porquinho:** Mostra apenas 5 essenciais, restante em camadas 2-3
   - **Educação:** Mínima - hierarquia visual torna óbvio o que é prioritário
   - **Risco:** Baixo - simplifica ao invés de complicar

4. **Celebração Visual de Progresso**
   - **Novel:** Dashboards financeiros são neutros/frios
   - **Porquinho:** Curvas subindo animadas, milestones celebrados
   - **Educação:** Zero - animações são auto-explicativas
   - **Risco:** Zero - adiciona satisfação emocional

**Estratégia de Educação do Usuário:**

**Não Precisa de Tutorial Explícito Porque:**
- Usa padrões que usuário já conhece (scroll, gráficos, cores)
- Inovações são simplificações, não complicações
- Respostas textuais tornam tudo mais claro

**Onboarding Mínimo:**
- Primeira vez que vê Dashboard: Tooltip rápido "Scroll para ver mais detalhes"
- Nenhum tour forçado de features
- Usuário descobre naturalmente através de uso

**Validação da Abordagem:**
- Instagram/YouTube não têm tutoriais - padrões são intuitivos
- Porquinho segue mesma filosofia: se precisa tutorial, UX falhou

## Experience Mechanics

**Fluxo Detalhado do Dashboard GPS:**

---

**1. INICIAÇÃO - Como Usuário Chega no Dashboard GPS**

**Cenário A: Ritual Semanal (Fluxo Completo)**

```
Importar OFX → Revisar Classificações IA → [Dashboard GPS]
```

- Após revisar classificações, botão "Ver Dashboard" aparece
- Click leva para Dashboard GPS com dados atualizados
- Transição suave (fade ou slide)

**Cenário B: Consulta Rápida (Acesso Direto)**

```
Abrir App → [Dashboard GPS]
```

- **Dashboard GPS é a tela inicial (home) do app**
- Dados já estão carregados (cached ou carrega em background)
- Semáforo aparece imediatamente (<1s)
- Gráficos carregam progressivamente (skeleton → dados reais)

**Cenário C: Retorno ao Dashboard**

```
Qualquer tela → Click Logo/Home → [Dashboard GPS]
```

- Logo no topo sempre leva de volta ao Dashboard
- Usuário pode voltar a qualquer momento

**Estado de Loading:**

- Semáforo: Aparece imediatamente com último valor conhecido
- Gráficos: Skeleton com animação shimmer
- Dados atualizam progressivamente (não espera tudo carregar)
- **Princípio:** Algo útil aparece em <1 segundo

---

**2. INTERAÇÃO - O Que Usuário Faz**

**Navegação Primária: Scroll Vertical**

```
[Semáforo - Fixo no Topo]
    ↓ scroll
[Camada 1: 5 Métricas Essenciais]
    ↓ scroll
[Camada 2: Visão Tática por Área]
    ↓ scroll
[Camada 3: Detalhes Completos]
```

**Comportamento do Scroll:**

- **Scroll Suave:** Não tem "snap" agressivo, flui naturalmente
- **Semáforo Sticky:** Fica fixo no topo enquanto scrolla
- **Progressive Disclosure:** Camadas aparecem gradualmente
- **90% Param na Camada 1:** Maioria nem scrolla, já tem resposta

**Interações Secundárias (Opcionais):**

- **Click em Gráfico:** Expande para ver detalhes (modal ou inline)
- **Hover em Métrica:** Tooltip com explicação adicional
- **Click em Recomendação:** Leva para ação específica (ex: ajustar categoria)

**Nenhum Click Obrigatório:**
- Usuário pode apenas scrollar e absorver informação
- Interações são opcionais para aprofundamento

---

**3. FEEDBACK - Como Sabe Que Está Funcionando**

**Visual Feedback (Primário):**

**Semáforo de Saúde (Topo Fixo):**
```
🟢 Saudável: Verde vibrante, check icon
🟡 Atenção: Amarelo, warning icon
🔴 Crítico: Vermelho, alert icon
```
- Transição animada entre estados (não abrupta)
- Pulsação suave quando crítico (atenção sem ser intrusivo)

**Gráfico de Patrimônio (Camada 1, Dominante):**
- Curva animada ao carregar (desenha linha progressivamente)
- Área preenchida abaixo da curva (sensação de crescimento)
- Última semana destacada (ponto maior, cor mais vibrante)
- Grid sutil (não competir com curva)

**5 Métricas Essenciais (Camada 1):**
- Cada métrica tem cor contextual:
  - Verde: Está bem / acima da meta
  - Amarelo: Atenção / próximo do limite
  - Vermelho: Crítico / acima do limite
- Ícone consistente por métrica (ex: 💰 reserva, 📊 gastos)
- Valor grande (numérico), contexto pequeno embaixo

**Textual Feedback (Secundário):**

**Respostas às 3 Perguntas (Cards Textuais):**

```
📍 Onde estou?
"Você gastou R$ 12.500 de R$ 15.000 planejados este mês."

🎯 Para onde vou?
"No ritmo atual, você atingirá reserva de R$ 120k em 18 meses."

⚙️ Preciso ajustar?
"Tudo certo! Continue assim." [ou] "Atenção: categoria Lazer 20% acima."
```

- Tom: Conversacional, não técnico
- Primeira pessoa: "Você" (pessoal, não corporativo)
- Números com contexto: Sempre comparativo, nunca absoluto isolado

**Estado de Sucesso vs. Problema:**

**Sucesso (🟢):**
- Feedback positivo celebratório
- "Parabéns! Você economizou R$ 500 vs média!"
- Tom encorajador, não neutro

**Problema (🔴):**
- Feedback com ação construtiva
- "Categoria Lazer 20% acima. Reduzir R$ 300 te coloca de volta no caminho."
- Tom: Descoberta de problema + solução, não culpa

---

**4. CONCLUSÃO - Como Sabe Que Terminou**

**Após 30 Segundos no Dashboard GPS, Usuário Pensa:**

✅ "Ok, entendi minha situação."
✅ "Sei se estou bem ou se preciso ajustar."
✅ "Vejo que minha reserva está [crescendo/estagnada]."
✅ "Sei exatamente o que fazer agora (ou que não preciso fazer nada)."

**Estado Mental Desejado:**

- **Se está bem (🟢):** Satisfação, tranquilidade, motivação para continuar
- **Se precisa atenção (🟡):** Clareza sobre problema, confiança de que pode ajustar
- **Se está crítico (🔴):** Urgência controlada, ação clara para tomar

**Próximas Ações Possíveis:**

1. **Fechar App (Maioria dos Casos)**
   - Usuário está satisfeito, tem resposta que precisava
   - Volta semana que vem para próximo check-in

2. **Aprofundar em Camada 2 ou 3 (Casos Específicos)**
   - Quer entender detalhes de categoria específica
   - Scroll para baixo para explorar

3. **Tomar Ação Sugerida (Se Houver Recomendação)**
   - Click em recomendação leva para ajustar orçamento
   - Ou abrir categoria para ver transações

**Feedback de Conclusão:**

- Nenhum pop-up forçado dizendo "Você completou!"
- Experiência fluida: usuário simplesmente sabe quando terminou
- Pode sair a qualquer momento sem penalidade
