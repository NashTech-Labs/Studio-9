import { DecimalPipe } from '@angular/common';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  HostBinding,
  Input,
  OnChanges,
  SimpleChanges,
  ViewChild,
} from '@angular/core';

import * as d3 from 'd3';
import * as _ from 'lodash';

import { ChartData } from './chart-data.interface';
import { Chart } from './chart.interface';
import { D3ChartHelper } from './d3-chart.helper';

const INTERVALS = {
  minute: 60,
  hour: 60 * 60,
  day: 24 * 60 * 60,
};

//const barWidth = 10;

export interface GaugeChartOptions {
  seriesGamma?: D3ChartHelper.CategoricalGamma;
  interval: number; //ms
  startAngle: number;
  endAngle: number;
  innerRadius: number;
  fontSize: string;
  numSections: number;
  ticksOffset: number;
  barWidth: number;
  paddingAngle: number;
}

@Component({
  selector: 'chart-gauge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg #canvas width='960' height='500'></svg>
  `,
})
export class GaugeChartComponent implements Chart<GaugeChartOptions>, OnChanges, AfterViewInit {
  @Input() data: ChartData;
  @Input() progress: number;
  @Input() globalProgress: number;
  @Input() options: GaugeChartOptions = GaugeChartComponent.defaultOptions;
  @Input() estimate: string;
  @Input() iteration: number;
  @HostBinding('style.height') styleHeight = '100%';
  @HostBinding('style.width') styleWidth = '100%';
  @ViewChild('canvas') private _svgElement: ElementRef;

  private outerRadius: number;
  private _decimalPipe: DecimalPipe = new DecimalPipe('en-US');

  constructor() {
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ('options' in changes) {
      this.options = _.defaults(this.options, GaugeChartComponent.defaultOptions);
    }
    if (changes['progress']) {
      this.drawChart(changes['progress'].previousValue);
    } else if (this._svgElement) {
      this.drawChart();
    }
  }

  ngAfterViewInit(): void {
    this.drawChart();
  }

  drawChart(previousProgress?: any) {
    const svg = new D3ChartHelper(this._svgElement.nativeElement);
    svg.margin = {
      left: 10,
      right: 10,
      bottom: 60,
      top: 10,
    };
    const oldG = svg.chartArea.selectAll('g.progress-group').data([true]);

    oldG.exit().remove();

    const g = oldG
      .enter()
      .append('g')
      .attr('class', 'progress-group')
      .merge(oldG)
      .attr('transform', 'translate(' + (svg.margin.left + svg.chartSize.width / 2) + ',' + (20 + svg.margin.top + svg.chartSize.height / 2) + ')');

    this.outerRadius = Math.min(svg.chartSize.width / 2, svg.chartSize.height / 2) / 2;

    const progressArc = d3.arc()
      .innerRadius(this.outerRadius - this.options.innerRadius)
      .outerRadius(this.outerRadius)
      .startAngle(this.degToRad(this.options.startAngle));

    // Add the background arc, from 0 to 100% (tau).
    if (!g.select('path.background').size()) {
      g.append('path')
        .attr('class', 'background')
        .datum({ endAngle: this.degToRad(this.options.endAngle) })
        .attr('d', progressArc);
    }

    // Add the foreground arc
    const oldForeground = g.select('path.foreground');
    const foreground = oldForeground.size()
      ? oldForeground.datum({ endAngle: this.degToRad(this.percToDeg(previousProgress || 0)) })
      : g.append('path')
        .attr('class', 'foreground')
        .datum({
          endAngle: this.degToRad(this.percToDeg(this.progress)),
        })
        .attr('d', progressArc);

    // Animate Progress
    foreground.transition()
      .duration(this.options.interval)
      .attrTween('d', this.arcTween(this.degToRad(this.percToDeg(this.progress)), progressArc));

    // Progress
    const oldProgressText = g.selectAll('text.progress-text').data([true]);

    oldProgressText.exit().remove();

    oldProgressText
      .enter()
      .append('text')
      .attr('dy', this.options.fontSize)
      .attr('class', 'progress-text')
      .attr('font-size', '1em')
      .attr('text-anchor', 'middle')
      .merge(oldProgressText)
      .text(`${this.formatFloat(this.progress * 100)}%`);

    // Global Progress
    const oldGlobalProgressText = g.selectAll('text.global-progress-text').data([true]);

    oldGlobalProgressText.exit().remove();

    oldGlobalProgressText
      .enter()
      .append('text')
      .attr('class', 'global-progress-text')
      .attr('font-size', this.options.fontSize)
      .attr('text-anchor', 'middle')
      .merge(oldGlobalProgressText)
      .text(`${this.formatFloat(this.globalProgress * 100)}%`);

    // Draw the tick marks
    const oldTicks = g.selectAll('path.arc-tick').data(d3.range(this.options.numSections));

    oldTicks.exit().remove();

    oldTicks
      .enter()
      .append('path')
      .attr('class', 'arc-tick')
      .merge(oldTicks)
      .classed('active', (_, tickIdx) => (tickIdx / this.options.numSections) <= this.globalProgress)
      .attr('d', (_, tickIdx) => this.tickArc(tickIdx)(tickIdx))
      .each(function (_, i) {
        let firstArcSection = /(^.+?)L/;

        let newArc = firstArcSection.exec(d3.select(this).attr('d'))[1];
        newArc = newArc.replace(/,/g, ' ');

        g.append('path')
          .attr('class', 'hiddenDonutArcs')
          .attr('id', 'donutArc' + i)
          .attr('d', newArc)
          .style('fill', 'none');
      });

    //Append the label names on the outside
    const textPath = g.selectAll('.donutText')
      .data(d3.range(this.options.numSections));

    textPath
      .enter()
      .append('text')
      .attr('class', 'donutText')
      .classed('active', (_, tickIdx) => (tickIdx / this.options.numSections) <= this.globalProgress)
      .attr('dy', -13)
      .append('textPath')
      .attr('startOffset', '50%')
      .style('text-anchor', 'middle')
      .attr('xlink:href', (_, i) => {
        return '#donutArc' + i;
      })
      .text((_, i) => {
        return `${this.formatFloat((i / this.options.numSections) * 100)}`;
      });
    // label
    const oldTextLabel = svg.chartArea.selectAll('text.text-label').data([true]);
    oldTextLabel.enter()
      .append('text')
      .attr('class', 'text-label')
      .attr('text-anchor', 'start')
      .attr('x', 20)
      .attr('y', 15)
      .text(`Training`);

    const oldLineDelimiter = svg.chartArea.selectAll('line.delimiter').data([true]);
    oldLineDelimiter.enter()
      .append('line')
      .attr('class', 'delimiter')
      .attr('x1', 0)
      .attr('y1', 0)
      .attr('x2', svg.chartSize.width)
      .attr('y2', 0);
    const pathGroup = svg.chartArea.selectAll('g.path-group').data([true]);
    pathGroup.enter()
      .append('g')
      .attr('class', 'path-group')
      .attr('transform', `translate(${svg.chartSize.width - 30}, -10)`);
    const oldPathDelimiter = pathGroup.selectAll('path.delimiter').data([true]);
    oldPathDelimiter.enter()
      .append('path')
      .attr('class', 'delimiter')
      .attr('d', 'M 0 10 L 30 10 L 25 15 L 5 15 Z');
    // estimate
    const len = this.outerRadius + this.options.ticksOffset;
    const oldEstimateLabel = g.selectAll('text.estimate-label').data([true]);
    oldEstimateLabel.enter()
      .append('text')
      .attr('class', 'estimate-label')
      .attr('text-anchor', 'end')
      .attr('dx', -1 * len)
      .attr('dy', len)
      .text(`Estimate`);

    const oldEstimate = g.selectAll('text.estimate').data([true]);

    const offset = Math.floor((Date.now() - Date.parse(this.estimate)) / 1000);
    const hours = ('0' + Math.floor((offset % INTERVALS.day) / INTERVALS.hour)).slice(-2),
      minutes = ('0' + Math.floor((offset % INTERVALS.hour) / INTERVALS.minute)).slice(-2),
      seconds = ('0' + offset % INTERVALS.minute).slice(-2);
    const label = `${hours}:${minutes}:${seconds}`;

    oldEstimate.enter()
      .append('text')
      .attr('class', 'estimate')
      .attr('text-anchor', 'end')
      .attr('dx', -1 * len)
      .attr('dy', len + 20)
      .merge(oldEstimate)
      .text(label);
    // Iterations
    const oldIterationLabel = g.selectAll('text.iteration-label').data([true]);
    oldIterationLabel.enter()
      .append('text')
      .attr('class', 'iteration-label')
      .attr('text-anchor', 'start')
      .attr('dx', len)
      .attr('dy', len)
      .text(`Iteration`);

    const oldIteration = g.selectAll('text.iteration').data([true]);
    oldIteration.enter()
      .append('text')
      .attr('class', 'iteration')
      .attr('text-anchor', 'start')
      .attr('dx', len)
      .attr('dy', len + 20)
      .merge(oldIteration)
      .text(this.iteration || 0);
    // Left Delimiter
    const oldLeftDelimiter = g.selectAll('path.left-delimiter').data([true]);
    oldLeftDelimiter.enter()
      .append('path')
      .attr('class', 'left-delimiter')
      .attr('d', `M ${-len} ${len} L ${-this.options.barWidth} ${this.options.barWidth} Z`);
    // Right Delimiter
    const oldRightDelimiter = g.selectAll('path.right-delimiter').data([true]);
    oldRightDelimiter.enter()
      .append('path')
      .attr('class', 'right-delimiter')
      .attr('d', `M ${len} ${len} L ${this.options.barWidth} ${this.options.barWidth} Z`);
  }

  percToDeg(perc) {
    return this.options.startAngle + perc * Math.abs(this.options.endAngle - this.options.startAngle);
  }

  degToRad(deg) {
    return deg * Math.PI / 180;
  }

  tickArc(tickIdx): d3.Arc<any, any> {
    const radius = this.outerRadius + this.options.ticksOffset;
    const step = Math.abs(this.options.endAngle - this.options.startAngle) / this.options.numSections;

    return d3.arc()
      .outerRadius(radius)
      .innerRadius(radius - this.options.barWidth)
      .startAngle(this.degToRad(this.options.startAngle + (step) * tickIdx + this.options.paddingAngle))
      .endAngle(this.degToRad(this.options.startAngle + (step) * (tickIdx + 1) - this.options.paddingAngle));
  }

  // Returns a tween for a transition’s "d" attribute, transitioning any selected
  // arcs from their current angle to the specified new angle.
  arcTween(newAngle, arc) {
    return function (d) {
      const interpolate = d3.interpolate(d.endAngle, newAngle);
      return function (t) {
        d.endAngle = interpolate(t);
        return arc(d);
      };
    };
  }

  formatFloat(value: number): string {
    return this._decimalPipe.transform(value, '1.0-2');
  }
}

export namespace GaugeChartComponent {
  export const defaultOptions: GaugeChartOptions = {
    seriesGamma: D3ChartHelper.CategoricalGamma.category10,
    interval: 350,
    startAngle: -90,
    innerRadius: 5,
    ticksOffset: 40,
    fontSize: '1.5em',
    numSections: 10,
    endAngle: 90,
    barWidth: 10,
    paddingAngle: 4,
  };
  export type Selection = [[number, number]];
}

