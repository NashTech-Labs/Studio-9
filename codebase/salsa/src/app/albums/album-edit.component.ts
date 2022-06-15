import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { TObjectId } from '../core/interfaces/common.interface';

import { IAlbum, IAlbumUpdate } from './album.interface';
import { AlbumService } from './album.service';

@Component({
  selector: 'app-album-edit',
  template: `
    <div [hidden]="pictureId">
      <asset-operations [type]="config.asset.values.ALBUM"
        [selectedItems]="[album]">
      </asset-operations>

      <app-spinner [visibility]="!album"></app-spinner>
      <div *ngIf="album"
        class="col-sm-10 col-sm-offset-1 col-md-8 col-md-offset-2 col-xl-6 col-xl-offset-3"
      >
        <h3 class="text-center">Edit Album</h3>
        <form [formGroup]="editForm">
          <app-input [label]="'Album Name'" [control]="editForm.controls['name']"></app-input>
          <app-description [control]="editForm.controls['description']"></app-description>
          <app-select [label]="'Label Mode'" [control]="editForm.controls['labelMode']"
            [options]="labelModeOptions"
            [disabled]="album.type !== config.album.type.values.SOURCE"
          ></app-select>
          <button type="button" class="btn btn-primary pull-right"
            [disabled]="editForm.invalid || editForm.pristine || editForm.disabled"
            (click)="onSubmit()"
          >
            Update&nbsp;<i class="glyphicon glyphicon-ok"></i>
          </button>
        </form>
      </div>
    </div>
  `,
})
export class AlbumEditComponent implements OnInit, OnDestroy {
  @Input() type: string;
  config = config;
  albumId: TObjectId;
  pictureId: TObjectId;
  editForm: FormGroup;
  labelModeOptions: AppSelectOptionData[] = AppSelectOptionData.fromList(config.album.labelMode.list, config.album.labelMode.labels);
  album: IAlbum;
  private routeSubscription: Subscription;

  constructor(
    private route: ActivatedRoute,
    private albums: AlbumService,
    private router: Router,
  ) {
    this.editForm = new FormGroup({
      name: new FormControl('', Validators.required),
      labelMode: new FormControl(IAlbum.LabelMode.LOCALIZATION, Validators.required),
      description: new FormControl(null),
    });
  }

  ngOnInit() {
    this.routeSubscription = this.route.params.subscribe(params => {
      this.albumId = params['albumId'];
      if (this.albumId) {
        this.albums.get(this.albumId).subscribe((album) => {
          this.album = album;
          this.editForm.controls['name'].setValue(album.name);
          this.editForm.controls['description'].setValue(album.description);
          this.editForm.controls['labelMode'].setValue(album.labelMode);
        });
      } else {
        this.router.navigate(['/desk', 'albums', 'create']);
      }
    });
  }

  onSubmit() {
    this.albums.update(this.album.id, <IAlbumUpdate> this.editForm.value).subscribe(() => {
      this.router.navigate(['/desk', 'albums', this.album.id]);
    });
  }


  ngOnDestroy() {
    this.routeSubscription.unsubscribe();
  }
}
