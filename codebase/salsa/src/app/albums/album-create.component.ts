import { Component } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { ActivityObserver } from '../utils/activity-observer';

import { IAlbum } from './album.interface';
import { AlbumService } from './album.service';

@Component({
  selector: 'album-create',
  template: `
    <div class="col-sm-10 col-sm-offset-1 col-md-8 col-md-offset-2 col-xl-6 col-xl-offset-3">
      <h3 class="text-center">Create A New Album</h3>
      <form [formGroup]="form" (ngSubmit)="form.valid && onSubmit()">
        <app-input [label]="'Name'" [control]="form.controls['name']"></app-input>
        <app-description [control]="form.controls['description']" [editMode]="true"></app-description>
        <app-select [label]="'Label Mode'" [control]="form.controls['labelMode']"
          [options]="labelModeOptions"></app-select>
        <button type="submit"
          [disabled]="!form.valid || ((_savingObserver.active) | async)" class="btn btn-success pull-right">Create
        </button>
      </form>
    </div>`,
})
export class AlbumCreateComponent {
  form: FormGroup;
  config = config;
  readonly labelModeOptions: AppSelectOptionData[] = AppSelectOptionData.fromList(config.album.labelMode.list, config.album.labelMode.labels);
  readonly _savingObserver = new ActivityObserver();

  constructor(
    private router: Router,
    private albums: AlbumService,
  ) {
    this.form = new FormGroup({
      name: new FormControl('', Validators.required),
      description: new FormControl(''),
      labelMode: new FormControl(IAlbum.LabelMode.CLASSIFICATION, Validators.required),
    });
  }

  onSubmit() {
    this._savingObserver
      .observe(this.albums.create(this.form.value)).subscribe((album) => this.router.navigate(['/desk', 'albums', album.id]));
  }
}

