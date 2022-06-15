import { HasUnsavedDataGuard } from '../core/core.guards';
import { DeskRoute } from '../core/desk.route';

import { UserContextComponent } from './user-context.component';
import { UserEditComponent } from './user-edit.component';
import { UserListComponent } from './user-list.component';

export const usersRoutes: DeskRoute[] = [
  {
    path: 'users',
    sideComponent: UserContextComponent,
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'manage',
      },
      {
        path: 'manage',
        children: [
          {
            path: '',
            pathMatch: 'full',
            component: UserListComponent,
          },
          {
            path: 'create',
            component: UserEditComponent,
            canDeactivate: [HasUnsavedDataGuard],
          },
        ],
      },
      {
        path: 'manage/:userId',
        component: UserEditComponent,
        canDeactivate: [HasUnsavedDataGuard],
      },
    ],
  },
];
