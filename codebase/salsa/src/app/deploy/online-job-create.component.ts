import { Component } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import * as _ from 'lodash';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Observable } from 'rxjs/Observable';
import { combineLatest } from 'rxjs/observable/combineLatest';

import config from '../config';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { AppFormGroup } from '../utils/forms';

import { DeployCreateForm } from './deploy-create.component';
import { IOnlineTriggeredJob } from './online-job.interface';
import { OnlineJobService } from './online-job.service';

@Component({
  selector: 'deploy-online-job-create',
  template: `
    <div class="row">
      <div class="col-md-6">
        <app-input [label]="'Online Job Name'" [control]="form.controls['name']"></app-input>
      </div>
      <div class="col-md-6">
        <app-check
          [label]="'Enabled'"
          [value]="true"
          [control]="form.controls.enabled"
        ></app-check>
      </div>
    </div>
    <div class="row">
      <div class="col-md-6">
        <app-description [control]="form.controls['description']" [editMode]="true"></app-description>
      </div>
    </div>
    <div class="row">
      <div class="col-md-6">
        <library-selector
          [inputLabel]="'Asset'"
          [value]="null"
          (valueChange)="onAssetSelect($event)"
          [available]="[config.asset.values.CV_MODEL]"
        ></library-selector>
      </div>
      <div class="col-md-12" *ngIf="form.value.target" [ngSwitch]="form.value.target?.type">
        <deploy-online-job-cv-options *ngSwitchCase="config.asset.values.CV_MODEL"
          [modelId]="form.value.target.id"
          (valueChanges)="receiveOptions($event)"
        ></deploy-online-job-cv-options>
      </div>
    </div>
  `,
})
export class OnlineJobCreateComponent implements DeployCreateForm {
  readonly config = config;
  readonly form: AppFormGroup<{
    name: FormControl;
    description: FormControl;
    target: FormControl,
    enabled: FormControl,
    options: FormControl
  }>;
  readonly validity: Observable<boolean>;

  private optionsValidity = new BehaviorSubject(false);

  constructor(private jobs: OnlineJobService, private router: Router) {
    this.form = new AppFormGroup({
      name: new FormControl(null, Validators.required),
      description: new FormControl(null),
      target: new FormControl(null, Validators.required),
      enabled: new FormControl(false),
      options: new FormControl(null, Validators.required),
    });

    this.validity = combineLatest(
      this.optionsValidity,
      this.form.statusChanges.map(() => this.form.valid),
    ).map(valids => _.every(valids));
  }

  receiveOptions([options, optionsValidity]: [IOnlineTriggeredJob.CreateOptions, boolean]) {
    this.form.controls.options.setValue(options);
    this.optionsValidity.next(optionsValidity);
  }

  onAssetSelect($event: LibrarySelectorValue) {
    this.form.controls.target.setValue({ id: $event.id, type: $event.entity });
  }

  submit() {
    const submission = this.jobs.create(this.form.value);

    submission.subscribe((job: IOnlineTriggeredJob) =>
      this.router.navigate(['/desk', 'deploy', 'online-job', job.id]));

    return submission;
  }
}
