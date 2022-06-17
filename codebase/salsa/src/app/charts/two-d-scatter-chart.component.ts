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
import { D3Lasso, lasso } from 'd3-lasso';
import * as _ from 'lodash';

import { ChartData } from './chart-data.interface';
import { Chart } from './chart.interface';
import { D3ChartHelper } from './d3-chart.helper';

global['d3'] = require('d3');

export enum SelectionMode {
  LASSO = 'LASSO',
  RECTANGLE = 'RECTANGLE',
}

export interface TwoDimScatterChartOptions {
  dotSize?: number;
  xDomain?: [number, number];
  yDomain?: [number, number];
  integerXDomain?: boolean;
  integerYDomain?: boolean;
  seriesGamma?: D3ChartHelper.CategoricalGamma | string[];
  labelX?: string | null;
  labelY?: string | null;
  margin?: D3ChartHelper.Margin;
  enableLegend: boolean;
}

@Component({
  selector: 'chart-two-d-scatter',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg #canvas width='960' height='500'></svg>
  `,
})
export class TwoDimScatterChartComponent implements Chart<TwoDimScatterChartOptions>, OnChanges, AfterViewInit {
  @Input() data: ChartData;
  @Input() selectionMode: SelectionMode;
  @Input() options: TwoDimScatterChartOptions = TwoDimScatterChartComponent.defaultOptions;
  @Input() selection: TwoDimScatterChartComponent.Selection;
  @Input() rectangularSelection: TwoDimScatterChartComponent.RectangularSelection;
  @Output() selectionChange = new EventEmitter<TwoDimScatterChartComponent.Selection>();
  @Output() rectangularSelectionChange = new EventEmitter<TwoDimScatterChartComponent.RectangularSelection>();
  @HostBinding('style.height') styleHeight = '100%';
  @HostBinding('style.width') styleWidth = '100%';
  @ViewChild('canvas') private _svgElement: ElementRef;
  private _selectionLock: boolean = false;
  private _brush: d3.BrushBehavior<any>;
  private _lasso: D3Lasso<SVGRectElement, ChartData.DataPoint, SVGElement, any>;
  private _svg: D3ChartHelper;

  constructor() {}

  ngOnChanges(changes: SimpleChanges): void {
    if ('options' in changes) {
      this.options = _.defaults(this.options, TwoDimScatterChartComponent.defaultOptions);
    }
    if (this._svgElement) {
      if ('selectionMode' in changes) {
        this._initSvg();
      }
      this.drawChart();
    }
  }

  ngAfterViewInit(): void {
    this._initSvg();
    this.drawChart();
  }

  // TODO: draw all seriaes x groups
  drawChart() {
    if (this.options.margin) {
      this._svg.margin = this.options.margin;
    }

    const seriesRange = d3.range(this.data.series.length);

    const classifier = D3ChartHelper.prepareAttributesClassifier(this.data);

    const colorScale = D3ChartHelper.prepareCategoricalGammaScale(
      classifier.range,
      this.options.seriesGamma,
    );

    // Legend
    if (classifier.range.length > 1 && this.options.enableLegend) {
      this._svg.pushLegend('class', D3ChartHelper.legendColor()
        .title(this.data.attributes[0].name)
        .titleWidth(120)
        .labels(classifier.labels)
        .scale(colorScale));
    }

    const [xScaleDraft, yScaleDraft] = D3ChartHelper.prepareMetricScales(
      this.data,
      [this._svg.xRange, this._svg.yRange, [1, 10]],
      [this.options.xDomain, this.options.yDomain],
      3,
      [this.options.dotSize * 2, this.options.dotSize * 2],
    );

    // AXES
    const [, [yScale, xScale]] = this._svg.setAxes(
      {
        location: D3ChartHelper.AssetLocation.left,
        label: ('labelY' in this.options) ? this.options.labelY : this.data.metrics[1].name,
        scale: yScaleDraft,
        options: this.options.integerYDomain
          ? D3ChartHelper.integerAxisOptions(yScaleDraft)
          : {},
      },
      {
        location: D3ChartHelper.AssetLocation.bottom,
        label: ('labelX' in this.options) ? this.options.labelX : this.data.metrics[0].name,
        scale: xScaleDraft,
        options: this.options.integerXDomain
          ? D3ChartHelper.integerAxisOptions(xScaleDraft)
          : {},
      },
    );

    // SERIES
    const oldSeriesGroup = this._svg.chartArea
      .selectAll('g.series')
      .data(seriesRange);

    oldSeriesGroup.exit().remove();

    const seriesGroup = oldSeriesGroup.enter()
      .append('g')
      .classed('series', true)
      .merge(oldSeriesGroup);

    // POINTS
    const oldPoints = seriesGroup.selectAll('rect')
      .data(i => this.data.series[i].data);

    oldPoints.exit().remove();

    const points = oldPoints.enter()
      .append('rect')
      .attr('x', d => xScale(d.values[0]) - this.options.dotSize / 2)
      .attr('y', d => yScale(d.values[1]) - this.options.dotSize / 2)
      .attr('width', this.options.dotSize)
      .attr('height', this.options.dotSize)
      .attr('fill', d => colorScale(classifier(d.attributes)))
      .merge(oldPoints);

    points.transition()
      .duration(300)
      .attr('x', d => xScale(d.values[0]) - this.options.dotSize / 2)
      .attr('y', d => yScale(d.values[1]) - this.options.dotSize / 2);

    points.order();

    switch (this.selectionMode) {
      case (SelectionMode.LASSO):
        this._svg.chartArea.selectAll('rect.lasso-area')
          .attr('width', this._svg.chartSize.width)
          .attr('height', this._svg.chartSize.height);

        this._lasso.items(points);

        //premap for dragStart for newly created points
        points.nodes().forEach(function (e) {
          const box = (<Element> e).getBoundingClientRect();
          e['__lasso'].lassoPoint = [Math.round(box.left + box.width / 2), Math.round(box.top + box.height / 2)];
        });

        if (this.selection && !this._selectionLock) {
          const selection = this.selection;
          points.each(function (this: SVGRectElement, d: ChartData.DataPoint) {
            const selected = selection.includes(d.referenceId);
            this['__lasso'].selected = selected;
            d3.select(this).classed('active', selected);
          });
        }
        break;
      case SelectionMode.RECTANGLE:
        this._brush
          .extent([[0, 0], [this._svg.chartSize.width, this._svg.chartSize.height]])
          .on('brush', () => {
            this._selectionLock = true;
            this._updateBrushExtras(xScale, yScale);
          })
          .on('end', () => {
            this._updateBrushExtras(xScale, yScale);
            const rectangularSelection = TwoDimScatterChartComponent._getEventSelection(this._svg, xScale, yScale);

            if (rectangularSelection) {
              if (!_.isEqual(rectangularSelection, this.rectangularSelection)) {
                this.rectangularSelection = rectangularSelection;
              }
              this.rectangularSelectionChange.emit(rectangularSelection);
              const selection = points.filter(this.rectangularSelectionFilter(rectangularSelection)).data().map(_ => _.referenceId);
              this.selectionChange.emit(this.selection = selection);
            } else {
              this.rectangularSelectionChange.emit(this.rectangularSelection = null);
              this.selectionChange.emit(this.selection = null);
            }
            this._selectionLock = false;
          });

        const oldBrushEl = this._svg.chartArea.selectAll<SVGGElement, number>('g.brush').data([1]);

        const brushEl = oldBrushEl.enter()
          .append<SVGGElement>('g')
          .attr('class', 'brush')
          .merge(oldBrushEl);

        brushEl.call(this._brush);

        if (this.rectangularSelection && !this._selectionLock) {
          this._brush.move(brushEl.transition(),
            <TwoDimScatterChartComponent.RectangularSelection> [
              [xScale(this.rectangularSelection[0][0]), yScale(this.rectangularSelection[1][1])],
              [xScale(this.rectangularSelection[1][0]), yScale(this.rectangularSelection[0][1])],
            ],
          );
        }

        this._updateBrushExtras(xScale, yScale);
        break;
      default:
        // do nothing
    }
  }

  private _updateBrushExtras(xScale: d3.ScaleLinear<number, number>, yScale: d3.ScaleLinear<number, number>): void {
    const rectangularSelection = TwoDimScatterChartComponent._getEventSelection(this._svg, xScale, yScale);
    const rects = this._svg.chartArea.selectAll('g.series rect');
    if (rectangularSelection === null) {
      rects.classed('active', false);
    } else {
      rects.classed('active', this.rectangularSelectionFilter(rectangularSelection));
    }
  }

  private rectangularSelectionFilter(
    rectangularSelection: TwoDimScatterChartComponent.RectangularSelection,
  ): (d: ChartData.DataPoint) => boolean {
    return (d: ChartData.DataPoint) => {
      return rectangularSelection[0][0] <= d.values[0] && d.values[0] <= rectangularSelection[1][0] && rectangularSelection[0][1] <= d.values[1] && d.values[1] <= rectangularSelection[1][1];
    };
  }

  private _initSvg() {
    this._svg = new D3ChartHelper(this._svgElement.nativeElement);

    if (this.selectionMode === SelectionMode.LASSO) {
      const oldLassoArea = this._svg.chartArea.selectAll('rect.lasso-area').data([1]);
      const lassoArea = oldLassoArea.enter().append('rect')
        .classed('lasso-area', true)
        .style('opacity', 0)
        .merge(oldLassoArea);

      this._lasso = lasso()
        .closePathDistance(150)
        .closePathSelect(true)
        .hoverSelect(false)
        .targetArea(lassoArea)
        .items(d3.selectAll());

      this._svg.chartArea.call(this._lasso);

      this._lasso
        .on('start', () => {
          this._selectionLock = true;
          this._lasso.items()
            .classed('not_possible', true)
            .classed('active', false);
        })
        .on('draw', () => {
          this._lasso.possibleItems()
            .classed('not_possible', false)
            .classed('possible', true);

          this._lasso.notPossibleItems()
            .classed('not_possible', true)
            .classed('possible', false);
        })
        .on('end', () => {
          this._lasso.items()
            .classed('not_possible', false)
            .classed('possible', false);

          this._lasso.selectedItems()
            .classed('active', true);

          const referenceIdSelectionValues = this._lasso.selectedItems()
            .data()
            .map(_ => _.referenceId);

          this.selectionChange.emit(this.selection = referenceIdSelectionValues);
          this._selectionLock = false;
        });
    }

    if (this.selectionMode === SelectionMode.RECTANGLE) {
      this._brush = d3.brush<any>();
      this._brush.extent([[0, 0], [this._svg.chartSize.width, this._svg.chartSize.height]]);
    }
  }

  static _getEventSelection(svg: D3ChartHelper, xScale: d3.ScaleLinear<number, number>, yScale: d3.ScaleLinear<number, number>): TwoDimScatterChartComponent.RectangularSelection {
    let brushSelection: TwoDimScatterChartComponent.RectangularSelection;
    if (d3.event) {
      brushSelection = d3.event.selection;
    } else {
      brushSelection = (<TwoDimScatterChartComponent.RectangularSelection> d3.brushSelection(svg.chartArea.select<SVGGElement>('g.brush').node()));
    }

    if (brushSelection) {
      return <TwoDimScatterChartComponent.RectangularSelection> [
        [xScale.invert(brushSelection[0][0]), yScale.invert(brushSelection[1][1])],
        [xScale.invert(brushSelection[1][0]), yScale.invert(brushSelection[0][1])],
      ];
    }
    return null;
  }
}

export namespace TwoDimScatterChartComponent {
  export const defaultOptions: TwoDimScatterChartOptions = {
    dotSize: 5,
    seriesGamma: D3ChartHelper.CategoricalGamma.category10,
    enableLegend: true,
  };
  export type Selection = string[];
  export type RectangularSelection = [[number, number], [number, number]];
}
