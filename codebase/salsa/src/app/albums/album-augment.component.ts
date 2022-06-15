import { Component } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import 'rxjs/add/operator/do';

import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { IAsset } from '../core/interfaces/common.interface';
import { ActivityObserver } from '../utils/activity-observer';
import { AppFormGroup } from '../utils/forms';
import { AppValidators } from '../utils/validators';

import { IAlbum, IAlbumAugmentParams } from './album.interface';
import { AlbumService } from './album.service';

@Component({
  selector: 'app-album-augment',
  template: `
    <asset-operations [type]="'${IAsset.Type.ALBUM}'" [selectedItems]="[]"></asset-operations>
    <form [formGroup]="form">
      <div class="row">
        <div class="col-md-6">
          <app-input [label]="'Output Album Name'" [control]="form.controls['outputName']"></app-input>
        </div>
        <div class="col-md-6">
          <div class="pull-right">
            <button class="btn btn-md btn-apply" (click)="submit()"
              [disabled]="form.invalid || !inputEntity || (_savingObserver.active | async)">
              Start
            </button>
          </div>
        </div>
      </div>

      <!-- inputs data -->
      <div class="pt5 row">
        <div class="col-md-6">
          <library-selector
            [inputLabel]="'Input Album'"
            [available]="['${IAsset.Type.ALBUM}']"
            [(value)]="inputEntity"
            [itemFilter]="_filterInputAlbumOptions"
            [caption]="'Select Training Input Data'"
          ></library-selector>
        </div>
        <div class="col-md-6">
          <app-check
            label="Include original images"
            [control]="form.controls['includeOriginalPictures']"
          ></app-check>
        </div>
      </div>
    </form>
    <app-tabs
      [tabs]="['Input Data', 'Augmentations']"
      [(active)]="activeTab"
    ></app-tabs>
    <!-- Tab panes -->
    <div class="flex-col" [adaptiveHeight]="{minHeight: 450}">
      <album-view-embed *ngIf="inputEntity?.id"
        [hidden]="activeTab !== 0"
        [albumId]="inputEntity.id"
      ></album-view-embed>
      <app-album-augmentation-params
        [hidden]="activeTab !== 1"
        [bloatFactorControl]="form.controls['bloatFactor']"
        (valueChange)="form.controls['augmentations'].setValue($event)"
      ></app-album-augmentation-params>
    </div>
  `,
})
export class AlbumAugmentComponent {

  readonly _savingObserver = new ActivityObserver();

  protected inputEntity: LibrarySelectorValue = null;
  protected form = new AppFormGroup({
    outputName: new FormControl('', Validators.required),
    augmentations: new FormControl([], Validators.required),
    includeOriginalPictures: new FormControl(true, Validators.required),
    bloatFactor: new FormControl(1, Validators.compose([
      Validators.required,
      Validators.min(1),
      AppValidators.number,
    ])),
  });
  protected activeTab: number = 1;

  constructor(
    private router: Router,
    private albums: AlbumService,
  ) {}

  protected submit() {
    const request: IAlbumAugmentParams = {
      outputName: this.form.controls.outputName.value,
      augmentations: this.form.controls.augmentations.value,
      includeOriginalPictures: !!this.form.controls.includeOriginalPictures.value,
      bloatFactor: +this.form.controls.bloatFactor.value,
    };

    this._savingObserver
      .observe(this.albums.augmentPictures(this.inputEntity.id, request))
      .subscribe((album) => this.router.navigate(['/desk', 'albums', album.id]));
  }

  protected _filterInputAlbumOptions = function(asset: LibrarySelectorValue): boolean {
    if (asset.entity === IAsset.Type.ALBUM) {
      return (<IAlbum> asset.object).type === IAlbum.Type.SOURCE;
    }

    return false;
  };
}
