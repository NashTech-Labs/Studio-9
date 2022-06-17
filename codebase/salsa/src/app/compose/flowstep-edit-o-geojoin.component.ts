import { Component, Input, OnChanges } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';

import config from '../config';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { ITable } from '../tables/table.interface';
import { TableService } from '../tables/table.service';

import { IFlow } from './flow.interface';
import { FlowService } from './flow.service';
import { FlowstepEditOptionsComponent } from './flowstep-edit-o.component';

@Component({
  selector: 'flowstep-edit-geojoin',
  template: `
    <div class="row">
      <div class="col-md-6">
         <library-selector
           *ngIf="!form.controls['id'].value"
           [inputLabel]="'Input Table #A'"
           [customLoaders]="[flowTablesLoader]"
           [value]="{id: form.controls['input']['controls'][0].value, entity: config.asset.values.TABLE}"
           (valueChange)="onInputTableSelect($event, 0)"
           [available]="[config.asset.values.TABLE]"></library-selector>
      </div>
      <div class="col-md-6">
         <library-selector
           *ngIf="!form.controls['id'].value"
           [inputLabel]="'Input Table #B'"
           [customLoaders]="[flowTablesLoader]"
           [value]="{id: form.controls['input']['controls'][1].value, entity: config.asset.values.TABLE}"
           (valueChange)="onInputTableSelect($event, 1)"
           [available]="[config.asset.values.TABLE]"></library-selector>
      </div>
    </div>
    <div class="row">
      <div class="col-md-6">
        <app-input [label]="'Left Table Alias'" [control]="form.controls['options']['controls'].leftPrefix"></app-input>
      </div>
      <div class="col-md-6">
         <app-input [label]="'Right Table Alias'" [control]="form.controls['options']['controls'].rightPrefix"></app-input>
      </div>
    </div>

    <div class="row brand-margin-bottom">
      <div class="col-md-6">
          <label>Join Conditions</label>
          <button *ngIf="!form.controls['id'].value"
            (click)="form.controls['options']['controls'].joinConditions.push(newJoinConditionFormControl())"
            class="btn btn-default"><i class="glyphicon glyphicon-plus"></i></button>
      </div>
    </div>
    <div *ngFor="let joinCondition of form.controls['options']['controls'].joinConditions.controls;let i = index"
      class="row">
      <div class="col-md-10 col-lg-11">
      <flowstep-edit-geojoin-condition
        [leftTable]="tablesList[0]" [rightTable]="tablesList[1]"
        [control]="joinCondition"
        [disabled]="!!form.controls['id'].value"
        ></flowstep-edit-geojoin-condition>
      </div>
      <div class="col-md-2 col-lg-1" *ngIf="!form.controls['id'].value">
        <button type="button" [disabled]="form.controls['options']['controls'].joinConditions.controls.length <= 1"
          (click)="form.controls['options']['controls'].joinConditions.removeAt(i)"
          class="btn btn-default"><i class="glyphicon glyphicon-remove"></i></button>
      </div>
    </div>
    <flowstep-options-pass-columns
      [control]="form.controls['options']['controls'].passColumns"
      [tableIds]="form.controls['input'].value"
      [readOnly]="!!form.controls['id'].value"
    ></flowstep-options-pass-columns>
  `,
})
export class FlowstepEditOptionsGeoJoinComponent extends FlowstepEditOptionsComponent implements OnChanges {
  @Input() form: FormGroup;
  @Input() flow: IFlow;

  readonly config = config;
  tablesList: ITable[] = [null, null];

  constructor(flows: FlowService, private tables: TableService) {
    super(flows);
  }

  ngOnChanges() {
    super.ngOnChanges();
    (<FormArray> this.form.controls['input']).controls.forEach((control, i) => {
      if (control.value) {
        this.tables.get(control.value).subscribe(table => this.updateColumnList(table, i));
      }
    });
  }

  protected onInputTableSelect(selection: LibrarySelectorValue, index: number) {
    this.form.markAsDirty();
    (<FormArray> this.form.controls['input']).controls[index].setValue(selection ? selection.id : null);
    this.updateColumnList(selection ? <ITable> selection.object : null, index);
  }

  protected updateColumnList(table: ITable, index: number) {
    this.tablesList[index] = table;
    // force children to update
    this.tablesList = [...this.tablesList];
  }

  protected newJoinConditionFormControl() {
    return new FormGroup({
      left: new FormGroup({
        geoType: new FormControl(config.flowstep.option.geojoin.geometry.values.POINT, Validators.required),
        coordinates: new FormArray([
          new FormGroup({
            lat: new FormControl(null, Validators.required),
            lon: new FormControl(null, Validators.required),
          }),
        ], Validators.minLength(1)),
      }),
      right: new FormGroup({
        geoType: new FormControl(config.flowstep.option.geojoin.geometry.values.POINT, Validators.required),
        coordinates: new FormArray([
          new FormGroup({
            lat: new FormControl(null, Validators.required),
            lon: new FormControl(null, Validators.required),
          }),
        ], Validators.minLength(1)),
      }),
      relation: new FormGroup({
        relType: new FormControl(config.flowstep.option.geojoin.relation.values.ST_DISTANCE, Validators.required),
        operator: new FormControl(config.flowstep.option.geojoin.operator.values.LT, Validators.required),
        value: new FormControl(null, Validators.required),
        relationBase: new FormControl(config.flowstep.option.geojoin.relationBase.values.LEFT, Validators.required),
      }),
    });
  }
}
