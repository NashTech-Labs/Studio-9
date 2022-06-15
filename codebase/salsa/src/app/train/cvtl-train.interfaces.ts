import { TObjectId } from '../core/interfaces/common.interface';
import { ParameterValues } from '../core/interfaces/params.interface';
import { IAbstractExperimentPipeline, IAbstractExperimentResult } from '../experiments/experiment.interfaces';

import { CVModelType, IAugmentationSummary, ICVAugmentationOptions, ICVModelSummary } from './cv-model.interface';
import { ICVEvaluationTimeSpentSummary, ICVModelTrainTimeSpentSummary } from './time-spent.interfaces';

export interface ICVTLTrainPipeline extends IAbstractExperimentPipeline {
  step1: ICVTLTrainStep1Params;
  step2?: ICVTLTrainStep2Params;
}

export interface ICVTLTrainResult extends IAbstractExperimentResult {
  step1: ICVTLTrainStepResult;
  step2?: ICVTLTrainStepResult;
}

export interface LabelOfInterest {
  label: string;
  threshold?: number;
}

export interface CommonTrainParams {
  inputSize?: CommonTrainParams.InputSize;
  loi?: LabelOfInterest[];
  defaultVisualThreshold?: number;
  iouThreshold?: number;
  featureExtractorLearningRate?: number;
  modelLearningRate?: number;
}

export namespace CommonTrainParams {
  export interface InputSize {
    width: number;
    height: number;
  }
}

export interface ICVTLTrainStepParams {
  tuneFeatureExtractor?: boolean;
  modelType: CVModelType.TLConsumer;
  input: string;
  testInput?: string;
  augmentationOptions?: ICVAugmentationOptions;
  params?: ParameterValues;
  trainParams?: CommonTrainParams;
}

export interface ICVTLTrainStepResult {
  cvModelId: TObjectId;
  output: string;
  testOutput?: string;
  summary: ICVModelSummary;
  testSummary?: ICVModelSummary;
  augmentationSummary?: IAugmentationSummary[];
  augmentedSampleAlbum?: TObjectId;
  probabilityPredictionTableId?: TObjectId;
  testProbabilityPredictionTableId?: TObjectId;
  trainTimeSpentSummary?: ICVModelTrainTimeSpentSummary;
  evaluationTimeSpentSummary?: ICVEvaluationTimeSpentSummary;
}

export interface ICVTLTrainStep1Params extends ICVTLTrainStepParams {
  featureExtractorModelId?: TObjectId;
  architecture?: string;
  featureExtractorParams?: ParameterValues;
}

export interface ICVTLTrainStep2Params extends ICVTLTrainStepParams {
}

