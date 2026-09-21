import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withViewTransitions } from '@angular/router';
import { provideClientHydration } from '@angular/platform-browser';
import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { AnimalApi } from './core/data/animal-api';
import { HttpAnimalApi } from './core/data/http-animal-api';
import { MockAnimalApi } from './core/data/mock-animal-api';
import { DemoSession } from './core/session/demo-session';
import { Session } from './core/session/session';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding(), withViewTransitions({ skipInitialTransition: true })),
    provideClientHydration(),
    { provide: AnimalApi, useClass: environment.live ? HttpAnimalApi : MockAnimalApi },
    // Overridden by keycloakProviders() in the browser when live. The server keeps the
    // demo session so `App` can render the shell; the platform guard in AnimalStore
    // means it never issues a request with it.
    { provide: Session, useClass: DemoSession },
  ],
};
