import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'animals' },
  {
    path: 'animals',
    title: 'Animals · Zoo Management System',
    loadComponent: () =>
      import('./features/animals/animal-list/animal-list').then((m) => m.AnimalList),
  },
  {
    path: 'animals/:id',
    title: 'Animal · Zoo Management System',
    loadComponent: () =>
      import('./features/animals/animal-detail/animal-detail').then((m) => m.AnimalDetail),
  },
  { path: '**', redirectTo: 'animals' },
];
