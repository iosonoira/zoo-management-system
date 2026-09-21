import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withViewTransitions } from '@angular/router';
import { provideClientHydration } from '@angular/platform-browser';
import { routes } from './app.routes';
import { AnimalApi } from './core/data/animal-api';
import { MockAnimalApi } from './core/data/mock-animal-api';
import { DemoSession } from './core/session/demo-session';
import { Session } from './core/session/session';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding(), withViewTransitions({ skipInitialTransition: true })),
    provideClientHydration(),
    { provide: AnimalApi, useClass: MockAnimalApi },
    { provide: Session, useClass: DemoSession },
  ],
};
