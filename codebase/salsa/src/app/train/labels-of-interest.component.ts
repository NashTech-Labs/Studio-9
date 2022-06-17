import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output, SimpleChanges } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';

import { Subscription } from 'rxjs/Subscription';

import { AppFormArray, AppFormGroup, EntityControls } from '../utils/forms';

import { LabelOfInterest } from './cvtl-train.interfaces';

type LOIItemForm = AppFormGroup<EntityControls<LabelOfInterest>>;

@Component({
  selector: 'labels-of-interest',
  template: `
    <ng-container *ngIf="labels && labels.length">
      <app-check [label]="'Specify Labels of Interest'"
        [checked]="form.enabled"
        (checkedChange)="_toggleLOI($event)"
      ></app-check>
      <ng-container *ngIf="form.enabled">
        <hr />
        <div class="row" *ngFor="let control of form.controls">
          <div class="col-sm-4 col-lg-2 col-lg-offset-2">
            <app-check
              [label]="control.controls.label.value"
              [checked]="control.enabled"
              (checkedChange)="$event ? control.enable() : control.disable()"
            ></app-check>
          </div>
          <div class="col-sm-8 col-lg-6">
            <app-slider
              label="Threshold"
              [min]="0"
              [max]="1"
              [step]="0.01"
              [disabled]="control.disabled"
              [value]="[control.controls.threshold.value]"
              (valueChange)="control.controls.threshold.setValue($event[0])"
            ></app-slider>
          </div>
        </div>
      </ng-container>
    </ng-container>
  `,
})
export class LabelsOfInterestComponent implements OnChanges, OnDestroy {
  @Input() labels: string[] = [];

  @Output() valueChange: EventEmitter<LabelOfInterest[]> = new EventEmitter<LabelOfInterest[]>();

  readonly form = new AppFormArray<LOIItemForm>([]);

  private readonly formSubscription: Subscription;

  constructor() {
    this.form.disable();
    this.formSubscription = this.form.valueChanges.subscribe((value) => {
      this._emitValueChange(value);
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['labels']) {
      while (this.form.length) {
        this.form.removeAt(this.form.length - 1);
      }

      this.labels.forEach(label => {
        const labelFormControl = new AppFormGroup({
          label: new FormControl(label, Validators.required),
          threshold: new FormControl(0.3, Validators.required),
        });
        if (!this.form.enabled) {
          labelFormControl.disable();
        }
        this.form.push(labelFormControl);
      });

    }
  }

  ngOnDestroy(): void {
    this.formSubscription.unsubscribe();
  }

  _toggleLOI(enabled: boolean) {
    if (enabled) {
      this.form.enable();
    } else {
      this.form.disable();
    }
  }

  private _emitValueChange(value: LabelOfInterest[]): void {
    if (this.form.disabled) {
      this.valueChange.emit(null);
    } else {
      this.valueChange.emit(value);
    }
  }

}

