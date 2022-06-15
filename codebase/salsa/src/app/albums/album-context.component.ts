import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { ISubscription } from 'rxjs/Subscription';

import config from '../config';
import { TObjectId } from '../core/interfaces/common.interface';
import { IEvent } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { ReactiveLoader } from '../utils/reactive-loader';

import { IAlbum } from './album.interface';
import { AlbumService } from './album.service';
import { UploadImagesToAlbumModalComponent } from './upload-images-to-album-modal.component';
import { UploadLabelsToAlbumModalComponent } from './upload-labels-to-album-modal.component';

@Component({
  selector: 'app-album-context',

  template: `
    <div class="group">
      <button type="button"
        class="btn btn-primary btn-block"
        [routerLink]="['/desk', 'albums', 'create']"
        routerLinkActive #albumsCreateActive="routerLinkActive"
        [ngClass]="{'btn-alt': !albumsCreateActive.isActive}"
      >Create Album</button>
      <button type="button"
        class="btn btn-primary btn-block"
        [routerLink]="['/desk', 'albums', 'augment']"
        routerLinkActive #albumsAugmentActive="routerLinkActive"
        [ngClass]="{'btn-alt': !albumsAugmentActive.isActive}"
      >Apply Data Augmentation</button>
      <button
        [disabled]="album && !(album | apply: _isUploadAvailable)"
        type="button"
        class="btn btn-primary btn-alt btn-block"
        (click)="openUploadModal()"
      >Upload Images/Video</button>

      <button
        [disabled]="!album || !(album | apply: _isUploadAvailable)"
        type="button"
        class="btn btn-primary btn-alt btn-block"
        (click)="openUploadLabelsModal()"
      >Upload Labels</button>
    </div>
    <upload-images-to-album-modal #uploadModal></upload-images-to-album-modal>
    <upload-labels-to-album-modal #uploadLabelsModal></upload-labels-to-album-modal>

    <core-project-context></core-project-context>

    <side-asset-list
      [caption]="'Albums'"
      icon="glyphicon glyphicon-picture"
      [statusesDefinition]="config.album.status"
      [baseRoute]="['/desk', 'albums']"
      [service]="albums"
      [reloadOn]="['${IEvent.Type.UPDATE_ALBUM_LIST}']"
      [actions]="{'edit': 'Edit'}"
    ></side-asset-list>
  `,
})
export class AlbumContextComponent implements OnInit, OnDestroy {
  readonly config = config;
  album: IAlbum;

  @ViewChild('uploadModal') private uploadModal: UploadImagesToAlbumModalComponent;
  @ViewChild('uploadLabelsModal') private uploadLabelsModal: UploadLabelsToAlbumModalComponent;
  private routeSubscription: ISubscription;
  private processSubscription: ISubscription;
  private singleAlbumLoader: ReactiveLoader<IAlbum, TObjectId>;

  constructor(
    private albums: AlbumService,
    private processService: ProcessService,
    private route: ActivatedRoute,
  ) {
    this.singleAlbumLoader = new ReactiveLoader(id => this.albums.get(id));

    this.singleAlbumLoader.subscribe((album: IAlbum) => {
      this.album = album;
      this.processSubscription = this.albums.getActiveProcess(album)
        .filter(_ => !!_)
        .flatMap(process => {
          return this.processService.observe(process);
        })
        .subscribe(() => {
          this.singleAlbumLoader.load(album.id);
        });
    });
  }

  ngOnInit() {
    this.routeSubscription = this.route.params.subscribe(params => {
      const albumId = params['albumId'];
      this.album = null;
      if (albumId) {
        this.singleAlbumLoader.load(albumId);
      }
    });
  }

  ngOnDestroy() {
    this.routeSubscription && this.routeSubscription.unsubscribe();
    this.processSubscription && this.processSubscription.unsubscribe();
  }

  openUploadModal() {
    this.uploadModal.open(this.album ? this.album.id : undefined);
  }

  openUploadLabelsModal() {
    this.uploadLabelsModal.open(this.album.id);
  }

  _isUploadAvailable = function(album: IAlbum) {
    return album
      && album.type === IAlbum.Type.SOURCE
      && album.status === IAlbum.Status.ACTIVE
      && !album.locked
      && !album.video;
  };
}
