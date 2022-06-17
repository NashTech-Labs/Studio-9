import { Component } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';

import { ImportModalComponent } from '../core/components/import-modal.component';

import { ICVModelImport } from './cv-model.interface';
import { CVModelService } from './cv-model.service';
import { trainConfig } from './train.config';

@Component({
  selector: 'cv-model-upload-modal',
  template: `
    <app-modal #modal
      [caption]="'Upload cv-model file'"
      [buttons]="[{'class': 'btn-primary', disabled: (uploadForm.invalid || !file), title: 'Upload' }]"
      (buttonClick)="uploadForm.valid && file && doUpload()">
      <form [formGroup]="uploadForm">
        <app-input
          [label]="'Name'"
          [control]="uploadForm.controls['name']"></app-input>
        <app-input
          [label]="'File'"
          [readonly]="true"
          [iconAfter]="'glyphicon-file'"
          file-upload
          [accept]="acceptList"
          [file-upload-click]="true"
          [value]="file?.name"
          (onSelectFile)="onSelectFile($event)">Choose File
        </app-input>
      </form>
    </app-modal>
  `,
})
export class CVModelUploadModalComponent extends ImportModalComponent<ICVModelImport> {
  readonly acceptList = trainConfig.cvModel.fileExtensions;

  constructor(service: CVModelService) {
    super(
      new FormGroup({
        name: new FormControl(''),
      }),
      service,
    );
  }

  getImportParams(): ICVModelImport {
    return this.uploadForm.value;
  }
}
