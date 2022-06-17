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

import { IPrediction, IPredictionCreate, IPredictionUpdate } from './prediction.interface';

@Injectable()
export class PredictionService extends DataService implements IAssetService<IPrediction, IPredictionCreate> {
  readonly assetType: IAsset.Type = IAsset.Type.PREDICTION;

  constructor(
    protected http: AppHttp,
    protected events: EventService,
    private notifications: NotificationService,
    private processes: ProcessService,
  ) {
    super(events);
  }

  create(data: IPredictionCreate): Observable<IPrediction> {
    // POST '/predictions'
    const observable = this.http.post('predictions', data);

    return AppHttp.execute(observable, (prediction: IPrediction) => {
      this.events.emit(IEvent.Type.CREATE_PREDICTION, prediction);
      this.events.emit(IEvent.Type.UPDATE_PREDICTION_LIST);
      this.notifications.create('Prediction created: ' + prediction.name);
    });
  }

  list(params?: IAssetListRequest): Observable<IBackendList<IPrediction>> {
    // GET '/predictions'
    const observable = this.http.get('predictions', params);

    return AppHttp.execute(observable,
      (data: IBackendList<IPrediction>) => {
        //this._data.list = data.data || [];

        this._data.listMeta = {
          count: data.count,
          countPage: data.data.length,
        };

        //this._observables.list.next(data);
      },
    );
  }

  get(id: string): Observable<IPrediction> {
    // GET '/predictions/:id'
    return this.http.get('predictions/' + id);
  }

  getActiveProcess(item: IPrediction): Observable<IProcess> {
    if (item.status === config.prediction.status.values.RUNNING) {
      return this.processes.getByTarget(item.id, IAsset.Type.PREDICTION);
    } else {
      return Observable.of(null);
    }
  }

  update(id: TObjectId, data: IPredictionUpdate): Observable<IPrediction> {
    const observable = this.http.put('predictions/' + id, data);

    return AppHttp.execute(observable,
      (data: IPrediction) => {
        this.events.emit(IEvent.Type.UPDATE_PREDICTION_LIST);
        this.events.emit(IEvent.Type.UPDATE_PREDICTION, { id: id });
        this.notifications.create('Prediction updated: ' + data.name);
      },
    );
  }

  'delete'(item: IPrediction): Observable<IObjectId> {
    return this.remove(item.id);
  }

  remove(id: string): Observable<IObjectId> {
    // DELETE '/predictions/:id'
    const observable = this.http.delete('predictions/' + id);

    return AppHttp.execute(observable, () => {
      this.events.emit(IEvent.Type.UPDATE_PREDICTION_LIST);
      this.events.emit(IEvent.Type.DELETE_PREDICTION, { id });
      this.notifications.create('Prediction deleted.'); // TODO: should backend return object instead of id?
    });
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_PREDICTION = 'CREATE_PREDICTION',
      UPDATE_PREDICTION_LIST = 'UPDATE_PREDICTION_LIST',
      UPDATE_PREDICTION = 'UPDATE_PREDICTION',
      DELETE_PREDICTION = 'DELETE_PREDICTION',
    }
  }
}
