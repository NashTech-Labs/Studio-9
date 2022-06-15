import { Injectable } from '@angular/core';

import { Observable } from 'rxjs/Observable';

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

import { IOnlineTriggeredJob, IOnlineTriggeredJobCreate, IOnlineTriggeredJobUpdate } from './online-job.interface';

@Injectable()
export class OnlineJobService implements IAssetService<IOnlineTriggeredJob, IOnlineTriggeredJobCreate> {
  readonly assetType: IAsset.Type = IAsset.Type.ONLINE_JOB;

  private baseUrl = 'online-jobs';

  constructor(
    protected http: AppHttp,
    protected events: EventService,
    private notifications: NotificationService,
  ) {}

  create(data: IOnlineTriggeredJobCreate): Observable<IOnlineTriggeredJob> {
    // POST '/online-jobs'
    const observable = this.http.post(this.baseUrl, data);

    return AppHttp.execute(observable, (item: IOnlineTriggeredJob) => {
      this.events.emit(IEvent.Type.CREATE_ONLINE_JOB, item);
      this.events.emit(IEvent.Type.UPDATE_ONLINE_JOB_LIST);
    });
  }

  update(id: TObjectId, data: IOnlineTriggeredJobUpdate): Observable<IOnlineTriggeredJob> {
    // PUT '/online-jobs/:id'
    const observable = this.http.put(`${this.baseUrl}/${id}`, data);

    return AppHttp.execute(observable, () => {
      this.events.emit(IEvent.Type.UPDATE_ONLINE_JOB_LIST);
      this.events.emit(IEvent.Type.UPDATE_ONLINE_JOB, { id: id });
    });
  }

  list(params?: IAssetListRequest): Observable<IBackendList<IOnlineTriggeredJob>> {
    // GET '/online-jobs'
    const observable = this.http.get(this.baseUrl, params);

    return AppHttp.execute(observable);
  }

  'delete'(item: IOnlineTriggeredJob): Observable<IObjectId> {
    // DELETE '/online-jobs/:id'
    const observable = this.http.delete(`${this.baseUrl}/${item.id}`);

    return AppHttp.execute(observable,
      () => {
        this.events.emit(IEvent.Type.UPDATE_ONLINE_JOB_LIST);
        this.events.emit(IEvent.Type.DELETE_ONLINE_JOB, { id: item.id });
        this.notifications.create('Online-Job deleted: ' + item.name);
      },
    );
  }

  get(id: TObjectId): Observable<IOnlineTriggeredJob> {
    // GET '/online-jobs/:id'
    const observable = this.http.get(`${this.baseUrl}/${id}`);

    return AppHttp.execute(observable);
  }

  getActiveProcess(item: IOnlineTriggeredJob): Observable<IProcess> {
    return Observable.of(null);
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_ONLINE_JOB = 'CREATE_ONLINE_JOB',
      UPDATE_ONLINE_JOB_LIST = 'UPDATE_ONLINE_JOB_LIST',
      UPDATE_ONLINE_JOB = 'UPDATE_ONLINE_JOB',
      DELETE_ONLINE_JOB = 'DELETE_ONLINE_JOB',
    }
  }
}
