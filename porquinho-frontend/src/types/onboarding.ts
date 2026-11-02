/** Progresso do onboarding guiado de 8 passos (Story 3.1). */
export interface OnboardingProgress {
  currentStep: number
  /** Ex.: ['tutorial_viewed'], ['tutorial_skipped', 'accounts_added'] */
  stepsCompleted: string[]
  completed: boolean
}
