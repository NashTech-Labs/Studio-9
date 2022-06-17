import { Component, ViewChild } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import * as _ from 'lodash';
import { Observable } from 'rxjs/Observable';
import { combineLatest } from 'rxjs/observable/combineLatest';
import { switchMap } from 'rxjs/operators/switchMap';
import { Subject } from 'rxjs/Subject';

import config from '../config';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { IAsset } from '../core/interfaces/common.interface';
import { AppFormGroup } from '../utils/forms';

import { DeployCreateForm } from './deploy-create.component';
import { IOnlineAPI, IOnlineAPICreate } from './online-api.interface';
import { OnlineAPIService } from './online-api.service';

export interface OnlineAPIPramsForm<T extends IOnlineAPI.Type> {
  validity: Observable<boolean>;
  value: IOnlineAPI.Params<T>;
}

@Component({
  selector: 'deploy-online-api-create',
  template: `
    <div class="row">
      <div class="col-md-6">
        <app-input [label]="'API Name'" [control]="form.controls['name']"></app-input>
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
          [inputLabel]="'Pipeline/Model'"
          [value]="null"
          (valueChange)="onAssetSelect($event)"
          [available]="['${IAsset.Type.PIPELINE}', '${IAsset.Type.MODEL}', '${IAsset.Type.CV_MODEL}']"
        ></library-selector>
      </div>
    </div>
    <ng-container [ngSwitch]="form.controls.target.value?.type">
      <deploy-online-api-create-pipeline #paramsForm
        *ngSwitchCase="'${IAsset.Type.PIPELINE}'"
        [pipelineId]="form.controls.target.value?.id"
      ></deploy-online-api-create-pipeline>
    </ng-container>
  `,
})
export class OnlineAPICreateComponent implements DeployCreateForm {
  readonly config = config;
  readonly form: AppFormGroup<{
    name: FormControl;
    description: FormControl;
    target: FormControl,
    enabled: FormControl,
    //secret: FormControl,
  }>;

  readonly validity: Observable<boolean>;
  private _paramsValiditySubject = new Subject<Observable<boolean>>();
  private _paramsForm: OnlineAPIPramsForm<IOnlineAPI.Type>;

  constructor(
    private service: OnlineAPIService,
    private router: Router,
  ) {
    this.form = new AppFormGroup({
      name: new FormControl(null, Validators.required),
      description: new FormControl(null),
      target: new FormControl(null, Validators.required),
      enabled: new FormControl(true),
      //secret: new FormControl(null, Validators.required),
    });

    this.validity =
      combineLatest(
        this._paramsValiditySubject.pipe(switchMap(_ => _)),
        this.form.statusChanges.map(() => this.form.valid),
      ).map(valids => _.every(valids));

    this._paramsValiditySubject.next(Observable.of(true));
  }

  @ViewChild('paramsForm')
  set paramsForm(formComponent: OnlineAPIPramsForm<IOnlineAPI.Type>) {
    this._paramsForm = formComponent;
    this._paramsValiditySubject.next(formComponent ? formComponent.validity : Observable.of(true));
  }

  onAssetSelect($event: LibrarySelectorValue) {
    this.form.controls.target.setValue({ id: $event.id, type: $event.entity });
  }

  submit() {
    const formValue = this.form.value;
    const request: IOnlineAPICreate = {
      enabled: !!formValue.enabled,
      name: formValue.name,
      description: formValue.description,
      secret: 'foo', // randomly generate
      target: formValue.target,
      params: this._paramsForm ? this._paramsForm.value : null,
    };

    const submission = this.service.create(request);

    submission.subscribe((api: IOnlineAPI<any>) =>
      this.router.navigate(['/desk', 'deploy', 'online-api', api.id]));

    return submission;
  }
}
