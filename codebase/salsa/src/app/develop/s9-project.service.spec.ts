import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TestBed, inject } from '@angular/core/testing';

import { Observable } from 'rxjs/Observable';

import { IListRequest } from '../core/interfaces/common.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';
import { ProcessService } from '../core/services/process.service';
import { SharedResourceService } from '../core/services/shared-resource.service';
import { EventServiceMock } from '../mocks/event.service.mock';
import { AppHttpMock } from '../mocks/http.mock';
import { NotificationServiceMock } from '../mocks/notification.service.mock';
import { ProcessServiceMock } from '../mocks/process.service.mock';
import { SharedResourceServiceMock } from '../mocks/shared-resource.service.mock';

import { IS9Project, IS9ProjectCreate, IS9ProjectUpdate } from './s9-project.interfaces';
import { S9ProjectService } from './s9-project.service';

interface IS9ProjectFileCreate {
  filePath: string;
  data: string;
}

interface IS9ProjectFileUpdate extends IS9ProjectFileCreate {
  lastKnownModifiedTime: string;
}

describe('S9ProjectService', () => {
  const params: IListRequest = {
    page: 1,
    page_size: 5,
  };

  let service: S9ProjectService,
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
        S9ProjectService,
      ],
    });

    executeSpy = spyOn(AppHttp, 'execute').and.callFake((data) => Observable.of(data)).and.callThrough();
    service = TestBed.get(S9ProjectService);
  });

  afterEach(() => {
    executeSpy.calls.reset();
  });

  it('should return a list of s9 projects ', inject([AppHttp], (http: AppHttp) => {
    const httpGetSpy = spyOn(http, 'get').and.callThrough();
    service.list(params).subscribe();
    expect(httpGetSpy.calls.count()).toBe(1);
    expect(httpGetSpy.calls.mostRecent().args).toEqual(['s9-projects', params]);
  }));

  it('should get a s9 project', inject([AppHttp], (http: AppHttp) => {
    const httpGetSpy = spyOn(http, 'get').and.callThrough();
    service.get('s9ProjectId123').subscribe();
    expect(httpGetSpy.calls.count()).toBe(1);
    expect(httpGetSpy.calls.mostRecent().args).toEqual(['s9-projects/s9ProjectId123']);
  }));

  it('should create a s9 project', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify) => {
    const httpPostSpy = spyOn(http, 'post').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const createNotificationSpy = spyOn(notify, 'create');
    const data: IS9ProjectCreate = {
      name: 'new S9 Project',
      description: 'Description of a new S9 Project',
    };

    service.create({...data}).subscribe();

    expect(httpPostSpy.calls.count()).toBe(1);
    expect(httpPostSpy.calls.mostRecent().args).toEqual(['s9-projects', data]);

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_S9_PROJECT_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.CREATE_S9_PROJECT, data]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['S9 Project has been created: ' + data.name]);
  }));


  it('should update a s9 project', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify) => {
    const httpPutSpy = spyOn(http, 'put').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const createNotificationSpy = spyOn(notify, 'create');
    const data: IS9ProjectUpdate = {
      name: 'updated S9 Project',
      description: 'Description of the updated S9 Project',
    };

    const s9ProjectId = 's9ProjectId123';
    service.update(s9ProjectId, {...data}).subscribe();

    expect(httpPutSpy.calls.count()).toBe(1);
    expect(httpPutSpy.calls.mostRecent().args).toEqual(['s9-projects/s9ProjectId123', data]);

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_S9_PROJECT_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.UPDATE_S9_PROJECT, { id: s9ProjectId }]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['S9 Project has been updated: ' + data.name]);
  }));

  it('should update file content' , inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify) => {
    const httpPutSpy = spyOn(http, 'put').and.callThrough();
    const createNotificationSpy = spyOn(notify, 'create');
    const fileData: IS9ProjectFileUpdate = {
      filePath: 'url',
      data: 'File Updated',
      lastKnownModifiedTime: JSON.stringify({
        headers: {
          'If-Unmodified-Since': new Date('2019-05-13T09:21:22Z').toUTCString(),
        },
      }),
    };

    const s9ProjectFileId = 's9ProjectFileId123';
    const updatedData: string = 'File Updated';
    const lastKnownModifiedTime = '2019-05-13T09:21:22Z';
    service.updateFileContent(s9ProjectFileId, 'url', updatedData, lastKnownModifiedTime).subscribe();

    expect(httpPutSpy.calls.count()).toBe(1);
    expect(httpPutSpy.calls.mostRecent().args).toEqual(['s9-projects/s9ProjectFileId123/files/url', fileData.data, {}, JSON.parse(fileData.lastKnownModifiedTime)]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['S9 Project file has been updated: ' + fileData.filePath]);
  }));

  it('should create file content' , inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify) => {
    const httpPutSpy = spyOn(http, 'put').and.callThrough();
    const createNotificationSpy = spyOn(notify, 'create');
    const fileData: IS9ProjectFileCreate = {
      filePath: 'url',
      data: 'File Created',
    };

    const s9ProjectFileId = 's9ProjectFileId123';
    const updatedData: string = 'File Created';
    service.createFileContent(s9ProjectFileId, 'url', updatedData).subscribe();

    expect(httpPutSpy.calls.count()).toBe(1);
    expect(httpPutSpy.calls.mostRecent().args).toEqual(['s9-projects/s9ProjectFileId123/files/url', fileData.data, {}]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['S9 Project file has been created: ' + fileData.filePath]);
  }));

  it('should delete a s9 project', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify) => {
    const httpDeleteSpy = spyOn(http, 'delete').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const createNotificationSpy = spyOn(notify, 'create');
    const data: IS9Project = {
      id: 's9ProjectId123',
      ownerId: 'ownerId1',
      updated: '2019-02-18T08:01:03.471Z',
      created: '2019-02-18T08:01:03.471Z',
      name: 'S9 Project to delete',
      description: 'Description of S9 Project',
      status: IS9Project.Status.IDLE,
    };

    service.delete({...data}).subscribe();

    expect(httpDeleteSpy.calls.count()).toBe(1);
    expect(httpDeleteSpy.calls.mostRecent().args).toEqual(['s9-projects/s9ProjectId123']);

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_S9_PROJECT_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.DELETE_S9_PROJECT, {id: data.id}]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args).toEqual(['S9 Project has been deleted: ' + data.name]);
 }));

 it('getActiveProcess should not call ProcessesService.getByTarget when status is IDLE', inject([ProcessService], (processService: ProcessService) => {
    const processSpy = spyOn(processService, 'getByTarget').and.callThrough();

    const data: IS9Project = {
      id: 's9ProjectId123',
      ownerId: 'ownerId1',
      updated: '2019-02-18T08:01:03.471Z',
      created: '2019-02-18T08:01:03.471Z',
      name: 'S9 Project to delete',
      description: 'Description of S9 Project',
      status: IS9Project.Status.IDLE,
    };

    service.getActiveProcess(data).subscribe();
    expect(processSpy.calls.count()).toBe(0);
 }));

 it('should encode filePath when creating and updating file content' , inject([AppHttp], (http: AppHttp) => {
   const httpPutSpy = spyOn(http, 'put').and.callThrough();
   const filePath: string = 'directory()1 newDirectory@1/subdirectory#1/file 1';
   const expectedFilePath = 's9-projects/s9ProjectId123/files/directory()1%20newDirectory%401/subdirectory%231/file%201';

   service.createFileContent('s9ProjectId123', filePath, '123').subscribe();

   expect(httpPutSpy.calls.count()).toBe(1);
   expect(httpPutSpy.calls.mostRecent().args[0]).toEqual(expectedFilePath);

   service.updateFileContent('s9ProjectId123', filePath, '123').subscribe();

   expect(httpPutSpy.calls.count()).toBe(2);
   expect(httpPutSpy.calls.mostRecent().args[0]).toEqual(expectedFilePath);
 }));
});
