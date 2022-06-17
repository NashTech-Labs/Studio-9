import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';

import * as _ from 'lodash';
import 'rxjs/add/observable/forkJoin';
import 'rxjs/add/observable/of';
import 'rxjs/add/operator/mergeMap';
import { Observable } from 'rxjs/Observable';

import config from '../config';
import { TObjectId } from '../core/interfaces/common.interface';
import { IExperimentFull } from '../experiments/experiment.interfaces';
import { ExperimentService } from '../experiments/experiment.service';
import { ITable, ITableColumnStats } from '../tables/table.interface';
import { TableService } from '../tables/table.service';
import { ITabularModel, ITabularTrainPipeline, ITabularTrainResult } from '../train/model.interface';
import { ModelService } from '../train/model.service';
import { ReactiveLoader } from '../utils/reactive-loader';

import { TabularDataRequest } from './visualize.interface';

@Component({
  selector: 'chart-generators-selector',
  template: `
    <app-spinner [visibility]="_loader.active | async"></app-spinner>
    <ng-template [ngIf]="_loader.loaded">
      <div *ngFor="let column of model.predictorColumns" class="bordered hover-bar">
        <!--{{filter | json}}-->
        <chart-generator
          [stats]="stats"
          [table]="table"
          [value]="getGenerator(column.name)"
          [columnName]="column.name"
          (valueChange)="setGenerator(column.name, $event)"></chart-generator>
        <hr />
        <app-check [label]="'Show on chart'"
          [checked]="chartGenerators.indexOf(column.name) > -1"
          (checkedChange)="toggleChartGenerator(column.name, $event)"></app-check>
      </div>
    </ng-template>
  `,
})
export class ChartGeneratorsSelectorComponent implements OnChanges {
  @Input() generators: TabularDataRequest.Generator[];
  @Output() generatorsChange: EventEmitter<TabularDataRequest.Generator[]> = new EventEmitter<TabularDataRequest.Generator[]>();

  @Input() chartGenerators: string[];
  @Output() chartGeneratorsChange: EventEmitter<string[]> = new EventEmitter<string[]>();

  @Input() label: string;
  @Input() modelId: TObjectId;
  @Input() disabled: boolean = false;

  model: ITabularModel;
  table: ITable;
  stats: ITableColumnStats[];

  readonly config = config;
  readonly _loader: ReactiveLoader<[ITabularModel, ITable, ITableColumnStats[]], TObjectId>;

  constructor(
    private models: ModelService,
    private tables: TableService,
    private experimentService: ExperimentService,
  ) {
    this._loader = new ReactiveLoader((modelId: TObjectId) => {
      return this.models.get(modelId).flatMap((model) => {
        return model.experimentId
          ? this.experimentService.get(model.experimentId)
            .flatMap((experiment: IExperimentFull<ITabularTrainPipeline, ITabularTrainResult>) => {
              return Observable.forkJoin(
                Observable.of(model),
                this.tables.get(experiment.pipeline.input),
                this.tables.getStatistic(experiment.pipeline.input).map(_ => _.stats),
              );
            })
          : Observable.of(null);
      });
    });

    this._loader.subscribe(([model, table, stats]) => {
      this.model = model;
      this.table = table;
      this.stats = stats;
      this.initGenerators();
    });
  }

  ngOnChanges(changes) {
    if ('modelId' in changes) {
      this._loader.load(this.modelId);
    }
  }

  initGenerators() {
    this.generators = this.model.predictorColumns.map((column): TabularDataRequest.Generator => {
      const isCategorical = column.variableType !== ITable.ColumnVariableType.CONTINUOUS;
      const stats = this.stats.find(_ => _.columnName === column.name);
      const oldValue = this.generators.find(_ => _.columnName === column.name);

      if (isCategorical) {
        const values = oldValue ? (<TabularDataRequest.CategoricalGenerator> oldValue).values : [stats.mostFrequentValue];
        return {
          columnName: column.name,
          type: 'categorical',
          values: values,
        };
      } else {
        const {min, max} = (<TabularDataRequest.ContinuousGenerator> oldValue) || stats;
        return {
          columnName: column.name,
          type: 'continuous',
          min: min,
          max: max,
          steps: oldValue ? (<TabularDataRequest.ContinuousGenerator> oldValue).steps : 1,
        };
      }
    });
    this.generatorsChange.emit(this.generators);
  }

  setGenerator(name: string, value: TabularDataRequest.Generator) {
    const index = this.generators.findIndex(_ => _.columnName === name);
    if (index >= 0 && !_.isEqual(this.generators[index], value)) {
      this.generators = this.generators.slice();
      this.generators[index] = value;
      this.generatorsChange.emit(this.generators);
    }
  }

  getGenerator(name: string) {
    return this.generators.find(_ => _.columnName === name);
  }

  toggleChartGenerator(columnName: string, checked: boolean): void {
    const value = this.chartGenerators.filter(_ => _ !== columnName);
    if (checked) {
      value.push(columnName);
    }
    this.chartGenerators = value;
    this.chartGeneratorsChange.emit(this.chartGenerators);
  }
}
