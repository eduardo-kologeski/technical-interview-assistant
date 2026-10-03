import { HttpInterceptorFn } from '@angular/common/http';
import { InjectionToken, inject } from '@angular/core';

export const API_ORIGIN = new InjectionToken<string>('API_ORIGIN', {
  providedIn: 'root',
  factory: () => (window as Window & { TIA_CONFIG?: { apiOrigin: string } }).TIA_CONFIG?.apiOrigin ?? '',
});

// Runs after authentication, so tenant/auth path checks still see relative /api URLs.
export const apiOriginInterceptor: HttpInterceptorFn = (request, next) => {
  const origin = inject(API_ORIGIN);
  return next(origin && request.url.startsWith('/api/')
    ? request.clone({ url: origin + request.url }) : request);
};
