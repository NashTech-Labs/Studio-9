import { HttpEventType, HttpParams, HttpRequest } from '@angular/common/http';
import { HttpUploadProgressEvent } from '@angular/common/http/src/response';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';

import config from '../../config';
import { NotificationServiceMock } from '../../mocks/notification.service.mock';
import { StorageServiceMock } from '../../mocks/storage.service.mock';

import { AppHttp, AppHttpError, BaileQueryEncoder } from './http.service';
import { NotificationService } from './notification.service';
import { StorageService } from './storage.service';

describe('HttpService', () => {
  let service: AppHttp;
  let httpMock: HttpTestingController;
  let storage: StorageServiceMock;
  let notification: NotificationServiceMock;
  const baseUrl = config.api.base;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [
        HttpClientTestingModule,
      ],
      providers: [
        {
          provide: StorageService,
          useClass: StorageServiceMock,
        },
        {
          provide: NotificationService,
          useClass: NotificationServiceMock,
        },
        AppHttp,
      ],
    });
    service = TestBed.get(AppHttp);
    httpMock = TestBed.get(HttpTestingController);
    storage = TestBed.get(StorageService);
    notification = TestBed.get(NotificationService);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should raise error when noNotification is in options', done => {
    const error = { code: 400, message: 'ERROR', httpStatus: 400 };
    service.get('url', {}, { noNotifications: true })
      .subscribe(
        () => {
          fail('Unwanted code branch');
        },
        (res: AppHttpError) => {
          expect(res).toEqual(error);
          done();
        });

    const errorRequest = httpMock.expectOne({ url: baseUrl + 'url', method: 'GET' });
    errorRequest.flush({ error: error }, {
      status: 400,
      statusText: 'Server Error',
    });
  });

  it('should not raise error noNotifications is false', done => {
    service.delete('url', {}, { noNotifications: false })
      .subscribe(
        () => {
          fail('Unwanted code branch');
        },
        () => {
          fail('Unwanted code branch');
        },
        () => {
          done();
        });
    const errorRequest = httpMock.expectOne({ url: baseUrl + 'url', method: 'DELETE' });
    errorRequest.error(new ErrorEvent('ERROR'), {
      status: 400,
      statusText: 'Server Error',
    });
  });

  it('should notify when noNotifications is false', done => {
    const error = { code: 400, message: 'ERROR', httpStatus: 400 };
    const notifySpy = spyOn(notification, 'create');

    service.delete('url', {}, { noNotifications: false })
      .subscribe(
        () => {
          fail('Unwanted code branch');
        },
        () => {
          fail('Unwanted code branch');
        },
        () => {
          expect(notifySpy).toHaveBeenCalledTimes(1);
          expect(notifySpy).toHaveBeenCalledWith(error.message, config.notification.level.values.DANGER);
          done();
        });
    const errorRequest = httpMock.expectOne({ url: baseUrl + 'url', method: 'DELETE' });
    errorRequest.flush({ error: error }, {
      status: 400,
      statusText: 'Server Error',
    });
  });

  it('should use catchWith on error', done => {
    const catchWithSpy = jasmine.createSpy('catchWithSpy').and.returnValue(Observable.of('foo'));
    const error = { code: 400, message: 'ERROR', httpStatus: 400 };
    service.patch('url', { body: 'text' }, {}, { catchWith: catchWithSpy })
      .subscribe(
        (data) => {
          expect(data).toBe('foo');
        },
        () => {
          fail('Unwanted code branch');
        },
        () => {
          expect(catchWithSpy).toHaveBeenCalledTimes(1);
          expect(catchWithSpy.calls.first().args[0]).toEqual(error);
          done();
        });

    const errorRequest = httpMock.expectOne({ url: baseUrl + 'url', method: 'PATCH' });
    errorRequest.flush({ error: error }, {
      status: 400,
      statusText: 'Server Error',
    });
  });

  it('should call success catchWith and noNotification both', done => {
    const catchWithSpy = jasmine.createSpy('catchWithSpy').and.returnValue(Observable.empty());
    const notifySpy = spyOn(notification, 'create');
    const error = { code: 400, message: 'ERROR', httpStatus: 400 };
    service.patch('url', { body: 'text' }, {}, { noNotifications: false, catchWith: catchWithSpy })
      .subscribe(
        () => {
          fail('Unwanted code branch');
        },
        () => {
          fail('Unwanted code branch');
        },
        () => {
          expect(catchWithSpy).toHaveBeenCalledTimes(1);
          expect(catchWithSpy.calls.first().args[0]).toEqual(error);
          expect(notifySpy).toHaveBeenCalledTimes(0);
          done();
        });

    const errorRequest = httpMock.expectOne({ url: baseUrl + 'url', method: 'PATCH' });
    errorRequest.flush({ error: error }, {
      status: 400,
      statusText: 'Server Error',
    });
  });

  it('should call failed catchWith and noNotification both', done => {
    const error = { code: 400, message: 'ERROR', httpStatus: 400 };
    const anotherError = { code: 404, message: 'ANOTHER_ERROR', httpStatus: 404 };
    const catchWithSpy = jasmine.createSpy('catchWithSpy').and.returnValue(Observable.throw(anotherError));
    const notifySpy = spyOn(notification, 'create');
    service.patch('url', { body: 'text' }, {}, { noNotifications: false, catchWith: catchWithSpy })
      .subscribe(
        () => {
          fail('Unwanted code branch');
        },
        () => {
          fail('Unwanted code branch');
        },
        () => {
          expect(catchWithSpy).toHaveBeenCalledTimes(1);
          expect(catchWithSpy.calls.first().args[0]).toEqual(error);
          expect(notifySpy).toHaveBeenCalledTimes(1);
          expect(notifySpy).toHaveBeenCalledWith(anotherError.message, config.notification.level.values.DANGER);
          done();
        });

    const errorRequest = httpMock.expectOne({ url: baseUrl + 'url', method: 'PATCH' });
    errorRequest.flush({ error: error }, {
      status: 400,
      statusText: 'Server Error',
    });
  });

  it('should hide error when noNotifications and no catchWith', done => {
    const error = { code: 400, message: 'ERROR', httpStatus: 400 };
    service.post('url', { body: 'text' }, {})
      .subscribe(
        () => {
          fail('Unwanted code branch');
        },
        () => {
          fail('Unwanted code branch');
        },
        () => {
          done();
        });

    const errorRequest = httpMock.expectOne({ url: baseUrl + 'url', method: 'POST' });
    errorRequest.flush({ error: error }, {
      status: 400,
      statusText: 'Server Error',
    });
  });

  it('should request should have token in headers when AUTHORIZED and should not have when NOT AUTHORIZED', () => {
    //with headers
    const token = 'userToken';
    storage.set(config.storage.token, { access_token: token });
    service.get('url').subscribe();
    const authorizedRequest = httpMock.expectOne({
      url: baseUrl + 'url',
      method: 'GET',
    }, 'Expect this custom request to be called with token headers');
    authorizedRequest.flush({ success: 'ok' }, {
      status: 200,
      statusText: 'OK',
    });
    expect(authorizedRequest.request.headers.has(config.api.tokenLabel)).toBeTruthy();
    expect(authorizedRequest.request.headers.get(config.api.tokenLabel)).toEqual(config.api.tokenValue(token));
    //without
    storage.remove(config.storage.token);
    service.get('anotherUrl').subscribe();
    const notAuthorizedRequest = httpMock.expectOne({
      url: baseUrl + 'anotherUrl',
      method: 'GET',
    }, 'Expect this custom request to be called without token headers');
    notAuthorizedRequest.flush({ success: 'ok' }, {
      status: 200,
      statusText: 'OK',
    });
    expect(notAuthorizedRequest.request.headers.has(config.api.tokenLabel)).toBeFalsy();
  });

  it('should request should have default or specified Content-Type', () => {
    // get requests shouldn't have content-type
    service.get('url').subscribe();
    const getRequest = httpMock.expectOne({
      url: baseUrl + 'url',
      method: 'GET',
    }, 'Expect this custom request to be called with token headers');
    getRequest.flush({ success: 'ok' }, {
      status: 200,
      statusText: 'OK',
    });
    expect(getRequest.request.headers.has('Content-Type')).toBeFalsy();
    //post should have optional content-type
    service.post('url', { body: 'Body' }, {}, { contentType: 'text' }).subscribe();
    const postRequest = httpMock.expectOne({
      url: baseUrl + 'url',
      method: 'POST',
    }, 'Expect this custom request to be called with token headers');
    postRequest.flush({ success: 'ok' }, {
      status: 200,
      statusText: 'OK',
    });
    expect(postRequest.request.headers.has('Content-Type')).toBeTruthy();
    expect(postRequest.request.headers.get('Content-Type')).toEqual('text');
  });

  it('should request should have extra headers', () => {
    service.put('url', { body: 'Body' }, {}, { headers: { extraHeader: 'value' } }).subscribe();
    const putRequest = httpMock.expectOne({
      url: baseUrl + 'url',
      method: 'PUT',
    }, 'Expect this custom request to be called with extra headers');
    putRequest.flush({ success: 'ok' }, {
      status: 200,
      statusText: 'OK',
    });
    expect(putRequest.request.headers.has('extraHeader')).toBeTruthy();
    expect(putRequest.request.headers.get('extraHeader')).toEqual('value');
  });

  it('should upload file with body params + check activity observer', (done) => {
    const body = { success: 'ok' };
    const eventSpy = jasmine.createSpy('eventSpy');
    const activitySpy = jasmine.createSpy('activitySpy');
    let file: File;
    try {
      file = new File([''], 'filename.txt', { type: 'text/plain' });
    } catch (_) {
      const f = new Blob(['The content of your file']);
      file = <File> Object.assign({}, f, { name: 'filename.txt' });
    }
    service.upload('upload', file, { bodyParam: 'value' }, { onHttpEvent: eventSpy }).subscribe((data) => {
      expect(data).toEqual(body);
      done();
    });
    service.active.filter(_ => !!_).subscribe(activitySpy);
    const postRequest = httpMock.expectOne({
      url: baseUrl + 'upload',
      method: 'POST',
    }, 'Expect upload events');
    postRequest.event(<HttpUploadProgressEvent> {
      type: HttpEventType.UploadProgress,
      total: 100,
      loaded: 50,
    });
    postRequest.flush(body, {
      status: 200,
      statusText: 'OK',
    });
    expect(eventSpy).toHaveBeenCalledTimes(3);
    expect(eventSpy.calls.first().args).toEqual([{ type: HttpEventType.Sent }]);
    expect(eventSpy.calls.argsFor(1)).toEqual([{
      type: HttpEventType.UploadProgress,
      total: 100,
      loaded: 50,
    }]);
    expect(activitySpy).not.toHaveBeenCalled();
  });

  it('should upload file even WITHOUT ANY body params or a file', (done) => {
    const body = { success: 'ok' };
    service.upload('upload', null).subscribe((data) => {
      expect(data).toEqual(body);
      done();
    });
    const postRequest = httpMock.expectOne({
      url: baseUrl + 'upload',
      method: 'POST',
    }, 'Expect upload events');
    postRequest.flush(body, {
      status: 200,
      statusText: 'OK',
    });
  });

  it('should call serialize ', () => {
    const serializeSpy = jasmine.createSpy('serializeSpy');
    const body = { body: 'Body' };
    service.request('PATCH', 'url', body, {}, { serialize: serializeSpy }).subscribe();
    const request = httpMock.expectOne({
      url: baseUrl + 'url',
      method: 'PATCH',
    }, 'Serialize should be called before this request');
    request.flush({ success: 'ok' }, {
      status: 200,
      statusText: 'OK',
    });
    expect(serializeSpy).toHaveBeenCalledTimes(1);
    expect(serializeSpy).toHaveBeenCalledWith(body);
  });

  it('should consume params as HttpParams instance', () => {
    const encoder = new BaileQueryEncoder();
    service.get('url', new HttpParams({
      fromObject: { 'key1': ['decoding', 'encoding', '123'], 'key2': ['value2'], 'key': 'value' },
      encoder,
    })).subscribe();

    const requestWithHttpParams = httpMock.expectOne((request: HttpRequest<any>) => {
      return (request.url === `${baseUrl}url` && request.method === 'GET');
    });

    requestWithHttpParams.flush({ success: 'ok' }, {
      status: 200,
      statusText: 'OK',
    });
    expect(requestWithHttpParams.request.params.keys().length).toBe(3);
    expect(requestWithHttpParams.request.params.has('key1')).toBeTruthy();
    expect(requestWithHttpParams.request.params.getAll('key1')).toEqual(['decoding', 'encoding', '123']);
    expect(requestWithHttpParams.request.params.has('key2')).toBeTruthy();
    expect(requestWithHttpParams.request.params.getAll('key2')).toEqual(['value2']);
    expect(requestWithHttpParams.request.params.has('key')).toBeTruthy();
    expect(requestWithHttpParams.request.params.getAll('key')).toEqual(['value']);

  });

  it('should consume params as object', () => {
    service.get('url', {
      'key1': ['decoding', 'encoding', 123],
      'key2': [null, 'value2'],
      'key': 'value',
      'key3': null,
    }).subscribe();
    const requestObjectParams = httpMock.expectOne((request: HttpRequest<any>) => {
      return (request.url === `${baseUrl}url` && request.method === 'GET');
    });

    requestObjectParams.flush({ success: 'ok' }, {
      status: 200,
      statusText: 'OK',
    });
    expect(requestObjectParams.request.params.keys().length).toBe(3);
    expect(requestObjectParams.request.params.has('key1')).toBeTruthy();
    expect(requestObjectParams.request.params.getAll('key1')).toEqual(['decoding,encoding,123']);
    expect(requestObjectParams.request.params.has('key2')).toBeTruthy();
    expect(requestObjectParams.request.params.getAll('key2')).toEqual([',value2']);
    expect(requestObjectParams.request.params.has('key')).toBeTruthy();
    expect(requestObjectParams.request.params.getAll('key')).toEqual(['value']);
  });

  it('should handle error type Error', (done) => {
    const catchWithSpy = jasmine.createSpy('catchWithSpy').and.returnValue(Observable.throw('catched'));
    service.get('url', {}, {
      deserialize: () => {
        throw new Error('deserializer failed');
      },
      catchWith: catchWithSpy,
    }).subscribe(
      () => {
        fail('Unwanted code branch');
      },
      () => {
        fail('Unwanted code branch');
      },
      () => {
        expect(catchWithSpy).toHaveBeenCalledTimes(1);
        done();
      },
    );
    const request = httpMock.expectOne({
      url: baseUrl + 'url',
      method: 'GET',
    }, 'Expect upload events');
    request.flush({ success: 'ok' }, {
      status: 200,
      statusText: 'OK',
    });
  });

  it('should handle null message', (done) => {
    const catchWithSpy = jasmine.createSpy('catchWithSpy').and.returnValue(Observable.throw('catched'));
    service.get('url', {}, {
      deserialize: () => {
        throw new Error();
      },
      catchWith: catchWithSpy,
    }).subscribe(
      () => {
        fail('Unwanted code branch');
      },
      () => {
        fail('Unwanted code branch');
      },
      () => {
        expect(catchWithSpy).toHaveBeenCalledTimes(1);
        done();
      },
    );
    const request = httpMock.expectOne({
      url: baseUrl + 'url',
      method: 'GET',
    }, 'Expect upload events');
    request.flush({ success: 'ok' }, {
      status: 200,
      statusText: 'OK',
    });
  });

  it('should handle unknown type of Error', (done) => {
    const catchWithSpy = jasmine.createSpy('catchWithSpy').and.returnValue(Observable.throw('catched'));
    service.get('url', {}, {
      deserialize: () => {
        /* tslint:disable:no-string-throw */
        throw 'any type of throw';
        /* tslint:enable */
      },
      catchWith: catchWithSpy,
    }).subscribe(
      () => {
        fail('Unwanted code branch');
      },
      () => {
        fail('Unwanted code branch');
      },
      () => {
        expect(catchWithSpy).toHaveBeenCalledTimes(1);
        done();
      },
    );
    const request = httpMock.expectOne({
      url: baseUrl + 'url',
      method: 'GET',
    }, 'Expect upload events');
    request.flush({ success: 'ok' }, {
      status: 200,
      statusText: 'OK',
    });
  });

  it('should execute with no subscription', (done) => {
    const body = { body: 'text' };
    const observable = Observable.of(body);
    AppHttp.execute(observable, null, null, () => {
      done();
    });
  });

  it('should execute with error / complete callback', () => {
    const error = new Error('error');
    const observableError = Observable.throw(error);
    expect(() => AppHttp.execute(observableError)).toThrow(error);
    const observableText = Observable.throw('text');
    expect(() => AppHttp.execute(observableText)).not.toThrow();
  });
});
