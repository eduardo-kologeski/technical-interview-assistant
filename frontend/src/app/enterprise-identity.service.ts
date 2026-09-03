import {HttpClient} from '@angular/common/http';
import {Injectable,inject} from '@angular/core';

export interface EnterpriseIdentityReadiness {
  ready:boolean;
  protocol:'OIDC'|'SAML'|null;
  blockers:{code:string;message:string}[];
}

@Injectable({providedIn:'root'})
export class EnterpriseIdentityService {
  private readonly http=inject(HttpClient);
  readiness(){return this.http.get<EnterpriseIdentityReadiness>('/api/enterprise-identity/readiness')}
}
