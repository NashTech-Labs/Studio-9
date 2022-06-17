import { IAsset, TObjectId } from '../core/interfaces/common.interface';
import { IModelColumn } from '../train/model.interface';

export interface IDIAACreate {
  modelId: TObjectId;
  name: string;
  description?: string;
  outputModelName: string;
  protectedGroupsColumnMapping: IDIAA.ProtectedGroupsColumnMapping;
  airSpecification?: IDIAA.AIRSpecification;
  higherModelScoreFavorable: boolean;
  diaaObjective: IDIAA.DIAAObjective;
}

export interface IDIAA extends IAsset, IDIAACreate {
  outputModelId: TObjectId;
  status: IDIAA.Status;

  summary: IDIAA.Summary;
  objective?: IDIAA.Objective;
  diaConstraints?: IDIAA.Constraint[];
  altSummary?: IDIAA.Summary;
  responseColumn?: IModelColumn;
  predictorColumns?: IModelColumn[];
}

export interface IDIAARun {
  objective: IDIAA.Objective;
  diaConstraints: IDIAA.Constraint[];
  responseColumn: IModelColumn;
  predictorColumns: IModelColumn[];
}

export interface IDIAAUpdate {
  name?: string;
  description?: string;
}

export namespace IDIAA {
  'use strict';

  export enum DIAAObjective {
    AIR = 'AIR',
    SMD = 'SMD',
  }

  export enum Status {
    CHECKING = 'CHECKING',
    CHECKED = 'CHECKED',
    RUNNING = 'RUNNING',
    DONE = 'DONE',
    ERROR = 'ERROR',
  }

  export enum ObjectiveMetric {
    RACE_BLACK = 'RACE_BLACK',
    RACE_HISPANIC = 'RACE_HISPANIC',
    RACE_ASIAN = 'RACE_ASIAN',
    GENDER_FEMALE = 'GENDER_FEMALE',
    AGE_OLDER = 'AGE_OLDER',
  }

  export enum ConstraintMetric {
    RACE_BLACK = 'RACE_BLACK',
    RACE_HISPANIC = 'RACE_HISPANIC',
    RACE_ASIAN = 'RACE_ASIAN',
    GENDER_FEMALE = 'GENDER_FEMALE',
    AGE_OLDER = 'AGE_OLDER',
    AUROC = 'AUROC',
    KS = 'KS',
  }

  export enum ConstraintOperator {
    EQ = 'EQ',
    NE = 'NE',
    LT = 'LT',
    GT = 'GT',
    T20 = 'T20',
    T10 = 'T10',
    T05 = 'T05',
    T01 = 'T01',
  }

  export enum DatasetRef {
    INPUT = 'INPUT',
    HOLD_OUT = 'HOLD_OUT',
    OUT_OF_TIME = 'OUT_OF_TIME',
  }

  export interface AIRSpecification {
    cutOffMode: 'decile' | 'percentile' | 'probability';
    decile?: [number, number];
    percentile?: number;
    probability?: number;
  }

  export interface ProtectedGroupsColumnMapping {
    race_white?: string;
    race_black?: string;
    race_hispanic?: string;
    race_asian?: string;
    gender_male?: string;
    gender_female?: string;
    age_younger?: string;
    age_older?: string;
  }

  export type Summary = {
    [T in DatasetRef]?: SummaryRow[];
  };

  export interface SummaryRow {
    airDecile?: number;

    race_black?: number;
    race_hispanic?: number;
    race_asian?: number;
    gender_female?: number;
    age_older?: number;
  }

  export interface Objective {
    metrics: IDIAA.ObjectiveMetric[];
    airDecile?: number;
  }

  export interface Constraint {
    metric: ConstraintMetric;
    operator: ConstraintOperator;
    value: number;
  }
}

