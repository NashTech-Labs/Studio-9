import { DecimalPipe } from '@angular/common';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  HostBinding,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';

import * as d3 from 'd3';
import * as _ from 'lodash';

import { ChartData } from './chart-data.interface';
import { Chart } from './chart.interface';
import { D3ChartHelper } from './d3-chart.helper';

export enum BarChartType {
  STACKED = 'STACKED',
  GROUPED = 'GROUPED',
}

export enum ChartOrientation {
  VERTICAL = 'VERTICAL',
  HORIZONTAL = 'HORIZONTAL',
}

export interface BarChartOptions {
  type?: BarChartType;
  orientation?: ChartOrientation;
  linearAttributeDomain?: [number, number];
  stacked?: boolean;
  integerDomain?: boolean;
  seriesGamma?: D3ChartHelper.CategoricalGamma;
  labelX?: string;
  labelY?: string;
  ticksCountY?: number;
  ticksCountX?: number;
  attributeAxis?: boolean;
  metricAxis?: boolean;
  attirbuteLabelAngle?: number;
  metricLabelAngle?: number;
  margin?: D3ChartHelper.Margin;
}

@Component({
  selector: 'chart-bar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg #canvas width='960' height='500'></svg>
  `,
})
export class BarChartComponent implements Chart<BarChartOptions>, OnChanges, AfterViewInit {
  @Input() data: ChartData;
  @Input() options: BarChartOptions = BarChartComponent.defaultOptions;
  @Output() selection: EventEmitter<BarChartComponent.Selection> = new EventEmitter<BarChartComponent.Selection>();
  @HostBinding('style.height') styleHeight = '100%';
  @HostBinding('style.width') styleWidth = '100%';
  @ViewChild('canvas') private _svgElement: ElementRef;
  private _decimalPipe: DecimalPipe = new DecimalPipe('en-US');

  constructor() {
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ('options' in changes) {
      this.options = _.defaults(this.options, BarChartComponent.defaultOptions);
    }
    if (this._svgElement) {
      this.drawChart();
    }
  }

  ngAfterViewInit(): void {
    this.drawChart();
  }

  drawChart() {
    const svg = new D3ChartHelper(this._svgElement.nativeElement);
    if (this.options.margin) {
      svg.margin = this.options.margin;
    }

    const seriesIndex = d3.range(this.data.series.length);
    const metricIndex = d3.range(this.data.metrics.length);

    const colorScale = D3ChartHelper.prepareCategoricalGammaScale(
      metricIndex,
      this.options.seriesGamma,
    );

    const classifier = D3ChartHelper.prepareAttributesClassifier(this.data);

    // Legend
    if (this.data.metrics.length > 1) {
      svg.pushLegend('metrics', D3ChartHelper.legendColor()
        .labels(this.data.metrics.map(_ => _.name))
        .scale(colorScale));
    }

    // ranges
    const metricRange = this.options.orientation === ChartOrientation.VERTICAL
      ? svg.yRange
      : svg.xRange;

    const attributeRange: [number, number] = this.options.orientation === ChartOrientation.VERTICAL
      ? svg.xRange
      : <[number, number]> svg.yRange.reverse();

    let metricScale = this.options.stacked
      ? D3ChartHelper.prepareStackedMetricsScale(
        this.data,
        metricRange,
        colorScale.range().length,
      )
      : D3ChartHelper.prepareCombinedMetricsScale(
        this.data,
        metricRange,
        true,
        colorScale.range().length,
      );

    // SERIES
    const oldSeriesGroup = svg.chartArea
      .selectAll('g.series')
      .data(seriesIndex);

    oldSeriesGroup.exit().remove();

    const seriesGroup = oldSeriesGroup.enter()
      .append('g')
      .classed('series', true)
      .merge(oldSeriesGroup);

    //Attribute axis
    let axes = [];
    let attributeScale: any = d3.scaleBand<number>() //@TODO DEFAULT ScaleBand but can be ScalePoint
      .domain(classifier.range)
      .range(attributeRange) // TODO: introduce an option
      .paddingInner(0.05)
      .paddingOuter(0.025);

    let ticks = [],
      step,
      start,
      stop;

    if (this.options.linearAttributeDomain) {
      [start, stop] = this.options.linearAttributeDomain;
      step = this.options.ticksCountX ? (stop - start) / this.options.ticksCountX : null;
      ticks = step
        ? [...d3.range(start, stop, step), stop].map(_ => this._formatFloat(_))
        : [...d3.range(start, stop, (stop - start) / classifier.range.length), stop].map(_ => this._formatFloat(_));

      attributeScale = d3.scalePoint<number>()
        .domain(d3.range(ticks.length))
        .rangeRound(attributeRange);
      if (this.options.attributeAxis) {
        axes.push({
          location: this.options.orientation === ChartOrientation.VERTICAL
            ? D3ChartHelper.AssetLocation.bottom
            : D3ChartHelper.AssetLocation.left,
          label: ('labelX' in this.options)
            ? this.options.labelX
            : this.data.attributes.map(_ => _.name).join('/'),
          scale: attributeScale,
          options: {
            tickFormat: i => ticks[i],
          },
        });
      }
    } else {
      const step = this.options.ticksCountX ? Math.ceil(classifier.labels.length / this.options.ticksCountX) : null;
      const labels = step
        ? classifier.labels.reduce((acc, label, index) => {
          if (index % step === 0) {
            acc.push(label);
          } else {
            acc.push(null);
          }
          return acc;
        }, [])
        : classifier.labels;
      if (this.options.attributeAxis) {
        axes.push({
          location: this.options.orientation === ChartOrientation.VERTICAL
            ? D3ChartHelper.AssetLocation.bottom
            : D3ChartHelper.AssetLocation.left,
          label: ('labelX' in this.options)
            ? this.options.labelX
            : this.data.attributes.map(_ => _.name).join('/'),
          scale: attributeScale,
          options: {
            tickFormat: i => labels[i],
            label: this.options.attirbuteLabelAngle,
          },
        });
      }
    }

    if (this.options.metricAxis) {
      //Metric axis
      axes.push({
        location: this.options.orientation === ChartOrientation.VERTICAL
          ? D3ChartHelper.AssetLocation.left
          : D3ChartHelper.AssetLocation.bottom,
        label: ('labelY' in this.options) ? this.options.labelY : this.data.metrics.map(_ => _.name).join('/'),
        scale: metricScale,
        options: this.options.integerDomain
          ? D3ChartHelper.integerAxisOptions(metricScale, this.options.ticksCountY)
          : {
            label: this.options.metricLabelAngle,
          },
      });
    }
    // Set Axes
    const [, newScales, , elements] = svg.setAxes(...axes);
    const attributeAxisEl: d3.Selection<SVGGElement, any, any, any> = elements[0];

    if (this.options.attributeAxis) {
      attributeScale = newScales[0];
      if (this.options.metricAxis) {
        metricScale = <any> newScales[1];
      }
    } else if (this.options.metricAxis) {
      metricScale = <any> newScales[0];
    }

    const bandWidth = this.options.linearAttributeDomain
      ? attributeScale.step()
      : attributeScale.bandwidth();

    const rectAttributeScale: d3.ScaleBand<number> = d3.scaleBand<number>()
        .domain(metricIndex)
        .rangeRound([0, bandWidth])
        .paddingInner(0.2);

    if (this.options.orientation === ChartOrientation.VERTICAL && attributeAxisEl) {
      attributeAxisEl
        .selectAll<SVGTextElement, number>('text')
        .each(D3ChartHelper.textTrimmer(bandWidth));
    }
    // BARS
    const oldBars = seriesGroup.selectAll<SVGGElement, [number, number, string]>('g.bar')
      .data(
        i => this.data.series[i].data.map((_, j): [number, number, string] => {
          return [i, j, _.referenceId || String(classifier(_.attributes))];
        }),
        ([, , referenceId]) => referenceId,
      );

    oldBars.exit().remove();

    const bars = oldBars.enter()
      .append('g')
      .classed('bar', true)
      .merge(oldBars)
      .attr('transform', ([i, j]) => {
        if (!this.options.linearAttributeDomain) {
          if (this.options.orientation === ChartOrientation.VERTICAL) {
            return `translate(${attributeScale(classifier(this.data.series[i].data[j].attributes))}, 0)`;
          } else if (this.options.orientation === ChartOrientation.HORIZONTAL) {
            return `translate(0, ${attributeScale(classifier(this.data.series[i].data[j].attributes))})`;
          }
        } else {
          const k = Math.ceil((Number(this.data.series[i].data[j].attributes[0]) - start) / step);
          let tick = 0;
          if (this.options.orientation === ChartOrientation.VERTICAL) {
            tick = step ?
              (k > 0) ? (k - 1) : 0
              : 1;
            return `translate(${attributeScale(tick) ||  attributeScale.range()[0]}, 0)`;
          } else if (this.options.orientation === ChartOrientation.HORIZONTAL) {
            tick = (k === 0) ? 1 : k;
            return `translate(0, ${attributeScale(tick) || 0})`;
          }
        }
      });

    // RECTS
    const oldRects = bars.selectAll('rect')
      .data(([i, j]) => this.data.metrics.map((_, k): [number, number, number] => [i, j, k]));

    oldRects.exit().remove();

    const rects = oldRects.enter()
      .append('rect')
      .each(this._initRectPosition(rectAttributeScale, metricScale))
      .merge(oldRects);

    rects
      .attr('fill', ([i, j, k]) => colorScale(k))
      .each(this._setRectTransition(rectAttributeScale, metricScale));

    //TITLE
    svg.appendTitle(this.data.series[0].title);
  }

  private _setRectTransition(
    rectAttributeScale: d3.ScaleBand<number>,
    metricScale: d3.ScaleContinuousNumeric<number, number>,
  ) {
    // *trollface*
    const [yAttr, xAttr, heightAttr, widthAttr] = this.options.orientation === ChartOrientation.VERTICAL
      ? ['y', 'x', 'height', 'width']
      : ['x', 'y', 'width', 'height'];

    const data = this.data;
    const options = this.options;

    const stacks: [number, number][] = [];

    const y0 = metricScale(0);

    const rectWidth = options.stacked
      ? rectAttributeScale.range()[1] - rectAttributeScale.range()[0]
      : rectAttributeScale.bandwidth();

    return function(
      this: SVGRectElement,
      datum: [number, number, number],
    ) {
      const [i, j, k] = datum;

      const value = data.series[i].data[j].values[k];

      const y1 = metricScale(value);

      let y = Math.min(y0, y1),
        height = Math.abs(y1 - y0);

      if (options.stacked) {
        const stack = stacks[j] || (stacks[j] = [0, 0]);
        const stackIndex = +(value >= 0);
        const stackShift = stack[stackIndex];
        y += stackShift;
        stack[stackIndex] = stackShift + y1 - y0;
      }

      // *trollface*
      d3.select(this)
        .transition()
        .duration(500)
        .delay((k + 1) * 10)
        .attr(xAttr, rectAttributeScale(options.stacked ? 0 : k))
        .attr(yAttr, y)
        .attr(widthAttr, rectWidth)
        .attr(heightAttr, height);
    };
  }

  private _initRectPosition(
    rectAttributeScale: d3.ScaleBand<number>,
    metricScale: d3.ScaleContinuousNumeric<number, number>,
  ) {
    // *trollface*
    const [yAttr, xAttr, heightAttr, widthAttr] = this.options.orientation === ChartOrientation.VERTICAL
      ? ['y', 'x', 'height', 'width']
      : ['x', 'y', 'width', 'height'];

    const options = this.options;

    const rectWidth = options.stacked
      ? rectAttributeScale.range()[1] - rectAttributeScale.range()[0]
      : rectAttributeScale.bandwidth();

    return function(
      this: SVGRectElement,
      datum: [number, number, number],
    ) {
      const [, , k] = datum;

      d3.select(this)
        .attr(xAttr, rectAttributeScale(options.stacked ? 0 : k))
        .attr(yAttr, metricScale(0))
        .attr(widthAttr, rectWidth)
        .attr(heightAttr, 0);
    };
  }

  private _formatFloat(value: number): string {
    return this._decimalPipe.transform(value, '1.0-2');
  }
}

export namespace BarChartComponent {
  export const defaultOptions: BarChartOptions = {
    type: BarChartType.GROUPED,
    seriesGamma: D3ChartHelper.CategoricalGamma.category10,
    orientation: ChartOrientation.VERTICAL,
    attirbuteLabelAngle: 0,
    metricLabelAngle: 0,
  };
  export type Selection = [[number, number]];
}

