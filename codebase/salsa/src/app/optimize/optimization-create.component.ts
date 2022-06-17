import { Component, OnInit } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import 'rxjs/add/operator/do';
import { Observable } from 'rxjs/Observable';

import { IFlow } from '../compose/flow.interface';
import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { IBackendList, TObjectId } from '../core/interfaces/common.interface';
import { ITable, ITableColumn, TTableValue } from '../tables/table.interface';
import { TableService } from '../tables/table.service';
import { TableColumnSelectOptionsPipe } from '../tables/tables.pipes';
import { ActivityObserver } from '../utils/activity-observer';

import { IOptimization } from './optimization.interface';
import { OptimizationService } from './optimization.service';

@Component({
  selector: 'app-optimization-create',
  template: `
    <asset-operations [type]="config.asset.values.OPTIMIZATION" [selectedItems]="[]"></asset-operations>
    <div class="row">
      <div class="col-md-6">
        <app-input [label]="'Optimization Name'" [control]="form.controls['name']"></app-input>
      </div>
      <div class="col-md-6">
        <div class="pull-right">
          <button class="btn btn-md btn-apply" (click)="_savingObserver.observe(submit())"
            [disabled]="form.invalid || (_savingObserver.active | async)">
            Run
          </button>
        </div>
      </div>
    </div>
    <div class="row">
      <div class="col-md-6">
        <app-description [control]="form.controls['description']" [editMode]="true"></app-description>
      </div>
    </div>
    <div class="row">
      <div class="col-md-6 pull-right">
        <div class="panel">
          <div class="panel-body">
            <label>Optimization Type</label>
            <app-check
              *ngFor="let key of config.optimization.type.list"
              [type]="'radio'"
              [name]="'optimizationType'"
              [label]="config.optimization.type.labels[key]"
              [value]="key"
              [control]="form.controls['optimizationType']"
            ></app-check>
          </div>
        </div>
      </div>
      <div class="col-md-6">
        <library-selector
          [inputLabel]="'Select Source Model'"
          [available]="[config.asset.values.MODEL]"
          [value]="{id: form.controls['modelId'].value,entity: config.asset.values.MODEL}"
          (valueChange)="form.controls['modelId'].setValue($event.id);selectedModel = $event"
          [caption]="'Select Source Model'"></library-selector>
      </div>
      <div class="col-md-6">
        <app-input [label]="'Optimization Model Name'" [control]="form.controls['outputModelName']"></app-input>
      </div>
    </div>
    <ng-template [ngIf]="selectedModel">
      <form [formGroup]="form" class="brand-tab">
        <div class="row">
          <div class="col-md-6">
            <div class="panel">
              <div class="panel-body">
                <label>Goal</label>
                <app-check
                  *ngFor="let key of config.optimization.goal.list"
                  [type]="'radio'"
                  [name]="'goal'"
                  [label]="config.optimization.goal.labels[key]"
                  [value]="config.optimization.goal.values[key]"
                  [control]="form.controls['objectives']['controls'][0].controls.goal"
                ></app-check>
              </div>
            </div>
          </div>
          <ng-template [ngIf]="form.value.optimizationType === config.optimization.type.values.PREDICTOR_TUNING">
            <div class="col-md-6">
              <div class="panel">
                <div class="panel-body">
                  <label>Metric</label>
                  <app-check
                    *ngFor="let key of config.optimization.metric.list"
                    [type]="'radio'"
                    [name]="'metric'"
                    [label]="config.optimization.metric.labels[key]"
                    [value]="config.optimization.metric.values[key]"
                    [control]="form.controls['objectives']['controls'][0].controls.metric"
                  ></app-check>
                </div>
              </div>
            </div>
          </ng-template>
          <ng-template [ngIf]="form.value.optimizationType === config.optimization.type.values.OBJECTIVE_FUNCTION">
            <div class="col-md-6">
              <library-selector
                [inputLabel]="'Select Objective Flow'"
                [available]="[config.asset.values.FLOW]"
                [value]="{id: form.controls['objectiveFlowId'].value, entity: config.asset.values.FLOW}"
                (valueChange)="$event && form.controls['objectiveFlowId'].setValue($event.id) && onObjectiveFlowChanged($event)"
                [caption]="'Select Objective Flow'"
              ></library-selector>
            </div>
            <div class="col-md-6">
              <app-select [label]="'Metric'"
                [control]="form.controls['objectives']['controls'][0].controls.columnName"
                [options]="objectiveColumnOptions"
              ></app-select>
            </div>
          </ng-template>
        </div>

        <div class="row" style="border-top: 1px #c5c5c5 solid; padding-top: 5px">
          <div class="col-md-6">
            <library-selector
              [inputLabel]="'Select Constraint Flow'"
              [available]="[config.asset.values.FLOW]"
              [value]="{id: form.controls['constraintFlowId'].value, entity: config.asset.values.FLOW}"
              (valueChange)="$event && form.controls['constraintFlowId'].setValue($event.id) && onConstraintFlowChanged($event)"
              [caption]="'Select Constraint Flow'"
            ></library-selector>
          </div>
        </div>
        <ng-template [ngIf]="constraintsTable">
          <div class="panel">
            <div class="panel-body">
              <div class="row">
                <div class="col-md-2">
                  <label>Specify Constraints</label>
                </div>
              </div>
              <div class="row pt5"
                *ngFor="let controlGroup of form.controls['constraints']['controls']">
                <div class="col-md-2 col-md-offset-2">
                  <app-check
                    [label]="controlGroup.controls.columnName.value | tableColumnDisplayName : constraintsTable"
                    [value]="true"
                    [control]="controlGroup.controls.selected"
                  ></app-check>
                </div>
                <div class="col-md-2">
                  <app-select [control]="controlGroup.controls.relationalOperator"
                    [disabled]="!controlGroup.controls.selected.value"
                    [options]="getColumnDataType(controlGroup.controls.columnName.value) === 'boolean' ? boolOperators : operators"
                  ></app-select>
                </div>
                <div class="col-md-4"
                  [ngSwitch]="getColumnDataType(controlGroup.controls.columnName.value)">
                  <app-select *ngSwitchCase="'boolean'"
                    [label]="'Value'"
                    [disabled]="!controlGroup.controls.selected.value"
                    [control]="controlGroup.controls.value"
                    [options]="boolOptions"></app-select>
                  <app-input *ngSwitchCase="'number'"
                    [type]="'number'"
                    [disabled]="!controlGroup.controls.selected.value"
                    [label]="'Value'"
                    [control]="controlGroup.controls.value"></app-input>
                  <app-input-suggestion *ngSwitchCase="'text'"
                    [label]="'Select Value'"
                    [disabled]="!controlGroup.controls.selected.value"
                    [control]="controlGroup.controls.value"
                    [suggestions]="{ fn: getColumnValuesLoader, args: [controlGroup.controls.columnName.value] }"
                  ></app-input-suggestion>
                  <app-input *ngSwitchDefault [disabled]="true"></app-input>
                </div>
              </div>
            </div>
          </div>
        </ng-template>
      </form>
    </ng-template>
  `,
})
export class OptimizationCreateComponent implements OnInit {
  form: FormGroup;
  readonly config = config;
  readonly filterConfig = config.flowstep.option.filter;
  readonly boolOperators: AppSelectOptionData[] = AppSelectOptionData.fromList(this.filterConfig.operator.booleanList, this.filterConfig.operator.labels);
  readonly operators: AppSelectOptionData[] = AppSelectOptionData.fromList(this.filterConfig.operator.list, this.filterConfig.operator.labels);
  readonly boolOptions: AppSelectOptionData[] = AppSelectOptionData.fromList(this.filterConfig.booleanValue.list, this.filterConfig.booleanValue.labels);
  selectedModel: LibrarySelectorValue = null;
  constraintsTable: ITable = null;
  objectiveColumnOptions: AppSelectOptionData[] = [];
  readonly _savingObserver = new ActivityObserver();
  private tableColumnSelectOptionsPipe = new TableColumnSelectOptionsPipe();

  constructor(
    private tables: TableService,
    private optimizations: OptimizationService,
    private router: Router,
  ) {}

  ngOnInit() {
    this.form = new FormGroup({
      name: new FormControl(null, Validators.required),
      description: new FormControl(null),
      optimizationType: new FormControl(config.optimization.type.values.PREDICTOR_TUNING, Validators.required),
      outputModelName: new FormControl(null, Validators.required),
      modelId: new FormControl(null, Validators.required),
      objectiveFlowId: new FormControl(null),
      objectives: new FormArray([new FormGroup({
        goal: new FormControl(config.optimization.goal.values.MAX, Validators.required),
        metric: new FormControl(config.optimization.metric.values.AUROC),
        columnName: new FormControl(''),
      })]),
      constraintFlowId: new FormControl(null),
      constraints: new FormArray([]),
    });

    this.form.controls['optimizationType'].valueChanges.subscribe(() => {
      this.form.patchValue({
        objectiveFlowId: null,
        objectives: [{
          columnName: '',
        }],
        constraintFlowId: null,
        constraints: [],
      });
    });
  }

  onConstraintFlowChanged(selection: LibrarySelectorValue): void {
    const flow = selection.object as IFlow;
    const tableId: TObjectId = flow.steps[flow.steps.length - 1].output;
    this.tables.get(tableId).subscribe((table) => {
      this.constraintsTable = table;
      const constraintsControl = (<FormArray> this.form.controls.constraints);
      while (constraintsControl.length) {
        constraintsControl.removeAt(0);
      }
      table.columns.forEach((column: ITableColumn) => {
        const control = new FormGroup({
          selected: new FormControl(false),
          columnName: new FormControl(column.name, Validators.required),
          relationalOperator: new FormControl(config.flowstep.option.filter.operator.values.EQ, Validators.required),
          value: new FormControl(null),
          logicalOperator: new FormControl(config.flowstep.option.filter.operatorGroup.values.AND, Validators.required),
        });
        constraintsControl.push(control);
      });
    });
  }

  onObjectiveFlowChanged(selection: LibrarySelectorValue): void {
    const flow = selection.object as IFlow;
    const tableId: TObjectId = flow.steps[flow.steps.length - 1].output;
    this.tables.get(tableId).subscribe((table) => {
      this.objectiveColumnOptions = this.tableColumnSelectOptionsPipe.transform(table.columns);
    });
  }

  getColumnDataType(columnName: string): string {
    if (this.constraintsTable) {
      const column = this.constraintsTable.columns.find(column => column.name === columnName);
      if (column && 'dataType' in column) {
        switch (column.dataType) {
          case ITable.ColumnDataType.INTEGER:
          case ITable.ColumnDataType.DOUBLE:
          case ITable.ColumnDataType.LONG:
            return 'number';
          case ITable.ColumnDataType.STRING:
          case ITable.ColumnDataType.TIMESTAMP:
            return 'text';
          case ITable.ColumnDataType.BOOLEAN:
            return 'boolean';
        }
      }
    }
    return '';
  }

  getColumnValuesLoader: Function = (search: string, columnName: string): Observable<TTableValue[]> => {
    if (this.constraintsTable) {
      const column = this.constraintsTable.columns.find(column => column.name === columnName);
      if (column && column.variableType === ITable.ColumnVariableType.CATEGORICAL) {
        return this.tables
          .values(this.constraintsTable.id, {
            column_name: columnName,
            search: search || '',
            limit: 20,
          })
          .map((result: IBackendList<TTableValue>) => result.data);
      }
    }
    return Observable.of([]);
  };

  submit(): Observable<IOptimization> {
    // TODO: this is not compatible with IOptimizationCreate
    let data = this.form.value;
    data.optimizationConstraints = data.constraints.filter(constraint => constraint.selected).map((constraint): IOptimization.Constraint => {
      return {
        columnName: constraint.columnName,
        value: constraint.value,
        relationalOperator: constraint.relationalOperator,
        logicalOperator: constraint.logicalOperator,
      };
    });
    return this.optimizations.create(data).do((optimization: IOptimization) => {
      this.router.navigate(['/desk', 'optimization', optimization.id]);
    });
  }
}
