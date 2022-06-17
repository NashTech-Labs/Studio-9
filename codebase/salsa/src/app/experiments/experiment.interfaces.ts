import { InjectionToken, Type } from '@angular/core';

import { IAsset } from '../core/interfaces/common.interface';
import { Feature } from '../core/interfaces/feature-toggle.interface';

import { IExperimentPipelineForm } from './experiment-pipeline.component';
import { IExperimentResultView } from './experiment-result.component';


export interface IAbstractExperimentPipeline {}

export interface IAbstractExperimentResult {}

export interface IExperiment extends IAsset {
  status: IExperiment.Status;
  type: ExperimentType;
}

export interface IExperimentFull<
  P extends IAbstractExperimentPipeline = IAbstractExperimentPipeline,
  R extends IAbstractExperimentResult = IAbstractExperimentResult,
> extends IExperiment {
  pipeline: P;
  result: R;
}

export interface IExperimentCreate {
  name: string;
  type: ExperimentType;
  description?: string;
  pipeline: IAbstractExperimentPipeline;
}

export interface IExperimentUpdate {
  name?: string;
  description?: string;
}

export namespace IExperiment {
  export enum Status {
    RUNNING = 'RUNNING',
    COMPLETED = 'COMPLETED',
    ERROR = 'ERROR',
    CANCELLED = 'CANCELLED',
  }
}

export interface ExperimentTypeDefinition {
  type: ExperimentType;
  name: string;
  pipelineComponent: Type<IExperimentPipelineForm>;
  resultComponent: Type<IExperimentResultView>;
  resultComponentHandlesErrors?: boolean;
  features: Feature[];
}

export const EXPERIMENT_TYPES: InjectionToken<ExperimentTypeDefinition[]> =
  new InjectionToken('ExperimentTypeDefinition');

export const enum ExperimentType {
  // to be extended by feature providers
}
