import { Component, Inject, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { Observable } from 'rxjs/Observable';
import { ReplaySubject } from 'rxjs/ReplaySubject';
import { ISubscription } from 'rxjs/Subscription';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { ReactiveLoader } from '../utils/reactive-loader';

import { IAlbum, IAlbumTagsSummary, IPicture } from './album.interface';
import { AlbumService } from './album.service';
import { albumsConfig } from './albums.config';
import { PictureViewComponent } from './picture-view.component';

export class AlbumData extends Observable<IAlbum> {}
export class AlbumDataSubject extends ReplaySubject<IAlbum> {
  constructor() {
    super(1);
  }
}

@Component({
  selector: 'app-album-view',
  template: `
    <ng-template #searchFormTemplate>
      <form class="form">
        <div class="row row-flex pt15" style="flex-direction: row-reverse;">
          <div class="col-xs-12 flex-static">
            <app-select
              [label]="'Search Mode'"
              [options]="searchModeOptions"
              [value]="searchMode"
              (valueChange)="setSearchMode($event)"
            ></app-select>
          </div>
          <div class="col-xs-12 flex-rubber" style="overflow: visible;" [ngSwitch]="searchMode">
            <app-select *ngSwitchCase="'labels'"
              [label]="'Labels'"
              [options]="searchLabelOptions"
              [multiple]="true"
              [iconBefore]="'glyphicon-search'"
              [disabled]="!searchLabelOptions.length || !_tagsLoader.loaded"
              [control]="searchForm.controls['labels']"
            ></app-select>
            <app-select *ngSwitchCase="'augmentations'"
              [label]="'DA Type'"
              [options]="searchAugmentationOptions"
              [multiple]="true"
              [iconBefore]="'glyphicon-search'"
              [control]="searchForm.controls['augmentations']"
            ></app-select>
            <app-input *ngSwitchDefault=""
              [control]="searchControl"
              [iconBefore]="'glyphicon-search'"
              [iconAfter]="'glyphicon-remove'"
              (iconAfterClick)="searchControl.setValue('')"
            ></app-input>
          </div>
        </div>
      </form>
    </ng-template>
    <div [hidden]="pictureShown" class="app-spinner-box">
      <ng-container [ngSwitch]="!!selectedPictures.length">
        <picture-operations
          *ngSwitchCase="true"
          [album]="album"
          [selectedPictures]="selectedPictures"
          (onPicturePreview)="onPicturePreview($event)"
          (onDelete)="onPicturesDeleted()"
        >
          <ng-container *ngTemplateOutlet="searchFormTemplate"></ng-container>
        </picture-operations>
        <asset-operations *ngSwitchCase="false" [type]="config.asset.values.ALBUM"
          [selectedItems]="[album]"
          (onDelete)="onAlbumDeleted()"
          (onInfo)="_showStats=!_showStats"
        >
          <ng-container *ngTemplateOutlet="searchFormTemplate"></ng-container>
        </asset-operations>
      </ng-container>
      <app-spinner [visibility]="_albumLoader.active | async"></app-spinner>
      <div class="row">
        <div *ngIf="_showStats && album.status===config.album.status.values.ACTIVE" class="col-md-4 col-xs-12 col-md-push-8">
          <albums-augmentation-time-spent-summary [augmentationSummary]="album.augmentationTimeSpentSummary">
          </albums-augmentation-time-spent-summary>
        </div>
        <div
          *ngIf="_albumLoader.loaded && album"
          [ngSwitch]="album.status"
          [ngClass]="_showStats && album.status===config.album.status.values.ACTIVE
            ? 'col-md-8 col-xs-12 col-md-pull-4'
            : 'col-md-12'"
        >
          <picture-view #pictureView
            [album]="album"
            [searchParams]="searchForm.value"
            [allowEdit]="album.type === config.album.type.values.SOURCE && !album.locked && !album.video"
            [(selectedPictures)]="selectedPictures"
          ></picture-view>

          <process-indicator *ngSwitchDefault
            [process]="albumProcess"
          ></process-indicator>

          <error-indicator *ngSwitchCase="config.album.status.values.ERROR"
            [process]="albumProcess" [target]="'album'"></error-indicator>
        </div>
      </div>
    </div>

    <router-outlet (activate)="pictureShown = true" (deactivate)="pictureShown = false"></router-outlet>
  `,
  providers: [{
    provide: AlbumData,
    useClass: AlbumDataSubject,
  }],
})
export class AlbumViewComponent implements OnInit, OnDestroy {
  @Input() type: string;
  readonly config = config;
  _showStats: boolean = false;
  pictureShown: boolean = false;
  albumProcess: IProcess;
  album: IAlbum;
  searchForm: FormGroup;
  searchControl: FormControl;
  searchMode: string = '';
  searchModeOptions = AppSelectOptionData.fromList(
    ['', 'labels', 'augmentations'],
    ['Filename', 'Labels', 'Augmentations'],
  );
  searchLabelOptions: AppSelectOptionData[] = [];
  searchAugmentationOptions: AppSelectOptionData[] = AppSelectOptionData.fromList(
    [null, ...albumsConfig.album.augmentationType.list],
    ['No Augmentation', ...albumsConfig.album.augmentationType.list.map(_ => albumsConfig.album.augmentationType.labels[_])],
  );
  selectedPictures: IPicture[] = [];
  @ViewChild('pictureView') pictureView: PictureViewComponent;
  readonly _albumLoader: ReactiveLoader<IAlbum, TObjectId>;
  readonly _tagsLoader: ReactiveLoader<IAlbumTagsSummary, TObjectId>;
  private routeSubscription: ISubscription;
  private processSubscription: ISubscription;
  private eventSubscription: ISubscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private albums: AlbumService,
    private processes: ProcessService,
    @Inject(AlbumData) private albumData: ReplaySubject<IAlbum>,
    private events: EventService,
  ) {
    this.searchForm = new FormGroup({
      search: new FormControl(''),
      labels: new FormControl([]),
      augmentations: new FormControl([]),
    });
    this.searchControl = new FormControl();
    this.searchControl.valueChanges.debounceTime(500).subscribe((value) => {
      this.searchForm.patchValue({ search: value });
    });
    this._albumLoader = new ReactiveLoader(id => this.albums.get(id));
    this._albumLoader.subscribe(album => {
      this.setAlbum(album);
    });
    this._tagsLoader = new ReactiveLoader<IAlbumTagsSummary, TObjectId>((albumId) => {
      this.searchLabelOptions = [];
      return this.albums.getTags(albumId);
    });
    this._tagsLoader.subscribe((data) => {
      this.searchLabelOptions = data.map(_ => {
        return { id: _.label, text: `${_.label || 'Unlabeled'} (${_.count})` };
      });
    });
  }

  ngOnInit() {
    this.eventSubscription = this.events.subscribe((event) => {
      if (event.type === IEvent.Type.UPDATE_ALBUM && this.album.id === event.data.id) {
        this.tryUpdateAlbum(this.album.id);
      }
      if (event.type === IEvent.Type.DELETE_ALBUM && this.album.id === event.data.id) {
        this.onAlbumDeleted();
      }
    });
    this.routeSubscription = this.route.params.subscribe(params => {
      this.tryUpdateAlbum(params['albumId']);
    });
  }

  setAlbum(album: IAlbum) {
    if (!this.album || this.album.id !== album.id) {
      this.selectedPictures = [];
    }
    this.album = album;
    this.albumData.next(this.album);
    // get process (casual)

    this.processSubscription && this.processSubscription.unsubscribe();
    this.processSubscription = this.albums.getActiveProcess(album)
      .do(process => {
        this.albumProcess = process; // processService will update this process object status
      })
      .filter(_ => !!_)
      .flatMap(process => {
        return this.processes.observe(process);
      })
      .subscribe(() => {
        this.tryUpdateAlbum(album.id);
      });
  }

  onAlbumDeleted() {
    this.router.navigate(['/desk', 'albums']);
  }

  onPicturesDeleted() {
    this.selectedPictures = [];
  }

  ngOnDestroy() {
    this.routeSubscription.unsubscribe();
    this.processSubscription && this.processSubscription.unsubscribe();
    this.eventSubscription && this.eventSubscription.unsubscribe();
  }

  setSearchMode(value) {
    this.searchMode = value;
    this.searchControl.setValue('');
    this.searchForm.reset({
      search: '',
      labels: [],
      augmentations: [],
    });
  }

  onPicturePreview(pictureId: TObjectId) {
    this.pictureView.previewPicture(pictureId);
  }

  private tryUpdateAlbum(albumId: TObjectId): void {
    this._albumLoader.load(albumId);
    this._tagsLoader.load(albumId);
  }
}
