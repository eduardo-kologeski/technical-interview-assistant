import { Routes } from '@angular/router';
import { authGuard } from './auth.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./landing-page.component').then((module) => module.LandingPageComponent),
  },
  {
    path: 'setup',
    loadComponent: () =>
      import('./setup-page.component').then((module) => module.SetupPageComponent),
  },
  {
    path: 'invites/:token',
    loadComponent: () =>
      import('./invitation-page.component').then((module) => module.InvitationPageComponent),
  },
  {
    path: 'verify-email/:token',
    loadComponent: () =>
      import('./identity-page.component').then((module) => module.IdentityPageComponent),
  },
  {
    path: 'reset-password/:token',
    loadComponent: () =>
      import('./identity-page.component').then((module) => module.IdentityPageComponent),
  },
  {
    path: 'auth/forgot-password',
    loadComponent: () =>
      import('./identity-page.component').then((module) => module.IdentityPageComponent),
  },
  {
    path: 'auth/login',
    loadComponent: () => import('./auth-page.component').then((module) => module.AuthPageComponent),
  },
  {
    path: 'auth/register',
    loadComponent: () => import('./auth-page.component').then((module) => module.AuthPageComponent),
  },
  {
    path: '',
    loadComponent: () => import('./shell.component').then((module) => module.ShellComponent),
    canActivate: [authGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () => import('./home.component').then((module) => module.HomeComponent),
      },
      {
        path: 'assessments/new',
        loadComponent: () =>
          import('./personal-assessment-create.component').then(
            (module) => module.PersonalAssessmentCreateComponent,
          ),
      },
      {
        path: 'assessments/:id/report',
        loadComponent: () =>
          import('./personal-assessment-report.component').then(
            (module) => module.PersonalAssessmentReportComponent,
          ),
      },
      {
        path: 'assessments/:id',
        loadComponent: () =>
          import('./personal-assessment-collection.component').then(
            (module) => module.PersonalAssessmentCollectionComponent,
          ),
      },
      {
        path: 'job-positions',
        loadComponent: () =>
          import('./job-positions.component').then((module) => module.JobPositionsComponent),
      },
      { path: 'interviewers', redirectTo: 'interviews' },
      { path: 'profile', redirectTo: 'interviews' },
      {
        path: 'company',
        loadComponent: () =>
          import('./company-admin.component').then((module) => module.CompanyAdminComponent),
      },
      {
        path: 'interviews/new',
        loadComponent: () => import('./app').then((module) => module.InterviewPage),
      },
      {
        path: 'interviews/:id',
        loadComponent: () => import('./app').then((module) => module.InterviewPage),
      },
      {
        path: 'interviews',
        loadComponent: () =>
          import('./interviews-workspace.component').then(
            (module) => module.InterviewsWorkspaceComponent,
          ),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
