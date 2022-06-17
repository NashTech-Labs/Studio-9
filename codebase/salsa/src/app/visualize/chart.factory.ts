import { ComponentFactoryResolver, ComponentRef, Type, ViewContainerRef } from '@angular/core';

import { ChartAbstract } from './chart.abstract';
import { BarChartOptionsComponent } from './charts/bar-chart-options.component';
import { BarChartComponent } from './charts/bar-chart.component';
import { ChartOptionsAbstract } from './charts/chart-options';
import { DashboardCharts } from './charts/chart.interfaces';
import { GeoHeatMapOptionsComponent } from './charts/geo-heat-map-options.component';
import { GeoHeatMapComponent } from './charts/geo-heat-map.component';
import { LineChartOptionsComponent } from './charts/line-chart-options.component';
import { LineChartComponent } from './charts/line-chart.component';
import { OneDimensionalScatterPlotChartComponent } from './charts/one-dimensional-scatter-plot-chart.component';
import { OneDimensionalScatterPlotOptionsComponent } from './charts/one-dimensional-scatter-plot-options.component';
import { PieChartOptionsComponent } from './charts/pie-chart-options.component';
import { PieChartComponent } from './charts/pie-chart.component';
import { ScatterPlotChartOptionsComponent } from './charts/scatter-plot-chart-options.component';
import { ScatterPlotChartComponent } from './charts/scatter-plot-chart.component';
import { TableChartComponent } from './charts/table-chart.component';
import { DashboardEditState } from './dashboard-edit-state';
import { IDashboard, IDashboardWidget } from './dashboard.interface';

export namespace ChartFactory {
  function getComponentClass(chartType: string): Type<ChartAbstract<DashboardCharts.IChartOptions>> {
    switch (chartType) {
      case IDashboard.DashboardChartType.BAR:
        return BarChartComponent;
      case IDashboard.DashboardChartType.LINE:
        return LineChartComponent;
      case IDashboard.DashboardChartType.PIE:
        return PieChartComponent;
      case IDashboard.DashboardChartType.SCATTER:
        return ScatterPlotChartComponent;
      case IDashboard.DashboardChartType.GEO:
        return GeoHeatMapComponent;
      case IDashboard.DashboardChartType.TABLE:
        return TableChartComponent;
      case IDashboard.DashboardChartType.ONEDSCATTER:
        return OneDimensionalScatterPlotChartComponent;
      default:
        throw new Error('Unknown Chart Type');
    }
  }

  export function getOptionsClass(chartType: string): Type<ChartOptionsAbstract> | null {
    // @TODO Replace with common options interface
    switch (chartType) {
      case IDashboard.DashboardChartType.BAR:
        return BarChartOptionsComponent;
      case IDashboard.DashboardChartType.LINE:
        return LineChartOptionsComponent;
      case IDashboard.DashboardChartType.PIE:
        return PieChartOptionsComponent;
      case IDashboard.DashboardChartType.SCATTER:
        return ScatterPlotChartOptionsComponent;
      case IDashboard.DashboardChartType.GEO:
        return GeoHeatMapOptionsComponent;
      case IDashboard.DashboardChartType.ONEDSCATTER:
        return OneDimensionalScatterPlotOptionsComponent;
      default:
        return null;
    }
  }

  export function createOptionsComponentInstance(viewContainer: ViewContainerRef,
                                                 componentFactoryResolver: ComponentFactoryResolver,
                                                 state: DashboardEditState): ComponentRef<ChartOptionsAbstract> {
    const optionsClass = ChartFactory.getOptionsClass(state.widgetForm.value.type);
    if (!optionsClass) {
      return;
    }
    const factory = componentFactoryResolver.resolveComponentFactory(optionsClass);
    const compRef = viewContainer.createComponent(factory);
    (<ChartOptionsAbstract> compRef.instance).state = state;
    return compRef;
  }

  export function createComponentInstance(viewContainer: ViewContainerRef,
                                          componentFactoryResolver: ComponentFactoryResolver,
                                          config: IDashboardWidget): ComponentRef<ChartAbstract<any>> {
    const factory = componentFactoryResolver.resolveComponentFactory(getComponentClass(config.type));
    const compRef = viewContainer.createComponent(factory);
    (<ChartAbstract<DashboardCharts.IChartOptions>> compRef.instance).config = config;
    return compRef;
  }
}

export const CHART_ENTRY_COMPONENTS = [
  LineChartComponent,
  BarChartComponent,
  BarChartOptionsComponent,
  PieChartComponent,
  PieChartOptionsComponent,
  LineChartOptionsComponent,
  ScatterPlotChartComponent,
  ScatterPlotChartOptionsComponent,
  GeoHeatMapComponent,
  GeoHeatMapOptionsComponent,
  TableChartComponent,
  OneDimensionalScatterPlotChartComponent,
  OneDimensionalScatterPlotOptionsComponent,
];
