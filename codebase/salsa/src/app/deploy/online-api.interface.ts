import { IAsset, IAssetReference } from '../core/interfaces/common.interface';
import { Pipeline } from '../pipelines/pipeline.interfaces';

export interface IOnlineAPI<T extends IOnlineAPI.Type = IOnlineAPI.Type> extends IAsset {
  target: IAssetReference<T>; // target model/flow/etc
  enabled: boolean;
  status: IOnlineAPI.Status;
  params: IOnlineAPI.Params<T>;
}

export namespace IOnlineAPI {
  export type Type = IAsset.Type.PIPELINE | IAsset.Type.CV_MODEL | IAsset.Type.MODEL;

  export type Params<T extends Type> =
    T extends IAsset.Type.PIPELINE ? { steps: Pipeline.Step[] }
    : never;

  export enum Status {
    ACTIVE = 'ACTIVE',
    PREPARING = 'PREPARING',
    INACTIVE = 'INACTIVE',
  }
}

export interface IOnlineAPICreate<T extends IOnlineAPI.Type = IOnlineAPI.Type> {
  name: string;
  description?: string;
  target: IAssetReference<T>; // target model/flow/etc
  secret: string; // secret to use for authentication
  enabled: boolean;
  params: IOnlineAPI.Params<T>;
}

export interface IOnlineAPIUpdate {
  name?: string;
  description?: string;
  enabled?: boolean;
}
