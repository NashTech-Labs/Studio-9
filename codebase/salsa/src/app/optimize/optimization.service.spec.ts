import { TestBed, inject } from '@angular/core/testing';

import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';

import { EventService, IEvent } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';
import { ProcessService } from '../core/services/process.service';
import { SharedResourceService } from '../core/services/shared-resource.service';
import { StorageService } from '../core/services/storage.service';
import { EventServiceMock } from '../mocks/event.service.mock';
import { AppHttpMock } from '../mocks/http.mock';
import { NotificationServiceMock } from '../mocks/notification.service.mock';
import { SharedResourceServiceMock } from '../mocks/shared-resource.service.mock';
import { StorageServiceMock } from '../mocks/storage.service.mock';

import { IOptimization, IOptimizationCreate, IOptimizationUpdate } from './optimization.interface';
import { OptimizationService } from './optimization.service';

describe('OptimizationService', () => {
  const params = { page: 1, page_size: 5 };
  let service: OptimizationService,
    executeSpy: jasmine.Spy;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: AppHttp,
          useClass: AppHttpMock,
        },
        {
          provide: StorageService,
          useClass: StorageServiceMock,
        },
        {
          provide: NotificationService,
          useClass: NotificationServiceMock,
        },
        {
          provide: SharedResourceService,
          useClass: SharedResourceServiceMock,
        },
        {
          provide: EventService,
          useClass: EventServiceMock,
        },
        ProcessService,
        OptimizationService,
      ],
    });
    service = TestBed.get(OptimizationService);
    executeSpy = spyOn(AppHttp, 'execute').and.callFake((data) => Observable.of(data)).and.callThrough();
  });

  afterEach(() => {
    executeSpy.calls.reset();
  });

  it('should return list of optimizations', inject([AppHttp], (http: AppHttp) => {
    const httpGetSpy = spyOn(http, 'get').and.callFake(() => {
      return Observable.of({ count: 0, data: [] });
    });
    service.list(params).subscribe();
    expect(httpGetSpy.calls.count()).toBe(1);
    expect(httpGetSpy.calls.mostRecent().args[0]).toEqual('optimizations');
    expect(httpGetSpy.calls.mostRecent().args[1]).toEqual(params);
  }));

  it('should create optimization', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpPostSpy = spyOn(http, 'post').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IOptimizationCreate = {
      modelId: 'modelId',
      name: 'Optimization Name',
      optimizationType: IOptimization.OptimizationType.OBJECTIVE_FUNCTION,
      outputModelName: 'Output model Name',
      objectiveFlowId: 'flowId',
      objectives: [],
      constraints: [],
      input: 'foo',
      columnMappings: [],
    };
    service.create(data).subscribe();
    expect(httpPostSpy.calls.count()).toBe(1);
    expect(httpPostSpy.calls.mostRecent().args[0]).toEqual('optimizations');
    expect(httpPostSpy.calls.mostRecent().args[1]).toEqual(data);

    expect(executeSpy.calls.count()).toBe(1);
    expect(executeSpy.calls.mostRecent().args[0]).toEqual(Observable.of(data));

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.UPDATE_OPTIMIZATION_LIST]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['Optimization created: ' + data.name]);
  }));

  it('should get optimization', inject([SharedResourceService], (sharedResourceService: SharedResourceService) => {
    const data: IOptimization = {
      id: 'modelId',
      ownerId: 'ownerId1',
      modelId: 'modelId',
      name: 'Optimization Name',
      optimizationType: IOptimization.OptimizationType.OBJECTIVE_FUNCTION,
      outputModelName: 'Output model Name',
      objectiveFlowId: 'flowId',
      objectives: [],
      constraints: [],
      updated: '2017-04-06T03:15:23.570Z',
      created: '2017-04-06T03:15:23.570Z',
      status: IOptimization.OptimizationStatus.DONE,
      outputModelId: 'outputModelId',
      input: 'foo',
      columnMappings: [],
    };
    const accessSharedResourceSpy = spyOn(sharedResourceService, 'withSharedAccess').and.callFake(() => {
      return {
        get: () => Observable.of(data),
      };
    });
    service.get('optimizationId').subscribe();

    expect(accessSharedResourceSpy.calls.count()).toBe(1);
    expect(accessSharedResourceSpy.calls.mostRecent().args).toEqual(['OPTIMIZATION', 'optimizationId']);
  }));


  it('should update optimization', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpPutSpy = spyOn(http, 'put').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IOptimizationUpdate = {
      name: 'updated Optimization Name',
    };
    service.update('optimizationId', data).subscribe();
    expect(httpPutSpy.calls.count()).toBe(1);
    expect(httpPutSpy.calls.mostRecent().args).toEqual(['optimizations/optimizationId', data]);

    expect(executeSpy.calls.count()).toBe(1);
    expect(executeSpy.calls.mostRecent().args[0]).toEqual(Observable.of(data));

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_OPTIMIZATION_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.UPDATE_OPTIMIZATION, {id: 'optimizationId'}]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['Optimization updated: ' + data.name]);
  }));

  it('should delete optimization', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpDeleteSpy = spyOn(http, 'delete').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IOptimization = {
      id: 'modelId',
      ownerId: 'ownerId1',
      modelId: 'modelId',
      name: 'Optimization Name',
      optimizationType: IOptimization.OptimizationType.OBJECTIVE_FUNCTION,
      outputModelName: 'Output model Name',
      objectiveFlowId: 'flowId',
      objectives: [],
      constraints: [],
      updated: '2017-04-06T03:15:23.570Z',
      created: '2017-04-06T03:15:23.570Z',
      status: IOptimization.OptimizationStatus.DONE,
      outputModelId: 'outputModelId',
      input: 'foo',
      columnMappings: [],
    };
    service.delete(data).subscribe();
    expect(httpDeleteSpy.calls.count()).toBe(1);
    expect(httpDeleteSpy.calls.mostRecent().args).toEqual(['optimizations/' + data.id]);

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_OPTIMIZATION_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.DELETE_OPTIMIZATION, {id: 'modelId'}]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['Optimization deleted: ' + data.name]);
  }));
});
