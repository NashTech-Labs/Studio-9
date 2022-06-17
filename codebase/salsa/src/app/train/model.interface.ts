import { IAsset, TObjectId } from '../core/interfaces/common.interface';
import { IAbstractExperimentPipeline, IAbstractExperimentResult } from '../experiments/experiment.interfaces';
import { ITable } from '../tables/table.interface';

export interface ITabularModel extends IAsset {
  status: ITabularModel.Status;
  responseColumn: IModelColumn;
  predictorColumns: IModelColumn[];
  class: ITabularModel.Class;
  experimentId?: TObjectId;
  classes?: String[];
  inLibrary?: boolean;
}

export interface IVariableImportance {
  name: string;
  min: number;
  lowerQuartile: number;
  median: number;
  upperQuartile: number;
  max: number;
  decision: IVariableImportance.Decision;
}

export namespace IVariableImportance {
  export enum Decision {
    TENTATIVE = 'TENTATIVE',
    CONFIRMED = 'CONFIRMED',
    REJECTED = 'REJECTED',
    SHADOW = 'SHADOW',
  }
}

export interface IModelEvaluationSummary {
  confusionMatrix?: IModelEvaluationSummary.ConfusionMatrixRow[];
  KS?: number;
  r2?: number;
  rmse?: number;
  MAPE?: number;
}

export namespace IModelEvaluationSummary {
  export interface ConfusionMatrixRow {
    className: string;
    truePositive: number;
    trueNegative: number;
    falsePositive: number;
    falseNegative: number;
  }

  export type ROCRow = [number, number];
}

export interface IModelHelpers {
  responseColumn?: IModelColumn;
  predictedColumn?: string;
  probabilityColumns?: string[];
  predictorColumns: IModelColumn[];
}

export interface IModelSummary extends IModelEvaluationSummary {
  roc?: IModelEvaluationSummary.ROCRow[];
  areaUnderROC?: number;
  predictors: IModelSummary.Predictor[];
  //intercept?: IModelValueSummary;
  formula?: string;
  variableImportance?: IVariableImportance[]; // mocks only now
}

export namespace IModelSummary {
  export type Predictor = ParametricModelPredictor | TreeModelPredictor;

  export interface ParametricModelPredictor {
    name: string;
    estimate: number;
    stdError: number;
    tvalue: number;
    pvalue: number;
  }

  export interface TreeModelPredictor {
    name: string;
    importance: number;
  }
}

export interface IModelColumn {
  name: string;
  displayName: string;
  dataType: ITable.ColumnDataType;
  variableType: ITable.ColumnVariableType;
}

export interface IModelUpdate {
  name?: string;
  description?: string;
}

export interface IModelCreate {
  name: string;
  input: TObjectId;
  outputTableName: string;
  holdOutInput?: TObjectId;
  holdOutOutputName?: string;
  outOfTimeInput?: TObjectId;
  outOfTimeOutputName?: string;
  responseColumn: IModelColumn;
  predictorColumns: IModelColumn[];

  description?: string;
  trainOptions?: ITabularModel.TrainOptions;

  validationThreshold?: number;
  samplingWeightColumn?: string;
}

export interface IModelClone {
  name: string;
}

export namespace ITabularModel {
  export enum Class {
    REGRESSION = 'REGRESSION',
    BINARY_CLASSIFICATION = 'BINARY_CLASSIFICATION',
    CLASSIFICATION = 'CLASSIFICATION',
  }

  export enum Status {
    ACTIVE = 'ACTIVE',
    TRAINING = 'TRAINING',
    PREDICTING = 'PREDICTING',
    ERROR = 'ERROR',
    CANCELLED = 'CANCELLED',
  }

  export enum PipelineStage {
    OUTLIERS_TREATMENT = 'OUTLIERS_TREATMENT',
    DESKEWING = 'DESKEWING',
    IMPUTATION = 'IMPUTATION',
    NORMALIZATION = 'NORMALIZATION',
    MULTICOLINEARITY_TREATMENT = 'MULTICOLINEARITY_TREATMENT',
    MODEL_PRIMITIVES = 'MODEL_PRIMITIVES',
  }

  export enum StageTechnique {
    OT_STDDEV = 'OT_STDDEV',
    OT_KMEANS = 'OT_KMEANS',

    DS_BOXCOX = 'DS_BOXCOX',
    DS_MANLY = 'DS_MANLY',
    DS_BOXCOX_MOD = 'DS_BOXCOX_MOD',

    IM_KNN = 'IM_KNN',
    IM_LINEAR = 'IM_LINEAR',
    IM_EXPONENT = 'IM_EXPONENT',
    IM_POLYNOM = 'IM_POLYNOM',

    NM_ZSCORE = 'NM_ZSCORE',
    NM_MINMAX = 'NM_MINMAX',
    NM_INDEX = 'NM_INDEX',

    MC_PCA = 'MC_PCA',

    MP_GLM = 'MP_GLM',
    MP_GLM_ELASTIC_NET = 'MP_GLM_ELASTIC_NET',
    MP_RANDOM_FOREST = 'MP_RANDOM_FOREST',
    MP_SVM = 'MP_SVM',
    MP_XGBOOST = 'MP_XGBOOST',
    MP_DECISION_TREE = 'MP_DECISION_TREE',
    MP_DNN = 'MP_DNN',
  }

  export interface StageTechniqueParameter {
    name: string;
    value?: number;
    stringValue?: string;
  }

  export interface StageTechniqueParameterConstraint {
    name: string;
    min?: number;
    max?: number;
    values?: number[] | string[];
  }

  export interface TrainOptions {
    variableImportance: boolean;
    modelExplanation: boolean;
    stages: PipelineStage[];
    techniques: {
      [T in PipelineStage]: StageTechnique[]
    };
    parameters: {
      [T in StageTechnique]: StageTechniqueParameterConstraint[]
    };
  }

  export interface PipelineSummary {
    stages: PipelineSummaryStage[];
  }

  export interface PipelineSummaryStage {
    stage: PipelineStage;
    technique: StageTechnique;
    parameters: StageTechniqueParameter[];
  }
}

export interface IModelTrainSummary {
  state: IModelTrainSummary.TrainState;
  iterations: IModelTrainSummary.TrainIteration[];
  resources: IModelTrainSummary.ResourcesSummary;
}

export namespace IModelTrainSummary {
  export enum TrainState {
    TRAINING = 'TRAINING',
    REFINING = 'REFINING',
    COMPLETE = 'COMPLETE',
  }

  export interface TrainIteration {
    index: number;
    summary: IModelSummary;
    hyperParameters: ITabularModel.PipelineSummaryStage[]; // TODO: rename to stages
  }

  export interface ResourcesSummaryValue {
    name: string;
    value: number;
  }

  export interface ResourceLog {
    value: number;
    iteration: number;
  }

  export interface ResourcesSummary {
    nodes: number; // integer
    cpus: number; // integer
    cpuCores: number; // integer
    gpus: number; // integer
    cpuLoad: ResourcesSummaryValue[];
    cpuLoadLog: ResourceLog[]; // cumulative percentage log for last N=100 iterations
    gpuLoad: ResourcesSummaryValue[];
    gpuLoadLog: ResourceLog[];
    memoryUsage: ResourcesSummaryValue[];
    memoryUsageLog: ResourceLog[];
  }
}

// TabularTrain interfaces
export interface ITabularTrainPipeline extends IAbstractExperimentPipeline {
  input: TObjectId;
  holdOutInput?: TObjectId;
  outOfTimeInput?: TObjectId;
  responseColumn: IModelColumn;
  predictorColumns: IModelColumn[];

  trainOptions?: ITabularModel.TrainOptions; // not used in baile - mocks only
  validationThreshold?: number; // same
  samplingWeightColumn?: string;
}

export interface ITabularTrainResult extends IAbstractExperimentResult {
  modelId: TObjectId;
  output: TObjectId;
  holdOutOutput?: TObjectId;
  outOfTimeOutput?: TObjectId;
  predictedColumn: string;
  probabilityColumns: string[];

  summary?: IModelSummary;
  holdOutSummary?: IModelEvaluationSummary;
  outOfTimeSummary?: IModelEvaluationSummary;
  pipelineSummary?: ITabularModel.PipelineSummary;
}

// TabularModel + Experiment data
export interface ITabularTrainModel extends ITabularModel {
  pipeline: ITabularTrainPipeline;
  result: ITabularTrainResult;
}
