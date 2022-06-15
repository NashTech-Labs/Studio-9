import { Component, Inject, OnDestroy } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { FeatureToggleService } from '../core/services/feature-toggle.service';
import { ActivityObserver } from '../utils/activity-observer';
import { AppFormGroup } from '../utils/forms';

import {
  EXPERIMENT_TYPES,
  ExperimentTypeDefinition,
  IAbstractExperimentPipeline,
  IExperimentFull,
} from './experiment.interfaces';
import { ExperimentService } from './experiment.service';

@Component({
  selector: 'experiment-create',
  template: `
    <asset-operations [type]="config.asset.values.EXPERIMENT" [selectedItems]="[]"></asset-operations>
    <form [formGroup]="form" (ngSubmit)="isFormsValid && onSubmit()">
      <div class="row">
        <div class="col-md-6">
          <app-input [label]="'Experiment Name'" [control]="form.controls.name"></app-input>
        </div>
        <div class="col-md-6">
          <div class="pull-right">
            <button
              type="submit"
              class="btn btn-md btn-success"
              [disabled]="!isFormsValid || (isSaving$ | async)"
            >
              Create
            </button>
          </div>
        </div>
      </div>
      <div class="row">
        <div class="col-md-6">
          <app-description [control]="form.controls.description" [editMode]="true"></app-description>
        </div>
      </div>
      <div class="row">
        <div class="col-md-6">
          <app-select
            [label]="'Experiment Type'"
            [control]="form.controls.type"
            [options]="typeOptions"
          ></app-select>
        </div>
      </div>

      <experiment-pipeline
        [type]="form.controls.type.value"
        (validityChange)="pipelineForm.isValid = $event"
        (dataChange)="pipelineForm.data = $event"
      ></experiment-pipeline>
    </form>
  `,
})
export class ExperimentCreateComponent implements OnDestroy {
  form: AppFormGroup<{
    name: FormControl;
    description: FormControl;
    type: FormControl;
  }>;
  typeOptions: AppSelectOptionData[];

  pipelineForm: { isValid: boolean, data: IAbstractExperimentPipeline } = {
    isValid: false,
    data: null,
  };

  config = config;

  private _subscription = new Subscription();
  private readonly _savingObserver: ActivityObserver = new ActivityObserver();

  constructor(
    private _router: Router,
    private _experimentService: ExperimentService,
    featureService: FeatureToggleService,
    @Inject(EXPERIMENT_TYPES) typeDefinitions: ExperimentTypeDefinition[],
  ) {
    this.typeOptions = typeDefinitions
      .filter(_ => !!_.pipelineComponent)
      .filter(_ => featureService.areFeaturesEnabled(_.features))
      .map(typeDefinition => ({
        id: typeDefinition.type,
        text: typeDefinition.name,
      }));
    this._createForm();
  }

  get isSaving$(): Observable<boolean> {
    return this._savingObserver.active;
  }

  get isFormsValid(): boolean {
    return this.form.valid && this.pipelineForm.isValid;
  }

  ngOnDestroy(): void {
    this._subscription.unsubscribe();
  }

  onSubmit(): void {
    this._subscription.add(
      this._savingObserver
        .observe(this._save())
        .do(experiment => this._router.navigate(['/desk', 'experiments', experiment.id]))
        .subscribe(),
    );
  }

  private _createForm(): void {
    this.form = new AppFormGroup({
      name: new FormControl(null, Validators.required),
      description: new FormControl(null),
      type: new FormControl(this.typeOptions.length === 1 ? this.typeOptions[0].id : null, Validators.required),
    });
  }

  private _save(): Observable<IExperimentFull> {
    return this._experimentService.create({
      ...this.form.value,
      pipeline: this.pipelineForm.data,
    });
  }
}
