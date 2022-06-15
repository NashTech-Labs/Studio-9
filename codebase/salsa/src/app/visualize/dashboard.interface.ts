import { IAsset, IAssetReference, TObjectId } from '../core/interfaces/common.interface';

import { TabularAssetType, TabularDataRequest } from './visualize.interface';

export interface ILayout {
  children?: ILayout[] | ILayoutWidget[];
  orientation?: IDashboard.LayoutOrientation;
  sizes?: number[];
  type: IDashboard.LayoutWidgetType;
}

export interface ILayoutWidget {
  type: IDashboard.LayoutWidgetType;
  widget: number;
}

export interface IDashboardCreate {
  name: string;
  layout: ILayout | ILayoutWidget;
  widgets: IDashboardWidget[];
  crossFilters: IDashboardXFilter[];
  description?: string;
  inputs?: IAssetReference[];
}

export interface IDashboardUpdate {
  name?: string;
  description?: string;
  layout?: ILayout | ILayoutWidget;
  widgets?: IDashboardWidget[];
  crossFilters?: IDashboardXFilter[];
  inputs?: IAssetReference[];
}

export interface IDashboard extends IAsset, IDashboardCreate {
  status: IDashboard.Status;
}

export interface IDashboardXFilter {
  tableId: TObjectId;
  columnName: string;
}

export interface IDashboardWidget {
  readonly input: IAssetReference<TabularAssetType>;
  readonly type: IDashboard.DashboardChartType;
  guid: string;
  name: string;
  readonly filters?: TabularDataRequest.Filter[];
  readonly chartFilters: string[];
  readonly generators?: TabularDataRequest.Generator[];
  readonly chartGenerators: string[];
  readonly metrics: TabularDataRequest.Aggregation[];
  readonly attributes: string[];
  options?: { [p: string]: any };
  groups?: TabularDataRequest.MergeGroup[];
}

export namespace IDashboard {
  'use strict';

  export enum Status {
    IDLE = 'IDLE',
  }

  export enum DashboardChartType {
    BAR = 'BAR',
    LINE = 'LINE',
    PIE = 'PIE',
    SCATTER = 'SCATTER',
    GEO = 'GEO',
    TABLE = 'TABLE',
    ONEDSCATTER = 'ONEDSCATTER',
  }

  export enum DashboardAggregationType {
    SUM = 'SUM',
    MIN = 'MIN',
    MAX = 'MAX',
    AVG = 'AVG',
    COUNT = 'COUNT',
    NO_AGGREGATE = 'NO_AGGREGATE',
  }

  export type LayoutWidgetType  = 'tab-area' | 'split-area';

  export type LayoutOrientation  = 'horizontal' | 'vertical';
}


