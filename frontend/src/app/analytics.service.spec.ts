import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AnalyticsService } from './analytics.service';

describe('AnalyticsService',()=>{
  beforeEach(()=>TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]}));
  it('sends operational filters and consumes the stable aggregate contract',()=>{
    const service=TestBed.inject(AnalyticsService),http=TestBed.inject(HttpTestingController);
    service.operational({from:'2026-08-01',to:'2026-08-31',status:'COMPLETED',vacancyId:'vacancy-1',vacancyPage:2,size:8})
      .subscribe(result=>expect(result.summary.total).toBe(7));
    const request=http.expectOne(item=>item.url==='/api/analytics/operational');
    expect(request.request.params.get('from')).toBe('2026-08-01');
    expect(request.request.params.get('status')).toBe('COMPLETED');
    expect(request.request.params.get('vacancyPage')).toBe('2');
    request.flush({from:'2026-08-01',to:'2026-08-31',summary:{total:7,inProgress:0,completed:7,averageMinutes:42,completionRate:100},byStatus:[{status:'COMPLETED',total:7}],byVacancy:{content:[],page:2,size:8,totalElements:0,totalPages:0,first:false,last:true},byInterviewer:{content:[],page:0,size:8,totalElements:0,totalPages:0,first:true,last:true}});
    http.verify();
  });
});
