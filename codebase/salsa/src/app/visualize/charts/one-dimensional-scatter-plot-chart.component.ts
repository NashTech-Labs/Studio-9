import { Component, ElementRef, HostBinding } from '@angular/core';

import { ChartData } from '../../charts/chart-data.interface';
import { OneDimScatterChartOptions } from '../../charts/one-d-scatter-chart.component';
import { EventService } from '../../core/services/event.service';
import { ExperimentService } from '../../experiments/experiment.service';
import { TTableValue } from '../../tables/table.interface';
import { TableService } from '../../tables/table.service';
import { ModelService } from '../../train/model.service';
import { ChartAbstract } from '../chart.abstract';
import { CrossFilterBus } from '../cross-filter-bus';
import { VisualizeDataService } from '../visualize-data.service';
import { TabularDataResponse } from '../visualize.interface';

import { DashboardCharts } from './chart.interfaces';

@Component({
  selector: 'scatter-plot-chart',
  template: `
    <div class="flex-rubber">
      <div class="svg" style="width: 100%; height: 100%;">
        <chart-one-d-scatter *ngIf="chartData" [data]="chartData" [options]="chartOptions"></chart-one-d-scatter>
      </div>
    </div>
    <div #filters [hidden]="!showFilters" class="chart-filters flex-static"
      *ngIf="(config.chartFilters?.length + config.chartGenerators?.length)"
      style="position: relative; width: 25%; padding-left: 5px; border-left: 1px solid #C0C0C0;"
    >
      <app-spinner [visibility]="_metaLoader.active | async"></app-spinner>
      <ng-template [ngIf]="_metaLoader.loaded">
        <chart-filter *ngFor="let columnName of config.chartFilters"
          [value]="filters[columnName]"
          [stats]="stats"
          [table]="table"
          [columnName]="columnName"
          (valueChange)="setFilter($event)"></chart-filter>
        <chart-generator *ngFor="let columnName of config.chartGenerators"
          [value]="generators[columnName]"
          [stats]="stats"
          [table]="table"
          [columnName]="columnName"
          (valueChange)="setGenerator($event)"></chart-generator>
      </ng-template>
    </div>
  `,
  styles: [`
    @media (min-width: 992px) {
      .chart-filters {
        width: 25%;
        padding-left: 5px;
        border-left: 1px solid #C0C0C0;
      }
    }
  `],
})
export class OneDimensionalScatterPlotChartComponent extends ChartAbstract<DashboardCharts.IOneDimensionalScatterPlotChartOptions> {
  @HostBinding('class') classes = 'row-flex';
  @HostBinding('style.height') styleHeight = '100%';
  chartData: ChartData;

  readonly defaultOptions = DashboardCharts.defaultOneDimensionalScatterPlotChartOptions;

  protected chartOptions: OneDimScatterChartOptions;
  protected _rowsLimit = 10000;

  constructor(
    tables: TableService,
    models: ModelService,
    experiments: ExperimentService,
    dataFetch: VisualizeDataService,
    bus: CrossFilterBus,
    el: ElementRef,
    events: EventService,
  ) {
    super(tables, models, experiments, bus, dataFetch, el, events);
  }

  visualizeData(data: TabularDataResponse) {
    if (!data || !data.data || !data.data[0]) {
      return;
    }
    const attributeIndices = this.config.attributes.map(attr => data.columns.findIndex(column => column.name === attr));
    const metricIndices = this.config.metrics.map(metric => data.columns.findIndex(column => column.name === metric.columnName));

    this.chartOptions = {
      circleRadius: this.options.bubbleSize,
      seriesGamma: this.options.categoricalGamma,
      yJitter: this.options.yJitter,
      title: this.options.title,
      subtitle: this.options.subtitle,
      labelX: this.options.xAxisTitle,
    };

    this.chartData = {
      metrics: this.config.metrics.map(metric => {
        const columnInfo = data.columns.find(_ => _.name === metric.columnName);
        return {
          name: columnInfo
            ? columnInfo.displayName || columnInfo.name
            : metric.columnName,
        };
      }),
      attributes: this.config.attributes.map(attribute => {
        const columnInfo = data.columns.find(_ => _.name === attribute);
        return {
          name: columnInfo
            ? columnInfo.displayName || columnInfo.name
            : attribute,
        };
      }),
      series: [{
        title: this.options.xAxisTitle,
        data: data.data.map((row: TTableValue[]): ChartData.DataPoint => {
          return {
            values: <number[]> metricIndices.map(_ => row[_]),
            attributes: attributeIndices.map(_ => row[_]),
          };
        }),
      }],
    };
  }
}
