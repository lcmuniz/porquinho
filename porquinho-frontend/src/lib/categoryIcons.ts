import type { Component } from 'vue'
import {
  Car,
  GraduationCap,
  HeartPulse,
  House,
  Landmark,
  PartyPopper,
  PiggyBank,
  Repeat,
  ShieldCheck,
  Shirt,
  Shuffle,
  Tag,
  TrendingUp,
  Utensils,
} from 'lucide-vue-next'

/**
 * Story 3.4: nome do ícone Lucide (kebab-case, como vem da API) → componente do lucide-vue-next.
 * Só os ícones usados pelas categorias; um nome desconhecido cai no ícone genérico Tag.
 */
const ICONS: Record<string, Component> = {
  car: Car,
  'graduation-cap': GraduationCap,
  'heart-pulse': HeartPulse,
  house: House,
  landmark: Landmark,
  'party-popper': PartyPopper,
  'piggy-bank': PiggyBank,
  repeat: Repeat,
  'shield-check': ShieldCheck,
  shirt: Shirt,
  shuffle: Shuffle,
  tag: Tag,
  'trending-up': TrendingUp,
  utensils: Utensils,
}

export function categoryIcon(name: string): Component {
  return ICONS[name] ?? Tag
}
