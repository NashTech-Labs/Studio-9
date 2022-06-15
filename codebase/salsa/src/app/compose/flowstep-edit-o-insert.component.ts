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
  selector: 'flowstep-edit-insert',
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
      <div class="col-md-6 pull-right">
        <div class="form-group brand-control p0" [formControlValidator]="form.controls['options']['controls'].formula"
          [ngClass]="{'disabled': form.controls['options']['controls'].formula.disabled}">
          <label class="input-group-label" for="formula"
            [ngClass]="{'disabled': form.controls['id'].value}">Formula</label>
          <span class="pull-right elegant-icons" [ngClass]="{'disabled': form.controls['id'].value}">&#xe00a;</span>
          <textarea [formControl]="form.controls['options']['controls'].formula"
            id="formula"
            [flowstepCode]="config.code.mode.EXCEL"
            [codeReadonly]="form.controls['id'].value"
            [codeTables]="[inputTable]"
            [field]="form.controls['options']['controls'].formula"
            [placeholder]="form.controls['input']['controls'][0].value ? 'ex. ExampleInput' : 'Choose table first'"
          ></textarea>
        </div>
      </div>
      <div class="col-md-6">
        <app-input [label]="'New Column Name'" [disabled]="form.controls['id'].value"
          [control]="form.controls['options']['controls'].name"></app-input>
      </div>
    </div>
    <flowstep-options-pass-columns
      [control]="form.controls['options']['controls'].passColumns"
      [tableIds]="form.controls['input'].value"
      [readOnly]="!!form.controls['id'].value"
    ></flowstep-options-pass-columns>
  `,
})
export class FlowstepEditOptionsInsertComponent extends FlowstepEditOptionsComponent implements OnChanges {
  @Input() form: FormGroup;
  @Input() flow: IFlow;
  readonly config = config;
  inputTable: ITable;

  constructor(flows: FlowService, private tables: TableService) {
    super(flows);
  }

  ngOnChanges() {
    super.ngOnChanges();
    const tableId = (<FormArray> this.form.controls['input']).controls[0].value;
    if (tableId) {
      this.tables.get(tableId).subscribe((table) => {
        this.inputTable = table;
      });
    }
  }

  protected onInputTableSelect(selection: LibrarySelectorValue) {
    this.form.markAsDirty();
    (<FormArray> this.form.controls['input']).controls[0].setValue(selection ? selection.id : null);
    this.inputTable = selection ? <ITable> selection.object : null;
    this.form.controls['options']['controls'].table.setValue(selection ? selection.object : null);
  }
}

