import { SpinnerComponent } from '../core-ui/components/spinner.component';
import { IfMocksGuard } from '../core/core.mocks-only';
import { DeskRoute } from '../core/desk.route';
import { IAsset } from '../core/interfaces/common.interface';

import { DeployContextComponent } from './deploy-context.component';
import { DeployCreateComponent } from './deploy-create.component';
import { OnlineAPIViewComponent } from './online-api-view.component';
import { OnlineJobViewComponent } from './online-job-view.component';
import { ScriptDeploymentViewComponent } from './script-deployment-view.component';

export const deployRoutes: DeskRoute[] = [
  {
    path: 'deploy',
    component: SpinnerComponent,
    sideComponent: DeployContextComponent,
  },
  {
    path: 'deploy/create',
    component: DeployCreateComponent,
    sideComponent: DeployContextComponent,
  },
  {
    path: 'deploy/online-job/:onlineJobId',
    component: OnlineJobViewComponent,
    sideComponent: DeployContextComponent,
  },
  {
    path: 'deploy/online-api/:itemId',
    canActivate: [IfMocksGuard],
    component: OnlineAPIViewComponent,
    sideComponent: DeployContextComponent,
  },
  {
    path: 'deploy/script-deployment/:itemId',
    component: ScriptDeploymentViewComponent,
    sideComponent: DeployContextComponent,
  },
];

export const deployModuleAssetURLMap = {
  [IAsset.Type.ONLINE_API]: ['/desk', 'deploy', 'online-api'],
  [IAsset.Type.ONLINE_JOB]: ['/desk', 'deploy', 'online-job'],
  [IAsset.Type.SCRIPT_DEPLOYMENT]: ['/desk', 'deploy', 'script-deployment'],
};
