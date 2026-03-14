<script setup lang="ts">
import SkipLink from '@/components/common/SkipLink.vue'
</script>

<template>
  <section aria-labelledby="a11y-showcase-heading" class="p-6 space-y-8">
    <header>
      <h2 id="a11y-showcase-heading" class="text-2xl font-semibold text-foreground mb-2">
        Accessibility Showcase
      </h2>
      <p class="text-muted-foreground">
        Demonstracao de padroes de acessibilidade WCAG 2.1 AA implementados no design system.
      </p>
    </header>

    <!-- Skip Link -->
    <article aria-labelledby="skip-link-heading" class="rounded-lg border border-border p-4 space-y-3">
      <h3 id="skip-link-heading" class="text-lg font-medium text-foreground">Skip Link</h3>
      <p class="text-sm text-muted-foreground">
        O skip link permite usuarios de teclado pular diretamente para o conteudo principal,
        evitando navegar por header e navegacao a cada pagina.
      </p>
      <div class="bg-muted rounded p-3 text-sm font-mono">
        <p class="text-muted-foreground mb-2">Exemplo (visivel ao receber foco com Tab):</p>
        <div class="relative h-8 overflow-hidden rounded border border-border bg-background">
          <SkipLink target="#demo-main">Pular para o conteudo principal</SkipLink>
          <span class="absolute inset-0 flex items-center px-3 text-xs text-muted-foreground">
            Pressione Tab para revelar o skip link
          </span>
        </div>
      </div>
      <p class="text-xs text-muted-foreground">Implementado em: <code>App.vue</code> + <code>SkipLink.vue</code></p>
    </article>

    <!-- Focus Indicators -->
    <article aria-labelledby="focus-heading" class="rounded-lg border border-border p-4 space-y-3">
      <h3 id="focus-heading" class="text-lg font-medium text-foreground">Focus Indicators</h3>
      <p class="text-sm text-muted-foreground">
        Todos os elementos interativos recebem um outline roxo de 2px ao receber foco via teclado,
        garantindo contraste minimo 3:1 contra os backgrounds do projeto.
      </p>
      <div class="flex flex-wrap gap-3">
        <button class="px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm font-medium">
          Botao (foque com Tab)
        </button>
        <button class="px-4 py-2 bg-secondary text-secondary-foreground rounded-md text-sm font-medium">
          Botao Secundario
        </button>
        <a href="#" class="px-4 py-2 text-primary underline text-sm" @click.prevent>Link (foque com Tab)</a>
      </div>
      <p class="text-xs text-muted-foreground">
        Configurado via <code>*:focus-visible</code> em <code>main.css</code>.
        Token: <code>--color-ring</code> (Purple 600 / Purple 400 no dark mode).
      </p>
    </article>

    <!-- Semantic Markup -->
    <article aria-labelledby="semantic-heading" class="rounded-lg border border-border p-4 space-y-3">
      <h3 id="semantic-heading" class="text-lg font-medium text-foreground">Semantic HTML Structure</h3>
      <p class="text-sm text-muted-foreground">
        A estrutura do app usa elementos HTML5 semanticos que criam landmarks para leitores de tela.
      </p>
      <div class="bg-muted rounded p-3 text-sm font-mono space-y-1">
        <p><span class="text-primary">&lt;header&gt;</span> — cabecalho e navegacao principal</p>
        <p class="pl-4"><span class="text-primary">&lt;nav aria-label="..."&gt;</span> — links de navegacao</p>
        <p><span class="text-primary">&lt;main id="main-content"&gt;</span> — conteudo principal</p>
        <p class="pl-4"><span class="text-primary">&lt;section&gt;</span> / <span class="text-primary">&lt;article&gt;</span> — secoes de conteudo</p>
        <p><span class="text-primary">&lt;footer&gt;</span> — rodape</p>
      </div>
    </article>

    <!-- Keyboard Navigation -->
    <article aria-labelledby="keyboard-heading" class="rounded-lg border border-border p-4 space-y-3">
      <h3 id="keyboard-heading" class="text-lg font-medium text-foreground">Keyboard Navigation</h3>
      <p class="text-sm text-muted-foreground">
        Todos os componentes shadcn-vue (baseados em reka-ui) suportam navegacao por teclado nativa.
      </p>
      <div class="overflow-x-auto">
        <table class="text-sm w-full" aria-label="Atalhos de teclado por componente">
          <thead>
            <tr class="border-b border-border">
              <th class="text-left py-2 pr-4 font-medium text-foreground">Componente</th>
              <th class="text-left py-2 pr-4 font-medium text-foreground">Teclas</th>
              <th class="text-left py-2 font-medium text-foreground">Acao</th>
            </tr>
          </thead>
          <tbody class="text-muted-foreground">
            <tr class="border-b border-border/50">
              <td class="py-2 pr-4">Button</td>
              <td class="py-2 pr-4"><kbd class="bg-muted px-1 rounded text-xs">Enter</kbd> / <kbd class="bg-muted px-1 rounded text-xs">Space</kbd></td>
              <td class="py-2">Aciona o botao</td>
            </tr>
            <tr class="border-b border-border/50">
              <td class="py-2 pr-4">Select / Combobox</td>
              <td class="py-2 pr-4"><kbd class="bg-muted px-1 rounded text-xs">Enter</kbd> / <kbd class="bg-muted px-1 rounded text-xs">↑↓</kbd></td>
              <td class="py-2">Abre / navega opcoes</td>
            </tr>
            <tr class="border-b border-border/50">
              <td class="py-2 pr-4">Dialog</td>
              <td class="py-2 pr-4"><kbd class="bg-muted px-1 rounded text-xs">Esc</kbd></td>
              <td class="py-2">Fecha o dialog</td>
            </tr>
            <tr>
              <td class="py-2 pr-4">Input</td>
              <td class="py-2 pr-4"><kbd class="bg-muted px-1 rounded text-xs">Tab</kbd></td>
              <td class="py-2">Navega entre campos</td>
            </tr>
          </tbody>
        </table>
      </div>
    </article>

    <!-- Touch Targets -->
    <article aria-labelledby="touch-heading" class="rounded-lg border border-border p-4 space-y-3">
      <h3 id="touch-heading" class="text-lg font-medium text-foreground">Touch Targets (44x44px)</h3>
      <p class="text-sm text-muted-foreground">
        Todos os elementos interativos tem area de toque minima de 44x44px, conforme WCAG 2.5.5.
        Configurado via CSS em <code>main.css</code> (<code>min-height: 2.75rem</code>).
      </p>
      <div class="flex flex-wrap gap-3 items-center">
        <button class="bg-primary text-primary-foreground rounded-md px-4 text-sm font-medium">
          Botao 44px+
        </button>
        <button class="bg-secondary text-secondary-foreground rounded-md px-4 text-sm font-medium" aria-label="Icone de exemplo">
          Icon
        </button>
      </div>
    </article>
  </section>
</template>
