import { ViewChild } from '@angular/core';
import { FormGroup } from '@angular/forms';

import { Observable } from 'rxjs/Observable';

import { ModalComponent } from '../../core-ui/components/modal.component';
import { IAsset, IAssetService } from '../interfaces/common.interface';

export abstract class ImportModalComponent<IP> {
  file: File;
  readonly defaultFormValue = {name: ''};
  @ViewChild('modal') protected modal: ModalComponent;

  protected constructor(
    readonly uploadForm: FormGroup,
    private service: IAssetService.Importable<IAsset, IP>,
  ) {}

  onSelectFile(file: File) {
    this.file = file;
    if (this.uploadForm.controls.hasOwnProperty('name')) {
      this.uploadForm.controls['name'].setValue(file.name.replace(/\.\w+$/, ''));
    }
  }

  abstract getImportParams(): IP;

  doUpload() {
    this.service.import(this.file, this.getImportParams());
    this.modal.hide();
  }

  open(): Observable<void> {
    this.uploadForm.reset(this.defaultFormValue);
    this.file = null;
    return this.modal.show();
  }
}
