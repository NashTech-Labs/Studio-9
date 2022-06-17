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

import { ICVPrediction, ICVPredictionCreate, ICVPredictionUpdate } from './cv-prediction.interface';

@Injectable()
export class CVPredictionService extends DataService implements IAssetService<ICVPrediction, ICVPredictionCreate> {
  readonly assetType: IAsset.Type = IAsset.Type.CV_PREDICTION;

  constructor(
    protected http: AppHttp,
    protected events: EventService,
    private notifications: NotificationService,
    private processes: ProcessService,
  ) {
    super(events);
  }

  create(data: ICVPredictionCreate): Observable<ICVPrediction> {
    const observable = this.http.post('cv-predictions', data);

    return AppHttp.execute(observable, (prediction: ICVPrediction) => {
      this.events.emit(IEvent.Type.CREATE_CV_PREDICTION, prediction);
      this.events.emit(IEvent.Type.UPDATE_CV_PREDICTION_LIST);
      this.notifications.create('CV Prediction created: ' + prediction.name);
    });
  }

  list(params?: IAssetListRequest): Observable<IBackendList<ICVPrediction>> {
    return this.http.get('cv-predictions', params);
  }

  get(id: TObjectId): Observable<ICVPrediction> {
    return this.http.get('cv-predictions/' + id);
  }

  getActiveProcess(item: ICVPrediction): Observable<IProcess> {
    if (item.status === config.cvPrediction.status.values.RUNNING) {
      return this.processes.getByTarget(item.id, IAsset.Type.CV_PREDICTION);
    } else {
      return Observable.of(null);
    }
  }

  'delete'(item: ICVPrediction): Observable<IObjectId> {
    return this.remove(item.id);
  }

  update(id: TObjectId, data: ICVPredictionUpdate): Observable<ICVPrediction> {
    const observable = this.http.put('cv-predictions/' + id, data);

    return AppHttp.execute(observable,
      (data: ICVPrediction) => {
        this.events.emit(IEvent.Type.UPDATE_CV_PREDICTION_LIST);
        this.events.emit(IEvent.Type.UPDATE_CV_PREDICTION, { id: id });
        this.notifications.create('CV Prediction updated: ' + data.name);
      },
    );
  }

  remove(id: TObjectId): Observable<IObjectId> {
    const observable = this.http.delete('cv-predictions/' + id);

    return AppHttp.execute(observable,
      () => {
        this.events.emit(IEvent.Type.UPDATE_CV_PREDICTION_LIST);
        this.events.emit(IEvent.Type.DELETE_CV_PREDICTION, { id });
        this.notifications.create('CV Prediction deleted.');
      },
    );
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_CV_PREDICTION = 'CREATE_CV_PREDICTION',
      UPDATE_CV_PREDICTION_LIST = 'UPDATE_CV_PREDICTION_LIST',
      UPDATE_CV_PREDICTION = 'UPDATE_CV_PREDICTION',
      DELETE_CV_PREDICTION = 'DELETE_CV_PREDICTION',
    }
  }
}
