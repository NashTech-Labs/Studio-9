import { DecimalPipe } from '@angular/common';
import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';

import * as _ from 'lodash';
import { Subscription } from 'rxjs/Subscription';

import { BarChartOptions } from '../charts/bar-chart.component';
import { ChartData } from '../charts/chart-data.interface';
import { D3ChartHelper } from '../charts/d3-chart.helper';
import { SelectionMode, TwoDimScatterChartComponent, TwoDimScatterChartOptions } from '../charts/two-d-scatter-chart.component';
import { IProcess } from '../core/interfaces/process.interface';

import { IModelTrainSummary, ITabularModel } from './model.interface';
import { ModelService } from './model.service';
import { trainConfig } from './train.config';

const ITERATIONS_GRAPH_DOMAIN_STEP = 50;
const ITERATIONS_LIST_STEP = 40;

@Component({
  selector: 'model-train-summary',
  template: `
    <div class="row train-container">
      <div class="col-md-3">
        <train-label label="Training Progress"></train-label>
        <progress-bar style="height:40px"
          [label]="trainingSummary | apply: _progressLabel: model"
          [progress]="process ? process.progress : 1"></progress-bar>
        <div class="row">
          <div class="col-xs-6 text-center">
            <span>Elapsed</span>
            <div class="resources-number" [ngSwitch]="model | apply: _isModelRunning">
              <timer *ngSwitchCase="true" [start]="model.created"></timer>
              <span *ngSwitchDefault="">--:--:--</span>
            </div>
          </div>
          <div class="col-xs-6 text-center">
            <span>Iterations</span>
            <div class="resources-number">{{trainingSummary?.iterations.length}}</div>
          </div>
        </div>
        <div class="text-center" *ngIf="model?.status === ITabularModel.Status.TRAINING">
          <div class="btn-group">
            <button type="button"
              class="btn btn-md btn-primary"
              (click)="cancelModel()">Cancel
            </button>
            <button type="button"
              [disabled]="trainingSummary?.state !== IModelTrainSummary.TrainState.REFINING"
              class="btn btn-md btn-primary"
              (click)="stopRefining()">Stop Refining
            </button>
          </div>
        </div>
        <train-label label="Resources"></train-label>
        <div class="row">
          <div class="col-xs-3 text-center">
            <span>Nodes</span>
            <div class="resources-number">{{trainingSummary?.resources.nodes}}</div>
          </div>
          <div class="col-xs-3 text-center">
            <span>CPUs</span>
            <div class="resources-number">{{trainingSummary?.resources.cpus}}</div>
          </div>
          <div class="col-xs-3 text-center">
            <span>Cores</span>
            <div class="resources-number">{{trainingSummary?.resources.cpuCores}}</div>
          </div>
          <div class="col-xs-3 text-center">
            <span>GPUs</span>
            <div class="resources-number">{{trainingSummary?.resources.gpus}}</div>
          </div>
        </div>
        <train-label label="CPU"></train-label>
        <div class="row train-resource-group">
          <div class="col-xs-3">
            <chart-bar *ngIf="cpuLoadChart"
              [data]="cpuLoadChart"
              [options]="resourceChartOptions"
            ></chart-bar>
          </div>
          <div class="col-xs-9">
            <chart-bar *ngIf="cpuLoadLogChart"
              [data]="cpuLoadLogChart"
              [options]="resourceChartOptions"
            ></chart-bar>
          </div>
        </div>
        <train-label label="GPU"></train-label>
        <div class="row train-resource-group">
          <div class="col-xs-3">
            <chart-bar *ngIf="gpuLoadChart"
              [data]="gpuLoadChart"
              [options]="resourceChartOptions"
            ></chart-bar>
          </div>
          <div class="col-xs-9">
            <chart-bar *ngIf="gpuLoadLogChart"
              [data]="gpuLoadLogChart"
              [options]="resourceChartOptions"
            ></chart-bar>
          </div>
        </div>
        <train-label label="Memory"></train-label>
        <div class="row train-resource-group">
          <div class="col-xs-3">
            <chart-bar *ngIf="memoryUsageChart"
              [data]="memoryUsageChart"
              [options]="resourceChartOptions"
            ></chart-bar>
          </div>
          <div class="col-xs-9">
            <chart-bar *ngIf="memoryUsageLogChart"
              [data]="memoryUsageLogChart"
              [options]="resourceChartOptions"
            ></chart-bar>
          </div>
        </div>
      </div>
      <div class="col-md-5">
        <div [hidden]="iterationSummary">
          <train-label label="Iteration Scores"></train-label>
          <chart-two-d-scatter *ngIf="iterationScoreChart"
            [selectionMode]="SelectionMode.RECTANGLE"
            [data]="iterationScoreChart"
            [options]="iterationsChartOptions"
            (selectionChange)="selectPoints($event)"
          ></chart-two-d-scatter>
          <train-label label="Iterations"></train-label>
          <div class="scrollbar scrollbar-style"
            [adaptiveHeight]="{minHeight: 200, property: 'max-height', pageMargin: 25, trigger: trainingSummary?.iterations}"
          >
            <svg #canvas width="960" height="500" class="iterations"></svg>
          </div>
        </div>
        <div [hidden]="!iterationSummary" class="model-pipeline-summary-embed">
          <train-label label="Iteration Summary"></train-label>
          <div class="model-pipeline-summary-container">
            <button class="btn btn-secondary pull-right" (click)="iterationSummary = null">
              <i class="glyphicon glyphicon-remove"></i>
            </button>
            <dl *ngFor="let stage of stageSummaries" class="dl-horizontal train-dl-summary">
              <dt>Stage</dt>
              <dd>{{trainConfig.model.pipelineStage.labels[stage.stage]}}</dd>
              <dt>Technique</dt>
              <dd>{{trainConfig.model.stageTechnique.labels[stage.technique]}}</dd>
              <ng-template [ngIf]="trainConfig.model.stageTechnique.params[stage.technique].length > 0">
                <dt>Parameters</dt>
                <dd>
                  <table class="table table-bordered table-sm">
                    <thead>
                    <tr>
                      <th style="width: 30%">Parameter</th>
                      <th>Value</th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr *ngFor="let param of stage.parameters"
                      [ngSwitch]="!!param.stringValue">
                      <td>{{paramTitle(stage.technique, param.name)}}</td>
                      <td *ngSwitchCase="true">{{param.stringValue}}</td>
                      <td *ngSwitchCase="false">{{param.value | number: '1.0-5'}}</td>
                    </tr>
                    </tbody>
                  </table>
                </dd>
              </ng-template>
            </dl>
          </div>
        </div>
      </div>
      <div class="col-md-4">
        <train-label label="Stages"></train-label>
        <div class="scrollbar scrollbar-style"
          [adaptiveHeight]="{minHeight: 600, property: 'height', pageMargin: 25, trigger: trainingSummary?.iterations}"
        >
          <train-techniques-chart
            [iterations]="trainingSummary?.iterations"
            [selectedIterations]="selectedIterations"
          ></train-techniques-chart>
        </div>
      </div>
    </div>
  `,
})
export class ModelTrainSummaryComponent implements OnChanges, OnDestroy {
  @Input() model: ITabularModel;
  @Input() process: IProcess;
  @Output() status: EventEmitter<string> = new EventEmitter<string>();
  readonly SelectionMode = SelectionMode;
  readonly trainConfig = trainConfig;
  readonly ITabularModel = ITabularModel;
  readonly IModelTrainSummary = IModelTrainSummary;
  cpuLoadChart: ChartData;
  cpuLoadLogChart: ChartData;
  gpuLoadChart: ChartData;
  gpuLoadLogChart: ChartData;
  memoryUsageChart: ChartData;
  memoryUsageLogChart: ChartData;
  iterationScoreChart: ChartData;
  resourceChartOptions: BarChartOptions = {
    margin: {
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
    },
  };
  iterationsChartOptions: TwoDimScatterChartOptions = {
    yDomain: [0, 1],
    integerXDomain: true,
    seriesGamma: [''],
    enableLegend: false,
    margin: {
      top: 0,
      left: 14,
      right: 0,
      bottom: 0,
    },
  };
  iterationSummary: IModelTrainSummary.TrainIteration;
  trainingSummary: IModelTrainSummary;
  progress: number = 0;
  selection: TwoDimScatterChartComponent.Selection;
  selectedIterations: number[] = [];
  stageSummaries: ITabularModel.PipelineSummaryStage[] = [];
  elapsed: string;
  @ViewChild('canvas') private _svgElement: ElementRef;
  private _summarySubscription: Subscription;
  private _decimalPipe: DecimalPipe = new DecimalPipe('en-US');

  constructor(private models: ModelService) {
  }

  paramTitle = (technique: ITabularModel.StageTechnique, name: string): string => {
    const paramDefinition = trainConfig.model.stageTechnique.params[technique].find(_ => _.name === name);
    return paramDefinition
      ? paramDefinition.title
      : name;
  };

  ngOnChanges(changes: SimpleChanges): void {
    if ('model' in changes) {
      this._summarySubscription && this._summarySubscription.unsubscribe();
      this._summarySubscription = this.models.getTrainSummary(this.model.id, true).subscribe(summary => {
        this.trainingSummary = summary;
        this.iterationsChartOptions.xDomain = [
          0,
          Math.ceil(this.trainingSummary.iterations.length / ITERATIONS_GRAPH_DOMAIN_STEP) * ITERATIONS_GRAPH_DOMAIN_STEP,
        ];
        this.progress = summary.progress;
        this.status.emit(summary.state);
        this.selectPoints(this.selection);
        if (this.trainingSummary.state === IModelTrainSummary.TrainState.COMPLETE) {
          this._summarySubscription.unsubscribe();
        }
        this._visualizeData();
      });
    }
  }

  ngOnDestroy(): void {
    this._summarySubscription && this._summarySubscription.unsubscribe();
  }

  drawIterations() {
    const svg = new D3ChartHelper(this._svgElement.nativeElement);
    svg.margin = { left: 10, right: 10, top: 0, bottom: 0 };
    //iterations
    const oldIterations = svg.chartArea.selectAll('g.iteration').data(this.trainingSummary.iterations);
    const iterations = oldIterations.enter()
      .append('g')
      .attr('class', 'iteration')
      .merge(oldIterations)
      .attr('transform', (_, i) => `translate(0, ${i * ITERATIONS_LIST_STEP})`);

    iterations.on('click', (iteration: IModelTrainSummary.TrainIteration) => {
      this.iterationSummary = !_.isEqual(iteration, this.iterationSummary) ? iteration : null;
      this.stageSummaries = this.iterationSummary ? trainConfig.model.pipelineOrder.map(
        stage => this.iterationSummary.hyperParameters.find(_ => _.stage === stage),
      ).filter(_ => !!_) : [];
    });

    const backgroundPath = iterations.selectAll('line.background').data((d) => [d]);
    backgroundPath.enter()
      .append('line')
      .attr('class', 'background')
      .merge(backgroundPath)
      .attr('x1', 0)
      .attr('x2', svg.chartSize.width)
      .attr('y1', 35)
      .attr('y2', 35);

    const foregroundPath = iterations.selectAll('line.foreground').data((d) => [d]);
    foregroundPath.enter()
      .append('line')
      .attr('class', 'foreground')
      .merge(foregroundPath)
      .classed('active', (iteration) => this.selectedIterations.find(_ => _ === iteration.index) >= 0)
      .attr('x1', 0)
      .attr('x2', (iteration) => svg.chartSize.width * iteration.summary.areaUnderROC)
      .attr('y1', 35)
      .attr('y2', 35);

    // index
    const oldIndexText = iterations.selectAll('text.index').data((d) => [d]);
    oldIndexText
      .enter()
      .append('text')
      .attr('x', 0)
      .attr('y', 20)
      .attr('dy', 10)
      .attr('class', 'index')
      .attr('font-size', '1em')
      .attr('text-anchor', 'start')
      .merge(oldIndexText)
      .classed('active', (iteration) => this.selectedIterations.find(_ => _ === iteration.index) >= 0)
      .text((iteration) => `#${iteration.index}`);

    // classifierStageInfo
    const oldClassifierStageText = iterations.selectAll('text.classifier').data((d) => [d]);
    oldClassifierStageText
      .enter()
      .append('text')
      .attr('y', 0)
      .attr('dy', 30)
      .attr('class', 'classifier')
      .attr('font-size', '1em')
      .attr('text-anchor', 'middle')
      .merge(oldClassifierStageText)
      .attr('x', svg.chartSize.width / 2)
      .classed('active', (iteration) => this.selectedIterations.find(_ => _ === iteration.index) >= 0)
      .text((iteration) => {
        const classifierStageInfo = iteration.hyperParameters.find(_ => _.stage === ITabularModel.PipelineStage.MODEL_PRIMITIVES);
        return `${classifierStageInfo ? trainConfig.model.stageTechnique.labels[classifierStageInfo.technique] : 'Unknown'}`;
      });
    // Auroc
    const oldAurocText = iterations.selectAll('text.auroc').data((d) => [d]);
    oldAurocText
      .enter()
      .append('text')
      .attr('y', 0)
      .attr('dy', 30)
      .attr('class', 'auroc')
      .attr('font-size', '1em')
      .attr('text-anchor', 'end')
      .merge(oldAurocText)
      .attr('x', svg.chartSize.width)
      .classed('active', (iteration) => this.selectedIterations.find(_ => _ === iteration.index) >= 0)
      .text((iteration) => `AUC = ${this.formatFloat(iteration.summary.areaUnderROC)}`);

    svg.svg.attr('height', (<SVGGraphicsElement> svg.chartArea.node()).getBBox().height);
  }

  selectPoints(selection: TwoDimScatterChartComponent.Selection) {
    if (!_.isEqual(this.selection, selection)) {
      this.selection = selection;
    }

    if (this.selection) {
      this.selectedIterations = this.trainingSummary.iterations
        .filter((iteration) =>
          this.selection.includes(String(iteration.index)),
        ).map(_ => _.index);
    } else {
      this.selectedIterations = [];
    }
    this.drawIterations();
  }

  cancelModel() {
    this.models.cancel(this.model).subscribe();
  }

  stopRefining() {
    this.models.stopRefining(this.model.id);
  }

  _progressLabel = (trainingSummary: IModelTrainSummary, model: ITabularModel): string => {
    if (!trainingSummary || !model) {
      return '';
    }
    const baseLabel = trainConfig.model.trainState.labels[trainingSummary.state];
    if (model.status === ITabularModel.Status.PREDICTING) {
      return `${baseLabel} (Initial Predict)`;
    }
    return baseLabel;
  };

  _isModelRunning = (model: ITabularModel) => {
    return [ITabularModel.Status.TRAINING/*, ITabularModel.Status.PREDICTING*/].includes(model.status);
  };

  formatFloat(value: number): string {
    return this._decimalPipe.transform(value, '1.0-2');
  }

  private _visualizeData(): void {
    this.cpuLoadChart = {
      metrics: [
        { name: 'Percentage' },
      ],
      attributes: [{ name: 'CPU' }],
      series: [{
        data: this.trainingSummary.resources.cpuLoad.map((load): ChartData.DataPoint => {
          return {
            values: [
              load.value,
            ],
            attributes: [load.name],
          };
        }),
      }],
    };
    this.cpuLoadLogChart = {
      metrics: [
        { name: 'Percentage' },
      ],
      attributes: [{ name: 'CPU' }],
      series: [{
        data: this._preparePerformanceLog(this.trainingSummary.resources.cpuLoadLog),
      }],
    };
    this.gpuLoadChart = {
      metrics: [
        { name: 'Percentage' },
      ],
      attributes: [{ name: 'GPU' }],
      series: [{
        data: this.trainingSummary.resources.gpuLoad.map((load): ChartData.DataPoint => {
          return {
            values: [
              load.value,
            ],
            attributes: [load.name],
          };
        }),
      }],
    };
    this.gpuLoadLogChart = {
      metrics: [
        { name: 'Percentage' },
      ],
      attributes: [{ name: 'GPU' }],
      series: [{
        data: this._preparePerformanceLog(this.trainingSummary.resources.gpuLoadLog),
      }],
    };
    this.memoryUsageChart = {
      metrics: [
        { name: 'GB' },
      ],
      attributes: [{ name: 'CPU' }],
      series: [{
        data: this.trainingSummary.resources.memoryUsage.map((load): ChartData.DataPoint => {
          return {
            values: [
              load.value,
            ],
            attributes: [load.name],
          };
        }),
      }],
    };
    this.memoryUsageLogChart = {
      metrics: [
        { name: 'Percentage' },
      ],
      attributes: [{ name: 'Memory' }],
      series: [{
        data: this._preparePerformanceLog(this.trainingSummary.resources.memoryUsageLog),
      }],
    };
    this.iterationScoreChart = {
      metrics: [
        { name: 'Iteration' },
        { name: 'AuROC' },
      ],
      attributes: [{ name: 'Model Type' }],
      series: [{
        data: this.trainingSummary.iterations.map((iteration): ChartData.DataPoint => {
          const classifierStageInfo = iteration.hyperParameters.find(_ => _.stage === ITabularModel.PipelineStage.MODEL_PRIMITIVES);
          return {
            values: [
              iteration.index,
              iteration.summary.areaUnderROC,
            ],
            attributes: [classifierStageInfo ? classifierStageInfo.technique : 'Unknown'],
            referenceId: String(iteration.index),
          };
        }),
      }],
    };
  }

  private _preparePerformanceLog(usageLog: IModelTrainSummary.ResourceLog[]): ChartData.DataPoint[] {
    return _.range(-50, 0).map((i): IModelTrainSummary.ResourceLog => {
      return {
        value: 0,
        iteration: i,
      };
    }).concat(usageLog).slice(-50).map((value): ChartData.DataPoint => {
      return {
        values: [
          value.value,
        ],
        attributes: [value.iteration],
        referenceId: String(value.iteration),
      };
    });
  }
}
