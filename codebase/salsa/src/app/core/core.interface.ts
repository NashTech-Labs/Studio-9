import { TObjectId } from './interfaces/common.interface';

export interface TokenResponse {
  access_token: string;
  expires_in: number;
  token_type: 'bearer';
}

export interface IS3BucketInfo {
  id: TObjectId;
  name: string;
}

// TODO: redefine once COR-339 is complete
export interface IS3BucketDetails extends IS3BucketInfo {
  bucketName: string;
  accessKey: string;
  secretKey: string;
  sessionToken: string;
}

export interface HasUnsavedData {
  hasUnsavedData(): boolean;
}

export interface GridOptions {
  columns: {
    name: string;
    alias?: string;
    style?: string;
  }[];
}

export type S3BucketReference = S3BucketReference.Predefined | S3BucketReference.Custom;

export namespace S3BucketReference {
  export interface Predefined {
    AWSS3BucketId: string;
  }

  export interface Custom {
    AWSRegion: string;
    AWSS3BucketName: string;
    AWSAccessKey: string;
    AWSSecretKey: string;
    AWSSessionToken?: string;
  }
}
