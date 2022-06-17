import { Injectable, Injector } from '@angular/core';

import { Observable } from 'rxjs/Observable';
import { of } from 'rxjs/observable/of';
import { map } from 'rxjs/operators/map';
import { mergeMap as flatMap } from 'rxjs/operators/mergeMap';

import config from '../config';
import { mocksMode } from '../core/core.mocks-only';
import { backendUrl } from '../core/helpers/backend-url.helper';
import { IAsset, IBackendList, TObjectId } from '../core/interfaces/common.interface';
import { AssetService } from '../core/services/asset.service';
import { IEvent } from '../core/services/event.service';
import { AppHttp } from '../core/services/http.service';
import { Downloader } from '../utils/downloader';
import { MiscUtils } from '../utils/misc';

import { BinaryDataset } from './dataset.interfaces';

@Injectable()
export class BinaryDatasetService
  extends AssetService<IAsset.Type.DATASET, BinaryDataset, BinaryDataset.CreateRequest, BinaryDataset.UpdateRequest> {

  protected readonly _createEventType: IEvent.Type = IEvent.Type.CREATE_DATASET;
  protected readonly _updateEventType: IEvent.Type = IEvent.Type.UPDATE_DATASET;
  protected readonly _deleteEventType: IEvent.Type = IEvent.Type.DELETE_DATASET;
  protected readonly _listUpdateEventType: IEvent.Type = IEvent.Type.UPDATE_DATASET_LIST;

  constructor(
    injector: Injector,
  ) {
    super(injector, IAsset.Type.DATASET);
  }

  listFiles(datasetId: TObjectId, data: BinaryDataset.FileSearchParams): Observable<IBackendList<BinaryDataset.File>> {
    return this._sharedAccessService.withSharedAccess(datasetId)
      .get(backendUrl(this._baseUrl, [datasetId, 'ls']), data);
  }

  downloadAllDatasetFiles(datasetId: TObjectId, token: string): Observable<boolean> {
    const observable = this._downloadUrl(datasetId, token)
      .pipe(
        flatMap(url => this._mapDownloadUrl(url)),
        flatMap(url => {
          return MiscUtils.downloadUrl(url);
        }),
      )
      .publish();

    observable.connect(); // make sure this runs irrespective of subscribers

    return observable;
  }

  downloadSelectedDatasetFiles(datasetId: TObjectId, files: string[], token: string): Observable<boolean> {
    const observable = this._downloadUrl(datasetId, token)
      .pipe(
        flatMap(url => this._mapDownloadUrl(url)),
        flatMap(url => Downloader.post(url, { files })),
        map(file => !!file),
      )
      .publish();

    observable.connect(); // make sure this runs irrespective of subscribers

    return observable;
  }

  downloadFile(datasetId: TObjectId, url: string): Observable<boolean> {
    const observable = of(url)
      .pipe(
        flatMap(url => this._mapDownloadUrl(url)),
        flatMap(url => MiscUtils.downloadUrl(url)),
      )
      .publish();

    observable.connect(); // make sure this runs irrespective of subscribers

    return observable;
  }

  deleteFile(datasetId: TObjectId, filePath: string): Observable<void> {
    const observable = this._http.delete(backendUrl(
      this._baseUrl,
      [datasetId, 'files', ...filePath.split('/')],
    ));

    return AppHttp.execute(observable,
      () => {
        this._events.emit(IEvent.Type.UPDATE_DATASET_FILE_LIST);
        this._events.emit(IEvent.Type.DELETE_DATASET_FILE, { datasetId, filename: filePath });
        this._notifications.create('Dataset file has been deleted: ' + filePath);
      },
    );
  }

  importFromS3(
    datasetId: TObjectId,
    importParams: BinaryDataset.ImportFromS3Request,
  ): Observable<BinaryDataset> {
    const observable = this._http.post(
      backendUrl(this._baseUrl, [datasetId, 'import']),
      importParams,
    );

    return AppHttp.execute(observable,
      () => {
        this._events.emit(IEvent.Type.UPDATE_DATASET_LIST);
        this._events.emit(IEvent.Type.UPDATE_DATASET, { id: datasetId });
        this._notifications.create('S3 import has been started');
      },
    );
  }

  exportToS3(
    datasetId: TObjectId,
    exportParams: BinaryDataset.ExportToS3Request,
  ): Observable<BinaryDataset> {
    const observable = this._http.post(
      backendUrl(this._baseUrl, [datasetId, 'export']),
      exportParams,
    );

    return AppHttp.execute(observable,
      () => {
        this._events.emit(IEvent.Type.UPDATE_DATASET_LIST);
        this._events.emit(IEvent.Type.UPDATE_DATASET, { id: datasetId });
        this._notifications.create('S3 export has been started');
      },
    );
  }

  uploadFiles(
    datasetId: TObjectId,
    files: File[],
  ): Observable<BinaryDataset.File> {
    const observable = this._http.monitoredUpload(
      backendUrl(this._baseUrl, [datasetId, 'files']),
      files,
      {},
    );

    return AppHttp.execute(observable,
      () => {
        this._events.emit(IEvent.Type.UPDATE_DATASET_LIST);
        this._events.emit(IEvent.Type.UPDATE_DATASET, { id: datasetId });
      },
    );
  }

  protected _hasProcess(item: BinaryDataset): boolean {
    return config.binaryDataset.status.hasProcess[item.status];
  }

  private _downloadUrl(datasetId: TObjectId, token: string): Observable<string> {
    return this._sharedAccessService.getSharedAccessParams(datasetId)
      .map(data => {
        let url = `${config.api.base}datasets/${datasetId}/download?access_token=${token}`;

        if (data.shared_resource_id) {
          url += `&shared_resource_id=${data.shared_resource_id}`;
        }

        return url;
      });
  }

  private _mapDownloadUrl(url: string): Observable<any> {
    if (mocksMode) {
      return this._http.get(url).map(_ => {
        return `data:application/octet-stream;charset=utf-8,` + encodeURIComponent(_);
      });
    }

    return of(url);
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_DATASET = 'CREATE_DATASET',
      UPDATE_DATASET = 'UPDATE_DATASET',
      DELETE_DATASET = 'DELETE_DATASET',
      UPDATE_DATASET_LIST = 'UPDATE_DATASET_LIST',
      DELETE_DATASET_FILE = 'DELETE_DATASET_FILE',
      UPDATE_DATASET_FILE_LIST = 'UPDATE_DATASET_FILE_LIST',
    }
  }
}
