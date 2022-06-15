import { ModuleWithProviders, NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import { CoreModule } from '../core/core.module';
import { ASSET_BASE_ROUTE } from '../core/interfaces/common.interface';
import { TablesModule } from '../tables/tables.module';
import { TrainModule } from '../train/train.module';

import { DIAAContextComponent } from './diaa-context.component';
import { DIAACreateComponent } from './diaa-create.component';
import { DIAAAIRSummaryComponent } from './diaa-model-summary.component';
import { DIAASpecificationComponent } from './diaa-specification.component';
import { DIAASummaryValueComponent } from './diaa-summary-value.component';
import { DIAASummaryComponent } from './diaa-summary.component';
import { DIAAViewAnalysisComponent } from './diaa-view-analysis.component';
import { DIAAViewComponent } from './diaa-view.component';
import { DecileRangePipe } from './diaa.pipes';
import { diaaModuleAssetURLMap } from './diaa.routes';
import { DIAAService } from './diaa.service';

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
  ],
  declarations: [
    DIAACreateComponent,
    DIAASpecificationComponent,
    DIAAAIRSummaryComponent,
    DIAAViewComponent,
    DIAAViewAnalysisComponent,
    DIAASummaryComponent,
    DIAASummaryValueComponent,
    DIAAContextComponent,
    DecileRangePipe,
  ],
})
export class DIAAModule {
  static forRoot(): ModuleWithProviders {
    return {
      ngModule: DIAAModule,
      providers: [
        DIAAService,
        {
          provide: ASSET_BASE_ROUTE,
          useValue: diaaModuleAssetURLMap,
          multi: true,
        },
      ],
    };
  }
}
