import { Injectable } from '@angular/core';

export type UiTheme = 'light' | 'dark';

const STORAGE_KEY = 'endia-ui-theme';

/**
 * Reads/writes the UI theme preference (FR-011/012). Applies it by setting
 * {@code document.documentElement.dataset.theme}. Defaults to light and silently
 * degrades (no crash) when localStorage is blocked.
 */
@Injectable({ providedIn: 'root' })
export class ThemePreferenceService {
  private current: UiTheme = 'light';

  constructor() {
    this.applyInitial();
  }

  get theme(): UiTheme {
    return this.current;
  }

  toggle(): UiTheme {
    const next = this.current === 'light' ? 'dark' : 'light';
    this.set(next);
    return next;
  }

  set(theme: UiTheme): void {
    this.current = theme;
    this.apply(theme);
    this.persist(theme);
  }

  private applyInitial(): void {
    const stored = this.read();
    const theme: UiTheme = stored === 'dark' ? 'dark' : 'light';
    this.current = theme;
    this.apply(theme);
  }

  private apply(theme: UiTheme): void {
    if (typeof document !== 'undefined') {
      document.documentElement.dataset['theme'] = theme;
    }
  }

  private read(): string | null {
    try {
      return window.localStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }
  }

  private persist(theme: UiTheme): void {
    try {
      window.localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      // Silently degrade when storage is blocked (spec Assumption).
    }
  }
}
