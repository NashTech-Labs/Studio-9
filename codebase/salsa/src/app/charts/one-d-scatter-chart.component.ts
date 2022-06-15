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

export interface OneDimScatterChartOptions {
  title?: string;
  subtitle?: string;
  circleRadius?: number;
  domain?: [number, number];
  yJitter?: boolean;
  seriesGamma: D3ChartHelper.CategoricalGamma;
  labelX?: string;
}

@Component({
  selector: 'chart-one-d-scatter',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg #canvas width='960' height='500'></svg>
  `,
  styles: [
    `.title {
      font-size: 16px;
      text-decoration: underline;
    }

    .subtitle {
      font-size: 10px;
    }`,
  ],
})
export class OneDimScatterChartComponent implements Chart<OneDimScatterChartOptions>, OnChanges, AfterViewInit {
  @Input() data: ChartData;
  @Input() options: OneDimScatterChartOptions = OneDimScatterChartComponent.defaultOptions;
  @Input() selection: OneDimScatterChartComponent.Selection;
  @Output() selectionChange: EventEmitter<OneDimScatterChartComponent.Selection> = new EventEmitter<OneDimScatterChartComponent.Selection>();
  @HostBinding('style.height') styleHeight = '100%';
  @HostBinding('style.width') styleWidth = '100%';
  @ViewChild('canvas') private _svgElement: ElementRef;

  constructor() {}

  ngOnChanges(changes: SimpleChanges): void {
    if ('options' in changes) {
      this.options = _.defaults(this.options, OneDimScatterChartComponent.defaultOptions);
    }
    if (this._svgElement) {
      this.drawChart();
    }
  }

  ngAfterViewInit(): void {
    this.drawChart();
  }

  // TODO: draw all seriaes x groups
  drawChart() {
    const svg = new D3ChartHelper(this._svgElement.nativeElement);

    const seriesRange = d3.range(this.data.series.length);

    const classifier = D3ChartHelper.prepareAttributesClassifier(this.data);

    const colorScale = D3ChartHelper.prepareCategoricalGammaScale(
      classifier.range,
      this.options.seriesGamma,
    );

    // Legend
    if (classifier.range.length > 1) {
      svg.pushLegend('class', D3ChartHelper.legendColor()
          .title(this.data.attributes[0].name)
          .titleWidth(120)
          .labels(classifier.labels)
          .scale(colorScale));
    } else {
      svg.removeLegend('class');
    }

    // Scales
    let [xScale, _radiusScale] = D3ChartHelper.prepareMetricScales(
      this.data,
      [svg.xRange, [1, this.options.circleRadius]],
      [this.options.domain],
      2,
    );

    // AXES
    [, [xScale]] = svg.setAxes(
      {
        location: D3ChartHelper.AssetLocation.bottom,
        label: this.options.labelX || this.data.metrics[0].name,
        scale: xScale,
      },
    );

    const yScale = d3.scalePoint<number>()
      .padding(0.5)
      .domain(classifier.range)
      .range(<[number, number]> svg.yRange.reverse());

    const yJitter = this.options.yJitter
      ? d3.randomNormal(0, yScale.step() / 8)
      : () => 0;


    // SERIES
    const oldSeriesGroup = svg.chartArea
      .selectAll('g.series')
      .data(seriesRange);

    oldSeriesGroup.exit().remove();

    const seriesGroup = oldSeriesGroup.enter()
      .append('g')
      .classed('series', true)
      .merge(oldSeriesGroup);

    // POINTS
    const oldPoints = seriesGroup.selectAll('circle')
      .data(i => this.data.series[i].data);

    oldPoints.exit().remove();

    const points = oldPoints.enter()
      .append('circle')
      .attr('fill', d => colorScale(classifier(d.attributes)))
      .style('fill-opacity', 0.4)
      .attr('cx', d => xScale(d.values[0]))
      .attr('cy', d => yScale(classifier(d.attributes)) + yJitter())
      .attr('r', this.options.circleRadius)
      .merge(oldPoints);

    points.transition()
      .duration(300)
      .attr('fill', d => colorScale(classifier(d.attributes)))
      .attr('cx', d => xScale(d.values[0]))
      .attr('cy', d => yScale(classifier(d.attributes)) + yJitter())
      .attr('r', this.options.circleRadius);

    points.order();

    // brush
    const brush = d3.brushX<any>();

    brush.extent([[0, 0], [svg.chartSize.width, svg.chartSize.height]]);

    const oldBrushEl = svg.chartArea.selectAll<SVGGElement, number>('g.brush')
      .data([1]);

    const brushEl = oldBrushEl.enter()
      .append<SVGGElement>('g')
      .attr('class', 'brush')
      .merge(oldBrushEl)
      .call(brush);

    const brushResizePath = (d) => {
      const e = +(d.type === 'e'),
        x = e ? 1 : -1,
        y = svg.chartSize.height / 2;
      return 'M' + (.5 * x) + ','
        + y + 'A6,6 0 0 ' + e + ' ' + (6.5 * x) + ','
        + (y + 6) + 'V' + (2 * y - 6)
        + 'A6,6 0 0 ' + e + ' ' + (.5 * x) + ','
        + (2 * y) + 'Z' + 'M' + (2.5 * x) + ','
        + (y + 8) + 'V' + (2 * y - 8) + 'M' + (4.5 * x) + ','
        + (y + 8) + 'V' + (2 * y - 8);
    };

    const oldHandles = brushEl.selectAll('.handle--custom')
      .data([{ type: 'w' }, { type: 'e' }]);

    const handles = oldHandles.enter()
      .append('path')
      .attr('display', 'none')
      .attr('class', 'handle--custom')
      .attr('stroke', '#000')
      .attr('cursor', 'ew-resize')
      .attr('d', brushResizePath)
      .merge(oldHandles);

    brush
      .on('brush', () => {
        this._updateBrushExtras(svg, handles, xScale);
      })
      .on('end', () => {
        this._updateBrushExtras(svg, handles, xScale);
        const selection = d3.event.selection;

        if (selection) {
          const selectionValues = <[number, number]> (<[number, number]> selection)
            .map(_ => xScale.invert(_));
          if (!_.isEqual(selectionValues, this.selection)) {
            this.selection = null;
          }
          this.selectionChange.emit(selectionValues);
        } else {
          this.selectionChange.emit(null);
        }
      });

    if (this.selection) {
      brush.move(brushEl.transition(),
        <[number, number]> this.selection.map(_ => xScale(_)));
    }

    this._updateBrushExtras(svg, handles, xScale);

    //TITLE
    svg.appendTitle(this.options.title, 'title', 15);
    //SUB TITLE
    svg.appendTitle(this.options.subtitle, 'sub-title', 30);
  }

  private _updateBrushExtras(svg, handles, xScale) {
    const selection = this._getEventSelection(svg);
    const circles = svg.chartArea.selectAll('g.series circle');
    if (selection === null) {
      circles.classed('active', false);
      handles.attr('display', 'none');
    } else {
      const [min, max] = selection
        .map(_ => xScale.invert(_));

      circles.classed('active', (d: ChartData.DataPoint) => {
        return min <= d.values[0] && d.values[0] <= max;
      });

      handles.attr('display', null).attr('transform', (d, i) => {
        if (selection[i] !== null) {
          return 'translate(' + [selection[i], -svg.chartSize.height / 4] + ')';
        }
        return null;
      });
    }
  }

  private _getEventSelection(svg): OneDimScatterChartComponent.Selection {
    if (d3.event) {
      return d3.event.selection;
    }
    return (<OneDimScatterChartComponent.Selection> d3.brushSelection(svg.chartArea.select('g.brush').node()));
  }
}

export namespace OneDimScatterChartComponent {
  export const defaultOptions: OneDimScatterChartOptions = {
    circleRadius: 5,
    yJitter: false,
    seriesGamma: D3ChartHelper.CategoricalGamma.category10,
  };
  export type Selection = [number, number];
}
