import { ModuleWithProviders, NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import { AlbumsModule } from '../albums/albums.module';
import { CoreModule } from '../core/core.module';
import { ASSET_BASE_ROUTE } from '../core/interfaces/common.interface';
import { TablesModule } from '../tables/tables.module';
import { TrainModule } from '../train/train.module';

import { DeployContextComponent } from './deploy-context.component';
import { DeployCreateComponent } from './deploy-create.component';
import { deployModuleAssetURLMap } from './deploy.routes';
import { OnlineAPICreatePipelineComponent } from './online-api-create-pipeline.component';
import { OnlineAPICreateComponent } from './online-api-create.component';
import { OnlineAPIViewComponent } from './online-api-view.component';
import { OnlineAPIService } from './online-api.service';
import { OnlineJobCreateComponent } from './online-job-create.component';
import { OnlineJobCVOptionsComponent } from './online-job-cv-options.component';
import { OnlineJobViewComponent } from './online-job-view.component';
import { OnlineJobService } from './online-job.service';
import { ScriptDeploymentCreateComponent } from './script-deployment-create.component';
import { ScriptDeploymentCv3stlDetectionComponent } from './script-deployment-cv-3stl-detection.component';
import { ScriptDeploymentPipelineParamsComponent } from './script-deployment-pipeline-params.component';
import { ScriptDeploymentViewComponent } from './script-deployment-view.component';
import { ScriptDeploymentService } from './script-deployment.service';

@NgModule({
  imports: [
    RouterModule,
    BrowserModule,
    FormsModule,
    ReactiveFormsModule,
    // deep cortex modules
    CoreModule,
    TablesModule,
    TrainModule,
    AlbumsModule,
  ],
  declarations: [
    DeployContextComponent,
    DeployCreateComponent,
    OnlineJobCreateComponent,
    OnlineJobViewComponent,
    OnlineJobCVOptionsComponent,
    OnlineAPICreateComponent,
    OnlineAPIViewComponent,
    ScriptDeploymentCreateComponent,
    ScriptDeploymentViewComponent,
    ScriptDeploymentPipelineParamsComponent,
    ScriptDeploymentCv3stlDetectionComponent,
    OnlineAPICreatePipelineComponent,
  ],
  exports: [
  ],
})
export class DeployModule {
  static forRoot(): ModuleWithProviders {
    return {
      ngModule: DeployModule,
      providers: [
        OnlineJobService,
        OnlineAPIService,
        ScriptDeploymentService,
        {
          provide: ASSET_BASE_ROUTE,
          useValue: deployModuleAssetURLMap,
          multi: true,
        },
      ],
    };
  }
}
