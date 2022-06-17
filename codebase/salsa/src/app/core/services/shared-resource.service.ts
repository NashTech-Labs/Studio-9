import { Injectable } from '@angular/core';

import 'rxjs/add/observable/empty';
import 'rxjs/add/observable/forkJoin';
import 'rxjs/add/observable/fromPromise';
import 'rxjs/add/observable/of';
import 'rxjs/add/observable/timer';
import 'rxjs/add/operator/first';
import 'rxjs/add/operator/mapTo';
import { Observable } from 'rxjs/Observable';
import { Subject } from 'rxjs/Subject';

import config from '../../config';
import { IAsset, IBackendList, TObjectId } from '../interfaces/common.interface';
import { ISharedResource, ISharedResourceCreate, ISharedResourceListRequest } from '../interfaces/shared-resource.interface';
import { IUser } from '../interfaces/user.interface';

import { EventService, IEvent } from './event.service';
import { AppHttp, AppHttpRequestOptions } from './http.service';
import { NotificationService } from './notification.service';

export interface SharedResourceQueryParams {
  shared_resource_id?: string;
}

export interface SharedResourceAccessHttpProxy {
  get: (url: string, params?: any, options?: AppHttpRequestOptions) => Observable<any>;
  post: (url: string, body: any, params?: any, options?: AppHttpRequestOptions) => Observable<any>;
}

@Injectable()
export class SharedResourceService {

  protected _sharedAccessResources: {[T in IAsset.Type]?: {[AssetId: string]: ISharedResource<T>}} = {};
  protected _assetHierarchyLinks: {[T in IAsset.Type]?: {[AssetId: string]: {
    assetType: IAsset.Type,
    assetId: TObjectId,
  }}} = {};

  protected _readyPromise: Promise<boolean>;
  protected _sharedResourceUpdateTimer: Observable<number>;
  protected _sharedResourceUpdated: Subject<boolean>;

  constructor(
    protected http: AppHttp,
    protected events: EventService,
    private notifications: NotificationService,
  ) {
    Object.keys(IAsset.Type).forEach(assetType => {
      this._sharedAccessResources[assetType] = {};
      this._assetHierarchyLinks[assetType] = {};
    });

    this._sharedResourceUpdated = new Subject<boolean>();
    this._readyPromise = this._sharedResourceUpdated.first().toPromise();

    // schedule shared resource updates
    this._sharedResourceUpdateTimer = Observable.timer(0, 60 * 1000);
    this._sharedResourceUpdateTimer.subscribe(() => {
      this._loadSharedAccessResources();
    });
  }

  bind<T extends IAsset.Type>(assetType: T): SharedResourceService.Bound<T> {
    return new SharedResourceService.Bound(this, assetType);
  }

  ensureSharedAccessLoaded(assetType: IAsset.Type, assetIds: TObjectId[]): Observable<boolean> {
    return Observable.fromPromise(this._readyPromise).flatMap(() => {
      const missingIds = assetIds.filter(assetId => !this._getSharedResourceIdSync(assetType, assetId));
      if (missingIds.length) {
        return this._loadSharedAccessResources().mapTo(true);
      }
      return Observable.of(true);
    });
  }

  getSharedAccessParams(assetType: IAsset.Type, assetId: TObjectId): Observable<SharedResourceQueryParams> {
    return this._getSharedAccessResourceId(assetType, assetId).map(sharedResourceId => {
      if (sharedResourceId) {
        return {'shared_resource_id': sharedResourceId};
      }
      return {};
    });
  }

  withSharedAccess(assetType: IAsset.Type, assetId: TObjectId): SharedResourceAccessHttpProxy {
    return {
      get: (url: string, params?: any, options?: AppHttpRequestOptions) => {
        return this.getSharedAccessParams(assetType, assetId).flatMap(_ => {
          return this.http.get(url, Object.assign({}, params || {}, _), options);
        }).share();
      },
      post: (url: string, body: any, params?: any, options?: AppHttpRequestOptions) => {
        return this.getSharedAccessParams(assetType, assetId).flatMap(_ => {
          return this.http.post(url, body, Object.assign({}, params || {}, _), options);
        }).share();
      },
    };
  }

  list(params?: ISharedResourceListRequest): Observable<IBackendList<ISharedResource>> {
    return this.http.get('shares', params);
  }

  get(id: TObjectId): Observable<ISharedResource> {
    return this.http.get('shares/' + id);
  }

  bulkShare<T extends IAsset>(assetType: IAsset.Type, items: T[], recipientEmail: string): Observable<T[]> {
    interface RequestResult {
      success: boolean;
      item: T;
    }

    const actions = items.map((item: T) => {
      return AppHttp.execute(this.http.post(`shares`, {
        assetId: item.id,
        assetType: assetType,
        recipientEmail: recipientEmail,
      }, {}, {
        noNotifications: true,
        deserialize: (): RequestResult => {
          return { success: true, item: item };
        },
        catchWith: (): Observable<RequestResult> => {
          return Observable.of({ success: false, item: item });
        },
      }));
    });

    const bulkAction: Observable<T[]> = Observable
      .forkJoin(...actions)
      .flatMap((responses: RequestResult[]) => {
        const doneItems: T[] = [], errorItems: T[] = [];
        responses.forEach((response) => {
          if (response.success) {
            doneItems.push(response.item);
          } else {
            errorItems.push(response.item);
          }
        });

        if (doneItems.length) {
          let names = doneItems.map(_ => _.name).join('", "');
          this.events.emit(IEvent.Type.UPDATE_SHARED_RESOURCE_LIST);
          this.notifications.create(`${config.asset.labelsPlural[assetType]} "${names}" successfully shared.`);
        }

        if (errorItems.length) {
          let names = errorItems.map(_ => _.name).join('", "');
          this.notifications.create(`These ${config.asset.labelsPlural[assetType]} caused a sharing error: "${names}"`, config.notification.level.values.DANGER);
        }
        return Observable.of(doneItems);
      })
      .share();

    return AppHttp.execute(bulkAction);
  }

  create<T extends IAsset.Type>(item: ISharedResourceCreate<T>): Observable<ISharedResource<T>> {
    const data = jQuery.extend(true, {}, item);

    // POST '/flows'
    const observable = this.http.post('shares', data);

    return AppHttp.execute(observable,
      (data: ISharedResource) => {
        this.events.emit(IEvent.Type.CREATE_SHARED_RESOURCE, data);
        this.events.emit(IEvent.Type.UPDATE_SHARED_RESOURCE_LIST);
        this.notifications.create(`${config.asset.labels[data.assetType]} successfully shared.`);
      },
    );
  }

  'delete'(item: ISharedResource): Observable<boolean> {
    // DELETE '/flows/:id'
    const observable = this.http.delete('shares/' + item.id);

    return AppHttp.execute(observable, () => {
      this.events.emit(IEvent.Type.UPDATE_SHARED_RESOURCE_LIST);
      this.events.emit(IEvent.Type.DELETE_SHARED_RESOURCE, { id: item.id });
      this.notifications.create(`${config.asset.labels[item.assetType]} shared access revoked.`);
    });
  }

  getSharedAssetOwner(assetType: IAsset.Type, assetId: TObjectId): Observable<IUser> {
    return this._getSharedAccessResourceId(assetType, assetId).flatMap(sharedResourceId => {
      return sharedResourceId ? this.owner(sharedResourceId) : Observable.empty();
    });
  }

  recipient(id: TObjectId): Observable<IUser> {
    return this.http.get('shares/' + id + '/recipient');
  }

  owner(id: TObjectId): Observable<IUser> {
    return this.http.get('shares/' + id + '/owner');
  }

  setHierarchyLink(
    assetType: IAsset.Type,
    assetId: TObjectId,
    parentAssetType: IAsset.Type,
    parentAssetId: TObjectId,
  ) {
    this._assetHierarchyLinks[assetType][assetId] = {
      assetType: parentAssetType,
      assetId: parentAssetId,
    };
  }

  private _loadSharedAccessResources(): Observable<void> {
    const observable = this.http.get('me/shares', {}, {noNotifications: true})
      .catch(() => Observable.empty())
      .map((list: IBackendList<ISharedResource>) => {
        this._pushSharedAccessResources(list.data);
        this._sharedResourceUpdated.next(true);
      });

    return AppHttp.execute(observable);
  }

  private _getSharedAccessResourceId(assetType: IAsset.Type, assetId: TObjectId): Observable<TObjectId> {
    return Observable.fromPromise(this._readyPromise).map(() => {
      return this._getSharedResourceIdSync(assetType, assetId);
    });
  }

  private _getSharedResourceIdSync(assetType: IAsset.Type, assetId: TObjectId): TObjectId {
    const sharedResource: ISharedResource = this._sharedAccessResources[assetType][assetId];
    if (sharedResource) {
      return sharedResource.id;
    }

    const sharedResourceLink = this._assetHierarchyLinks[assetType][assetId];
    if (sharedResourceLink) {
      return this._getSharedResourceIdSync(sharedResourceLink.assetType, sharedResourceLink.assetId);
    }

    return null;
  }

  private _pushSharedAccessResources(list: ISharedResource[]) {
    list.forEach(share => {
      this._sharedAccessResources[share.assetType][share.assetId] = <ISharedResource<any>> share;
    });
  }
}

export namespace SharedResourceService {
  export class Bound<T extends IAsset.Type> {
    constructor(
      private readonly _service: SharedResourceService,
      readonly assetType: T,
    ) {}

    getSharedAccessParams(assetId: TObjectId): Observable<SharedResourceQueryParams> {
      return this._service.getSharedAccessParams(this.assetType, assetId);
    }

    withSharedAccess(assetId: TObjectId): SharedResourceAccessHttpProxy {
      return this._service.withSharedAccess(this.assetType, assetId);
    }

    public setHierarchyLink(
      assetType: IAsset.Type,
      assetId: TObjectId,
      parentAssetId: TObjectId,
    ) {
      this._service.setHierarchyLink(assetType, assetId, this.assetType, parentAssetId);
    }
  }
}

declare module './event.service' {
  export namespace IEvent {
    export const enum Type {
      CREATE_SHARED_RESOURCE = 'CREATE_SHARED_RESOURCE',
      UPDATE_SHARED_RESOURCE_LIST = 'UPDATE_SHARED_RESOURCE_LIST',
      DELETE_SHARED_RESOURCE = 'DELETE_SHARED_RESOURCE',
    }
  }
}
