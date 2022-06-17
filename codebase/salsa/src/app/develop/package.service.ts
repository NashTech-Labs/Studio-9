import { Injectable } from '@angular/core';

import { Observable } from 'rxjs/Observable';

import {
  IBackendList,
  IObjectId,
  TObjectId,
} from '../core/interfaces/common.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';

import {
  IPackage,
  IPackagePublish,
  IPackageSearchParams,
} from './package.interfaces';

@Injectable()
export class PackageService {
  private readonly _baseUrl = 'packages';
  constructor(
    private _http: AppHttp,
    private _notifications: NotificationService,
    protected _events: EventService,
  ) {
  }

  list(params?: IPackageSearchParams): Observable<IBackendList<IPackage>> {
    return this._http.get(this._baseUrl, params);
  }

  get(id: TObjectId): Observable<IPackage> {
    return this._http.get(this._baseUrl + '/' + id);
  }

  'delete'(item: IPackage): Observable<IObjectId> {
    const observable = this._http.delete(this._baseUrl + '/' + item.id);

    return AppHttp.execute(observable,
      () => {
        this._events.emit(IEvent.Type.DELETE_PACKAGE, {id: item.id});
        this._notifications.create('S9 Project Package has been deleted: ' + item.name);
      },
    );
  }

  publish(item: IPackage, data: IPackagePublish) {
    const observable = this._http.post(this._baseUrl + '/' + item.id + '/publish', data);
    return AppHttp.execute(observable,
      () => {
        this._events.emit(IEvent.Type.PUBLISH_PACKAGE, { id: item.id });
        this._notifications.create('S9 Project package published: ' + item.name);
      },
    );
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      DELETE_PACKAGE = 'DELETE_PACKAGE',
      PUBLISH_PACKAGE = 'PUBLISH_PACKAGE',
    }
  }
}
