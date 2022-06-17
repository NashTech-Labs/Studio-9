import { DeskRoute } from '../core/desk.route';

import { ProcessContextComponent } from './process-context.component';
import { ProcessesListComponent } from './processes-list.component';

export const processesRoutes: DeskRoute[] = [
  {
    path: 'processes',
    component: ProcessesListComponent,
    sideComponent: ProcessContextComponent,
  },
];
