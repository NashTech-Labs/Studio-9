import {
  HttpClient,
  HttpErrorResponse,
  HttpEvent,
  HttpEventType,
  HttpHeaders,
  HttpParameterCodec,
  HttpParams,
  HttpResponse,
  HttpResponseBase,
  HttpUrlEncodingCodec,
} from '@angular/common/http';
import { HttpObserve } from '@angular/common/http/src/client';
import {
  Injectable,
  NgZone,
} from '@angular/core';

import { EventSourcePolyfill } from 'event-source-polyfill';
import * as _ from 'lodash';
import 'rxjs/add/observable/empty';
import 'rxjs/add/observable/throw';
import 'rxjs/add/operator/catch';
import 'rxjs/add/operator/concat';
import 'rxjs/add/operator/retryWhen';
import 'rxjs/add/operator/take';
import { Observable } from 'rxjs/Observable';
import { ErrorObservable } from 'rxjs/observable/ErrorObservable';
import { Subject } from 'rxjs/Subject';
import { Subscriber } from 'rxjs/Subscriber';

import config from '../../config';
import { ActivityObserver } from '../../utils/activity-observer';

import { IUploadNotification, NotificationService } from './notification.service';
import { StorageService } from './storage.service';

export interface IServerSentEvent<T> {
  type: string;
  data: T;
}

export namespace IServerSentEvent {
  export enum Type {
    MESSAGE = 'message',
  }
}

export interface AppHttpSSERequestOptions<T> {
  retry?: boolean;
  headers?: { [header: string]: string };
  noNotifications?: boolean;
  catchWith?: (res: MessageEvent) => Observable<IServerSentEvent<T>>;
  parse?: (_: string) => T;
}

export class BaileQueryEncoder extends HttpUrlEncodingCodec implements HttpParameterCodec {
  encodeKey(k: string): string {
    return encodeURIComponent(k);
  }

  encodeValue(v: string): string {
    return encodeURIComponent(v);
  }

  /*decodeKey(k: string): string {  // @TODO WHAT DOES IT MEAN?
    return decodeURIComponent(k);
  }

  decodeValue(v: string): string {
    return decodeURIComponent(v);
  }*/
}

export interface AppHttpRequestOptions {
  contentType?: string;
  responseType?: AngularHttpClientResponseType;
  headers?: { [header: string]: string };
  noNotifications?: boolean;
  catchWith?: (res: AppHttpError) => Observable<any>;
  deserialize?: (_: any) => any;
  serialize?: (_: any) => any;
  onHttpEvent?: (_: HttpEvent<any>) => void;
  getResponse?: boolean;
}

export interface AppHttpError {
  code: number;
  message: string;
  httpStatus: number;
}

export interface AppHttpFile extends File {
  realName?: string;
}

type AngularHttpClientResponseType = 'arraybuffer' | 'blob' | 'json' | 'text';

interface AngularHttpClientRequestOptions {
  body?: any;
  headers?: HttpHeaders;
  params?: HttpParams;
  observe?: HttpObserve;
  reportProgress?: boolean;
  responseType?: AngularHttpClientResponseType;
  withCredentials?: boolean;
}

@Injectable()
export class AppHttp {
  private _activityObserver = new ActivityObserver();
  private _httpErrors = new Subject<HttpErrorResponse>();

  constructor(
    private _http: HttpClient,
    private storage: StorageService,
    private notifications: NotificationService,
    private zone: NgZone,
  ) {}

  getToken() {
    const token = this.storage.get(config.storage.token);
    return token ? config.api.tokenValue(token.access_token) : null;
  }

  request(method: string, url: string, body: any, params: any, options?: AppHttpRequestOptions): Observable<any> {
    let res = this._http.request(method, config.api.base + url, this._options(options, params, body));
    return this._catch(this._deserialize(res, options), options).share(); // share: to make consecutive observers calls
  }

  get(url: string, params?: any, options?: AppHttpRequestOptions): Observable<any> {
    let res = this._http.request('GET', config.api.base + url, this._options(options, params));
    return this._catch(this._deserialize(res, options), options).share(); // share: to make cosecutive observers calls
  }

  post(url: string, body: any, params?: any, options?: AppHttpRequestOptions): Observable<any> {
    let res = this._http.request('POST', config.api.base + url, this._options(options, params, body));
    return this._activityObserver.observe(this._catch(this._deserialize(res, options), options).share()); // share: to make cosecutive observers calls
  }

  put(url: string, body: any, params?: any, options?: AppHttpRequestOptions): Observable<any> {
    let res = this._http.request('PUT', config.api.base + url, this._options(options, params, body));
    return this._activityObserver.observe(this._catch(this._deserialize(res, options), options).share()); // share: to make cosecutive observers calls
  }

  patch(url: string, body: any, params?: any, options?: AppHttpRequestOptions): Observable<any> {
    let res = this._http.request('PATCH', config.api.base + url, this._options(options, params, body));
    return this._activityObserver.observe(this._catch(this._deserialize(res, options), options).share()); // share: to make cosecutive observers calls
  }

  'delete'(url: string, params?: any, options?: AppHttpRequestOptions): Observable<any> {
    let res = this._http.request('DELETE', config.api.base + url, this._options(options, params));
    return this._activityObserver.observe(this._catch(this._deserialize(res, options), options).share()); // share: to make cosecutive observers calls
  }

  upload(url: string, files: AppHttpFile | AppHttpFile[], bodyParams: { [key: string]: string } = {}, options: AppHttpRequestOptions = {}): Observable<any> {
    const body = new FormData();
    if (files) {
      const upload = Array.isArray(files) ? files : [files];
      upload.forEach(file => body.append('file', file, file.realName || file.name));
    }
    Object.keys(bodyParams).forEach(key => {
      const value = bodyParams[key];
      if (value !== null && value !== undefined) {
        body.append(key, value);
      }
    });

    let res = this._http.request('POST', config.api.base + url, this._options(options, {}, body));
    return this._catch(this._deserialize(res, options), options).share(); // share: to make cosecutive observers calls
  }

  sseStream<T = any>(
    url: string,
    options: AppHttpSSERequestOptions<T> = null,
    eventTypes: string[] = [IServerSentEvent.Type.MESSAGE],
  ): Observable<IServerSentEvent<T>> {
    const token = this.getToken();
    const serviceHeaders = {};
    if (token) {
      serviceHeaders[config.api.tokenLabel] = token;
    }

    const convertEvent = function(event: MessageEvent): IServerSentEvent<T> {
      return {
        type: event.type,
        data: options && options.parse
          ? options.parse(event.data)
          : JSON.parse(event.data),
      };
    };

    let observable: Observable<IServerSentEvent<T>> = Observable.create((subscriber: Subscriber<any>) => {
      return this.zone.runOutsideAngular(() => {
        const esOptions = {
          headers: Object.assign(
            {},
            serviceHeaders,
            (options && options.headers) ? options.headers : {},
          ),
          Transport: XMLHttpRequest,
        };

        const es = new EventSourcePolyfill(config.api.base + url, esOptions);
        eventTypes.forEach(eventType => {
          if (eventType === IServerSentEvent.Type.MESSAGE) {
            es.onmessage = event => subscriber.next(convertEvent(event));
          } else {
            es.addEventListener(eventType, event => subscriber.next(convertEvent(<MessageEvent> event)));
          }
        });
        es.onerror = err => subscriber.error(err);

        return () => {
          es.close();
        };
      });
    });

    if (options && options.retry) {
      observable = observable.retryWhen((err) => {
        return err.delay(5000)
          .take(10)
          .concat(Observable.throw(err));
      });
    }

    if (options && options.catchWith) {
      observable = observable.catch(options.catchWith);
    }

    if (!options || !options.noNotifications) {
      observable = observable.catch((error: MessageEvent) => {
        this.notifications.create(
          'Can\'t connect to the server, please check your internet connection and reload the page',
          config.notification.level.values.DANGER,
        );
        console.error(error);
        return Observable.empty<IServerSentEvent<T>>();
      });
    }

    return observable.share();
  }

  monitoredUpload(url: string, files: AppHttpFile | AppHttpFile[], bodyParams: { [key: string]: string } = {}, options: AppHttpRequestOptions = {}): Observable<any> {
    return new Observable<IUploadNotification>(subscriber => {
      subscriber.next(this.notifications.createUploadNotification(files, () => {
        subscriber.unsubscribe();
        this.notifications.create('Upload cancelled');
      }));
    }).flatMap(notification => {
      return this.upload(url, files, bodyParams, {
        ...options,
        onHttpEvent: event => {
          if (event.type === HttpEventType.UploadProgress) {
            notification.onProgress(event.total ? event.loaded / event.total * 100 : 100);
          }
        },
        catchWith: (error: AppHttpError) => {
          notification.onError(error);

          return Observable.empty();
        },
      }).do(() => notification.onComplete());
    }).share();
  }

  get active(): Observable<boolean> {
    return this._activityObserver.active;
  }

  get httpErrors(): Observable<HttpErrorResponse> {
    return this._httpErrors;
  }

  private _options(options: AppHttpRequestOptions = {}, params?: Object, body?: any): AngularHttpClientRequestOptions {
    const token = this.getToken();
    const serviceHeaders = {};
    if (token) {
      serviceHeaders[config.api.tokenLabel] = token;
    }
    if (body && options.contentType) {
      serviceHeaders['Content-Type'] = options.contentType;
    }
    return {
      body: this._serialize(body, options),
      responseType: options.responseType || 'json',
      headers: new HttpHeaders(_.merge({}, serviceHeaders, options.headers || {})),
      params: this._encodeHttpParams(params),
      observe: options.onHttpEvent ? 'events' : (options.getResponse ? 'response' : 'body'),
      reportProgress: !!options.onHttpEvent,
    };
  }

  // wrap and serialize request
  private _serialize(value: any, options: AppHttpRequestOptions): any {
    return (options && options.serialize) ? options.serialize(value) : value;
  }

  // unwrap and deserialize response
  private _deserialize(res: Observable<any>, options?: AppHttpRequestOptions): Observable<any> {
    if (options && options.onHttpEvent) {
      res = (<Observable<HttpEvent<any>>> res)
        .do(options.onHttpEvent)
        .filter(_ => _.type === HttpEventType.Response)
        .map(_ => {
          const response = (<HttpResponse<any>> _);

          return options.getResponse ? response : response.body;
        });
    }
    return res.map((body: any) => {
      return (options && options.deserialize) ? options.deserialize(body) : body;
    });
  }

  private _catch(res: Observable<any>, options?: AppHttpRequestOptions): Observable<HttpResponseBase> {
    // parse error
    let out = res.catch((res: any): ErrorObservable => {
      let error: AppHttpError;
      if (res instanceof HttpErrorResponse) {
        this._httpErrors.next(res);
        if (res.error instanceof ProgressEvent) {
          error = this._error(res.message, res.status);
        } else if (typeof res.error === 'object') {
          const errorResponse = res.error.error || res.error;
          error = Object.assign({}, errorResponse, {
            httpStatus: res.status,
          });
        } else { // should be a case when response body is not JSON
          error = this._error(res.error, res.status);
        }
      } else if (res instanceof Error) {
        console.error(res);
        error = this._error(res.message);
      } else {
        console.error(res);
        error = this._error('Error handler can\'t handle this');
      }

      return Observable.throw(error);
    });

    if (options && options.catchWith) {
      out = out.catch(options.catchWith);
    }

    if (!options || !options.noNotifications) {
      out = out.catch((error: AppHttpError) => {
        this.notifications.create(error.message, config.notification.level.values.DANGER);
        return Observable.empty();
      });
    }

    return out;
  }

  // encode search (url) params
  private _encodeHttpParams(params?: Object): HttpParams {
    if (params instanceof HttpParams) {
      return params;
    }

    const paramsMap: { [ param: string]: string | string[] } = Object.keys(params || {}).reduce((newParams, key) => {
      let value = params[key];
      if (_.isArray(value)) {
        newParams[key] = value.map(_ => _ === null ? '' : String(_)).join(',');
      } else if (value !== null && value !== undefined) {
        newParams[key] = String(value);
      }
      return newParams;
    }, {});

    return new HttpParams({ encoder: new BaileQueryEncoder(), fromObject: paramsMap });
  }

  // create structured error response
  private _error(message: string, status: number = 400): AppHttpError {
    return {
      code: status,
      message: message || `Server error (${status})`,
      httpStatus: status,
    };
  }

  /**
   * This is a helper method to start executing shared observable got from this service.
   *
   * @param o
   * @param next
   * @param error
   * @param complete
   * @returns {Observable<T>}
   */
  public static execute<T>(o: Observable<T>, next?: (value: T) => void, error?: (error: any) => void, complete?: () => void): Observable<T> {
    const dummy = () => {};
    o.subscribe(
      next || dummy,
      error || function(e) {
        // this will throw any error that's in UI code so that app crashes
        if (e instanceof Error) {
          throw e;
        }
      },
      complete || dummy,
    );
    return o;
  }
}
