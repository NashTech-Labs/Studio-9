import { IAsset, TObjectId } from '../core/interfaces/common.interface';

import { ISimpleMappingPair } from './play.interface';

export interface IPredictionCreate {
  modelId: string;
  name: string;
  description?: string;
  input: TObjectId;
  outputTableName: string;
  columnMappings: ISimpleMappingPair[];
}

export interface IPredictionUpdate {
  name?: string;
  description?: string;
}

export interface IPrediction extends IAsset {
  modelId: string;
  status: IPredictionStatus;
  input: TObjectId;
  output: TObjectId;
  columnMappings: ISimpleMappingPair[];
}

export enum IPredictionStatus {
  NEW = 'NEW',
  RUNNING = 'RUNNING',
  ERROR = 'ERROR',
  DONE = 'DONE',
  INCOMPLETE = 'INCOMPLETE',
}
