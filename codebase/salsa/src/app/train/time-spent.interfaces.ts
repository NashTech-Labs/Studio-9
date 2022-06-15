import { IJobTimeSpentSummary } from '../core/interfaces/time-spent.interface';

export interface IPipelineSpent {
  time: number;
  description: string;
}

export interface ICVModelTrainTimeSpentSummary extends IJobTimeSpentSummary {
  dataLoadingTime: number;
  pipelineDetails: IPipelineSpent[];
  trainingTime: number;
  initialPredictionTime: number;
  modelSavingTime: number;
}

export interface ICVEvaluationTimeSpentSummary extends IJobTimeSpentSummary {
  dataLoadingTime: number;
  pipelineDetails: IPipelineSpent[];
  modelLoadingTime: number;
  scoreTime: number;
}
