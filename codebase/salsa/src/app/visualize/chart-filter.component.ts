import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output } from '@angular/core';

import * as _ from 'lodash';
import { Observable } from 'rxjs/Observable';
import { Subject } from 'rxjs/Subject';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { IBackendList } from '../core/interfaces/common.interface';
import { ITable, ITableColumn, ITableColumnStats, TTableValue } from '../tables/table.interface';
import { TableService } from '../tables/table.service';
import { MiscUtils } from '../utils/misc';

import { TabularDataRequest } from './visualize.interface';

@Component({
  selector: 'chart-filter',
  template: `
    <div *ngIf="columnStats" [ngSwitch]="column.variableType">
      <div *ngSwitchCase="'${ITable.ColumnVariableType.CONTINUOUS}'">
        <label>{{column.displayName}}</label>
        <span *ngIf="columnStats.min === columnStats.max">{{columnStats.min | number: '1.0-1'}}</span>
        <app-slider *ngIf="columnStats.min < columnStats.max"
          [min]="columnStats.min"
          [max]="columnStats.max"
          [step]="(columnStats.max - columnStats.min)/100"
          [value]="value ? [value['min'], value['max']] : [columnStats.min, columnStats.max]"
          [range]="true"
          (valueChange)="onRangeValueChange($event)"
        ></app-slider>
      </div>
      <div *ngSwitchCase="'${ITable.ColumnVariableType.CATEGORICAL}'"
        [ngSwitch]="columnStats.uniqueCount <= CHECKBOXES_LIMIT">
        <div
          *ngSwitchCase="true">
          <label>{{column.displayName}}</label>
          <ng-template [ngIf]="options">
            <app-check
              [label]="'(all)'"
              [checked]="isAllChecked()"
              (checkedChange)="checkAll($event)">
            </app-check>
            <app-check *ngFor="let value of options"
              [label]="value"
              [checked]="isChecked(value)"
              (checkedChange)="checkChange(value, $event)">
            </app-check>
          </ng-template>
        </div>
        <div
          *ngSwitchDefault="" [ngSwitch]="columnStats.uniqueCount <= CATEGORICAL_LIMIT">
          <app-select
            *ngSwitchCase="true"
            [multiple]="true"
            [label]="column.displayName"
            [options]="options"
            [value]="(value && value['values']) ? ['values'] : options"
            (valueChange)="onSelectValueChange($event)"
          ></app-select>
          <span *ngSwitchDefault="">
            {{column.displayName}} contains more than {{CATEGORICAL_LIMIT}} values. Filter is unavailable.
          </span>
        </div>
      </div>
    </div>
  `,
})
export class ChartFilterComponent implements OnInit, OnChanges, OnDestroy {
  readonly CHECKBOXES_LIMIT = 5;
  readonly CATEGORICAL_LIMIT = 250;

  @Input() value: TabularDataRequest.Filter;
  @Input() stats: ITableColumnStats[];
  @Input() table: ITable;
  @Input() columnName: string;
  @Output() valueChange = new EventEmitter<TabularDataRequest.Filter>();

  readonly config = config;
  column: ITableColumn;
  columnStats: ITableColumnStats;
  options: (string | number)[] = [];
  _selected: (string | number)[] = [];
  private autoSuggestSubscription: Subscription;
  private debouncer = new Subject<TabularDataRequest.Filter>();
  private debouncerSubscription: Subscription;

  constructor(protected tables: TableService) {
  }

  ngOnInit() {
    this.initDebouncer();
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
        this.getColumnValuesLoader().subscribe(data => {
          this.options = data;
          this._selected = this.value
            ? [...(<TabularDataRequest.CategoricalGenerator> this.value).values]
            : this.options.slice();
        });
      }
    } else if ('value' in changes) {
      if (this.column.variableType === ITable.ColumnVariableType.CATEGORICAL) {
        this._selected = this.value
          ? [...(<TabularDataRequest.CategoricalGenerator> this.value).values]
          : this.options.slice();
      }
    }
  }

  getSelectedIndex(name: string): number {
    return this._selected.indexOf(name);
  }

  isAllChecked(): boolean {
    return this._selected.length >= this.options.length;
  }

  isChecked(name: string): boolean {
    return this.getSelectedIndex(name) >= 0;
  }

  checkAll(checked: boolean): void {
    this._selected = checked ? this.options.slice() : [];
    this.debouncer.next({ columnName: this.column.name, type: 'categorical', values: [...this._selected] });
  }

  checkChange(name: string, checked: boolean): void {
    const selected = _.intersection(this._selected, this.options).filter(_ => _ !== name);

    if (checked) {
      selected.push(name);
    }

    this._selected = selected;
    this.debouncer.next({ columnName: this.column.name, type: 'categorical', values: [...this._selected] });
  }

  onRangeValueChange(value) {
    const [min, max] = value;
    this.debouncer.next({ columnName: this.column.name, type: 'continuous', min, max });
  }

  onSelectValueChange(values: (string | number)[]) {
    this.debouncer.next({ columnName: this.column.name, type: 'categorical', values: [...(values || [])] });
  }

  ngOnDestroy() {
    this.debouncerSubscription && this.debouncerSubscription.unsubscribe();
    this.autoSuggestSubscription && this.autoSuggestSubscription.unsubscribe();
  }

  private initDebouncer() {
    this.debouncerSubscription && this.debouncerSubscription.unsubscribe();
    this.debouncerSubscription = MiscUtils.distinctUntilChangedDeep(this.debouncer.asObservable())
      .debounceTime(0)
      .subscribe((value) => this.valueChange.emit(value));
  }

  private getColumnValuesLoader: Function = (search: string): Observable<TTableValue[]> => {
    return this.tables
      .values(this.table.id, {
        column_name: this.columnName,
        search: search || '',
        limit: this.CATEGORICAL_LIMIT,
      })
      .map((result: IBackendList<TTableValue>) => result.data);
  };
}
