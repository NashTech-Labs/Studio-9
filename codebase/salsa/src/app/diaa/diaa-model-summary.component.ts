import { ChangeDetectionStrategy, Component, Input, OnChanges, SimpleChanges } from '@angular/core';

import { diaaConfig } from './diaa.config';
import { IDIAA } from './diaa.interface';

@Component({
  selector: 'diaa-model-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div *ngIf="!summary" class="text-center">
      Model has no summary
    </div>
    <ng-template [ngIf]="summary">
      <dl *ngFor="let key of diaaConfig.datasetRef.list"
        [hidden]="!summary[key]?.length"
        class="dl-horizontal train-dl-summary">
        <dt>
          {{diaaConfig.diaaObjectives.metricLabels[diaa.diaaObjective]}} {{key | apply: _datasetRefHeader}}
        </dt>
        <dd>
          <table class="table table-bordered table-sm">
            <thead>
            <tr>
              <th>Group</th>
              <th *ngFor="let row of summary[key]"
                [ngSwitch]="diaa.airSpecification.cutOffMode">
                <span *ngSwitchCase="'decile'">{{row.airDecile | apply: _decileHeader}}</span>
                <span *ngSwitchDefault="">Value</span>
              </th>
            </tr>
            </thead>
            <tbody>
            <tr *ngFor="let metric of _metrics">
              <td>{{metric.label}}</td>
              <td *ngFor="let row of summary[key]">
                {{row[metric.key] | number: '1.0-3'}}
              </td>
            </tr>
            </tbody>
          </table>
        </dd>
      </dl>
    </ng-template>
  `,
})

export class DIAAAIRSummaryComponent implements OnChanges {
  readonly diaaConfig = diaaConfig;

  @Input() diaa: IDIAA;
  @Input() summary: IDIAA.Summary;

  _metrics: {
    metric: IDIAA.ObjectiveMetric,
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

  _datasetRefHeader = (key: IDIAA.DatasetRef): string => {
    switch (key) {
      case IDIAA.DatasetRef.HOLD_OUT:
        return '(Hold-Out)';
      case IDIAA.DatasetRef.OUT_OF_TIME:
        return '(Out-of-Time)';
      default:
        return '';
    }
  }

}
