import { describe, it, expect } from 'vitest'
import { Tag } from 'lucide-vue-next'
import { categoryColorLabel, categoryIcon, categoryIconLabel } from '../categoryIcons'
import { cleanCategoryName } from '../categoryName'

// Cópia de CategoryOptions.ICONS e CategoryOptions.COLORS (backend). Mantenha as três listas em sincronia.
const BACKEND_ICONS = [
  'shield-check',
  'house',
  'utensils',
  'car',
  'heart-pulse',
  'shuffle',
  'party-popper',
  'graduation-cap',
  'shirt',
  'repeat',
  'landmark',
  'trending-up',
  'piggy-bank',
  'shopping-cart',
  'paw-print',
  'plane',
  'gift',
  'smartphone',
  'zap',
  'fuel',
  'dumbbell',
  'baby',
  'coffee',
  'wallet',
  'folder',
]

const BACKEND_COLORS = [
  '#2563EB',
  '#3B82F6',
  '#0EA5E9',
  '#14B8A6',
  '#10B981',
  '#059669',
  '#F59E0B',
  '#F97316',
  '#EF4444',
  '#EC4899',
  '#8B5CF6',
  '#9333EA',
  '#6366F1',
  '#64748B',
]

describe('categoryIcons', () => {
  it('mapeia os 25 ícones do backend para um componente próprio e um nome em pt-BR', () => {
    expect(BACKEND_ICONS).toHaveLength(25)
    expect(BACKEND_ICONS.filter((icon) => categoryIcon(icon) === Tag)).toEqual([])
    expect(BACKEND_ICONS.filter((icon) => categoryIconLabel(icon) === icon)).toEqual([])
  })

  it('nome desconhecido cai no ícone Tag', () => {
    expect(categoryIcon('skull')).toBe(Tag)
  })

  it('dá um nome em pt-BR a cada uma das 14 cores da paleta, sem repetir', () => {
    expect(BACKEND_COLORS).toHaveLength(14)
    const labels = BACKEND_COLORS.map(categoryColorLabel)
    expect(labels.filter((label) => label.startsWith('#'))).toEqual([])
    expect(new Set(labels).size).toBe(14)
    expect(categoryColorLabel('#f97316')).toBe('Laranja')
  })
})

describe('cleanCategoryName', () => {
  it('normaliza para NFC e tira espaços e invisíveis das pontas', () => {
    expect(cleanCategoryName('​﻿ Café  ')).toBe('Café')
    expect(cleanCategoryName('​﻿')).toBe('')
  })
})
