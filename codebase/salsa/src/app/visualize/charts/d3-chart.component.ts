import { DecimalPipe } from '@angular/common';
import { ElementRef } from '@angular/core';

import * as d3 from 'd3';

import { EventService } from '../../core/services/event.service';
import { ExperimentService } from '../../experiments/experiment.service';
import { ITable, ITableColumn } from '../../tables/table.interface';
import { TableService } from '../../tables/table.service';
import { ModelService } from '../../train/model.service';
import { ChartAbstract } from '../chart.abstract';
import { CrossFilterBus } from '../cross-filter-bus';
import { VisualizeDataService } from '../visualize-data.service';
import { TabularDataRequest, TabularDataResponse } from '../visualize.interface';

import { DashboardCharts } from './chart.interfaces';

export interface IPreparedSeries {
  series: d3.Selection<SVGGraphicsElement, any, SVGElement, any>;
  name: string;
  metric: TabularDataRequest.Aggregation;
  innerName: string;
  column: ITableColumn;
  label: string[];
  values: IPreparedValue[];
}

export interface IPreparedValue {
  hash: string;
  label: string[];
  rect: d3.Selection<SVGGraphicsElement, any, SVGElement, any>;
  value: number;
}

export abstract class D3Chart<T extends DashboardCharts.IChartOptions> extends ChartAbstract<T> {
  protected seriesCount: number;
  protected rowsCount: number;

  protected svg: d3.Selection<SVGElement, any, any, any>;
  protected chartArea: d3.Selection<SVGGElement, any, SVGElement, any>;
  protected width: number;
  protected height: number;
  protected margin: { left: number; right: number; top: number, bottom: number };
  protected svgWidth: number;
  protected svgHeight: number;
  protected potentialLegendWidth: number = 100;
  protected chartColorScale: d3.ScaleOrdinal<number, string>;

  protected previousSeries: IPreparedSeries[] = [];
  protected preparedSeries: IPreparedSeries[] = [];
  protected decimalPipe: DecimalPipe = new DecimalPipe('en-US');

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
    this.svg = d3.select(this.selector.nativeElement);
    this.svgWidth = parseInt(this.svg.style('width'), 10);
    this.svgHeight = parseInt(this.svg.style('height'), 10);
    //this.potentialLegendWidth = 70; //@TODO calculate it
    this.margin = this.margin || { top: 40, right: 10 + this.potentialLegendWidth, bottom: 10, left: 10 };
    this.width = this.svgWidth - this.margin.left - this.margin.right;
    this.height = this.svgHeight - this.margin.top - this.margin.bottom;
    this.chartArea = this.svg.select<SVGGElement>('g.chartArea');
    if (!this.chartArea.size()) {
      this.chartArea = this.svg
        .append<SVGGElement>('g')
        .attr('class', 'chartArea')
        .attr('transform', 'translate(' + this.margin.left + ',' + this.margin.top + ')');
    }
  }

  prepareSeries(data: TabularDataResponse) {
    if (!data || !data.data || !data.data[0]) {
      return;
    }

    this.preparedSeries = [];
    // preparing data
    const metricIndexes = (this.config.metrics || []).map(metric => data.columns.findIndex(_ => _.name === metric.columnName)).filter(_ => _ >= 0);
    const attributeIndexes = (this.config.attributes || []).map(attribute => data.columns.findIndex(_ => _.name === attribute)).filter(_ => _ >= 0);
    this.preparedSeries = metricIndexes.map((columnIndex, index): IPreparedSeries => {
      let column = this._response.columns[columnIndex];
      return {
        metric: this.config.metrics[index],
        series: null,
        name: `${this.config.metrics[index].aggregator}(${column.displayName})`,
        column,
        innerName: column.name,
        label: attributeIndexes.map(_ => data.columns[_]).map(_ => _.displayName || _.name),
        values: data.data.map((row): IPreparedValue => {
          return {
            hash: attributeIndexes.map(_ => {
              return encodeURIComponent(<string> row[_]);
            }).join('&'),
            label: attributeIndexes.map(_ => {
              if (data.columns[_].dataType === ITable.ColumnDataType.DOUBLE) {
                return this.decimalPipe.transform(parseFloat(<string> row[_]), '1.0-1');
              }
              return String(row[_]);
            }),
            rect: null,
            value: <number> row[columnIndex],
          };
        }),
      };
    });

    this.seriesCount = this.preparedSeries.length; // The number of series.
    this.rowsCount = this.preparedSeries.reduce((acc, row) => {
      return Math.max(acc, row.values.length);
    }, 0);

    this.chartColorScale = d3.scaleOrdinal<number, string>()
      .domain(d3.range(this.seriesCount))
      .range(d3.schemeCategory10);

    //sort
    this.preparedSeries.forEach(row => {
      row.values.sort((a, b) => {
        if (a.hash < b.hash) {
          return -1;
        } else {
          return 1;
        }
      });
    });
  }

  drawLegend(height?: number) {
    this.svg.selectAll('.legend').remove();
    if (!this.preparedSeries || !this.preparedSeries.length || !this._response || !('columns' in this._response) || !this._response.columns.length) {
      return;
    }
    const legend = this.svg.selectAll('.legend')
      .data(this.preparedSeries.map(_ => `${_.metric.aggregator}(${_.column.displayName || _.column.name})`))
      .enter().append('g')
      .attr('class', 'legend')
      .attr('transform', (d, i) => {
        return `translate(${this.width + this.margin.left + this.potentialLegendWidth}, ${i * 20})`;
      });

    legend.append('rect')
      .attr('x', -18)
      .attr('y', height ? 18 : 9)
      .attr('width', 18)
      .attr('height', height ? height : 18)
      .style('fill', (d, i) => {
        return this.chartColorScale(i);
      });

    legend.append('text')
      .attr('x', -24)
      .attr('y', 18)
      .attr('dy', '.35em')
      .style('text-anchor', 'end')
      .text((d) => {
        return d;
      })
      .each(this._trimTextWrap(this.potentialLegendWidth - 26));

    this.svg.selectAll('text.titles').remove();
    //Title
    this.svg.append('text')
      .attr('x', (this.svgWidth / 2))
      .attr('y', 15)
      .attr('text-anchor', 'middle')
      .attr('class', 'titles')
      .style('font-size', '16px')
      .style('text-decoration', 'underline')
      .text(this.options.title);
    //SubTitle
    this.svg.append('text')
      .attr('x', (this.svgWidth / 2))
      .attr('y', 30)
      .attr('text-anchor', 'middle')
      .attr('class', 'titles')
      .style('font-size', '10px')
      .text(this.options.subtitle);
  }

  protected _trimTextWrap(width) {
    return function() {
      let self = d3.select(this),
        textLength = self.node().getComputedTextLength(),
        text = self.text();
      while (textLength > width && text.length > 0) {
        text = text.slice(0, -1);
        self.text(text + '…');
        textLength = self.node().getComputedTextLength();
      }
    };
  }
}
