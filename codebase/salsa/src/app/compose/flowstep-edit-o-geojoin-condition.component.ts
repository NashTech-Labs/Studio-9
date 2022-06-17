import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { FormGroup } from '@angular/forms';

import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { ITable } from '../tables/table.interface';

@Component({
  selector: 'flowstep-edit-geojoin-condition',
  template: `
    <div class="form-group p0" [formControlValidator]="control">
      <div class="input-group" (click)="toggleExpand()" >
        <label *ngIf="!!label" class="input-group-addon input-group-label" [ngClass]="{'active': expanded, 'disabled': !ready || disabled}"
          >{{label}}</label>
        <input type="text" [disabled]="!ready || disabled" [readonly]="ready && !disabled" class="form-control" [ngClass]="{'active': expanded, 'has-label': !!label}" value="{{summary}}"/>
        <span class="input-group-addon"  [ngClass]="{'active': expanded, 'disabled': !ready || disabled}"
          ><i class="glyphicon" [ngClass]="expanded ? 'glyphicon-triangle-top' : 'glyphicon-triangle-bottom'"
            ></i></span>
      </div>
      <div class="pt5" [formControlValidatorFeedback]="control"></div>
    </div>
    <div  *ngIf="ready && expanded" class="panel" style="padding: 15px; border: 1px #0d47a1 solid;">
      <div class="row">
        <div class="col-md-6">
          <flowstep-edit-geojoin-geometry
            [table]="leftTable"
            [label]="'Geometry #A'"
            [control]="control.controls['left']"
            ></flowstep-edit-geojoin-geometry>
        </div>
        <div class="col-md-6">
          <flowstep-edit-geojoin-geometry
            [table]="rightTable"
            [label]="'Geometry #B'"
            [control]="control.controls['right']"
            ></flowstep-edit-geojoin-geometry>
        </div>
      </div>
      <div class="row">
        <div class="col-md-4">
          <app-select
            [label]="'Relation'"
            [control]="control.controls['relation']['controls'].relType" [options]="relationTypes"
            (valueChange)="setRelationType($event)"
            ></app-select>
        </div>
        <div class="col-md-4">
          <app-select
            [label]="'Operand #1'"
            [control]="control.controls['relation']['controls'].relationBase"
            [options]="[{id: geoConfig.relationBase.values.LEFT, text: 'Geometry A'}, {id: geoConfig.relationBase.values.RIGHT, text: 'Geometry B'}]"
            ></app-select>
        </div>
        <div class="col-md-4">
          <app-select
            [label]="'Operand #2'"
            [control]="control.controls['relation']['controls'].relationBase"
            [options]="[{id: geoConfig.relationBase.values.LEFT, text: 'Geometry B'}, {id: geoConfig.relationBase.values.RIGHT, text: 'Geometry A'}]"
            ></app-select>
        </div>
      </div>
      <div class="row" *ngIf="relationHasParameter(control.controls['relation']['controls'].relType.value)">
        <div class="col-md-4 col-md-offset-4">
          <app-select
            [label]="'Operator'"
            [control]="control.controls['relation']['controls'].operator" [options]="operators"
            ></app-select>
        </div>
        <div class="col-md-4">
          <app-input
            [label]="'Value (meters)'" [type]="'number'"
            [control]="control.controls['relation']['controls'].value"
            ></app-input>
        </div>
      </div>
    </div>
  `,
})
export class FlowstepEditOptionsGeoJoinConditionComponent implements OnChanges, OnDestroy {
  @Input() label: string;
  @Input() control: FormGroup;
  @Input() leftTable: ITable;
  @Input() rightTable: ITable;
  @Input() disabled: boolean = false;

  readonly geoConfig = config.flowstep.option.geojoin;
  readonly relationTypes: AppSelectOptionData[] = AppSelectOptionData.fromList(this.geoConfig.relation.list,
    this.geoConfig.relation.labels);
  readonly operators: AppSelectOptionData[] = AppSelectOptionData.fromList(this.geoConfig.operator.list,
    this.geoConfig.operator.labels);
  summary: string = '...';
  expanded: boolean = false;
  ready: boolean = false;

  private controlSubscription: Subscription;

  constructor() {
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ('control' in changes) {
      this.controlSubscription && this.controlSubscription.unsubscribe();
      this.controlSubscription = this.control.valueChanges.subscribe(() => {
        this._updateSummary();
      });
    }
    this.ready = !!(this.leftTable && this.rightTable);
    this._updateSummary();
  }

  ngOnDestroy(): void {
    this.controlSubscription && this.controlSubscription.unsubscribe();
  }

  setRelationType(relationType: string) {
    if (!this.relationHasParameter(relationType)) {
      (<FormGroup> this.control.controls['relation']).controls['operator'].setValue('eq');
      (<FormGroup> this.control.controls['relation']).controls['value'].setValue('true');
    } else {
      (<FormGroup> this.control.controls['relation']).controls['operator'].reset('eq');
      (<FormGroup> this.control.controls['relation']).controls['value'].reset(null);
    }
  }

  toggleExpand() {
    if (!this.disabled) {
      this.expanded = !this.expanded;
    }
  }

  private _updateSummary() {
    if (!this.ready) {
      this.summary = '-- Please select tables first --';
      return;
    }

    if (!this.disabled && !this.control.valid) {
      this.summary = '-- Please select --';
      return;
    }

    const value = this.control.value;
    const geometryA = this._geometrySummary(value.left);
    const geometryB = this._geometrySummary(value.right);
    const relationArguments = value.relation.relationBase === this.geoConfig.relationBase.values.LEFT
      ? `(${geometryA}, ${geometryB})`
      : `(${geometryB}, ${geometryA})`;
    this.summary = `${this.geoConfig.relation.labels[value.relation.relType]} ${relationArguments}`;
    if (this.relationHasParameter(value.relation.relType)) {
      this.summary += ` ${this.geoConfig.operator.labels[value.relation.operator]} ${value.relation.value}`;
    }
  }

  private _geometrySummary(value) {
    const args = value.coordinates.map(point => {
      return `${point.lat}, ${point.lon}`;
    }).join(', ');
    return `${this.geoConfig.geometry.labels[value.geoType]}(${args})`;
  }

  private relationHasParameter(relationType: string): boolean {
    return this.geoConfig.relationsWithParameter.indexOf(relationType) >= 0;
  }
}
