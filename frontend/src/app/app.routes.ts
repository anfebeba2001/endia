import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/home/home.component').then((m) => m.HomeComponent),
    title: 'Endia — Learn anything',
  },
  {
    path: 'subtopics',
    loadComponent: () =>
      import('./pages/subtopics/subtopics.component').then((m) => m.SubtopicsComponent),
    title: 'Endia — Choose subtopics',
  },
  {
    path: 'snacks',
    loadComponent: () =>
      import('./pages/snacks/snacks.component').then((m) => m.SnacksComponent),
    title: 'Endia — Snacks',
  },
  {
    path: 'quiz',
    loadComponent: () =>
      import('./pages/quiz/quiz.component').then((m) => m.QuizComponent),
    title: 'Endia — Quiz',
  },
  {
    path: 'results',
    loadComponent: () =>
      import('./pages/results/results.component').then((m) => m.ResultsComponent),
    title: 'Endia — Results',
  },
  {
    path: '**',
    redirectTo: '',
  },
];
