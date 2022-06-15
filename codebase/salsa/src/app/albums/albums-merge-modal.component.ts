import { Component, ViewChild } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';

import { Observable } from 'rxjs/Observable';

import { ModalComponent } from '../core-ui/components/modal.component';
import { ActivityObserver } from '../utils/activity-observer';
import { AppFormGroup } from '../utils/forms';

import { IAlbum } from './album.interface';
import { AlbumService } from './album.service';

@Component({
  selector: 'albums-merge-modal',
  template: `
    <app-modal #modal [caption]="'Merge albums'"
      [buttons]="[{'class': 'btn-primary', disabled: mergeForm.invalid || (_savingObserver.active | async), title: 'Merge' }]"
      (buttonClick)="mergeForm.valid && doMerge()"
    >
      <form [formGroup]="mergeForm">
        <div class="form-group">
          <app-input
            label="New Album Name"
            [control]="mergeForm.controls['name']"
          ></app-input>
          <app-check
            label="Include only labelled pictures"
            [control]="mergeForm.controls['copyOnlyLabelledPictures']"
          ></app-check>
        </div>
      </form>
    </app-modal>
  `,
})
export class AlbumsMergeModalComponent {
  @ViewChild('modal') readonly modal: ModalComponent;

  readonly _savingObserver = new ActivityObserver();
  readonly mergeForm = new AppFormGroup({
    name: new FormControl('', Validators.required),
    copyOnlyLabelledPictures: new FormControl(false, Validators.required),
  });

  private items: IAlbum[] = [];

  constructor(
    private service: AlbumService,
  ) {}

  open(items: IAlbum[]): Observable<void> {
    this.mergeForm.reset({
      name: '',
      copyOnlyLabelledPictures: false,
    });
    this.items = items;

    return this.modal.show();
  }

  doMerge() {
    this.service.create({
      name: this.mergeForm.controls.name.value,
      labelMode: this.items[0].labelMode,
      copyPicturesFrom: this.items.map(_ => _.id),
      copyOnlyLabelledPictures: !!this.mergeForm.controls.copyOnlyLabelledPictures.value,
    }).subscribe(() => {
      this.modal.hide();
    });
  }
}
