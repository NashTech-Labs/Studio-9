import { HttpClientTestingModule } from '@angular/common/http/testing';
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

import { ICVPredictionCreate } from './cv-prediction.interface';
import { CVPredictionService } from './cv-prediction.service';

describe('CVPredictionService', () => {
  const params = { page: 1, page_size: 5 };
  let service: CVPredictionService,
    executeSpy: jasmine.Spy;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [
        HttpClientTestingModule,
      ],
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
        CVPredictionService,
      ],
    });
    service = TestBed.get(CVPredictionService);
    executeSpy = spyOn(AppHttp, 'execute').and.callFake((data) => Observable.of(data)).and.callThrough();
  });

  afterEach(() => {
    executeSpy.calls.reset();
  });

  it('should return list of cv-prediction', inject([AppHttp], (http: AppHttp) => {
    const httpGetSpy = spyOn(http, 'get').and.callThrough();
    service.list(params).subscribe();
    expect(httpGetSpy.calls.count()).toBe(1);
    expect(httpGetSpy.calls.mostRecent().args).toEqual(['cv-predictions', params]);
  }));

  it('should create cv-prediction', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpPostSpy = spyOn(http, 'post').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: ICVPredictionCreate = {
      modelId: 'modelId',
      name: 'Prediction Name',
      input: 'albumId',
      outputAlbumName: 'Output Album Name',
    };
    service.create(data).subscribe();
    expect(httpPostSpy.calls.count()).toBe(1);
    expect(httpPostSpy.calls.mostRecent().args).toEqual(['cv-predictions', data]);

    expect(executeSpy.calls.count()).toBe(1);
    expect(executeSpy.calls.mostRecent().args[0]).toEqual(Observable.of(data));

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.UPDATE_CV_PREDICTION_LIST]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['CV Prediction created: ' + data.name]);
  }));

  it('should get cv-prediction', inject([AppHttp], (http: AppHttp) => {
    const httpGetSpy = spyOn(http, 'get').and.callThrough();
    service.get('predictionId').subscribe();
    expect(httpGetSpy.calls.count()).toBe(1);
    expect(httpGetSpy.calls.mostRecent().args).toEqual(['cv-predictions/predictionId']);
  }));

  it('should remove cv-prediction', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpDeleteSpy = spyOn(http, 'delete').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    service.remove('predictionId').subscribe();
    expect(httpDeleteSpy.calls.count()).toBe(1);
    expect(httpDeleteSpy.calls.mostRecent().args).toEqual(['cv-predictions/' + 'predictionId']);

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_CV_PREDICTION_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.DELETE_CV_PREDICTION, {id: 'predictionId'}]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['CV Prediction deleted.']);
  }));
});
