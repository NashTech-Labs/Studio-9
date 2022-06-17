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
import { ProcessService } from '../core/services/process.service';

import { IReplay, IReplayCreate } from './replay.interface';

@Injectable()
export class ReplayService extends DataService implements IAssetService<IReplay, IReplayCreate> {
  readonly assetType: IAsset.Type = IAsset.Type.REPLAY;

  constructor(
    protected http: AppHttp,
    protected events: EventService,
    private notifications: NotificationService,
    private processes: ProcessService,
  ) {
    super(events);
  }

  create(data: IReplayCreate): Observable<IReplay> {
    const observable = this.http.post('replays', data, null, {serialize: (res) => {
      // flats structure [array of columnMappings] to flat array of columnMappings =
      // [{sourceColumn:{tableId: string,columnName: string},mappedColumn:{tableId: string,columnName: string}}]
      res.columnMappings = res.sources.reduce((result, source) => {
        source.columnMappings && source.columnMappings.forEach(item => {
          result.push(item);
        });
        return result;
      }, []);
      delete res.sources;
      return res;
    }});

    return AppHttp.execute(observable, (replay: IReplay) => {
      //this.list();
      this.events.emit(IEvent.Type.CREATE_REPLAY, replay);
      this.events.emit(IEvent.Type.UPDATE_REPLAY_LIST);
      this.notifications.create(`Replay created: ${replay.name}`);
    });
  }

  list(params?: IAssetListRequest): Observable<IBackendList<IReplay>> {
    const observable = this.http.get('replays', params);

    return AppHttp.execute(observable,
      (data: IBackendList<IReplay>) => {
        this._data.listMeta = {
          count: data.count,
          countPage: data.data.length,
        };
      },
    );
  }

  get(id: TObjectId): Observable<IReplay> {
    return this.http.get('replays/' + id);
  }

  getActiveProcess(item: IReplay): Observable<IProcess> {
    if (item.status === config.replay.status.values.RUNNING) {
      return this.processes.getByTarget(item.id, IAsset.Type.REPLAY);
    } else {
      return Observable.of(null);
    }
  }

  'delete'(item: IReplay): Observable<IObjectId> {
    return this.remove(item.id);
  }

  remove(id: TObjectId): Observable<IObjectId> {
    const observable = this.http.delete('replays/' + id);

    return AppHttp.execute(observable,
      () => {
        this.events.emit(IEvent.Type.UPDATE_REPLAY_LIST);
        this.events.emit(IEvent.Type.DELETE_REPLAY, { id });
        this.notifications.create('Replay deleted.'); // @todo what to show
      },
    );
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_REPLAY = 'CREATE_REPLAY',
      UPDATE_REPLAY_LIST = 'UPDATE_REPLAY_LIST',
      UPDATE_REPLAY = 'UPDATE_REPLAY',
      DELETE_REPLAY = 'DELETE_REPLAY',
    }
  }
}
