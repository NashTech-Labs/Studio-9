import { Component, ElementRef, HostBinding } from '@angular/core';

import * as d3 from 'd3';

import { EventService } from '../../core/services/event.service';
import { ExperimentService } from '../../experiments/experiment.service';
import { TableService } from '../../tables/table.service';
import { ModelService } from '../../train/model.service';
import { CrossFilterBus } from '../cross-filter-bus';
import { VisualizeDataService } from '../visualize-data.service';
import { TabularDataResponse } from '../visualize.interface';

import { DashboardCharts } from './chart.interfaces';
import { D3Chart, IPreparedValue } from './d3-chart.component';

const scales = {
  'PURE': ['#7f3b08', '#b35806', '#e08214', '#fdb863', '#fee0b6', '#f7f7f7', '#d8daeb', '#b2abd2', '#8073ac', '#542788', '#2d004b'],
  'SPECTRAL': ['#d53e4f', '#f46d43', '#fdae61', '#fee08b', '#e6f598', '#abdda4', '#66c2a5', '#3288bd'],
  'REDBLACKGREEN': ['#ff0000', '#AA0000', '#550000', '#005500', '#00AA00', '#00ff00'],
  'VIRIDIS': ['#440154', '#440256', '#450457', '#450559', '#46075a', '#46085c', '#460a5d', '#460b5e', '#470d60', '#470e61', '#471063', '#471164', '#471365', '#481467', '#481668', '#481769', '#48186a', '#481a6c', '#481b6d', '#481c6e', '#481d6f', '#481f70', '#482071', '#482173', '#482374', '#482475', '#482576', '#482677', '#482878', '#482979', '#472a7a', '#472c7a', '#472d7b', '#472e7c', '#472f7d', '#46307e', '#46327e', '#46337f', '#463480', '#453581', '#453781', '#453882', '#443983', '#443a83', '#443b84', '#433d84', '#433e85', '#423f85', '#424086', '#424186', '#414287', '#414487', '#404588', '#404688', '#3f4788', '#3f4889', '#3e4989', '#3e4a89', '#3e4c8a', '#3d4d8a', '#3d4e8a', '#3c4f8a', '#3c508b', '#3b518b', '#3b528b', '#3a538b', '#3a548c', '#39558c', '#39568c', '#38588c', '#38598c', '#375a8c', '#375b8d', '#365c8d', '#365d8d', '#355e8d', '#355f8d', '#34608d', '#34618d', '#33628d', '#33638d', '#32648e', '#32658e', '#31668e', '#31678e', '#31688e', '#30698e', '#306a8e', '#2f6b8e', '#2f6c8e', '#2e6d8e', '#2e6e8e', '#2e6f8e', '#2d708e', '#2d718e', '#2c718e', '#2c728e', '#2c738e', '#2b748e', '#2b758e', '#2a768e', '#2a778e', '#2a788e', '#29798e', '#297a8e', '#297b8e', '#287c8e', '#287d8e', '#277e8e', '#277f8e', '#27808e', '#26818e', '#26828e', '#26828e', '#25838e', '#25848e', '#25858e', '#24868e', '#24878e', '#23888e', '#23898e', '#238a8d', '#228b8d', '#228c8d', '#228d8d', '#218e8d', '#218f8d', '#21908d', '#21918c', '#20928c', '#20928c', '#20938c', '#1f948c', '#1f958b', '#1f968b', '#1f978b', '#1f988b', '#1f998a', '#1f9a8a', '#1e9b8a', '#1e9c89', '#1e9d89', '#1f9e89', '#1f9f88', '#1fa088', '#1fa188', '#1fa187', '#1fa287', '#20a386', '#20a486', '#21a585', '#21a685', '#22a785', '#22a884', '#23a983', '#24aa83', '#25ab82', '#25ac82', '#26ad81', '#27ad81', '#28ae80', '#29af7f', '#2ab07f', '#2cb17e', '#2db27d', '#2eb37c', '#2fb47c', '#31b57b', '#32b67a', '#34b679', '#35b779', '#37b878', '#38b977', '#3aba76', '#3bbb75', '#3dbc74', '#3fbc73', '#40bd72', '#42be71', '#44bf70', '#46c06f', '#48c16e', '#4ac16d', '#4cc26c', '#4ec36b', '#50c46a', '#52c569', '#54c568', '#56c667', '#58c765', '#5ac864', '#5cc863', '#5ec962', '#60ca60', '#63cb5f', '#65cb5e', '#67cc5c', '#69cd5b', '#6ccd5a', '#6ece58', '#70cf57', '#73d056', '#75d054', '#77d153', '#7ad151', '#7cd250', '#7fd34e', '#81d34d', '#84d44b', '#86d549', '#89d548', '#8bd646', '#8ed645', '#90d743', '#93d741', '#95d840', '#98d83e', '#9bd93c', '#9dd93b', '#a0da39', '#a2da37', '#a5db36', '#a8db34', '#aadc32', '#addc30', '#b0dd2f', '#b2dd2d', '#b5de2b', '#b8de29', '#bade28', '#bddf26', '#c0df25', '#c2df23', '#c5e021', '#c8e020', '#cae11f', '#cde11d', '#d0e11c', '#d2e21b', '#d5e21a', '#d8e219', '#dae319', '#dde318', '#dfe318', '#e2e418', '#e5e419', '#e7e419', '#eae51a', '#ece51b', '#efe51c', '#f1e51d', '#f4e61e', '#f6e620', '#f8e621', '#fbe723', '#fde725'],
};

const LEGEND_WIDTH = 50;

@Component({
  selector: 'scatter-plot-chart',
  template: `
    <div class="flex-rubber">
      <div class="svg" style="width: 100%; height: 100%;">
        <svg #canvas width="960" height="500"></svg>
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
export class ScatterPlotChartComponent extends D3Chart<DashboardCharts.IScatterPlotChartOptions> {
  @HostBinding('class') classes = 'row-flex';
  @HostBinding('style.height') styleHeight = '100%';
  readonly defaultOptions = DashboardCharts.defaultScatterPlotChartOptions;
  legend: d3.Selection<any, any, any, any>;

  protected xScale: d3.ScaleLinear<number, number>;
  protected yScale: d3.ScaleLinear<number, number>;
  protected _rowsLimit = 100;

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
    this.margin = { top: 40, right: 20, bottom: 50, left: 50 };
    if (this.config.metrics.length > 2) {
      this.margin.right += LEGEND_WIDTH;
    }
    super.visualizeData(data);

    if (!this.width || !this.height || !this.svgHeight || !this.svgWidth) {
      return;
    }

    this.prepareSeries(data);

    this.chartArea.selectAll('text.axis-label').remove();
    this.chartArea.select('.balls').remove(); //@TODO
    this.chartArea.selectAll('.ball-label').remove(); //@TODO
    let xMin = null, xMax = null, yMin = null, yMax = null, valueMin = null, valueMax = null;

    if (this.preparedSeries.length < 2) {
      return;
    }

    this.preparedSeries[0].values.forEach((data: IPreparedValue) => {
      if (xMin === null) {
        xMin = data.value;
      }
      if (xMax === null) {
        xMax = data.value;
      }
      xMin = Math.min(xMin, data.value);
      xMax = Math.max(xMax, data.value);
    });

    this.preparedSeries[1].values.forEach((data: IPreparedValue) => {
      if (yMin === null) {
        yMin = data.value;
      }
      if (yMax === null) {
        yMax = data.value;
      }
      yMin = Math.min(yMin, data.value);
      yMax = Math.max(yMax, data.value);
    });

    const scale = scales[this.options.gamma];
    let colourPct = [];

    if (this.preparedSeries.length === 3) {
      this.preparedSeries[2].values.forEach((data: IPreparedValue) => {
        if (valueMin === null) {
          valueMin = data.value;
        }
        if (valueMax === null) {
          valueMax = data.value;
        }
        valueMin = Math.min(valueMin, data.value);
        valueMax = Math.max(valueMax, data.value);
      });

      // create colour scale
      let pct = ScatterPlotChartComponent.linspace(0, 100, scale.length).map((d) => {
        return Math.round(d);
      });
      colourPct = d3.zip<string | number>(pct.map(_ => _ + '%'), scale, pct);
      this.chartColorScale = d3.scaleOrdinal<number, string>()
        .domain([valueMin, valueMax])
        .range(scale);
    } else {
      this.chartColorScale = d3.scaleOrdinal<number, string>()
        .domain([valueMin, valueMax])
        .range(d3.schemeCategory20);
    }

    // magic here :)
    const titleMargin = 50;
    const circleRadius = 10;
    this.xScale = d3.scaleLinear()
      .clamp(false)
      .domain([xMin, xMax])
      .range([circleRadius, this.width - circleRadius - titleMargin]);
    const [xMinExtended, xMaxExtended] = [0, this.width].map(_ => this.xScale.invert(_));
    this.xScale
      .domain([xMinExtended, xMaxExtended])
      .range([0, this.width])
      .nice(10);

    this.yScale = d3.scaleLinear()
      .clamp(false)
      .domain([yMin, yMax])
      .range([this.height - circleRadius, circleRadius]);
    const [yMinExtended, yMaxExtended] = [this.height, 0].map(_ => this.yScale.invert(_));
    this.yScale
      .domain([yMinExtended, yMaxExtended])
      .range([this.height, 0])
      .nice(10);

    let dots = [];

    const balls = this.chartArea
      .append('g')
      .attr('class', 'balls');
    for (let j = 0; j < this.rowsCount; j++) {
      const x = this.xScale(this.preparedSeries[0].values[j].value);
      const y = this.yScale(this.preparedSeries[1].values[j].value);
      const circle = balls
        .append('g')
        .attr('class', 'ball')
        .attr('transform', `translate(${x}, ${y})`)
        .attr('fill', () => {
          if (this.preparedSeries.length < 3) {
            return this.chartColorScale(0);
          }
          const percent = (this.preparedSeries[2].values[j].value - valueMin) * 100 / (valueMax - valueMin);
          const res = colourPct.find(_ => _[2] >= percent);
          return res[1];
        });
      circle.append('circle')
        .attr('cx', 0)
        .attr('cy', 0)
        .attr('r', circleRadius)
        .style('fill-opacity', 1);

      const labelX = x + circleRadius + 2;
      this.chartArea
        .append('text')
        .attr('class', 'ball-label')
        .style('text-anchor', 'left')
        .style('fill', 'black')
        .attr('y', y + 4)
        .attr('x', labelX)
        .text(this.preparedSeries[0].values[j].label.join(', '))
        .each(this._trimTextWrap(this.width - x - circleRadius - 2));
      dots.push(circle);

      //bindings
      this.chartArea.selectAll('.ball')
        .on('mouseover', (_, index) => {
          d3.select('.toolTip').remove();
          const divTooltip = d3.select('body').append('div').attr('class', 'toolTip');
          divTooltip.style('left', d3.event.pageX + 10 + 'px');
          divTooltip.style('top', d3.event.pageY - 25 + 'px');
          divTooltip.style('display', 'inline-block');
          if (this.preparedSeries[0].values[index]) {
            divTooltip.text(this.preparedSeries[0].values[index].label.join(', '));
          }
        })
        .on('mouseout', () => {
          d3.select('.toolTip').remove();
        });
    }

    const maxXAbs = Math.max(1, Math.abs(xMin), Math.abs(xMax));
    const xFormatPrecision = d3.precisionPrefix((xMax - xMin) / 10, maxXAbs);
    const xFormat = d3.formatPrefix(`.${xFormatPrecision}`, maxXAbs);
    const xAxis = this.chartArea.select('g.x-axis');
    const xLine = d3.axisBottom(this.xScale).tickFormat(xFormat).ticks(10);
    const xLineDom = xAxis.size()
      ? xAxis
      : this.chartArea.append('g').attr('class', 'x-axis');

    xAxis.size()
      ? xLineDom.call(xLine).transition().duration(500).attr('transform', 'translate(0, ' + this.height + ')')
      : xLineDom.call(xLine).attr('transform', 'translate(0, ' + this.height + ')');

    const xAxisColumn = this.preparedSeries[0].column;
    this.chartArea.append('text')
      .attr('class', 'axis-label')
      .attr('text-anchor', 'middle')  // this makes it easy to centre the text as the transform is applied to the anchor
      .attr('transform', `translate(${(this.width / 2)}, ${(this.height + this.margin.bottom - 5)})`)  // centre below axis
      .text(this.options.xAxisTitle || `${this.config.metrics[0].aggregator}(${xAxisColumn.displayName || xAxisColumn.name})`)
      .each(this._trimTextWrap(this.width));

    const maxYAbs = Math.max(1, Math.abs(yMin), Math.abs(yMax));
    const yFormatPrecision = d3.precisionPrefix((yMax - yMin) / 10, maxYAbs);
    const yFormat = d3.formatPrefix(`.${yFormatPrecision}`, maxYAbs);
    const yAxis = this.chartArea.select('g.y-axis');
    const yLine = d3.axisLeft(this.yScale).tickFormat(yFormat).ticks(10);
    const yLineDom = yAxis.size()
      ? yAxis
      : this.chartArea.append('g').attr('class', 'y-axis');

    yAxis.size()
      ? yLineDom.call(yLine).transition().duration(500).attr('transform', 'translate(0, 0)')
      : yLineDom.call(yLine).attr('transform', 'translate(0, 0)');

    const yAxisColumn = this.preparedSeries[1].column;
    this.chartArea.append('text')
      .attr('class', 'axis-label')
      .attr('text-anchor', 'middle')  // this makes it easy to centre the text as the transform is applied to the anchor
      .attr('transform', `translate(0, -10)`)  // on top of left axis
      .text(this.options.yAxisTitle || `${this.config.metrics[1].aggregator}(${yAxisColumn.displayName || yAxisColumn.name})`)
      .each(this._trimTextWrap(this.margin.left * 2));

    this.drawCenterLines(yMin < 0 && yMax > 0, xMin < 0 && xMax > 0);
    if (this.preparedSeries.length > 2) {
      this.drawGradientLegend(colourPct, valueMin, valueMax);
    } else {
      this.removeGradientLegend();
    }

    // Titles
    this.svg.selectAll('text.titles').remove();
    // Title
    this.svg.append('text')
      .attr('x', (this.svgWidth / 2))
      .attr('y', 15)
      .attr('text-anchor', 'middle')
      .attr('class', 'titles')
      .style('font-size', '16px')
      .style('text-decoration', 'underline')
      .text(this.options.title);
    // SubTitle
    this.svg.append('text')
      .attr('x', (this.svgWidth / 2))
      .attr('y', 30)
      .attr('text-anchor', 'middle')
      .attr('class', 'titles')
      .style('font-size', '10px')
      .text(this.options.subtitle);

  }

  drawCenterLines(xCenterLine: boolean, yCenterLine: boolean) {
    const centerXAxis = this.chartArea.select('g.x-center-line');
    if (xCenterLine) {
      const center = d3.scaleLinear()
        .rangeRound([0, this.width]);
      const centerLine = d3.axisTop(center).ticks(0);
      const line = centerXAxis.size()
        ? centerXAxis
        : this.chartArea.append('g').attr('class', 'x-center-line');

      if (!centerXAxis.size()) {
        line.call(centerLine).attr('transform', 'translate(0,' + this.height + ')');
      }
      line.call(centerLine).transition().duration(500).attr('transform', 'translate(0,' + this.yScale(0) + ')');
    } else if (centerXAxis.size()) {
      centerXAxis.remove();
    }

    const centerYAxis = this.chartArea.select('g.y-center-line');

    if (yCenterLine) {
      const center = d3.scaleLinear()
        .rangeRound([this.height, 0]);
      const centerLine = d3.axisRight(center).ticks(0);
      const line = centerYAxis.size()
        ? centerYAxis
        : this.chartArea.append('g').attr('class', 'y-center-line');

      if (!centerYAxis.size()) {
        line.call(centerLine).attr('transform', 'translate(' + this.width + ', 0)');
      }
      line.call(centerLine).transition().duration(500).attr('transform', 'translate(' + this.xScale(0) + ', 0)');
    } else if (centerYAxis.size()) {
      centerYAxis.remove();
    }
  }

  drawGradientLegend(colourPct, valueMin, valueMax) {
    // add the legend now
    const legendFullHeight = this.height;
    const legendFullWidth = LEGEND_WIDTH;

    const legendMargin = { top: 20, bottom: 20, left: 5, right: 20 };

    // use same margins as main plot
    const legendWidth = legendFullWidth - legendMargin.left - legendMargin.right;
    const legendHeight = legendFullHeight - legendMargin.top - legendMargin.bottom;

    this.svg.select('g.gradient-legend').remove();

    this.legend = this.svg
      .append('g')
      .attr('class', 'gradient-legend')
      .attr('width', legendFullWidth)
      .attr('height', legendFullHeight)
      .attr('transform', 'translate(' + (this.svgWidth - legendMargin.left - legendFullWidth) + ',' +
        legendMargin.top + ')');

    // clear current legend
    this.legend.selectAll('*').remove();

    const colorColumn = this.preparedSeries[2].column;
    this.legend.append('text')
      .attr('class', 'axis-label')
      .attr('text-anchor', 'middle')  // this makes it easy to centre the text as the transform is applied to the anchor
      .attr('transform', `translate(${legendWidth / 2}, -10)`)  // on top of legend
      .text(this.options.yAxisTitle || `${this.config.metrics[1].aggregator}(${colorColumn.displayName || colorColumn.name})`)
      .each(this._trimTextWrap(legendFullWidth));


    // append gradient bar
    let gradient = this.legend.append('defs')
      .append('linearGradient')
      .attr('id', 'gradient')
      .attr('x1', '0%') // bottom
      .attr('y1', '100%')
      .attr('x2', '0%') // to top
      .attr('y2', '0%')
      .attr('spreadMethod', 'pad');

    colourPct.forEach((d) => {
      gradient.append('stop')
        .attr('offset', d[0])
        .attr('stop-color', d[1])
        .attr('stop-opacity', 1);
    });

    this.legend.append('rect')
      .attr('x1', 0)
      .attr('y1', 0)
      .attr('width', legendWidth)
      .attr('height', legendHeight)
      .style('fill', 'url(#gradient)');

    // create a scale and axis for the legend
    let legendScale = d3.scaleLinear()
      .domain([valueMin, valueMax])
      .range([legendHeight, 0]);

    let legendAxis = d3.axisRight(legendScale)
      .ticks(10);

    this.legend.append('g')
      .attr('class', 'legend axis')
      .attr('transform', 'translate(' + legendWidth + ', 0)')
      .call(legendAxis);
  }

  removeGradientLegend() {
    this.svg.select('g.gradient-legend').remove();
  }

  static linspace(start, end, n) {
    let out = [];
    const delta = (end - start) / (n - 1);

    let i = 0;
    while (i < (n - 1)) {
      out.push(start + (i * delta));
      i++;
    }

    out.push(end);
    return out;
  }
}
