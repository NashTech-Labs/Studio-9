import { ModuleWithProviders, NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import { CoreModule } from '../core/core.module';
import { ASSET_BASE_ROUTE } from '../core/interfaces/common.interface';
import { TablesModule } from '../tables/tables.module';
import { TrainModule } from '../train/train.module';

import { OptimizationContextComponent } from './optimization-context.component';
import { OptimizationCreateComponent } from './optimization-create.component';
import { OptimizationViewComponent } from './optimization-view.component';
import { OptimizationService } from './optimization.service';
import { OptimizationResolve, optimizeModuleAssetURLMap } from './optimize.routes';

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
    TablesModule,
  ],
  declarations: [
    OptimizationCreateComponent,
    OptimizationViewComponent,
    OptimizationContextComponent,
  ],
  exports: [
  ],
})
export class OptimizeModule {
  static forRoot(): ModuleWithProviders {
    return {
      ngModule: OptimizeModule,
      providers: [
        OptimizationService,
        OptimizationResolve,
        {
          provide: ASSET_BASE_ROUTE,
          useValue: optimizeModuleAssetURLMap,
          multi: true,
        },
      ],
    };
  }
}
