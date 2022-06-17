import { IAsset, IAssetReference } from '../core/interfaces/common.interface';
import { ITableColumn, TTableValue } from '../tables/table.interface';

import { IDashboard } from './dashboard.interface';

export type TabularAssetType = IAsset.Type.TABLE | IAsset.Type.MODEL;

export interface TabularDataRequest {
  asset: IAssetReference<TabularAssetType>;
  aggregations: TabularDataRequest.Aggregation[];
  groupBy: string[]; // column names
  filters: TabularDataRequest.Filter[];
  generators?: TabularDataRequest.Generator[]; // only for models
  groups?: TabularDataRequest.MergeGroup[];
  limit?: number;
}

export interface GeospatialDataRequest<T extends GeospatialDataRequest.Mode = GeospatialDataRequest.Mode> {
  mode: T;
  query: GeospatialDataRequest.Query<T>;
}

export namespace GeospatialDataRequest {
  export enum Mode {
    CV_PREDICTION = 'CV_PREDICTION',
  }

  export type Query<T extends Mode> =
    T extends Mode.CV_PREDICTION ? CVPredictionQuery
    : any;

  export interface CVPredictionQuery {
    where: string; // This IS an SQL query 'where' part for now;
  }
}

export interface GeospatialDataResponse<T extends GeospatialDataRequest.Mode = GeospatialDataRequest.Mode> {
  data: GeospatialDataResponse.Datum<T>[];
  count: number;
}

export namespace GeospatialDataResponse {
  export type Datum<T extends GeospatialDataRequest.Mode> =
    // filename, target, min_lat, min_long, max_lat, max_long, confidence?
    T extends GeospatialDataRequest.Mode.CV_PREDICTION ? [string, string, number, number, number, number, number]
    : never;
}

export namespace TabularDataRequest {
  export interface MergeGroup {
    columnName: string;
    values: TTableValue[];
    mergedValue: TTableValue;
  }

  export interface Aggregation {
    columnName: string;
    aggregator: IDashboard.DashboardAggregationType;
  }

  export interface ContinuousFilter {
    columnName: string;
    type: 'continuous';
    min: number;
    max: number;
  }

  export interface CategoricalFilter {
    columnName: string;
    type: 'categorical';
    values: TTableValue[];
  }

  export type Filter = ContinuousFilter | CategoricalFilter;

  export interface ContinuousGenerator {
    columnName: string;
    type: 'continuous';
    min: number;
    max: number;
    steps: number;
  }

  export interface CategoricalGenerator {
    columnName: string;
    type: 'categorical';
    values: TTableValue[];
  }

  export type Generator = ContinuousGenerator | CategoricalGenerator;
}

export interface TabularDataResponse {
  columns: ITableColumn[];
  data: TTableValue[][];
  count: number;
}
