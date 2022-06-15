import { Component, ElementRef, HostBinding } from '@angular/core';

import * as d3 from 'd3';

import config from '../../config';
import { EventService } from '../../core/services/event.service';
import { ExperimentService } from '../../experiments/experiment.service';
import { TableService } from '../../tables/table.service';
import { ModelService } from '../../train/model.service';
import { CrossFilterBus } from '../cross-filter-bus';
import { VisualizeDataService } from '../visualize-data.service';
import { TabularDataResponse } from '../visualize.interface';

import { AxisChart } from './axis-chart.component';
import { DashboardCharts } from './chart.interfaces';
import { IPreparedSeries } from './d3-chart.component';

@Component({
  selector: 'bar-chart',
  template: `
    <div class="flex-col vertical-align" style="width: 100%"
      *ngIf="!config.attributes.length || !config.metrics.length">
      <div class="text-center" style="width: 100%">
        <a (click)="edit()" style="font-size: 50px"><i class="glyphicon glyphicon-pencil"></i>Edit</a>
      </div>
    </div>
    <div class="flex-rubber" [hidden]="!config.attributes.length || !config.metrics.length">
      <div class="svg" style="width: 100%; height: 100%;">
        <svg #canvas width="960" height="500"></svg>
      </div>
    </div>
    <div #filterEl [hidden]="!showFilters" class="chart-filters flex-static"
      *ngIf="(config?.chartFilters?.length + config?.chartGenerators?.length)"
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
export class BarChartComponent extends AxisChart<DashboardCharts.IBarChartOptions> {
  @HostBinding('class') classes = 'row-flex';
  @HostBinding('style.height') styleHeight = '100%';
  readonly defaultOptions = DashboardCharts.defaultBarChartOptions;

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

  visualizeData(data: TabularDataResponse) {
    super.visualizeData(data);

    if (!this.width || !this.height || !this.svgHeight || !this.svgWidth || !this.yz) {
      return;
    }

    const y0Positive = new Array(this.rowsCount).fill(0);
    const y0Negative = new Array(this.rowsCount).fill(0);

    //data for stacked bars
    const y01z = this.yz.map((values, i) => {
      let res = values.map((d, key) => {
        const row = [y0Negative[key], y0Positive[key], d];
        if (d >= 0) {
          y0Positive[key] += d;
        } else if (d < 0) {
          y0Negative[key] += d;
        }
        return row;
      });
      res['series'] = i;
      return res;
    });
    const y1Min = d3.min(y01z, (y) => {
      return d3.min(y, (d) => {
        return d[0] + d[2];
      });
    });
    const y1Max = d3.max(y01z, (y) => {
      return d3.max(y, (d) => {
        return d[1] + d[2];
      });
    });
    let showCenterLine = false;
    if (this.options.type === config.chart.options.type.values.GROUPED) {
      // GROUPED
      this.metricScale.domain([Math.min(this.yMin, 0), Math.max(this.yMax, 0)]);
      if (this.yMin < 0 && this.yMax > 0) {
        showCenterLine = true;
      }
    } else {
      // STACKED
      this.metricScale.domain([Math.min(y1Min, 0), Math.max(y1Max, 0)]);
      if (y1Min < 0 && y1Max > 0) {
        showCenterLine = true;
      }
    }
    this.drawLegend();
    this.refreshAxes(showCenterLine);
    this.makeGridLines();

    if (this.preparedSeries.length) {
      //draw series
      this.preparedSeries.forEach((series, i) => {
        //const previousSeries = this.previousSeries.find(_ => _.column.name === metricName);
        const previousSeries = this.previousSeries[i];
        if (previousSeries && previousSeries.series) {
          series.series = previousSeries.series;
          previousSeries.series = null;
          this.drawRects(i, previousSeries);
        } else {
          this.newSeries(i);
          this.drawRects(i);
        }

      });

      let tooltipValues = [];
      this.preparedSeries.forEach((series, i) => {
        for (let j = 0; j < this.rowsCount; j++) {
          tooltipValues.push({ rectId: `${series.name}:${series.values[j].hash}`, group: series.values[j].label, metric: series.name, value: series.values[j].value });
        }
      });
      //bindings
      this.chartArea.selectAll('g.series rect')
        .on('mouseover', function () {
          d3.select('.toolTip').remove();
          const rectId = d3.select(this).attr('data-rect-id');
          const tooltip = tooltipValues.find(_ => _.rectId === rectId);
          if (tooltip) {
            const divTooltip = d3.select('body').append('div').attr('class', 'toolTip');
            divTooltip.style('left', d3.event.pageX + 10 + 'px');
            divTooltip.style('top', d3.event.pageY - 25 + 'px');
            divTooltip.style('display', 'inline-block');
            divTooltip.html(`<b>${tooltip.group}</b><br />${tooltip.metric} = ${tooltip.value}`);
          }
        })
        .on('mouseout', () => {
          d3.select('.toolTip').remove();
        });

      //drawing
      for (let i = 0; i < this.seriesCount; i++) {
        for (let j = 0; j < this.rowsCount; j++) {
          const currentRect = this.preparedSeries[i].values[j].rect;
          const d = y01z[i][j];
          if (!currentRect) {
            continue;
          }
          currentRect.transition()
            .duration(500)
            .delay(() => {
              return (j + 1) * 10;
            })
            .attr('x', () => {
              if (this.options.orientation === config.chart.options.orientation.values.VERTICAL) {
                if (this.options.type === config.chart.options.type.values.GROUPED) {
                  // GROUPED
                  return this.attributeScale(j) + this.attributeScale.bandwidth() / this.seriesCount * i;
                }
                // STACKED
                return this.attributeScale(j);
              }
              if (this.options.orientation === config.chart.options.orientation.values.HORIZONTAL) {
                if (this.options.type === config.chart.options.type.values.GROUPED) {
                  // GROUPED
                  return d[2] < 0 ? this.metricScale(d[2]) : this.metricScale(0);
                }
                // STACKED
                return d[2] < 0 ? this.metricScale(d[2] + d[0]) : this.metricScale(d[1]);
              }
            })
            .attr('y', () => {
              if (this.options.orientation === config.chart.options.orientation.values.VERTICAL) {
                if (this.options.type === config.chart.options.type.values.GROUPED) {
                  // GROUPED
                  return d[2] < 0 ? this.metricScale(0) : this.metricScale(d[2]);
                }
                // STACKED
                return d[2] < 0 ? this.metricScale(d[0]) : this.metricScale(d[2] + d[1]);
              }
              if (this.options.orientation === config.chart.options.orientation.values.HORIZONTAL) {
                if (this.options.type === config.chart.options.type.values.GROUPED) {
                  // GROUPED
                  return this.attributeScale(j) + this.attributeScale.bandwidth() / this.seriesCount * i;
                }
                // STACKED
                return this.attributeScale(j);
              }
            })
            .attr('width', () => {
              if (this.options.orientation === config.chart.options.orientation.values.VERTICAL) {
                if (this.options.type === config.chart.options.type.values.GROUPED) {
                  // GROUPED
                  return this.attributeScale.bandwidth() / this.seriesCount;
                }
                // STACKED
                return this.attributeScale.bandwidth();
              }
              if (this.options.orientation === config.chart.options.orientation.values.HORIZONTAL) {
                if (this.options.type === config.chart.options.type.values.GROUPED) {
                  // GROUPED
                  return (d[2] < 0)
                    ? Math.abs(this.metricScale(0) - this.metricScale(d[2]))
                    : Math.abs(this.metricScale(d[2]) - this.metricScale(0));
                }
                // STACKED
                return (d[2] < 0)
                  ? Math.abs(this.metricScale(0) - this.metricScale(d[2]))
                  : Math.abs(this.metricScale(0) - this.metricScale(d[2]));
              }
            })
            .attr('height', () => {
              if (this.options.orientation === config.chart.options.orientation.values.VERTICAL) {
                if (this.options.type === config.chart.options.type.values.GROUPED) {
                  // GROUPED
                  return (d[2] < 0)
                    ? Math.abs(this.metricScale(0) - this.metricScale(d[2]))
                    : Math.abs(this.metricScale(d[2]) - this.metricScale(0));
                }
                // STACKED
                return (d[2] < 0)
                  ? Math.abs(this.metricScale(0) - this.metricScale(d[2]))
                  : Math.abs(this.metricScale(d[2]) - this.metricScale(0));
              }
              if (this.options.orientation === config.chart.options.orientation.values.HORIZONTAL) {
                if (this.options.type === config.chart.options.type.values.GROUPED) {
                  // GROUPED
                  return this.attributeScale.bandwidth() / this.seriesCount;
                }
                // STACKED
                return this.attributeScale.bandwidth();
              }
            });
        }
      }
    }

    this.previousSeries.forEach(_ => _.series && _.series.remove());

    this.previousSeries = this.preparedSeries;
    this.chartArea.selectAll('g.series').raise();
  }

  newSeries(i1) {
    this.preparedSeries[i1].series = this.chartArea
      .append<SVGGElement>('g')
      .attr('class', 'series');
  }

  newRect(i1, j1) {
    this.preparedSeries[i1].values[j1].rect = this.preparedSeries[i1].series
      .append<SVGRectElement>('rect')
      .attr('data-rect-id', `${this.preparedSeries[i1].name}:${this.preparedSeries[i1].values[j1].hash}`)
      .attr('x', () => {
        if (this.options.orientation === config.chart.options.orientation.values.VERTICAL) {
          return this.attributeScale(j1);
        }
        if (this.options.orientation === config.chart.options.orientation.values.HORIZONTAL) {
          return this.metricScale(0);
        }
      })
      .attr('y', () => {
        if (this.options.orientation === config.chart.options.orientation.values.VERTICAL) {
          return this.metricScale(0);
        }
        if (this.options.orientation === config.chart.options.orientation.values.HORIZONTAL) {
          return this.attributeScale(j1);
        }
      })
      .attr('width', () => {
        if (this.options.orientation === config.chart.options.orientation.values.VERTICAL) {
          if (this.options.type === config.chart.options.type.values.GROUPED) {
            return this.attributeScale.bandwidth() / this.preparedSeries.length;
          }
          return this.attributeScale.bandwidth();
        }
        if (this.options.orientation === config.chart.options.orientation.values.HORIZONTAL) {
          return 0;
        }
      })
      .attr('height', () => {
        if (this.options.orientation === config.chart.options.orientation.values.VERTICAL) {
          return 0;
        }
        if (this.options.orientation === config.chart.options.orientation.values.HORIZONTAL) {
          if (this.options.type === config.chart.options.type.values.GROUPED) {
            return this.attributeScale.bandwidth() / this.preparedSeries.length;
          }
          return this.attributeScale.bandwidth();
        }
      });
  }

  drawRects(i1: number, previousSeries?: IPreparedSeries) {
    this.preparedSeries[i1].series.attr('fill', this.chartColorScale(i1));

    let j1 = 0;
    while (j1 < this.rowsCount) {
      const currentHash = this.preparedSeries[i1].values[j1].hash;
      const previousValue = previousSeries && previousSeries.values.find(value => value.hash === currentHash);
      if (previousValue && previousValue.rect) {
        this.preparedSeries[i1].values[j1].rect = previousValue.rect;
        previousValue.rect = null;
      } else {
        this.newRect(i1, j1);
      }
      j1++;
    }
    if (previousSeries) {
      for (let i = 0; i < previousSeries.values.length; i++) {
        if (previousSeries.values[i].rect) {
          previousSeries.values[i].rect.remove();
        }
      }
    }
  }
}
