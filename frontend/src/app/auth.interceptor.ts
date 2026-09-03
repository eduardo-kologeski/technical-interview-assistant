import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const withToken = (token: string | null) => token ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : request;
  return next(withToken(auth.accessToken())).pipe(catchError(error => {
    const isAuthEndpoint = /\/auth\/(login|register|refresh|logout)(?:[/?#]|$)/.test(request.url);
    if (!(error instanceof HttpErrorResponse) || error.status !== 401 || isAuthEndpoint || !auth.refreshToken()) return throwError(() => error);
    return auth.refresh().pipe(
      switchMap(token => next(withToken(token))),
      catchError(refreshError => { auth.clear(); router.navigateByUrl('/auth/login'); return throwError(() => refreshError); })
    );
  }));
};
