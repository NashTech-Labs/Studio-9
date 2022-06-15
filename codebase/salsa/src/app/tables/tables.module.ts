import { ModuleWithProviders, NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import config from '../config';
import { CoreModule } from '../core/core.module';
import { ASSET_BASE_ROUTE, IAsset } from '../core/interfaces/common.interface';
import { Feature } from '../core/interfaces/feature-toggle.interface';
import { IEvent } from '../core/services/event.service';
import { LIBRARY_SECTIONS, LibrarySectionDefinition } from '../library/library.interface';
import { libraryModuleAssetURLMap } from '../library/library.routes';

import { TableColumnHistogramDirective } from './table-column-histogram.directive';
import { TableScatterPlotMatrixComponent } from './table-scatter-plot-matrix.component';
import { TableUploadModalComponent } from './table-upload-modal.component';
import { TableViewEmbeddableComponent } from './table-view-embed.component';
import { TableViewComponent } from './table-view.component';
import { ITable } from './table.interface';
import { TableService } from './table.service';
import { TableColumnDisplayNamePipe, TableColumnSelectOptionsPipe, TableNumberTitlePipe } from './tables.pipes';

@NgModule({
  imports: [
    RouterModule,
    BrowserModule,
    FormsModule,
    ReactiveFormsModule,
    // deep cortex modules
    CoreModule,
  ],
  declarations: [
    // Components
    TableViewComponent,
    TableViewEmbeddableComponent,
    TableUploadModalComponent,
    TableScatterPlotMatrixComponent,
    // Directives
    TableColumnHistogramDirective,
    // Pipes
    TableColumnSelectOptionsPipe,
    TableColumnDisplayNamePipe,
    TableNumberTitlePipe,
  ],
  exports: [
    // Components
    TableViewEmbeddableComponent,
    // Pipes
    TableColumnSelectOptionsPipe,
    TableColumnDisplayNamePipe,
  ],
  entryComponents: [
    TableViewComponent,
    TableUploadModalComponent,
  ],
})
export class TablesModule {
  static forRoot(): ModuleWithProviders {
    return {
      ngModule: TablesModule,
      providers: [
        TableService,
        {
          provide: ASSET_BASE_ROUTE,
          useValue: libraryModuleAssetURLMap,
          multi: true,
        },
        {
          provide: LIBRARY_SECTIONS,
          deps: [TableService],
          useFactory: (service: TableService): LibrarySectionDefinition<ITable> => {
            return {
              service,
              assetType: IAsset.Type.TABLE,
              icon: 'iconapp iconapp-tables',
              inProjects: true,
              actions: {},
              reloadOn: IEvent.Type.UPDATE_TABLE_LIST,
              statusesDefinition: config.table.status,
              completeStatus: ITable.Status.ACTIVE,
              features: [Feature.TABLES_MODULE],
              columns: [
                { name: 'Type', alias: 'datasetType', get: (_: ITable) => config.table.datasetType.labels[_.datasetType], style: 'width: 12%' },
              ],
              sharable: true,
              viewComponent: TableViewComponent,
              sidebarActions: [
                {
                  caption: 'Import Table',
                  modalClass: TableUploadModalComponent,
                },
              ],
            };
          },
          multi: true,
        },
      ],
    };
  }
}

// Features
declare module '../core/interfaces/feature-toggle.interface' {
  export const enum Feature {
    TABLES_MODULE = 'TABLES_MODULE',
    UPLOAD_TABLE = 'UPLOAD_TABLE',
  }
}
