import {
  ModuleWithProviders,
  NgModule,
} from '@angular/core';
import {
  FormsModule,
  ReactiveFormsModule,
} from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import config from '../config';
import { CoreUIModule } from '../core-ui/core-ui.module';
import { CoreModule } from '../core/core.module';
import { IAsset } from '../core/interfaces/common.interface';
import { IEvent } from '../core/services/event.service';
import {
  LIBRARY_SECTIONS,
  LibrarySectionDefinition,
} from '../library/library.interface';

import { DatasetFileOperationsComponent } from './dataset-file-operations';
import { DatasetFilesListComponent } from './dataset-files-list.component';
import { DatasetViewComponent } from './dataset-view.component';
import { BinaryDataset } from './dataset.interfaces';
import { BinaryDatasetService } from './dataset.service';
import { ExportDatasetModalComponent } from './export-dataset.modal.component';
import { UploadFilesToDatasetModalComponent } from './upload-files-to-dataset.modal.component';

@NgModule({
  imports: [
    RouterModule,
    BrowserModule,
    FormsModule,
    ReactiveFormsModule,
    // deep cortex modules
    CoreModule,
    CoreUIModule,
  ],
  declarations: [
    DatasetViewComponent,
    DatasetFilesListComponent,
    DatasetFileOperationsComponent,
    UploadFilesToDatasetModalComponent,
    ExportDatasetModalComponent,
  ],
  exports: [
  ],
  entryComponents: [
    UploadFilesToDatasetModalComponent,
    ExportDatasetModalComponent,
    DatasetViewComponent,
  ],
})
export class DatasetModule {
  static forRoot(): ModuleWithProviders {
    return {
      ngModule: DatasetModule,
      providers: [
        BinaryDatasetService,
        {
          provide: LIBRARY_SECTIONS,
          deps: [BinaryDatasetService],
          useFactory: (service: BinaryDatasetService): LibrarySectionDefinition<BinaryDataset> => {
            return {
              service,
              assetType: IAsset.Type.DATASET,
              icon: 'glyphicon glyphicon-duplicate',
              inProjects: true,
              actions: {},
              sidebarActions: [
                {
                  caption: 'Upload Dataset Files',
                  modalClass: UploadFilesToDatasetModalComponent,
                },
              ],
              bulkOperations: [
                {
                  name: 'Export',
                  iconClass: 'glyphicon glyphicon-cloud-upload',
                  isAvailable: ExportDatasetModalComponent.isAvailable,
                  modalClass: ExportDatasetModalComponent,
                },
              ],
              viewComponent: DatasetViewComponent,
              reloadOn: IEvent.Type.UPDATE_DATASET_LIST,
              statusesDefinition: config.binaryDataset.status,
              completeStatus: BinaryDataset.Status.IDLE,
            };
          },
          multi: true,
        },
      ],
    };
  }
}

