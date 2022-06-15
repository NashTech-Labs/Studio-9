import { InjectionToken } from '@angular/core';

import { Observable } from 'rxjs/Observable';

import { IProcess } from './process.interface';

export const ASSET_BASE_ROUTE: InjectionToken<string[]> =
  new InjectionToken('AssetBaseRoute');

export type TObjectId = string;

export interface IObjectId {
  id: TObjectId;
}

export interface IAsset {
  id: TObjectId;
  ownerId: TObjectId;
  name: string;
  created: string;
  updated: string;
  status?: string;
  description?: string;
  inLibrary?: boolean;
}

export interface IObjectHash<T> {
  [key: string]: T;
}

export interface IAssetReference<T extends IAsset.Type = IAsset.Type> {
  id: TObjectId;
  type: T;
}

export namespace IAsset {
  export enum Type {
    FLOW = 'FLOW',
    TABLE = 'TABLE',
    TABLE_STATS = 'TABLE_STATS',
    MODEL = 'MODEL',
    PREDICTION = 'PREDICTION',
    REPLAY = 'REPLAY',
    DASHBOARD = 'DASHBOARD',
    // PROCESS = 'PROCESS', // experimental
    ALBUM = 'ALBUM',
    CV_MODEL = 'CV_MODEL',
    CV_PREDICTION = 'CV_PREDICTION',
    OPTIMIZATION = 'OPTIMIZATION',
    DIAA = 'DIAA',
    ONLINE_JOB = 'ONLINE_JOB',
    ONLINE_API = 'ONLINE_API',
    S9_PROJECT = 'S9_PROJECT',
    PIPELINE = 'PIPELINE',
    EXPERIMENT = 'EXPERIMENT',
    DATASET = 'DATASET',
    SCRIPT_DEPLOYMENT = 'SCRIPT_DEPLOYMENT',
  }

  export interface StatusesDescription {
    labels: {[status: string]: string};
    styles: {[status: string]: string};
  }
}

export interface IAssetSaveParams {
  name?: string;
  description?: string;
}

export interface IAssetService<T extends IAsset, C> {
  readonly assetType: IAsset.Type;
  list(params?: IAssetListRequest): Observable<IBackendList<T>>;
  get(id: TObjectId): Observable<T>;
  create(createParams: C): Observable<T>;
  'delete'(item: T): Observable<IObjectId>;
  getActiveProcess(item: T): Observable<IProcess>;
  save?(id: TObjectId, saveParams: IAssetSaveParams): Observable<T>;
}

export namespace IAssetService {
  export interface Cloneable<T extends IAsset, CC> {
    clone(id: TObjectId, cloneParams: CC): Observable<T>;
  }

  export interface Importable<T extends IAsset, IP> {
    import?(file: File, importParams: IP): Observable<T>;
  }

  export function isCloneable<T extends IAsset>(
    s: IAssetService<T, any>,
  ): s is IAssetService<T, any> & Cloneable<T, any> {
    return 'clone' in s;
  }

  export function isImportable<T extends IAsset>(
    s: IAssetService<T, any>,
  ): s is IAssetService<T, any> & Importable<T, any> {
    return 'import' in s;
  }
}

export interface IBackendList<T> {
  data: T[];
  count: number;
}

export interface IListRequest {
  search?: string;
  order?: string;
  page?: number;
  page_size?: number;
  scope?: string;
}

export interface IAssetListRequest extends IListRequest {
  projectId?: TObjectId;
  folderId?: TObjectId;
}
