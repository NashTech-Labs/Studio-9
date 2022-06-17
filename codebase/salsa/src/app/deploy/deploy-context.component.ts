import { Component } from '@angular/core';

import { IEvent } from '../core/services/event.service';

import { deployConfig } from './deploy.config';
import { OnlineAPIService } from './online-api.service';
import { OnlineJobService } from './online-job.service';
import { ScriptDeploymentService } from './script-deployment.service';

@Component({
  selector: 'app-deploy-context',
  template: `
    <div class="group">
      <button type="button" class="btn btn-primary btn-alt btn-block"
        [routerLink]="['/desk', 'deploy', 'create']">
        Create New Deployment
      </button>
    </div>

    <core-project-context></core-project-context>

    <side-asset-list
      [caption]="'Online Prediction Jobs'"
      icon="iconapp iconapp-robots"
      [statusesDefinition]="deployConfig.onlineJob.status"
      [baseRoute]="['/desk', 'deploy', 'online-job']"
      [service]="onlineJobService"
      [reloadOn]="['${IEvent.Type.UPDATE_ONLINE_JOB_LIST}']"
    ></side-asset-list>

    <side-asset-list
      *mocksOnly="true"
      [caption]="'Online APIs'"
      icon="iconapp iconapp-robots"
      [statusesDefinition]="deployConfig.onlineAPI.status"
      [baseRoute]="['/desk', 'deploy', 'online-api']"
      [service]="onlineAPIService"
      [reloadOn]="['${IEvent.Type.UPDATE_ONLINE_API_LIST}']"
    ></side-asset-list>

    <side-asset-list
      *mocksOnly="true"
      [caption]="'External Deployments'"
      icon="iconapp iconapp-robots"
      [statusesDefinition]="deployConfig.scriptDeployment.status"
      [baseRoute]="['/desk', 'deploy', 'script-deployment']"
      [service]="scriptDeploymentService"
      [reloadOn]="['${IEvent.Type.UPDATE_SCRIPT_DEPLOYMENT_LIST}']"
    ></side-asset-list>
  `,
})
export class DeployContextComponent {
  readonly deployConfig = deployConfig;

  constructor(
    readonly onlineJobService: OnlineJobService,
    readonly onlineAPIService: OnlineAPIService,
    readonly scriptDeploymentService: ScriptDeploymentService,
  ) {}
}
