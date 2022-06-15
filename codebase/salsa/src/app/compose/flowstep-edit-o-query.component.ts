import { Component, Input, OnChanges, OnDestroy } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';

import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { ITable, ITableColumn } from '../tables/table.interface';
import { TableService } from '../tables/table.service';

import { IFlow } from './flow.interface';
import { FlowService } from './flow.service';
import { FlowstepEditOptionsComponent } from './flowstep-edit-o.component';

@Component({
  selector: 'flowstep-edit-query',
  template: `
    <div class="row">
      <div class="col-md-12">
        <div class="form-group brand-control p0" [formControlValidator]="form.controls['options']['controls'].expression"
            [ngClass]="{'disabled': form.controls['options']['controls'].expression.disabled}">
            <label class="input-group-label" for="formula"
              [ngClass]="{'disabled': form.controls['id'].value}">Query</label>
            <span class="pull-right elegant-icons" [ngClass]="{'disabled': form.controls['id'].value}">&#xe00a;</span>
            <textarea [formControl]="form.controls['options']['controls'].expression"
              id="Query"
              [flowstepCode]="config.code.mode.SQL"
              [codeReadonly]="form.controls['id'].value"
              [codeTables]="filledTables"
              [field]="form.controls['options']['controls'].expression"
              class="form-control input-sm"
              [placeholder]="form.controls['input']['controls'][0].value ? 'ex. ExampleInput' : 'Choose table first'"></textarea>
          </div>
      </div>
    </div>
    <div class="row brand-margin-bottom">
      <div class="col-md-12">
        <label>Select Input Tables and set Aliases</label>
        <button *ngIf="!form.controls['id'].value" (click)="addControls()" class="btn btn-default">
          <i class="glyphicon glyphicon-plus"></i>
        </button>
      </div>
    </div>
    <div class="row" *ngFor="let control of form.controls['options']['controls'].inputAliases.controls; let i = index">
      <div class="col-md-6">
        <library-selector
          [disabled]="form.controls['id'].value"
          [inputLabel]="'Table'"
          [customLoaders]="[flowTablesLoader]"
          [value]="{id: form.controls['input']['controls'][i].value, entity: config.asset.values.TABLE}"
          (valueChange)="onTableSelect($event,i)"
          [available]="[config.asset.values.TABLE]"></library-selector>
      </div>
      <div class="col-md-5">
        <app-input *ngIf="i === 0" [label]="'Table Alias'" [control]="control" (blur)="generateSql()"></app-input>
        <app-input *ngIf="i !== 0" [label]="'Table Alias'" [control]="control"></app-input>
      </div>
      <div class="col-md-1">
        <button *ngIf="!form.controls['id'].value && i < form.controls['input']['controls'].length && form.controls['input']['controls'].length > 1"
          (click)="removeControls(i)" class="btn btn-default">
            <i class="glyphicon glyphicon-remove"></i>
        </button>
      </div>
    </div>
  `,
})
export class FlowstepEditOptionsQueryComponent extends FlowstepEditOptionsComponent implements OnChanges, OnDestroy {
  @Input() form: FormGroup;
  @Input() flow: IFlow;

  readonly config = config;
  filledTables: ITable[] = [];
  private codeTables: ITable[] = [];
  private counter: number = 0;
  private additionalFormSubscription: Subscription;

  constructor(flows: FlowService, private tables: TableService) {
    super(flows);
  }

  ngOnChanges() {
    super.ngOnChanges();
    this.additionalFormSubscription && this.additionalFormSubscription.unsubscribe();
    if (this.form.value.input && this.form.value.input.length) {
      this.tables.getMany(this.form.value.input.filter(_ => _)).subscribe((manyTables) => {
        this.codeTables = manyTables;
        this._updateCodeTablesForCompletion();
      });
    }
    this.additionalFormSubscription = (<FormArray> (<FormGroup> this.form.controls['options']).controls['inputAliases']).valueChanges.subscribe(() => {
      this._updateCodeTablesForCompletion();
    });
  }

  ngOnDestroy() {
    this.additionalFormSubscription && this.additionalFormSubscription.unsubscribe();
  }

  protected onTableSelect(selection: LibrarySelectorValue, i: number) {
    this.form.markAsDirty();
    this.form.controls['input']['controls'][i].setValue(selection ? selection.id : null);
    this.setCodeTable(i, selection ? <ITable> selection.object : null);
    if (i === 0) {
      this.generateSql();
    }
  }

  protected addControls() {
    (<FormArray> this.form.controls['input']).push(this.newTableAlias());
    this.counter++;
    (<FormArray> (<FormGroup> this.form.controls['options']).controls['inputAliases']).push(this.newTableAlias('Table' + (this.counter + 1)));
    this.codeTables.push(null);
    this._updateCodeTablesForCompletion();
  }

  protected removeControls(i: number) {
    (<FormArray> this.form.controls['input']).removeAt(i);
    (<FormArray> (<FormGroup> this.form.controls['options']).controls['inputAliases']).removeAt(i);
    this.codeTables.splice(i, 1);
    this._updateCodeTablesForCompletion();
  }

  protected generateSql() {
    if (!(<FormControl> this.form.controls['options']['controls'].expression).value) {
      let tableAlias = (<FormArray> (<FormGroup> this.form.controls['options']).controls['inputAliases']).controls[0].value;
      if (tableAlias) {
        (<FormControl> this.form.controls['options']['controls'].expression).setValue(`SELECT * FROM "${tableAlias}"`);
      }
    }
  }

  protected _updateCodeTablesForCompletion() {
    this.filledTables = this.codeTables.filter(_ => !!_).map((table: ITable, i: number) => {
      return Object.assign({}, table, {
        columns: table.columns.map((column: ITableColumn) => {
          return {
            name: (<FormArray> (<FormGroup> this.form.controls['options']).controls['inputAliases']).controls[i].value + '.' + column.name,
            displayName: column.displayName,
            dataType: column.dataType,
            variableType: column.variableType,
            align: column.align,
          };
        }),
      });
    });
  }

  protected setCodeTable(i: number, table: ITable) {
    this.codeTables[i] = table;
    this._updateCodeTablesForCompletion();
  }

  protected newTableAlias(name: string = null) {
    return new FormControl(name, Validators.required);
  }
}

