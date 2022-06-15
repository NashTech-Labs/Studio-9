import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';

import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';

import { TObjectId } from '../core/interfaces/common.interface';
import { IExperimentFull } from '../experiments/experiment.interfaces';
import { ExperimentService } from '../experiments/experiment.service';
import { ITable } from '../tables/table.interface';
import { TableService } from '../tables/table.service';
import { ITabularModel, ITabularTrainPipeline, ITabularTrainResult } from '../train/model.interface';
import { ModelService } from '../train/model.service';
import { ReactiveLoader } from '../utils/reactive-loader';

import { diaaConfig } from './diaa.config';
import { IDIAA } from './diaa.interface';

@Component({
  selector: 'diaa-specification',
  template: `
    <div *ngIf="!diaa.airSpecification" class="text-center">
      Model has no AIR settings
    </div>
    <app-spinner [visibility]="_tableLoader.active | async"></app-spinner>
    <dl *ngIf="_tableLoader.loaded" class="dl-horizontal train-dl-summary">
      <dt>Is Higher Score of Response Variable Favorable?</dt>
      <dd>{{diaa?.higherModelScoreFavorable ? 'Yes' : 'No'}}</dd>
      <dt>Objective Mode</dt>
      <dd>
        {{diaaConfig.diaaObjectives.metricLabels[diaa.diaaObjective]}}
      </dd>
      <ng-template [ngIf]="diaa.diaaObjective === diaaConfig.diaaObjectives.values.AIR">
        <dt>Mode</dt>
        <dd *ngIf="diaa.airSpecification" [ngSwitch]="diaa.airSpecification.cutOffMode">
          <ng-template [ngSwitchCase]="'decile'">Decile: {{diaa.airSpecification.decile | diaaDecileRange }}
          </ng-template>
          <ng-template [ngSwitchCase]="'percentile'">Percentile: up to {{diaa.airSpecification.percentile}}%
          </ng-template>
          <ng-template [ngSwitchCase]="'probability'">Probability of Positive Response: >=
            {{diaa.airSpecification.probability | number: '1.0-3'}}
          </ng-template>
        </dd>
      </ng-template>
      <dt>Columns</dt>
      <dd>
        <table class="table table-bordered table-sm">
          <thead>
          <tr>
            <th>Group</th>
            <th>Column</th>
          </tr>
          </thead>
          <tbody>
          <tr *ngFor="let metric of _metrics">
            <td>{{metric.label}}</td>
            <td>{{diaa.protectedGroupsColumnMapping[metric.key] | tableColumnDisplayName: inputTable}}</td>
          </tr>
          </tbody>
        </table>
      </dd>
    </dl>
  `,
})

export class DIAASpecificationComponent implements OnChanges {
  readonly diaaConfig = diaaConfig;
  inputTable: ITable;
  _metrics: {
    metric: IDIAA.ObjectiveMetric;
    key: string;
    label: string;
  }[];
  @Input() diaa: IDIAA;
  readonly _tableLoader: ReactiveLoader<ITable, TObjectId>;

  constructor(
    private models: ModelService,
    private tables: TableService,
    private experimentService: ExperimentService,
  ) {
    this._tableLoader = new ReactiveLoader<ITable, TObjectId>(id => {
      return this.models.get(id).flatMap((model: ITabularModel) => {
        return model.experimentId
          ? this.experimentService.get(model.experimentId)
              .flatMap((experiment: IExperimentFull<ITabularTrainPipeline, ITabularTrainResult>) => {
                return experiment.result.output ? this.tables.get(experiment.result.output) : Observable.of(null);
              })
          : Observable.of(null);
      });
    });

    this._tableLoader.subscribe((table: ITable) => {
      this.inputTable = table;
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    this._tableLoader.load(this.diaa.modelId);
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
}
