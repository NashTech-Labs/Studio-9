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

export enum BoxMetric {
  MIN = 'MIN',
  LOWER_QUARTILE = 'LOWER_QUARTILE',
  MEDIAN = 'MEDIAN',
  UPPER_QUARTILE = 'UPPER_QUARTILE',
  MAX = 'MAX',
}

export interface BoxChartOptions {
  metricDomain?: [number, number];
  labelX?: string;
  labelY?: string;
  ticksCountY?: number;
  metricsMapping: {
    [K in BoxMetric]: number; // assigns metric index for each of BoxMetric
  };
  groupByAttributes?: number[];
  boxWidth?: number;
  integerDomain?: boolean;
  // these two can be defined to provide custom coloring to chart
  colorGamma?: D3ChartHelper.CategoricalGamma | string[];
  colorClassifier?: D3ChartHelper.AttributesClassifier;
}

@Component({
  selector: 'box-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg #canvas width='960' height='500'></svg>
  `,
})
export class BoxChartComponent implements Chart<BoxChartOptions>, OnChanges, AfterViewInit {
  @Input() data: ChartData;
  @Input() options: BoxChartOptions;
  @Output() selection: EventEmitter<BoxChartComponent.Selection> = new EventEmitter<BoxChartComponent.Selection>();
  @HostBinding('style.height') styleHeight = '100%';
  @HostBinding('style.width') styleWidth = '100%';
  @ViewChild('canvas')
  private _svgElement: ElementRef;
  private _decimalPipe: DecimalPipe = new DecimalPipe('en-US');

  constructor() {
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ('options' in changes) {
      this.options = _.defaults(this.options, BoxChartComponent.defaultOptions);
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

    const seriesIndex = d3.range(this.data.series.length);

    const groupByIndices = this.options.groupByAttributes || d3.range(this.data.attributes.length);

    const colorIndices = _.range(this.data.attributes.length).filter(_ => {
      return groupByIndices.indexOf(_) < 0;
    });

    const groupClassifier = D3ChartHelper.prepareAttributesClassifier(this.data, ', ', groupByIndices);

    const colorClassifier = this.options.colorClassifier ||
      D3ChartHelper.prepareAttributesClassifier(this.data, ', ', colorIndices);

    // Color Scale
    const colorScale = D3ChartHelper.prepareCategoricalGammaScale(
      colorClassifier.range,
      D3ChartHelper.getCategoricalGamma(this.options.colorGamma),
    );

    // Legend
    if (colorClassifier.range.length > 1) {
      svg.pushLegend('class', D3ChartHelper.legendColor()
        .title(colorIndices.map(_ => this.data.attributes[_].name).join(', '))
        .titleWidth(120)
        .labels(colorClassifier.labels)
        .scale(colorScale));
    }

    // ranges
    // 1. Metric Range
    const metricRange = svg.yRange;
    // 2. AttributeRange
    const attributeRange: [number, number] = svg.xRange;

    // SERIES
    const oldSeriesGroup = svg.chartArea
      .selectAll('g.series')
      .data(seriesIndex);

    oldSeriesGroup.exit().remove();

    const seriesGroup = oldSeriesGroup.enter()
      .append('g')
      .classed('series', true)
      .merge(oldSeriesGroup);

    // Default Scales
    // 2. Metric Scale
    const metricScaleDraft = D3ChartHelper.prepareCombinedMetricsScale(
      this.data,
      metricRange,
      true,
      this.data.metrics.length,
      this.options.metricDomain,
    );

    // 2. Attribute Scale
    const axes = [];
    const attributeScaleDraft: any = d3.scaleBand<number>()
      .domain(groupClassifier.range)
      .rangeRound(attributeRange)
      .paddingInner(0.5)
      .paddingOuter(0.25);

    // AXES
    // 1. Attribute Axis
    const labels = groupClassifier.labels;

    axes.push({
      location: D3ChartHelper.AssetLocation.bottom,
      label: ('labelX' in this.options)
        ? this.options.labelX
        : this.data.attributes.map(_ => _.name).join('/'),
      scale: attributeScaleDraft,
      options: {
        tickFormat: i => labels[i],
        angle: -90,
      },
    });
    // 2. Metric axis
    const [start, stop] = metricScaleDraft.domain();
    const metricStep = this.options.ticksCountY ? (stop - start) / this.options.ticksCountY : null;
    axes.push({
      location: D3ChartHelper.AssetLocation.left,
      label: ('labelY' in this.options) ? this.options.labelY : this.data.metrics.map(_ => _.name).join('/'),
      scale: metricScaleDraft,
      options: this.options.integerDomain
        ? D3ChartHelper.integerAxisOptions(metricScaleDraft, this.options.ticksCountY)
        : (
          metricStep
            ? { ticks: [...d3.range(start, stop, metricStep), stop].map(_ => this._formatFloat(_)) }
            : {}
        ),
    });

    // Init Both Axes
    const [, [attributeScale, metricScale]] = svg.setAxes(...axes);

    const bandWidth = attributeScale.bandwidth();

    const [minIndex, maxIndex, quartileMinIndex, quartileMaxIndex, medianIndex] = [
      this.options.metricsMapping[BoxMetric.MIN],
      this.options.metricsMapping[BoxMetric.MAX],
      this.options.metricsMapping[BoxMetric.LOWER_QUARTILE],
      this.options.metricsMapping[BoxMetric.UPPER_QUARTILE],
      this.options.metricsMapping[BoxMetric.MEDIAN],
    ];

    // SERIES
    const oldGroups = seriesGroup
      .selectAll('g.box-group')
      .data(i => this.data.series[i].data.map((_, j) => [i, j]));

    oldGroups.exit().remove();

    const groups = oldGroups.enter()
      .append('g')
      .attr('class', 'box-group')
      .merge(oldGroups)
      .on('mouseover', ([i, j]) => {
        d3.select('.toolTip').remove();
        const divTooltip = d3.select('body').append('div').attr('class', 'toolTip');
        divTooltip.style('left', d3.event.pageX - 120 + 'px');
        divTooltip.style('top', d3.event.pageY - 200 + 'px');
        divTooltip.style('display', 'inline-block');
        let html = `<table class="table" style="margin-bottom: 0;">
          <label>${labels[groupClassifier(this.data.series[i].data[j].attributes)]}</label>
          <tr><td>Max</td><td>${this._formatFloat(this.data.series[i].data[j].values[maxIndex])}</td></tr>
          <tr><td>75%</td><td>${this._formatFloat(this.data.series[i].data[j].values[quartileMaxIndex])}</td></tr>
          <tr><td>Median</td><td>${this._formatFloat(this.data.series[i].data[j].values[medianIndex])}</td></tr>
          <tr><td>25%</td><td>${this._formatFloat(this.data.series[i].data[j].values[quartileMinIndex])}</td></tr>
          <tr><td>Min</td><td>${this._formatFloat(this.data.series[i].data[j].values[minIndex])}</td></tr>
        </table>`;
        divTooltip.html(html);
      })
      .on('mouseout', () => {
        d3.select('.toolTip').remove();
      });

    // Draw the box plot vertical lines
    const oldVerticalLines = groups.selectAll('line.center').data(d => [d]);
    oldVerticalLines.exit().remove();
    oldVerticalLines.enter()
      .append('line')
      .attr('class', 'center')
      .merge(oldVerticalLines)
      .attr('x1', ([i, j]) => {
          return attributeScale(groupClassifier(this.data.series[i].data[j].attributes)) + bandWidth / 2;
        },
      )
      .attr('y1', ([i, j]) => {
          return metricScale(this.data.series[i].data[j].values[minIndex]);
        },
      )
      .attr('x2', ([i, j]) => {
          return attributeScale(groupClassifier(this.data.series[i].data[j].attributes)) + bandWidth / 2;
        },
      )
      .attr('y2', ([i, j]) => {
          return metricScale(this.data.series[i].data[j].values[maxIndex]);
        },
      )
      .attr('stroke', '#000')
      .attr('stroke-width', 1)
      .attr('fill', 'none');

    // Draw the boxes of the box plot, filled in white and on top of vertical lines
    const oldRects = groups.selectAll('rect.box').data(d => [d]);
    oldRects.exit().remove();
    oldRects
      .enter()
      .append('rect')
      .attr('class', 'box')
      .merge(oldRects)
      .attr('width', bandWidth)
      .attr('height', ([i, j]) => {
        return metricScale(this.data.series[i].data[j].values[quartileMinIndex]) - metricScale(this.data.series[i].data[j].values[quartileMaxIndex]);
      })
      .attr('x', ([i, j]) => {
        return attributeScale(groupClassifier(this.data.series[i].data[j].attributes));
      })
      .attr('y', ([i, j]) => {
        return metricScale(this.data.series[i].data[j].values[quartileMaxIndex]);
      }).attr('fill', ([i, j]) => {
        return colorScale(colorClassifier(this.data.series[i].data[j].attributes));
      })
      .attr('stroke', '#000')
      .attr('stroke-width', 1);

    // Now render all the horizontal lines at once - the whiskers and the median
    // Top whisker
    const oldTopWhiskers = groups.selectAll('line.topWhisker').data(d => [d]);

    oldTopWhiskers.exit().remove();

    oldTopWhiskers.enter()
      .append('line')
      .attr('class', 'topWhisker')
      .merge(oldTopWhiskers)
      .attr('x1', ([i, j]) => {
        return attributeScale(groupClassifier(this.data.series[i].data[j].attributes));
      })
      .attr('y1', ([i, j]) => {
        return metricScale(this.data.series[i].data[j].values[minIndex]);
      })
      .attr('x2', ([i, j]) => {
        return attributeScale(groupClassifier(this.data.series[i].data[j].attributes)) + bandWidth;
      })
      .attr('y2', ([i, j]) => {
        return metricScale(this.data.series[i].data[j].values[minIndex]);
      })
      .attr('stroke', '#000')
      .attr('stroke-width', 1)
      .attr('fill', 'none');

    // Median line
    const oldMedians = groups.selectAll('line.median').data(d => [d]);

    oldMedians.exit().remove();

    oldMedians.enter()
      .append('line')
      .attr('class', 'median')
      .merge(oldMedians)
      .attr('x1', ([i, j]) => {
        return attributeScale(groupClassifier(this.data.series[i].data[j].attributes));
      })
      .attr('y1', ([i, j]) => {
        return metricScale(this.data.series[i].data[j].values[medianIndex]);
      })
      .attr('x2', ([i, j]) => {
        return attributeScale(groupClassifier(this.data.series[i].data[j].attributes)) + bandWidth;
      })
      .attr('y2', ([i, j]) => {
        return metricScale(this.data.series[i].data[j].values[medianIndex]);
      })
      .attr('stroke', '#000')
      .attr('stroke-width', 1.5)
      .attr('fill', 'none');

    // Bottom whisker
    const oldBottomWhiskers = groups.selectAll('line.bottomWhisker').data(d => [d]);

    oldBottomWhiskers.exit().remove();

    oldMedians.enter()
      .append('line')
      .attr('class', 'bottomWhisker')
      .merge(oldBottomWhiskers)
      .attr('x1', ([i, j]) => {
        return attributeScale(groupClassifier(this.data.series[i].data[j].attributes));
      })
      .attr('y1', ([i, j]) => {
        return metricScale(this.data.series[i].data[j].values[maxIndex]);
      })
      .attr('x2', ([i, j]) => {
        return attributeScale(groupClassifier(this.data.series[i].data[j].attributes)) + bandWidth;
      })
      .attr('y2', ([i, j]) => {
        return metricScale(this.data.series[i].data[j].values[maxIndex]);
      })
      .attr('stroke', '#000')
      .attr('stroke-width', 1)
      .attr('fill', 'none');

    //TITLE
    svg.appendTitle(this.data.series[0].title);
  }


  private _formatFloat(value: number): string {
    return this._decimalPipe.transform(value, '1.0-3');
  }
}

export namespace BoxChartComponent {
  export const defaultOptions: Partial<BoxChartOptions> = {
    colorGamma: D3ChartHelper.CategoricalGamma.category10,
    ticksCountY: 5,
    boxWidth: 30,
    integerDomain: false,
  };
  export type Selection = [[number, number]];
}

