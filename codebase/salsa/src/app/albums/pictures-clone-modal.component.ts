import { Component, ViewChild } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';

import { Observable } from 'rxjs/Observable';

import { ModalComponent } from '../core-ui/components/modal.component';
import { ActivityObserver } from '../utils/activity-observer';
import { AppFormGroup } from '../utils/forms';

import { IAlbum, IPicture } from './album.interface';
import { AlbumService } from './album.service';

@Component({
  selector: 'pictures-clone-modal',
  template: `
    <app-modal #modal [captionPrefix]="'Clone album '" [caption]="album?.name"
      [buttons]="[{'class': 'btn-primary', disabled: cloneForm.invalid || (_savingObserver.active | async), title: 'Clone' }]"
      (buttonClick)="cloneForm.valid && doClone()"
    >
      <form [formGroup]="cloneForm">
        <div class="form-group">
          <app-input [label]="'New Album Name'"
            [control]="cloneForm.controls['name']"
          ></app-input>
          <small>You've selected {{pictures.length}} picture(s)</small>
        </div>
      </form>
    </app-modal>
  `,
})
export class PicturesCloneModalComponent {
  public album;
  public pictures: IPicture[] = [];
  readonly cloneForm = new AppFormGroup({
    name: new FormControl('', Validators.required),
  });
  readonly _savingObserver = new ActivityObserver();
  @ViewChild('modal') private modal: ModalComponent;

  constructor(
    private albums: AlbumService,
  ) {
  }

  open(album: IAlbum, pictures: IPicture[]): Observable<void> {
    this.album = album;
    this.pictures = pictures;
    this.cloneForm.reset({
      name: '',
    });
    return this.modal.show();
  }

  doClone() {
    const observable = this.albums.clone(this.album.id, {
      name: this.cloneForm.controls['name'].value || undefined,
      pictureIds: this.pictures.map(picture => picture.id),
    });
    this._savingObserver.observe(observable).subscribe(() => {
      this.modal.hide();
    });
  }
}
