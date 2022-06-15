import { D3ChartHelper } from '../../charts/d3-chart.helper';

export namespace DashboardCharts {
  'use strict';
  import CategoricalGamma = D3ChartHelper.CategoricalGamma;

  export enum ChartOrientation {
    VERTICAL = 'VERTICAL',
    HORIZONTAL = 'HORIZONTAL',
  }

  export enum Gamma {
    RAINBOW = 'RAINBOW',
    MAGMA = 'MAGMA',
    BLUES = 'BLUES',
    GREENS = 'GREENS',
    REDS = 'REDS',
    GREYS = 'GREYS',
    RED_BLUE = 'RED_BLUE',
  }

  export enum GeoHeatMapType {
    STATE = 'STATE',
    COUNTY = 'COUNTY',
    ZIP = 'ZIP',
  }

  export enum DrawMethod {
    AREA = 'AREA',
    BUBBLE = 'BUBBLE',
  }

  export enum BarChartType {
    STACKED = 'STACKED',
    GROUPED = 'GROUPED',
  }

  export interface IChartOptions {
    title: string;
    subtitle: string;
  }

  export interface IAxisChartOptions extends IChartOptions {
    orientation?: ChartOrientation;
    xAxisTitle?: string;
    yAxisTitle?: string;
    xAxisAngle: number;
    yAxisAngle: number;
    hierarchy?: boolean;
    xGrid: boolean;
    yGrid: boolean;
  }

  export interface IBarChartOptions extends IAxisChartOptions {
    type: BarChartType;
    hierarchy: boolean;
  }

  export interface ILineChartOptions extends IAxisChartOptions {
    hierarchy: boolean;
  }

  export interface IPieChartOptions extends IChartOptions {
  }

  export interface ITableChartOptions extends IChartOptions {
  }

  export interface IGeoHeatMapOptions extends IChartOptions {
    geoType: DashboardCharts.GeoHeatMapType;
    gamma: Gamma;
    stateColumn: string;
    countyColumn: string;
    drawMethod: DashboardCharts.DrawMethod;
    bubbleSize: number;
  }

  export interface IScatterPlotChartOptions extends IAxisChartOptions {
    gamma: Gamma;
  }

  export interface IOneDimensionalScatterPlotChartOptions extends IChartOptions {
    xAxisTitle?: string;
    categoricalGamma: CategoricalGamma;
    yJitter?: boolean;
    bubbleSize: number;
  }


  // Defaults
  export const defaultChartOptions: IChartOptions = {
    title: '',
    subtitle: '',
  };

  export const defaultAxisChartOptions: IAxisChartOptions = {
    title: '',
    subtitle: '',
    xAxisTitle: '',
    yAxisTitle: '',
    xAxisAngle: 0,
    yAxisAngle: 0,
    orientation: DashboardCharts.ChartOrientation.VERTICAL,
    xGrid: false,
    yGrid: false,
  };

  export const defaultScatterPlotChartOptions: IScatterPlotChartOptions = {
    title: '',
    subtitle: '',
    xAxisTitle: '',
    yAxisTitle: '',
    xAxisAngle: 0,
    yAxisAngle: 0,
    hierarchy: false,
    xGrid: false,
    yGrid: false,
    gamma: Gamma.RAINBOW,
  };

  export const defaultLineChartOptions: ILineChartOptions = {
    title: '',
    subtitle: '',
    xAxisTitle: '',
    yAxisTitle: '',
    xAxisAngle: 0,
    yAxisAngle: 0,
    orientation: DashboardCharts.ChartOrientation.VERTICAL,
    hierarchy: false,
    xGrid: false,
    yGrid: false,
  };

  export const defaultBarChartOptions: IBarChartOptions = {
    title: '',
    subtitle: '',
    xAxisTitle: '',
    yAxisTitle: '',
    xAxisAngle: 0,
    yAxisAngle: 0,
    type: DashboardCharts.BarChartType.GROUPED,
    orientation: DashboardCharts.ChartOrientation.VERTICAL,
    hierarchy: false,
    xGrid: false,
    yGrid: false,
  };

  export const defaultGeoHeatMapOptions: IGeoHeatMapOptions = {
    title: '',
    subtitle: '',
    geoType: DashboardCharts.GeoHeatMapType.STATE,
    gamma: Gamma.BLUES,
    stateColumn: null,
    countyColumn: null,
    drawMethod: DashboardCharts.DrawMethod.AREA,
    bubbleSize: 30,
  };

  export const defaultOneDimensionalScatterPlotChartOptions: IOneDimensionalScatterPlotChartOptions = {
    title: '',
    subtitle: '',
    xAxisTitle: null,
    yJitter: false,
    bubbleSize: 5,
    categoricalGamma: CategoricalGamma.category10,
  };
}
