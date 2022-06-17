import { Component, ElementRef, HostBinding } from '@angular/core';

import * as d3 from 'd3';
import { PieArcDatum } from 'd3-shape';

import { EventService } from '../../core/services/event.service';
import { ExperimentService } from '../../experiments/experiment.service';
import { TableService } from '../../tables/table.service';
import { ModelService } from '../../train/model.service';
import { CrossFilterBus } from '../cross-filter-bus';
import { VisualizeDataService } from '../visualize-data.service';

import { DashboardCharts } from './chart.interfaces';
import { D3Chart, IPreparedValue } from './d3-chart.component';

@Component({
  selector: 'pie-chart',
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
export class PieChartComponent extends D3Chart<DashboardCharts.IPieChartOptions> {
  @HostBinding('class') classes = 'row-flex';
  @HostBinding('style.height') styleHeight = '100%';
  readonly defaultOptions: DashboardCharts.IPieChartOptions = DashboardCharts.defaultChartOptions;

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
    this.chartArea.selectAll('.arc').remove(); //@TODO ANIMATE

    if (!this.width || !this.height || !this.svgHeight || !this.svgWidth) {
      return;
    }

    this.prepareSeries(data);

    const width = this.margin.left + this.width / 2;
    const height = this.margin.top + this.height / 2;

    this.chartColorScale = d3.scaleOrdinal<number, string>()
      .domain(d3.range(this.rowsCount))
      .range(d3.schemeCategory20);

    this.chartArea.attr('transform', 'translate(' + width + ',' + height + ')');

    const radius = Math.min(this.width, this.height) / 2;
    const pie = d3.pie<IPreparedValue>()
      .sort(null)
      .value(d => {
        return d.value;
      });

    const path = d3.arc<PieArcDatum<IPreparedValue>>()
      .outerRadius(radius - 10)
      .innerRadius(0);

    this.drawLegend();

    if (!this.preparedSeries.length) {
      return;
    }

    const arc = this.chartArea.selectAll('.arc')
      .data(pie(this.preparedSeries[0].values))
      .enter()
      .append('g')
      .attr('class', 'arc');

    arc.append('path')
      .attr('d', path)
      .attr('fill', (d, i) => {
        return this.chartColorScale(i);
      });

    let tooltipValues = [];
    this.preparedSeries.forEach((series) => {
      for (let j = 0; j < this.rowsCount; j++) {
        tooltipValues.push({ label: series.values[j].label + ' ' + series.name, value: series.values[j].value });
      }
    });
    //bindings
    this.chartArea.selectAll('g.arc')
      .on('mouseover', (_, index) => {
        d3.select('.toolTip').remove();
        const divTooltip = d3.select('body').append('div').attr('class', 'toolTip');
        divTooltip.style('left', d3.event.pageX + 10 + 'px');
        divTooltip.style('top', d3.event.pageY - 25 + 'px');
        divTooltip.style('display', 'inline-block');
        if (tooltipValues[index] && 'label' in tooltipValues[index] && 'value' in tooltipValues[index]) {
          divTooltip.html(`${tooltipValues[index].label} : ${tooltipValues[index].value}`);
        }
      })
      .on('mouseout', () => {
        d3.select('.toolTip').remove();
      });

    this.previousSeries = this.preparedSeries;
  }

  drawLegend() {
    this.svg.selectAll('.legend').remove();
    if (!this.preparedSeries.length || !this._response.columns.length) {
      return;
    }

    const seriesToDraw = this.preparedSeries[0];
    const legend = this.svg.selectAll('.legend')
      .data(seriesToDraw.values.map(data => {
        return data.label + ` ${seriesToDraw.metric.aggregator}(${seriesToDraw.column.displayName || seriesToDraw.column.name})`;
      }))
      .enter().append('g')
      .attr('class', 'legend')
      .attr('transform', (d, i) => {
        return `translate(${this.width + this.margin.left + this.potentialLegendWidth}, ${i * 20})`;
      });

    legend.append('rect')
      .attr('x', -18)
      .attr('y', 9)
      .attr('width', 18)
      .attr('height', 18)
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
      });
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

}
