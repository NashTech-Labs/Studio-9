import { Component, HostBinding, Input, OnChanges, SimpleChanges } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';

import 'rxjs/add/observable/forkJoin';
import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';

import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { TObjectId } from '../core/interfaces/common.interface';
import { ITable, ITableColumn } from '../tables/table.interface';
import { TableService } from '../tables/table.service';
import { ReactiveLoader } from '../utils/reactive-loader';

@Component({
  selector: 'flowstep-options-pass-columns',
  template: `
    <div class="row brand-margin-bottom" *ngIf="!readOnly || !!control.controls.length">
      <div class="col-md-4">
          <label>Select Result Columns (optional)</label>
          <button *ngIf="!readOnly"
            (click)="control.push(newPassColumnsFormControl())"
            class="btn btn-default"><i class="glyphicon glyphicon-plus"></i></button>
      </div>
    </div>
    <app-spinner *ngIf="control.controls.length" [visibility]="tablesLoader.active | async" [height]="40"></app-spinner>
    <ng-template [ngIf]="tablesLoader.loaded">
      <div class="row" *ngFor="let controlGroup of control.controls; let i = index">
        <div class="col-md-6">
          <app-input *ngIf="readOnly" [label]="'Column Name'" [control]="controlGroup['controls'].columnName"
            [disabled]="true">
          </app-input>
          <app-select *ngIf="!readOnly" [label]="'Column Name'" [control]="controlGroup['controls'].tmp"
            (valueChange)="setPassColumn($event, controlGroup)"
            [options]="inputColumnOptions"></app-select>
        </div>
        <div class="col-md-4">
          <app-input [label]="'New Column Name'"
            [control]="controlGroup['controls'].newColumnName"
            [disabled]="readOnly || !controlGroup['controls'].tmp.value">
          </app-input>
        </div>
        <div class="col-md-2" *ngIf="!readOnly">
          <a (click)="control.removeAt(i)"
            class="btn btn-default"><i class="glyphicon glyphicon-remove"></i></a>
        </div>
      </div>
    </ng-template>
`,
})
export class FlowstepOptionsPassColumnsComponent implements OnChanges {
  @Input() readOnly: boolean = false;
  @Input() control: FormArray;
  @Input() tableIds: TObjectId[] = [];
  @HostBinding('style.position') position = 'relative';
  @HostBinding('style.min-height') minHeight = '35px';
  @HostBinding('style.display') display = 'block';
  inputColumnOptions: AppSelectOptionData[] = [];
  readonly tablesLoader: ReactiveLoader<ITable[], TObjectId[]>;

  constructor(
    private tables: TableService,
  ) {
    this.tablesLoader = new ReactiveLoader<ITable[], TObjectId[]>((tableIds: TObjectId[]) => {
      // return Observable.empty();
      const tableIdsFiltered = tableIds.filter(_ => !!_);
      return tableIdsFiltered.length
        ? Observable.forkJoin(tableIdsFiltered.map(tableId => this.tables.get(tableId)))
        : Observable.of([]);
    });

    this.tablesLoader.subscribe((tables: ITable[]) => {
      this.inputColumnOptions = tables
        .map((table, i) => {
          return {
            id: table.id,
            text: table.name,
            children: table.columns.map((column: ITableColumn) => {
              return {
                id: i + ':' + column.name,
                text: column.displayName,
              };
            }),
          };
        });
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ('tableIds' in changes) {
      this.tablesLoader.load(this.tableIds);
    }
    if ('control' in changes) {
      if (this.control.controls.length === 1 && !(<FormGroup> this.control.controls[0]).controls['columnName'].value) {
        this.control.removeAt(0);
      }
      this.control.controls.forEach((group: FormGroup) => {
        group.controls.tmp.setValue((group.value.tableReference || '0') + ':' + group.value.columnName);
      });
    }
  }

  newPassColumnsFormControl() {
    return new FormGroup({
      newColumnName: new FormControl(null),
      columnName: new FormControl(null),
      tableReference: new FormControl(null),
      tmp: new FormControl(''), // tableReferrer+colName
    });
  }

  setPassColumn(value: string, controlGroup: FormGroup) {
    if (value) {
      const [tableRef, columnName] = value.split(':');
      const selectedOption = this.inputColumnOptions.reduce<AppSelectOptionData>((acc, tableGroup) => {
        return acc || tableGroup.children.find(_ => _.id === value);
      }, null);
      const newColumnName = selectedOption ? selectedOption.text : columnName;
      controlGroup.controls['tableReference'].setValue(parseInt(tableRef));
      controlGroup.controls['columnName'].setValue(columnName);
      controlGroup.controls['newColumnName'].setValue(newColumnName);
      controlGroup.controls['newColumnName'].setValidators(Validators.required);
    } else {
      controlGroup.controls['tableReference'].setValue(null);
      controlGroup.controls['columnName'].setValue(null);
      controlGroup.controls['newColumnName'].setValue(null);
      controlGroup.controls['newColumnName'].clearValidators();
    }
  }
}
