import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { CompanyService } from './company.service';
import { map, of, switchMap } from 'rxjs';
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const companies = inject(CompanyService);
  return auth.ensureSession().pipe(switchMap(authenticated => authenticated
    ? companies.load().pipe(map(() => true))
    : of(router.createUrlTree(['/auth/login']))));
};
