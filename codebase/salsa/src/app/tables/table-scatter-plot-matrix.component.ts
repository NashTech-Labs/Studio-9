import { DecimalPipe } from '@angular/common';
import { Component, ElementRef, HostBinding, Input, NgZone, OnChanges, SimpleChanges, ViewChild } from '@angular/core';

import * as d3 from 'd3';
import { ScaleLinear } from 'd3-scale';
import 'rxjs/add/observable/forkJoin';
import { Observable } from 'rxjs/Observable';

import {
  IScatterSummary,
  IScatterSummaryChart,
  ITable,
  ITableStats,
  TTableValue,
} from './table.interface';
import { TableService } from './table.service';

@Component({
  selector: 'table-scatter-plot-matrix',
  template: `
    <app-spinner [visibility]="!summary"></app-spinner>
    <div class="flex-static">
      <app-select *ngIf="summary" [label]="'Columns'" [(value)]="_selectedColumns" (valueChange)="drawScatter()"
        [multiple]="true"
        [options]="summary.columns | tableColumnSelectOptions"></app-select>
    </div>
    <div class="flex-rubber">
      <div class="svg" style="width: 100%; height: 100%;">
        <svg #canvas width="805" height="805" style="background-color: white;"></svg>
      </div>
    </div>
  `,
  styles: [
    `svg {
      width: 100%;
      height: 100%;
    }`,
  ],
})
export class TableScatterPlotMatrixComponent implements OnChanges {
  @HostBinding('style.height') styleHeight = '100%';
  @HostBinding('class') classes = 'flex-col';
  @Input() table: ITable = null;
  summary: IScatterSummary;
  _selectedColumns: string[] = [];
  @ViewChild('canvas') private canvas: ElementRef;
  private chartPadding: number = 6;
  private svg: d3.Selection<SVGElement, any, any, any>;
  private chartArea: d3.Selection<SVGGElement, any, SVGElement, any>;
  private areaWidth: number;
  private areaHeight: number;
  private margin: { left: number; right: number; top: number, bottom: number };
  private svgWidth: number;
  private svgHeight: number;
  private stats: ITableStats;
  private decimalPipe: DecimalPipe = new DecimalPipe('en-US');

  constructor(
    private tables: TableService,
    private zone: NgZone,
  ) {
    this.margin = { top: 0, right: 0, bottom: 100, left: 100 };
  }

  ngOnChanges(changes: SimpleChanges) {
    if ('table' in changes) {
      Observable.forkJoin(
        this.tables.getStatistic(this.table.id),
        this.tables.scatterSummary(this.table.id),
      ).subscribe(([stats, summary]) => {
        this.stats = stats;
        this.summary = summary;
        this._selectedColumns = summary.columns.slice(0, 5).map(_ => _.name);
        this.drawScatter();
      });
    }
  }

  drawScatter() {
    this.svg = d3.select(this.canvas.nativeElement);
    this.svgWidth = parseInt(this.svg.style('width'), 10);
    this.svgHeight = parseInt(this.svg.style('height'), 10);
    this.areaWidth = this.svgWidth - this.margin.left - this.margin.right;
    this.areaHeight = this.svgHeight - this.margin.top - this.margin.bottom;

    this.svg.select('.chartArea').remove();

    if (!this.summary.data.length || !this._selectedColumns || this._selectedColumns.length < 2) {
      return;
    }

    this.chartArea = this.svg
      .append<SVGGElement>('g')
      .attr('class', 'chartArea')
      .attr('width', this.areaWidth)
      .attr('height', this.areaHeight)
      .attr('transform', 'translate(' + this.margin.left + ',' + this.margin.top + ')');

    const steps = this.summary.quantizationSteps;
    const selectedsummaryColumns = this.summary.columns.filter(column => this._selectedColumns.indexOf(column.name) > -1).map(_ => _.name);
    const columnNames = this.summary.columns.reduce((acc, _) => {
      acc[_.name] = _.displayName || _.name;
      return acc;
    }, {});

    const columnsCount = selectedsummaryColumns.length;
    const chartSize = (Math.min(this.areaWidth, this.areaHeight) / columnsCount);
    const chartInnerSize = (chartSize - this.chartPadding * 2);
    const rectSize = chartInnerSize / steps;

    const scales: {[p: string]: ScaleLinear<number, number>} = this.stats.stats.reduce((acc, _) => {
      acc[_.columnName] = d3.scaleLinear()
        .domain([_.min, _.max])
        .range([this.chartPadding, chartSize - this.chartPadding]);

      return acc;
    }, {});

    //drawing axes
    for (let i = 0; i < columnsCount; i++) {
      const xScale = scales[selectedsummaryColumns[i]];
      const yScale = xScale.copy().range(xScale.range().reverse());

      const columnName = this.table.columns.find(column => column.name === selectedsummaryColumns[i]).displayName || selectedsummaryColumns[i];
      if (i < columnsCount - 1) {
        const xAxis = d3.axisBottom<number>(xScale).tickValues(xScale.domain());

        this.chartArea.append('g')
          .attr('class', 'x-axis')
          .attr('transform', `translate(${(i * chartSize)}, ${(chartSize * (columnsCount - 1)) + this.chartPadding})`)
          .call(xAxis)
          .selectAll('text')
          .attr('text-anchor', 'end')
          .attr('dx', '-0.8em')
          .attr('dy', '-0.5em')
          .attr('transform', `rotate(-90)`);

        this.chartArea.append('text')
          .attr('class', 'x-axis-label')
          .attr('text-anchor', 'end')
          .attr('transform', `translate(${(i * chartSize) + chartSize / 2}, ${(chartSize * (columnsCount - 1)) + this.chartPadding + 5})  rotate(-90)`)
          .text(columnName)
          .each(this._trimTextWrap(this.margin.bottom - this.chartPadding - 5));
      }
      if (i > 0) {
        const yAxis = d3.axisLeft<number>(yScale).tickValues(yScale.domain());

        this.chartArea.append('g')
          .attr('class', 'y-axis')
          .attr('transform', `translate(${-this.chartPadding} , ${((i - 1) * chartSize) + this.margin.top})`)
          .call(yAxis);

        this.chartArea.append('text')
          .attr('class', 'y-axis-label')
          .attr('text-anchor', 'end')
          .attr('transform', `translate(${-this.chartPadding - 5}, ${(chartSize * (i - 1)) + this.margin.top + chartSize / 2})`)
          .text(columnName)
          .each(this._trimTextWrap(this.margin.left - this.chartPadding - 5));
      }
    }

    const drawnCharts = this.summary.data.filter(_ => {
      return selectedsummaryColumns.findIndex(column => column === _.column1) >= 0 &&
          selectedsummaryColumns.findIndex(column => column === _.column2) >= 0;
    });

    const charts = this.chartArea
      .selectAll('g.scatter-chart')
      .data<IScatterSummaryChart>(drawnCharts)
      .enter()
      .append('g')
      .attr('class', 'scatter-chart')
      .attr('transform', (d) => {
        const index1 = selectedsummaryColumns.findIndex(column => column === d.column1);
        const index2 = selectedsummaryColumns.findIndex(column => column === d.column2);
        return `translate(${(chartSize * index1)}, ${(chartSize * (index2 - 1))})`;
      });

    charts.append('rect')
      .attr('class', 'scatter-chart-border')
      .attr('fill', 'white')
      .attr('stroke-width', 1)
      .attr('stroke', 'black')
      .attr('transform', `translate(${this.chartPadding}, ${this.chartPadding})`)
      .attr('width', chartInnerSize)
      .attr('height', chartInnerSize);

    const rects = charts.selectAll('rect.scatter-chart-dot')
      .data(d => {
        const xScale = scales[d.column1];
        const yScale = scales[d.column2].copy()
          .range(scales[d.column2].range().reverse());
        const colorScale = d3.scaleQuantize<string>()
          .domain([0, d3.max(d.values, _ => _.count)])
          .range(d3.range(0, 256).map(_ => `rgb(${_}, ${_}, 255)`).reverse());

        return d.values.map(_ => {
          return Object.assign({
            x: xScale(_.value1),
            y: yScale(_.value2),
            color: colorScale(_.count),
            column1: d.column1,
            column2: d.column2,
          }, _);
        });
      })
      .enter()
      .append('rect')
      .attr('class', 'scatter-chart-dot')
      .attr('transform', function(d) {
        return `translate(${d.x}, ${d.y - rectSize})`;
      })
      .attr('stroke-width', 0)
      .attr('fill', function(d) {
        return d.color;
      })
      .attr('width', rectSize)
      .attr('height', rectSize);

    this.zone.runOutsideAngular(() => {
      rects
        .on('mouseover', (d) => {
          d3.select('.toolTip').remove();

          const divTooltip = d3.select('body').append('div').attr('class', 'toolTip');
          divTooltip.style('left', d3.event.pageX + 10 + 'px');
          divTooltip.style('top', d3.event.pageY - 25 + 'px');
          divTooltip.style('display', 'inline-block');

          divTooltip.html(`<table cellspacing="5" cellpadding="10" border="1">
            <thead><tr><th>${columnNames[d.column2]}</th><th>${columnNames[d.column1]}</th><th>Count</th></tr></thead>
            <tbody><tr><td>${this._formatNumber(d.value2)}</td><td>${this._formatNumber(d.value1)}</td><td>${d.count}</td></tr></tbody>
          </table>`);
        })
        .on('mouseout', () => {
          d3.select('.toolTip').remove();
        });
    });
  }

  private _formatNumber(value: TTableValue) {
    return this.decimalPipe.transform(parseFloat(<string> value), '1.0-1');
  }

  private _trimTextWrap(width) {
    return function () {
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
