import { IJobTimeSpentSummary } from '../core/interfaces/time-spent.interface';
import { IPipelineSpent } from '../train/time-spent.interfaces';

export interface ICVPredictionTimeSpentSummary extends IJobTimeSpentSummary {
  dataLoadingTime: number;
  pipelineDetails: IPipelineSpent[];
  modelLoadingTime: number;
  predictionTime: number;
}
