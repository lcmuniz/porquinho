# Executive Summary

## Project Vision

O **porquinho** é uma plataforma brasileira de gestão financeira pessoal que transforma disciplina financeira em experiência visualmente recompensadora. O produto resolve o problema crítico de abandono de ferramentas financeiras através de três pilares:

1. **Orçamento por Envelope Digital** - Conceito YNAB adaptado ao Brasil (parcelamento, Pix, bancos brasileiros)
2. **IA de Classificação Automática** - Reduz esforço de 20 minutos para 2 minutos de revisão
3. **Dashboard GPS Financeiro** - Visualização motivadora que responde "Onde estou? Para onde vou? Preciso ajustar?"

**Core Insight:** Usuários abandonam ferramentas financeiras não por falta de vontade, mas por "muito esforço para pouco retorno visual de progresso". O porquinho inverte essa equação.

## Target Users

**Primary User: "O Profissional Que Ganha Bem Mas Não Sobra"**

- **Perfil:** Luiz, 38 anos, funcionário público, R$ 25.000/mês
- **Problema:** Zero reserva de emergência, dinheiro não sobra apesar da boa renda
- **Frustração:** "Eu ganho bem, onde diabos está indo meu dinheiro?"
- **Comportamento de Uso:**
  - **Ritual Semanal:** 5 minutos aos sábados (desktop, café da manhã)
  - **Consultas Rápidas:** Durante a semana antes de gastos >R$300 (mobile/desktop)
- **Tech Literacy:** Intermediário - confortável com apps, mas não quer complexidade
- **Objetivo:** Criar reserva de emergência R$120k em 6 meses, ter clareza financeira

**Secondary User: "Familiar Observador"**

- **Papel:** Cônjuge/família que acompanha passivamente
- **Uso:** Consulta ocasional de status (modo visualização)
- **Nota MVP:** Foco 100% no usuário primário

## Key Design Challenges

1. **Dual Mode Experience**
   - **Desafio:** Mesma plataforma serve dois contextos diferentes
     - **Modo Ritual** (desktop, 5 min, semanal): Importação → Revisão → Dashboard completo → Insights
     - **Modo Consulta Rápida** (mobile/desktop, 30 seg, ad-hoc): "Posso gastar R$X?"
   - **Risco:** Tentar fazer tudo pode resultar em nenhuma experiência sendo ótima
   - **Abordagem:** Priorizar claramente cada modo (desktop-first para ritual, mobile-optimized para consulta)

2. **Scroll Vertical com 3 Camadas de Hierarquia**
   - **Desafio:** Dashboard GPS tem 3 camadas (Layer 1: 5 métricas → Layer 2: Visão tática → Layer 3: Detalhes)
   - **Solução:** Progressive disclosure via scroll vertical
     - Camada 1 sempre visible "above the fold" (90% do uso)
     - Camadas 2 e 3 expandem progressivamente
   - **Oportunidade:** Scroll cria narrativa natural "Onde estou → Para onde vou → Como ajustar"

3. **Alerta de Problema + Ação Construtiva**
   - **Desafio:** Insights podem revelar más notícias sem desmotivar usuário
   - **Tom:** "Descoberta de problema" (não culpa/julgamento)
   - **Padrão:** Sempre acompanhar insight negativo com ação construtiva clara
   - **Exemplo:** "Você gastou R$1.200 em delivery 📊 → Reduzindo 30%, você adiciona R$360/mês à sua reserva"

## Design Opportunities

1. **Semáforo de Saúde Financeira como Quick Win**
   - Visual instantâneo (🟢🟡🔴) que responde "estou bem?" em 1 segundo
   - Serve tanto Modo Ritual quanto Consulta Rápida
   - "Gancho" que traz usuário de volta ao ritual semanal
   - Oportunidade de micro-satisfação diária

2. **Dashboard GPS como Diferencial Competitivo**
   - Competitors têm "relatórios do passado" (análise retroativa)
   - Porquinho tem "navegação para o futuro" (orientação proativa)
   - Scroll vertical permite storytelling: Situação atual → Trajetória → Ajustes necessários
   - Satisfação visual de ver progresso (curvas subindo, metas enchendo)

3. **Insights Surpresa como Retention Mechanic**
   - "Gamification" sem ser infantil ou condescendente
   - Curiosidade semanal = hábito sustentável (não obrigação)
   - Tone de "descoberta interessante" mantém engajamento
   - Antecipação positiva: "O que vou descobrir hoje?"

4. **Classificação IA como Redução de Atrito**
   - IA faz 80%+ do trabalho pesado automaticamente
   - Usuário apenas revisa e ajusta (2 min vs. 20 min manual)
   - UX challenge: Como mostrar que IA "trabalhou" sem exigir validação de cada item?
   - Oportunidade: Transparência da IA gera confiança
