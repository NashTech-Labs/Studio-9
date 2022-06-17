import { IfMocksGuard } from '../core/core.mocks-only';
import { DeskRoute } from '../core/desk.route';
import { IAsset } from '../core/interfaces/common.interface';

import { DIAAContextComponent } from './diaa-context.component';
import { DIAACreateComponent } from './diaa-create.component';
import { DIAAViewComponent } from './diaa-view.component';

export const diaaRoutes: DeskRoute[] = [
  {
    path: 'diaa',
    sideComponent: DIAAContextComponent,
    canActivate: [IfMocksGuard],
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'create',
      },
      {
        path: 'create',
        component: DIAACreateComponent,
        canActivate: [IfMocksGuard],
      },
      {
        path: ':diaaId',
        component: DIAAViewComponent,
        canActivate: [IfMocksGuard],
      },
    ],
  },
];

export const diaaModuleAssetURLMap = {
  [IAsset.Type.DIAA]: ['/desk', 'diaa'],
};
