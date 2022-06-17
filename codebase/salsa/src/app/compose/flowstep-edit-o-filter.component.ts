import { Component, Input, OnChanges } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';

import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { IBackendList } from '../core/interfaces/common.interface';
import { ITable, TTableValue } from '../tables/table.interface';
import { TableService } from '../tables/table.service';
import { TableColumnSelectOptionsPipe } from '../tables/tables.pipes';

import { IFlow } from './flow.interface';
import { FlowService } from './flow.service';
import { FlowstepEditOptionsComponent } from './flowstep-edit-o.component';

@Component({
  selector: 'flowstep-edit-filter',
  template: `
    <div class="row">
      <div class="col-md-6 col-sm-6">
         <library-selector
           *ngIf="!form.controls['id'].value"
           [inputLabel]="'Input Table'"
           [customLoaders]="[flowTablesLoader]"
           [value]="{id: form.controls['input']['controls'][0].value, entity: config.asset.values.TABLE}"
           (valueChange)="onInputTableSelect($event);"
           [available]="[config.asset.values.TABLE]"></library-selector>
      </div>
    </div>
    <div *ngIf="form.controls['input']['controls'][0].value">
      <div class="row brand-margin-bottom">
        <div class="col-md-4">
          <label>Conditions</label>
          <button *ngIf="!form.controls['id'].value"
            (click)="newColumnsFormControl()"
            class="btn btn-default"><i class="glyphicon glyphicon-plus"></i></button>
        </div>
      </div>
      <div class="row pt5"
        *ngFor="let controlGroup of form.controls['options']['controls'].conditions.controls; let i = index;let isLast = last">
        <div class="col-md-4">
          <app-select [label]="'Column'"
            [control]="controlGroup['controls'].column"
            [options]="inputColumnOptions"
            (valueChange)="onConditionColumnSelect(controlGroup);"></app-select>
        </div>
        <div class="col-md-2">
          <app-select [control]="controlGroup['controls'].operator"
            [options]="getColumnDataType(controlGroup['controls'].column.value) === 'boolean' ? boolOperators: operators"
            ></app-select>
        </div>
        <div *ngIf="!form.controls['id'].value"
          class="col-md-3"
          [ngSwitch]="getColumnDataType(controlGroup['controls'].column.value)">
            <app-select *ngSwitchCase="'boolean'"
              [label]="'Value'"
              [control]="controlGroup['controls'].value"
              [options]="boolOptions"></app-select>
            <app-input *ngSwitchCase="'number'"
              [type]="'number'"
              [label]="'Value'"
              [control]="controlGroup['controls'].value"></app-input>
            <app-input-suggestion *ngSwitchCase="'text'"
              [label]="'Select Value'"
              [disabled]="!controlGroup['controls'].column.value"
              [control]="controlGroup['controls'].value"
              [suggestions]="{ fn: getColumnValuesLoader, args: [controlGroup['controls'].column.value] }"
              ></app-input-suggestion>
            <app-input *ngSwitchDefault [disabled]="true"></app-input>
        </div>
        <div class="col-md-3" *ngIf="form.controls['id'].value">
          <app-input
            [label]="'Value'"
            [control]="controlGroup['controls'].value"></app-input>
        </div>
        <div class="col-md-2">
           <app-select *ngIf="!isLast"
             [control]="controlGroup['controls'].operatorGroup"
             [options]="operatorGroups"
           ></app-select>
        </div>
        <div class="col-md-1">
          <button *ngIf="form.controls['options']['controls'].conditions.controls.length > 1"
            [disabled]="form.controls['id'].value"
            class="btn btn-default" (click)="removeColumnsFormControl(i)"
            title="Remove">
              <i class="glyphicon glyphicon-remove"></i>
          </button>
        </div>
      </div>
    </div>
    <flowstep-options-pass-columns
      [control]="form.controls['options']['controls'].passColumns"
      [tableIds]="form.controls['input'].value"
      [readOnly]="!!form.controls['id'].value"
    ></flowstep-options-pass-columns>
  `,
})
export class FlowstepEditOptionsFilterComponent extends FlowstepEditOptionsComponent implements OnChanges {
  @Input() form: FormGroup;
  @Input() flow: IFlow;
  readonly config = config;
  readonly filterConfig = config.flowstep.option.filter;
  readonly boolOperators: AppSelectOptionData[]  = AppSelectOptionData.fromList(
    this.filterConfig.operator.booleanList, this.filterConfig.operator.labels);
  readonly operators: AppSelectOptionData[]      = AppSelectOptionData.fromList(
    this.filterConfig.operator.list, this.filterConfig.operator.labels);
  readonly operatorGroups: AppSelectOptionData[] = AppSelectOptionData.fromList(
    this.filterConfig.operatorGroup.list, this.filterConfig.operatorGroup.labels);
  readonly boolOptions: AppSelectOptionData[]    = AppSelectOptionData.fromList(
    this.filterConfig.booleanValue.list, this.filterConfig.booleanValue.labels);
  inputColumnOptions: AppSelectOptionData[] = [];
  private tableColumnSelectOptionsPipe = new TableColumnSelectOptionsPipe();
  private inputTable: ITable;

  constructor(flows: FlowService, private tables: TableService) {
    super(flows);
  }

  getColumnValuesLoader: Function = (search: string, columnName: string): Observable<TTableValue[]> => {
    if (this.inputTable) {
      const column = this.inputTable.columns.find(column => column.name === columnName);
      if (column && column.variableType === ITable.ColumnVariableType.CATEGORICAL) {
        return this.tables
          .values(this.inputTable.id, {
            column_name: columnName,
            search: search || '',
            limit: 20,
          })
          .map((result: IBackendList<TTableValue>) => result.data);
      }
    }
    return Observable.of([]);
  };

  getColumnDataType(columnName: string): string {
    if (this.inputTable) {
      const column = this.inputTable.columns.find(column => column.name === columnName);
      if (column && column.hasOwnProperty('dataType')) {
        return config.table.column.dataType.controlDataTypes[column.dataType];
      }
    }
    return '';
  }

  ngOnChanges() {
    super.ngOnChanges();
    const tableId = (<FormArray> this.form.controls['input']).controls[0].value;
    if (tableId) {
      this.tables.get(tableId).subscribe((table) => {
        this.getColumns(table);
      });
    }
  }

  newColumnsFormControl() {
    let currentFormGroup = new FormGroup({
      column: new FormControl(null, Validators.required),
      operator: new FormControl(this.filterConfig.operator.values.EQ, Validators.required),
      value: new FormControl(null, Validators.required),
      operatorGroup: new FormControl(this.filterConfig.operatorGroup.values.AND, Validators.required),
    });
    (<FormArray> (<FormGroup> this.form.controls['options']).controls['conditions']).push(currentFormGroup);
  }

  removeColumnsFormControl(index: number) {
    (<FormArray> (<FormGroup> this.form.controls['options']).controls['conditions']).removeAt(index);
  }

  onInputTableSelect(selection: LibrarySelectorValue) {
    this.form.markAsDirty();
    this.getColumns(selection ? <ITable> selection.object : null);
    this.form.controls['input']['controls'][0].setValue(selection ? selection.id : null);
    let condIndex = (<FormArray> (<FormGroup> this.form.controls['options']).controls['conditions']).length;
    while (condIndex) {
      this.removeColumnsFormControl(--condIndex);
    }
    this.newColumnsFormControl();
  }

  onConditionColumnSelect(formGroup: FormGroup) {
    formGroup.controls['value'].reset();
  }

  private getColumns(table: ITable | null) {
    this.inputTable = table;
    this.inputColumnOptions = table
      ? this.tableColumnSelectOptionsPipe.transform(table.columns)
      : [];
  }
}
