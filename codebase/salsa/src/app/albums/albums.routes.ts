import { DeskRoute } from '../core/desk.route';
import { IAsset } from '../core/interfaces/common.interface';

import { AlbumAugmentComponent } from './album-augment.component';
import { AlbumContextComponent } from './album-context.component';
import { AlbumCreateComponent } from './album-create.component';
import { AlbumEditComponent } from './album-edit.component';
import { AlbumViewComponent } from './album-view.component';
import { TagImageComponent } from './tag-image.component';

export const albumsRoutes: DeskRoute[] = [
  {
    path: 'albums',
    sideComponent: AlbumContextComponent,
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: 'create',
      },
      {
        path: 'create',
        component: AlbumCreateComponent,
      },
      {
        path: 'augment',
        component: AlbumAugmentComponent,
      },
    ],
  },
  {
    path: 'albums/:albumId/edit',
    component: AlbumEditComponent,
    sideComponent: AlbumContextComponent,
  },
  {
    path: 'albums/:albumId',
    component: AlbumViewComponent,
    sideComponent: AlbumContextComponent,
    children: [
      {
        path: 'tag/:pictureId',
        component: TagImageComponent,
      },
    ],
  },
];

export const albumsModuleAssetURLMap = {
  [IAsset.Type.ALBUM]: ['/desk', 'albums'],
};
