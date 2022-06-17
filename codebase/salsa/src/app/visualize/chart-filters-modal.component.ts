import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';

import config from '../config';
import { ModalComponent } from '../core-ui/components/modal.component';
import { IDimensions } from '../tables/table.interface';

import { IDashboardXFilter } from './dashboard.interface';

@Component({
  selector: 'chart-filters-modal',
  template: `
    <app-modal #modal
      *ngIf="dimensions && filters"
      [sizeClass]="config.modal.size.LARGE"
      [caption]="'Choose Filters to Show on Chart'"
      [buttons]="[{'class': 'btn-primary', title: 'Save' }]"
      (buttonClick)="doSubmit()">

      <app-form-group [caption]="'Metrics'">
        <div class="row" *ngFor="let metric of dimensions.metrics;let i = index">
          <div class="col-md-3">
            <label class="checkbox">{{metric.displayName}}</label>
          </div>
          <div class="col-md-3">
            <app-check [label]="'Show on Chart'"
              [checked]="isChecked(metric.name)"
              (checkedChange)="checkChange(metric.name, $event)"></app-check>
          </div>
          <div class="col-md-3">
            <app-check [type]="'radio'" [label]="'Local Filter'"
              [name]="'filterMetric_' + i"
              [checked]="!isCrossFilter(metric.name)"
              (checkedChange)="switchCrossFilter(metric.name, $event)"
              [value]="false"></app-check>
          </div>
          <div class="col-md-3">
            <app-check [type]="'radio'" [label]="'Cross Filter'"
              [name]="'filterMetric_' + i"
              [checked]="isCrossFilter(metric.name)"
              (checkedChange)="switchCrossFilter(metric.name, $event)"
              [value]="true"></app-check>
          </div>
        </div>
      </app-form-group>
      <app-form-group [caption]="'Attributes'">
        <div class="row" *ngFor="let attribute of dimensions.attributes;let i = index">
          <div class="col-md-3">
            <label class="checkbox">{{attribute.displayName}}</label>
          </div>

          <div class="col-md-3">
            <app-check [label]="'Show on Chart'"
              [checked]="isChecked(attribute.name)"
              (checkedChange)="checkChange(attribute.name, $event)"></app-check>
          </div>

          <div class="col-md-3">
            <app-check [type]="'radio'" [label]="'Local Filter'"
              [name]="'filterAttribute_' + i"
              [checked]="!isCrossFilter(attribute.name)"
              (checkedChange)="switchCrossFilter(attribute.name, $event)"
              [value]="false"></app-check>
          </div>
          <div class="col-md-3">
            <app-check [type]="'radio'" [label]="'Cross Filter'"
              [name]="'filterAttribute' + i"
              [checked]="isCrossFilter(attribute.name)"
              (checkedChange)="switchCrossFilter(attribute.name, $event)"
              [value]="true"></app-check>
          </div>
        </div>
      </app-form-group>
    </app-modal>`,
})


export class ChartFiltersModalComponent {
  config = config;
  @Input() dimensions: IDimensions;
  @Input() filters: string[];
  @Input() crossFilters: IDashboardXFilter[];
  @Output() filtersChange: EventEmitter<string[]> = new EventEmitter<string[]>();
  @Output() crossFiltersChange: EventEmitter<IDashboardXFilter[]> = new EventEmitter<IDashboardXFilter[]>();
  control: FormControl;
  @ViewChild('modal') private modal: ModalComponent;

  open() {
    this.modal.show();
  }

  doSubmit() {
    this.filtersChange.emit(this.filters);
    this.crossFiltersChange.emit(this.crossFilters);
    this.modal.hide();
  }

  isChecked(name: string): boolean {
    return this.filters.findIndex(_ => _ === name) >= 0;
  }

  isCrossFilter(name: string): boolean {
    return this.crossFilters.findIndex(_ => _.tableId === this.dimensions.tableId && _.columnName === name) >= 0;
  }

  checkChange(name: string, checked: boolean): void {
    const value = this.filters.filter(_ => _ !== name);
    if (checked) {
      value.push(name);
    }
    this.filters = value;
  }

  switchCrossFilter(name: string, enable: boolean) {
    const value = this.crossFilters.filter(_ => _.tableId !== this.dimensions.tableId || _.columnName !== name);
    if (enable) {
      value.push({
        tableId: this.dimensions.tableId,
        columnName: name,
      });
    }
    this.crossFilters = value;
  }
}
