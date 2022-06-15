import { ModuleWithProviders, NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import { ChartsModule } from '../charts/charts.module';
import { CoreModule } from '../core/core.module';
import { ASSET_BASE_ROUTE } from '../core/interfaces/common.interface';
import { TablesModule } from '../tables/tables.module';
import { TrainModule } from '../train/train.module';

import { AttributesListPipe, ChartEditContextComponent, MetricsListPipe } from './chart-edit-context.component';
import { ChartEditFiltersComponent } from './chart-edit-filters.component';
import { ChartEditGroupsComponent } from './chart-edit-groups.component';
import { ChartEditComponent } from './chart-edit.component';
import { ChartFilterComponent } from './chart-filter.component';
import { ChartFiltersModalComponent } from './chart-filters-modal.component';
import { ChartGeneratorComponent } from './chart-generator.component';
import { ChartGeneratorsSelectorComponent } from './chart-generators-selector.component';
import { CHART_ENTRY_COMPONENTS } from './chart.factory';
import { ChartsDockComponent } from './charts-dock-panel.component';
import { CrossFilterBus } from './cross-filter-bus';
import { DashboardEditContextComponent } from './dashboard-edit-context.component';
import { DashboardEditLayoutComponent } from './dashboard-edit-layout.component';
import { DashboardEditStateFactory } from './dashboard-edit-state';
import { DashboardEditComponent } from './dashboard-edit.component';
import { DashboardService } from './dashboard.service';
import { GeoDataService } from './geodata.service';
import { GeospatialGlobeViewComponent } from './geospatial-globe-view.component';
import { VisualizeContextComponent } from './visualize-context.component';
import { VisualizeDataService } from './visualize-data.service';
import { VisualizeGeospatialContextComponent } from './visualize-geospatial-context.component';
import { VisualizeGeospatialComponent } from './visualize-geospatial.component';
import { VisualizeViewComponent } from './visualize-view.component';
import { DashboardFormResolve, visualizeModuleAssetURLMap } from './visualize.routes';

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
    ChartsModule,
  ],
  declarations: [
    VisualizeContextComponent,
    VisualizeGeospatialContextComponent,
    DashboardEditComponent,
    VisualizeViewComponent,
    DashboardEditContextComponent,
    ChartsDockComponent,
    ChartFilterComponent,
    ChartFiltersModalComponent,
    ChartGeneratorsSelectorComponent,
    ChartGeneratorComponent,
    ChartEditComponent,
    ChartEditFiltersComponent,
    ChartEditGroupsComponent,
    ChartEditContextComponent,
    DashboardEditLayoutComponent,
    VisualizeGeospatialComponent,
    GeospatialGlobeViewComponent,
    MetricsListPipe,
    AttributesListPipe,
    ...CHART_ENTRY_COMPONENTS,
  ],
  exports: [
    VisualizeContextComponent,
    DashboardEditComponent,
    VisualizeViewComponent,
  ],
  entryComponents: [...CHART_ENTRY_COMPONENTS],
})
export class VisualizeModule {
  static forRoot(): ModuleWithProviders {
    return {
      ngModule: VisualizeModule,
      providers: [
        GeoDataService,
        VisualizeDataService,
        DashboardService,
        DashboardFormResolve,
        CrossFilterBus,
        DashboardEditStateFactory,
        MetricsListPipe,
        AttributesListPipe,
        {
          provide: ASSET_BASE_ROUTE,
          useValue: visualizeModuleAssetURLMap,
          multi: true,
        },
      ],
    };
  }
}
