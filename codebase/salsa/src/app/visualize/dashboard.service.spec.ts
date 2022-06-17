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

import { IDashboard, IDashboardCreate } from './dashboard.interface';
import { DashboardService } from './dashboard.service';

describe('DashboardService', () => {
  const params = { page: 1, page_size: 5 };
  let service: DashboardService,
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
        DashboardService,
      ],
    });
    service = TestBed.get(DashboardService);
    executeSpy = spyOn(AppHttp, 'execute').and.callFake((data) => Observable.of(data)).and.callThrough();
  });

  afterEach(() => {
    executeSpy.calls.reset();
  });

  it('should return list of dashboards', inject([AppHttp], (http: AppHttp) => {
    const httpGetSpy = spyOn(http, 'get').and.callThrough();
    service.list(params).subscribe();
    expect(httpGetSpy.calls.count()).toBe(1);
    expect(httpGetSpy.calls.mostRecent().args).toEqual(['dashboards', params]);
  }));

  it('should create dashboard', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpPostSpy = spyOn(http, 'post').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IDashboardCreate = {
      name: 'Dashboard',
      layout: null,
      widgets: [],
      crossFilters: [],
    };
    service.create(data).subscribe();
    expect(httpPostSpy.calls.count()).toBe(1);
    expect(httpPostSpy.calls.mostRecent().args).toEqual(['dashboards', data]);

    expect(executeSpy.calls.count()).toBe(1);
    expect(executeSpy.calls.mostRecent().args[0]).toEqual(Observable.of(data));

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.UPDATE_DASHBOARD_LIST]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['Dashboard created: ' + data.name]);
  }));

  it('should get dashboard', inject([AppHttp], (http: AppHttp) => {
    const httpGetSpy = spyOn(http, 'get').and.callThrough();
    service.get('dashboardId').subscribe();

    expect(httpGetSpy.calls.count()).toBe(1);
    expect(httpGetSpy.calls.mostRecent().args).toEqual(['dashboards/dashboardId']);
  }));


  it('should update dashboard', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpPutSpy = spyOn(http, 'put').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IDashboardCreate = {
      name: 'Dashboard',
      layout: null,
      widgets: [],
      crossFilters: [],
    };
    service.update('dashboardId', data).subscribe();
    expect(httpPutSpy.calls.count()).toBe(1);
    expect(httpPutSpy.calls.mostRecent().args).toEqual(['dashboards/dashboardId', data]);

    expect(executeSpy.calls.count()).toBe(1);
    expect(executeSpy.calls.mostRecent().args[0]).toEqual(Observable.of(data));

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_DASHBOARD_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.UPDATE_DASHBOARD, {id: 'dashboardId'}]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['Dashboard updated: ' + data.name]);
  }));

  it('should delete dashboard', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpDeleteSpy = spyOn(http, 'delete').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IDashboard = {
      name: 'Dashboard',
      layout: null,
      widgets: [],
      crossFilters: [],
      status: IDashboard.Status.IDLE,
      id: 'modelId',
      ownerId: 'ownerId1',
      updated: '2017-04-06T03:15:23.570Z',
      created: '2017-04-06T03:15:23.570Z',
    };
    service.delete(data).subscribe();
    expect(httpDeleteSpy.calls.count()).toBe(1);
    expect(httpDeleteSpy.calls.mostRecent().args).toEqual(['dashboards/' + data.id]);

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_DASHBOARD_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.DELETE_DASHBOARD, {id: data.id}]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['Dashboard deleted: ' + data.name]);
  }));
});
