import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TestBed, inject } from '@angular/core/testing';

import { Observable } from 'rxjs/Observable';

import { IListRequest } from '../core/interfaces/common.interface';
import { EventService } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';
import { ProcessService } from '../core/services/process.service';
import { SharedResourceService } from '../core/services/shared-resource.service';
import { EventServiceMock } from '../mocks/event.service.mock';
import { AppHttpMock } from '../mocks/http.mock';
import { NotificationServiceMock } from '../mocks/notification.service.mock';
import { ProcessServiceMock } from '../mocks/process.service.mock';
import { SharedResourceServiceMock } from '../mocks/shared-resource.service.mock';

import { PipelineService } from './pipeline.service';

describe('PipelineService', () => {
  const params: IListRequest = {
    page: 1,
    page_size: 5,
  };

  let service: PipelineService,
  executeSpy: jasmine.Spy;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [
        HttpClientTestingModule,
      ],
      providers: [
        {
          provide: NotificationService,
          useClass: NotificationServiceMock,
        },
        {
          provide: EventService,
          useClass: EventServiceMock,
        },
        {
          provide: AppHttp,
          useClass: AppHttpMock,
        },
        {
          provide: ProcessService,
          useClass: ProcessServiceMock,
        },
        {
          provide: SharedResourceService,
          useClass: SharedResourceServiceMock,
        },
        PipelineService,
      ],
    });

    executeSpy = spyOn(AppHttp, 'execute').and.callFake((data) => Observable.of(data)).and.callThrough();
    service = TestBed.get(PipelineService);
  });

  afterEach(() => {
    executeSpy.calls.reset();
  });

  it('should use request parameters when calling listOperators', inject([AppHttp], (http: AppHttp) => {
    const httpGetSpy = spyOn(http, 'get').and.callThrough();
    service.listOperators(params).subscribe();
    expect(httpGetSpy.calls.count()).toBe(1);
    expect(httpGetSpy.calls.mostRecent().args.slice(0, 2)).toEqual(['pipeline-operators', params]);
  }));

  it('should use default parameters when calling listOperators', inject([AppHttp], (http: AppHttp) => {
    const httpGetSpy = spyOn(http, 'get').and.callThrough();
    const defaultParams = {
      page: 1,
      page_size: 1000,
    };
    service.listOperators().subscribe();
    expect(httpGetSpy.calls.count()).toBe(1);
    expect(httpGetSpy.calls.mostRecent().args.slice(0, 2)).toEqual(['pipeline-operators', defaultParams]);
  }));

});
