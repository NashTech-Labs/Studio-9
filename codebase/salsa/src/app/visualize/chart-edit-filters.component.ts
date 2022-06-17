import { Component, Input, OnChanges, OnDestroy } from '@angular/core';

import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { IDimensions } from '../tables/table.interface';
import { ReactiveLoader } from '../utils/reactive-loader';

import { DashboardEditState } from './dashboard-edit-state';
import { IDashboard } from './dashboard.interface';

@Component({
  selector: 'chart-edit-filters',
  template: `
    <table class="table table-striped">
      <thead>
      <tr>
        <th>Name</th>
        <th>Show On Chart</th>
        <th>Local Filter</th>
        <th>Cross Filter</th>
      </tr>
      </thead>
      <tbody>
        <tr *ngFor="let metric of dimensions?.metrics;let i = index">
          <td>
            <label class="checkbox ellipsis" [title]="metric.displayName">
              <i class="fa fa-sort-numeric-asc"></i>
              {{metric.displayName}}
            </label>
          </td>
          <td>
            <app-check
            [checked]="isChecked(metric.name)"
            (checkedChange)="checkChange(metric.name, $event)"></app-check>
          </td>
          <td>
            <app-check [type]="'radio'"
              [name]="'filterMetric_' + i"
              [checked]="!isCrossFilter(metric.name)"
              (checkedChange)="switchCrossFilter(metric.name, $event)"
              [value]="false"></app-check>
          </td>
          <td>
            <app-check [type]="'radio'"
              [name]="'filterMetric_' + i"
              [checked]="isCrossFilter(metric.name)"
              (checkedChange)="switchCrossFilter(metric.name, $event)"
              [value]="true"></app-check>
          </td>
        </tr>
        <tr *ngFor="let attribute of dimensions?.attributes;let i = index">
          <td>
            <label class="checkbox ellipsis" [title]="attribute.displayName">
              <i class="fa fa-tag"></i>
              {{attribute.displayName}}
            </label>
          </td>
          <td>
            <app-check [checked]="isChecked(attribute.name)"
              (checkedChange)="checkChange(attribute.name, $event)"></app-check>
          </td>
          <td>
            <app-check [type]="'radio'"
              [name]="'filterAttribute_' + i"
              [checked]="!isCrossFilter(attribute.name)"
              (checkedChange)="switchCrossFilter(attribute.name, $event)"
              [value]="false"></app-check>
          </td>
          <td>
            <app-check [type]="'radio'"
              [name]="'filterAttribute' + i"
              [checked]="isCrossFilter(attribute.name)"
              (checkedChange)="switchCrossFilter(attribute.name, $event)"
              [value]="true"></app-check>
          </td>
        </tr>
      </tbody>
    </table>
  `,
})
export class ChartEditFiltersComponent implements OnChanges, OnDestroy {
  config = config;
  @Input() state: DashboardEditState;
  dimensions: IDimensions;
  private formSubscription: Subscription;
  private _dimensionsLoader: ReactiveLoader<IDimensions, any>;

  constructor() {
    // todo: remove loader here
    this._dimensionsLoader = new ReactiveLoader(() => {
      const input = this.state.getCurrentInput();
      const data: IDimensions = { tableId: null, metrics: [], attributes: [] };
      if (!input) {
        return Observable.of(data);
      }

      switch (input.type) {
        case config.asset.values.TABLE:
          data.tableId = input.id;
          input.table.columns.forEach(column => {
            if (column.columnType === config.table.column.columnType.values.METRIC) {
              data.metrics.push(column);
            } else if (column.columnType === config.table.column.columnType.values.ATTRIBUTE) {
              data.attributes.push(column);
            }
          });
          break;
        case config.asset.values.MODEL:
          data.tableId = input.model.pipeline ? input.model.pipeline.input : null;
          data.metrics = [input.model.responseColumn];
          data.attributes = [...input.model.predictorColumns];
          break;
        default:
          throw new Error('Unsupported input type');
      }

      return Observable.of(data);
    });

    this._dimensionsLoader.subscribe((data: IDimensions) => {
      this.dimensions = data;
    });
  }

  ngOnChanges() {
    this.formSubscription && this.formSubscription.unsubscribe();
    this.formSubscription = this.state.widgetForm.controls['input'].valueChanges.subscribe(() => {
      this._dimensionsLoader.load();
    });
    this._dimensionsLoader.load();
  }

  ngOnDestroy() {
    this.formSubscription && this.formSubscription.unsubscribe();
  }

  isChecked(name: string): boolean {
    return this.state.widgetForm.value.chartFilters.findIndex(_ => _ === name) >= 0;
  }

  isCrossFilter(name: string): boolean {
    return this.state.form.value.crossFilters.findIndex(_ => _.tableId === this.dimensions.tableId && _.columnName === name) >= 0;
  }

  switchCrossFilter(name: string, enable: boolean) {
    const value = (<IDashboard> this.state.form.value).crossFilters.filter(_ => _.tableId !== this.dimensions.tableId || _.columnName !== name);
    if (enable) {
      value.push({
        tableId: this.dimensions.tableId,
        columnName: name,
      });
    }
    this.state.form.controls.crossFilters.setValue(value);
  }

  checkChange(name: string, checked: boolean): void {
    const value = this.state.widgetForm.value.chartFilters.filter(_ => _ !== name);
    if (checked) {
      value.push(name);
    }
    this.state.widgetForm.controls.chartFilters.setValue(value);
  }
}
