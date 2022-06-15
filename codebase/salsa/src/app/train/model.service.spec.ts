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
import { ITable } from '../tables/table.interface';

import { IModelCreate, IModelUpdate, ITabularModel } from './model.interface';
import { ModelService } from './model.service';

describe('ModelService', () => {
  const params = { page: 1, page_size: 5 };
  let service: ModelService,
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
        ModelService,
      ],
    });
    spyOn(TestBed.get(EventService), 'filter').and.callFake(() => {
      return Observable.of({
        data: {
          target: {},
        },
      });
    });
    service = TestBed.get(ModelService);
    executeSpy = spyOn(AppHttp, 'execute').and.callFake((data) => Observable.of(data)).and.callThrough();
  });

  afterEach(() => {
    executeSpy.calls.reset();
  });

  it('should return list of models', inject([AppHttp], (http: AppHttp) => {
    const httpGetSpy = spyOn(http, 'get').and.callFake(() => {
      return Observable.of({ count: 0, data: [] });
    });
    service.list(params).subscribe();
    expect(httpGetSpy.calls.count()).toBe(1);
    expect(httpGetSpy.calls.mostRecent().args[0]).toEqual('models');
    expect(httpGetSpy.calls.mostRecent().args[1]).toEqual(params);
  }));

  it('should get model', inject([SharedResourceService], (sharedResourceService: SharedResourceService) => {
    const data: ITabularModel = {
      id: 'modelId',
      ownerId: 'ownerId1',
      name: 'CVModel Name',
      updated: '2017-04-06T03:15:23.570Z',
      created: '2017-04-06T03:15:23.570Z',
      status: ITabularModel.Status.ACTIVE,
      class: ITabularModel.Class.CLASSIFICATION,
      responseColumn: {
        name: 'all_pre_rt_psa_responded_to_therapy',
        displayName: 'all_pre_rt_psa_responded_to_therapy',
        dataType: ITable.ColumnDataType.INTEGER,
        variableType: ITable.ColumnVariableType.CATEGORICAL,
      },
      predictorColumns: [
        {
          name: 'ethnicity',
          displayName: 'ethnicity',
          dataType: ITable.ColumnDataType.STRING,
          variableType: ITable.ColumnVariableType.CATEGORICAL,
        },
      ],
    };
    const accessSharedResourceSpy = spyOn(sharedResourceService, 'withSharedAccess').and.callFake(() => {
      return {
        get: () => Observable.of(data),
      };
    });
    service.get('modelId').subscribe();

    expect(accessSharedResourceSpy.calls.count()).toBe(1);
    expect(accessSharedResourceSpy.calls.mostRecent().args).toEqual(['MODEL', 'modelId']);
  }));

  it('should create model', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpPostSpy = spyOn(http, 'post').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IModelCreate = {
      name: 'Model Name',
      input: 'tableId',
      outputTableName: 'output table name',
      responseColumn: {
        name: 'all_pre_rt_psa_responded_to_therapy',
        displayName: 'all_pre_rt_psa_responded_to_therapy',
        dataType: ITable.ColumnDataType.INTEGER,
        variableType: ITable.ColumnVariableType.CATEGORICAL,
      },
      predictorColumns: [
        {
          name: 'ethnicity',
          displayName: 'ethnicity',
          dataType: ITable.ColumnDataType.STRING,
          variableType: ITable.ColumnVariableType.CATEGORICAL,
        },
      ],
    };
    service.create(data).subscribe();
    expect(httpPostSpy.calls.count()).toBe(1);
    expect(httpPostSpy.calls.mostRecent().args[0]).toEqual('models');
    expect(httpPostSpy.calls.mostRecent().args[1]).toEqual(data);

    expect(executeSpy.calls.count()).toBe(1);
    expect(executeSpy.calls.mostRecent().args[0]).toEqual(Observable.of(data));

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.UPDATE_MODEL_LIST]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['Model created: ' + data.name]);
  }));

  it('should update model', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpPutSpy = spyOn(http, 'put').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IModelUpdate = {
      name: 'updated Model Name',
    };
    service.update('modelId', data).subscribe();
    expect(httpPutSpy.calls.count()).toBe(1);
    expect(httpPutSpy.calls.mostRecent().args[0]).toEqual('models/modelId');
    expect(httpPutSpy.calls.mostRecent().args[1]).toEqual(data);

    expect(executeSpy.calls.count()).toBe(1);
    expect(executeSpy.calls.mostRecent().args[0]).toEqual(Observable.of(data));

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_MODEL_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.UPDATE_MODEL, {id: 'modelId'}]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['Model updated: ' + data.name]);
  }));

  it('should delete model', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpDeleteSpy = spyOn(http, 'delete').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: ITabularModel = {
      id: 'modelId',
      ownerId: 'ownerId1',
      name: 'CVModel Name',
      updated: '2017-04-06T03:15:23.570Z',
      created: '2017-04-06T03:15:23.570Z',
      status: ITabularModel.Status.ACTIVE,
      class: ITabularModel.Class.REGRESSION,
      responseColumn: {
        name: 'all_pre_rt_psa_responded_to_therapy',
        displayName: 'all_pre_rt_psa_responded_to_therapy',
        dataType: ITable.ColumnDataType.INTEGER,
        variableType: ITable.ColumnVariableType.CATEGORICAL,
      },
      predictorColumns: [
        {
          name: 'ethnicity',
          displayName: 'ethnicity',
          dataType: ITable.ColumnDataType.STRING,
          variableType: ITable.ColumnVariableType.CATEGORICAL,
        },
      ],
    };
    service.delete(data).subscribe();
    expect(httpDeleteSpy.calls.count()).toBe(1);
    expect(httpDeleteSpy.calls.mostRecent().args).toEqual(['models/' + data.id]);

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_MODEL_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.DELETE_MODEL, {id: data.id}]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['Model deleted: ' + data.name]);
  }));
});
