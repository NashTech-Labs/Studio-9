import { Component, ElementRef, HostBinding } from '@angular/core';

import * as d3 from 'd3';

import config from '../../config';
import { EventService } from '../../core/services/event.service';
import { ExperimentService } from '../../experiments/experiment.service';
import { TableService } from '../../tables/table.service';
import { ModelService } from '../../train/model.service';
import { CrossFilterBus } from '../cross-filter-bus';
import { VisualizeDataService } from '../visualize-data.service';

import { AxisChart } from './axis-chart.component';
import { DashboardCharts } from './chart.interfaces';
import { IPreparedValue } from './d3-chart.component';

@Component({
  selector: 'line-chart',
  template: `
    <div class="flex-rubber">
      <div class="svg" style="width: 100%; height: 100%;">
        <svg #canvas width="960" height="500"></svg>
      </div>
    </div>
    <div #filterEl [hidden]="!showFilters" class="chart-filters flex-static"
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
export class LineChartComponent extends AxisChart<DashboardCharts.ILineChartOptions> {
  @HostBinding('class') classes = 'row-flex';
  @HostBinding('style.height') styleHeight = '100%';
  readonly defaultOptions = DashboardCharts.defaultLineChartOptions;

  constructor(
    tables: TableService,
    models: ModelService,
    experiments: ExperimentService,
    bus: CrossFilterBus,
    dataFetch: VisualizeDataService,
    el: ElementRef,
    events: EventService,
  ) {
    super(tables, models, experiments, bus, dataFetch, el, events);
  }

  visualizeData(data?: any) {
    super.visualizeData(data);

    if (!data || !data.data || !data.data[0]) {
      return;
    }

    this.drawLegend(3);

    if (!('orientation' in this.options) || this.options.orientation === config.chart.options.orientation.values.VERTICAL) {
      this.metricScale.domain([Math.min(this.yMin, 0), Math.max(this.yMax, 0)]);
    }

    this.refreshAxes(this.yMin < 0 && this.yMax > 0);
    this.makeGridLines();

    if (this.preparedSeries.length) {
      const valueline = d3.line<IPreparedValue>()
        .x((d, i) => {
          if (!('orientation' in this.options) || this.options.orientation === config.chart.options.orientation.values.VERTICAL) {
            return this.attributeScale(i) + this.attributeScale.bandwidth() / 2;
          }
          if (this.options.orientation === config.chart.options.orientation.values.HORIZONTAL) {
            return this.metricScale(d.value);
          }
        })
        .y((d, i) => {
          if (!('orientation' in this.options) || this.options.orientation === config.chart.options.orientation.values.VERTICAL) {
            return this.metricScale(d.value);
          }
          if (this.options.orientation === config.chart.options.orientation.values.HORIZONTAL) {
            return this.attributeScale(i) + this.attributeScale.bandwidth() / 2;
          }
        });

      this.preparedSeries.forEach((series, i) => {
        //const previousSeries = this.previousSeries.find(_ => _.column.name === series.column.name);
        const previousSeries = this.previousSeries[i];

        const metricName = series.column.name;
        if (previousSeries && previousSeries.series) {
          series.series = previousSeries.series;
          previousSeries.series = null;
        } else {
          series.series = this.chartArea
            .append<SVGPathElement>('path')
            .attr('class', 'series')
            .attr('data-series', metricName);
        }

        series.series.transition()
          .attr('d', valueline(series.values))
          .style('stroke', this.chartColorScale(i))
          .style('stroke-width', '2px')
          .style('fill', 'none');
      });
    }

    this.previousSeries.forEach(_ => _.series && _.series.remove());

    this.previousSeries = this.preparedSeries;
  }

}
