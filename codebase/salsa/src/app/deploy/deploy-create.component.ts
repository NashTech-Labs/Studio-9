import { Component, ViewChild } from '@angular/core';

import { Observable } from 'rxjs/Observable';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { ifMocks } from '../core/core.mocks-only';
import { IAsset } from '../core/interfaces/common.interface';
import { ActivityObserver } from '../utils/activity-observer';

export interface DeployCreateForm {
  validity: Observable<boolean>;
  submit(): Observable<any>;
}

@Component({
  template: `
    <asset-operations [type]="config.asset.values.ONLINE_JOB" [selectedItems]="[]">
    </asset-operations>
    <form>
      <div class="row">
        <div class="col-md-6">
          <app-select label="Deploy Type" [options]="deployTypes" [(value)]="selectedDeployType"></app-select>
        </div>
        <div class="col-md-6">
          <div class="pull-right">
            <button class="btn btn-md btn-apply"
              (click)="form && _savingObserver.observe(form.submit())"
              [disabled]="!(form?.validity | async) || (_savingObserver.active | async)">
              Create
            </button>
          </div>
        </div>
      </div>
      <ng-container [ngSwitch]="selectedDeployType">
        <deploy-online-job-create #createForm *ngSwitchCase="'${IAsset.Type.ONLINE_JOB}'"></deploy-online-job-create>
        <deploy-online-api-create #createForm *ngSwitchCase="'${IAsset.Type.ONLINE_API}'"></deploy-online-api-create>
        <deploy-script-deployment-create #createForm *ngSwitchCase="'${IAsset.Type.SCRIPT_DEPLOYMENT}'"></deploy-script-deployment-create>
      </ng-container>
    </form>
  `,
})
export class DeployCreateComponent {
  // TODO: add switching of deploy sub items creating
  readonly config = config;
  readonly deployTypes = AppSelectOptionData.fromList(
    [
      IAsset.Type.ONLINE_JOB,
      ...ifMocks([
        IAsset.Type.ONLINE_API,
        IAsset.Type.SCRIPT_DEPLOYMENT,
      ], []),
    ],
    config.asset.labels,
  );

  @ViewChild('createForm') readonly form: DeployCreateForm;
  readonly _savingObserver = new ActivityObserver();

  selectedDeployType: IAsset.Type = null;
}
