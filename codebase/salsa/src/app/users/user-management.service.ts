import { Injectable } from '@angular/core';

import { Observable } from 'rxjs/Observable';
import { forkJoin } from 'rxjs/observable/forkJoin';
import { of } from 'rxjs/observable/of';

import config from '../config';
import { IBackendList, IObjectId, TObjectId } from '../core/interfaces/common.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';

import { IUMUser, IUserCreateRequest, IUserSearchParams, IUserUpdateRequest, UserStatus } from './user.interfaces';

@Injectable()
export class UserManagementService {
  private readonly _baseUrl = 'users';

  constructor(
    protected http: AppHttp,
    protected events: EventService,
    private notifications: NotificationService,
  ) {
  }

  list(params: IUserSearchParams): Observable<IBackendList<IUMUser>> {
    return this.http.get(this._baseUrl, params);
  }

  create(item: IUserCreateRequest): Observable<IUMUser> {
    const observable = this.http
      .post(this._baseUrl, item)
      .do((data: IUMUser) => {
        this.events.emit(IEvent.Type.UPDATE_USER_LIST);
        this.events.emit(IEvent.Type.CREATE_USER, data);
        this.notifications.create('User has been created: ' + this.getUserInformation(data));
      });

    return observable;
  }

  get(id: TObjectId): Observable<IUMUser> {
    return this.http.get(this._baseUrl + '/' + id);
  }

  update(id: TObjectId, data: IUserUpdateRequest): Observable<IUMUser> {
    const observable = this.http.put(this._baseUrl + '/' + id, data);

    return AppHttp.execute(observable,
      (data: IUMUser) => {
        this.events.emit(IEvent.Type.UPDATE_USER_LIST);
        this.events.emit(IEvent.Type.UPDATE_USER, { id });
        this.notifications.create('User has been updated: ' + this.getUserInformation(data));
      },
    );
  }

  'delete'(item: IUMUser, transferTo: IUMUser = null): Observable<IObjectId> {
    const params = transferTo ? { transferOwnershipTo: transferTo.id } : null;
    const observable = this.http.delete(
      this._baseUrl + '/' + item.id,
      params,
    );

    return AppHttp.execute(observable,
      () => {
        this.events.emit(IEvent.Type.UPDATE_USER_LIST);
        this.events.emit(IEvent.Type.DELETE_USER, { id: item.id });
        this.notifications.create(
          'User has been deleted: ' + this.getUserInformation(item) +
          (transferTo ? '. Ownership was transferred to ' + this.getUserInformation(transferTo) : ''),
        );
      },
    );
  }

  getUserInformation(item: IUMUser): string {
    return item.firstName + ' ' + item.lastName + ' (' + item.email + ')';
  }

  listAll(): Observable<IUMUser[]> {
    const fetcher = (page: number): Observable<IBackendList<IUMUser>> => {
      return this.list({page: page, page_size: config.listAllChunkSize, order: 'firstName,lastName,email'});
    };
    return fetcher(1).flatMap((firstResponse: IBackendList<IUMUser>) => {
      if (firstResponse.count <= firstResponse.data.length) {
        return of(firstResponse.data);
      } else {
        const pageToFetch = Array(Math.ceil(firstResponse.count / config.listAllChunkSize - 1)).fill(0).map((_, i) => i + 2);
        const observables = pageToFetch.map(fetcher);
        return forkJoin(observables).map((responses: IBackendList<IUMUser>[]) => {
         return [...firstResponse.data].concat(...responses.map(_ => _.data));
        });
      }
    });
  }

  activateUser(item: IUMUser): Observable<IUMUser> {
    if (item.status === UserStatus.ACTIVE) {
      this.notifications.create(
        'User is already active',
        config.notification.level.values.DANGER,
      );
      return Observable.of(item);
    }

    const observable = this.http.post(this._baseUrl + '/' + item.id  + '/activate', null);

    return AppHttp.execute(observable,
      () => {
        this.events.emit(IEvent.Type.UPDATE_USER_LIST);
        this.events.emit(IEvent.Type.ACTIVATE_USER, { id: item.id });
        this.notifications.create('User has been activated: ' + this.getUserInformation(item));
      },
    );
  }

  deactivateUser(item: IUMUser): Observable<IUMUser> {
    if (item.status === UserStatus.DEACTIVATED) {
      this.notifications.create(
        'User is already deactivated',
        config.notification.level.values.DANGER,
      );
      return Observable.of(item);
    }

    const observable = this.http.post(this._baseUrl + '/' + item.id + '/deactivate', null);

    return AppHttp.execute(observable,
      () => {
        this.events.emit(IEvent.Type.UPDATE_USER_LIST);
        this.events.emit(IEvent.Type.DEACTIVATE_USER, { id: item.id });
        this.notifications.create('User has been deactivated: ' + this.getUserInformation(item));
      },
    );
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_USER = 'CREATE_USER',
      UPDATE_USER_LIST = 'UPDATE_USER_LIST',
      UPDATE_USER = 'UPDATE_USER',
      DELETE_USER = 'DELETE_USER',
      ACTIVATE_USER = 'ACTIVATE_USER',
      DEACTIVATE_USER = 'DEACTIVATE_USER',
    }
  }
}
