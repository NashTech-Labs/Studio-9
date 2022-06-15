import { HttpRequest } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import config from '../../config';
import { users as fixtureUsers } from '../../fixture/fixtures/users';
import { EventServiceMock } from '../../mocks/event.service.mock';
import { NotificationServiceMock } from '../../mocks/notification.service.mock';
import { StorageServiceMock } from '../../mocks/storage.service.mock';
import { UserRole } from '../../users/user.interfaces';
import { IUserStats } from '../interfaces/user.interface';

import { EventService } from './event.service';
import { AppHttp } from './http.service';
import { NotificationService } from './notification.service';
import { StorageService } from './storage.service';
import { UserService } from './user.service';

class MockRouter {
  navigate = jasmine.createSpy('navigate');
}

describe('UserService', () => {
  let service: UserService;
  let httpMock: HttpTestingController;
  const baseUrl = config.api.base;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [
        HttpClientTestingModule,
      ],
      providers: [
        AppHttp,
        {
          provide: StorageService,
          useClass: StorageServiceMock,
        },
        {
          provide: NotificationService,
          useClass: NotificationServiceMock,
        },
        {
          provide: EventService,
          useClass: EventServiceMock,
        },
        {
          provide: Router,
          useClass: MockRouter,
        },
        UserService,
      ],
    });
    service = TestBed.get(UserService);
    httpMock = TestBed.get(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should make a request to specific url', (done) => {
    service.signin({}).subscribe(() => {
      expect(true).toBeTruthy();
      done();
    });

    const request = httpMock.expectOne((request: HttpRequest<any>) => {
      return (request.url === `${baseUrl}signin` && request.method === 'POST');
    });
    request.flush({ data: {} }, {
      status: 200,
      statusText: 'OK',
    });
  });

  it('Storage should contain authenticated user model', (done) => {
    const user = fixtureUsers.data[0];

    const fakeCredentials = { email: user.email, password: user.password };

    const response = {
      access_token: 'cba48e04-2359-4f88-a1e4-9ce7d803a02f',
      expires_in: 86400,
      token_type: 'bearer',
    };

    service.signin(fakeCredentials).subscribe(() => {
      service.updateSessionState().subscribe(() => {
        expect(service.isAuthenticated()).toBeTruthy();
        done();
      });
    }, () => {
      fail('Unwanted branch');
    });

    const request = httpMock.expectOne((request: HttpRequest<any>) => {
      return (request.url === `${baseUrl}signin` && request.method === 'POST');
    });
    request.flush(response, {
      status: 200,
      statusText: 'OK',
    });

    const userRequest = httpMock.expectOne((request: HttpRequest<any>) => {
      return (request.url === `${baseUrl}me` && request.method === 'GET');
    });
    userRequest.flush(user, {
      status: 200,
      statusText: 'OK',
    });
  });

  it('Should getStats', (done) => {
    const stats: IUserStats = <any> {
      space_used: 0,
    };
    service.getStats().subscribe(_stats => {
      expect(_stats).toEqual(stats);
      done();
    });
    const request = httpMock.expectOne((request: HttpRequest<any>) => {
      return (request.url === `${baseUrl}me/stats` && request.method === 'GET');
    });
    request.flush(stats, {
      status: 200,
      statusText: 'OK',
    });
  });

  it('Should signup', (done) => {
    const user = {
      'id': '6529d82e-b794-4947-827e-5edf802113c4',
      'username': 'test1',
      'email': 'test1@test1.com',
      'firstName': 'test1',
      'lastName': 'test',
      'status': 'INACTIVE',
      'dataFilterInstances': [],
      'role': UserRole.USER,
      'organizationId': 'orgs_self_service',
      'fromRootOrg': false,
      'created': '2016-09-27T08:32:06.360Z',
      'updated': '2016-09-27T08:32:06.360Z',
      'organization': {
        'id': 'orgs_self_service',
        'name': 'SelfService',
        'desc': 'Self-service users',
        'parentOrganizationId': 'orgs_knoldus',
        'status': 'ACTIVE',
        'applicationIds': [],
        'dataFilterInstances': [],
        'created': '2016-06-14T00:00:00Z',
        'updated': '2016-06-14T00:00:00Z',
        'signUpEnabled': true,
        'signUpGroupIds': [],
      },
    };
    const fakeCredentials = {
      'firstName': 'test1',
      'lastName': 'test',
      'username': 'test1',
      'email': 'test1@test1.com',
      'password': 'test1',
      'password2': 'test1',
      'confirmation': true,
    };
    service.signup(fakeCredentials).subscribe(() => {
      done();
    });
    const request = httpMock.expectOne((request: HttpRequest<any>) => {
      return (request.url === `${baseUrl}signup` && request.method === 'POST');
    });
    request.flush(user, {
      status: 200,
      statusText: 'OK',
    });
  });
});
