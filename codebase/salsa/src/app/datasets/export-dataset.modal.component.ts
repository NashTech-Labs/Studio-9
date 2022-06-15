import { Component } from '@angular/core';
import { Router } from '@angular/router';

import { Observable } from 'rxjs/Observable';
import { map } from 'rxjs/operators/map';

import { TObjectId } from '../core/interfaces/common.interface';
import { LibrarySectionDefinition } from '../library/library.interface';
import { EntityControlDeep } from '../utils/forms';

import {
  DatasetIOModalComponent,
  ZERO_ID,
} from './dataset-io.modal.component';
import { BinaryDataset } from './dataset.interfaces';
import { BinaryDatasetService } from './dataset.service';

@Component({
  selector: 'export-dataset-modal',
  template: `
    <app-modal #modal
      [caption]="'Export Dataset Files'"
      [buttons]="[{
        'class': 'btn-primary',
        disabled: (form.invalid || ((_savingObserver.active) | async) || datasetId === '${ZERO_ID}'),
        title: 'Upload'
      }]"
      (buttonClick)="form.valid && action()">
      <form>
        <app-spinner [visibility]="datasetsLoader.active | async"></app-spinner>

        <div *ngIf="datasetOptions">
          <app-select
            label="Dataset"
            [disabled]="datasetIdLocked"
            [(value)]="datasetId"
            (valueChange)="_setDatasetId($event)"
            [options]="datasetOptions"
          ></app-select>

          <div>
            <s3-bucket-form [form]="form.controls.s3Bucket"></s3-bucket-form>
            <app-input
              [label]="'Files path'"
              [placeholder]="'path/to/files/'"
              [control]="form.controls.path"
            ></app-input>
          </div>
        </div>
      </form>
    </app-modal>`,
})

export class ExportDatasetModalComponent extends DatasetIOModalComponent
  implements LibrarySectionDefinition.BulkModalComponent<BinaryDataset> {

  protected form: EntityControlDeep<BinaryDataset.S3IOReference>;

  constructor(datasetService: BinaryDatasetService, router: Router) {
    super(datasetService, router);
  }

  open(items: BinaryDataset[]): Observable<any> {
    return this._open(items.map(_ => _.id).pop());
  }

  protected _prepareForm() {
    this.form = super._prepareS3Form();
  }

  protected _action(): Observable<TObjectId> {
    const value = this.form.value;
    return this.datasetService.exportToS3(
      this.datasetId,
      {
        to: {
          s3Bucket: value.s3Bucket,
          path: value.path,
        },
      },
    ).pipe(map(() => this.datasetId));
  }

  static isAvailable(items: BinaryDataset[]) {
    return items.length === 1;
  }
}
