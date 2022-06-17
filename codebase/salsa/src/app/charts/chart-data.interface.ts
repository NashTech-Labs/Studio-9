import { TTableValue } from '../tables/table.interface';

export interface ChartData {
  series: ChartData.Series[];
  metrics: ChartData.Metric[];
  attributes: ChartData.Attribute[];
}

export namespace ChartData {
  export interface Series {
    title?: string;
    data: DataPoint[];
  }

  export interface Metric {
    name: string;
  }

  export interface Attribute {
    name: string;
  }

  export interface DataPoint {
    values: number[]; // length equals to ChartData.metrics.length
    attributes?: TTableValue[]; // length equals to ChartData.attributes.length
    referenceId?: string; // reference ID is to be used for selections/highlights
  }
}
