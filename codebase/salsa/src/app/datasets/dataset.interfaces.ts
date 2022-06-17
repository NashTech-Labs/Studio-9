import { S3BucketReference } from '../core/core.interface';
import {
  IAsset,
  IListRequest,
  TObjectId,
} from '../core/interfaces/common.interface';

export interface BinaryDataset extends IAsset {
  id: TObjectId;
  ownerId: string;
  name: string;
  created: string;
  updated: string;
  description?: string;
}

export namespace BinaryDataset {
  export enum Status {
    IDLE = 'IDLE',
    EXPORTING = 'EXPORTING',
    IMPORTING = 'IMPORTING',
    ERROR = 'ERROR',
  }

  export interface File {
    filename: string;
    filepath: string;
    filesize: number;
    modified: string;
  }

  export interface CreateRequest {
    name: string;
    description?: string;
  }

  export interface UpdateRequest {
    name?: string;
    description?: string;
  }

  export interface S3IOReference {
    s3Bucket: S3BucketReference;
    path: string;
  }

  export interface ImportFromS3Request {
    from: S3IOReference;
  }

  export interface ExportToS3Request {
    to: S3IOReference;
  }

  export interface FileSearchParams extends IListRequest {
  }
}

