import { Component } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import * as _ from 'lodash';
import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Observable } from 'rxjs/Observable';
import { combineLatest } from 'rxjs/observable/combineLatest';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { AppFormGroup } from '../utils/forms';

import { DeployCreateForm } from './deploy-create.component';
import { deployConfig } from './deploy.config';
import { IScriptDeployment } from './script-deployment.interface';
import { ScriptDeploymentService } from './script-deployment.service';


@Component({
  selector: 'deploy-script-deployment-create',
  template: `
    <div class="row">
      <div class="col-md-6">
        <app-input [label]="'Deployment Name'" [control]="form.controls.name"></app-input>
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
          [label]="'Pipeline'"
          [control]="form.controls['mode']"
          [options]="modes"
        ></app-select>
      </div>
      <div class="col-md-6">
        <app-select
          [label]="'Hardware'"
          [control]="form.controls['hardwareMode']"
          [options]="hardwareModes"
        ></app-select>
      </div>
    </div>
    <app-form-group [caption]="'Pipeline params'" *ngIf="form.value['mode']">
      <deploy-script-deployment-pipeline-params
        [mode]="form.value['mode']"
        (paramsChange)="onParamsChange($event)"
        (validityChange)="onParamsValidityChange($event)"
      ></deploy-script-deployment-pipeline-params>
    </app-form-group>
  `,
})
export class ScriptDeploymentCreateComponent implements DeployCreateForm {
  readonly config = config;

  readonly form: AppFormGroup<{
    name: FormControl;
    description: FormControl;
    mode: FormControl,
    hardwareMode: FormControl,
    params: FormControl,
  }>;

  readonly validity: Observable<boolean>;

  readonly modes = AppSelectOptionData.fromList(
    deployConfig.scriptDeployment.mode.list,
    deployConfig.scriptDeployment.mode.labels,
    deployConfig.scriptDeployment.mode.disabled,
  );

  readonly hardwareModes = AppSelectOptionData.fromList(
    deployConfig.scriptDeployment.hardwareMode.list,
    deployConfig.scriptDeployment.hardwareMode.labels,
    deployConfig.scriptDeployment.hardwareMode.disabled,
  );

  private _paramsValidity = new BehaviorSubject(false);

  constructor(
    private _sdService: ScriptDeploymentService,
    private _router: Router,
  ) {
    this.form = new AppFormGroup({
      name: new FormControl(null, Validators.required),
      description: new FormControl(null),
      mode: new FormControl(null, Validators.required),
      hardwareMode: new FormControl(null, Validators.required),
      params: new FormControl(null, Validators.required),
    });

    this.validity = combineLatest(
      this._paramsValidity,
      this.form.statusChanges.map(() => this.form.valid),
    ).map(valids => _.every(valids));
  }

  onParamsChange(params: IScriptDeployment.Params): void {
    this.form.controls.params.setValue(params);
  }

  onParamsValidityChange(valid: boolean): void {
    this._paramsValidity.next(valid);
  }

  submit(): Observable<IScriptDeployment> {
    const o = this._sdService.create(this.form.value);
    o.subscribe((sd: IScriptDeployment) => {
      this._router.navigate(['/desk', 'deploy', 'script-deployment', sd.id]);
    });

    return o;
  }
}
