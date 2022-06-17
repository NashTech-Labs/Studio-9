import { DeskRoute } from '../core/desk.route';
import { IAsset } from '../core/interfaces/common.interface';
import { Feature } from '../core/interfaces/feature-toggle.interface';

import { FlowComposeComponent } from './flow-compose.component';
import { FlowContextComponent } from './flow-context.component';
import { FlowCreateComponent } from './flow-create.component';
import { FlowEditComponent } from './flow-edit.component';
import { FlowGraphComponent } from './flow-graph.component';
import { FlowInfoComponent } from './flow-info.component';
import { FlowLayoutComponent } from './flow-layout.component';
import { FlowTableViewComponent } from './flow-table-view.component';
import { FlowstepEditComponent } from './flowstep-edit.component';

export const composeRoutes: DeskRoute[] = [
  {
    path: 'flows',
    sideComponent: FlowContextComponent,
    features: [Feature.COMPOSE_MODULE],
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'compose',
      },
      {
        path: 'compose',
        component: FlowComposeComponent,
      },
      {
        path: 'create',
        component: FlowCreateComponent,
      },
    ],
  },
  {
    path: 'flows/:flowId',
    sideComponent: FlowContextComponent,
    features: [Feature.COMPOSE_MODULE],
    component: FlowLayoutComponent,
    children: [
      {
        path: '',
        redirectTo: 'info',
        pathMatch: 'full',
      },
      {
        path: 'info',
        component: FlowInfoComponent,
      },
      {
        path: 'graph',
        component: FlowGraphComponent,
      },
      {
        path: 'edit',
        component: FlowEditComponent,
      },
      {
        path: 'tables/:tableId',
        component: FlowTableViewComponent,
      },
      {
        path: 'steps/create',
        pathMatch: 'full',
        redirectTo: 'info',
      },
      {
        path: 'steps/create/:type',
        component: FlowstepEditComponent,
      },
      {
        path: 'steps/:stepId',
        component: FlowstepEditComponent,
      },
    ],
  },
];

export const composeModuleAssetURLMap = {
  [IAsset.Type.FLOW]: ['/desk', 'flows'],
};
