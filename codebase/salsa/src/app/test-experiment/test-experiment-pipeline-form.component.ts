import { Component, EventEmitter, OnDestroy, OnInit, Output } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';

import { Subscription } from 'rxjs/Subscription';

import { IExperimentPipelineForm } from '../experiments/experiment-pipeline.component';
import { IAbstractExperimentPipeline } from '../experiments/experiment.interfaces';
import { AppFormGroup } from '../utils/forms';

@Component({
  selector: 'test-experiment-pipeline-form',
  template: `
    <app-input
      [label]="'Test Field 1'"
      [control]="form.controls.testField1"
    ></app-input>
    <app-input
      [label]="'Test Field 2'"
      [control]="form.controls.testField2"
    ></app-input>
  `,
})
export class TestExperimentPipelineFormComponent implements OnInit, OnDestroy, IExperimentPipelineForm {
  @Output() validityChange = new EventEmitter<boolean>();
  @Output() dataChange = new EventEmitter<IAbstractExperimentPipeline>();

  form: AppFormGroup<{
    testField1: FormControl;
    testField2: FormControl;
  }>;

  private _subscription = new Subscription();

  ngOnInit(): void {
    this.form = new AppFormGroup({
      testField1: new FormControl(null, Validators.required),
      testField2: new FormControl(null),
    });

    this._onFormChanged();

    this._subscription.add(this.form.valueChanges.subscribe(() => this._onFormChanged()));
  }

  ngOnDestroy(): void {
    this._subscription.unsubscribe();
  }

  private _onFormChanged(): void {
    this.validityChange.emit(this.form.valid);
    this.dataChange.emit(this.form.value);
  }
}
