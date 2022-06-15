import { TestBed, inject } from '@angular/core/testing';

import * as _ from 'lodash';
import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';

import config from '../config';
import { IBackendList } from '../core/interfaces/common.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';
import { ProcessService } from '../core/services/process.service';
import { SharedResourceService } from '../core/services/shared-resource.service';
import { StorageService } from '../core/services/storage.service';
import { flows as fixtureFlows } from '../fixture/fixtures/flows';
import { EventServiceMock } from '../mocks/event.service.mock';
import { AppHttpMock } from '../mocks/http.mock';
import { NotificationServiceMock } from '../mocks/notification.service.mock';
import { SharedResourceServiceMock } from '../mocks/shared-resource.service.mock';
import { StorageServiceMock } from '../mocks/storage.service.mock';
import { ITable } from '../tables/table.interface';

import {
  IBackendFlow,
  IBackendFlowstep,
  IFlow,
  IFlowCreate,
  IFlowUpdate,
  IFlowstep,
} from './flow.interface';
import { FlowService } from './flow.service';

describe('FlowService', () => {
  const params = { page: 1, page_size: 5 };
  let service: FlowService,
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
        FlowService,
      ],
    });
    service = TestBed.get(FlowService);
    executeSpy = spyOn(AppHttp, 'execute').and.callFake((data) => Observable.of(data)).and.callThrough();
  });

  afterEach(() => {
    executeSpy.calls.reset();
  });

  it('should return list of flows', inject([AppHttp], (http: AppHttp) => {
    const httpGetSpy = spyOn(http, 'get').and.callFake(() => {
      return Observable.of({ count: 0, data: [] });
    });
    service.list(params).subscribe();
    expect(httpGetSpy.calls.count()).toBe(1);
    expect(httpGetSpy.calls.mostRecent().args[0]).toEqual('flows');
    expect(httpGetSpy.calls.mostRecent().args[1]).toEqual(params);
  }));

  it('Should getMyFlows', inject([AppHttp], (http: AppHttp) => {
    const deserializedFlowsList = (<IBackendFlow[]> fixtureFlows.data).map(flow => FlowService._deserializeFlow(_.cloneDeep(flow)));

    const httpGetSpy = spyOn(http, 'get').and.callFake(() => {
      return Observable.of({ count: deserializedFlowsList.length, data: deserializedFlowsList });
    });

    const flowsGotSpy = jasmine.createSpy('flowsGotSpy', () => {
      expect(service.data.flows).toEqual(deserializedFlowsList);
    }).and.callThrough();

    service.getMyFlows().subscribe(flowsGotSpy);

    expect(httpGetSpy).toHaveBeenCalledTimes(1);
    expect(flowsGotSpy).toHaveBeenCalledTimes(1);
    expect(httpGetSpy.calls.mostRecent().args[0]).toEqual('flows');
  }));

  it('should get flow', inject([SharedResourceService], (sharedResourceService: SharedResourceService) => {
    const setHierarchyLinkSpy = spyOn(sharedResourceService, 'setHierarchyLink');
    const data: IBackendFlow = {
      id: 'flowId',
      ownerId: 'ownerId1',
      name: 'Flow Name',
      updated: '2017-04-06T03:15:23.570Z',
      created: '2017-04-06T03:15:23.570Z',
      status: IFlow.Status.DONE,
      description: 'description',
      inLibrary: true,
      tables: ['tableId1', 'tableId2'],
      steps: [{
        id: '1bc51d0c-e0cc-4af9-a020-3430a08bb989',
        name: 'Step1',
        input: ['tableId1'],
        transformer: {
          newColName: 'Decile',
          aggregator: 'ntile',
          orderBy: ['bad_predicted'],
          isDesc: false,
          ntileGroupsCount: 10,
          transformerType: IBackendFlowstep.Type.WINDOW,
        },
        output: 'tableId2',
        status: IFlowstep.Status.DONE,
        updated: '2017-04-06T03:15:23.570Z',
      }],
    };
    const accessSharedResourceSpy = spyOn(sharedResourceService, 'withSharedAccess').and.callFake(() => {
      return {
        get: () => Observable.of(data),
      };
    });
    service.get('flowId').subscribe();

    expect(accessSharedResourceSpy.calls.count()).toBe(1);
    expect(accessSharedResourceSpy.calls.mostRecent().args).toEqual(['FLOW', 'flowId']);

    expect(setHierarchyLinkSpy.calls.count()).toBe(2);
    expect(setHierarchyLinkSpy.calls.first().args).toEqual(['TABLE', 'tableId1', 'FLOW', 'flowId']);
    expect(setHierarchyLinkSpy.calls.mostRecent().args).toEqual(['TABLE', 'tableId2', 'FLOW', 'flowId']);
  }));

  it('should return /getTables a flow', inject([AppHttp, SharedResourceService], (http: AppHttp, sharedResourceService: SharedResourceService) => {
    const response: IBackendList<ITable> = {
      count: 1,
      data: <ITable[]> [
         {
          id: 'tableId1',
          name: 'table2',
          ownerId: 'ownerId1',
          datasetId: 'datasetId1',
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
        {
          id: 'tableId2',
          name: 'table1',
          ownerId: 'ownerId1',
          datasetId: 'datasetId12',
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
          datasetType: ITable.DatasetType.SOURCE,
        },
      ],
    };
    const accessSharedResourceSpy = spyOn(sharedResourceService, 'withSharedAccess').and.callFake(() => {
      return {
        get: () => Observable.of(response),
      };
    });
    const setHierarchyLinkSpy = spyOn(sharedResourceService, 'setHierarchyLink');
    service.getTables('flowId').subscribe();

    expect(accessSharedResourceSpy.calls.count()).toBe(1);
    expect(accessSharedResourceSpy.calls.mostRecent().args).toEqual(['FLOW', 'flowId']);

    expect(setHierarchyLinkSpy.calls.count()).toBe(2);
    expect(setHierarchyLinkSpy.calls.first().args).toEqual(['TABLE', 'tableId1', 'FLOW', 'flowId']);
    expect(setHierarchyLinkSpy.calls.mostRecent().args).toEqual(['TABLE', 'tableId2', 'FLOW', 'flowId']);
  }));

  it('should create flow', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpPostSpy = spyOn(http, 'post').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IFlowCreate = {
      name: 'Flow Name',
      description: 'Description',
    };
    service.create(data).subscribe();
    expect(httpPostSpy.calls.count()).toBe(1);
    expect(httpPostSpy.calls.mostRecent().args[0]).toEqual('flows');
    expect(httpPostSpy.calls.mostRecent().args[1]).toEqual(data);

    expect(executeSpy.calls.count()).toBe(1);
    expect(executeSpy.calls.mostRecent().args[0]).toEqual(Observable.of(data));

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.UPDATE_FLOW_LIST]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['Flow successfully created: ' + data.name]);
  }));

  it('should update flow', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const data: IFlowUpdate = {
      id: 'flowId',
      name: 'updated Flow Name',
      description: 'New Description',
    };
    const createNotificationSpy = spyOn(notify, 'create');
    const httpPutSpy = spyOn(http, 'put').and.callFake(() => Observable.of(data));
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    service.update(data).subscribe();
    expect(httpPutSpy.calls.count()).toBe(1);
    expect(httpPutSpy.calls.mostRecent().args[0]).toEqual('flows/flowId');
    expect(httpPutSpy.calls.mostRecent().args[1]).toEqual(data);

    expect(executeSpy.calls.count()).toBe(1);
    expect(executeSpy.calls.mostRecent().args[0]).toEqual(Observable.of(data));

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_FLOW, 'flowId']);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.UPDATE_FLOW_LIST]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['Flow updated: ' + data.name]);
  }));

  it('should delete model', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpDeleteSpy = spyOn(http, 'delete').and.callFake(() => Observable.of({ id: 'flowId' }));
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IBackendFlow = {
      id: 'flowId',
      ownerId: 'ownerId1',
      name: 'Flow Name',
      updated: '2017-04-06T03:15:23.570Z',
      created: '2017-04-06T03:15:23.570Z',
      status: IFlow.Status.DONE,
      description: 'description',
      inLibrary: true,
      tables: ['tableId1', 'tableId2'],
      steps: [{
        id: '1bc51d0c-e0cc-4af9-a020-3430a08bb989',
        name: 'Step1',
        input: ['tableId1'],
        transformer: {
          newColName: 'Decile',
          aggregator: 'ntile',
          orderBy: ['bad_predicted'],
          isDesc: false,
          ntileGroupsCount: 10,
          transformerType: IBackendFlowstep.Type.WINDOW,
        },
        output: 'tableId2',
        status: IFlowstep.Status.DONE,
        updated: '2017-04-06T03:15:23.570Z',
      }],
    };
    service.delete(data).subscribe();
    expect(httpDeleteSpy.calls.count()).toBe(1);
    expect(httpDeleteSpy.calls.mostRecent().args).toEqual(['flows/' + data.id]);

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_FLOW_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.DELETE_FLOW, {id: data.id}]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['Flow deleted: ' + data.name]);
  }));

  it('exportUrl', () => {
    const url = service.exportUrl('testid', 'testtoken');
    expect(url).toBe(config.api.base + 'flows/' + 'testid' + '/export' + '?access_token=' + 'testtoken');
  });
});

