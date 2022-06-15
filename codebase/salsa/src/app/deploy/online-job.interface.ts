import { IAsset, IAssetReference, TObjectId } from '../core/interfaces/common.interface';

export interface IOnlineTriggeredJob extends IAsset {
  target: IAssetReference; // target model/flow/etc
  enabled: boolean;
  options: IOnlineTriggeredJob.Options;
  status: IOnlineTriggeredJob.Status;
}

export interface IOnlineTriggeredJobCreate {
  name: string;
  description?: string;
  target: IAssetReference; // target model/flow/etc
  enabled: boolean;
  options: IOnlineTriggeredJob.CreateOptions;
}

export interface IOnlineTriggeredJobUpdate {
  name?: string;
  description?: string;
  enabled?: boolean;
}


export namespace IOnlineTriggeredJob {
  export enum Status {
    RUNNING = 'RUNNING',
    IDLE = 'IDLE',
  }

  export enum JobType {
    ONLINE_CV_PREDICTION = 'ONLINE_CV_PREDICTION',
    ONLINE_PREDICTION = 'ONLINE_PREDICTION',
  }

  export type Options = CVModelOptions | ModelOptions;

  export type CreateOptions = CVModelCreateOptions | ModelCreateOptions;

  export interface CVModelOptions {
    type: JobType.ONLINE_CV_PREDICTION;
    inputBucketId: TObjectId; // @see IS3Bucket;
    inputImagesPath: string; // path relative to bucket root
    outputAlbumId: TObjectId;
  }

  export interface ModelOptions {
    type: JobType.ONLINE_PREDICTION;
  }

  export interface CVModelCreateOptions {
    type: JobType.ONLINE_CV_PREDICTION;
    inputBucketId: TObjectId; // @see IS3Bucket;
    inputImagesPath: string; // path relative to bucket root
    outputAlbumName: string;
  }

  export interface ModelCreateOptions {
    type: JobType.ONLINE_PREDICTION;
  }
}
