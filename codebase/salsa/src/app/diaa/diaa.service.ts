import { Injectable } from '@angular/core';

import 'rxjs/add/observable/empty';
import 'rxjs/add/observable/of';
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
import { SharedResourceService } from '../core/services/shared-resource.service';

import { IDIAA, IDIAACreate, IDIAARun, IDIAAUpdate } from './diaa.interface';

@Injectable()
export class DIAAService implements IAssetService<IDIAA, IDIAACreate> {
  readonly assetType: IAsset.Type = IAsset.Type.DIAA;

  constructor(
    protected http: AppHttp,
    protected events: EventService,
    private sharedService: SharedResourceService,
    private notifications: NotificationService,
    private processes: ProcessService,
  ) {
  }

  @mocksOnly(Observable.of({data: [], count: 0}))
  list(params?: IAssetListRequest): Observable<IBackendList<IDIAA>> {
    return this.http.get('diaas', params);
  }

  @mocksOnly(Observable.empty())
  get(id: TObjectId): Observable<IDIAA> {
    return this.sharedService.withSharedAccess(
      IAsset.Type.DIAA,
      id,
    ).get('diaas/' + id);
  }

  getActiveProcess(item: IDIAA): Observable<IProcess> {
    if (item.status === IDIAA.Status.CHECKING || item.status === IDIAA.Status.RUNNING) {
      return this.processes.getByTarget(item.id, IAsset.Type.DIAA);
    } else {
      return Observable.of(null);
    }
  }

  @mocksOnly(Observable.empty())
  create(item: IDIAACreate): Observable<IDIAA> {
    const request = jQuery.extend(true, {}, item);

    const observable = this.http.post('diaas', request, null);

    return AppHttp.execute(observable,
      (data: IDIAA) => {
        this.events.emit(IEvent.Type.CREATE_DIAA, data);
        this.events.emit(IEvent.Type.UPDATE_DIAA_LIST);
        this.notifications.create('DIAA created: ' + data.name);
      },
    );
  }

  @mocksOnly(Observable.empty())
  update(id: TObjectId, data: IDIAAUpdate): Observable<IDIAA> {
    const observable = this.http.put('diaas/' + id, data, null, {serialize: _ => _});

    return AppHttp.execute(observable,
      (data: IDIAA) => {
        this.events.emit(IEvent.Type.UPDATE_DIAA_LIST);
        this.events.emit(IEvent.Type.UPDATE_DIAA, { id: id });
        this.notifications.create('DIAA updated: ' + data.name);
      },
    );
  }

  @mocksOnly(Observable.empty())
  run(id: TObjectId, data: IDIAARun): Observable<IDIAA> {
    const observable = this.http.post('diaas/' + id + '/run', data, null, {serialize: _ => _});

    return AppHttp.execute(observable,
      (data: IDIAA) => {
        this.events.emit(IEvent.Type.UPDATE_DIAA_LIST);
        this.events.emit(IEvent.Type.UPDATE_DIAA, { id: id });
        this.notifications.create('DIAA executed: ' + data.name);
      },
    );
  }

  @mocksOnly(Observable.empty())
  'delete'(item: IDIAA): Observable<IObjectId> {
    const observable = this.http.delete('diaas/' + item.id);

    return AppHttp.execute(observable,
      () => {
        this.events.emit(IEvent.Type.UPDATE_DIAA_LIST);
        this.events.emit(IEvent.Type.DELETE_DIAA, { id: item.id });
        this.notifications.create('DIAA deleted: ' + item.name);
      },
    );
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_DIAA = 'CREATE_DIAA',
      UPDATE_DIAA_LIST = 'UPDATE_DIAA_LIST',
      UPDATE_DIAA = 'UPDATE_DIAA',
      DELETE_DIAA = 'DELETE_DIAA',
    }
  }
}
