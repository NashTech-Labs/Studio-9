import { Component, Input, OnChanges, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { ModalComponent } from '../core-ui/components/modal.component';
import { ITable, ITableColumn } from '../tables/table.interface';
import { AppFormArray, AppFormGroup } from '../utils/forms';

@Component({
  selector: 'flowstep-edit-geojoin-geometry',
  template: `
    <app-input
      [label]="label"
      [readonly]="true" [value]="summary"
      (click)="openModal()"
    ></app-input>
    <app-modal #modal [caption]="label" [buttons]="[{'class': 'btn-apply', 'title': 'OK'}]" (buttonClick)="modal.hide()">
      <div class="row">
        <div class="col-md-6">
          <app-select
            [label]="'Geometry Type'"
            [control]="control.controls['geoType']" [options]="geometryTypes"
            (valueChange)="setGeometryType($event)"
          ></app-select>
        </div>
      </div>
      <div *ngIf="control.controls['geoType'].value !== config.geometry.values.POINT" class="row brand-margin-bottom">
        <div class="col-md-10">
          <label>Points</label>
          <button type="button" (click)="control.controls['coordinates'].push(newPointControl())"
            class="btn btn-default"><i class="glyphicon glyphicon-plus"></i></button>
        </div>
      </div>
      <div class="row"
        *ngFor="let controlGroup of control.controls['coordinates'].controls;let i = index; let isLast = last">
        <div [class]="control.controls['geoType'].value !== config.geometry.values.POINT ? 'col-md-5' : 'col-md-6'">
          <app-select
            [label]="'Latitude'"
            [control]="controlGroup.controls.lat" [options]="columnOptions"
          ></app-select>
        </div>
        <div [class]="control.controls['geoType'].value !== config.geometry.values.POINT ? 'col-md-5' : 'col-md-6'">
          <app-select
            [label]="'Longitude'"
            [control]="controlGroup.controls.lon" [options]="columnOptions"
          ></app-select>
        </div>
        <div class="col-md-2 text-right" *ngIf="control.controls['geoType'].value !== config.geometry.values.POINT">
          <button type="button" (click)="control.controls['coordinates'].removeAt(i)"
            [disabled]="control.controls['coordinates'].length <= 3"
            class="btn btn-default"><i class="glyphicon glyphicon-remove"></i></button>
        </div>
      </div>
    </app-modal>
  `,
})
export class FlowstepEditOptionsGeoJoinGeometryComponent implements OnInit, OnChanges {
  @Input() label: string;
  @Input() control: AppFormGroup<{
    geoType: FormControl;
    coordinates: AppFormArray<AppFormGroup<{
      lat: FormControl;
      lon: FormControl;
    }>>;
  }>;
  @Input() table: ITable;

  readonly config = config.flowstep.option.geojoin;
  readonly geometryTypes: AppSelectOptionData[] = AppSelectOptionData.fromList(this.config.geometry.list, this.config.geometry.labels);

  summary: string = '...';
  columnOptions: AppSelectOptionData[] = [];

  @ViewChild('modal') private modal: ModalComponent;

  constructor() {
  }

  ngOnInit() {
    this.control.valueChanges.subscribe(() => this._updateSummary());
    this._updateSummary();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ('table' in changes) {
      this.columnOptions = (this.table ? this.table.columns : []).map((column: ITableColumn) => {
        return {
          id: column.name,
          text: column.displayName,
        };
      });
    }
  }

  openModal() {
    this.modal.show();
  }

  setGeometryType(geoType) {
    const coordsControls = this.control.controls['coordinates'];
    if (geoType === this.config.geometry.values.POINT) {
      while (coordsControls.length > 1) {
        coordsControls.removeAt(1);
      }
    } else if (geoType === this.config.geometry.values.POLYGON) {
      while (coordsControls.length < 3) {
        coordsControls.push(this.newPointControl());
      }
    }
  }

  private _updateSummary() {
    if (!this.control.valid) {
      this.summary = '-- Please select --';
      return;
    }

    const value = this.control.value;
    const args = value.coordinates.map(point => {
      return `${point.lat}, ${point.lon}`;
    }).join(', ');
    this.summary = `${this.config.geometry.labels[value.geoType]}(${args})`;
  }

  private newPointControl() {
    return new FormGroup({
      lat: new FormControl(null, Validators.required),
      lon: new FormControl(null, Validators.required),
    });
  }
}
