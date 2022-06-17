import { Component, HostBinding, Input, OnChanges, OnDestroy, OnInit, ViewChild } from '@angular/core';

import { ISubscription } from 'rxjs/Subscription';

import config from '../config';
import { SaveToLibraryModalComponent } from '../core/components/save-to-library.modal.component';
import { IAsset, TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { ReactiveLoader } from '../utils/reactive-loader';

import { IAlbum } from './album.interface';
import { AlbumService } from './album.service';

@Component({
  selector: 'album-view-embed',
  template: `
    <app-spinner [visibility]="_albumLoader.active | async"></app-spinner>
    <ng-template [ngIf]="album">
      <div [ngSwitch]="album.status">
        <error-indicator *ngSwitchCase="config.album.status.values.ERROR"
          [process]="albumProcessList[album.id]" [target]="'album'"></error-indicator>

        <picture-view *ngSwitchCase="config.album.status.values.ACTIVE"
          [album]="album"
          [allowEdit]="false"
          [allowSelection]="false"
          [poll]="poll"
        >
          <button *ngIf="!album.inLibrary"
            class="btn btn-secondary btn-xs btn-right-small-device"
            (click)="saveToLibrary()"
            title="Save Album To The Library"
          >
            <i class="glyphicon glyphicon-book"></i>
            <span class="full-text">Save To The Library</span>
            <span class="small-text">Save</span>
          </button>
        </picture-view>

        <process-indicator *ngSwitchDefault
          [process]="albumProcessList[album.id]"
        ></process-indicator>
      </div>
    </ng-template>
    <save-to-library-modal #saveToLibraryModal [service]="albums"></save-to-library-modal>
  `,
})
export class AlbumViewEmbedComponent implements OnChanges, OnDestroy, OnInit {
  @HostBinding('class') _cssClass = 'app-spinner-box';
  @Input() albumId: TObjectId;
  @Input() poll: boolean = false;
  config = config;
  albumProcessList: { [albumProcessId: string]: IProcess } = {};
  album: IAlbum;
  readonly _albumLoader: ReactiveLoader<IAlbum, TObjectId>;
  private processSubscription: ISubscription;
  private eventSubscription: ISubscription;

  @ViewChild('saveToLibraryModal') private saveToLibraryModal: SaveToLibraryModalComponent<IAlbum>;

  constructor(
    private albums: AlbumService,
    private processes: ProcessService,
    private events: EventService,
  ) {
    this._albumLoader = new ReactiveLoader(id => this.albums.get(id));
    this._albumLoader.subscribe(album => {
      this.setAlbum(album);
    });
  }

  ngOnInit() {
    this.albumProcessList = this.processes.data.targets[config.asset.aliasesPlural[IAsset.Type.ALBUM]];
    this.eventSubscription = this.events.subscribe((event) => {
      if (event.type === IEvent.Type.UPDATE_ALBUM && this.album.id === event.data.id) {
        this._albumLoader.load(this.album.id);
      }
    });
  }

  ngOnChanges(): void {
    if (this.albumId) {
      this._albumLoader.load(this.albumId);
    }
  }

  setAlbum(album: IAlbum) {
    this.album = album;
    // get process (casual)
    if (config.album.status.hasProcess[album.status]) {
      this.processSubscription && this.processSubscription.unsubscribe();
      this.processSubscription = this.processes.subscribeByTarget(album.id, IAsset.Type.ALBUM, () => {
        this._albumLoader.load(album.id);
      });
    }
  }

  saveToLibrary() {
    this.saveToLibraryModal.open(this.album);
  }

  ngOnDestroy() {
    this.eventSubscription && this.eventSubscription.unsubscribe();
    this.processSubscription && this.processSubscription.unsubscribe();
  }
}
