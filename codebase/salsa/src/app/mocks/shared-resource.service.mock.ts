import { Injectable, Injector } from '@angular/core';

import 'rxjs/add/observable/empty';
import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';

import { IAsset, IBackendList, TObjectId } from '../core/interfaces/common.interface';
import { ISharedResource } from '../core/interfaces/shared-resource.interface';
import { IUser } from '../core/interfaces/user.interface';
import { AppHttp } from '../core/services/http.service';
import {
  SharedResourceAccessHttpProxy,
  SharedResourceQueryParams,
  SharedResourceService,
} from '../core/services/shared-resource.service';

@Injectable()
export class SharedResourceServiceMock {
  constructor(
    private _injector: Injector,
  ) {}

  getSharedAccessResource(assetType: string, assetId: TObjectId): Observable<ISharedResource> {
    return Observable.of(null);
  }

  ensureSharedAccessLoaded(assetType: string, assetIds: TObjectId[]): Observable<boolean> {
    return Observable.of(true);
  }

  getSharedAccessParams(assetType: string, assetId: TObjectId): Observable<SharedResourceQueryParams> {
    return Observable.of({});
  }

  withSharedAccess(assetType: string, assetId: TObjectId): SharedResourceAccessHttpProxy {
    return this._injector.get(AppHttp);
  }

  list(params?: any): Observable<IBackendList<ISharedResource>> {
    return Observable.of({
      data: [],
      count: 0,
    });
  }

  get(id): Observable<ISharedResource> {
    return Observable.empty();
  }

  create(item): Observable<ISharedResource> {
    return Observable.of(item);
  }

  update(item): Observable<ISharedResource> {
    return Observable.of(item);
  }

  'delete'(item: ISharedResource): Observable<boolean> {
    return Observable.of(true);
  }

  getSharedAssetOwner(assetType: string, assetId: TObjectId): Observable<IUser> {
    return Observable.empty();
  }

  recipient(id: TObjectId): Observable<IUser> {
    return Observable.empty();
  }

  owner(id: TObjectId): Observable<IUser> {
    return Observable.empty();
  }

  public setHierarchyLink(assetType: string, assetId: TObjectId, parentAssetType: string, parentAssetId: TObjectId) {
  }

  bind<T extends IAsset.Type>(assetType: T): SharedResourceService.Bound<T> {
    return new SharedResourceService.Bound(<any> this, assetType);
  }

  bulkShare<T extends IAsset>(assetType: IAsset.Type, items: T[], recipientEmail: string): Observable<T[]> {
    return Observable.of([]);
  }
}
