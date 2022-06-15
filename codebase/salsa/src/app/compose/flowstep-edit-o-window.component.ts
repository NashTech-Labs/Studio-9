import { Component, Input, OnChanges } from '@angular/core';
import { FormArray, FormGroup } from '@angular/forms';

import config from '../config';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { ITable } from '../tables/table.interface';
import { TableService } from '../tables/table.service';

import { IFlow } from './flow.interface';
import { FlowService } from './flow.service';
import { FlowstepEditOptionsComponent } from './flowstep-edit-o.component';

@Component({
  selector: 'flowstep-edit-window',
  template: `
    <div class="row">
      <div class="col-md-6">
         <library-selector
           *ngIf="!form.controls['id'].value"
           [inputLabel]="'Input Table'"
           [customLoaders]="[flowTablesLoader]"
           [value]="{id: form.controls['input']['controls'][0].value, entity: config.asset.values.TABLE}"
           (valueChange)="onInputTableSelect($event)"
           [available]="[config.asset.values.TABLE]"></library-selector>
      </div>
      <!--New Column Name-->
      <div class="col-md-6">
        <app-input [label]="'New Column Name'"
          [control]="form.controls['options']['controls'].newColName"></app-input>
      </div>
    </div>
    <div class="row">
      <!--Operator-->
      <div class="col-md-6">
        <app-select [label]="'Operator'" [control]="form.controls['options']['controls'].aggregator"
          [options]="config.flowstep.option.window.aggregator.list"></app-select>
      </div>
      <!--Column-->
      <div class="col-md-6" *ngIf="_isParameterAvailable('aggregatorArg')">
        <app-select [label]="'Argument Column'"
          [control]="form.controls['options']['controls'].aggregatorArg"
          [options]="table?.columns | filter : numericFilteringFn | tableColumnSelectOptions"></app-select>
      </div>
      <!--Partition By-->
      <div class="col-md-6">
          <app-select [multiple]="true" [label]="'Partition by'" [control]="form.controls['options']['controls'].partitionBy"
            [options]="table?.columns | tableColumnSelectOptions"></app-select>
      </div>
    </div>
    <app-form-group *ngIf="_isParameterAvailable('windowUpperBound')"
      [caption]="'Window Bounds'"
      [helpText]="helpText"
    >
      <div class="row">
        <!-- Window Lower Bound -->
        <div class="col-md-6">
          <app-input [type]="'number'"
            [label]="'Preceding'"
            [control]="form.controls['options']['controls'].windowLowerBound"
            [placeholder]="'Unbounded'"></app-input>
        </div>
        <!-- Window Upper Bound -->
        <div class="col-md-6">
          <app-input [type]="'number'"
            [label]="'Following'"
            [control]="form.controls['options']['controls'].windowUpperBound"
            [placeholder]="'Unbounded'"></app-input>
        </div>
      </div>
    </app-form-group>
    <div class="row">
      <!--ignoreNulls-->
      <div class="col-md-6" *ngIf="_isParameterAvailable('ignoreNulls')">
        <app-check [type]="'radio'" [label]="'Nulls Ignore'"
          (checkedChange)="form.controls['options']['controls'].respectNulls.setValue(false)"
          [value]="true"
          [control]="form.controls['options']['controls'].ignoreNulls"></app-check>
      </div>
      <!--respectNulls-->
      <div class="col-md-6" *ngIf="_isParameterAvailable('respectNulls')">
        <app-check [type]="'radio'" [label]="'Nulls Respect'"
          (checkedChange)="form.controls['options']['controls'].ignoreNulls.setValue(false)"
          [value]="true"
          [control]="form.controls['options']['controls'].respectNulls"></app-check>
      </div>
    </div>
    <div class="row" *ngIf="_isParameterAvailable('orderBy') || _isParameterAvailable('withinGroupExpression')">
      <!--Order By-->
      <div class="col-md-6" *ngIf="_isParameterAvailable('orderBy')">
        <app-select [multiple]="true" [label]="'Order By'" [control]="form.controls['options']['controls'].orderBy"
            [options]="table?.columns | tableColumnSelectOptions"></app-select>
      </div>
      <!--withinGroupExpression-->
      <div class="col-md-6" *ngIf="_isParameterAvailable('withinGroupExpression')">
        <app-select [multiple]="true" [label]="'Within Group'"
          [control]="form.controls['options']['controls'].withinGroupExpression"
          [options]="table?.columns | tableColumnSelectOptions"></app-select>
      </div>
      <!--isDesc-->
      <div class="col-md-6">
        <app-select [label]="'Asc/Desc'"
          [value]="form.controls['options']['controls'].isDesc.value === true ? 'true': 'false'"
          [disabled]="form.controls['options']['controls'].isDesc.disabled"
          (valueChange)="changeIsDesc($event)"
          [options]="isDescList"></app-select>
      </div>
    </div>
    <div class="row">
      <!--Offset-->
      <div class="col-md-6" *ngIf="_isParameterAvailable('offset')">
        <app-input [label]="'Offset'" [control]="form.controls['options']['controls'].offset" [type]="'number'"></app-input>
      </div>
      <!--ntileGroupsCount-->
      <div class="col-md-6" *ngIf="_isParameterAvailable('ntileGroupsCount')">
        <app-input [label]="'Groups count'" [control]="form.controls['options']['controls'].ntileGroupsCount"></app-input>
      </div>
      <!--percentile-->
      <div class="col-md-6" *ngIf="_isParameterAvailable('percentile')">
        <app-input [type]="'number'"
           [label]="'Percentile Value'"
           [step]="'any'"
           [control]="form.controls['options']['controls'].percentile"></app-input>
      </div>
      <!--listaggDelimiter-->
      <div class="col-md-6" *ngIf="_isParameterAvailable('listaggDelimiter')">
        <app-input [label]="'Delimiter'" [control]="form.controls['options']['controls'].listaggDelimiter"></app-input>
      </div>
    </div>
    <flowstep-options-pass-columns
      [control]="form.controls['options']['controls'].passColumns"
      [tableIds]="form.controls['input'].value"
      [readOnly]="!!form.controls['id'].value"
    ></flowstep-options-pass-columns>
  `,
})
export class FlowstepEditOptionsWindowComponent extends FlowstepEditOptionsComponent implements OnChanges {
  @Input() form: FormGroup;
  @Input() flow: IFlow;

  readonly config = config;
  readonly isDescList = [
    { id: 'false', text: 'Asc' },
    { id: 'true', text: 'Desc' },
  ];
  readonly helpText: string = `<div class="tooltip-inner-html">The window frame is defined as <b>between x preceding and y following</b>.<br />
  Putting <b>0</b> in either of the fields means <b>current row</b> for the respective bound</div>`;
  table: ITable;

  constructor(flows: FlowService, private tables: TableService) {
    super(flows);
  }

  ngOnChanges() {
    super.ngOnChanges();
    const tableId = (<FormArray> this.form.controls['input']).controls[0].value;
    if (tableId) {
      this.tables.get(tableId).subscribe((table) => this.table = table);
    }
  }

  changeIsDesc(value: string) {
    (<FormGroup> this.form.controls['options']).controls['isDesc'].setValue(value === 'true');
  }

  onInputTableSelect(selection: LibrarySelectorValue) {
    this.form.markAsDirty();
    (<FormArray> this.form.controls['input']).controls[0].setValue(selection ? selection.id : null);
    this.table = selection ? <ITable> selection.object : null;
  }

  _isParameterAvailable(parameterName: string): boolean {
    const currentOperator = (<FormGroup> this.form.controls['options']).controls['aggregator'].value;
    return (this.config.flowstep.option.window.aggregator.options[currentOperator] || []).indexOf(parameterName) > -1;
  }
}
