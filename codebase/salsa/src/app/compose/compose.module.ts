import { ModuleWithProviders, NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import { CoreModule } from '../core/core.module';
import { ASSET_BASE_ROUTE } from '../core/interfaces/common.interface';
import { TablesModule } from '../tables/tables.module';

import { ClusterGraphDirective } from './cluster-graph.directive';
import { ClusteringResultsComponent } from './clustering-results.component';
import { composeModuleAssetURLMap } from './compose.routes';
import { FlowComposeComponent } from './flow-compose.component';
import { FlowContextComponent } from './flow-context.component';
import { FlowCreateComponent } from './flow-create.component';
import { FlowEditComponent } from './flow-edit.component';
import { FlowGraphComponent } from './flow-graph.component';
import { FlowGraphDirective } from './flow-graph.directive';
import { FlowInfoComponent } from './flow-info.component';
import { FlowLayoutComponent } from './flow-layout.component';
import { FlowTableViewComponent } from './flow-table-view.component';
import { FlowService } from './flow.service';
import { FlowstepCodeDirective } from './flowstep-code.directive';
import { FlowstepEditFormComponent } from './flowstep-edit-form.component';
import { FlowstepEditOptionsAggregateComponent } from './flowstep-edit-o-aggregate.component';
import { FlowstepEditOptionsClusterComponent } from './flowstep-edit-o-cluster.component';
import { FlowstepEditOptionsFilterComponent } from './flowstep-edit-o-filter.component';
import { FlowstepEditOptionsGeoJoinConditionComponent } from './flowstep-edit-o-geojoin-condition.component';
import { FlowstepEditOptionsGeoJoinGeometryComponent } from './flowstep-edit-o-geojoin-geometry.component';
import { FlowstepEditOptionsGeoJoinComponent } from './flowstep-edit-o-geojoin.component';
import { FlowstepEditOptionsInsertComponent } from './flowstep-edit-o-insert.component';
import { FlowstepEditOptionsJoinComponent } from './flowstep-edit-o-join.component';
import { FlowstepEditOptionsMapComponent } from './flowstep-edit-o-map.component';
import { FlowstepEditOptionsQueryComponent } from './flowstep-edit-o-query.component';
import { FlowstepEditOptionsWindowComponent } from './flowstep-edit-o-window.component';
import { FlowstepEditComponent } from './flowstep-edit.component';
import { FlowstepNavigationComponent } from './flowstep-navigation.component';
import { FlowstepOptionsPassColumnsComponent } from './flowstep-options-pass-columns.component';
import { FlowstepService } from './flowstep.service';


@NgModule({
  imports: [
    RouterModule,
    BrowserModule,
    FormsModule,
    ReactiveFormsModule,
    // deep cortex modules
    CoreModule,
    TablesModule,
  ],
  declarations: [
    FlowLayoutComponent,
    FlowContextComponent,
    FlowComposeComponent,
    FlowGraphComponent,
    FlowCreateComponent,
    FlowEditComponent,
    FlowstepEditComponent,
    FlowstepEditFormComponent,
    FlowstepEditOptionsInsertComponent,
    FlowstepEditOptionsAggregateComponent,
    FlowstepEditOptionsJoinComponent,
    FlowstepEditOptionsClusterComponent,
    FlowstepEditOptionsQueryComponent,
    FlowstepEditOptionsFilterComponent,
    FlowstepEditOptionsWindowComponent,
    FlowstepEditOptionsMapComponent,
    FlowstepEditOptionsGeoJoinComponent,
    FlowstepEditOptionsGeoJoinConditionComponent,
    FlowstepEditOptionsGeoJoinGeometryComponent,
    FlowstepOptionsPassColumnsComponent,
    FlowstepNavigationComponent,
    FlowInfoComponent,
    FlowTableViewComponent,
    FlowGraphDirective,
    ClusteringResultsComponent,
    ClusterGraphDirective,
    FlowstepCodeDirective,
  ],
  exports: [
    FlowstepEditOptionsInsertComponent,
    FlowstepEditOptionsAggregateComponent,
    FlowstepEditOptionsJoinComponent,
    FlowstepEditOptionsClusterComponent,
    FlowstepEditOptionsQueryComponent,
    FlowstepEditOptionsFilterComponent,
    FlowstepEditOptionsWindowComponent,
    FlowstepEditOptionsMapComponent,
    FlowstepEditOptionsGeoJoinComponent,
    FlowstepEditOptionsGeoJoinConditionComponent,
    FlowstepEditOptionsGeoJoinGeometryComponent,

    FlowstepEditFormComponent,
    FlowGraphDirective,
  ],
})
export class ComposeModule {
  static forRoot(): ModuleWithProviders {
    return {
      ngModule: ComposeModule,
      providers: [
        FlowService,
        FlowstepService,
        {
          provide: ASSET_BASE_ROUTE,
          useValue: composeModuleAssetURLMap,
          multi: true,
        },
        //{
        //  provide: LIBRARY_SECTIONS,
        //  deps: [FlowService],
        //  useFactory: (service: FlowService): LibrarySectionDefinition<IFlow> => {
        //    return {
        //      service,
        //      assetType: IAsset.Type.FLOW,
        //      icon: 'iconapp iconapp-flows',
        //      inProjects: true,
        //      actions: {},
        //      baseRoute: ['/desk', 'flows'],
        //      reloadOn: IEvent.Type.UPDATE_FLOW_LIST,
        //      statusesDefinition: config.flow.status,
        //      completeStatus: IFlow.Status.DONE,
        //      features: [Feature.COMPOSE_MODULE],
        //      sharable: true,
        //      sidebarActions: [
        //        {
        //          caption: 'Create Flow',
        //          navigateTo: ['/desk', 'flows', 'create'],
        //        },
        //      ],
        //    };
        //  },
        //  multi: true,
        //},
      ],
    };
  }
}

// Features
declare module '../core/interfaces/feature-toggle.interface' {
  export const enum Feature {
    COMPOSE_MODULE = 'COMPOSE_MODULE',
    INSERT_TRANSFORMER = 'INSERT_TRANSFORMER',
    AGGREGATE_TRANSFORMER = 'AGGREGATE_TRANSFORMER',
    JOIN_TRANSFORMER = 'JOIN_TRANSFORMER',
    CLUSTER_TRANSFORMER = 'CLUSTER_TRANSFORMER',
    QUERY_TRANSFORMER = 'QUERY_TRANSFORMER',
    FILTER_TRANSFORMER = 'FILTER_TRANSFORMER',
    WINDOW_TRANSFORMER = 'WINDOW_TRANSFORMER',
    MAP_TRANSFORMER = 'MAP_TRANSFORMER',
    GEOJOIN_TRANSFORMER = 'GEOJOIN_TRANSFORMER',
  }
}
