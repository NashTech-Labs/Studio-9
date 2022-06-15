import { ChangeDetectionStrategy, Component, Input, OnChanges, SimpleChanges } from '@angular/core';

import { ITabularModel } from '../train/model.interface';

import { diaaConfig } from './diaa.config';
import { IDIAA } from './diaa.interface';

@Component({
  selector: 'diaa-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dl class="dl-horizontal train-dl-summary">
      <dt>
        {{diaaConfig.diaaObjectives.maximizingLabels[diaa.diaaObjective]}}
      </dt>
      <dd>
        {{diaa.objective.metrics | apply: _drawObjectiveMetrics }}
        <ng-template [ngIf]="diaa.objective.airDecile">(decile {{diaa.objective.airDecile}}0%)</ng-template>
      </dd>

      <dt>DIA constraints</dt>
      <dd>
        <ul>
          <li *ngFor="let row of diaa.diaConstraints">
            {{diaaConfig.constraintMetric.labels[row.metric]}}
            {{diaaConfig.constraintOperator.labels[row.operator]}}
            {{row.value | number: '1.0-3'}}
          </li>
        </ul>
      </dd>
      <ng-template [ngIf]="diaa.diaaObjective === diaaConfig.diaaObjectives.values.AIR">
        <dt>AIR Mode</dt>
        <dd [ngSwitch]="diaa.airSpecification.cutOffMode">
          <ng-template [ngSwitchCase]="'decile'">Decile: {{diaa.airSpecification.decile | diaaDecileRange}}</ng-template>
          <ng-template [ngSwitchCase]="'percentile'">Percentile: up to {{diaa.airSpecification.percentile}}%</ng-template>
          <ng-template [ngSwitchCase]="'probability'">Probability of Positive Response: >=
            {{diaa.airSpecification.probability | number: '1.0-3'}}
          </ng-template>
        </dd>
      </ng-template>
    </dl>

    <hr/>
    <dl *ngIf="diaa.summary"
      class="dl-horizontal train-dl-summary">

      <dt>AUC (alt./orig.)</dt>
      <dd>
        <diaa-summary-value
          [value]="model.summary.areaUnderROC"
          [originalValue]="originalModel.summary.areaUnderROC"
        ></diaa-summary-value>
      </dd>

      <dt>KS (alt./orig.)</dt>
      <dd>
        <diaa-summary-value
          [value]="model.summary.KS"
          [originalValue]="originalModel.summary.KS"
        ></diaa-summary-value>
      </dd>

      <dt>
        {{diaaConfig.diaaObjectives.metricLabels[diaa.diaaObjective]}} (alt./orig.)
      </dt>
      <dd>
        <table class="table table-bordered table-sm diaa-air-table">
          <thead>
          <tr>
            <th>Group</th>
            <th *ngFor="let row of diaa.altSummary[diaaConfig.datasetRef.values.INPUT]"
              [ngClass]="{'locked': row.airDecile && row.airDecile !== diaa.objective.airDecile}"
              [ngSwitch]="diaa.airSpecification.cutOffMode">
              <span *ngSwitchCase="'decile'">{{row.airDecile | apply: _decileHeader}}</span>
              <span *ngSwitchDefault="">Value</span>
            </th>
          </tr>
          </thead>
          <tbody>
          <tr *ngFor="let metric of _metrics">
            <td>{{metric.label}}</td>
            <td *ngFor="let row of diaa.altSummary[diaaConfig.datasetRef.values.INPUT]; let i = index"
              [ngClass]="{
                'locked': row.airDecile && row.airDecile !== diaa.objective.airDecile,
                'di-warning':  row | apply : _showWarning : metric.key
              }"
            >
              <diaa-summary-value
                [value]="row[metric.key]"
                [originalValue]="diaa.summary[diaaConfig.datasetRef.values.INPUT][i][metric.key]"
              ></diaa-summary-value>
            </td>
          </tr>
          </tbody>
        </table>
      </dd>

      <ng-template [ngIf]="diaa.altSummary[diaaConfig.datasetRef.values.HOLD_OUT]">
        <dt>KS (Hold-Out/orig.)</dt>
        <dd>
          <diaa-summary-value
            [value]="model.holdOutSummary.KS"
            [originalValue]="originalModel.summary.KS"
          ></diaa-summary-value>
        </dd>

        <dt>
          {{diaaConfig.diaaObjectives.metricLabels[diaa.diaaObjective]}} (Hold-Out/orig.)
        </dt>
        <dd>
          <table class="table table-bordered table-sm diaa-air-table">
            <thead>
            <tr>
              <th>Group</th>
              <th *ngFor="let row of diaa.altSummary[diaaConfig.datasetRef.values.HOLD_OUT]"
                [ngClass]="{'locked': row.airDecile && row.airDecile !== diaa.objective.airDecile}"
                [ngSwitch]="diaa.airSpecification.cutOffMode">
                <span *ngSwitchCase="'decile'">{{row.airDecile | apply: _decileHeader}}</span>
                <span *ngSwitchDefault="">Value</span>
              </th>
            </tr>
            </thead>
            <tbody>
            <tr *ngFor="let metric of _metrics">
              <td>{{metric.label}}</td>
              <td *ngFor="let row of diaa.altSummary[diaaConfig.datasetRef.values.HOLD_OUT]; let i = index"
                [ngClass]="{
                'locked': row.airDecile && row.airDecile !== diaa.objective.airDecile,
                'di-warning':  row | apply : _showWarning : metric.key
              }"
              >
                <diaa-summary-value
                  [value]="row[metric.key]"
                  [originalValue]="diaa.summary[diaaConfig.datasetRef.values.INPUT][i][metric.key]"
                ></diaa-summary-value>
              </td>
            </tr>
            </tbody>
          </table>
        </dd>
      </ng-template>

      <ng-template [ngIf]="diaa.altSummary[diaaConfig.datasetRef.values.OUT_OF_TIME]">
        <dt>KS (Out-of-Time/orig.)</dt>
        <dd>
          <diaa-summary-value
            [value]="model.holdOutSummary.KS"
            [originalValue]="originalModel.summary.KS"
          ></diaa-summary-value>
        </dd>

        <dt>
          {{diaaConfig.diaaObjectives.metricLabels[diaa.diaaObjective]}} (Out-of-Time/orig.)
        </dt>
        <dd>
          <table class="table table-bordered table-sm diaa-air-table">
            <thead>
            <tr>
              <th>Group</th>
              <th *ngFor="let row of diaa.altSummary[diaaConfig.datasetRef.values.OUT_OF_TIME]"
                [ngClass]="{'locked': row.airDecile && row.airDecile !== diaa.objective.airDecile}"
                [ngSwitch]="diaa.airSpecification.cutOffMode">
                <span *ngSwitchCase="'decile'">{{row.airDecile | apply: _decileHeader}}</span>
                <span *ngSwitchDefault="">Value</span>
              </th>
            </tr>
            </thead>
            <tbody>
            <tr *ngFor="let metric of _metrics">
              <td>{{metric.label}}</td>
              <td *ngFor="let row of diaa.altSummary[diaaConfig.datasetRef.values.OUT_OF_TIME]; let i = index"
                [ngClass]="{
                'locked': row.airDecile && row.airDecile !== diaa.objective.airDecile,
                'di-warning':  row | apply : _showWarning : metric.key
              }"
              >
                <diaa-summary-value
                  [value]="row[metric.key]"
                  [originalValue]="diaa.summary[diaaConfig.datasetRef.values.INPUT][i][metric.key]"
                ></diaa-summary-value>
              </td>
            </tr>
            </tbody>
          </table>
        </dd>
      </ng-template>
    </dl>
  `,
})

export class DIAASummaryComponent implements OnChanges {
  @Input() diaa: IDIAA;
  @Input() model: ITabularModel;
  @Input() originalModel: ITabularModel;

  readonly diaaConfig = diaaConfig;

  _metrics: {
    metric: IDIAA.ObjectiveMetric;
    key: string;
    label: string;
  }[];

  ngOnChanges(changes: SimpleChanges): void {
    this._metrics = (<IDIAA.ObjectiveMetric[]> this.diaaConfig.objectiveMetric.list)
      .filter(metric => {
        return !!this.diaa.protectedGroupsColumnMapping[metric.toLowerCase()];
      })
      .map(metric => {
        return {
          metric,
          key: metric.toLowerCase(),
          label: this.diaaConfig.objectiveMetric.labels[metric],
        };
      });
  }

  _decileHeader = (decile: number) => {
    if (decile) {
      return `Decile ${decile * 10}% value`;
    }

    return 'Value';
  };

  _drawObjectiveMetrics = (metrics: IDIAA.ObjectiveMetric[]): string => {
    return metrics.map(_ => diaaConfig.objectiveMetric.labels[_]).join(', ');
  };

  _showWarning = (row: IDIAA.SummaryRow, metric: string): boolean => {
    return (this.diaa.diaaObjective === diaaConfig.diaaObjectives.values.AIR)
      ? row[metric] < 0.9
      : row[metric] < -30;
  };
}
