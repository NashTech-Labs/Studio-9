import * as d3 from 'd3';
import { ScaleOrdinal } from 'd3-scale';
import * as d3Chromatic from 'd3-scale-chromatic';
import * as d3Legend from 'd3-svg-legend';
import { LegendColor } from 'd3-svg-legend';
import * as _ from 'lodash';

import { TTableValue } from '../tables/table.interface';

import { ChartData } from './chart-data.interface';

export type AxisScale<D> = d3.AxisScale<D> & {
  range(range: [number, number]): AxisScale<D>;
};

export interface IAxisParameters<D, S extends AxisScale<D> = AxisScale<D>> {
  location: D3ChartHelper.AssetLocation;
  label?: string;
  scale: S;
  options?: D3ChartHelper.AxisOptions<D> | null;
}

export class D3ChartHelper {
  readonly svg: d3.Selection<SVGElement, any, any, any>;
  readonly chartArea: d3.Selection<any, any, SVGElement, any>;

  private _margin: D3ChartHelper.Margin;
  private _legendMargin: D3ChartHelper.Margin = {left: 0, top: 0, right: 0, bottom: 0};
  private _leftAxisWidth = 0;
  private _rightAxisWidth = 0;
  private _bottomAxisHeight = 0;

  constructor(
    element: SVGElement,
  ) {
    this.svg = d3.select(element);
    const oldChartArea = this.svg.selectAll('g.chartArea').data([true]);
    this.chartArea = oldChartArea.enter()
      .append('g')
      .attr('class', 'chartArea')
      .merge(oldChartArea);
    //force chartArea transform translate
    this.margin = { left: 20, top: 30, right: 20, bottom: 5 };
  }

  set margin(margin: D3ChartHelper.Margin) {
    this._margin = margin;
    this._updateChartAreaPosition();
    // todo: redraw axes or throw if any axes are in place when this is called
  }

  get margin(): D3ChartHelper.Margin {
    return this._margin;
  }

  get size(): D3ChartHelper.Size {
    return {
      width: parseInt(this.svg.style('width'), 10),
      height: parseInt(this.svg.style('height'), 10),
    };
  }

  get rect(): D3ChartHelper.Rect {
    const { width, height } = this.size;
    return {
      left: 0,
      top: 0,
      width,
      height,
    };
  }

  get chartSize(): D3ChartHelper.Size {
    const { width, height } = this.size;
    return {
      width: width - this._margin.left - this._margin.right - this._legendMargin.left - this._legendMargin.right - this._rightAxisWidth - this._leftAxisWidth,
      height: height - this._margin.top - this._margin.bottom - this._legendMargin.top - this._legendMargin.bottom - this._bottomAxisHeight,
    };
  }

  get xRange(): [number, number] {
    return [0, this.chartSize.width];
  }

  get yRange(): [number, number] {
    return [this.chartSize.height, 0];
  }

  get legend(): d3.Selection<any, any, any, any> {
    const oldLegend = this.svg.selectAll('g.legend').data([true]);
    return oldLegend.enter()
      .append('g')
      .attr('class', 'legend')
      .merge(oldLegend);
  }

  // instance helpers
  pushLegend(name: string, legendCallback: (selection: d3.Selection<any, any, any, any>, ...args: any[]) => void) {
    const element = this.legend.selectAll(`g.legend-${name}`)
      .data([true]);

    element.enter()
      .append('g')
      .attr('class', `legend-${name}`)
      .merge(element)
      .call(legendCallback);

    const legendBox = this.legend.node().getBBox();
    this._legendMargin.right = legendBox.width + 10;

    this.legend
      .attr('transform', `translate(${this.size.width - this._margin.right - legendBox.width},${this.margin.top})`);
  }

  removeLegend(name: string) {
    this.legend.selectAll(`g.legend-${name}`)
      .remove();
  }

  appendTitle(title, className: string = 'chart-title', top: number = 15) {
    //TEXT
    const oldTitle = this.svg.selectAll(`text.${className}`)
      .data([1]);

    oldTitle.exit().remove();

    oldTitle.enter()
      .append('text')
      .attr('class', className)
      .merge(oldTitle)
      .attr('transform', `translate(${this.size.width / 2}, ${top})`)
      .text(title)
      .attr('text-anchor', 'middle')
      .each(D3ChartHelper.textTrimmer(this.size.width));
  }

  setAxes<D1, S1 extends AxisScale<D1>>(axis1Parameters: IAxisParameters<D1, S1>): [
    [d3.Axis<D1>],
    [S1],
    [d3.Selection<any, boolean, any, boolean>],
    [d3.Selection<any, boolean, any, boolean>]
  ];
  setAxes<D1, S1 extends AxisScale<D1>, D2, S2 extends AxisScale<D2>>(
    axis1Parameters: IAxisParameters<D1, S1>,
    axis2Parameters: IAxisParameters<D2, S2>,
  ): [
    [d3.Axis<D1>, d3.Axis<D2>],
    [S1, S2],
    [d3.Selection<any, boolean, any, boolean>, d3.Selection<any, boolean, any, boolean>],
    [d3.Selection<any, boolean, any, boolean>, d3.Selection<any, boolean, any, boolean>]
  ];
  setAxes<D1, S1 extends AxisScale<D1>, D2, S2 extends AxisScale<D2>, D3, S3 extends AxisScale<D3>>(
    axis1Parameters: IAxisParameters<D1, S1>,
    axis2Parameters: IAxisParameters<D2, S2>,
    axis3Parameters: IAxisParameters<D3, S3>,
  ): [
    [d3.Axis<D1>, d3.Axis<D2>, d3.Axis<D3>],
    [S1, S2, S3],
    [d3.Selection<any, boolean, any, boolean>, d3.Selection<any, boolean, any, boolean>, d3.Selection<any, boolean, any, boolean>],
    [d3.Selection<any, boolean, any, boolean>, d3.Selection<any, boolean, any, boolean>, d3.Selection<any, boolean, any, boolean>]
  ];
  setAxes<D, S extends AxisScale<D>>(...axesParameters: IAxisParameters<D, S>[]): [
    d3.Axis<D>[],
    S[],
    d3.Selection<any, boolean, any, boolean>[],
    d3.Selection<any, boolean, any, boolean>[]
  ];
  setAxes(...axesParameters: IAxisParameters<any>[]): [
    d3.Axis<any>[],
    AxisScale<any>[],
    d3.Selection<any, boolean, any, boolean>[],
    d3.Selection<any, boolean, any, boolean>[]
  ] {
    let axes: d3.Axis<any>[] = [],
      scales: AxisScale<any>[] = [],
      elements: d3.Selection<any, boolean, any, boolean>[] = [],
      groupElements: d3.Selection<any, boolean, any, boolean>[] = [];

    const axisLocationOrder = {
      [D3ChartHelper.AssetLocation.left]: 0,
      [D3ChartHelper.AssetLocation.right]: 1,
      [D3ChartHelper.AssetLocation.bottom]: 2,
    };

    // axes definition order matters, we need to make sure that left axis is added before right one
    axesParameters = _.sortBy(axesParameters, _ => axisLocationOrder[_.location]);

    axesParameters.forEach(axisParameters => {
      const [, groupElement, axisElement] = this._applyAxis(axisParameters);

      axisParameters.scale = this._setAxisPosition(groupElement, axisElement, axisParameters);
    });

    // redrawing scales
    axesParameters.forEach(axisParameters => {
      const [axis, groupElement, element] = this._applyAxis(axisParameters);
      axes.push(axis);
      scales.push(axisParameters.scale);
      groupElements.push(groupElement);
      elements.push(element);
    });

    return [axes, scales, groupElements, elements];
  }

  private _applyAxis<D>(axisParameters: IAxisParameters<D>): [
    d3.Axis<D>,
    d3.Selection<any, boolean, any, boolean>,
    d3.Selection<any, boolean, any, boolean>
  ] {
    // Creating a group with axis and its label
    const oldGroupElement = this.chartArea.selectAll(`g.axis-group-${axisParameters.location}`)
      .data([true]);

    const groupElement = oldGroupElement
      .enter()
      .append('g')
      .attr('class', `axis-group-${axisParameters.location}`)
      .attr('fill', 'black')
      .merge(oldGroupElement);

    const oldAxisElement = groupElement.selectAll(`g.axis-${axisParameters.location}`).data([true]);

    // Creating axis element
    const axisElement = oldAxisElement
      .enter()
      .append('g')
      .attr('class', `axis-${axisParameters.location}`)
      .merge(oldAxisElement);

    const axis = this._createAxis(axisParameters.scale, axisParameters.location);

    // applying axis external options
    if (axisParameters.options) {
      if (axisParameters.options.ticks) {
        axis.tickValues(axisParameters.options.ticks);
      }

      if (axisParameters.options.tickFormat) {
        axis.tickFormat(axisParameters.options.tickFormat);
      }
    }

    // draw axis
    axisElement.call(<any> axis);

    // placing axis with labels
    this._setAxisPosition(groupElement, axisElement, axisParameters);

    return [axis, groupElement, axisElement];
  }

  private _setAxisPosition<D>(groupElement, axisElement, axisParameters: IAxisParameters<D>): AxisScale<D> {
    const scale: any = axisParameters.scale.copy();
    // Adding Label to any type of axis
    const oldAxisLabel = groupElement.selectAll('text.axis-label').data([1]);
    const axisLabel = oldAxisLabel.enter()
      .append('text')
      .classed('axis-label', true)
      .attr('text-anchor', 'middle')
      .merge(oldAxisLabel)
      .text(axisParameters.label);

    if (axisParameters.options && axisParameters.options.angle) {
      axisElement.selectAll('text')
        .attr('y', 0)
        .attr('x', 9)
        .attr('dy', '.35em')
        .attr('transform', 'rotate(90)')
        .style('text-anchor', 'start');
    }

    //Rescaling group + setting its position
    switch (axisParameters.location) {
      case D3ChartHelper.AssetLocation.left:
        groupElement.attr('transform', `translate(0, 0)`);
        this._leftAxisWidth = (<SVGGraphicsElement> axisElement.node()).getBBox().width;
        axisElement.attr('transform', `translate(0, 0)`);
        axisLabel.attr('transform', `translate(-${this._leftAxisWidth + 2}, ${this.chartSize.height / 2}) rotate(-90)`);
        // need to fix chartArea position
        this._updateChartAreaPosition();
        break;
      case D3ChartHelper.AssetLocation.bottom:
        groupElement.attr('transform', `translate(0, ${this.chartSize.height})`);
        const axisHeight = (<SVGGraphicsElement> axisElement.node()).getBBox().height + 12; //@ TODO WHY?
        axisElement.attr('transform', `translate(0, 0)`);
        axisLabel.attr('transform', `translate(${this.chartSize.width / 2}, ${axisHeight + 2})`);
        this._bottomAxisHeight = axisHeight;
        break;
      case D3ChartHelper.AssetLocation.right:
        groupElement.attr('transform', `translate(${this.chartSize.width}, 0)`);
        this._rightAxisWidth = (<SVGGraphicsElement> axisElement.node()).getBBox().width;
        axisElement.attr('transform', `translate(0, 0)`);
        axisLabel.attr('transform', `translate(${this._rightAxisWidth}, ${this.chartSize.height / 2}) rotate(90)`);
        break;
      default:
        throw new Error(`Unknown D3ChartHelper.AssetLocation value: ${axisParameters.location}`);
    }

    return scale;
  }

  private _createAxis<D>(scale: AxisScale<D>, location: D3ChartHelper.AssetLocation): d3.Axis<D> {
    // Rearranging scales depending on its location
    switch (location) {
      case D3ChartHelper.AssetLocation.left:
        scale.range(this.yRange);
        return d3.axisLeft(scale);
      case D3ChartHelper.AssetLocation.bottom:
        scale.range(this.xRange);
        return d3.axisBottom(scale);
      case D3ChartHelper.AssetLocation.right:
        scale.range(this.yRange);
        return d3.axisRight(scale);
      default:
        throw new Error(`Unknown D3ChartHelper.AssetLocation value: ${location}`);
    }
  }

  private _updateChartAreaPosition() {
    this.chartArea
      .attr('transform', `translate(${this.margin.left + this._leftAxisWidth}, ${this.margin.top})`);
  }

  // Static methods
  static legendColor(): LegendColor {
    return d3Legend.legendColor();
  }

  static prepareMetricScales(
    data: ChartData,
    ranges: [number, number][],
    domains: [number, number][] = [],
    limit: number = data.metrics.length,
    paddings: number[] = [],
  ): d3.ScaleLinear<number, number>[] {
    return data.metrics.slice(0, limit).map((_, i) => {
      const range = ranges[i];
      if (!range) {
        throw new Error(`No range defined for metric: ${_.name}`);
      }
      const domain = domains[i] || [
        d3.min(data.series, series => d3.min(series.data, _ => _.values[i])) || 0,
        d3.max(data.series, series => d3.max(series.data, _ => _.values[i])) || 0,
      ];

      const scale = d3.scaleLinear()
        .domain(domain)
        .rangeRound(range)
        .clamp(false)
        .nice();

      const padding = paddings[i] || 0;
      if (padding > 0) {
        let [rangeLeft, rangeRight] = range;
        if (rangeRight < rangeLeft) {
          rangeLeft += padding;
          rangeRight -= padding;
        } else {
          rangeRight += padding;
          rangeLeft -= padding;
        }
        const domainLeft = scale.invert(rangeLeft);
        const domainRight = scale.invert(rangeRight);
        scale
          .domain([domainLeft, domainRight])
          .rangeRound([rangeLeft, rangeRight]);
      }

      return scale;
    });
  }

  static prepareCombinedMetricsScale(
    data: ChartData,
    range: [number, number],
    includeZero: boolean = false,
    limit: number = data.metrics.length,
    metricDomain?: [number, number],
  ): d3.ScaleLinear<number, number> {

    const domain = metricDomain ? metricDomain : [
      d3.min(data.series, series => d3.min(series.data, _ => d3.min(_.values.slice(0, limit)))),
      d3.max(data.series, series => d3.max(series.data, _ => d3.max(_.values.slice(0, limit)))),
    ];

    if (includeZero) {
      domain[0] = d3.min([0, domain[0]]);
      domain[1] = d3.max([0, domain[1]]);
    }

    return d3.scaleLinear()
      .domain(domain)
      .rangeRound(range)
      .nice();
  }

  static prepareStackedMetricsScale(
    data: ChartData,
    range: [number, number],
    limit: number = data.metrics.length,
  ): d3.ScaleLinear<number, number> {

    const domain = [
      d3.min(data.series[0].data, _ => d3.sum(_.values.slice(0, limit).map(_ => Math.min(0, _)))),
      d3.max(data.series[0].data, _ => d3.sum(_.values.slice(0, limit).map(_ => Math.max(0, _)))),
    ];

    return d3.scaleLinear()
      .domain(domain)
      .rangeRound(range)
      .nice();
  }

  static prepareAttributesClassifier(
    data: ChartData,
    labelGlue: string = ', ',
    takeIndices?: number[], // attribute indices
  ): D3ChartHelper.AttributesClassifier {
    const includeIndices: number[] = takeIndices || d3.range(data.attributes.length);

    const domain = [];
    const labels = [];
    const _trashMap: {[k: string]: string} = {};

    data.series.forEach((series) => {
      series.data.forEach((row: ChartData.DataPoint) => {
        const values: TTableValue[] = includeIndices.map(_ => row.attributes[_]);

        const hash = values.map(encodeURIComponent).join('&');
        if (!_trashMap.hasOwnProperty(hash)) {
          const label = values.map(String).join(labelGlue);
          _trashMap[hash] = label;
          domain.push(hash);
          labels.push(label);
        }
      });
    });

    const range = d3.range(domain.length);

    const scale = d3.scaleOrdinal<number>()
      .domain(domain)
      .range(range);

    return Object.assign(function(attributes: TTableValue[]) {
      const values: TTableValue[] = includeIndices.map(_ => attributes[_]);
      return scale(values.map(encodeURIComponent).join('&'));
    }, {
      domain,
      range,
      labels,
    });
  }

  static prepareCategoricalGammaScale<T>(domain: T[], gamma: D3ChartHelper.CategoricalGamma | string[]): ScaleOrdinal<T, string> {
    return d3.scaleOrdinal<T, string>()
      .domain(domain)
      .range(D3ChartHelper.getCategoricalGamma(gamma));
  }

  static getCategoricalGamma(gamma: D3ChartHelper.CategoricalGamma | string[]): string[] {
    if (Array.isArray(gamma)) {
      return gamma;
    }

    switch (gamma) {
      case 'category10':
        return d3.schemeCategory10;
      case 'category20':
        return d3.schemeCategory20;
      case 'category20b':
        return d3.schemeCategory20b;
      case 'category20c':
        return d3.schemeCategory20c;
      case 'schemeAccent':
        return <string[]> d3Chromatic.schemeAccent;
      case 'schemeDark2':
        return <string[]> d3Chromatic.schemeDark2;
      case 'schemePaired':
        return <string[]> d3Chromatic.schemePaired;
      case 'schemePastel1':
        return <string[]> d3Chromatic.schemePastel1;
      case 'schemePastel2':
        return <string[]> d3Chromatic.schemePastel2;
      case 'schemeSet1':
        return <string[]> d3Chromatic.schemeSet1;
      case 'schemeSet2':
        return <string[]> d3Chromatic.schemeSet2;
      case 'schemeSet3':
        return <string[]> d3Chromatic.schemeSet3;
      default:
        throw new Error('Unsupported gamma');
    }
  }

  static integerAxisOptions(
    scale: d3.AxisScale<number> & { ticks(count?: number): number[]},
    ticksCount?: number,
  ): D3ChartHelper.AxisOptions<number> {
    return {
      ticks: scale.ticks(ticksCount).filter(_ => _ % 1 === 0),
      tickFormat: d3.format('d'),
    };
  }


  static textTrimmer(width) {
    return function() {
      //tslint:disable-next-line:static-this
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

  static addText<T, E extends SVGElement>(
    el: E,
    data: T[],
    classSelector: string,
    xFn: number | d3.ValueFn<SVGTextElement, T, number>,
    yFn: number | d3.ValueFn<SVGTextElement, T, number>,
    textFn: string | d3.ValueFn<SVGTextElement, T, string>,
    textAnchor?: string,
    width?: number,
    rotateAngle?: number,
    dx?: number | string,
    dy?: number | string,
  ): d3.Selection<SVGTextElement, T, E, undefined> {
    const oldText = d3.select<E, undefined>(el)
      .selectAll<SVGTextElement, T>(`text.${classSelector}`)
      .data(data);
    oldText.exit().remove();
    const text = oldText.enter()
      .append<SVGTextElement>('text')
      .attr('class', classSelector)
      .attr('text-anchor', textAnchor || 'start')
      .attr('transform', `rotate(${rotateAngle || 0})`)
      .merge(oldText)
      .attr('y', <any> yFn) // see https://github.com/Microsoft/TypeScript/issues/1805
      .attr('dy', `${dy || 0}`)
      .attr('x', <any> xFn) // ^
      .attr('dx', `${dx || 0}`)
      .text(<any> textFn); // -------^

    if (width) {
      text.each(D3ChartHelper.textTrimmer(width));
    }

    return text;
  }
}

export namespace D3ChartHelper {
  export interface Margin {
    left: number;
    top: number;
    right: number;
    bottom: number;
  }

  export interface Size {
    width: number;
    height: number;
  }

  export interface Rect extends Size {
    left: number;
    top: number;
  }

  export interface AttributesClassifier {
    (x: TTableValue[]): number;
    readonly domain: string[];
    readonly labels: string[];
    readonly range: number[];
  }

  export interface AxisOptions<D> {
    ticks?: D[];
    tickFormat?: (value: D, i: number) => string;
    angle?: number;
  }

  export enum CategoricalGamma {
    category10 = 'category10',
    category20 = 'category20',
    category20b = 'category20b',
    category20c = 'category20c',
    schemeAccent = 'schemeAccent',
    schemeDark2 = 'schemeDark2',
    schemePaired = 'schemePaired',
    schemePastel1 = 'schemePastel1',
    schemePastel2 = 'schemePastel2',
    schemeSet1 = 'schemeSet1',
    schemeSet2 = 'schemeSet2',
    schemeSet3 = 'schemeSet3',
  }

  export enum AssetLocation {
    left = 'left',
    bottom = 'bottom',
    right = 'right',
  }
}
