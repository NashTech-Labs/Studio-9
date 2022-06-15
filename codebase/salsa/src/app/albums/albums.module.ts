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

import { AlbumAugmentComponent } from './album-augment.component';
import { AlbumAugmentationParamsComponent } from './album-augmentation-params.component';
import { AlbumContextComponent } from './album-context.component';
import { AlbumCreateComponent } from './album-create.component';
import { AlbumEditComponent } from './album-edit.component';
import { AlbumViewEmbedComponent } from './album-view-embed.component';
import { AlbumViewComponent } from './album-view.component';
import { IAlbum } from './album.interface';
import { AlbumService } from './album.service';
import { AlbumsMergeModalComponent } from './albums-merge-modal.component';
import { albumsModuleAssetURLMap } from './albums.routes';
import { AlbumsAugmentationTimeSpentSummaryComponent } from './augmentation-timespent-summary.component';
import { PictureOperationsComponent } from './picture-operations.component';
import { PicturePreviewComponent } from './picture-preview-modal.component';
import { PictureViewComponent } from './picture-view.component';
import { PicturesCloneModalComponent } from './pictures-clone-modal.component';
import { TagImageComponent } from './tag-image.component';
import { TagImageDirective } from './tag-image.directive';
import { UploadImagesToAlbumModalComponent } from './upload-images-to-album-modal.component';
import { UploadLabelsToAlbumModalComponent } from './upload-labels-to-album-modal.component';

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
    AlbumViewComponent,
    AlbumContextComponent,
    AlbumCreateComponent,
    AlbumAugmentComponent,
    AlbumAugmentationParamsComponent,
    AlbumViewEmbedComponent,
    PicturePreviewComponent,
    PictureViewComponent,
    TagImageComponent,
    TagImageDirective,
    UploadImagesToAlbumModalComponent,
    AlbumEditComponent,
    UploadLabelsToAlbumModalComponent,
    AlbumsMergeModalComponent,
    PictureOperationsComponent,
    PicturesCloneModalComponent,
    AlbumsAugmentationTimeSpentSummaryComponent,
  ],
  exports: [
    AlbumViewEmbedComponent,
    AlbumAugmentationParamsComponent,
  ],
  entryComponents: [
    UploadImagesToAlbumModalComponent,
    AlbumsMergeModalComponent,
  ],
})
export class AlbumsModule {
  static forRoot(): ModuleWithProviders {
    return {
      ngModule: AlbumsModule,
      providers: [
        AlbumService,
        {
          provide: ASSET_BASE_ROUTE,
          useValue: albumsModuleAssetURLMap,
          multi: true,
        },
        {
          provide: LIBRARY_SECTIONS,
          deps: [AlbumService],
          useFactory: (service: AlbumService): LibrarySectionDefinition<IAlbum> => {
            return {
              service,
              assetType: IAsset.Type.ALBUM,
              icon: 'glyphicon glyphicon-picture',
              inProjects: true,
              actions: {},
              baseRoute: ['/desk', 'albums'],
              reloadOn: IEvent.Type.UPDATE_ALBUM_LIST,
              statusesDefinition: config.album.status,
              completeStatus: IAlbum.Status.ACTIVE,
              features: [Feature.ALBUMS_MODULE],
              columns: [
                {name: 'Type', get: (_: IAlbum) => config.album.type.labels[_.type], style: 'width: 12%'},
                {name: 'Label Mode', get: (_: IAlbum) => config.album.labelMode.labels[_.labelMode], style: 'width: 22%'},
              ],
              selectorColumns: [
                {name: 'Label Mode', get: (_: IAlbum) => config.album.labelMode.labels[_.labelMode], style: 'width: 22%'},
              ],
              sharable: true,
              sidebarActions: [
                {
                  caption: 'Create Album',
                  navigateTo: ['/desk', 'albums', 'create'],
                },
                {
                  caption: 'Upload Images/Video',
                  modalClass: UploadImagesToAlbumModalComponent,
                },
              ],
              bulkOperations: [
                {
                  name: 'Merge',
                  iconClass: 'imgaction imgaction-clone',
                  isAvailable: (items) => service.isMergeAvailable(items),
                  modalClass: AlbumsMergeModalComponent,
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
    ALBUMS_MODULE = 'ALBUMS_MODULE',
  }
}
