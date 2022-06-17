import { Component, Input, OnChanges, OnDestroy } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';

import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { ITable, ITableColumn } from '../tables/table.interface';
import { TableService } from '../tables/table.service';

import { IFlow } from './flow.interface';
import { FlowService } from './flow.service';
import { FlowstepEditOptionsComponent } from './flowstep-edit-o.component';

@Component({
  selector: 'flowstep-edit-map',
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
    <div class="row brand-margin-bottom">
      <div class="col-xs-12">
          <label>Result Columns</label>
          <button *ngIf="!form.controls['id'].value"
            (click)="collapseMappings = false; _addNewColumnFormControl()"
            class="btn btn-default"><i class="glyphicon glyphicon-plus"></i></button>
          <button class="btn btn-default  pull-right"
            (click)="collapseMappings = !collapseMappings"
            [title]="collapseMappings ? 'Show mappings' : 'Hide mappings'">
            <i class="glyphicon" [ngClass]="collapseMappings ? 'glyphicon-eye-open' : 'glyphicon-eye-close'"></i>
          </button>
      </div>
    </div>
    <div [hidden]="collapseMappings" class="row" *ngFor="let controlGroup of form.controls['options']['controls'].changes.controls;let i = index">
      <div class="col-md-6">
        <app-input *ngIf="form.controls['id'].value" [label]="'Column Name'" [control]="controlGroup.controls.value" [disabled]="true">
        </app-input>
        <app-select *ngIf="!form.controls['id'].value" [label]="'Column Name'" [control]="controlGroup.controls.value"
          (valueChange)="setColumn($event, controlGroup)"
          [options]="inputColumnOptions"></app-select>
      </div>
      <div class="col-xs-10 col-md-5">
        <app-input [label]="'New Column Name'"
          [control]="controlGroup.controls.name"
          [disabled]="form.controls['id'].value || !controlGroup.controls.value.value">
        </app-input>
      </div>
      <div class="col-xs-2 col-md-1" *ngIf="!form.controls['id'].value">
        <a (click)="form.controls['options']['controls'].changes.removeAt(i)"
          class="btn btn-default pull-right"><i class="glyphicon glyphicon-remove"></i></a>
      </div>
    </div>
  `,
})
export class FlowstepEditOptionsMapComponent extends FlowstepEditOptionsComponent implements OnChanges, OnDestroy {
  @Input() form: FormGroup;
  @Input() flow: IFlow;

  readonly config = config;
  inputColumnOptions: AppSelectOptionData[] = [];
  collapseMappings: boolean = false;
  private columnUsed: any = {};
  private additionalFormSubscription: Subscription;

  constructor(
    flows: FlowService,
    private tables: TableService,
  ) {
    super(flows);
  }

  ngOnChanges() {
    super.ngOnChanges();
    this.additionalFormSubscription && this.additionalFormSubscription.unsubscribe();
    this.additionalFormSubscription = this.form.controls['options'].valueChanges.subscribe(() => {
      this._updateActiveColumns();
    });
    this._updateActiveColumns();
    const tableId = (<FormArray> this.form.controls['input']).controls[0].value;
    if (tableId) {
      this.tables.get(tableId).subscribe((table) => {
        this.setInputColumnOptions(table);
      });
    }
  }

  ngOnDestroy() {
    this.additionalFormSubscription && this.additionalFormSubscription.unsubscribe();
  }

  protected _updateActiveColumns() {
    this.columnUsed = {};
    (<FormArray> (<FormGroup> this.form.controls['options']).controls['changes']).value
      .filter((_: any) => !!_.value)
      .forEach((_: any) => {
        this.columnUsed[_.value] = true;
      });
  }

  protected _addNewColumnFormControl(column: ITableColumn = null) {
    (<FormArray> (<FormGroup> this.form.controls['options']).controls['changes']).push(new FormGroup({
      'value': new FormControl(column ? column.name : null, Validators.required),
      'name': new FormControl(column ? column.displayName : null, Validators.required),
    }));
  }

  protected onInputTableSelect(selection: LibrarySelectorValue) {
    this.form.markAsDirty();
    (<FormArray> this.form.controls['input']).controls[0].setValue(selection ? selection.id : null);
    this._inputTableChanged(selection ? <ITable> selection.object : null);
  }

  protected setInputColumnOptions(table) {
    this.inputColumnOptions = table.columns.map((column: ITableColumn) => {
      return {
        id: column.name,
        text: column.displayName,
      };
    });
  }

  protected _inputTableChanged(table: ITable) {
    if ((<FormControl> this.form.controls['id']).value) {
      return;
    }

    let changesControl = (<FormArray> (<FormGroup> this.form.controls['options']).controls['changes']);

    while (changesControl.length) {
      changesControl.removeAt(0);
    }

    if (table) {
      this.inputColumnOptions = table.columns.map((column: ITableColumn) => {
        this._addNewColumnFormControl(column);

        return {
          id: column.name,
          text: column.displayName,
        };
      });
    } else {
      this.inputColumnOptions = [];
      this._addNewColumnFormControl();
    }
  }

  protected setColumn(name: string, controlGroup: FormGroup) {
    const control = controlGroup.controls['name'];
    control.setValue(name);
  }
}
