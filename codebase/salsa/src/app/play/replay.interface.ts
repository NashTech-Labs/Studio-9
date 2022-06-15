import { IAsset, TObjectId } from '../core/interfaces/common.interface';

import { IMappingPair } from './play.interface';

export interface IReplay extends IAsset {
  flowId: TObjectId;
  originalFlowId: TObjectId;
  status: string;
}

export interface IReplayOutputName {
  tableId: TObjectId;
  newTableName: string;
}

export interface IReplayCreate {
  flowId: TObjectId;
  name: string;
  description?: string;
  outputTableNames: IReplayOutputName[];
  columnMappings: IMappingPair[];
}


