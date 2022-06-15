import { DecimalPipe } from '@angular/common';
import { Component, Input, ViewChild } from '@angular/core';

import { BehaviorSubject } from 'rxjs/BehaviorSubject';
import { Observable } from 'rxjs/Observable';

import config from '../config';
import { ModalComponent } from '../core-ui/components/modal.component';
import { TObjectId } from '../core/interfaces/common.interface';
import { ReactiveLoader } from '../utils/reactive-loader';

import { IAlbum, IPicture, IPictureAugmentation, IPicturePredictedTag, IPictureTag } from './album.interface';
import { AlbumService } from './album.service';
import { albumsConfig } from './albums.config';
import { ITagArea } from './tag-image.directive';

@Component({
  selector: 'picture-preview',
  template: `
  <app-modal #modal [caption]="picture?.filename" [sizeClass]="config.modal.size.LARGE">
    <app-spinner [visibility]="_loader.active | async"></app-spinner>
    <div class="row" *ngIf="_loader.loaded && picture">
      <div class="col-md-6 col-md-push-6">
        <ng-template [ngIf]="album && album.type !== config.album.type.values.DERIVED">
          <div>
            <label>Image size: </label>
            {{picture.filepath | cache: 'picturePath/' + picture.id | apply: _imageSize | async}}
          </div>

          <label>Labels</label>
          <table class="table">
            <tr>
              <th>Label</th>
              <ng-template [ngIf]="album.labelMode === config.album.labelMode.values.LOCALIZATION">
                <th title="(x,y)">Position</th>
                <th>Size</th>
              </ng-template>
            </tr>
            <tr *ngFor="let tag of picture.tags">
              <td>{{tag.label}}</td>
              <ng-template [ngIf]="album.labelMode === config.album.labelMode.values.LOCALIZATION">
                <td>({{tag.area.left}},{{tag.area.top}})</td>
                <td>{{tag.area.width}}x{{tag.area.height}}</td>
              </ng-template>
            </tr>
            <tr *ngIf="!picture.tags.length">
              <td [attr.colspan]="album.labelMode === config.album.labelMode.values.LOCALIZATION ? 3 : 1">No labels</td>
            </tr>
          </table>
        </ng-template>
        <ng-template [ngIf]="album && album.type !== config.album.type.values.SOURCE">
          <label>Predicted Labels</label>
          <table class="table">
            <tr>
              <th>Label</th>
              <ng-template [ngIf]="album.labelMode === config.album.labelMode.values.LOCALIZATION">
                <th title="(x,y)">Position</th>
                <th>Size</th>
              </ng-template>
              <th title="Confidence">Conf.</th>
            </tr>
            <tr *ngFor="let tag of picture.predictedTags">
              <td>{{tag.label}}</td>
              <ng-template [ngIf]="album.labelMode === config.album.labelMode.values.LOCALIZATION">
                <td>({{tag.area.left}},{{tag.area.top}})</td>
                <td>{{tag.area.width}}x{{tag.area.height}}</td>
              </ng-template>
              <td>{{tag.confidence | number: '1.0-3'}}</td>
            </tr>
            <tr *ngIf="!picture.predictedTags.length">
              <td [attr.colspan]="album.labelMode === config.album.labelMode.values.LOCALIZATION ? 4 : 2">No labels</td>
            </tr>
          </table>
        </ng-template>
      </div>
      <div class="col-md-6 col-md-pull-6">
        <div class="panel panel-default">
          <div class="panel-body">
            <div class="image-block" [ngSwitch]="!picture.predictionsRendered && album?.labelMode === config.album.labelMode.values.LOCALIZATION">
              <img *ngSwitchCase="true"
                tag-image
                [allowEdit]="false"
                style="width:100%;vertical-align: middle;"
                [src]="picture.filepath | cache: 'picturePath/' + picture.id"
                [tags]="areas"/>
              <img *ngSwitchDefault
                style="width:100%;vertical-align: middle;"
                [src]="picture.filepath | cache: 'picturePath/' + picture.id"/>
            </div>
          </div>
        </div>
        <ng-template [ngIf]="album && album.type === config.album.type.values.SOURCE && picture.augmentationsApplied?.length">
          <label>Augmentations</label>
          <table class="table">
            <tr>
              <th>Type</th>
              <th>Parameters</th>
            </tr>
            <tr *ngFor="let augmentation of picture.augmentationsApplied">
              <td>{{ albumsConfig.album.augmentationType.labels[augmentation.augmentationType] }}</td>
              <td>
                <p *ngFor="let param of augmentation | apply: _augmentationParamsToStrings">{{param}}</p>
              </td>
              <td>
                <p *ngFor="let param of augmentation | apply: _augmentationExtraParamsToStrings">{{param}}</p>
              </td>
            </tr>
            <tr *ngIf="!picture.tags.length">
              <td [attr.colspan]="album.labelMode === config.album.labelMode.values.LOCALIZATION ? 3 : 1">No labels</td>
            </tr>
          </table>
        </ng-template>
      </div>
    </div>
  </app-modal>
  `,
  styles: [
    '.panel-body { padding: 0; }',
    '.panel-body .image-block { overflow: hidden; }',
  ],
})
export class PicturePreviewComponent {
  readonly config = config;
  readonly albumsConfig = albumsConfig;
  @Input() album: IAlbum;

  picture: IPicture = null;
  areas: ITagArea[] = [];

  readonly _loader: ReactiveLoader<IPicture, TObjectId>;
  @ViewChild('modal') private modal: ModalComponent;
  private decimalPipe: DecimalPipe = new DecimalPipe('en-US');

  constructor(
    private albums: AlbumService,
  ) {
    this._loader = new ReactiveLoader((pictureId: TObjectId): Observable<IPicture> => {
      this.picture = null;
      return this.albums.getPicture(this.album.id, pictureId);
    });

    this._loader.subscribe((picture) => {
      this.picture = picture;
      if (this.album.type !== config.album.type.values.SOURCE) {
        this.areas = (this.picture.predictedTags || []).map(this.predictedTagToArea);
      } else {
        this.areas = (this.picture.tags || []).map(this.tagToArea);
      }
    });
  }

  public open(pictureId: TObjectId) {
    this._loader.load(pictureId);
    this.modal.show();
  }

  _imageSize(filepath: string = null) {
    const value = new BehaviorSubject<string>('...');

    if (filepath) {
      const image = new Image();

      image.onload = () => {
        value.next(`${image.width}x${image.height}`);
      };

      image.src = filepath;
    }

    return value;
  }

  protected _augmentationParamsToStrings = (augmentation: IPictureAugmentation): string[] => {
    const ignoredKeys = ['augmentationType', 'extraParams'];
    return Object.keys(augmentation).filter(_ => !ignoredKeys.includes(_)).map(key => {
      const value = augmentation[key];

      switch (typeof value) {
        case 'number':
          return `${key}: ${this.decimalPipe.transform(value, '1.0-3')}`;
        default:
          return `${key}: ${value}`;
      }
    });
  };

  protected _augmentationExtraParamsToStrings = (augmentation: IPictureAugmentation): string[] => {
    return Object.keys(augmentation.extraParams || {}).map(key => {
      const value = augmentation[key];

      return `${key}: ${this.decimalPipe.transform(value, '1.0-3')}`;
    });
  };

  private tagToArea = (tag: IPictureTag): ITagArea => {
    const area = {
      label: tag.label,
    };

    if (tag.area) {
      Object.assign(area, {
        x: tag.area.left,
        y: tag.area.top,
        width: tag.area.width,
        height: tag.area.height,
      });
    }

    return area;
  };

  private predictedTagToArea = (tag: IPicturePredictedTag): ITagArea => {
    const area = this.tagToArea(tag);

    area.label += ` [${this.decimalPipe.transform(tag.confidence, '1.0-3')}]`;

    return area;
  };
}
