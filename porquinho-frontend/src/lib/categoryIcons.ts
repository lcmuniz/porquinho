import type { Component } from 'vue'
import {
  Baby,
  Car,
  Coffee,
  Dumbbell,
  Fuel,
  Gift,
  GraduationCap,
  HeartPulse,
  House,
  Landmark,
  PartyPopper,
  PawPrint,
  PiggyBank,
  Plane,
  Repeat,
  ShieldCheck,
  Shirt,
  ShoppingCart,
  Shuffle,
  Smartphone,
  Tag,
  TrendingUp,
  Utensils,
  Wallet,
  Zap,
} from 'lucide-vue-next'

/**
 * Stories 3.4 e 3.5: nome do ícone Lucide (kebab-case, como vem da API) → componente do lucide-vue-next.
 * Cobre a lista de `GET /api/v1/categories/options`; um nome desconhecido cai no ícone genérico Tag.
 * Mantenha em sincronia com CategoryOptions.java (ICONS e COLORS) no backend: ao mudar um, mude o outro e a
 * lista em `lib/__tests__/categoryIcons.spec.ts`.
 */
const ICONS: Record<string, Component> = {
  baby: Baby,
  car: Car,
  coffee: Coffee,
  dumbbell: Dumbbell,
  fuel: Fuel,
  gift: Gift,
  'graduation-cap': GraduationCap,
  'heart-pulse': HeartPulse,
  house: House,
  landmark: Landmark,
  'party-popper': PartyPopper,
  'paw-print': PawPrint,
  'piggy-bank': PiggyBank,
  plane: Plane,
  repeat: Repeat,
  'shield-check': ShieldCheck,
  shirt: Shirt,
  'shopping-cart': ShoppingCart,
  shuffle: Shuffle,
  smartphone: Smartphone,
  tag: Tag,
  'trending-up': TrendingUp,
  utensils: Utensils,
  wallet: Wallet,
  zap: Zap,
}

/** Nomes em pt-BR para o leitor de tela na escolha do ícone. */
const ICON_LABELS: Record<string, string> = {
  baby: 'Bebê',
  car: 'Carro',
  coffee: 'Café',
  dumbbell: 'Academia',
  fuel: 'Combustível',
  gift: 'Presente',
  'graduation-cap': 'Formatura',
  'heart-pulse': 'Saúde',
  house: 'Casa',
  landmark: 'Banco',
  'party-popper': 'Festa',
  'paw-print': 'Pet',
  'piggy-bank': 'Cofrinho',
  plane: 'Avião',
  repeat: 'Recorrente',
  'shield-check': 'Escudo',
  shirt: 'Roupa',
  'shopping-cart': 'Carrinho de compras',
  shuffle: 'Variável',
  smartphone: 'Celular',
  tag: 'Etiqueta',
  'trending-up': 'Investimento',
  utensils: 'Talheres',
  wallet: 'Carteira',
  zap: 'Energia',
}

/** Nomes em pt-BR das cores da paleta, para o leitor de tela na escolha da cor. */
const COLOR_LABELS: Record<string, string> = {
  '#2563EB': 'Azul royal',
  '#3B82F6': 'Azul',
  '#0EA5E9': 'Azul-celeste',
  '#14B8A6': 'Verde-azulado',
  '#10B981': 'Verde-esmeralda',
  '#059669': 'Verde-escuro',
  '#F59E0B': 'Âmbar',
  '#F97316': 'Laranja',
  '#EF4444': 'Vermelho',
  '#EC4899': 'Rosa',
  '#8B5CF6': 'Violeta',
  '#9333EA': 'Roxo',
  '#6366F1': 'Índigo',
  '#64748B': 'Cinza',
}

export function categoryIcon(name: string): Component {
  return ICONS[name] ?? Tag
}

export function categoryIconLabel(name: string): string {
  return ICON_LABELS[name] ?? name
}

export function categoryColorLabel(hex: string): string {
  return COLOR_LABELS[hex.toUpperCase()] ?? hex
}
