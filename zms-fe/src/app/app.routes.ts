import { CanActivateFn, GuardResult, Routes } from '@angular/router';
import { environment } from '../environments/environment';

/** Live mode only: the demo build has nobody to sign in. */
const guards: CanActivateFn[] = environment.live
  ? [
      (route, state) =>
        import('./core/auth/keycloak-providers').then(
          (m) => m.animalRouteGuard(route, state) as Promise<GuardResult>,
        ),
    ]
  : [];

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'animals' },
  {
    path: 'animals',
    title: 'Animals · Zoo Management System',
    canActivate: guards,
    loadComponent: () =>
      import('./features/animals/animal-list/animal-list').then((m) => m.AnimalList),
  },
  {
    path: 'animals/:id',
    title: 'Animal · Zoo Management System',
    canActivate: guards,
    loadComponent: () =>
      import('./features/animals/animal-detail/animal-detail').then((m) => m.AnimalDetail),
  },
  { path: '**', redirectTo: 'animals' },
];
