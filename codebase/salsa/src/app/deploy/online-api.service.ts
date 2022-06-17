import { Injectable } from '@angular/core';

import { Observable } from 'rxjs/Observable';

import { mocksOnly } from '../core/core.mocks-only';
import {
  IAsset,
  IAssetListRequest,
  IAssetService,
  IBackendList,
  IObjectId,
  TObjectId,
} from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';
import { ProcessService } from '../core/services/process.service';

import { IOnlineAPI, IOnlineAPICreate, IOnlineAPIUpdate } from './online-api.interface';

@Injectable()
export class OnlineAPIService implements IAssetService<IOnlineAPI, IOnlineAPICreate> {
  readonly assetType: IAsset.Type = IAsset.Type.ONLINE_API;

  private baseUrl = 'online-apis';

  constructor(
    protected http: AppHttp,
    protected events: EventService,
    private notifications: NotificationService,
    private processes: ProcessService,
  ) {}

  @mocksOnly(Observable.empty())
  create<T extends IOnlineAPI.Type>(data: IOnlineAPICreate<T>): Observable<IOnlineAPI<T>> {
    // POST '/online-jobs'
    const observable = this.http.post(this.baseUrl, data);

    return AppHttp.execute(observable, (item: IOnlineAPI) => {
      this.events.emit(IEvent.Type.CREATE_ONLINE_API, item);
      this.events.emit(IEvent.Type.UPDATE_ONLINE_API_LIST);
    });
  }

  @mocksOnly(Observable.empty())
  update(id: TObjectId, data: IOnlineAPIUpdate): Observable<IOnlineAPI> {
    // PUT '/online-jobs/:id'
    const observable = this.http.put(`${this.baseUrl}/${id}`, data);

    return AppHttp.execute(observable, () => {
      this.events.emit(IEvent.Type.UPDATE_ONLINE_API_LIST);
      this.events.emit(IEvent.Type.UPDATE_ONLINE_API, { id: id });
    });
  }

  @mocksOnly(Observable.of({data: [], count: 0}))
  list(params?: IAssetListRequest): Observable<IBackendList<IOnlineAPI>> {
    // GET '/online-jobs'
    const observable = this.http.get(this.baseUrl, params);

    return AppHttp.execute(observable);
  }

  @mocksOnly(Observable.empty())
  'delete'(item: IOnlineAPI): Observable<IObjectId> {
    // DELETE '/online-jobs/:id'
    const observable = this.http.delete(`${this.baseUrl}/${item.id}`);

    return AppHttp.execute(observable,
      () => {
        this.events.emit(IEvent.Type.UPDATE_ONLINE_API_LIST);
        this.events.emit(IEvent.Type.DELETE_ONLINE_API, { id: item.id });
        this.notifications.create('Online API deleted: ' + item.name);
      },
    );
  }

  @mocksOnly(Observable.empty())
  get(id: TObjectId): Observable<IOnlineAPI> {
    // GET '/online-jobs/:id'
    const observable = this.http.get(`${this.baseUrl}/${id}`);

    return AppHttp.execute(observable);
  }

  @mocksOnly(Observable.empty())
  getActiveProcess(item: IOnlineAPI): Observable<IProcess> {
    if (item.status === IOnlineAPI.Status.PREPARING) {
      return this.processes.getByTarget(item.id, IAsset.Type.ONLINE_API);
    } else {
      return Observable.of(null);
    }
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_ONLINE_API = 'CREATE_ONLINE_API',
      UPDATE_ONLINE_API_LIST = 'UPDATE_ONLINE_API_LIST',
      UPDATE_ONLINE_API = 'UPDATE_ONLINE_API',
      DELETE_ONLINE_API = 'DELETE_ONLINE_API',
    }
  }
}
