import { Injectable } from '@angular/core';

import { Observable } from 'rxjs/Observable';

import config from '../config';
import {
  IAsset,
  IAssetListRequest,
  IAssetService,
  IBackendList,
  IObjectId,
  TObjectId,
} from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { DataService } from '../core/services/data.service';
import { EventService, IEvent } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';

import { IDashboard, IDashboardCreate, IDashboardUpdate } from './dashboard.interface';

@Injectable()
export class DashboardService extends DataService implements IAssetService<IDashboard, IDashboardCreate> {
  readonly assetType: IAsset.Type = IAsset.Type.DASHBOARD;

  protected _type = config.asset.values.DASHBOARD;

  constructor(
    protected http: AppHttp,
    protected events: EventService,
    private notifications: NotificationService,
  ) {
    super(events);
  }

  list(params?: IAssetListRequest): Observable<IBackendList<IDashboard>> {
    return this.http.get('dashboards', params);
  }

  create(item: IDashboardCreate): Observable<IDashboard> {
    const data = jQuery.extend(true, {}, item);
    const observable = this.http.post('dashboards', data);

    return AppHttp.execute(observable,
      (dashboard: IDashboard) => {
        this.events.emit(IEvent.Type.CREATE_DASHBOARD, dashboard);
        this.events.emit(IEvent.Type.UPDATE_DASHBOARD_LIST);
        this.notifications.create('Dashboard created: ' + dashboard.name);
      },
    );
  }


  get(id: TObjectId): Observable<IDashboard> {
    return this.http.get('dashboards/' + id);
  }

  getActiveProcess(item: IDashboard): Observable<IProcess> {
    return Observable.of(null);
  }

  update(id: TObjectId, data: IDashboardUpdate): Observable<IDashboard> {
    const observable = this.http.put('dashboards/' + id, data);
    // console.log(JSON.stringify(data)); //@TODO REMOVE
    return AppHttp.execute(observable,
      (data: IDashboard) => {
        this.events.emit(IEvent.Type.UPDATE_DASHBOARD_LIST);
        this.events.emit(IEvent.Type.UPDATE_DASHBOARD, { id });
        this.notifications.create('Dashboard updated: ' + data.name);
      },
    );
  }

  'delete'(item: IDashboard): Observable<IObjectId> {
    const observable = this.http.delete('dashboards/' + item.id);

    return AppHttp.execute(observable,
      () => {
        this.events.emit(IEvent.Type.UPDATE_DASHBOARD_LIST);
        this.events.emit(IEvent.Type.DELETE_DASHBOARD, { id: item.id });
        this.notifications.create('Dashboard deleted: ' + item.name);
      },
    );
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_DASHBOARD = 'CREATE_DASHBOARD',
      UPDATE_DASHBOARD_LIST = 'UPDATE_DASHBOARD_LIST',
      UPDATE_DASHBOARD = 'UPDATE_DASHBOARD',
      DELETE_DASHBOARD = 'DELETE_DASHBOARD',
    }
  }
}
