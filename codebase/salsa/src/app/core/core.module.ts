import { ModuleWithProviders, NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import { CoreUIModule } from '../core-ui/core-ui.module';

import { AssetListComponent } from './components/asset-list.component';
import { AssetOperationsComponent } from './components/asset-operations.component';
import { CloneModalComponent } from './components/clone-modal.component';
import { ErrorIndicatorComponent } from './components/error-indicator.component';
import { LibrarySelectorComponent } from './components/library-selector.component';
import { ParameterValueControlComponent } from './components/parameter-value-control.component';
import { CombineProcessesPipe, ProcessIndicatorComponent } from './components/process-indicator.component';
import { ProjectContextModalComponent } from './components/project-context-modal.component';
import { ProjectContextComponent } from './components/project-context.component';
import { ProjectLinkModalComponent } from './components/project-link-modal.component';
import { S3BucketFormComponent } from './components/s3-bucket-form.component';
import { SaveToLibraryModalComponent } from './components/save-to-library.modal.component';
import { ShareModalComponent } from './components/share-modal.component';
import { SideAssetListComponent } from './components/side-asset-list.component';
import { IfMocksPipe, MocksOnlyDirective } from './core.mocks-only';
import { AppUserNamePipe, LoadAssetPipe } from './core.pipes';
import { AssetStatusColorDirective } from './directives/asset-status-color.directive';
import { FeatureToggleDirective } from './directives/feature-toggle.directive';
import { FileUploadDirective } from './directives/file-upload.directive';
import { ProjectBreadcrumbsDirective } from './directives/project-breadcrumbs.directive';
import { AssetURLService } from './services/asset-url.service';
import { EventService } from './services/event.service';
import { FeatureToggleGuard, FeatureToggleService } from './services/feature-toggle.service';
import { AppHttp } from './services/http.service';
import { NotificationService } from './services/notification.service';
import { ProcessService } from './services/process.service';
import { S3BucketService } from './services/s3bucket.service';
import { SharedResourceService } from './services/shared-resource.service';
import { StorageService } from './services/storage.service';
import { UserService } from './services/user.service';

const CORE_DECLARATIONS = [
  LibrarySelectorComponent,
  ProcessIndicatorComponent,
  ErrorIndicatorComponent,
  AssetOperationsComponent,
  ShareModalComponent,
  CloneModalComponent,
  ProjectLinkModalComponent,
  S3BucketFormComponent,
  SideAssetListComponent,
  ProjectContextComponent,
  ProjectContextModalComponent,
  SaveToLibraryModalComponent,
  ParameterValueControlComponent,
  // Pipes
  IfMocksPipe,
  AppUserNamePipe,
  CombineProcessesPipe,
  LoadAssetPipe,
  // Directives
  MocksOnlyDirective,
  FileUploadDirective,
  AssetStatusColorDirective,
  FeatureToggleDirective,
  ProjectBreadcrumbsDirective,
  AssetListComponent,
];

@NgModule({
  imports: [
    RouterModule,
    BrowserModule,
    FormsModule,
    ReactiveFormsModule,
    CoreUIModule,
  ],
  declarations: CORE_DECLARATIONS,
  exports: [CoreUIModule, ...CORE_DECLARATIONS],
})
export class CoreModule {
  static forRoot(): ModuleWithProviders {
    return {
      ngModule: CoreModule,
      providers: [
        AppHttp,
        StorageService,
        EventService,
        NotificationService,
        AssetURLService,
        ProcessService,
        SharedResourceService,
        S3BucketService,
        UserService,
        FeatureToggleService,
        FeatureToggleGuard,
      ],
    };
  }
}
