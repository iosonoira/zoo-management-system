import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  // Client-rendered: the roster needs a token in live mode, and prerendering it in
  // demo mode would bake a snapshot of the mock into the HTML.
  { path: 'animals', renderMode: RenderMode.Client },
  { path: '**', renderMode: RenderMode.Server },
];
