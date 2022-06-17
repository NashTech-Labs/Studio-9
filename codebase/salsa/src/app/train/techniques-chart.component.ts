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
import { Observable } from 'rxjs/Observable';
import { Observer } from 'rxjs/Observer';

import { D3ChartHelper } from '../charts/d3-chart.helper';

import { IModelTrainSummary, ITabularModel } from './model.interface';
import { ITrainTechniqueParameterDefinition, trainConfig } from './train.config';

interface ParamDatum {
  count: number;
  parameters: {
    [parameterName: string]: (string | number)[];
  };
}

interface TechniqueData {
  [stageName: string]: {
    [techniqueName: string]: ParamDatum;
  };
}

interface ScrollDirection {
  technique: string;
  parametersLength: number;
  direction: boolean;
}

const TICKS_COUNT = 5;
const PARAMETER_WIDTH = 100;
const PARAMETER_FIRST_HORIZONTAL_PADDING = 15;
const PARAMETER_HORIZONTAL_PADDING = 15;
const PARAMETER_FIRST_VERTICAL_PADDING = 15;
const PARAMETER_VERTICAL_PADDING = 15;
const TECHNIQUE_HEIGHT = 60;
const STAGE_FIRST_VERTICAL_PADDING = 10;
const STAGE_VERTICAL_PADDING = 35;
const VISIBLE_PARAMS = 2;
const PARAMETERS_OFFSET = (PARAMETER_WIDTH + PARAMETER_HORIZONTAL_PADDING);
const animationTime = 200;

@Component({
  selector: 'train-techniques-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg #canvas width='960' height='500'>
      <defs>
        <clipPath id="myClip">
          <rect [attr.x]="clipPadding" y="0"
            [attr.width]="clipWidth"
            [attr.height]="clipHeight"></rect>
        </clipPath>
      </defs>
    </svg>
  `,
})
export class TechniquesChartComponent implements OnChanges, AfterViewInit {
  @Input() iterations: IModelTrainSummary.TrainIteration[];
  @Input() selectedIterations: number[];
  @HostBinding('style.height') styleHeight = '100%';
  @HostBinding('style.width') styleWidth = '100%';
  clipWidth: number = PARAMETERS_OFFSET * VISIBLE_PARAMS;
  clipHeight: number = TECHNIQUE_HEIGHT;
  clipPadding = PARAMETER_FIRST_HORIZONTAL_PADDING;
  @ViewChild('canvas') private _svgElement: ElementRef;
  private _decimalPipe: DecimalPipe = new DecimalPipe('en-US');
  private observer: Observer<ScrollDirection>;
  private scrollCoordinates: { [stageName: string]: { [technique: string]: number } } = {};

  constructor() {
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (this._svgElement) {
      if ((changes['selectedIterations'] && !_.isEqual(changes['selectedIterations'].currentValue, changes['selectedIterations'].previousValue))
        || (changes['iterations'] && !_.isEqual(changes['iterations'].currentValue, changes['iterations'].previousValue))) {
        this.drawChart();
      }
    }
  }

  ngAfterViewInit(): void {
    this.drawChart();
  }

  drawChart() {
    const svg = new D3ChartHelper(this._svgElement.nativeElement);
    svg.margin = { top: 10, left: 1, bottom: 15, right: 0 };

    if (!this.iterations) {
      return;
    }

    const stageInstances: ITabularModel.PipelineSummaryStage[] = _.flatten(this.iterations.map(_ => _.hyperParameters));
    const selectedStageInstances: ITabularModel.PipelineSummaryStage[] = _.flatten(this.iterations.filter(iteration => _.includes(this.selectedIterations, iteration.index)).map(_ => _.hyperParameters));

    const techniquesByStage: TechniqueData = this._prepareIterationTechniques(stageInstances);
    const selectedTechniquesByStage: TechniqueData = selectedStageInstances.length ? this._prepareIterationTechniques(selectedStageInstances) : {};

    Object.keys(techniquesByStage).forEach((stage: string) => {
      selectedTechniquesByStage[stage] = _.defaults(selectedTechniquesByStage[stage] || {}, _.mapValues(techniquesByStage[stage], (): ParamDatum => {
        return {
          count: 0,
          parameters: {},
        };
      }));
    });

    const enabledStages: string[] = trainConfig.model.pipelineOrder.filter(stage => techniquesByStage.hasOwnProperty(stage));

    // STAGES
    const oldStageGroup = svg.chartArea
      .selectAll<SVGGElement, string>('g.stage')
      .data(enabledStages);

    oldStageGroup.exit().remove();

    const stageGroupTops: number[] = enabledStages
      .map(stage => TECHNIQUE_HEIGHT * d3.keys(techniquesByStage[stage]).length + STAGE_VERTICAL_PADDING)
      .map((height, idx, acc) => {
        return d3.sum(acc.slice(0, idx));
      });
    // ADD LAST STAGE TECHNIQUES PADDING
    const lastStage = enabledStages[enabledStages.length - 1];
    const totalHeight = stageGroupTops[stageGroupTops.length - 1] + d3.keys(techniquesByStage[lastStage]).length * TECHNIQUE_HEIGHT + STAGE_VERTICAL_PADDING + STAGE_FIRST_VERTICAL_PADDING;
    svg.svg.attr('height', totalHeight || 500);

    const stageGroup = oldStageGroup.enter()
      .append<SVGGElement>('g')
      .attr('class', 'stage')
      .merge(oldStageGroup)
      .attr('transform', (_, i) => `translate(0, ${STAGE_FIRST_VERTICAL_PADDING + stageGroupTops[i]})`);

    //tslint:disable-next-line:no-this-assignment
    const self = this;
    //drawing techniques
    stageGroup.each(function (stage: string) {
      self.drawStage(this, techniquesByStage, selectedTechniquesByStage, stage, svg);
      self._drawVerticalDelimiter(this, techniquesByStage, stage);
    });
  }

  private _drawVerticalDelimiter(stageEl: SVGGElement, techniquesByStage: TechniqueData, stage: string) {
    const stageSelection = d3.select(stageEl);
    const oldLineDelimiter = stageSelection.selectAll('line.delimiter').data([true]);
    oldLineDelimiter.enter()
      .append('line')
      .attr('class', 'delimiter')
      .attr('x1', 0)
      .attr('y1', 0)
      .attr('x2', 0)
      .merge(oldLineDelimiter)
      .attr('y2', TECHNIQUE_HEIGHT * d3.keys(techniquesByStage[stage]).length + 5)
      .attr('transform', 'translate(-1,0)');

    const oldPathGroup = stageSelection.selectAll('g.path-group').data([true]);
    const pathGroup = oldPathGroup.enter()
      .append('g')
      .attr('class', 'path-group')
      .merge(oldPathGroup)
      .attr('transform', `translate(0, 0)`);

    const oldPathDelimiter = pathGroup.selectAll('path.delimiter').data([true]);
    oldPathDelimiter.enter()
      .append('path')
      .attr('class', 'delimiter')
      .attr('d', 'M 0 0 L 3 3 L 3 10 L 0 13 Z');
  }

  private drawStage(that: SVGGElement, techniquesByStage: TechniqueData, selectedTechniquesByStage: TechniqueData, stage: string, svg: D3ChartHelper) {
    const attributeDomain = trainConfig.model.pipelineStage.techniques[stage].filter(_ => techniquesByStage[stage].hasOwnProperty(_));
    const stageWidth = svg.chartSize.width - PARAMETERS_OFFSET * VISIBLE_PARAMS - PARAMETER_FIRST_VERTICAL_PADDING - 25;
    const metricRange = [0, stageWidth];
    const attributeRange: [number, number] = [15, TECHNIQUE_HEIGHT * attributeDomain.length + 15];

    let metricScale = d3.scaleLinear()
      .domain([
        0,
        d3.max(d3.values(techniquesByStage[stage]).map(_ => _.count)),
      ])
      .rangeRound(metricRange)
      .nice();

    const attributeScale: any = d3.scaleBand<string>()
      .domain(attributeDomain)
      .rangeRound(attributeRange)
      .paddingInner(0.5)
      .paddingOuter(0.25);

    const data = this._prepareTechniqueParameters(techniquesByStage, stage);
    // STAGE LABELS
    D3ChartHelper.addText(that, [true], 'stage-label', 10, 10, trainConfig.model.pipelineStage.labels[stage]);
    // TECHNIQUES
    this._drawTechniqueBars(that, data, 'technique', attributeScale, metricScale, this._initTechniquePosition, this._setTechniqueTransition);

    // Selected TECHNIQUES
    const selectedData = this._prepareTechniqueParameters(selectedTechniquesByStage, stage);
    this._drawTechniqueBars(that, selectedData, 'active-technique', attributeScale, metricScale, this._initTechniquePosition, this._setTechniqueTransition);
    // TECHNIQUE LABELS INSIDE BAR
    const techniqueLabels = D3ChartHelper.addText(that, data, 'technique', 10, (_, i) => TECHNIQUE_HEIGHT * (i + 0.5), (d) => trainConfig.model.stageTechnique.labels[d.technique], 'start', stageWidth, 0, null, '1.7em');

    techniqueLabels.raise();
    // TECHNIQUE CHARTS
    const oldParameterGroups = d3.select(that).selectAll('g.technique-chart').data(data);
    oldParameterGroups.exit().remove();
    const parameterGroups = oldParameterGroups.enter()
      .append('g')
      .attr('class', 'technique-chart')
      .merge(oldParameterGroups)
      .attr('transform', (d, i) => `translate(${stageWidth + PARAMETER_FIRST_HORIZONTAL_PADDING}, ${i * TECHNIQUE_HEIGHT + PARAMETER_FIRST_VERTICAL_PADDING} )`)
      .style('clip-path', `url(#myClip)`);

    const parameterList = trainConfig.model.stageTechnique.params;
    // Group Of Parameter Charts
    const oldCharts = parameterGroups.selectAll('g.charts').data(d => [d]);
    oldCharts.exit().remove();
    const charts = oldCharts.enter()
      .append('g')
      .attr('class', 'charts')
      .merge(oldCharts)
      .attr('transform', (d) => {
        if (!this.scrollCoordinates.hasOwnProperty(stage)) {
          this.scrollCoordinates[stage] = {};
          this.scrollCoordinates[stage][d.technique] = 0;
        }
        if (!this.scrollCoordinates[stage].hasOwnProperty(d.technique)) {
          this.scrollCoordinates[stage][d.technique] = 0;
        }
        return `translate(${this.scrollCoordinates[stage][d.technique]}, 0)`;
      });
    // EACH CHART
    const oldParameterCharts = charts
      .selectAll<SVGGElement, [string, ITrainTechniqueParameterDefinition]>('g.chart')
      .data((d) => {
        return parameterList[d.technique].map(parameter => [d.technique, parameter]);
      });
    oldParameterCharts.exit().remove();
    const parameterCharts = oldParameterCharts.enter()
      .append<SVGGElement>('g')
      .attr('class', 'chart')
      .attr('transform', ([technique], chartIndex) => `translate(${chartIndex * (PARAMETER_HORIZONTAL_PADDING + PARAMETER_WIDTH) + PARAMETER_FIRST_HORIZONTAL_PADDING}, 0)`)
      .merge(oldParameterCharts);

    const parameterScrollData = data.map(d => {
      return {
        technique: d.technique,
        parametersLength: d3.keys(d.parameters).length,
        parameterNeedLeftScroll: this.scrollCoordinates[stage][d.technique] < 0,
        parameterNeedRightScroll: this.scrollCoordinates[stage][d.technique] + d3.keys(d.parameters).length * PARAMETERS_OFFSET > VISIBLE_PARAMS * PARAMETERS_OFFSET,
      };
    });
    // Left Chevron BUTTON-GROUP
    const oldLeftButton = d3.select(that).selectAll('g.left-button').data(parameterScrollData);
    oldLeftButton.exit().remove();
    const leftButton = oldLeftButton.enter()
      .append('g')
      .attr('class', 'left-button')
      .attr('transform', (_, i) => `translate(${stageWidth + PARAMETER_FIRST_HORIZONTAL_PADDING}, ${i * TECHNIQUE_HEIGHT + PARAMETER_FIRST_VERTICAL_PADDING} )`)
      .merge(oldLeftButton)
      .attr('visibility', (d) => d.parameterNeedLeftScroll ? 'visible' : 'hidden');
    // Left Chevron
    const oldLeftPath = leftButton.selectAll('path').data([true]);
    oldLeftPath.exit().remove();
    oldLeftPath.enter()
      .append('path')
      .attr('d', `M 10 10 L 0 ${TECHNIQUE_HEIGHT / 2} L 10 ${TECHNIQUE_HEIGHT - 10} Z`);

    // Right Chevron BUTTON-GROUP
    const oldRightButton = d3.select(that).selectAll('g.right-button').data(parameterScrollData);
    oldRightButton.exit().remove();
    const rightButton = oldRightButton.enter()
      .append('g')
      .attr('class', 'right-button')
      .attr('transform', (_, i) => `translate(${stageWidth + (PARAMETERS_OFFSET * VISIBLE_PARAMS ) + PARAMETER_FIRST_HORIZONTAL_PADDING + 5}, ${i * TECHNIQUE_HEIGHT + PARAMETER_FIRST_VERTICAL_PADDING} )`)
      .merge(oldRightButton)
      .attr('visibility', (d) => d.parameterNeedRightScroll ? 'visible' : 'hidden');
    // Right Chevron
    const oldRightPath = rightButton.selectAll('path').data([true]);
    oldRightPath.exit().remove();
    oldRightPath.enter()
      .append('path')
      .attr('d', `M 0 10 L 10 ${TECHNIQUE_HEIGHT / 2} L 0 ${TECHNIQUE_HEIGHT - 10} Z`);

    //tslint:disable-next-line:no-this-assignment
    const self = this;
    parameterCharts.each(function ([technique, parameter]) {
      self.drawParameter(this, [technique, parameter], techniquesByStage, selectedTechniquesByStage, stage, svg);
    });

    const observable: Observable<ScrollDirection> = Observable.create(observer => this.observer = observer);
    observable.debounceTime(animationTime).subscribe((value) => {
      this.scrollChart(charts.filter(d => d.technique === value.technique), stage, value, leftButton.filter(d => d.technique === value.technique), rightButton.filter(d => d.technique === value.technique));
    });

    // SCROLL LEFT-RIGHT
    leftButton.on('click', (d) => {
      this.observer.next({ technique: d.technique, parametersLength: d.parametersLength, direction: false });
    });

    rightButton.on('click', (d) => {
      this.observer.next({ technique: d.technique, parametersLength: d.parametersLength, direction: true });
    });
  }

  private scrollChart(chart, stage, value: ScrollDirection, leftButton, rightButton) {
    if (value.direction) {
      if (this.scrollCoordinates[stage][value.technique] + value.parametersLength * PARAMETERS_OFFSET > (VISIBLE_PARAMS * PARAMETERS_OFFSET)) {
        this.scrollCoordinates[stage][value.technique] -= PARAMETERS_OFFSET;
        leftButton.attr('visibility', 'visible');
      }
    } else {
      if (this.scrollCoordinates[stage][value.technique] < 0) {
        this.scrollCoordinates[stage][value.technique] += PARAMETERS_OFFSET;
        rightButton.attr('visibility', 'visible');
      }
    }
    if (this.scrollCoordinates[stage][value.technique] + (value.parametersLength * PARAMETERS_OFFSET) === (VISIBLE_PARAMS * PARAMETERS_OFFSET)) {
      rightButton.attr('visibility', 'hidden');
    }
    if (this.scrollCoordinates[stage][value.technique] === 0) {
      leftButton.attr('visibility', 'hidden');
    }
    chart.transition()
      .duration(animationTime)
      .attr('transform', `translate(${this.scrollCoordinates[stage][value.technique]}, 0)`);
  }

  private drawParameter(that: SVGGElement, [technique, parameter]: [string, ITrainTechniqueParameterDefinition], techniquesByStage: TechniqueData, selectedTechniquesByStage: TechniqueData, stage: string, svg: D3ChartHelper) {
    // PARAMETER LABEL
    D3ChartHelper.addText(that, [true], 'technique-label', 0, 10, parameter.title, 'start', PARAMETER_WIDTH);

    const values = d3.values(techniquesByStage[stage][technique].parameters[parameter.name]);

    const [metricValues, paramAttributeDomain] = this._getParameterValues(parameter, values, svg, values);

    //Metric axis
    const paramMetricScale: d3.ScaleLinear<number, number> = d3.scaleLinear()
      .domain([0, d3.max(metricValues)])
      .rangeRound([0, PARAMETER_WIDTH])
      .nice();
    //Attribute axis
    const rectAttributeScale: d3.ScaleBand<number> = d3.scaleBand<number>()
      .domain(d3.range(metricValues.length))
      .rangeRound([TECHNIQUE_HEIGHT - PARAMETER_VERTICAL_PADDING, PARAMETER_VERTICAL_PADDING])
      .paddingInner(0.2)
      .paddingOuter(0.5);

    // DRAW PARAMS ALL
    this._drawParameterBars(that, metricValues, 'parameter', rectAttributeScale, paramMetricScale, this._initParameterPosition, this._setParameterTransition, this._bindTooltips, paramAttributeDomain);
    // SELECTED PARAMS
    if (selectedTechniquesByStage && stage in selectedTechniquesByStage && technique in selectedTechniquesByStage[stage]) {
      const selectedValues = d3.values(selectedTechniquesByStage[stage][technique].parameters[parameter.name]);
      const [selectedMetricValues] = this._getParameterValues(parameter, selectedValues, svg, values);
      this._drawParameterBars(that, selectedMetricValues, 'active-parameter', rectAttributeScale, paramMetricScale, this._initParameterPosition, this._setParameterTransition, this._bindTooltips, paramAttributeDomain);
    } else {
      const [selectedMetricValues] = this._getParameterValues(parameter, [], svg, values);
      this._drawParameterBars(that, selectedMetricValues, 'active-parameter', rectAttributeScale, paramMetricScale, this._initParameterPosition, this._setParameterTransition, this._bindTooltips, paramAttributeDomain);
    }
  }

  private _setTechniqueTransition(rectAttributeScale: d3.ScaleBand<string>,
                                  metricScale: d3.ScaleContinuousNumeric<number, number>) {
    // *trollface*
    const y0 = metricScale(0);

    const rectWidth = rectAttributeScale.bandwidth();

    return function (this: SVGRectElement,
                     datum: { technique: string, count: number }) {
      const y1 = metricScale(datum.count);

      let y = Math.min(y0, y1),
        height = Math.abs(y1 - y0);

      // *trollface*
      d3.select(this)
        .transition()
        .duration(500)
        .delay((_, i) => (i + 1) * 10)
        .attr('y', rectAttributeScale(datum.technique))
        .attr('x', y)
        .attr('height', rectWidth)
        .attr('width', height);
    };
  }

  private _initTechniquePosition(rectAttributeScale: d3.ScaleBand<string>,
                                 metricScale: d3.ScaleContinuousNumeric<number, number>) {
    // *TROLLFACE*
    const rectWidth = rectAttributeScale.bandwidth();

    return function (this: SVGRectElement,
                     datum: { technique: string, count: number }) {

      d3.select(this)
        .attr('y', rectAttributeScale(datum.technique))
        .attr('x', metricScale(0))
        .attr('height', rectWidth)
        .attr('width', 0);
    };
  }

  private _setParameterTransition(rectAttributeScale: d3.ScaleBand<number>,
                                  metricScale: d3.ScaleContinuousNumeric<number, number>) {
    // *trollface*
    const y0 = metricScale(0);

    const rectWidth = rectAttributeScale.bandwidth();

    return function (this: SVGRectElement,
                     datum: number,
                     index: number) {
      const y1 = metricScale(datum);

      let y = Math.min(y0, y1),
        height = Math.abs(y1 - y0);

      // *trollface*
      d3.select(this)
        .transition()
        .duration(500)
        .delay((_, i) => (i + 1) * 10)
        .attr('y', rectAttributeScale(index))
        .attr('x', y)
        .attr('height', rectWidth)
        .attr('width', height);
    };
  }

  private _initParameterPosition(rectAttributeScale: d3.ScaleBand<number>,
                                 metricScale: d3.ScaleContinuousNumeric<number, number>) {
    // *trollface*
    const rectWidth = rectAttributeScale.bandwidth();

    return function (this: SVGRectElement,
                     datum: number,
                     index: number) {
      d3.select(this)
        .attr('y', rectAttributeScale(index))
        .attr('x', metricScale(0))
        .attr('height', rectWidth)
        .attr('width', 0);
    };
  }

  private _prepareIterationTechniques(iterations: ITabularModel.PipelineSummaryStage[]): TechniqueData {
    return iterations.reduce((acc, s: ITabularModel.PipelineSummaryStage) => {
      if (!acc.hasOwnProperty(s.stage)) {
        acc[s.stage] = {};
      }
      if (!acc[s.stage].hasOwnProperty(s.technique)) {
        acc[s.stage][s.technique] = {
          count: 1,
          parameters: {},
        };
        s.parameters.forEach(param => {
          acc[s.stage][s.technique].parameters[param.name] = [param.value || param.stringValue];
        });
      } else {
        acc[s.stage][s.technique].count++;
        s.parameters.forEach(param => {
          acc[s.stage][s.technique].parameters[param.name].push(param.value || param.stringValue);
        });
      }
      return acc;
    }, {});
  }

  private _prepareTechniqueParameters(data: TechniqueData, stage): (ParamDatum & { technique: string })[] {
    if (!data) {
      return [];
    }
    return trainConfig.model.pipelineStage.techniques[stage]
      .filter(_ => data[stage].hasOwnProperty(_))
      .map(technique => {
        return {
          technique,
          parameters: data[stage][technique].parameters,
          count: data[stage][technique].count,
        };
      });
  }

  private _bindTooltips(selection, paramAttributeDomain) {
    selection.on('mouseover', (_, index) => {
      d3.select('.toolTip').remove();
      const divTooltip = d3.select('body').append('div').attr('class', 'toolTip');
      divTooltip.style('left', d3.event.pageX + 10 + 'px');
      divTooltip.style('top', d3.event.pageY - 25 + 'px');
      divTooltip.style('display', 'inline-block');
      divTooltip.html(`Value: ${paramAttributeDomain[index]} Count: ${_}`);
    })
      .on('mouseout', () => {
        d3.select('.toolTip').remove();
      });
  }

  private _getParameterValues(parameter, currentValues, svg, originalValues): [number[], string[]] {
    let paramAttributeDomain: string[],
      result: number[];
    if (parameter.type === 'continuous') {
      const [start, stop] = d3.extent<number>(originalValues);
      const step = (stop - start) / TICKS_COUNT;
      paramAttributeDomain = [...d3.range(start, stop, step), stop].map(_ => this._formatFloat(_));
      result = currentValues.reduce((acc, value) => {
        const index = Math.floor((value - start) / step);
        acc[index]++;
        return acc;
      }, new Array(paramAttributeDomain.length).fill(0));
    } else {
      paramAttributeDomain = d3.extent(originalValues);
      result = currentValues.reduce((acc, value) => {
        const index = paramAttributeDomain.indexOf(value);
        acc[index]++;
        return acc;
      }, new Array(paramAttributeDomain.length).fill(0));
    }
    return [result, paramAttributeDomain];
  }

  private _drawTechniqueBars(el, data: any[], classSelector: string, attributeScale, metricScale, _initTechniquePosition, _setTechniqueTransition) {
    const oldBars = d3.select(el).selectAll(`rect.${classSelector}`)
      .data(data);

    oldBars.exit().remove();

    const bars = oldBars.enter()
      .append('rect')
      .attr('class', classSelector)
      .each(_initTechniquePosition(attributeScale, metricScale))
      .merge(oldBars);

    bars
      .each(_setTechniqueTransition(attributeScale, metricScale));

    bars.order();
  }

  private _drawParameterBars(el, data, classSelector, rectAttributeScale, paramMetricScale, _initParameterPosition, _setParameterTransition, _bindTooltips, paramAttributeDomain) {
    const oldBars = d3.select(el).selectAll(`rect.${classSelector}`)
      .data(data);

    oldBars.exit().remove();

    const bars = oldBars.enter()
      .append('rect')
      .attr('class', classSelector)
      .each(_initParameterPosition(rectAttributeScale, paramMetricScale))
      .merge(oldBars);

    bars
      .each(_setParameterTransition(rectAttributeScale, paramMetricScale));

    bars.order();

    _bindTooltips(bars, paramAttributeDomain);
  }

  private _formatFloat(value: number): string {
    return this._decimalPipe.transform(value, '1.0-2');
  }

}
