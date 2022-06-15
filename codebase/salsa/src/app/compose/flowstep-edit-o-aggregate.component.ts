import { Component, Input, OnChanges } from '@angular/core';
import { FormArray, FormGroup } from '@angular/forms';

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
  selector: 'flowstep-edit-aggregate',
  template: `
    <div class="row">
      <div class="col-md-6">
         <library-selector
           *ngIf="!form.controls['id'].value"
           [inputLabel]="'Input Table'"
           [customLoaders]="[flowTablesLoader]"
           [value]="{id: form.controls['input']['controls'][0].value, entity: config.asset.values.TABLE}"
           (valueChange)="onInputTableSelect($event)"
           [available]="[config.asset.values.TABLE]"
         ></library-selector>
      </div>
    </div>
    <div class="row">
      <div class="col-md-6">
        <app-input [label]="'New column name'" [control]="form.controls['options']['controls'].name"></app-input>
      </div>
      <div class="col-md-6">
        <app-select [label]="'Operator'" [control]="form.controls['options']['controls'].operator"
          [options]="operators"></app-select>
      </div>
    </div>
    <div class="row">
      <div class="col-md-6">
        <app-select [label]="'Operand column'" [control]="form.controls['options']['controls'].operandColumn"
          [options]="inputColumnOptions"></app-select>
      </div>
      <div class="col-md-6">
        <app-select [multiple]="true" [label]="'Group by columns'"
          [control]="form.controls['options']['controls'].groupByColumns"
          [options]="inputColumnOptions"></app-select>
      </div>
    </div>
  `,
})
export class FlowstepEditOptionsAggregateComponent extends FlowstepEditOptionsComponent implements OnChanges {
  @Input() form: FormGroup;
  @Input() flow: IFlow;
  inputColumnOptions: AppSelectOptionData[] = [];
  readonly config = config;
  readonly aggregateConfig = config.flowstep.option.aggregate;
  readonly operators: AppSelectOptionData[] = AppSelectOptionData.fromList(this.aggregateConfig.type.list,
    this.aggregateConfig.type.labels);
  private tableColumnSelectOptionsPipe = new TableColumnSelectOptionsPipe();

  constructor(flows: FlowService, private tables: TableService) {
    super(flows);
  }

  ngOnChanges() {
    super.ngOnChanges();
    const tableId = (<FormArray> this.form.controls['input']).controls[0].value;
    if (tableId) {
      this.tables.get(tableId).subscribe((table) => this.getColumns(table));
    }
  }

  protected onInputTableSelect(selection: LibrarySelectorValue) {
    this.form.markAsDirty();
    (<FormArray> this.form.controls['input']).controls[0].setValue(selection ? selection.id : null);
    this.getColumns(selection ? <ITable> selection.object : null);
  }

  private getColumns(table: ITable | null) {
    this.inputColumnOptions = table
      ? this.tableColumnSelectOptionsPipe.transform(table.columns)
      : [];
  }
}

