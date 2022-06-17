import { HttpEventType } from '@angular/common/http';
import { Injectable, Injector } from '@angular/core';

import * as _ from 'lodash';
import 'rxjs/add/observable/empty';
import 'rxjs/add/operator/publish';
import { Observable } from 'rxjs/Observable';
import { ReplaySubject } from 'rxjs/ReplaySubject';

import config from '../config';
import { mocksMode } from '../core/core.mocks-only';
import { IAsset, IAssetService, IBackendList, TObjectId } from '../core/interfaces/common.interface';
import { AssetService } from '../core/services/asset.service';
import { IEvent } from '../core/services/event.service';
import { AppHttp, AppHttpError } from '../core/services/http.service';
import { MiscUtils } from '../utils/misc';
import { Omit } from '../utils/Omit';

import {
  IAlbum,
  IAlbumAugmentParams,
  IAlbumClone,
  IAlbumCreate,
  IAlbumImportFromS3,
  IAlbumImportLabelsFromS3,
  IAlbumImportVideoFromS3,
  IAlbumTagsSummary,
  IAlbumUpdate,
  IAlbumUploadParams,
  IPicture,
  IPictureSave,
  IPictureSearchParams,
} from './album.interface';

interface IAlbumImportFromS3Request extends Omit<IAlbumImportFromS3, 'applyLogTransformation'> {
  applyLogTransformation?: 'true' | 'false';
}

@Injectable()
export class AlbumService extends AssetService<IAsset.Type.ALBUM, IAlbum, IAlbumCreate, IAlbumUpdate>
  implements IAssetService.Cloneable<IAlbum, IAlbumClone> {

  protected _createEventType: IEvent.Type = IEvent.Type.CREATE_ALBUM;
  protected _updateEventType: IEvent.Type = IEvent.Type.UPDATE_ALBUM;
  protected _deleteEventType: IEvent.Type = IEvent.Type.DELETE_ALBUM;
  protected _listUpdateEventType: IEvent.Type = IEvent.Type.UPDATE_ALBUM_LIST;

  constructor(
    injector: Injector,
  ) {
    super(injector, IAsset.Type.ALBUM);
  }

  clone(id: TObjectId, clone: IAlbumClone): Observable<IAlbum> {
    if (clone.name === '') {
      delete clone.name;
    }
    const observable = this._sharedAccessService.withSharedAccess(
      id,
    ).post(`albums/${id}/copy`, clone);

    return AppHttp.execute(observable,
      (album: IAlbum) => {
        this._events.emit(IEvent.Type.CREATE_ALBUM, album);
        this._events.emit(IEvent.Type.UPDATE_ALBUM_LIST);
        this._notifications.create('Album cloned: ' + album.name);
      },
    );
  }

  augmentPictures(albumId: TObjectId, params: IAlbumAugmentParams): Observable<IAlbum> {
    const observable = this._http.post(`albums/${albumId}/augment`, params);

    return AppHttp.execute(observable,
      (album: IAlbum) => {
        this._events.emit(IEvent.Type.UPDATE_ALBUM_LIST);
        this._notifications.create('Augmented Album created: ' + album.name);
      },
    );
  }


  uploadPicture(albumId: TObjectId, uploadParams: IAlbumUploadParams): Observable<IPicture> {
    let notificationDataObservable = new ReplaySubject();
    let notificationData = {
      text: 'Initiating',
      progress: 0,
      file: uploadParams.file,
    };

    let notification = this._notifications.create(
      { type: config.notification.type.values.FILE_UPLOAD, dataAsync: notificationDataObservable},
      config.notification.level.values.INFO,
      { timeout: 0 },
    );

    notificationDataObservable.next(Object.assign({}, notificationData));

    const filename = uploadParams.filename || uploadParams.file.name.replace(/\.[^/.]+$/, ''); // remove extension of the file

    const observable = this._http.upload(`albums/${ albumId }/uploadPicture`, uploadParams.file, {
      filename,
    }, { // see XMLHttpRequestEventTarget listeners
      onHttpEvent: event => {
        if (event.type === HttpEventType.UploadProgress) {
          notificationDataObservable.next(Object.assign(notificationData, {
            text: 'Uploading',
            progress: event.total ? event.loaded / event.total * 100 : 100,
          }));
        }
      },
      catchWith: (error: AppHttpError) => {
        notification.options.timeout = 0;

        notificationDataObservable.next(Object.assign(notificationData, {
          level: config.notification.level.values.DANGER,
          text: error.message,
        }));

        return Observable.empty();
      },
    });

    return AppHttp.execute(observable,
      () => {
        this._events.emit(IEvent.Type.UPDATE_ALBUM_LIST);
        this._events.emit(IEvent.Type.UPDATE_ALBUM, { id: albumId });
        this._events.emit(IEvent.Type.UPDATE_PICTURE_LIST, { albumId });
        notification.options.timeout = Date.now() - notification.created + config.notification.defaults.timeout;

        notificationDataObservable.next(Object.assign(notificationData, {
          level: config.notification.level.values.SUCCESS,
          text: 'Uploaded',
          progress: 100,
        }));
      },
    );
  }


  uploadLabels(albumId: TObjectId, labelsFile: File): Observable<IAlbum> {
    const notificationDataObservable = new ReplaySubject();
    const notificationData = {
      text: 'Initiating',
      progress: 0,
      file: labelsFile,
    };

    const notification = this._notifications.create(
      { type: config.notification.type.values.FILE_UPLOAD, dataAsync: notificationDataObservable},
      config.notification.level.values.INFO,
      { timeout: 0 },
    );

    notificationDataObservable.next(Object.assign({}, notificationData));

    const observable = this._http.upload(`albums/${ albumId }/uploadLabels`, labelsFile, {}, {
      onHttpEvent: event => {
        if (event.type === HttpEventType.UploadProgress) {
          notificationDataObservable.next(Object.assign(notificationData, {
            text: 'Uploading',
            progress: event.total ? event.loaded / event.total * 100 : 100,
          }));
        }
      },
      catchWith: (error: AppHttpError) => {
        notification.options.timeout = 0;

        notificationDataObservable.next(Object.assign(notificationData, {
          level: config.notification.level.values.DANGER,
          text: error.message,
        }));

        return Observable.empty();
      },
    });

    return AppHttp.execute(observable,
      () => {
        this._events.emit(IEvent.Type.UPDATE_ALBUM_LIST);
        this._events.emit(IEvent.Type.UPDATE_ALBUM, { id: albumId });
        this._events.emit(IEvent.Type.UPDATE_PICTURE_LIST, { albumId });
        notification.options.timeout = Date.now() - notification.created + config.notification.defaults.timeout;

        notificationDataObservable.next(Object.assign(notificationData, {
          level: config.notification.level.values.SUCCESS,
          text: 'Uploaded',
          progress: 100,
        }));
      },
    );
  }

  importLabelsFromS3(albumId: TObjectId, importParams: IAlbumImportLabelsFromS3): Observable<any> {
    const observable = this._http.post(`albums/${ albumId }/importLabelsFromS3`, importParams);

    return AppHttp.execute(observable,
      () => {
        this._events.emit(IEvent.Type.UPDATE_ALBUM_LIST);
        this._events.emit(IEvent.Type.UPDATE_ALBUM, { id: albumId });
        this._events.emit(IEvent.Type.UPDATE_PICTURE_LIST, { albumId });
        this._notifications.create('Import complete');
      },
    );
  }

  importPicturesFromS3(albumId: TObjectId, importParams: IAlbumImportFromS3): Observable<IAlbum> {
    const notificationDataObservable = new ReplaySubject();

    const notificationData = {
      text: 'Initiating',
      progress: 0,
      file: importParams.labels,
    };

    const notification = importParams.labels
      ? this._notifications.create(
        { type: config.notification.type.values.FILE_UPLOAD, dataAsync: notificationDataObservable },
        config.notification.level.values.INFO,
        { timeout: 0 })
      : null;

    if (notification) {
      notificationDataObservable.next(Object.assign({}, notificationData));
    }

    // this omits 'labels' file
    const bodyParams: Omit<IAlbumImportFromS3Request, 'labels'> = {
      AWSS3BucketId: importParams.AWSS3BucketId,
      AWSRegion: importParams.AWSRegion,
      AWSS3BucketName: importParams.AWSS3BucketName,
      AWSAccessKey: importParams.AWSAccessKey,
      AWSSecretKey: importParams.AWSSecretKey,
      AWSSessionToken: importParams.AWSSessionToken,
      S3CSVPath: importParams.S3CSVPath,
      S3ImagesPath: importParams.S3ImagesPath,
    };

    if ('applyLogTransformation' in importParams) {
      bodyParams.applyLogTransformation = importParams.applyLogTransformation
        ? 'true'
        : 'false';
    }

    const observable = this._http.upload(`albums/${ albumId }/importPicturesFromS3`, importParams.labels, bodyParams,
      {
        onHttpEvent: event => {
          if (event.type === HttpEventType.UploadProgress && notification) {
            notificationDataObservable.next(Object.assign(notificationData, {
              text: 'Uploading labels',
              progress: event.total ? event.loaded / event.total * 100 : 100,
            }));
          }
        },
        catchWith: (error: AppHttpError) => {
          if (notification) {
            notification.options.timeout = 0;

            notificationDataObservable.next(Object.assign(notificationData, {
              level: config.notification.level.values.DANGER,
              text: error.message,
            }));

            return Observable.empty();
          }
          return Observable.throw(error);
        },
      });

    return AppHttp.execute(observable,
      () => {
        this._events.emit(IEvent.Type.UPDATE_ALBUM_LIST);
        this._events.emit(IEvent.Type.UPDATE_ALBUM, { id: albumId });
        this._events.emit(IEvent.Type.UPDATE_PICTURE_LIST, { albumId });
        if (notification) {
          notification.options.timeout = Date.now() - notification.created + config.notification.defaults.timeout;

          notificationDataObservable.next(Object.assign(notificationData, {
            level: config.notification.level.values.SUCCESS,
            text: 'Uploaded',
            progress: 100,
          }));
        }
      },
    );
  }

  importVideoFromS3(albumId: TObjectId, importParams: IAlbumImportVideoFromS3): Observable<IAlbum> {
    const observable: Observable<IAlbum> = this._http.post(`albums/${ albumId }/importVideoFromS3`, importParams);

    return AppHttp.execute(observable,
      () => {
        this._events.emit(IEvent.Type.UPDATE_ALBUM_LIST);
        this._events.emit(IEvent.Type.UPDATE_ALBUM, { id: albumId });
        this._events.emit(IEvent.Type.UPDATE_PICTURE_LIST, { albumId });
      },
    );
  }

  isMergeAvailable(items: IAlbum[]): boolean {
    return items.length > 1
      && _.every(items, _ => _.type === IAlbum.Type.SOURCE && _.status === IAlbum.Status.ACTIVE)
      && _.chain(items).countBy(_ => _.labelMode).keys().value().length === 1;
  }

  pictureList(album: IAlbum, params?: IPictureSearchParams): Observable<IBackendList<IPicture>> {
    const observable = this._sharedAccessService.withSharedAccess(
      album.id,
    ).get(`albums/${album.id}/pictures`, params);

    return AppHttp.execute(observable);
  }

  getPicture(albumId: TObjectId, pictureId: TObjectId): Observable<IPicture> {
    // GET '/albums/:albumId/pictures/:pictureId'
    return this._sharedAccessService.withSharedAccess(
      albumId,
    ).get(`albums/${ albumId }/pictures/${pictureId}`);
  }

  saveTags(albumId: TObjectId, pictureId: TObjectId, data: IPictureSave): Observable<IPicture> {
    const observable = this._http.put('albums/' + albumId + '/pictures/' + pictureId, data);
    return AppHttp.execute(observable, () => {
      this._events.emit(IEvent.Type.UPDATE_PICTURE_LIST, { albumId });
      this._events.emit(IEvent.Type.UPDATE_PICTURE, {id: pictureId, albumId: albumId});
      this._notifications.create('Picture updated');
    });
  }

  deletePicture(album: IAlbum, picture: IPicture): Observable<TObjectId> {
    // DELETE '/albums/:albumId/pictures/:pictureId'
    const observable = this._http.delete('albums/' + album.id + '/pictures/' + picture.id);

    return AppHttp.execute(observable,
      () => {
        this._events.emit(IEvent.Type.UPDATE_PICTURE_LIST, {albumId: album.id});
        this._events.emit(IEvent.Type.DELETE_PICTURE, {id: picture.id, albumId: album.id});
        this._notifications.create('Picture deleted');
      },
    );
  }

  getTags(albumId: TObjectId): Observable<IAlbumTagsSummary> {
    return this._sharedAccessService.withSharedAccess(
      albumId,
    ).get(`albums/${ albumId }/tags`);
  }

  exportUrl(id: TObjectId, token: string): Observable<string> {
    if (mocksMode) {
      return this._http.get(`albums/${id}/export`).map(_ => {
        return `data:application/octet-stream;charset=utf-8,` +
          encodeURIComponent(_);
      });
    }

    return this._sharedAccessService.getSharedAccessParams(id).map(data => {
      let url = `${config.api.base}albums/${id}/export?access_token=${token}`;

      if (data.shared_resource_id) {
        url += `&shared_resource_id=${data.shared_resource_id}`;
      }

      return url;
    });
  }

  download(id: TObjectId, token: string): Observable<boolean> {
    const observable = this.exportUrl(id, token).flatMap(url => {
      return MiscUtils.downloadUrl(url, `${id}.csv`);
    }).publish();

    observable.connect(); // make sure this runs irrespective of subscribers
    return observable;
  }

  getAugmentationDefaults(): Observable<IAlbumAugmentParams.Augmentation[]> {
    return this._http.get('config/cv-data-augmentation-defaults');
  }

  protected _hasProcess(item: IAlbum): boolean {
    return config.album.status.hasProcess[item.status];
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_ALBUM = 'CREATE_ALBUM',
      UPDATE_ALBUM = 'UPDATE_ALBUM',
      DELETE_ALBUM = 'DELETE_ALBUM',
      UPDATE_ALBUM_LIST = 'UPDATE_ALBUM_LIST',
      DELETE_PICTURE = 'DELETE_PICTURE',
      UPDATE_PICTURE = 'UPDATE_PICTURE',
      UPDATE_PICTURE_LIST = 'UPDATE_PICTURE_LIST',
    }
  }
}
