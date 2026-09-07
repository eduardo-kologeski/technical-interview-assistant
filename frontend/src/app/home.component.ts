import { Component, inject } from '@angular/core';
import { CompanyService } from './company.service';
import { DashboardComponent } from './dashboard.component';
import { PersonalDashboardComponent } from './personal-dashboard.component';

@Component({
  selector: 'app-home',
  imports: [DashboardComponent, PersonalDashboardComponent],
  template: `@if (companies.activeCompany()) { <app-dashboard /> } @else { <app-personal-dashboard /> }`,
})
export class HomeComponent { readonly companies = inject(CompanyService); }
