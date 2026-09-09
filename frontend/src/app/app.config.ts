import { ApplicationConfig, APP_INITIALIZER, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';

import { routes } from './app.routes';
import { ThemePreferenceService } from './services/theme-preference.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(),
    {
      provide: APP_INITIALIZER,
      useFactory: (themePref: ThemePreferenceService) => () => new Promise<void>((resolve) => {
        // Constructing the service applies the persisted theme on boot (FR-012).
        themePref.theme;
        resolve();
      }),
      deps: [ThemePreferenceService],
      multi: true,
    },
  ],
};
