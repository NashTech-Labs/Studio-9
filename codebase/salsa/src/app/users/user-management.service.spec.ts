import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TestBed, inject } from '@angular/core/testing';

import { Observable } from 'rxjs/Observable';

import { IListRequest } from '../core/interfaces/common.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';
import { EventServiceMock } from '../mocks/event.service.mock';
import { AppHttpMock } from '../mocks/http.mock';
import { NotificationServiceMock } from '../mocks/notification.service.mock';

import { UserManagementService } from './user-management.service';
import { IUMUser, IUserCreateRequest, IUserUpdateRequest, UserRole, UserStatus } from './user.interfaces';

describe('UserManagementService', () => {
  const params: IListRequest = {
    page: 1,
    page_size: 5,
  };

  let service: UserManagementService,
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
        UserManagementService,
      ],
    });
    executeSpy = spyOn(AppHttp, 'execute').and.callFake((data) => Observable.of(data)).and.callThrough();
    service = TestBed.get(UserManagementService);
  });

  afterEach(() => {
    executeSpy.calls.reset();
  });

  it('should return a list of users', inject([AppHttp], (http: AppHttp) => {
    const httpGetSpy = spyOn(http, 'get').and.callThrough();
    service.list(params).subscribe();
    expect(httpGetSpy.calls.count()).toBe(1);
    expect(httpGetSpy.calls.mostRecent().args).toEqual(['users', params]);
  }));

  it('should get a user', inject([AppHttp], (http: AppHttp) => {
    const httpGetSpy = spyOn(http, 'get').and.callThrough();
    service.get('userId').subscribe();

    expect(httpGetSpy.calls.count()).toBe(1);
    expect(httpGetSpy.calls.mostRecent().args).toEqual(['users/userId']);
  }));

  it('should create a regular user', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpPostSpy = spyOn(http, 'post').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IUserCreateRequest = {
      username: 'UserName',
      email: 'abcde123@mail.com',
      password: 'abcd1234_+@',
      firstName: 'FirstName',
      lastName: 'LastName',
      role: UserRole.USER,
    };

    service.create(data).subscribe();
    expect(httpPostSpy.calls.count()).toBe(1);
    expect(httpPostSpy.calls.mostRecent().args).toEqual(['users', data]);

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_USER_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.CREATE_USER, {
      ...data,
    }]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args)
      .toEqual(['User has been created: ' + data.firstName + ' ' + data.lastName + ' (' + data.email + ')']);
  }));

  it('should update user', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpPutSpy = spyOn(http, 'put').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IUserUpdateRequest = {
      username: 'UserName',
      email: 'abcde123@mail.com',
      password: 'abcd1234_+@',
      firstName: 'FirstName',
      lastName: 'LastName',
      role: UserRole.USER,
    };

    service.update('updateUserId', data).subscribe();
    expect(httpPutSpy.calls.count()).toBe(1);
    expect(httpPutSpy.calls.mostRecent().args).toEqual(['users/updateUserId', data]);

    expect(executeSpy.calls.count()).toBe(1);
    expect(executeSpy.calls.mostRecent().args[0]).toEqual(Observable.of(data));

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_USER_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.UPDATE_USER, { id: 'updateUserId' }]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args)
      .toEqual(['User has been updated: ' + data.firstName + ' ' + data.lastName + ' (' + data.email + ')']);
  }));

  it('should delete user', inject([AppHttp, EventService, NotificationService], (http: AppHttp, eventService: EventService, notify: NotificationService) => {
    const createNotificationSpy = spyOn(notify, 'create');
    const httpDeleteSpy = spyOn(http, 'delete').and.callThrough();
    const emitEventSpy = spyOn(eventService, 'emit').and.callThrough();
    const data: IUMUser = {
      id: 'userId',
      username: 'UserName',
      email: 'abcde123@mail.com',
      firstName: 'FirstName',
      lastName: 'LastName',
      role: UserRole.USER,
      status: UserStatus.DEACTIVATED,
      created: '2019-02-21T12:13:01.318Z',
      updated: '2019-02-21T12:13:01.318Z',
    };

    service.delete(data).subscribe();
    expect(httpDeleteSpy.calls.count()).toBe(1);
    expect(httpDeleteSpy.calls.mostRecent().args).toEqual(['users/' + data.id, null]);

    expect(emitEventSpy.calls.count()).toBe(2);
    expect(emitEventSpy.calls.first().args).toEqual([IEvent.Type.UPDATE_USER_LIST]);
    expect(emitEventSpy.calls.mostRecent().args).toEqual([IEvent.Type.DELETE_USER, { id: 'userId' }]);

    expect(createNotificationSpy.calls.count()).toBe(1);
    expect(createNotificationSpy.calls.mostRecent().args)
      .toEqual(['User has been deleted: ' + data.firstName + ' ' + data.lastName + ' (' + data.email + ')']);
  }));
});
