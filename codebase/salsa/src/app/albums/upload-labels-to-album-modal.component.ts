import { Component, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';

import { Observable } from 'rxjs/Observable';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { ModalComponent } from '../core-ui/components/modal.component';
import { TObjectId } from '../core/interfaces/common.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ActivityObserver } from '../utils/activity-observer';
import { MiscUtils, describeEnum } from '../utils/misc';
import { AppValidators } from '../utils/validators';

import { AlbumService } from './album.service';

enum CSVLocation {
  LOCAL_FILE = 'LOCAL_FILE',
  BUCKET_PATH = 'BUCKET_PATH',
}

const CSVLocationDescription = describeEnum(CSVLocation, {
  labels: {
    LOCAL_FILE: 'Local Desktop File',
    BUCKET_PATH: 'S3 Bucket Path',
  },
});

@Component({
  selector: 'upload-labels-to-album-modal',
  template: `
    <app-modal #modal
      [caption]="'Upload Labels'"
      [buttons]="[{ 'class': 'btn-primary',
        disabled: (CSVPathMode === CSVLocationVariable.LOCAL_FILE && localUploadForm.invalid)
          || (CSVPathMode === CSVLocationVariable.BUCKET_PATH && s3UploadForm.invalid)
          || (_savingObserver.active | async),
        title: 'Upload' }]"
      (buttonClick)="((CSVLocationVariable.LOCAL_FILE && localUploadForm.valid)
        || (CSVPathMode === CSVLocationVariable.BUCKET_PATH && s3UploadForm.valid)) && doUpload()">
      <form>
        <div class="form-group">
          <app-select [label]="'CSV Location'"
            [options]="CSVLocationOptions"
            [(value)]="CSVPathMode">
          </app-select>
          <div [ngSwitch]="CSVPathMode">
            <div *ngSwitchCase="CSVLocationVariable.BUCKET_PATH">
              <s3-bucket-form [form]="s3UploadForm"></s3-bucket-form>
              <app-input
                [label]="'S3 Bucket Path'"
                [placeholder]="'path/to/file.csv'"
                [control]="s3UploadForm.controls['S3CSVPath']"
              ></app-input>
            </div>
            <div *ngSwitchCase="CSVLocationVariable.LOCAL_FILE">
              <app-input [label]="'Labels CSV file'"
                [readonly]="true"
                [iconAfter]="'glyphicon-tags'"
                [placeholder]="'pick a file...'"
                file-upload
                [accept]="config.picture.labels.extension.list"
                [value]="localUploadForm.value.labels?.name"
                [file-upload-click]="true"
                (onSelectFile)="onUploadLabels($event)"
              ></app-input>
            </div>
          </div>
        </div>
      </form>
    </app-modal>`,
})


export class UploadLabelsToAlbumModalComponent {
  readonly config = config;
  readonly _savingObserver = new ActivityObserver();
  readonly CSVLocationVariable = CSVLocation;
  readonly CSVLocationOptions: AppSelectOptionData[] =
    AppSelectOptionData.fromList(CSVLocationDescription.list, CSVLocationDescription.labels);
  readonly s3UploadForm: FormGroup;
  readonly localUploadForm: FormGroup;
  albumId: TObjectId;
  CSVPathMode: CSVLocation;
  @ViewChild('modal') private modal: ModalComponent;

  constructor(private albums: AlbumService, private events: EventService) {
    const buckedIdControl = new FormControl(null);
    const regionControl = new FormControl(null, Validators.required);
    const bucketNameControl = new FormControl(null, Validators.required);
    const accessKeyControl = new FormControl(null, Validators.required);
    const secretKeyControl = new FormControl(null, Validators.required);
    const sessionTokenControl = new FormControl(null, Validators.required);
    AppValidators.crossValidate(
      buckedIdControl,
      [regionControl, bucketNameControl, accessKeyControl, secretKeyControl, sessionTokenControl],
      (bucketId: string) => {
        return !bucketId
          ? Validators.required
          : Validators.nullValidator;
      },
    );

    this.s3UploadForm = new FormGroup({
      AWSS3BucketId: buckedIdControl,
      AWSRegion: regionControl,
      AWSS3BucketName: bucketNameControl,
      AWSAccessKey: accessKeyControl,
      AWSSecretKey: secretKeyControl,
      AWSSessionToken: sessionTokenControl,
      S3CSVPath: new FormControl(null, Validators.required),
    });

    this.localUploadForm = new FormGroup({
      labels: new FormControl(null, Validators.required),
    });
  }

  setAlbum(albumId: TObjectId) {
    this.albumId = albumId;
    this.CSVPathMode = CSVLocation.LOCAL_FILE;
  }

  open(albumId: TObjectId) {
    this.setAlbum(albumId);
    this.CSVPathMode = CSVLocation.LOCAL_FILE;
    MiscUtils.fillForm(this.s3UploadForm, {
      AWSS3BucketName: null,
      AWSAccessKey: null,
      AWSSecretKey: null,
      AWSSessionToken: null,
      labels: null,
      S3CSVPath: null,
    });
    MiscUtils.fillForm(this.localUploadForm, {
      labels: null,
    });
    this.s3UploadForm.markAsPristine();
    this.localUploadForm.markAsPristine();
    this.modal.show();
  }

  onUploadLabels(file: File) {
    this.localUploadForm.controls['labels'].setValue(file);
  }

  doUpload() {
    this._savingObserver.observe(this.resolveAlbumnUploadFunction())
      .subscribe(null, null, () => {
        this.events.emit(IEvent.Type.UPDATE_ALBUM, { id: this.albumId });
        this.modal.hide();
      });
  }

  private resolveAlbumnUploadFunction(): Observable<any> {
    switch (this.CSVPathMode) {
      case CSVLocation.BUCKET_PATH:
        return this.albums.importLabelsFromS3(this.albumId, this.s3UploadForm.value);
      case CSVLocation.LOCAL_FILE:
        return this.albums.uploadLabels(this.albumId, this.localUploadForm.value.labels);
      default:
        throw new Error('Unknown Path Mode');
    }
  }
}
