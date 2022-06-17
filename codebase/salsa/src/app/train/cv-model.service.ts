import { Injectable } from '@angular/core';

import { Observable } from 'rxjs/Observable';

import {
  IAsset,
  IAssetListRequest,
  IAssetSaveParams,
  IAssetService,
  IBackendList,
  TObjectId,
} from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { NotificationService } from '../core/services/notification.service';
import { ProcessService } from '../core/services/process.service';
import { SharedResourceService } from '../core/services/shared-resource.service';
import { MiscUtils } from '../utils/misc';

import { ICVModel, ICVModelImport, ICVModelUpdate } from './cv-model.interface';

@Injectable()
export class CVModelService implements IAssetService<ICVModel, null>, IAssetService.Importable<ICVModel, ICVModelImport> {
  readonly assetType: IAsset.Type = IAsset.Type.CV_MODEL;

  protected _type = IAsset.Type.CV_MODEL;

  constructor(
    protected http: AppHttp,
    protected events: EventService,
    private sharedService: SharedResourceService,
    private processes: ProcessService,
    private notifications: NotificationService,
  ) {
  }

  list(params?: IAssetListRequest): Observable<IBackendList<ICVModel>> {
    // GET '/cv-models'
    return this.http.get('cv-models', params);
  }

  get(id: TObjectId): Observable<ICVModel> {
    return this.sharedService.withSharedAccess(
      IAsset.Type.CV_MODEL,
      id,
    ).get('cv-models/' + id);
  }

  getActiveProcess(item: ICVModel): Observable<IProcess> {
    if (
      item.status === ICVModel.Status.TRAINING
      || item.status === ICVModel.Status.PREDICTING
      || item.status === ICVModel.Status.SAVING
    ) {
      return this.processes.getByTarget(item.id, IAsset.Type.CV_MODEL);
    } else {
      return Observable.of(null);
    }
  }

  import(file: File, params: ICVModelImport): Observable<ICVModel> {
    const observable = this.http.monitoredUpload(`cv-models/import`, file, {
      name: params.name || file.name,
    }).flatMap((cvmodel: ICVModel) => this.get(cvmodel.id));

    return AppHttp.execute(observable,
      () => {
        this.events.emit(IEvent.Type.UPDATE_CV_MODEL_LIST);
      },
    );
  }

  create(data: any): Observable<ICVModel> {
    return Observable.throw('Not available');
  }

  update(modelId: TObjectId, data: ICVModelUpdate): Observable<ICVModel> {
    // PUT '/models/:id'
    const observable = this.http.put('cv-models/' + modelId, data);

    return AppHttp.execute(observable,
      (data: ICVModel) => {
        this.events.emit(IEvent.Type.UPDATE_CV_MODEL_LIST);
        this.events.emit(IEvent.Type.UPDATE_CV_MODEL, { id: modelId });
        this.notifications.create('Model updated: ' + data.name);
      },
    );
  }

  save(id: TObjectId, saveParams: IAssetSaveParams): Observable<ICVModel> {
    const observable = this.http.post('cv-models/' + id + '/save', saveParams);

    return AppHttp.execute(observable, () => {
      this.events.emit(IEvent.Type.UPDATE_CV_MODEL_LIST);
      this.events.emit(IEvent.Type.UPDATE_CV_MODEL, { id });
      this.notifications.create('Model saved.');
    });
  }

  'delete'(item: ICVModel): Observable<ICVModel> {
    // DELETE '/models/:id'
    const observable = this.http.delete('cv-models/' + item.id);

    return AppHttp.execute(observable,
      () => {
        this.events.emit(IEvent.Type.UPDATE_CV_MODEL_LIST);
        this.events.emit(IEvent.Type.DELETE_CV_MODEL, { id: item.id });

        this.notifications.create('Model deleted: ' + item.name);
      },
    );
  }

  exportUrl(id: TObjectId): Observable<string> {
    return this.sharedService.withSharedAccess(IAsset.Type.CV_MODEL, id)
      .get(`cv-models/${id}/export`);
  }

  download(id: TObjectId): Observable<boolean> {
    const observable = this.exportUrl(id).flatMap(url => {
      return MiscUtils.downloadUrl(url, `${id}.bin`);
    }).publish();

    observable.connect(); // make sure this runs irrespective of subscribers
    return observable;
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      UPDATE_CV_MODEL_LIST = 'UPDATE_CV_MODEL_LIST',
      UPDATE_CV_MODEL = 'UPDATE_CV_MODEL',
      DELETE_CV_MODEL = 'DELETE_CV_MODEL',
    }
  }
}
