import {
  ApplicationConfig,
  APP_INITIALIZER,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';

import { routes } from './app.routes';
import { ThemePreferenceService } from './services/theme-preference.service';
import { MAX_POLLING_WINDOW_MS } from './services/batch.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(),
    // FR-010 configuration seam: polling is bounded to 120 s by default. Deployments can
    // override this provider; E2E tests set `window.__MAX_POLLING_WINDOW_MS__` before boot.
    {
      provide: MAX_POLLING_WINDOW_MS,
      useFactory: () => {
        const override = (globalThis as unknown as Record<string, unknown>)[
          '__MAX_POLLING_WINDOW_MS__'
        ];
        return typeof override === 'number' && override > 0 ? override : 120_000;
      },
    },
    {
      provide: APP_INITIALIZER,
      useFactory: (themePref: ThemePreferenceService) => () =>
        new Promise<void>((resolve) => {
          // Constructing the service applies the persisted theme on boot (FR-012).
          themePref.theme;
          resolve();
        }),
      deps: [ThemePreferenceService],
      multi: true,
    },
  ],
};
