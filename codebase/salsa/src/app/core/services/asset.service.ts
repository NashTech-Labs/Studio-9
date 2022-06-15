import { Injector } from '@angular/core';

import { Observable } from 'rxjs/Observable';

import config from '../../config';
import {
  IAsset,
  IAssetListRequest, IAssetReference, IAssetSaveParams,
  IAssetService,
  IBackendList,
  IObjectId,
  TObjectId,
} from '../interfaces/common.interface';
import { IProcess } from '../interfaces/process.interface';

import { EventService, IEvent } from './event.service';
import { AppHttp } from './http.service';
import { NotificationService } from './notification.service';
import { ProcessService } from './process.service';
import { SharedResourceService } from './shared-resource.service';

export abstract class AssetService<
  AT extends IAsset.Type, // particular asset type
  T extends IAsset, // asset interface
  C, // create interface type
  U, // update interface type
  FT extends T = T, // full asset type for assets with extended data on single item responses
> implements IAssetService<T, C> {

  protected readonly _sharedAccessService: SharedResourceService.Bound<AT>;
  protected readonly _http: AppHttp;
  protected readonly _notifications: NotificationService;
  protected readonly _events: EventService;
  protected readonly _processes: ProcessService;

  protected abstract readonly _updateEventType: IEvent.Type;
  protected abstract readonly _createEventType: IEvent.Type;
  protected abstract readonly _deleteEventType: IEvent.Type;
  protected abstract readonly _listUpdateEventType: IEvent.Type;

  protected constructor(
    injector: Injector,
    readonly assetType: AT,
    protected readonly _baseUrl: string = config.asset.aliasesPlural[assetType],
  ) {
    this._sharedAccessService = injector.get(SharedResourceService).bind(assetType);
    this._http = injector.get(AppHttp);
    this._notifications = injector.get(NotificationService);
    this._events = injector.get(EventService);
    this._processes = injector.get(ProcessService);
  }

  list(params?: IAssetListRequest): Observable<IBackendList<T>> {
    return this._http.get(this._baseUrl, params);
  }

  get(id: TObjectId): Observable<FT> {
    return this._sharedAccessService.withSharedAccess(
      id,
    ).get(this._baseUrl + '/' + id).do(_ => this._setHierarchyLinks(_));
  }

  create(data: C): Observable<FT> {
    const observable = this._http.post(this._baseUrl, data);

    return AppHttp.execute(observable, item => {
      this._setHierarchyLinks(item);
      this._events.emit(this._listUpdateEventType);
      this._events.emit(this._createEventType, item);
      this._notifications.create(config.asset.labels[this.assetType] + ' has been created: ' + item.name);
    });
  }

  update(id: TObjectId, data: U): Observable<FT> {
    const observable = this._http.put(this._baseUrl + '/' + id, data);

    return AppHttp.execute(observable, item => {
      this._setHierarchyLinks(item);
      this._events.emit(this._listUpdateEventType);
      this._events.emit(this._updateEventType, { id });
      this._notifications.create(config.asset.labels[this.assetType] + ' has been updated: ' + item.name);
    });
  }

  'delete'(item: T): Observable<IObjectId> {
    const observable = this._http.delete(this._baseUrl + '/' + item.id);

    return AppHttp.execute(observable,
      () => {
        this._events.emit(this._listUpdateEventType);
        this._events.emit(this._deleteEventType, { id: item.id });
        this._notifications.create(config.asset.labels[this.assetType] + ' has been deleted: ' + item.name);
      },
    );
  }

  save(id: TObjectId, saveParams: IAssetSaveParams): Observable<T> {
    const observable = this._http.post(this._baseUrl + '/' + id + '/save', saveParams);

    return AppHttp.execute(observable, () => {
      this._events.emit(this._listUpdateEventType);
      this._events.emit(this._updateEventType, { id });
      this._notifications.create(config.asset.labels[this.assetType] + ' has been saved.');
    });
  }

  getActiveProcess(item: T): Observable<IProcess> {
    if (this._hasProcess(item)) {
      return this._processes.getByTarget(item.id, this.assetType);
    } else {
      return Observable.of(null);
    }
  }

  //noinspection JSUnusedLocalSymbols
  protected _hasProcess(item: T): boolean {
    return false;
  }

  //noinspection JSUnusedLocalSymbols
  protected _getChildAssets(item: FT): IAssetReference[] {
    return [];
  }

  private _setHierarchyLinks(item: FT): void {
    try {
      const childAssets: IAssetReference[] = this._getChildAssets(item);
      childAssets.forEach(({type, id}) => {
        this._sharedAccessService.setHierarchyLink(type, id, item.id);
      });
    } catch (e) {
      console.error(`Error while working with assets hierarchy: ${e}`);
    }
  }
}
