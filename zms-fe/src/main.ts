import { mergeApplicationConfig } from '@angular/core';
import { bootstrapApplication } from '@angular/platform-browser';
import { environment } from './environments/environment';
import { appConfig } from './app/app.config';
import { App } from './app/app';

async function browserConfig() {
  if (!environment.live) {
    return appConfig;
  }
  // Imported lazily so the demo bundle never pulls in keycloak-js.
  const { keycloakProviders } = await import('./app/core/auth/keycloak-providers');
  return mergeApplicationConfig(appConfig, { providers: keycloakProviders() });
}

browserConfig()
  .then((config) => bootstrapApplication(App, config))
  .catch((err) => console.error(err));
