import { DecimalPipe } from '@angular/common';
import { Directive, ElementRef, Input, NgZone, OnChanges, OnDestroy } from '@angular/core';

import * as Chart from 'chart.js';
import * as _ from 'lodash';

import { ITable, ITableColumn, ITableColumnHistogram } from './table.interface';

@Directive({
  selector: '[table-column-histogram]',
})
export class TableColumnHistogramDirective implements OnChanges, OnDestroy {
  @Input('table-column-histogram') histogram: ITableColumnHistogram;
  @Input('table-column') column: ITableColumn;
  private el: HTMLCanvasElement;
  private chartConfig: Chart.ChartConfiguration;
  private chart: Chart;
  private decimalPipe: DecimalPipe = new DecimalPipe('en-US');

  constructor(
    el: ElementRef,
    private zone: NgZone,
  ) {
    this.el = el.nativeElement;
  }

  ngOnChanges() {
    const isLinear = this.column.variableType === ITable.ColumnVariableType.CONTINUOUS;

    const data = this.histogram.map(row => row.count);
    const labels = isLinear
      ? this.histogram.map(_ => _.min).concat([_.last(this.histogram).max]).map(_ => this._formatFloat(_))
      : this.histogram.map(row => {
        if (row.value === null) {
          return 'Other...';
        }
        switch (this.column.dataType) {
          case ITable.ColumnDataType.DOUBLE:
            return this._formatFloat(<number> row.value);
          case ITable.ColumnDataType.INTEGER:
          case ITable.ColumnDataType.LONG:
            return row.value.toString();
          default:
            return String(row.value);
        }
      });

    this.chartConfig = {
      type: 'bar',
      data: {
        labels,
        datasets: [{
          borderWidth: 0,
          backgroundColor: '#0d47a1',
          data,
        }],
      },
      options: {
        scales: {
          xAxes: [
            {
              categoryPercentage: 1,
              barPercentage: 1,
              gridLines: {
                offsetGridLines: !isLinear,
                drawOnChartArea: false,
              },
              ticks: {
                autoSkip: false,
                maxRotation: 90,
                minRotation: 0,
              },
            },
          ],
          yAxes: [{
            //type: 'logarithmic',
            ticks: {
              min: 0,
            },
          }],
        },
        tooltips: <Chart.ChartTooltipOptions> {
          callbacks: isLinear ? {
            title: () => '',
          } : {},
          mode: 'x',
          intersect: false,
        },
        elements: {
          rectangle: {
            borderWidth: 2,
          },
        },
        responsive: true,
        legend: {
          display: false,
        },
        title: {
          display: true,
          text: 'histogram',
        },
      },
    };

    this.zone.runOutsideAngular(() => {
      this.chart && this.chart.destroy();
      this.chart = new Chart(this.el, this.chartConfig);
    });
  }

  ngOnDestroy(): void {
    this.zone.runOutsideAngular(() => {
      this.chart && this.chart.destroy();
    });
  }

  private _formatFloat(value: number): string {
    return this.decimalPipe.transform(value, '1.0-2');
  }
}
