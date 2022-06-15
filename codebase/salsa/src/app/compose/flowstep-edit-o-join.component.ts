import { Component, Input, OnChanges } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { ITable } from '../tables/table.interface';
import { TableService } from '../tables/table.service';
import { TableColumnSelectOptionsPipe } from '../tables/tables.pipes';

import { IFlow } from './flow.interface';
import { FlowService } from './flow.service';
import { FlowstepEditOptionsComponent } from './flowstep-edit-o.component';

@Component({
  selector: 'flowstep-edit-join',
  template: `
    <div class="row">
      <div class="col-md-6">
         <library-selector
           *ngIf="!form.controls['id'].value"
           [inputLabel]="'Input Table #A'"
           [customLoaders]="[flowTablesLoader]"
           [value]="{id: form.controls['input']['controls'][0].value, entity: config.asset.values.TABLE}"
           (valueChange)="onInputTableSelect($event, 0)"
           [available]="[config.asset.values.TABLE]"></library-selector>
      </div>
      <div class="col-md-6">
         <library-selector
           *ngIf="!form.controls['id'].value"
           [inputLabel]="'Input Table #B'"
           [customLoaders]="[flowTablesLoader]"
           [value]="{id: form.controls['input']['controls'][1].value, entity: config.asset.values.TABLE}"
           (valueChange)="onInputTableSelect($event, 1)"
           [available]="[config.asset.values.TABLE]"></library-selector>
      </div>
    </div>
    <div class="row">
      <div class="col-md-6">
        <app-input [label]="'Left Table Alias'" [control]="form.controls['options']['controls'].leftPrefix"></app-input>
      </div>
      <div class="col-md-6">
         <app-input [label]="'Right Table Alias'" [control]="form.controls['options']['controls'].rightPrefix"></app-input>
      </div>
    </div>
    <div class="row">
      <div class="col-md-6">
        <app-select [label]="'Values Matching'" [control]="form.controls['options']['controls'].type"
          [options]="joinTypes"></app-select>
      </div>
    </div>
    <div class="row"
      *ngFor="let controlGroup of form.controls['options']['controls'].columns.controls; let i = index">
      <div class="col-md-6">
        <app-select [label]="'From'" [control]="controlGroup.controls.from"
          [disabled]="!form.controls['input']['controls'][0].value || !form.controls['input']['controls'][1].value"
          [options]="flatColumnOptions[0]"></app-select>
      </div>
      <div class="col-md-6">
        <div class="row">
          <div class="col-md-8">
            <app-select [label]="'To'" [control]="controlGroup.controls.to"
              [disabled]="!form.controls['input']['controls'][0].value || !form.controls['input']['controls'][1].value"
              [options]="flatColumnOptions[1]"></app-select>
          </div>
          <div class="col-md-4" *ngIf="!form.controls['id'].value">
            <button *ngIf="i < form.controls['options']['controls'].columns.controls.length - 1"
              (click)="form.controls['options']['controls'].columns.removeAt(i)"
              class="btn btn-default"><i class="glyphicon glyphicon-remove"></i></button>
            <button *ngIf="i === form.controls['options']['controls'].columns.controls.length - 1"
              (click)="form.controls['options']['controls'].columns.push(newColumnsFormControl())"
              class="btn btn-default"><i class="glyphicon glyphicon-plus"></i></button>
          </div>
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
export class FlowstepEditOptionsJoinComponent extends FlowstepEditOptionsComponent implements OnChanges {
  @Input() form: FormGroup;
  @Input() flow: IFlow;

  readonly config = config;
  flatColumnOptions: AppSelectOptionData[][] = [null, null];
  readonly joinTypes = AppSelectOptionData.fromList(config.flowstep.option.join.type.list, config.flowstep.option.join.type.labels);
  private tableColumnSelectOptionsPipe = new TableColumnSelectOptionsPipe();

  constructor(flows: FlowService, private tables: TableService) {
    super(flows);
  }

  ngOnChanges() {
    super.ngOnChanges();
    (<FormArray> this.form.controls['input']).controls.forEach((control, i) => {
      if (control.value) {
        this.tables.get(control.value).subscribe(table => this.updateColumnList(table, i));
      }
    });
  }

  protected onInputTableSelect(selection: LibrarySelectorValue, index: number) {
    this.form.markAsDirty();
    (<FormArray> this.form.controls['input']).controls[index].setValue(selection ? selection.id : null);
    this.updateColumnList(selection ? <ITable> selection.object : null, index);
  }

  protected updateColumnList(table: ITable, index: number) {
    this.flatColumnOptions[index] = table
      ? this.tableColumnSelectOptionsPipe.transform(table.columns)
      : null;
  }

  protected newColumnsFormControl() {
    return new FormGroup({
      from: new FormControl(null, Validators.required),
      to: new FormControl(null, Validators.required),
      type: new FormControl(config.flowstep.option.join.type.values.NORMAL),
    });
  }
}
