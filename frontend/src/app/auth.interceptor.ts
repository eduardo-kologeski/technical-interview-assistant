import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const withToken = (token: string | null) => {
    const headers: Record<string, string> = {};
    if (token) headers['Authorization'] = `Bearer ${token}`;
    const companyId = localStorage.getItem('tia.companyId');
    if (companyId) headers['X-Company-Id'] = companyId;
    return Object.keys(headers).length ? request.clone({ setHeaders: headers }) : request;
  };
  return next(withToken(auth.accessToken())).pipe(catchError(error => {
    const isAuthEndpoint = request.url.includes('/api/auth/');
    if (!(error instanceof HttpErrorResponse) || error.status !== 401 || isAuthEndpoint || !auth.refreshToken()) return throwError(() => error);
    return auth.refresh().pipe(
      switchMap(token => next(withToken(token))),
      catchError(refreshError => { auth.clear(); router.navigateByUrl('/auth/login'); return throwError(() => refreshError); })
    );
  }));
};
