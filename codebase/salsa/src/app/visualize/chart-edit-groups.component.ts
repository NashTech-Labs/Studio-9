import { Component, Input, OnChanges, OnDestroy } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';

import 'rxjs/add/observable/forkJoin';
import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { TTableValue } from '../tables/table.interface';
import { TableService } from '../tables/table.service';
import { MiscUtils } from '../utils/misc';
import { ReactiveLoader } from '../utils/reactive-loader';

import { DashboardEditState } from './dashboard-edit-state';
import { IDashboardWidget } from './dashboard.interface';
import { TabularDataRequest } from './visualize.interface';

interface ITableValuesCache {
  columnName: string;
  values: TTableValue[];
}

@Component({
  selector: 'chart-edit-groups',
  template: `
    <app-spinner [visibility]="_valuesLoader.active | async"></app-spinner>
    <div *ngIf="_valuesLoader.loaded" class="panel-body">
      <div class="row pt5">
        <div class="col-xs-6">
          <app-select [label]="'Attribute'" [control]="attributeControl"
            [options]="state.getWidgetAttributeColumns() | tableColumnSelectOptions"></app-select>
        </div>
        <div class="col-xs-6">
          <button class="btn btn-default btn-block" (click)="addGroup(attributeControl.value)"
            [disabled]="!attributeControl.value"
            title="Add Group">Add Group
            <i class="glyphicon glyphicon-plus"></i>
          </button>
        </div>
      </div>
      <div class="row pt5" *ngFor="let controlGroup of groupsForm.controls; let j = index">
        <ng-template [ngIf]="controlGroup.value['columnName']">
          <div class="col-xs-3">
            <app-input [value]="controlGroup.value['columnName']" [readonly]="true"></app-input>
          </div>
          <div class="col-xs-3">
            <app-input [control]="controlGroup['controls']['mergedValue']"></app-input>
          </div>
          <div class="col-xs-5">
            <app-select
              [multiple]="true"
              [control]="controlGroup['controls']['values']"
              [options]="findAttrValues(controlGroup.value['columnName'])"></app-select>
          </div>
          <div class="col-xs-1">
            <button class="btn btn-default pull-right" (click)="groupsForm.removeAt(j)"
              title="Remove Group">
              <i class="glyphicon glyphicon-remove"></i>
            </button>
          </div>
        </ng-template>
      </div>
      <button class="btn btn-block btn-primary" (click)="fillWidgetForm()">Update</button>
    </div>
  `,
})
export class ChartEditGroupsComponent implements OnChanges, OnDestroy {
  readonly config = config;
  readonly _valuesLoader: ReactiveLoader<ITableValuesCache[], string[]>;
  @Input() state: DashboardEditState;
  attributeControl = new FormControl(null, Validators.required);
  groupsForm: FormArray = new FormArray([
    new FormGroup({
      columnName: new FormControl(null, Validators.required),
      mergedValue: new FormControl(null, Validators.required),
      values: new FormControl([], Validators.minLength(1)),
    }),
  ]);
  private formGroupsSubscription: Subscription;
  private formAttributesSubscription: Subscription;
  private uniqueValues: ITableValuesCache[] = [];

  constructor(
    private tables: TableService,
  ) {
    // todo: remove loader here
    this._valuesLoader = new ReactiveLoader((attributes: string[]): Observable<ITableValuesCache[]> => {
      const input = this.state.getCurrentInput();
      const currentCache = this.uniqueValues;
      const uncachedAttributes = attributes.filter(attribute => {
        return !currentCache.find(_ => _.columnName === attribute);
      });

      if (!uncachedAttributes.length) {
        return Observable.of(currentCache);
      }

      const observables: Observable<ITableValuesCache>[] = uncachedAttributes.map(attribute => {
        return this.tables.values(input.table.id, {
          column_name: attribute,
          search: '',
          limit: 100,
        }).map((_): ITableValuesCache => {
          return {
            columnName: attribute,
            values: _.data,
          };
        });
      });

      return Observable.forkJoin(observables).map(values => {
        return currentCache.concat(values);
      });
    });

    this._valuesLoader.subscribe(newCache => {
      this.uniqueValues = newCache;
    });
  }

  ngOnChanges() {
    // groups binding
    this.formGroupsSubscription = this.state.widgetForm.controls['groups'].valueChanges.subscribe((groups: TabularDataRequest.MergeGroup[]) => {
      MiscUtils.fillForm(this.groupsForm, groups);
    });
    MiscUtils.fillForm(this.groupsForm, (<IDashboardWidget> this.state.widgetForm.value).groups);

    // attributes binding
    this.formAttributesSubscription = this.state.widgetForm.controls['attributes'].valueChanges.subscribe((attributes: string[]) => {
      this._valuesLoader.load(attributes);
      MiscUtils.fillForm(this.groupsForm, []);
      this.fillWidgetForm();
    });
    this._valuesLoader.load(this.state.widgetForm.controls['attributes'].value);
  }

  ngOnDestroy() {
    this.formGroupsSubscription && this.formGroupsSubscription.unsubscribe();
    this.formAttributesSubscription && this.formAttributesSubscription.unsubscribe();
  }

  addGroup(attribute: string) {
    this.groupsForm.push(this.newGroupForm(attribute));
  }

  newGroupForm(attribute: string) {
    return new FormGroup({
      columnName: new FormControl(attribute, Validators.required),
      mergedValue: new FormControl(null, Validators.required),
      values: new FormControl([], Validators.minLength(1)),
    });
  }

  findAttrValues(attr: string): TTableValue[] {
    const valuesCache = this.uniqueValues.find(_ => _.columnName === attr);
    return valuesCache ? valuesCache.values : [];
  }

  fillWidgetForm() {
    MiscUtils.fillForm(this.state.widgetForm.controls['groups'], this.groupsForm.value);
  }
}
