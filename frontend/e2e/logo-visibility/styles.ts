import type { Page } from '@playwright/test';

/** Read the computed theme state + header/brand styles for assertion. */
export async function readHeaderStyles(page: Page) {
  return page.evaluate(() => {
    const header = document.querySelector('.app-shell__header');
    const brand = document.querySelector('.brand__name');
    const toggle = document.querySelector('.theme-toggle');
    const dot = document.querySelector('.brand__dot');
    const dotBox = dot ? dot.getBoundingClientRect() : null;
    return {
      htmlTheme: document.documentElement.dataset.theme,
      headerBg: header ? getComputedStyle(header).backgroundColor : null,
      logoColor: brand ? getComputedStyle(brand).color : null,
      toggleText: toggle ? (toggle.textContent || '').trim() : null,
      toggleAria: toggle ? toggle.getAttribute('aria-label') : null,
      dotRendered: !!dotBox && dotBox.width > 0 && dotBox.height > 0,
    };
  });
}
