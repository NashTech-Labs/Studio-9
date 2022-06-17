import { TObjectId } from './common.interface';

export interface IProject {
  id: TObjectId;
  name: string;
  folders: IProjectFolder[];
  ownerId: string;
  created: string;
  updated: string;
}

export interface IProjectCreate {
  name: string;
}

export interface IProjectUpdate {
  name: string;
}

export interface IProjectFolder {
  id: TObjectId;
  path: string;
}
