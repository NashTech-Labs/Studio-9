import { Component, Input, OnChanges } from '@angular/core';
import { FormArray, FormGroup } from '@angular/forms';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { ITable } from '../tables/table.interface';
import { TableService } from '../tables/table.service';

import { IFlow } from './flow.interface';
import { FlowService } from './flow.service';
import { FlowstepEditOptionsComponent } from './flowstep-edit-o.component';

@Component({
  selector: 'flowstep-edit-cluster',
  template: `
    <div class="row">
      <div class="col-md-6">
         <library-selector
           *ngIf="!form.controls['id'].value"
           [inputLabel]="'Input Table'"
           [customLoaders]="[flowTablesLoader]"
           [value]="{id: form.controls['input']['controls'][0].value, entity: config.asset.values.TABLE}"
           (valueChange)="onInputTableSelect($event);"
           [available]="[config.asset.values.TABLE]"></library-selector>
      </div>
    </div>
    <div class="row">
      <div class="col-md-6">
        <app-select
          [label]="'Type'"
          [control]="form.controls['options']['controls'].type"
          [options]="clusterTypes"
        ></app-select>
      </div>
      <div class="col-md-6">
        <app-input [label]="'Groups'" [type]="'number'" [control]="form.controls['options']['controls'].groups"></app-input>
      </div>
    </div>
    <div class="row">
      <div class="col-md-6">
        <app-input [label]="'Iterations'" [type]="'number'"
          [control]="form.controls['options']['controls'].iterations"></app-input>
      </div>
      <div class="col-md-6">
        <app-select [multiple]="true" [label]="'Columns'" [control]="form.controls['options']['controls'].columns"
          [options]="table?.columns | filter : numericFilteringFn  | tableColumnSelectOptions"></app-select>
      </div>
    </div>
    <flowstep-options-pass-columns
      [control]="form.controls['options']['controls'].passColumns"
      [tableIds]="form.controls['input'].value"
      [readOnly]="!!form.controls['id'].value"
    ></flowstep-options-pass-columns>
  `,
})
export class FlowstepEditOptionsClusterComponent extends FlowstepEditOptionsComponent implements OnChanges {
  @Input() form: FormGroup;
  @Input() flow: IFlow;
  readonly config = config;
  readonly clusterTypes: AppSelectOptionData[] = AppSelectOptionData.fromList(config.flowstep.option.cluster.type.list,
    config.flowstep.option.cluster.type.labels);
  table: ITable;

  constructor(flows: FlowService, private tables: TableService) {
    super(flows);
  }

  ngOnChanges() {
    super.ngOnChanges();
    const tableId = (<FormArray> this.form.controls['input']).controls[0].value;
    if (tableId) {
      this.tables.get(tableId).subscribe((table) => {
        this.table = table;
      });
    }
  }

  protected onInputTableSelect(selection: LibrarySelectorValue) {
    this.form.markAsDirty();
    (<FormArray> this.form.controls['input']).controls[0].setValue(selection ? selection.id : null);
    this.table = selection ? <ITable> selection.object : null;
  }
}

