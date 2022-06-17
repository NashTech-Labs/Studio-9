import { DecimalPipe } from '@angular/common';
import {
  Component,
  EventEmitter,
  HostBinding,
  Input,
  OnChanges,
  OnDestroy,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { ModalService } from '../core-ui/services/modal.service';
import { IBackendList } from '../core/interfaces/common.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { MiscUtils } from '../utils/misc';
import { ReactiveLoader } from '../utils/reactive-loader';

import {
  IAlbum,
  IPicture,
  IPictureAugmentation,
  IPicturePredictedTag,
  IPictureSearchParams,
  IPictureTag,
} from './album.interface';
import { AlbumService } from './album.service';
import { albumsConfig } from './albums.config';
import { PicturePreviewComponent } from './picture-preview-modal.component';

@Component({
  selector: 'picture-view',
  template: `
    <div class="row">
      <div class="col-md-12">
        <div class="p0 form-control brand-control">
          <div class="row">
            <div class="col-md-6">
              <div class="row">
                <div class="col-xs-8">
                  <div *ngIf="album" class="ellipsis text-bold">
                    {{album.name}} ({{config.album.labelMode.labels[album.labelMode]}})
                  </div>
                </div>
                <div class="col-xs-4">
                  <ng-content select="button"></ng-content>
                </div>
              </div>
            </div>
            <div class="col-md-6" *ngIf="picturesList">
              <div class="pull-right">
                <ng-template [ngIf]="allowSelection">
                  <small>Selected {{selectedPictures.length}} item(s)</small>
                  <button (click)="resetSelection()"
                    [disabled]="!selectedPictures.length"
                    type="button" class="btn btn-secondary btn-xs">
                    <i class="glyphicon glyphicon-remove"></i>
                  </button>
                </ng-template>
                <button *ngIf="!!album.video"
                  (click)="viewMode = 'video'"
                  [ngClass]="{'active': viewMode === 'video'}"
                  type="button" class="btn btn-secondary btn-xs"><i class="glyphicon glyphicon-film"></i></button>
                <button (click)="viewMode = 'list'"
                  [ngClass]="{'active': viewMode === 'list'}"
                  type="button" class="btn btn-secondary btn-xs"><i class="glyphicon glyphicon-th-list"></i></button>
                <button (click)="viewMode = 'grid'"
                  [ngClass]="{'active': viewMode === 'grid'}"
                  type="button" class="btn btn-secondary btn-xs"><i class="glyphicon glyphicon-th"></i></button>
                {{(picturesList.count || 0) | pluralize:({other: '{} pictures', '0': 'No pictures', '1': '{} picture'})}}
                <ng-template [ngIf]="album.video"> + video</ng-template>
              </div>
              <div class="pull-right" *ngIf="viewMode !== 'video'">
                <app-pagination [page]="form.controls['page']"
                  [pageSize]="form.controls['page_size']"
                  [currentPageSize]="picturesList.data.length"
                  [rowsCount]="picturesList.count">
                </app-pagination>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <app-spinner [visibility]="_itemsLoader.active | async"></app-spinner>
    <div *ngIf="viewMode === 'video'">
      <video
        [src]="album.video.filepath | cache: 'videoPath/' + album.id"
        class="album-video" playsinline controls
        [adaptiveHeight]="{minHeight: 300}"
        style="max-width: 100%"
      ></video>
    </div>
    <div class="table-scroll" [adaptiveHeight]="{minHeight: 450}" *ngIf="viewMode === 'list'">
      <table *ngIf="picturesList" class="table dataTable table-hover">
        <thead>
        <tr style="white-space: nowrap">
          <th *ngIf="allowSelection" style="width: 1%">
            <app-check [checked]="selectedPictures | apply: isSelectedAll: picturesList.data"
              (checkedChange)="setSelectAll($event)"></app-check>
          </th>
          <th *ngFor="let item of options?.columns"
            [grid-sort]="item"
            [grid-sort-control]="form.controls['order']"
            [attr.style]="item.style | safeStyle"
          >
            {{item.name}}
          </th>
          <th *ngIf="allowEdit" class="text-right">Actions</th>
        </tr>
        </thead>
        <tbody>
        <tr *ngFor="let item of picturesList.data">
          <td *ngIf="allowSelection">
            <app-check
              (checkedChange)="changeSelection(item, $event)"
              [checked]="isSelected(item) > -1"
              [name]="'selection' + item.id"
              [type]="'checkbox'"
            ></app-check>
          </td>
          <td (click)="previewPicture(item.id)">
            <img width="32px" height="32px" [src]="item.filepath | cache: 'picturePath/' + item.id">
          </td>
          <td (click)="previewPicture(item.id)">
            <span [title]="item.filename" class="ellipsis">{{item.filename}}</span>
          </td>
          <td (click)="previewPicture(item.id)">
            {{item.filesize | formatBytes}}
          </td>
          <td *ngIf="album.type !== config.album.type.values.DERIVED"
            style="max-width: 300px;"
          >
            <a [routerLink]="['/desk', 'albums', album.id, 'tag', item.id]" *ngIf="allowEdit"
              title="Edit labels">{{ item.tags | apply: labelsToString }}
            </a>
            <ng-template [ngIf]="!allowEdit">
              {{ item.tags | apply: labelsToString }}
            </ng-template>
          </td>
          <td style="max-width: 300px;" *ngIf="album.type !== config.album.type.values.SOURCE">
            {{(item?.predictedTags || []) | apply: labelsToString }}
          </td>
          <td style="max-width: 300px;" *ngIf="album.type !== config.album.type.values.SOURCE">
            {{(item?.predictedTags || []) | apply: confidenceToString }}
          </td>
          <td (click)="previewPicture(item.id)" style="max-width: 300px;" *ngIf="album.type === config.album.type.values.SOURCE">
            {{(item?.augmentationsApplied || []) | apply: augmentationsToString }}
          </td>
          <td class="text-muted" *ngIf="allowEdit">
            <div class="dropdown text-right" dropdown [dropdownContainer]="'.table-scroll'">
              <a class="nav-link link-colorless table-row-actions" data-toggle="dropdown" aria-haspopup="true" aria-expanded="true">
                <span class="glyphicon glyphicon-option-vertical"></span>
              </a>

              <ul class="dropdown-menu dropdown-menu-right">
                <li><a [routerLink]="['/desk', 'albums', album.id, 'tag', item.id]"
                  class="dropdown-item link">
                  Label
                </a></li>
                <li><a
                  (click)="deletePicture(item)"
                  class="dropdown-item link">
                  Trash
                </a></li>
              </ul>
            </div>
          </td>
        </tr>
        </tbody>
      </table>
    </div>
    <div *ngIf="viewMode === 'grid'">
      <div class="row flex-tiles" *ngIf="picturesList">
        <div class="col-xs-6 col-sm-6 col-md-4 col-lg-3"
          *ngFor="let item of picturesList.data">
          <div class="thumbnail">
            <img (click)="previewPicture(item.id)" style="max-height:100px" class="thumbnail" [src]="item.filepath | cache: 'picturePath/' + item.id">
            <div class="caption">
              <p>size:{{item.filesize | formatBytes}}</p>
              <p [title]="item.filename" class="ellipsis">filename: {{item.filename}}</p>
              <p *ngIf="album.type !== config.album.type.values.DERIVED">labels: {{ item.tags | apply: labelsToString
                }}</p>
              <p *ngIf="album.type !== config.album.type.values.SOURCE">predicted labels:
                {{ (item?.predictedTags || []) | apply: labelsToString }}</p>
              <p *ngIf="album.type !== config.album.type.values.SOURCE">confidence:
                {{ (item?.predictedTags || []) | apply: confidenceToString }}</p>
              <p *ngIf="allowEdit">
                <a [routerLink]="['tag', item.id]" role="button" class="btn btn-primary">Label</a>
                <a (click)="deletePicture(item)" class="btn btn-default" role="button">Trash</a>
              </p>
              <p *ngIf="!allowEdit">
                <a (click)="previewPicture(item.id)" role="button" class="btn btn-primary">Preview</a>
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
    <picture-preview #preview [album]="album"></picture-preview>
  `,
})
export class PictureViewComponent implements OnChanges, OnDestroy {
  @HostBinding('class') _cssClass = 'app-spinner-box';
  config = config;
  @Input() album: IAlbum;
  @Input() selectedPictures: IPicture[] = [];
  @Input() allowEdit: boolean = true;
  @Input() allowSelection: boolean = true;
  @Input() searchParams: Partial<IPictureSearchParams> = {};
  @Input() poll: boolean = false;
  @Output() selectedPicturesChange = new EventEmitter<IPicture[]>();
  form: FormGroup;
  picturesList: IBackendList<IPicture>;
  viewMode: 'list' | 'grid' | 'video' = 'list';
  options: {
    columns: {
      name: string;
      alias?: string;
      style?: string;
    }[];
  };
  readonly _itemsLoader: ReactiveLoader<IBackendList<IPicture>, IPictureSearchParams>;
  protected formSubscription: Subscription;
  protected eventSubscription: Subscription;
  protected timerSubscription: Subscription;
  @ViewChild('preview') private preview: PicturePreviewComponent;
  private _decimalPipe: DecimalPipe = new DecimalPipe('en-US');

  get albumService() {
    return this.albums;
  }

  constructor(
    private albums: AlbumService,
    protected events: EventService,
    private _modalService: ModalService,
  ) {
    this._itemsLoader = new ReactiveLoader((formData: IPictureSearchParams) => {
      const params = Object.assign({}, this.searchParams, formData);
      if (params.labels && !params.labels.length) {
        delete params.labels;
      }
      if (params.augmentations && !params.augmentations.length) {
        delete params.augmentations;
      }
      return this.albums.pictureList(this.album, params);
    });
    this._itemsLoader.subscribe((data) => {
      this.picturesList = data;
      this.selectedPicturesChange.emit(this.selectedPictures);
    });

    this.form = new FormGroup({
      order: new FormControl(),
      page: new FormControl(1),
      page_size: new FormControl(20),
    });

    this.formSubscription && this.formSubscription.unsubscribe();
    this.formSubscription = this.form.valueChanges.debounceTime(100).subscribe(data => {
      this._itemsLoader.load(data);
    });

    this.eventSubscription && this.eventSubscription.unsubscribe();
    this.eventSubscription = this.events.subscribe((event) => {
      if (event.type === IEvent.Type.UPDATE_PICTURE_LIST && event.data.albumId === this.album.id) {
        this._itemsLoader.load(this.form.value);
      }
    });
  }

  setSelectAll(selected: boolean) {
    const items = this.picturesList.data.reduce((acc, item) => {
      const index = acc.findIndex(picture => picture.id === item.id);
      if (index === -1 && selected) {
        acc.push(item);
      } else if (index > -1 && !selected) {
        acc.splice(index, 1);
      }
      return acc;
    }, this.selectedPictures);
    this.selectedPictures = [...items];
    this.selectedPicturesChange.emit(this.selectedPictures);
  }

  resetSelection() {
    this.selectedPictures = [];
    this.selectedPicturesChange.emit(this.selectedPictures);
  }

  ngOnChanges(changes: SimpleChanges) {
    if ('album' in changes) {
      this.picturesList = null;
      this.selectedPictures = [];
      this.viewMode = this.album.video ? 'video' : 'list';
      MiscUtils.fillForm(this.form, {
        search: '',
        order: null,
        page: 1,
        labels: [],
      });

      const baseColumns = [
        { name: 'Image', style: 'width: 2%' },
        { name: 'Filename', alias: 'fileName' },
        { name: 'Size', alias: 'fileSize', style: 'width: 10%' },
      ];
      switch (this.album.type) {
        case config.album.type.values.SOURCE:
          this.options = {
            columns: [
              ...baseColumns,
              { name: 'Labels', alias: 'tags.label' },
              { name: 'Augmentations' },
            ],
          };
          break;
        case config.album.type.values.TRAINRESULTS:
          this.options = {
            columns: [
              ...baseColumns,
              { name: 'Labels', alias: 'tags.label' },
              { name: 'Predicted Labels', alias: 'predictedTags.label' },
              { name: 'Confidence', alias: 'predictedTags.confidence', style: 'width: 12%' },
            ],
          };
          break;
        case config.album.type.values.DERIVED:
          this.options = {
            columns: [
              ...baseColumns,
              { name: 'Predicted Labels', alias: 'predictedTags.label' },
              { name: 'Confidence', alias: 'predictedTags.confidence', style: 'width: 12%' },
            ],
          };
          break;
        default:
          throw new Error('No album type');
      }
      this.options.columns = this.options.columns.filter(_ => !!_);
      this._itemsLoader.load(this.form.value);
    } else if ('searchParams' in changes) {
      this.form.controls['page'].setValue(1);
      // this is triggered by form change event: this._itemsLoader.load(this.form.value);
    }

    if ('poll' in changes) {
      this.timerSubscription && this.timerSubscription.unsubscribe();
      if (this.poll) {
        this.timerSubscription = Observable.timer(10000, 10000).subscribe(() => {
          this._itemsLoader.load(this.form.value);
        });
      }
    }
  }

  ngOnDestroy() {
    this.formSubscription && this.formSubscription.unsubscribe();
    this.eventSubscription && this.eventSubscription.unsubscribe();
    this.timerSubscription && this.timerSubscription.unsubscribe();
  }

  previewPicture(id) {
    this.preview && this.preview.open(id);
  }

  deletePicture(picture: IPicture): void {
    const message = `Are you sure you want to delete the picture '${picture.filename}'?`;
    this._modalService
      .confirm(message)
      .take(1)
      .filter(Boolean)
      .subscribe(() => this.albums.deletePicture(this.album, picture));
  }

  labelsToString = (tags: IPictureTag[]) => {
    return tags.map(tag => {
      return this.album.labelMode === IAlbum.LabelMode.LOCALIZATION
        ? `${tag.label} (${tag.area.left},${tag.area.top};${tag.area.width}x${tag.area.height})`
        : `${tag.label}`;
    }).join(', ');
  };

  confidenceToString = (tags: IPicturePredictedTag[]) => {
    return tags
      .map(tag => this._decimalPipe.transform(tag.confidence, '1.0-3'))
      .join(', ');
  };

  augmentationsToString = (augmentations: IPictureAugmentation[]) => {
    return augmentations
      .map(_ => albumsConfig.album.augmentationType.labels[_.augmentationType])
      .join(', ');
  };

  changeSelection(item: IPicture, checked: boolean) {
    let i = this.isSelected(item);
    if (checked) {
      i >= 0 || this.selectedPictures.push(item);
    } else {
      i >= 0 && this.selectedPictures.splice(i, 1);
    }
    this.selectedPictures = [...this.selectedPictures];
    this.selectedPicturesChange.emit(this.selectedPictures);
  }

  isSelected(currentItem: IPicture): number {
    return this.selectedPictures.findIndex(item => item.id === currentItem.id);
  }

  isSelectedAll = function (selectedPictures: IPicture[], pictures: IPicture[]): boolean {
    return pictures.filter(picture =>
      selectedPictures.find(currentPicture => currentPicture.id === picture.id)).length === pictures.length;
  };
}

