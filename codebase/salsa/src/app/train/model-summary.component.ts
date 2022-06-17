import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';

import * as _ from 'lodash';

import { BoxChartOptions } from '../charts/box-chart.component';
import { ChartData } from '../charts/chart-data.interface';
import { TTableValue } from '../tables/table.interface';

import { IModelEvaluationSummary, IModelSummary, ITabularModel, IVariableImportance } from './model.interface';
import { trainConfig } from './train.config';

// These two define importance graph coloring
const IMPORTANCE_GRAPH_DOMAIN = [
  IVariableImportance.Decision.CONFIRMED,
  IVariableImportance.Decision.REJECTED,
  IVariableImportance.Decision.TENTATIVE,
  IVariableImportance.Decision.SHADOW,
];
const IMPORTANCE_GRAPH_GAMMA = ['lime', 'red', 'yellow', 'blue'];


@Component({
  selector: 'model-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div *ngIf="!summary" class="text-center">
      Model has no summary
    </div>
    <ng-template [ngIf]="summary">
      <app-tabs *ngIf="holdOutSummary || outOfTimeSummary"
        [tabs]="TABS | map: _tabToLabelFn"
        [hiddenTabs]="hiddenTabs"
        [(active)]="activeTab">
        <div style="padding-top: 15px"></div>
      </app-tabs>

      <div class="flex-static" [hidden]="activeTab !== TAB_SUMMARY">
        <div *ngIf="summary.roc" style="width:100%;height:400px;">
          <canvas [roc-chart]="summary.roc"></canvas>
        </div>
        <dl class="dl-horizontal train-dl-summary">
          <ng-template [ngIf]="validationThreshold && (summary.areaUnderROC || summary.KS)">
            <dt>Validation Threshold</dt>
            <dd>{{validationThreshold}}</dd>
          </ng-template>
          <ng-template [ngIf]="summary.areaUnderROC">
            <dt>Area Under ROC</dt>
            <dd>{{summary.areaUnderROC | number: '1.0-3'}}</dd>
          </ng-template>
          <model-evaluation-summary [summary]="summary"></model-evaluation-summary>
          <ng-template [ngIf]="summary.predictors && summary.predictors.length">
            <dt>Coefficients</dt>
            <dd>
              <table class="table table-bordered table-sm" *ngIf="_parametricPredictors.length">
                <thead>
                  <tr>
                    <th>Variable</th>
                    <th>Estimate</th>
                    <th>Std. Error</th>
                    <th>T-Value</th>
                    <th>P-Value</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let row of _parametricPredictors">
                    <td>{{row.name}}</td>
                    <td>{{row.estimate | number: '1.0-3'}}</td>
                    <td>{{row.stdError | number: '1.0-3'}}</td>
                    <td>{{row.tvalue | number: '1.0-3'}}</td>
                    <td>{{row.pvalue | number: '1.0-3'}}</td>
                  </tr>
                </tbody>
              </table>
              <table class="table table-bordered table-sm" *ngIf="_treePredictors.length">
                <thead>
                  <tr>
                    <th>Variable</th>
                    <th>Importance</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let row of _treePredictors">
                    <td>{{row.name}}</td>
                    <td>{{row.importance | number: '1.0-3'}}</td>
                  </tr>
                </tbody>
              </table>
            </dd>
          </ng-template>
        </dl>
        <div
          *ngIf="summary?.variableImportance && variableImportanceChart && trainOptions?.variableImportance"
            style="width:100%;height:600px;">
          <box-chart [data]="variableImportanceChart" [options]="chartOptions"></box-chart>
        </div>
      </div>
      <div class="flex-static" [hidden]="activeTab !== TAB_HOLD_OUT_SUMMARY">
        <model-evaluation-summary [summary]="holdOutSummary"
          [referenceSummary]="summary"
          [validationThreshold]="validationThreshold"></model-evaluation-summary>
      </div>
      <div class="flex-static" [hidden]="activeTab !== TAB_OUT_OF_TIME_SUMMARY">
        <model-evaluation-summary [summary]="outOfTimeSummary"
          [referenceSummary]="summary"
          [validationThreshold]="validationThreshold"></model-evaluation-summary>
      </div>
    </ng-template>
  `,
})

export class ModelSummaryComponent implements OnChanges {
  @Input() summary: IModelSummary;
  @Input() holdOutSummary: IModelEvaluationSummary;
  @Input() outOfTimeSummary: IModelEvaluationSummary;
  @Input() trainOptions: ITabularModel.TrainOptions;
  @Input() validationThreshold: number;

  readonly TAB_SUMMARY = 0;
  readonly TAB_HOLD_OUT_SUMMARY = 1;
  readonly TAB_OUT_OF_TIME_SUMMARY = 2;
  readonly TABS = [
    { label: 'Model Summary', index: this.TAB_SUMMARY },
    { label: 'Hold-Out Summary', index: this.TAB_HOLD_OUT_SUMMARY },
    { label: 'Out-Of-Time Summary', index: this.TAB_OUT_OF_TIME_SUMMARY },
  ];
  activeTab = this.TAB_SUMMARY;
  hiddenTabs: number[] = [];
  variableImportanceChart: ChartData = null;
  chartOptions: BoxChartOptions;
  _parametricPredictors: IModelSummary.ParametricModelPredictor[] = [];
  _treePredictors: IModelSummary.TreeModelPredictor[] = [];

  ngOnChanges() {
    this.hiddenTabs = [this.TAB_SUMMARY, this.TAB_HOLD_OUT_SUMMARY, this.TAB_OUT_OF_TIME_SUMMARY];
    this.toggleTab(this.TAB_SUMMARY, !!this.summary);
    this.toggleTab(this.TAB_HOLD_OUT_SUMMARY, !!this.holdOutSummary);
    this.toggleTab(this.TAB_OUT_OF_TIME_SUMMARY, !!this.outOfTimeSummary);
    this.activeTab = this.TAB_SUMMARY;

    const domain = IMPORTANCE_GRAPH_DOMAIN;
    const labels = domain.map(_ => trainConfig.model.variableImportanceDecision.labels[_]);
    const range = _.range(domain.length);

    const classifier = Object.assign(function (attributes: TTableValue[]) {
      const value = <IVariableImportance.Decision> attributes[1];
      return domain.indexOf(value);
    }, {
      domain,
      range,
      labels,
    });

    this.chartOptions = {
      labelX: 'Variable',
      labelY: 'Importance',
      metricsMapping: {
        MIN: 0,
        MAX: 1,
        LOWER_QUARTILE: 2,
        UPPER_QUARTILE: 3,
        MEDIAN: 4,
      },
      groupByAttributes: [0],
      colorClassifier: classifier,
      colorGamma: IMPORTANCE_GRAPH_GAMMA,
    };
    if (this.summary) {
      if (this.summary.variableImportance) {
        const variableImportance = _.sortBy(this.summary.variableImportance, _ => _.median);
        this.variableImportanceChart = {
          series: [
            {
              title: 'Variable Importance',
              data: variableImportance.map((variableRow: IVariableImportance) => {
                return {
                  attributes: [variableRow.name, variableRow.decision],
                  values: [variableRow.min, variableRow.max, variableRow.lowerQuartile, variableRow.upperQuartile, variableRow.median],
                };
              }),
            },
          ],
          metrics: [
            { name: 'min' },
            { name: 'max' },
            { name: 'lowerQuartile' },
            { name: 'upperQuartile' },
            { name: 'median' },
          ],
          attributes: [
            { name: 'Variable' },
            { name: 'Final Decision' },
          ],
        };
      }
      if (this.summary.predictors) {
        this._parametricPredictors = this._getParametricPredictors(this.summary.predictors);
        this._treePredictors = this._getTreePredictors(this.summary.predictors);
      }
    }
  }

  toggleTab(index: number, show: boolean) {
    if (show) {
      this.hiddenTabs = this.hiddenTabs.filter(_ => _ !== index);
    } else if (this.hiddenTabs.indexOf(index) === -1) {
      this.hiddenTabs.push(index);
    }
  }

  _tabToLabelFn(item) {
    return item.label;
  }

  _getParametricPredictors(predictors: IModelSummary.Predictor[]): IModelSummary.ParametricModelPredictor[] {
    return <IModelSummary.ParametricModelPredictor[]> predictors.filter(_ => _.hasOwnProperty('estimate'));
  }

  _getTreePredictors(predictors: IModelSummary.Predictor[]): IModelSummary.TreeModelPredictor[] {
    return <IModelSummary.TreeModelPredictor[]> predictors.filter(_ => _.hasOwnProperty('importance'));
  }
}
