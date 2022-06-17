import { IAsset, TObjectId } from '../core/interfaces/common.interface';

export type TTableValue = string | number;

export interface IDatasetParams {
  page?: number;
  page_size?: number;
}

export interface ITable extends IAsset {
  datasetId: TObjectId;
  status: ITable.Status;
  columns: ITableColumn[];
  datasetType: ITable.DatasetType;
  inLibrary?: boolean;
  size?: number;
  options?: { indices: string[] };
}

export interface ITableColumn {
  // OBSOLETED / id: TTableColumnId;
  name: string;
  displayName: string;
  dataType: ITable.ColumnDataType;
  variableType: ITable.ColumnVariableType;
  columnType?: string;
  align?: ITable.ColumnAlign;
}

export interface ITableColumnExt extends ITableColumn {
  covariateType: string;
}

export interface ITableId {
  id: TObjectId;
}

export interface ITableClone {
  name?: string;
}

export interface ITableUpdate {
  name?: string;
  description?: string;
  columns?: ITableColumn[];
}

export interface IColumnValueParams {
  column_name: string;
  search: string;
  limit?: number;
}

export interface ITableStats {
  id: string;
  status: string;
  stats: ITableColumnStats[];
}

export interface ITableColumnStats {
  columnName: string;
  min?: number;
  max?: number;
  avg?: number;
  std?: number;
  stdPopulation?: number;
  median?: number;
  uniqueCount?: number;
  mostFrequentValue?: TTableValue;
  histogram?: ITableColumnHistogram;
}

export interface ITableColumnHistogramRow {
  count: number;
  min?: number;
  max?: number;
  value?: TTableValue;
}

export type ITableColumnHistogram = ITableColumnHistogramRow[];

export interface IDimensions {
  tableId: TObjectId;
  metrics: ITableColumn[];
  attributes: ITableColumn[];
}

export interface IScatterSummary {
  quantizationSteps: number;
  columns: ITableColumn[];
  data: IScatterSummaryChart[];
}

export interface IScatterSummaryChart {
  column1: string;
  column2: string;
  values: IScatterSummaryChartRow[];
}

export interface IScatterSummaryChartRow {
  value1: number;
  value2: number;
  count: number;
}

export namespace ITable {
  export enum Status {
    ACTIVE = 'ACTIVE',
    INACTIVE = 'INACTIVE',
    SAVING = 'SAVING',
    ERROR = 'ERROR',
  }

  export enum DatasetType {
    SOURCE = 'SOURCE',
    DERIVED = 'DERIVED',
  }

  export enum ColumnDataType {
    STRING = 'STRING',
    INTEGER = 'INTEGER',
    DOUBLE = 'DOUBLE',
    BOOLEAN = 'BOOLEAN',
    TIMESTAMP = 'TIMESTAMP',
    LONG = 'LONG',
  }

  export enum ColumnVariableType {
    CONTINUOUS = 'CONTINUOUS',
    CATEGORICAL = 'CATEGORICAL',
  }

  export enum ColumnAlign {
    LEFT = 'LEFT',
    RIGHT = 'RIGHT',
    CENTER = 'CENTER',
  }
}
