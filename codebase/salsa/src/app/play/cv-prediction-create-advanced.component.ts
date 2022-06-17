import { Component, EventEmitter, Input, OnDestroy, Output } from '@angular/core';
import { FormControl } from '@angular/forms';

import { Subscription } from 'rxjs/Subscription';

import { CVModelType, ICVModel } from '../train/cv-model.interface';
import { LabelOfInterest } from '../train/cvtl-train.interfaces';
import { trainConfig } from '../train/train.config';
import { AppFormGroup } from '../utils/forms';

import { ICVPredictionCreate } from './cv-prediction.interface';

@Component({
  selector: 'cv-prediction-create-advanced',
  template: `
    <app-slider
      [min]="0"
      [max]="1"
      [step]="0.01"
      [range]="false"
      label="Visual Threshold"
      [helpText]="'${trainConfig.hints.defaultVisualThresholdHelpText}'"
      [value]="[form.controls.defaultVisualThreshold.value]"
      (valueChange)="form.controls.defaultVisualThreshold.setValue($event[0])"
    ></app-slider>

    <labels-of-interest
      [labels]="model.classes"
      (valueChange)="onLoiChange($event)"
    ></labels-of-interest>
  `,
})
export class PlayCVPredictionCreateAdvancedComponent implements OnDestroy {
  @Input('model') model: ICVModel;
  @Output() valueChange: EventEmitter<ICVPredictionCreate.Options> = new EventEmitter<ICVPredictionCreate.Options>();

  readonly form = new AppFormGroup({
    loi: new FormControl(),
    defaultVisualThreshold: new FormControl(0.3),
  });

  protected labels: string[] = [];

  private readonly formSubscription: Subscription;

  constructor() {
    this.formSubscription = this.form.valueChanges.subscribe((value) => {
      this._emitValueChange(value);
    });
  }

  ngOnDestroy() {
    this.formSubscription.unsubscribe();
  }

  protected onLoiChange(value: LabelOfInterest[]): void {
    this.form.controls.loi.setValue(value);
  }

  private _emitValueChange(value: ICVPredictionCreate.Options = this.form.value) {
    if (!Object.keys(value).length) {
      this.valueChange.emit(null);
    } else {
      this.valueChange.emit(value);
    }
  }

  static advancedOptionsAvailable(model: ICVModel) {
    return model.modelType.type === CVModelType.Type.TL
      && model.modelType.tlType === CVModelType.TLType.LOCALIZATION;
  }
}

