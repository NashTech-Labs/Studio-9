import { HasUnsavedDataGuard } from '../core/core.guards';
import { DeskRoute } from '../core/desk.route';

import { CanvasContextComponent } from './canvas-context.component';
import { PipelineContextComponent } from './pipeline-context.component';
import { PipelineCreateComponent } from './pipeline-create.component';
import { PipelineComponent } from './pipeline.component';

export const pipelineRoutes: DeskRoute[] = [
  {
    path: 'pipelines',
    component: PipelineCreateComponent,
    sideComponent: PipelineContextComponent,
  },
  {
    path: 'pipelines/create',
    component: PipelineComponent,
    sideComponent: CanvasContextComponent,
    canDeactivate: [HasUnsavedDataGuard],
    data: {
      edit: true,
    },
  },
  {
    path: 'pipelines/:pipelineId',
    component: PipelineComponent,
    sideComponent: PipelineContextComponent,
  },
  {
    path: 'pipelines/:pipelineId/edit',
    component: PipelineComponent,
    sideComponent: CanvasContextComponent,
    canDeactivate: [HasUnsavedDataGuard],
    data: {
      edit: true,
    },
  },
];
