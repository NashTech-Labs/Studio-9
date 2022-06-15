import { IAsset, TObjectId } from '../core/interfaces/common.interface';
import { ICVModelSummary } from '../train/cv-model.interface';
import { LabelOfInterest } from '../train/cvtl-train.interfaces';
import { ICVEvaluationTimeSpentSummary } from '../train/time-spent.interfaces';

import { ICVPredictionTimeSpentSummary } from './time-spent.interface';

export interface ICVPredictionCreate {
  modelId: TObjectId;
  name: string;
  description?: string;
  input: TObjectId;
  outputAlbumName: string;
  options?: ICVPredictionCreate.Options;
  evaluate?: boolean;
}

export interface ICVPredictionUpdate {
  name?: string;
  description?: string;
}

export interface ICVPrediction extends IAsset {
  modelId: TObjectId;
  status: ICVPredictionStatus;
  input: TObjectId;
  output: TObjectId;
  summary?: ICVModelSummary;
  probabilityPredictionTableId?: TObjectId;
  predictionTimeSpentSummary?: ICVPredictionTimeSpentSummary;
  evaluationTimeSpentSummary?: ICVEvaluationTimeSpentSummary;
}

export namespace ICVPredictionCreate {
  export interface Options {
    loi?: LabelOfInterest[];
    defaultVisualThreshold: number;
  }
}

export enum ICVPredictionStatus {
  NEW = 'NEW',
  RUNNING = 'RUNNING',
  ERROR = 'ERROR',
  DONE = 'DONE',
  INCOMPLETE = 'INCOMPLETE',
}
