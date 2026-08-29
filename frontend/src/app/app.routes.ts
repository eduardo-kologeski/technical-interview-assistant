import { Routes } from '@angular/router';
import { AuthPageComponent } from './auth-page.component';
import { authGuard } from './auth.guard';
import { DashboardComponent } from './dashboard.component';
import { InterviewPage } from './app';
import { JobPositionsComponent } from './job-positions.component';
import { ShellComponent } from './shell.component';
import { ProfileComponent } from './profile.component';
import { InterviewersComponent } from './interviewers.component';

export const routes: Routes = [
  { path: 'auth/login', component: AuthPageComponent },
  { path: 'auth/register', component: AuthPageComponent },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', component: DashboardComponent },
      { path: 'job-positions', component: JobPositionsComponent },
      { path: 'interviewers', component: InterviewersComponent },
      { path: 'profile', component: ProfileComponent },
      { path: 'interviews/:id', component: InterviewPage },
      { path: 'interviews', component: InterviewPage },
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
