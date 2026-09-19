import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  { path: 'animals', renderMode: RenderMode.Prerender },
  { path: '**', renderMode: RenderMode.Server },
];
