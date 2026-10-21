import { Routes } from '@angular/router';

/**
 * Rutas de la aplicacion.
 *
 * <p>Cada ruta carga su pagina de forma perezosa: el bundle inicial solo
 * contiene el shell y el dashboard, que es lo que se ve al entrar.
 */
export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'dashboard',
  },
  {
    path: 'dashboard',
    title: 'CodeQuest · Panel',
    loadComponent: () => import('./pages/dashboard/dashboard').then((m) => m.Dashboard),
  },
  {
    path: 'katas',
    title: 'CodeQuest · Retos',
    loadComponent: () => import('./pages/katas/katas').then((m) => m.Katas),
  },
  {
    path: 'katas/:id',
    title: 'CodeQuest · Reto',
    loadComponent: () => import('./pages/kata-detail/kata-detail').then((m) => m.KataDetail),
  },
  {
    path: 'quizzes',
    title: 'CodeQuest · Conceptos',
    loadComponent: () => import('./pages/quizzes/quizzes').then((m) => m.Quizzes),
  },
  {
    path: 'design',
    title: 'CodeQuest · Diseno',
    loadComponent: () => import('./pages/design/design').then((m) => m.Design),
  },
  {
    path: 'design/:id',
    title: 'CodeQuest · Diseno',
    loadComponent: () => import('./pages/design-detail/design-detail').then((m) => m.DesignDetail),
  },
  {
    path: '**',
    redirectTo: 'dashboard',
  },
];
