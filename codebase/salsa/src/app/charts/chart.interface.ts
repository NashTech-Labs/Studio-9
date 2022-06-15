import { ChartData } from './chart-data.interface';

export interface Chart<O> {
  data: ChartData;
  options: O;
}
