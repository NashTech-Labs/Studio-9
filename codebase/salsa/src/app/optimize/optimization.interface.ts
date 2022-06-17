import { IAsset, TObjectId } from '../core/interfaces/common.interface';
import { ISimpleMappingPair } from '../play/play.interface';
import { IModelEvaluationSummary, IModelSummary, ITabularModel } from '../train/model.interface';

export interface IOptimizationCreate {
  modelId: TObjectId;
  name: string;
  description?: string;
  optimizationType: IOptimization.OptimizationType;
  outputModelName: string;
  input: TObjectId;
  columnMappings: ISimpleMappingPair[];
  objectiveFlowId?: TObjectId;
  objectives: IOptimization.Objective[];
  constraintFlowId?: TObjectId;
  constraints: IOptimization.Constraint[];
}

export interface IOptimization extends IAsset, IOptimizationCreate {
  outputModelId: TObjectId;
  status: IOptimization.OptimizationStatus;

  outputTable?: TObjectId;
  summary?: IModelSummary;
  holdOutSummary?: IModelEvaluationSummary;
  outOfTimeSummary?: IModelEvaluationSummary;
  pipelineSummary?: ITabularModel.PipelineSummary;
}

export interface IOptimizationUpdate {
  name?: string;
  description?: string;
}

export interface IOptimizationId {
  id: TObjectId;
}

export namespace IOptimization {
  'use strict';

  export enum OptimizationStatus {
    RUNNING = 'RUNNING',
    DONE = 'DONE',
    ERROR = 'ERROR',
  }

  export enum OptimizationType {
    PREDICTOR_TUNING = 'PREDICTOR_TUNING',
    OBJECTIVE_FUNCTION = 'OBJECTIVE_FUNCTION',
  }

  export enum ObjectiveMetric {
    AUROC = 'AUROC',
    KS = 'KS',
    RMSE = 'RMSE',
  }

  export enum ObjectiveGoal {
    MIN = 'MIN',
    MAX = 'MAX',
  }

  export enum RelationalOperator {
    eq = 'eq',
    ne = 'ne',
    lt = 'lt',
    gt = 'gt',
  }

  export enum LogicalOperator {
    AND = 'AND',
    OR = 'OR',
  }

  export interface Objective {
    metric?: ObjectiveMetric;
    columnName?: string; // TODO: include table reference?
    goal: ObjectiveGoal;
  }
  export interface Constraint {
    columnName: string; // TODO: include table reference?
    value: string | number;
    relationalOperator: RelationalOperator;
    logicalOperator?: LogicalOperator;
  }

}
