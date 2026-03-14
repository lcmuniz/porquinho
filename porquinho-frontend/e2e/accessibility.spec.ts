import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

test.describe('Accessibility (WCAG 2.1 AA)', () => {
  test('design system page has no critical a11y violations', async ({ page }) => {
    await page.goto('/design-system')
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze()
    expect(results.violations).toEqual([])
  })

  test('home page has no critical a11y violations', async ({ page }) => {
    await page.goto('/')
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze()
    expect(results.violations).toEqual([])
  })

  test('design system page has skip link as first focusable element', async ({ page }) => {
    await page.goto('/design-system')
    await page.keyboard.press('Tab')
    const focused = await page.evaluate(() => {
      const el = document.activeElement
      return {
        tagName: el?.tagName.toLowerCase(),
        href: el?.getAttribute('href'),
        className: el?.className,
      }
    })
    expect(focused.tagName).toBe('a')
    expect(focused.href).toBe('#main-content')
    expect(focused.className).toContain('skip-link')
  })

  test('buttons on design system page meet minimum touch target height', async ({ page }) => {
    await page.goto('/design-system')
    const buttons = page.locator('button')
    const count = await buttons.count()
    const checkCount = Math.min(count, 5)

    for (let i = 0; i < checkCount; i++) {
      const box = await buttons.nth(i).boundingBox()
      expect(box).not.toBeNull()
      expect(box!.height).toBeGreaterThanOrEqual(44)
    }
  })
})
