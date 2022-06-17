import { Component } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { Observable } from 'rxjs/Observable';
import { map } from 'rxjs/operators/map';

import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { IBackendList, TObjectId } from '../core/interfaces/common.interface';
import { LibrarySectionDefinition } from '../library/library.interface';
import { AppFormGroup, EntityControlDeep } from '../utils/forms';
import { MiscUtils, describeEnum } from '../utils/misc';
import { AppValidators } from '../utils/validators';

import { DatasetIOModalComponent, ZERO_ID } from './dataset-io.modal.component';
import { BinaryDataset } from './dataset.interfaces';
import { BinaryDatasetService } from './dataset.service';

enum UploadMode {
  FILES = 'FILES',
  S3_BUCKET = 'S3_BUCKET',
}

const uploadModeDescription =  describeEnum(UploadMode, {
  labels: {
    FILES: 'Upload Files',
    S3_BUCKET: 'Import Files From S3',
  },
});

const MAX_FILE_NAMES_SHOW = 3;

@Component({
  selector: 'upload-files-to-dataset-modal',
  template: `
    <app-modal #modal
      [caption]="'Import/Upload Files To Dataset'"
      [buttons]="[{
        'class': 'btn-primary',
        disabled: (form.invalid || ((_savingObserver.active) | async)),
        title: 'Upload'
      }]"
      (buttonClick)="form.valid && action()">
      <form>
        <app-spinner [visibility]="datasetsLoader.active | async"></app-spinner>

        <div *ngIf="datasetOptions">
          <app-select [label]="'Dataset'"
            [disabled]="datasetIdLocked"
            [(value)]="datasetId"
            (valueChange)="_setDatasetId($event)"
            [options]="datasetOptions"
          ></app-select>

          <ng-template [ngIf]="datasetId === '${ZERO_ID}'">
            <div class="form-group">
              <app-input [label]="'Dataset Name'" [control]="form.controls['datasetName']"></app-input>
            </div>
          </ng-template>

          <app-select [label]="'Upload Mode'" [options]="uploadModeOptions"
            [control]="form.controls['uploadMode']">
          </app-select>

          <ng-template
            [ngIf]="form.value['uploadMode'] === '${UploadMode.FILES}'"
          >
            <app-input
              label="Files"
              iconAfter="glyphicon-remove"
              placeholder="pick files..."
              file-upload
              [accept]="[]"
              [readonly]="true"
              [value]="form.controls['files'].value | apply: filesNames"
              [file-upload-click]="true"
              [file-upload-multiple]="true"
              (onSelectFiles)="onFilesSelected($event)"
            >
              Upload Files
            </app-input>
          </ng-template>

          <div *ngIf="form.value['uploadMode'] === '${UploadMode.S3_BUCKET}'">
            <ng-container *ngVar="form.controls.s3Params as s3Form">
              <s3-bucket-form [form]="s3Form.controls.s3Bucket"></s3-bucket-form>
              <app-input
                [label]="'Files path'"
                [placeholder]="'path/to/files/'"
                [control]="s3Form.controls.path"
              ></app-input>
            </ng-container>
          </div>
        </div>
      </form>
    </app-modal>`,
})

export class UploadFilesToDatasetModalComponent extends DatasetIOModalComponent
  implements LibrarySectionDefinition.ModalComponent {

  readonly uploadModeOptions: AppSelectOptionData[] =
    AppSelectOptionData.fromList(uploadModeDescription.list, uploadModeDescription.labels);

  protected files = [];
  protected form: AppFormGroup<{
    uploadMode: FormControl,
    datasetName: FormControl,
    s3Params: EntityControlDeep<BinaryDataset.S3IOReference>,
    files: FormControl,
  }>;

  constructor(datasetService: BinaryDatasetService, router: Router) {
    super(datasetService, router);
  }

  open(datasetId: TObjectId): Observable<void> {
    MiscUtils.fillForm(this.form, {
      uploadMode: UploadMode.FILES,
    });

    return this._open(datasetId);
  }

  protected _prepareForm() {
    const uploadModeControl = new FormControl(UploadMode.FILES, Validators.required);
    const s3ParamsControl = super._prepareS3Form();
    const filesControl = new FormControl(null);

    this.form = new AppFormGroup({
      uploadMode: uploadModeControl,
      s3Params: s3ParamsControl,
      datasetName: new FormControl(null),
      files: filesControl,
    });

    // s3 credentials
    AppValidators.crossValidate(
      uploadModeControl,
      [s3ParamsControl],
      (uploadMode: UploadMode) => {
        return (uploadMode === UploadMode.S3_BUCKET) ? Validators.required : Validators.nullValidator;
      },
      (uploadMode: UploadMode) => uploadMode === UploadMode.S3_BUCKET,
    );

    // files
    AppValidators.crossValidate(
      uploadModeControl,
      [filesControl],
      (value: UploadMode) => {
        return value === UploadMode.FILES ? Validators.required : Validators.nullValidator;
      },
      (uploadMode: UploadMode) => uploadMode === UploadMode.FILES,
    );
  }

  protected _onDatasetsLoaded(datasetList: IBackendList<BinaryDataset>): void {
    super._onDatasetsLoaded(datasetList);
    this.datasetOptions.unshift({
      id: ZERO_ID,
      text: 'Create New Dataset',
    });
  }

  protected onFilesSelected(files: File[]): void {
    this.form.controls.files.setValue(files.filter(_ => _.size > 0));
  }

  protected _setDatasetId(datasetId: TObjectId): void {
    super._setDatasetId(datasetId);

    const datasetNameControl = this.form.controls.datasetName;

    if (this.datasetId === ZERO_ID) {
      datasetNameControl.setValidators(Validators.required);
      datasetNameControl.enable();
    } else {
      datasetNameControl.setValidators(Validators.nullValidator);
      datasetNameControl.disable();
    }
    datasetNameControl.updateValueAndValidity();
  }

  protected _action(): Observable<TObjectId> {
    const value = this.form.value;
    switch (this.form.value.uploadMode) {
      case UploadMode.FILES:
        return this.resolveDataset()
          .flatMap(datasetId => {
            return this.datasetService.uploadFiles(
              datasetId,
              this.form.controls.files.value,
            ).pipe(map(() => datasetId));
          });
      case UploadMode.S3_BUCKET:
        return this.resolveDataset()
          .flatMap(datasetId => {
            return this.datasetService.importFromS3(
              datasetId,
              {
                from: value.s3Params,
              },
            ).pipe(map(() => datasetId));
          });
      default:
        throw new Error('Unknown Upload Mode');
    }
  }

  protected filesNames(files: File[]): string {
    if (files) {
      if (files.length > MAX_FILE_NAMES_SHOW) {
        return files.length + ' files selected';
      }
      return files.map(file => file.name).join(', ');
    }
    return null;
  }

  private resolveDataset(): Observable<TObjectId> {
    if (this.datasetId === ZERO_ID) {
      const newDataset: BinaryDataset.CreateRequest = {
        name: this.form.value['datasetName'],
      };
      return this._savingObserver.observe(this.datasetService.create(newDataset)).map(_ => _.id);
    } else {
      return Observable.of(this.datasetId);
    }
  }
}
