import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output } from '@angular/core';

import { Observable } from 'rxjs/Observable';
import { Subject } from 'rxjs/Subject';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { IBackendList } from '../core/interfaces/common.interface';
import { ITable, ITableColumn, ITableColumnStats, TTableValue } from '../tables/table.interface';
import { TableService } from '../tables/table.service';

import { TabularDataRequest } from './visualize.interface';

@Component({
  selector: 'chart-generator',
  template: `
    <div *ngIf="columnStats" [ngSwitch]="column.variableType">
      <div *ngSwitchCase="'${ITable.ColumnVariableType.CONTINUOUS}'">
        <label>{{column.displayName}}</label>
        <app-slider *ngIf="steps > 1"
          [min]="columnStats.min"
          [max]="columnStats.max"
          [step]="(columnStats.max - columnStats.min)/100"
          [value]="value ? [value['min'], value['max']] : [columnStats.min, columnStats.max]"
          [range]="true"
          (valueChange)="onRangeValueChange($event)"
        ></app-slider>
        <app-slider *ngIf="steps === 1"
          [min]="columnStats.min"
          [max]="columnStats.max"
          [step]="(columnStats.max - columnStats.min)/100"
          [value]="value ? [value['min']] : [columnStats.min]"
          [range]="false"
          (valueChange)="onRangeValueChange($event.concat([columnStats.max]))"
        ></app-slider>
        <div style="margin-top:5px;">
          <app-input [label]="'Values'" [type]="'number'" [value]="steps" [min]="1"
            (valueChange)="onStepsChange($event)"></app-input>
        </div>
      </div>
      <div *ngSwitchCase="'${ITable.ColumnVariableType.CATEGORICAL}'"
        [ngSwitch]="columnStats.uniqueCount <= 5">
        <div
          *ngSwitchCase="true">
          <label>{{column.displayName}}</label>
          <ng-template [ngIf]="options">
            <app-check *ngFor="let value of options"
              [label]="value"
              [checked]="isChecked(value)"
              (checkedChange)="checkChange(value, $event)">
            </app-check>
          </ng-template>
        </div>
        <div *ngSwitchDefault="">
          <app-select [multiple]="true"
            [label]="column.displayName || column.name"
            [options]="options"
            [value]="_selected"
            (valueChange)="onSelectValueChange($event)"
          ></app-select>
        </div>
      </div>
    </div>
  `,
})
export class ChartGeneratorComponent implements OnChanges, OnDestroy {
  @Input() value: TabularDataRequest.Generator;
  @Input() stats: ITableColumnStats[];
  @Input() table: ITable;
  @Input() columnName: string;
  @Output() valueChange = new EventEmitter<TabularDataRequest.Generator>();

  config = config;
  column: ITableColumn;
  columnStats: ITableColumnStats;
  options: TTableValue[] = [];
  _selected: TTableValue[] = [];
  steps: number = 10;

  private autoSuggestSubscription: Subscription;
  private debouncer = new Subject<TabularDataRequest.Generator>();

  constructor(protected tables: TableService) {
    this.debouncer
      .debounceTime(100)
      .subscribe((value) => this.valueChange.emit(value));
  }

  ngOnChanges(changes) {
    if ('stats' in changes || 'table' in changes || 'columnName' in changes) {
      if (!this.table) {
        return;
      }
      if (!this.stats) {
        return;
      }
      let columnStat = this.stats.find(stat => stat.columnName === this.columnName);
      if (!columnStat) {
        return;
      }
      let column = this.table.columns.find(_ => _.name === this.columnName);
      if (!column) {
        return;
      }

      this.column = column;
      this.columnStats = columnStat;

      if (this.column.variableType === ITable.ColumnVariableType.CATEGORICAL) {
        this.getColumnValuesLoader().subscribe(data => this.options = data);
      }
    }

    if ('value' in changes) {
      if (this.column.variableType === ITable.ColumnVariableType.CATEGORICAL) {
        this._selected = this.value
          ? [...(<TabularDataRequest.CategoricalGenerator> this.value).values]
          : [];
      } else {
        this.steps = this.value ? (<TabularDataRequest.ContinuousGenerator> this.value).steps : 10;
      }
    }
  }

  getIndex(name: string): number {
    return this._selected.indexOf(name);
  }

  isChecked(name: string): boolean {
    return this.getIndex(name) >= 0;
  }

  checkChange(name: string, checked: boolean): void {
    if (checked) {
      this._selected.push(name);
    } else {
      this._selected.splice(this.getIndex(name), 1);
    }
    this.debouncer.next({ columnName: this.column.name, type: 'categorical', values: [...this._selected] });
  }

  onRangeValueChange(value: number[]) {
    const [min, max] = value;
    this.debouncer.next({ columnName: this.column.name, type: 'continuous', min, max, steps: this.steps });
  }

  onSelectValueChange(values: (string | number)[]) {
    this.debouncer.next({ columnName: this.column.name, type: 'categorical', values: [...(values || [])] });
  }

  onStepsChange(steps: number) {
    this.steps = steps;
    const [min, max] = this.value ? [(<TabularDataRequest.ContinuousGenerator> this.value).min, (<TabularDataRequest.ContinuousGenerator> this.value).max] : [this.columnStats.min, this.columnStats.max];
    this.debouncer.next({ columnName: this.column.name, type: 'continuous', min, max, steps: steps });
  }

  ngOnDestroy() {
    this.autoSuggestSubscription && this.autoSuggestSubscription.unsubscribe();
  }

  private getColumnValuesLoader: Function = (search: string): Observable<TTableValue[]> => {
    return this.tables
      .values(this.table.id, {
        column_name: this.columnName,
        search: search || '',
        limit: 20,
      })
      .map((result: IBackendList<TTableValue>) => result.data);
  };
}
