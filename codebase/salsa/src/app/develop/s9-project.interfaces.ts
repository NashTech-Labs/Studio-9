import {
  IAsset,
  TObjectId,
} from '../core/interfaces/common.interface';

export interface IS9Project extends IAsset {
  packageName?: string;
  packageVersion?: string;
}

export interface IS9ProjectUpdate {
  name: string;
  description?: string;
}

export interface IS9ProjectCreate {
  name: string;
  description?: string;
}

export interface IS9ProjectFile {
  type: IS9ProjectFile.Type;
  name: string;
  modified: string; //datetime
}

export namespace IS9ProjectFile {
  export enum Type {
    FILE = 'FILE',
    DIR = 'DIR',
  }

  export interface Content {
    content: string;
    contentType: string;
    lastModified: string; //datetime
  }
}

export namespace IS9Project {
  export enum Status {
    IDLE = 'IDLE',
    BUILDING = 'BUILDING',
    INTERACTIVE = 'INTERACTIVE',
  }
}

export interface IS9ProjectSession {
  id: string;
  authToken: string;
  s9ProjectId: TObjectId;
  created: string;
  url: string;
}

export interface IS9ProjectSessionCreate {
  useGPU: boolean;
}

export namespace IS9ProjectSession {
  export enum Status {
    RUNNING = 'RUNNING',
    COMPLETED = 'COMPLETED',
    QUEUED = 'QUEUED',
    SUBMITTED = 'SUBMITTED',
    FAILED = 'FAILED',
  }
}

export interface IOpenedS9ProjectFile {
  file: IS9ProjectFile;
  content: string;
  editorMode: string;
  isEditing: boolean;
  hasChanges: boolean;
}
