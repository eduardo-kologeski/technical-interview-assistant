import { Routes } from '@angular/router';
import { AuthPageComponent } from './auth-page.component';
import { authGuard } from './auth.guard';
import { DashboardComponent } from './dashboard.component';
import { InterviewPage } from './app';
import { JobPositionsComponent } from './job-positions.component';
import { ShellComponent } from './shell.component';
import { ProfileComponent } from './profile.component';
import { InterviewersComponent } from './interviewers.component';
import { LandingPageComponent } from './landing-page.component';
import { InvitationPageComponent } from './invitation-page.component';
import { CompanyAdminComponent } from './company-admin.component';
import { InterviewsWorkspaceComponent } from './interviews-workspace.component';

export const routes: Routes = [
  { path: '', pathMatch: 'full', component: LandingPageComponent },
  { path: 'invites/:token', component: InvitationPageComponent },
  { path: 'auth/login', component: AuthPageComponent },
  { path: 'auth/register', component: AuthPageComponent },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', component: DashboardComponent },
      { path: 'job-positions', component: JobPositionsComponent },
      { path: 'interviewers', redirectTo: 'interviews' },
      { path: 'profile', redirectTo: 'interviews' },
      { path: 'company', component: CompanyAdminComponent },
      { path: 'interviews/new', component: InterviewPage },
      { path: 'interviews/:id', component: InterviewPage },
      { path: 'interviews', component: InterviewsWorkspaceComponent },
    ],
  },
  { path: '**', redirectTo: '' },
];
