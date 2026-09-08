import {HttpClient} from '@angular/common/http';
import {Injectable,inject} from '@angular/core';

export interface SubscriptionSnapshot {
  planCode:string;
  planName:string;
  status:'TRIALING'|'ACTIVE'|'PAST_DUE'|'CANCELED';
  trialEndsAt:string|null;
  trialDaysRemaining:number;
  usage:{
    activeVacancies:number; activeVacanciesLimit:number;
    members:number; membersLimit:number;
    monthlyInterviews:number; monthlyInterviewsLimit:number;
  };
  entitlements:Record<string,boolean>;
}

@Injectable({providedIn:'root'})
export class SubscriptionService {
  private readonly http=inject(HttpClient);
  current(){return this.http.get<SubscriptionSnapshot|null>('/api/subscription')}
}
