import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

import { IModelEvaluationSummary } from './model.interface';

@Component({
  selector: 'model-evaluation-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ng-template [ngIf]="summary">
      <dl class="dl-horizontal train-dl-summary">
        <ng-template [ngIf]="summary?.rmse">
          <dt>RMSE</dt>
          <dd>{{ summary.rmse | number: '1.0-3' }}</dd>
        </ng-template>
        <ng-template [ngIf]="summary?.r2">
          <dt>r<sup>2</sup></dt>
          <dd>{{ summary.r2 | number: '1.0-3'}}</dd>
        </ng-template>
        <ng-template [ngIf]="summary?.MAPE">
          <dt>MAPE</dt>
          <dd>{{ summary.MAPE | number: '1.0-3'}}</dd>
        </ng-template>
        <ng-template [ngIf]="summary?.KS">
          <dt>Kolmogorov-Smirnoff Stat. (KS)</dt>
          <dd><i class="glyphicon"
              [ngClass]="{'glyphicon-arrow-up': summary?.KS >= referenceSummary?.KS * validationThreshold,'glyphicon-arrow-down': summary?.KS < referenceSummary?.KS * validationThreshold}"
            ></i>{{ summary.KS | number: '1.0-3' }}
          </dd>
        </ng-template>
        <ng-template [ngIf]="summary?.confusionMatrix">
          <dt>Confusion matrix</dt>
          <dd>
            <table class="table table-bordered table-sm">
              <thead>
              <tr>
                <th>Value</th>
                <th>Actual count</th>
                <th>Predicted count</th>
                <th>Precision</th>
                <th>Recall</th>
                <th>F1 Score</th>
                <th>Accuracy</th>
              </tr>
              </thead>
              <tbody>
              <tr *ngFor="let row of summary.confusionMatrix">
                <td>{{row.className}}</td>
                <td>{{row.truePositive + row.falseNegative}}</td>
                <td>{{row.truePositive + row.falsePositive}}</td>
                <td>{{row | apply: _precisionMetric | number: '1.3-3'}}</td>
                <td>{{row | apply: _recallMetric | number: '1.3-3'}}</td>
                <td>{{row | apply: _F1Metric | number: '1.3-3'}}</td>
                <td>{{row | apply: _accuracyMetric | percent: '1.2-2'}}</td>
              </tr>
              </tbody>
            </table>
          </dd>
          <dd>
            <confusion-chart *ngIf="summary.confusionMatrix?.length === 2"
              [data]="summary.confusionMatrix"></confusion-chart>
          </dd>
        </ng-template>
      </dl>
    </ng-template>
  `,
  styles: [`
    .glyphicon-arrow-up { color: green; }
    .glyphicon-arrow-down { color: red; }
  `],
})

export class ModelEvaluationSummaryComponent {
  @Input() summary: IModelEvaluationSummary;
  @Input() referenceSummary: IModelEvaluationSummary;
  @Input() validationThreshold: number;

  _F1Metric = (row: IModelEvaluationSummary.ConfusionMatrixRow): number => {
    const TPx2 = row.truePositive * 2;
    return TPx2 > 0
      ? TPx2 / (TPx2 + row.falsePositive + row.falseNegative)
      : 0;
  };

  _precisionMetric = (row: IModelEvaluationSummary.ConfusionMatrixRow): number => {
    return row.truePositive > 0
      ? row.truePositive / (row.truePositive + row.falsePositive)
      : 0;
  };

  _recallMetric = (row: IModelEvaluationSummary.ConfusionMatrixRow): number => {
    return row.truePositive > 0
      ? row.truePositive / (row.truePositive + row.falseNegative)
      : 0;
  };

  _accuracyMetric = (row: IModelEvaluationSummary.ConfusionMatrixRow): number => {
    return (row.truePositive + row.trueNegative) > 0
      ? (row.truePositive + row.trueNegative) / (row.truePositive + row.falsePositive + row.trueNegative + row.falseNegative)
      : 0;
  };
}
