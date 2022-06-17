import { TestBed, inject } from '@angular/core/testing';

import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';

import { IBackendList } from '../core/interfaces/common.interface';
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

import { ITable, ITableUpdate } from './table.interface';
import { TableService } from './table.service';

describe('TableService', () => {
  const params = { page: 1, page_size: 5 };
  let service: TableService,
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
        TableService,
      ],
    });
    service = TestBed.get(TableService);
    executeSpy = spyOn(AppHttp, 'execute').and.callFake((data) => Observable.of(data)).and.callThrough();
  });

  afterEach(() => {
    executeSpy.calls.reset();
  });

  it('should return list of tables', inject([AppHttp], (http: AppHttp) => {
    const response: IBackendList<ITable> = {
      count: 1,
      data: [
        <ITable> {
          id: 'prostate_cancer',
          name: 'all_cohort_For_DEMO_2',
          ownerId: 'ownerId1',
          datasetId: 'prostate_cancer',
          columns: [{
            name: 'patientid',
            displayName: 'patientid',
            dataType: ITable.ColumnDataType.STRING,
            variableType: ITable.ColumnVariableType.CATEGORICAL,
            columnType: 'ATTRIBUTE',
            align: ITable.ColumnAlign.LEFT,
          }],
          status: ITable.Status.ACTIVE,
          created: '2017-03-24T13:57:29.522Z',
          updated: '2017-03-24T13:57:29.522Z',
          datasetType: ITable.DatasetType.DERIVED,
        },
      ],
    };
    const httpGetSpy = spyOn(http, 'get').and.callFake(() => {
      return Observable.of(response);
    });
    service.list(params).subscribe();
    expect(httpGetSpy.calls.count()).toBe(1);
    expect(httpGetSpy.calls.mostRecent().args[0]).toEqual('tables');
    expect(httpGetSpy.calls.mostRecent().args[1]).toEqual(params);
  }));

  it('should get table', inject([SharedResourceService], (sharedResourceService: SharedResourceService) => {
    const table: ITable = {
      id: 'prostate_cancer',
      name: 'all_cohort_For_DEMO_2',
      ownerId: 'ownerId1',
      datasetId: 'prostate_cancer',
      columns: [{
        name: 'patientid',
        displayName: 'patientid',
        dataType: ITable.ColumnDataType.STRING,
        variableType: ITable.ColumnVariableType.CATEGORICAL,
        columnType: 'ATTRIBUTE',
        align: ITable.ColumnAlign.LEFT,
      }],
      status: ITable.Status.ACTIVE,
      created: '2017-03-24T13:57:29.522Z',
      updated: '2017-03-24T13:57:29.522Z',
      datasetType: ITable.DatasetType.DERIVED,
    };
    const accessSharedResourceSpy = spyOn(sharedResourceService, 'withSharedAccess').and.callFake(() => {
      return {
        get: () => Observable.of(table),
      };
    });
    service.get('tableId').subscribe();

    expect(accessSharedResourceSpy.calls.count()).toBe(1);
    expect(accessSharedResourceSpy.calls.mostRecent().args).toEqual(['TABLE', 'tableId']);
  }));

  it('should update table', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpPutSpy = spyOn(http, 'put').and.callThrough();
    const data: ITableUpdate = {
      name: 'updated Table Name',
      columns: [],
    };
    service.update('tableId', data).subscribe();
    expect(httpPutSpy.calls.count()).toBe(1);
    expect(httpPutSpy.calls.mostRecent().args[0]).toEqual('tables/tableId');
    expect(httpPutSpy.calls.mostRecent().args[1]).toEqual(data);

    expect(executeSpy.calls.count()).toBe(1);
    expect(executeSpy.calls.mostRecent().args[0]).toEqual(Observable.of(data));

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['Table updated: ' + data.name]);
  }));

  it('should delete table', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpDeleteSpy = spyOn(http, 'delete').and.callFake((...args) => Observable.of(...args));
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const table: ITable = {
      id: 'prostate_cancer',
      name: 'all_cohort_For_DEMO_2',
      ownerId: 'ownerId1',
      datasetId: 'prostate_cancer',
      columns: [{
        name: 'patientid',
        displayName: 'patientid',
        dataType: ITable.ColumnDataType.STRING,
        variableType: ITable.ColumnVariableType.CATEGORICAL,
        columnType: 'ATTRIBUTE',
        align: ITable.ColumnAlign.LEFT,
      }],
      status: ITable.Status.ACTIVE,
      created: '2017-03-24T13:57:29.522Z',
      updated: '2017-03-24T13:57:29.522Z',
      datasetType: ITable.DatasetType.DERIVED,
    };
    service.delete(table).subscribe();
    expect(httpDeleteSpy.calls.count()).toBe(1);
    expect(httpDeleteSpy.calls.mostRecent().args).toEqual(['tables/' + table.id]);

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_TABLE_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.DELETE_TABLE, {id: 'prostate_cancer'}]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['Table deleted: ' + table.name]);
  }));
});
