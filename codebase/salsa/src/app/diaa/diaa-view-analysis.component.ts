import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';

import { Observable } from 'rxjs/Observable';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { ITableColumnExt } from '../tables/table.interface';
import { IModelColumn, ITabularTrainModel } from '../train/model.interface';

import { diaaConfig } from './diaa.config';
import { IDIAA, IDIAARun } from './diaa.interface';
import { DIAAService } from './diaa.service';

@Component({
  selector: 'diaa-view-analysis',
  template: `
    <form [formGroup]="form">
      <div class="row">
        <div class="col-lg-6">
          <div class="panel">
            <div class="panel-body">
              <label>{{diaaConfig.diaaObjectives.metricLabels[diaa.diaaObjective]}} to maximize</label>
              <table class="table table-bordered table-sm diaa-goal-table">
                <thead>
                <tr>
                  <th>Group</th>
                  <th *ngFor="let row of diaa.summary[diaaConfig.datasetRef.values.INPUT]"
                    [ngClass]="{ 'locked': row.airDecile && form.value.objective.airDecile && form.value.objective.airDecile !== row.airDecile }"
                    [ngSwitch]="diaa.airSpecification.cutOffMode">
                    <span *ngSwitchCase="'decile'">{{row.airDecile | apply: _decileHeader}}</span>
                    <span *ngSwitchDefault="">Value</span>
                  </th>
                </tr>
                </thead>
                <tbody>
                <tr *ngFor="let goal of _optimizationGoals">
                  <th>{{goal.name}}</th>
                  <ng-template [ngIf]="diaa.diaaObjective === diaaConfig.diaaObjectives.values.AIR">
                    <td *ngFor="let row of diaa.summary[diaaConfig.datasetRef.values.INPUT]"
                      [ngClass]="{
                      'locked': row.airDecile && form.value.objective.airDecile && form.value.objective.airDecile !== row.airDecile,
                      'di-warning': row[goal.key] < 0.9
                    }"
                      (click)="_toggleObjective(goal.metric, row[goal.key], row.airDecile)"
                    >
                      <i class="glyphicon"
                        [ngClass]="(row.airDecile === form.value.objective.airDecile && form.value.objective.metrics.indexOf(goal.metric) >= 0)
                        ? 'glyphicon-check'
                        : 'glyphicon-unchecked'"
                      ></i>
                      {{row[goal.key] | number: '1.0-3'}}
                    </td>
                  </ng-template>
                  <ng-template [ngIf]="diaa.diaaObjective === diaaConfig.diaaObjectives.values.SMD">
                    <td *ngFor="let row of diaa.summary[diaaConfig.datasetRef.values.INPUT]"
                      [ngClass]="{'di-warning': row[goal.key] < -30}"
                      (click)="_toggleObjective(goal.metric, row[goal.key])"
                    >
                      <i class="glyphicon"
                        [ngClass]="(form.value.objective.metrics.indexOf(goal.metric) >= 0)
                          ? 'glyphicon-check'
                          : 'glyphicon-unchecked'"
                      ></i>
                      {{row[goal.key] | number: '1.0-3'}}
                    </td>
                  </ng-template>
                </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <div class="col-lg-6">
          <div class="panel">
            <div class="panel-body">
              <label>Specify Constraints</label>
              <div class="row pt5"
                *ngFor="let controlGroup of form.controls['constraints']['controls']">
                <div class="col-md-3 col-xs-6">
                  <app-check
                    [label]="diaaConfig.constraintMetric.labels[controlGroup.value.metric] || controlGroup.value.metric"
                    [value]="true"
                    [control]="controlGroup.controls.selected"
                  ></app-check>
                </div>
                <div class="col-md-3 col-xs-6">
                  <app-select [control]="controlGroup.controls.operator"
                    [disabled]="!controlGroup.controls.selected.value"
                    [options]="operators"
                  ></app-select>
                </div>
                <div class="col-md-6 col-xs-12">
                  <app-input
                    [type]="'number'"
                    [disabled]="!controlGroup.controls.selected.value"
                    [label]="'Value'"
                    [control]="controlGroup.controls.value"></app-input>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div class="flex-col" [adaptiveHeight]="{minHeight: 450}">
        <table-view-embed [editMode]="true"
          [columnOptions]="form.controls['responseColumns'].value.concat(form.controls['predictorColumns'].value)"
          [lockedColumns]="[model.responseColumn.name]"
          [id]="model.pipeline.input" (columnOptionsChange)="_onColumnOptionsValue($event)"></table-view-embed>
      </div>
    </form>
  `,
})
export class DIAAViewAnalysisComponent implements OnChanges {
  readonly config = config;
  readonly diaaConfig = diaaConfig;

  @Input() diaa: IDIAA;
  @Input() model: ITabularTrainModel = null;

  readonly form: FormGroup;
  readonly operators: AppSelectOptionData[] = AppSelectOptionData.fromList(
    this.diaaConfig.constraintOperator.list,
    this.diaaConfig.constraintOperator.labels,
  );
  _optimizationGoals: {
    key: string,
    metric: IDIAA.ObjectiveMetric,
    name: string,
  }[] = [];

  constructor(
    private service: DIAAService,
  ) {
    this.form = new FormGroup({
      objective: new FormGroup({
        airDecile: new FormControl(null),
        metrics: new FormControl([], [Validators.required, Validators.minLength(1)]),
      }),
      constraints: new FormArray([]),
      responseColumns: new FormControl([], [Validators.required, Validators.minLength(1), Validators.maxLength(1)]),
      predictorColumns: new FormControl([], [Validators.required, Validators.minLength(1)]),
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ('model' in changes && this.model) {
      this.form.patchValue({
        objective: {
          airDecile: null,
          metrics: [],
        },
      });

      const constraintsControl = (<FormArray> this.form.controls.constraints);
      while (constraintsControl.length) {
        constraintsControl.removeAt(0);
      }
      this._optimizationGoals = [];
      [
        [IDIAA.ConstraintMetric.AUROC, 'areaUnderROC'],
        [IDIAA.ConstraintMetric.KS, 'KS'],
      ].forEach(([metric, key]) => {
        constraintsControl.push(new FormGroup({
          selected: new FormControl(metric === IDIAA.ConstraintMetric.KS),
          metric: new FormControl(metric, Validators.required),
          operator: new FormControl(IDIAA.ConstraintOperator.T05, Validators.required),
          value: new FormControl(this.model.result && this.model.result.summary
            ? this.model.result.summary[key].toFixed(3)
            : null,
          ),
        }));
      });

      Object.keys(IDIAA.ObjectiveMetric)
        .filter(key => !!this.diaa.protectedGroupsColumnMapping[key.toLowerCase()])
        .forEach((metric: IDIAA.ObjectiveMetric) => {
          const key = metric.toLowerCase();
          constraintsControl.push(new FormGroup({
            selected: new FormControl(true),
            metric: new FormControl(metric, Validators.required),
            operator: new FormControl(IDIAA.ConstraintOperator.GT, Validators.required),
            value: new FormControl(this.diaa.diaaObjective === this.diaaConfig.diaaObjectives.values.AIR ? 0.9 : -30),
          }));
          this._optimizationGoals.push({
            key,
            metric,
            name: this.diaaConfig.objectiveMetric.labels[metric],
          });
        });

      this.form.patchValue({
        responseColumns: [this.model.responseColumn].map(column => {
          return Object.assign({ covariateType: config.model.column.covariate.values.RESPONSE }, column);
        }),
        predictorColumns: this.model.predictorColumns.map(column => {
          return Object.assign({ covariateType: config.model.column.covariate.values.PREDICTOR }, column);
        }),
      });
    }
  }

  submit(): Observable<IDIAA> {
    let data = <IDIAARun> this.form.value;
    Object.assign(data, {
      diaConstraints: this.form.value.constraints.filter(constraint => constraint.selected).map((constraint): IDIAA.Constraint => {
        return {
          metric: constraint.metric,
          value: constraint.value,
          operator: constraint.operator,
        };
      }),
      predictorColumns: this.form.value.predictorColumns.map((item: any): IModelColumn => {
        return {
          name: item.name,
          displayName: item.displayName,
          dataType: item.dataType,
          variableType: item.variableType,
        };
      }),
      responseColumn: this.model.responseColumn, // can't change
    });

    return this.service.run(this.diaa.id, data);
  }

  _onColumnOptionsValue(data: ITableColumnExt[]) {
    const columnsData: ITableColumnExt[] = data || [];
    let predictorColumns = [];
    let responseColumns = [];

    columnsData.forEach(item => {
      if (item.covariateType === config.model.column.covariate.values.RESPONSE) {
        responseColumns.push(item);
      }

      if (item.covariateType === config.model.column.covariate.values.PREDICTOR) {
        predictorColumns.push(item);
      }
    });

    this.form.controls['responseColumns'].setValue(responseColumns);
    this.form.controls['predictorColumns'].setValue(predictorColumns);
  }

  _toggleObjective(metric: IDIAA.ObjectiveMetric, value: number, decile?: number) {
    const objectiveFormGroup = <FormGroup> this.form.controls['objective'];
    const currentMetrics: IDIAA.ObjectiveMetric[] = objectiveFormGroup.controls['metrics'].value || [];

    if (decile && currentMetrics.length) {
      const currentDecile = objectiveFormGroup.controls['airDecile'].value;
      if (currentDecile !== decile) {
        return; // throw?
      }
    }

    if (currentMetrics.indexOf(metric) >= 0) {
      const newMetrics = currentMetrics.filter(_ => _ !== metric);
      objectiveFormGroup.controls['metrics'].setValue(newMetrics);
      if (!newMetrics.length) {
        objectiveFormGroup.controls['airDecile'].setValue(null);
      }
      this._toggleConstraintValue(metric, this.diaa.diaaObjective === this.diaaConfig.diaaObjectives.values.AIR ? 0.9 : -30);
    } else {
      objectiveFormGroup.controls['metrics'].setValue([metric, ...currentMetrics]);
      objectiveFormGroup.controls['airDecile'].setValue(decile);
      this._toggleConstraintValue(metric, value);
    }
  }

  _decileHeader = (decile: number) => {
    if (decile) {
      return `Decile ${decile * 10}% value`;
    }

    return 'Value';
  };

  private _toggleConstraintValue(metric: IDIAA.ObjectiveMetric, value: number) {
    const constraintsControl = (<FormArray> this.form.controls.constraints);
    constraintsControl.controls.forEach((group: FormGroup) => {
      if (group.controls['metric'].value === metric) {
        group.controls['value'].setValue(value.toFixed(3));
      }
    });
  }
}
