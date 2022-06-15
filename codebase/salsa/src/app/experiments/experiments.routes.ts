import { DeskRoute } from '../core/desk.route';
import { IAsset } from '../core/interfaces/common.interface';

import { ExperimentCreateComponent } from './experiment-create.component';
import { ExperimentViewComponent } from './experiment-view.component';
import { ExperimentsContextComponent } from './experiments-context.component';

export const experimentsRoutes: DeskRoute[] = [
  {
    path: 'experiments',
    sideComponent: ExperimentsContextComponent,
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'create',
      },
      {
        path: 'create',
        component: ExperimentCreateComponent,
      },
      {
        path: ':experimentId',
        component: ExperimentViewComponent,
      },
    ],
  },
];

export const experimentsAssetURLMap = {
  [IAsset.Type.EXPERIMENT]: ['/desk', 'experiments'],
};
