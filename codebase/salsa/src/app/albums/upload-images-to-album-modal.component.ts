import { Component, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';

import { Observable } from 'rxjs/Observable';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { ModalComponent } from '../core-ui/components/modal.component';
import { IBackendList, TObjectId } from '../core/interfaces/common.interface';
import { ActivityObserver } from '../utils/activity-observer';
import { MiscUtils, describeEnum } from '../utils/misc';
import { ReactiveLoader } from '../utils/reactive-loader';
import { AppValidators } from '../utils/validators';

import { IAlbum, IAlbumCreate } from './album.interface';
import { AlbumService } from './album.service';

const ZERO_ID = '-1';

enum UploadMode {
  SINGLE_PICTURE = 'SINGLE_PICTURE',
  S3_BUCKET = 'S3_BUCKET',
  S3_VIDEO = 'S3_VIDEO',
}

enum UploadCSVPath {
  LOCAL_FILE = 'LOCAL_FILE',
  BUCKET_PATH = 'BUCKET_PATH',
  NONE  = 'NONE',
}

const uploadModeDescription =  describeEnum(UploadMode, {
  labels: {
    SINGLE_PICTURE: 'Upload Single Picture',
    S3_BUCKET: 'Import Pictures From S3',
    S3_VIDEO: 'Import Video From S3',
  },
});

const uploadCSVPathDescription = describeEnum(UploadCSVPath, {
  labels: {
    LOCAL_FILE: 'Upload File',
    BUCKET_PATH: 'S3 Bucket Path',
    NONE: 'None',
  },
});

@Component({
  selector: 'upload-images-to-album-modal',
  template: `
    <app-modal #modal
      [caption]="'Upload Images/Video'"
      [buttons]="[{ 'class': 'btn-primary', disabled: (albumForm.invalid || ((_savingObserver.active) | async)), title: 'Upload' }]"
      (buttonClick)="albumForm.valid && doUpload()">
      <form>
        <app-spinner [visibility]="!albumList"></app-spinner>
        <div *ngIf="albumList">
          <app-select [label]="'Album'"
            [disabled]="_albumIdLocked"
            [(value)]="albumId"
            (valueChange)="setAlbum($event)"
            [options]="albumOptions"
          ></app-select>
          <ng-template [ngIf]="albumId === ZERO_ID">
            <div class="form-group">
              <app-input [label]="'Album Name'" [control]="albumForm.controls['albumName']"></app-input>
            </div>
            <app-select [label]="'Label Mode'" [control]="albumForm.controls['albumLabelMode']"
              [options]="labelModeOptions"
            ></app-select>
          </ng-template>
          <app-select [label]="'Upload Mode'" [options]="uploadModeOptions | apply: _filterUploadModes: albumId"
            [control]="albumForm.controls['uploadMode']">
          </app-select>
          <ng-template [ngIf]="albumForm.value.uploadMode === UploadMode.SINGLE_PICTURE">
            <app-input [label]="'Image File Name'" [control]="albumForm.controls['filename']"></app-input>
            <app-input *ngIf="albumId" [label]="'Image'"
              [readonly]="true"
              [iconAfter]="'glyphicon-file'"
              [placeholder]="'pick an image...'"
              file-upload
              [accept]="config.picture.import.extension.list"
              [value]="albumForm.value['file']?.name"
              [file-upload-click]="true"
              (onSelectFile)="onUploadPicture($event)">Upload Images
            </app-input>
          </ng-template>
          <div *ngVar="isClassification(albumId, albumForm.controls['albumLabelMode'].value); let isClassificationAlbum = value">
            <ng-template [ngIf]="albumForm.value.uploadMode === UploadMode.S3_BUCKET">
              <s3-bucket-form [form]="albumForm"></s3-bucket-form>
              <app-input
                [label]="'Images path'"
                [placeholder]="'path/to/images/'"
                [control]="albumForm.controls['S3ImagesPath']"
              ></app-input>
              <app-select [label]="'Labeling mode'"
                [disabled]="!isClassificationAlbum"
                [options]="uploadCSVPathOptions"
                [control]="albumForm.controls['CSVPathMode']">
              </app-select>
              <div [ngSwitch]="albumForm.value.CSVPathMode">
                <div *ngSwitchCase="UploadCSVPath.BUCKET_PATH">
                  <app-input
                    [label]="'Labels CSV file path'"
                    [placeholder]="'path/to/file.csv'"
                    [control]="albumForm.controls['S3CSVPath']"
                  ></app-input>
                </div>
                <div *ngSwitchCase="UploadCSVPath.LOCAL_FILE">
                  <app-input [label]="'Labels CSV file'"
                    [readonly]="true"
                    [iconAfter]="'glyphicon-tags'"
                    [placeholder]="'pick a file...'"
                    file-upload
                    [accept]="config.picture.labels.extension.list"
                    [value]="albumForm.value.labels?.name"
                    [file-upload-click]="true"
                    (onSelectFile)="onUploadLabels($event)"
                  ></app-input>
                </div>
              </div>
              <div *ngIf="!isClassificationAlbum" class="alert alert-warning" role="alert">
                <strong><i class="glyphicon glyphicon-exclamation-sign"></i></strong> Currently automatic labelling
                available only for Classification albums.
              </div>
              <app-check
                label="Apply Logarithmic Transformation"
                [control]="albumForm.controls['applyLogTransformation']"
              ></app-check>
            </ng-template>
            <ng-template [ngIf]="albumForm.value.uploadMode === UploadMode.S3_VIDEO">
              <s3-bucket-form [form]="albumForm"></s3-bucket-form>
              <app-input
                [label]="'Video path'"
                [placeholder]="'path/to/video.mp4'"
                [control]="albumForm.controls['S3VideoPath']"
              ></app-input>
              <app-input
                [type]="'number'"
                label="Extract every N'th frame"
                [min]="1"
                [max]="1000"
                [step]="1"
                [control]="albumForm.controls['frameRateDivider']"
              ></app-input>
            </ng-template>
          </div>
        </div>
      </form>
    </app-modal>`,
})

export class UploadImagesToAlbumModalComponent {
  readonly config = config;
  albumForm: FormGroup;
  readonly labelModeOptions: AppSelectOptionData[] = AppSelectOptionData.fromList(config.album.labelMode.list, config.album.labelMode.labels);
  readonly uploadModeOptions: AppSelectOptionData[] =
    AppSelectOptionData.fromList(uploadModeDescription.list, uploadModeDescription.labels);
  readonly uploadCSVPathOptions: AppSelectOptionData[] =
    AppSelectOptionData.fromList(uploadCSVPathDescription.list, uploadCSVPathDescription.labels);
  albumOptions: AppSelectOptionData[];
  albumList: IAlbum[];
  albumId: TObjectId;
  _albumIdLocked: boolean = false;
  readonly ZERO_ID = ZERO_ID;
  readonly UploadCSVPath = UploadCSVPath;
  readonly UploadMode = UploadMode;
  readonly _savingObserver = new ActivityObserver();
  @ViewChild('modal') private modal: ModalComponent;
  private albumsLoader: ReactiveLoader<IBackendList<IAlbum>, any>;

  constructor(private albums: AlbumService) {
    const
      albumNameControl = new FormControl(null),
      albumLabelModeControl = new FormControl(IAlbum.LabelMode.CLASSIFICATION),
      uploadModeControl = new FormControl(UploadMode.SINGLE_PICTURE, Validators.required),
      CSVPathModeControl = new FormControl(UploadCSVPath.LOCAL_FILE),
      imagesFileControl = new FormControl(null),
      labelsFileControl = new FormControl(null),
      s3ImagesPathControl = new FormControl(null),
      s3VideoPathControl = new FormControl(null),
      frameRateDividerControl = new FormControl(25),
      s3CSVPathControl = new FormControl(null),
      regionControl = new FormControl(null),
      bucketIdControl = new FormControl(null),
      bucketNameControl = new FormControl(null),
      accessKeyControl = new FormControl(null),
      secretKeyControl = new FormControl(null),
      sessionTokenControl = new FormControl(null),
      applyLogTransformationControl = new FormControl(true);

    this.albumForm = new FormGroup({
      filename: new FormControl(null),
      albumName: albumNameControl,
      albumLabelMode: albumLabelModeControl,
      uploadMode: uploadModeControl,
      AWSS3BucketId: bucketIdControl,
      AWSRegion: regionControl,
      AWSS3BucketName: bucketNameControl,
      AWSAccessKey: accessKeyControl,
      AWSSecretKey: secretKeyControl,
      AWSSessionToken: sessionTokenControl,
      S3ImagesPath: s3ImagesPathControl,
      S3VideoPath: s3VideoPathControl,
      frameRateDivider: frameRateDividerControl,
      file: imagesFileControl,
      labels: labelsFileControl,
      CSVPathMode: CSVPathModeControl,
      S3CSVPath: s3CSVPathControl,
      applyLogTransformation: applyLogTransformationControl,
    });
    //s3 credentials
    AppValidators.crossValidateMulti(
      [uploadModeControl, bucketIdControl],
      [regionControl, bucketNameControl, accessKeyControl, secretKeyControl, sessionTokenControl],
      (uploadMode: UploadMode, bucketId: string) => {
        return (uploadMode === UploadMode.S3_BUCKET && !bucketId)
          ? Validators.required
          : Validators.nullValidator;
      },
    );
    //image file
    AppValidators.crossValidate(uploadModeControl, [imagesFileControl], (value: UploadMode) => {
      if (value === UploadMode.SINGLE_PICTURE) {
        return Validators.required;
      }
      return Validators.nullValidator;
    });
    //image path
    AppValidators.crossValidate(uploadModeControl, [s3ImagesPathControl], (value: UploadMode) => {
      if (value === UploadMode.S3_BUCKET) {
        return Validators.required;
      }
      return Validators.nullValidator;
    });
    //video path
    AppValidators.crossValidate(uploadModeControl, [s3VideoPathControl, frameRateDividerControl], (value: UploadMode) => {
      if (value === UploadMode.S3_VIDEO) {
        return Validators.required;
      }
      return Validators.nullValidator;
    });
    //labels file
    AppValidators.crossValidateMulti(
      [uploadModeControl, CSVPathModeControl],
      [labelsFileControl],
      (uploadMode: UploadMode, csvPathMode: UploadCSVPath) => {
        return (uploadMode === UploadMode.S3_BUCKET && csvPathMode === UploadCSVPath.LOCAL_FILE)
          ? Validators.required
          : Validators.nullValidator;
      },
    );
    //labels s3path
    AppValidators.crossValidateMulti(
      [uploadModeControl, CSVPathModeControl],
      [s3CSVPathControl],
      (uploadMode: UploadMode, csvPathMode: UploadCSVPath) => {
        return (uploadMode === UploadMode.S3_BUCKET && csvPathMode === UploadCSVPath.BUCKET_PATH)
          ? Validators.required
          : Validators.nullValidator;
      },
    );

    this.albumsLoader = new ReactiveLoader(() => this._loadList());
    this.albumsLoader.subscribe((_: IBackendList<IAlbum>) => {
      this.albumList = _.data.filter(album => album.type === config.album.type.values.SOURCE);
      this.albumOptions = this.albumList.map(album => {
        return {
          id: album.id,
          text: album.name,
          disabled: album.status !== IAlbum.Status.ACTIVE || !!album.video,
        };
      });

      this.albumOptions.push({
        id: ZERO_ID,
        text: 'Create New Album',
      });
    });
  }

  isClassification(albumId: TObjectId, manualLabelMode: IAlbum.LabelMode) {
    const album = this.albumList.find(_ => _.id === albumId);
    if (!album && albumId !== ZERO_ID) {
      return false;
    }
    return (albumId === ZERO_ID ? manualLabelMode : album.labelMode) === config.album.labelMode.values.CLASSIFICATION;
  }

  setAlbum(albumId: TObjectId) {
    this.albumId = albumId;
    // new album name
    if (albumId === ZERO_ID) {
      this.albumForm.controls['albumName'].setValidators(Validators.required);
    } else {
      this.albumForm.controls['albumName'].setValidators(Validators.nullValidator);
    }
    this.albumForm.controls['albumName'].updateValueAndValidity();
    this.albumForm.controls['CSVPathMode'].setValue(UploadCSVPath.NONE);
    this.albumForm.controls['CSVPathMode'].updateValueAndValidity();
  }

  open(albumId?: TObjectId) {
    this.setAlbum(albumId || ZERO_ID);
    this._albumIdLocked = !!albumId;
    this.albumList = null;
    this.albumsLoader.load();
    MiscUtils.fillForm(this.albumForm, {
      filename: null,
      albumName: null,
      uploadMode: UploadMode.SINGLE_PICTURE,
      AWSS3BucketName: null,
      AWSAccessKey: null,
      AWSSecretKey: null,
      AWSSessionToken: null,
      file: null,
      labels: null,
      CSVPathMode: UploadCSVPath.NONE,
      S3CSVPath: null,
    });

    return this.modal.show();
  }

  onUploadPicture(file: File) {
    this.albumForm.controls['file'].setValue(file);
    this.albumForm.controls['filename'].setValue(file.name.replace(/\.\w+$/, ''));
  }

  onUploadLabels(file: File) {
    this.albumForm.controls['labels'].setValue(file);
  }

  doUpload() {
    this._savingObserver.observe(this.startUpload())
      .subscribe(null, null, () => this.modal.hide());
  }

  startUpload(): Observable<any> {
    const value = this.albumForm.value;
    switch (this.albumForm.value.uploadMode) {
      case UploadMode.SINGLE_PICTURE:
        return this.resolveAlbum().flatMap(albumId => this.albums.uploadPicture(albumId, {
          filename: value.filename,
          file: value.file,
        }));
      case UploadMode.S3_BUCKET:
        return this.resolveAlbum().flatMap(albumId => this.albums.importPicturesFromS3(albumId, {
          AWSS3BucketId: value.AWSS3BucketId,
          AWSRegion: value.AWSRegion,
          AWSS3BucketName: value.AWSS3BucketName,
          AWSAccessKey: value.AWSAccessKey,
          AWSSecretKey: value.AWSSecretKey,
          AWSSessionToken: value.AWSSessionToken,
          S3ImagesPath: value.S3ImagesPath,
          labels: value.labels,
          S3CSVPath: value.S3CSVPath,
          applyLogTransformation: value.applyLogTransformation,
        }));
      case UploadMode.S3_VIDEO:
        return this.resolveAlbum().flatMap(albumId => this.albums.importVideoFromS3(albumId, {
          AWSS3BucketId: value.AWSS3BucketId,
          AWSRegion: value.AWSRegion,
          AWSS3BucketName: value.AWSS3BucketName,
          AWSAccessKey: value.AWSAccessKey,
          AWSSecretKey: value.AWSSecretKey,
          AWSSessionToken: value.AWSSessionToken,
          S3VideoPath: value.S3VideoPath,
          frameRateDivider: parseInt(value.frameRateDivider),
        }));
      default:
        throw new Error('Unknown Upload Mode');
    }
  }

  protected _filterUploadModes = function(modes: AppSelectOptionData[], albumId: TObjectId): AppSelectOptionData[] {
    return modes.map(({id, text, disabled}) => ({
      id,
      text,
      disabled: disabled || (id === UploadMode.S3_VIDEO && albumId !== ZERO_ID),
    }));
  };

  private resolveAlbum(): Observable<TObjectId> {
    if (this.albumId === ZERO_ID) {
      const newAlbum: IAlbumCreate = {
        name: this.albumForm.value['albumName'],
        labelMode: this.albumForm.value['albumLabelMode'],
      };
      return this._savingObserver.observe(this.albums.create(newAlbum)).map(_ => _.id);
    } else {
      return Observable.of(this.albumId);
    }
  }

  private _loadList(): Observable<IBackendList<IAlbum>> {
    return this.albums.list({ page_size: 1000 }); //@TODO
  }
}
