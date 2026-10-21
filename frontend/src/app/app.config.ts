import { provideHttpClient, withFetch } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // withFetch(): usa la API Fetch en lugar de XHR. Es lo que recomienda
    // Angular desde la v18 y evita la capa extra de zone.js en el cliente.
    provideHttpClient(withFetch()),
  ],
};
