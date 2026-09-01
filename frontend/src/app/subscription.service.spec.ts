import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController,provideHttpClientTesting} from '@angular/common/http/testing';
import {TestBed} from '@angular/core/testing';
import {SubscriptionService} from './subscription.service';

describe('SubscriptionService',()=>{
  beforeEach(()=>TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]}));
  it('loads the active company subscription without payment data',()=>{
    const service=TestBed.inject(SubscriptionService),http=TestBed.inject(HttpTestingController);
    service.current().subscribe(result=>{
      expect(result).not.toBeNull();
      if(!result)return;
      expect(result.planCode).toBe('STARTER');
      expect(result.usage.activeVacanciesLimit).toBe(25);
      expect((result as unknown as Record<string,unknown>)['paymentMethod']).toBeUndefined();
    });
    const request=http.expectOne('/api/subscription');
    expect(request.request.method).toBe('GET');
    request.flush({planCode:'STARTER',planName:'Starter',status:'TRIALING',trialEndsAt:'2026-09-30T00:00:00Z',trialDaysRemaining:29,usage:{activeVacancies:2,activeVacanciesLimit:25,members:4,membersLimit:10,monthlyInterviews:3,monthlyInterviewsLimit:100},entitlements:{'vacancy.create':true}});
    http.verify();
  });
});
