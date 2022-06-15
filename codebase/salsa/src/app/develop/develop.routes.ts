import { HasUnsavedDataGuard } from '../core/core.guards';
import { DeskRoute } from '../core/desk.route';
import { IAsset } from '../core/interfaces/common.interface';

import { S9ProjectCreateComponent } from './s9-project-create.component';
import { S9ProjectFilesComponent } from './s9-project-files.component';
import { S9ProjectPackagesComponent } from './s9-project-packages.component';
import { S9ProjectViewComponent } from './s9-project-view.component';
import { DevelopContextComponent } from './develop-context.component';
import { PackagesListContainerComponent } from './packages-list-container.component';

export const developRoutes: DeskRoute[] = [
  {
    path: 'develop',
    sideComponent: DevelopContextComponent,
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'projects',
      },
      {
        path: 'projects',
        children: [
          {
            path: '',
            pathMatch: 'full',
            redirectTo: 'create',
          },
          {
            path: 'create',
            component: S9ProjectCreateComponent,
          },
          {
            path: ':projectId',
            component: S9ProjectViewComponent,
          },
          {
            path: ':projectId/files',
            component: S9ProjectFilesComponent,
            canDeactivate: [HasUnsavedDataGuard],
          },
          {
            path: ':projectId/packages',
            component: S9ProjectPackagesComponent,
          },
        ],
      },
      {
        path: 'packages',
        component: PackagesListContainerComponent,
      },
    ],
  },
];

export const developModuleAssetURLMap = {
  [IAsset.Type.S9_PROJECT]: ['/desk', 'develop', 'projects'],
};
