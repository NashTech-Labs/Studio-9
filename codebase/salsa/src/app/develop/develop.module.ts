import { ModuleWithProviders, NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import config from '../config';
import { CoreUIModule } from '../core-ui/core-ui.module';
import { CoreModule } from '../core/core.module';
import { ASSET_BASE_ROUTE, IAsset } from '../core/interfaces/common.interface';
import { IEvent } from '../core/services/event.service';
import { LIBRARY_SECTIONS, LibrarySectionDefinition } from '../library/library.interface';

import { BinaryViewerComponent } from './binary-viewer.component';
import { BuildS9ProjectModalComponent } from './build-s9-project-modal.component';
import { CodeEditorComponent } from './code-editor.component';
import { S9ProjectCreateComponent } from './s9-project-create.component';
import { S9ProjectFilesComponent } from './s9-project-files.component';
import { S9ProjectPackagesComponent } from './s9-project-packages.component';
import { S9ProjectSessionComponent } from './s9-project-session.component';
import { S9ProjectViewComponent } from './s9-project-view.component';
import { IS9Project } from './s9-project.interfaces';
import { S9ProjectService } from './s9-project.service';
import { DevelopContextComponent } from './develop-context.component';
import { developModuleAssetURLMap } from './develop.routes';
import { ImageViewerComponent } from './image-viewer.component';
import { NotebookViewerComponent } from './notebook-viewer.component';
import { PackageDetailsModalComponent } from './package-details-modal.component';
import { PackageOperationsComponent } from './package-operations.component';
import { PackageOperatorsListComponent } from './package-operators-list.component';
import { PackagePrimitivesListComponent } from './package-primitives-list.component';
import { PackageService } from './package.service';
import { PackagesListContainerComponent } from './packages-list-container.component';
import { PackagesListComponent } from './packages-list.component';
import { PublishS9ProjectModalComponent } from './publish-s9-project-modal.component';

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
    DevelopContextComponent,
    S9ProjectCreateComponent,
    S9ProjectViewComponent,
    S9ProjectSessionComponent,
    PackagesListComponent,
    PackageOperationsComponent,
    PackagesListContainerComponent,
    S9ProjectFilesComponent,
    S9ProjectPackagesComponent,
    PackageDetailsModalComponent,
    PackageOperatorsListComponent,
    PackagePrimitivesListComponent,
    PackagesListComponent,
    PublishS9ProjectModalComponent,
    BuildS9ProjectModalComponent,
    NotebookViewerComponent,
    ImageViewerComponent,
    CodeEditorComponent,
    BinaryViewerComponent,
  ],
  entryComponents: [
    PublishS9ProjectModalComponent,
    BuildS9ProjectModalComponent,
  ],
})
export class DevelopModule {
  static forRoot(): ModuleWithProviders {
    return {
      ngModule: DevelopModule,
      providers: [
        S9ProjectService,
        PackageService,
        {
          provide: LIBRARY_SECTIONS,
          deps: [S9ProjectService],
          useFactory: (service: S9ProjectService): LibrarySectionDefinition<IS9Project> => {
            return {
              service,
              assetType: IAsset.Type.S9_PROJECT,
              icon: 'glyphicon glyphicon-picture',
              inProjects: true,
              actions: {
                'files': {
                  name: 'Files',
                  iconClass: 'glyphicon glyphicon-file',
                },
                'packages': {
                  name: 'Packages',
                  iconClass: 'glyphicon glyphicon-th-large',
                },
              },
              baseRoute: ['/desk', 'develop', 'projects'],
              reloadOn: IEvent.Type.UPDATE_S9_PROJECT_LIST,
              statusesDefinition: config.s9Project.status,
              completeStatus: IS9Project.Status.IDLE, // should be updated when the new statuses are added
              columns: [
                {name: 'Name', get: (_: IS9Project) => _.name, style: 'width: 12%'},
              ],
              selectorColumns: [
                {name: 'Name', get: (_: IS9Project) => _.name, style: 'width: 12%'},
              ],
              sharable: false,
              sidebarActions: [
                {
                  caption: 'Create S9 Project',
                  navigateTo: ['/desk', 'develop', 'projects', 'create'],
                },
              ],
              bulkOperations: [
                {
                  name: 'Publish',
                  iconClass: 'glyphicon glyphicon-cloud-upload',
                  isAvailable: (items) => service.isPublishingAvailable(items),
                  modalClass: PublishS9ProjectModalComponent,
                },
                {
                  name: 'Build',
                  iconClass: 'glyphicon glyphicon-repeat',
                  isAvailable: (items) => service.isBuildingAvailable(items),
                  modalClass: BuildS9ProjectModalComponent,
                },
              ],
            };
          },
          multi: true,
        },
        {
          provide: ASSET_BASE_ROUTE,
          useValue: developModuleAssetURLMap,
          multi: true,
        },
      ],
    };
  }
}
