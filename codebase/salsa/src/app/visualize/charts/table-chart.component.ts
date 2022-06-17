import { DecimalPipe } from '@angular/common';
import { Component, ElementRef, HostBinding } from '@angular/core';

import * as d3 from 'd3';

import { EventService } from '../../core/services/event.service';
import { ExperimentService } from '../../experiments/experiment.service';
import { ITable } from '../../tables/table.interface';
import { TableService } from '../../tables/table.service';
import { ModelService } from '../../train/model.service';
import { ChartAbstract } from '../chart.abstract';
import { CrossFilterBus } from '../cross-filter-bus';
import { VisualizeDataService } from '../visualize-data.service';
import { TabularDataResponse } from '../visualize.interface';

import { DashboardCharts } from './chart.interfaces';

@Component({
  selector: 'visualize-chart-table',
  template: `
    <div class="flex-rubber">
      <div #canvas class="table-scroll" style="width: 100%; height: 100%; overflow-y: auto;">
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
export class TableChartComponent extends ChartAbstract<DashboardCharts.ITableChartOptions> {
  @HostBinding('class') classes = 'row-flex';
  @HostBinding('style.height') styleHeight = '100%';
  readonly defaultOptions: DashboardCharts.ITableChartOptions = DashboardCharts.defaultChartOptions;
  private _decimalPipe: DecimalPipe = new DecimalPipe('en-US');
  private _div: d3.Selection<HTMLDivElement, any, any, any>;

  constructor(tables: TableService, models: ModelService, experiments: ExperimentService, bus: CrossFilterBus, dataFetch: VisualizeDataService, el: ElementRef, events: EventService) {
    super(tables, models, experiments, bus, dataFetch, el, events);
  }

  visualizeData(data?: TabularDataResponse) {
    this._div = d3.select(this.selector.nativeElement);

    const oldTable = this._div.selectAll('table.table').data([true]);

    if (!data || !data.data || !data.data[0]) {
      oldTable.remove();
      return;
    }

    const table = oldTable.enter()
      .append('table')
      .attr('class', 'table')
      .merge(oldTable);

    const oldHeader = table.selectAll('thead').data([true]);
    const header = oldHeader.enter()
      .append('thead')
      .merge(oldHeader);

    const columns = data.columns.map(column => {
      const aggregatedColumn = this.config.metrics.find(_ => _.columnName === column.name);
      if (aggregatedColumn && aggregatedColumn.aggregator) {
        return `${aggregatedColumn.aggregator}(${column.displayName})`;
      }
      return column.displayName;
    });

    const oldHeaderColumns = header.selectAll('th').data(columns);

    oldHeaderColumns.exit().remove();

    oldHeaderColumns.enter()
      .append('th')
      .merge(oldHeaderColumns)
      .text((d) => d);

    const oldBody = table.selectAll('tbody').data([true]);
    const body = oldBody.enter()
      .append('tbody')
      .merge(oldBody);

    const oldRows = body.selectAll('tr').data(data.data);

    oldRows.exit().remove();

    const rows = oldRows.enter()
      .append('tr')
      .merge(oldRows);

    rows.order();

    const oldCols = rows.selectAll('td').data((d) => d);
    oldCols.exit().remove();

    oldCols.enter()
      .append('td')
      .merge(oldCols)
      .order()
      .text((d, i) => {
        switch (data.columns[i].dataType) {
          case ITable.ColumnDataType.DOUBLE:
            return this._formatFloat(<number> d);
          default:
            return d;
        }
      });
  }

  private _formatFloat(value: number): string {
    return this._decimalPipe.transform(value, '1.0-3');
  }
}
