import { DecimalPipe } from '@angular/common';
import { Component, Host, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import 'rxjs/add/observable/combineLatest';
import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { TObjectId } from '../core/interfaces/common.interface';
import { MiscUtils } from '../utils/misc';
import { ReactiveLoader } from '../utils/reactive-loader';
import { AppValidators } from '../utils/validators';

import { AlbumData } from './album-view.component';
import { IAlbum, IPicture, IPicturePredictedTag, IPictureTag } from './album.interface';
import { AlbumService } from './album.service';
import { ITagArea, TagImageDirective } from './tag-image.directive';

@Component({
  selector: 'tag-image',
  template: `
    <picture-operations
      [selectedPictures]="[picture]" (onDelete)="navigateBack()" [album]="album" [inPictureView]="true">
      <div class="btn-group pull-right" role="group">
        <button type="button" class="btn btn-primary"
          (click)="submit()">
          Update&nbsp;<i class="glyphicon glyphicon-ok"></i></button>
        <button type="button" class="btn btn-default"
          (click)="navigateBack()">
          Close&nbsp;<i class="glyphicon glyphicon-remove"></i></button>
      </div>
    </picture-operations>
    <app-spinner [visibility]="!album || !_loader.loaded"></app-spinner>
    <div class="pt15" *ngIf="_loader.loaded && album && picture">
      <div class="row">
        <div class="col-md-6 col-md-push-6">
          <label>Labels</label>
          <button
            *ngIf="album | apply: _canEditTags"
            class="btn btn-default"
            (click)="form.controls['tags'].push(newTagForm())"
            title="Add">
            <i class="glyphicon glyphicon-plus"></i>
          </button>
          <div class="row"
            *ngFor="let controlGroup of form.controls['tags'].controls; let i = index;">
            <div class="col-xs-10">
              <app-input [label]="'Label'" [control]="controlGroup.controls.label"
                (blur)="onTagInputBlur()"
                (focus)="onTagInputFocus(i)"
              ></app-input>
            </div>
            <div class="col-xs-10 col-md-9 col-lg-10 form-group"
              *ngIf="album.labelMode === config.album.labelMode.values.LOCALIZATION">
              <div class="input-group">
                <input type="text"
                  [disabled]="controlGroup.disabled"
                  [readonly]="true" class="form-control"
                  value="{{controlGroup.value | apply: tagDescription}}"
                />
                <span class="input-group-addon" [ngClass]="{'disabled': album | apply: _canEditTags}">
                  <i class="glyphicon glyphicon-screenshot"></i>
                </span>
              </div>
            </div>
            <div class="col-xs-2 col-md-3 col-lg-2 text-right" *ngIf="album | apply: _canEditTags">
              <button class="btn btn-default" (click)="form.controls['tags'].removeAt(i)"
                title="Remove">
                <i class="glyphicon glyphicon-remove"></i>
              </button>
            </div>
          </div>
          <ng-template [ngIf]="album.type !== config.album.type.values.SOURCE">
            <label>Predicted Labels</label>
            <div class="row"
              *ngFor="let tag of picture?.predictedTags">
              <div class="col-xs-12">
                <app-input [label]="'Label'" [value]="tag.label" [disabled]="true"></app-input>
              </div>
              <div class="col-xs-12 form-group" *ngIf="album.labelMode === config.album.labelMode.values.LOCALIZATION">
                <div class="input-group">
                  <input type="text"
                    [disabled]="true"
                    class="form-control" value="{{tag | apply: predictedTagDescription}}"/>
                  <span class="input-group-addon" [ngClass]="{'disabled': true }">
                  <i class="glyphicon glyphicon-screenshot"></i></span>
                </div>
              </div>
            </div>
          </ng-template>
        </div>
        <div class="col-md-6 col-md-pull-6">
          <div class="panel panel-default">
            <div class="panel-heading">
              <div class="row">
                <div class="col-md-6 ellipsis" [title]="picture.filename">
                  {{picture.filename}}
                </div>
                <div class="col-md-6" *ngIf="album.labelMode === config.album.labelMode.values.LOCALIZATION">
                  <div class="input-group">
                    <label class="input-group-addon input-group-label label-bare"
                      for="zoom"
                      [ngClass]="{'disabled': form.controls['zoom'].disabled}">
                      <i class="glyphicon glyphicon-search"></i>
                    </label>
                    <input type="number" min="0" step="0.1" class="form-control"
                      [formControl]="form.controls['zoom']" id="zoom">
                    <div class="input-group-btn">
                      <button [disabled]="{'disabled': form.controls['zoom'].disabled}"
                        title="Zoom In" class="btn btn-default" (click)="onZoomIn()">
                        <i class="glyphicon glyphicon-zoom-in"></i></button>
                      <button [disabled]="{'disabled': form.controls['zoom'].disabled}"
                        title="Zoom Out" class="btn btn-default" (click)="onZoomOut()">
                        <i class="glyphicon glyphicon-zoom-out"></i></button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="panel-body" [adaptiveHeight]="{pageMargin: 30, minHeight: 450, property: 'height'}"
              style="overflow:auto">
              <div class="image-block" [ngSwitch]="album.labelMode">
                <img *ngSwitchCase="config.album.labelMode.values.LOCALIZATION"
                  #editor="tagImageDirective"
                  tag-image
                  [allowEdit]="album | apply: _canEditTags"
                  style="width:100%;vertical-align: middle;"
                  [src]="picture.filepath | cache: 'picturePath/' + picture.id"
                  (tagsChange)="onAreasChange($event)"
                  [scale]="form.controls['zoom'].value"
                  (scaleChange)="form.controls['zoom'].setValue($event)"
                  [tags]="areas"/>
                <img *ngSwitchDefault
                  style="width:100%;vertical-align: middle;"
                  [src]="picture.filepath | cache: 'picturePath/' + picture.id"/>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `, styles: [
    '.panel-body { padding: 0; }',
  ],
})
export class TagImageComponent implements OnInit, OnDestroy {
  album: IAlbum;
  picture: IPicture;
  readonly config = config;
  readonly form: FormGroup;
  readonly _loader: ReactiveLoader<IPicture, TObjectId>;
  areas: ITagArea[] = [];

  @ViewChild('editor') private editor: TagImageDirective;
  private routeSubscription: Subscription;
  private formSubscription: Subscription;
  private step: number = 0.1;
  private decimalPipe: DecimalPipe = new DecimalPipe('en-US');

  constructor(
    private albums: AlbumService,
    @Host() private albumData: AlbumData,
    private route: ActivatedRoute,
    private router: Router,
  ) {
    this.form = new FormGroup({
      zoom: new FormControl(null, [Validators.required, AppValidators.float]),
      tags: new FormArray([this.newTagForm()]),
    });

    this._loader = new ReactiveLoader((pictureId: TObjectId): Observable<IPicture> => {
      return this.albums.getPicture(this.album.id, pictureId);
    });

    this._loader.subscribe(picture => {
      this.picture = picture;

      this.areas = this.mapTags(this.picture.tags);
      if (this.album.type !== config.album.type.values.SOURCE) {
        this.areas = this.areas.concat(this.mapPredictedTags(this.picture.predictedTags || []));
      }
      MiscUtils.fillForm(this.form, {
        tags: picture.tags,
      }, !this._canEditTags(this.album), false);
    });
  }

  ngOnInit() {
    this.formSubscription = this.form.controls['tags'].valueChanges.subscribe(value => {
      if (this._canEditTags(this.album)) {
        this.areas = this.mapTags(value.filter((tag: IPictureTag) => tag.area.width > 0 && tag.area.height > 0));
      }
    });

    this.routeSubscription = Observable.combineLatest(
      this.albumData,
      this.route.params.map((params): TObjectId => params['pictureId']),
    ).subscribe(([album, pictureId]) => {
      this.album = album;
      if (!this._canEditTags(this.album)) {
        this.form.disable();
      }

      this._loader.load(pictureId);
    });
  }

  mapTags(tags: IPictureTag[]): ITagArea[] {
    return tags.map(this.tagToArea);
  }

  mapPredictedTags(tags: IPicturePredictedTag[]): ITagArea[] {
    return tags.map(this.predictedTagToArea);
  }

  mapAreas(areas: ITagArea[]): IPictureTag[] {
    return areas.map((area: ITagArea): IPictureTag => {
      return {
        label: area.label,
        area: {
          top: area.y,
          left: area.x,
          width: area.width,
          height: area.height,
        },
      };
    });
  }

  ngOnDestroy() {
    this.routeSubscription && this.routeSubscription.unsubscribe();
    this.formSubscription && this.formSubscription.unsubscribe();
  }

  onAreasChange(areas: ITagArea[]) {
    if (!this._canEditTags(this.album)) {
      return;
    }

    const tagsControl: FormArray = <FormArray> this.form.controls['tags'];
    while (tagsControl.length) {
      tagsControl.removeAt(0);
    }

    const filtered = areas.filter(tagArea => {
      return tagArea.height > 0 && tagArea.width > 0;
    });
    if (filtered.length) {
      tagsControl.push(this.newTagForm());
      MiscUtils.fillForm(tagsControl, this.mapAreas(filtered));
    }
  }

  onTagInputBlur() {
    if (this.editor) {
      this.editor.blurAll();
    }
  }

  onTagInputFocus(i) {
    if (this.editor) {
      this.editor.focus(i);
    }
  }

  onZoomIn() {
    this.form.controls['zoom'].setValue(parseFloat(this.form.controls['zoom'].value) + this.step);
  }

  onZoomOut() {
    this.form.controls['zoom'].setValue(parseFloat(this.form.controls['zoom'].value) - this.step);
  }

  newTagForm() {
    return new FormGroup({
      label: new FormControl('', Validators.required),
      area: new FormGroup({
        top: new FormControl(0, AppValidators.number),
        left: new FormControl(0, AppValidators.number),
        width: new FormControl(50, AppValidators.number),
        height: new FormControl(50, AppValidators.number),
      }),
    });
  }

  submit() {
    if (!this._canEditTags(this.album)) {
      return;
    }
    this.albums.saveTags(this.album.id, this.picture.id, {
      tags: this.form.value.tags,
    }).subscribe((picture) => {
      this.picture = picture;
      this.navigateBack();
    });
  }

  navigateBack() {
    this.router.navigate(['/desk', 'albums', this.album.id]);
  }

  tagDescription = (tag: IPictureTag): string => {
    return `Position: (${tag.area.left},${tag.area.top}) Size: ${tag.area.width}x${tag.area.height}`;
  };

  predictedTagDescription = (tag: IPicturePredictedTag): string => {
    return this.tagDescription(tag) + ' Confidence: ' + this.decimalPipe.transform(tag.confidence, '1.0-3');
  };

  // checks if user can change tags
  _canEditTags = function(album: IAlbum): boolean {
    return album.type === IAlbum.Type.SOURCE && !album.locked;
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
