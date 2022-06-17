import { Component } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';

import config from '../config';
import { ImportModalComponent } from '../core/components/import-modal.component';

import { ITableImport, TableService } from './table.service';

@Component({
  selector: 'table-upload-modal',
  template: `
    <app-modal #modal [caption]="'Upload file'"
      [buttons]="[{'class': 'btn-primary', disabled: (uploadForm.invalid || !file), title: 'Upload' }]"
      (buttonClick)="uploadForm.valid && file && doUpload()"
      >
      <form [formGroup]="uploadForm">
        <app-select
          [label]="'Format Type'"
          [control]="uploadForm.controls['format']"
          [options]="formatList"></app-select>
        <app-select
          *ngIf="uploadForm.value.format === 'csv'"
          [label]="'Delimiter'"
          [control]="uploadForm.controls['delimiter']"
          [options]="delimiterList"></app-select>
        <app-input [label]="'Table Name'" [control]="uploadForm.controls['name']"></app-input>
        <app-input [label]="'File'"
          [readonly]="true"
          [iconAfter]="'glyphicon-file'"
          file-upload
          [accept]="acceptList"
          [file-upload-click]="true"
          [value]="file?.name"
          (onSelectFile)="onSelectFile($event)">Choose File
        </app-input>
        <app-description [control]="uploadForm.controls['description']" [editMode]="true"></app-description>
        <ng-template [ngIf]="uploadForm.value.format === 'csv'">
          <app-check [label]="'Determine Null Values'" [control]="uploadForm.controls['determNulls']"></app-check>
          <app-input [label]="'Null Value'"
            *ngIf="uploadForm.value.determNulls"
            [control]="uploadForm.controls['nullValue']"></app-input>
        </ng-template>
      </form>
    </app-modal>
  `,
})
export class TableUploadModalComponent extends ImportModalComponent<ITableImport> {
  readonly delimiterList = [
    { id: ',', text: 'Comma (CSV)' },
    { id: '\t', text: 'Tabulation (TSV)' },
    { id: ';', text: 'Semicolon' },
  ];
  readonly formatList = [
    { id: 'csv', text: 'CSV/TXT' },
    { id: 'json', text: 'JSON' },
  ];
  readonly acceptList = config.table.import.extension.list;
  readonly defaultFormValue = {format: 'csv', name: null, delimiter: ',', nullValue: 'NULL', determNulls: false};

  constructor(service: TableService) {
    super(
      new FormGroup({
        format: new FormControl('csv', Validators.required),
        name: new FormControl(null, Validators.required),
        delimiter: new FormControl(',', Validators.required),
        nullValue: new FormControl('NULL'),
        determNulls: new FormControl(false),
        description: new FormControl(''),
      }),
      service,
    );
  }

  getImportParams(): ITableImport {
    const formValue = this.uploadForm.value;
    return {
      ...formValue,
      nullValue: formValue.determNulls ? formValue.nullValue : undefined,
    };
  }
}
