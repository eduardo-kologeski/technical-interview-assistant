import {TestBed} from '@angular/core/testing';
import {HttpTestingController,provideHttpClientTesting} from '@angular/common/http/testing';
import {provideHttpClient} from '@angular/common/http';
import {EnterpriseIdentityService} from './enterprise-identity.service';

describe('EnterpriseIdentityService',()=>{
  let service:EnterpriseIdentityService;
  let http:HttpTestingController;
  beforeEach(()=>{
    TestBed.configureTestingModule({providers:[provideHttpClient(),provideHttpClientTesting()]});
    service=TestBed.inject(EnterpriseIdentityService);
    http=TestBed.inject(HttpTestingController);
  });
  afterEach(()=>http.verify());

  it('reads only the administrative readiness status',()=>{
    const response={ready:false,protocol:null,blockers:[{code:'FEATURE_DISABLED',message:'Desativado'}]};
    service.readiness().subscribe(value=>expect(value).toEqual(response));
    http.expectOne('/api/enterprise-identity/readiness').flush(response);
  });
});
