import { IAbstractExperimentPipeline, IAbstractExperimentResult } from '../experiments/experiment.interfaces';

export interface ITestExperimentPipeline extends IAbstractExperimentPipeline {
  testField1: string;
  testField2?: string;
}

export interface ITestExperimentResult extends IAbstractExperimentResult {
  testField1: string;
  testField2?: string;
}

export namespace ITestExperiment {
  export const Type = 'TestExperiment';
}
