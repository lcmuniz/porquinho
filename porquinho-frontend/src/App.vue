<script setup lang="ts">
import { RouterView } from 'vue-router'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
</script>

<template>
  <div class="min-h-screen bg-background text-foreground">
    <!-- Design System Validation: Tailwind CSS + shadcn-vue -->
    <div class="container mx-auto max-w-5xl p-6">
      <h1 class="text-3xl font-bold text-primary mb-2">porquinho</h1>
      <p class="text-sm text-muted-foreground mb-8">Design System — Tailwind CSS v4 + shadcn-vue</p>

      <div class="grid gap-6 md:grid-cols-2">
        <!-- Botões -->
        <Card>
          <CardHeader>
            <CardTitle>Buttons</CardTitle>
            <CardDescription>Componentes Button do shadcn-vue com cor primária roxa</CardDescription>
          </CardHeader>
          <CardContent class="flex flex-wrap gap-3">
            <Button>Primary</Button>
            <Button variant="secondary">Secondary</Button>
            <Button variant="outline">Outline</Button>
            <Button variant="ghost">Ghost</Button>
            <Button variant="destructive">Destructive</Button>
          </CardContent>
        </Card>

        <!-- Input -->
        <Card>
          <CardHeader>
            <CardTitle>Input</CardTitle>
            <CardDescription>Campo de texto com Tailwind CSS</CardDescription>
          </CardHeader>
          <CardContent class="flex flex-col gap-3">
            <Input placeholder="Digite algo..." type="text" />
            <Input placeholder="Valor monetário..." type="number" />
          </CardContent>
        </Card>
      </div>

      <!-- Área principal do app -->
      <div class="mt-8">
        <RouterView />
      </div>
    </div>
  </div>
</template>
