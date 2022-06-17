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

import { IPrediction, IPredictionCreate, IPredictionStatus } from './prediction.interface';
import { PredictionService } from './prediction.service';

describe('PredictionService', () => {
  const params = { page: 1, page_size: 5 };
  let service: PredictionService,
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
        PredictionService,
      ],
    });
    service = TestBed.get(PredictionService);
    executeSpy = spyOn(AppHttp, 'execute').and.callFake((data) => Observable.of(data)).and.callThrough();
  });

  afterEach(() => {
    executeSpy.calls.reset();
  });

  it('should return list of predictions', inject([AppHttp], (http: AppHttp) => {
    const httpGetSpy = spyOn(http, 'get').and.callFake(() => {
      return Observable.of({ count: 0, data: [] });
    });
    service.list(params).subscribe();
    expect(httpGetSpy.calls.count()).toBe(1);
    expect(httpGetSpy.calls.mostRecent().args[0]).toEqual('predictions');
    expect(httpGetSpy.calls.mostRecent().args[1]).toEqual(params);
  }));

  it('should create prediction', inject([AppHttp, EventService], (http: AppHttp, eventService: EventService) => {
    const httpPostSpy = spyOn(http, 'post').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IPredictionCreate = {
      modelId: 'modelId',
      name: 'Prediction Name',
      outputTableName: 'Output Table Name',
      input: 'tableId',
      columnMappings: [],
    };
    service.create(data).subscribe();
    expect(httpPostSpy.calls.count()).toBe(1);
    expect(httpPostSpy.calls.mostRecent().args[0]).toEqual('predictions');
    expect(httpPostSpy.calls.mostRecent().args[1]).toEqual(data);

    expect(executeSpy.calls.count()).toBe(1);
    expect(executeSpy.calls.mostRecent().args[0]).toEqual(Observable.of(data));

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.UPDATE_PREDICTION_LIST]);

  }));

  it('should get prediction', inject([AppHttp], (http: AppHttp) => {
    const data: IPrediction = {
      id: 'modelId',
      ownerId: 'ownerId1',
      modelId: 'modelId',
      name: 'Prediction Name',
      output: 'OutputTableId',
      input: 'tableId',
      columnMappings: [],
      status: IPredictionStatus.DONE,
      updated: '2017-04-06T03:15:23.570Z',
      created: '2017-04-06T03:15:23.570Z',
    };
    const httpGetSpy = spyOn(http, 'get').and.callFake(() => {
      return Observable.of(data);
    });
    service.get('predictionId').subscribe();

    expect(httpGetSpy.calls.count()).toBe(1);
    expect(httpGetSpy.calls.mostRecent().args[0]).toEqual('predictions/predictionId');
  }));

  it('should remove prediction', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpDeleteSpy = spyOn(http, 'delete').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    service.remove('predictionId').subscribe();
    expect(httpDeleteSpy.calls.count()).toBe(1);
    expect(httpDeleteSpy.calls.mostRecent().args).toEqual(['predictions/predictionId']);

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_PREDICTION_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.DELETE_PREDICTION, {id: 'predictionId'}]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['Prediction deleted.']);
  }));
});
