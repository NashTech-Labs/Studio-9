import { IAsset, TObjectId } from './common.interface';

export interface IProcess {
  id: TObjectId;
  ownerId: TObjectId; // TEMP TEMP TEMP // TODO: should it be here?
  target: IAsset.Type; // see config.asset
  targetId: TObjectId;
  status: IProcess.Status;
  progress: number; // from 0 to 1
  estimate?: number; // experimental // seconds?
  created: string; // Date
  started: string; // Date
  completed?: string; // Date
  cause?: string;
  jobType: IProcess.JobType;
}

export namespace IProcess {
  export enum Status {
    RUNNING = 'RUNNING',
    COMPLETED = 'COMPLETED',
    CANCELLED = 'CANCELLED',
    FAILED = 'FAILED',
    QUEUED = 'QUEUED',
  }

  export enum JobType {
    S3_VIDEO_IMPORT = 'S3_VIDEO_IMPORT',
    S3_IMAGES_IMPORT = 'S3_IMAGES_IMPORT',
    TABULAR_UPLOAD = 'TABULAR_UPLOAD',
    ALBUM_AUGMENTATION = 'ALBUM_AUGMENTATION',
    CV_MODEL_TRAIN = 'CV_MODEL_TRAIN',
    CV_MODEL_EVALUATE = 'CV_MODEL_EVALUATE',
    CV_MODEL_PREDICT = 'CV_MODEL_PREDICT',
    TABULAR_EVALUATE = 'TABULAR_EVALUATE',
    TABULAR_TRAIN = 'TABULAR_TRAIN',
    TABULAR_PREDICT = 'TABULAR_PREDICT',
    CV_MODEL_IMPORT = 'CV_MODEL_IMPORT',
    TABULAR_COLUMN_STATISTICS = 'TABULAR_COLUMN_STATISTICS',
    MERGE_ALBUM = 'MERGE_ALBUM',
    PROJECT_BUILD = 'PROJECT_BUILD',
    GENERIC_EXPERIMENT = 'GENERIC_EXPERIMENT',
    DATASET_EXPORT = 'DATASET_EXPORT',
    DATASET_IMPORT = 'DATASET_IMPORT',
    API_DEPLOYMENT = 'API_DEPLOYMENT',
    SCRIPT_DEPLOYMENT = 'SCRIPT_DEPLOYMENT',
  }
}
