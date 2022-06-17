import { Component, ViewChild } from '@angular/core';
import { FormControl, Validators } from '@angular/forms';

import { Observable } from 'rxjs/Observable';

import { AlbumService } from '../../albums/album.service';
import { FlowService } from '../../compose/flow.service';
import config from '../../config';
import { ModalComponent } from '../../core-ui/components/modal.component';
import { TableService } from '../../tables/table.service';
import { ModelService } from '../../train/model.service';
import { ActivityObserver } from '../../utils/activity-observer';
import { AppFormGroup } from '../../utils/forms';
import { IAsset, TObjectId } from '../interfaces/common.interface';

/**
 * This is dependent upon set of services, should work accepting LibrarySectionDefinition,
 * including some parameters definition changing the layout
 */
@Component({
  selector: 'clone-modal',
  template: `
    <app-modal #modal [captionPrefix]="'Clone ' + config.asset.labels[assetType]" [caption]="assetName"
      [buttons]="[{'class': 'btn-primary', disabled: cloneForm.invalid || (_savingObserver.active | async), title: 'Clone' }]"
      (buttonClick)="cloneForm.valid && doClone()"
    >
      <form [formGroup]="cloneForm">
        <div class="form-group">
          <app-input [label]="'New ' + config.asset.labels[assetType] + ' Name'"
            [control]="cloneForm.controls['name']"
          ></app-input>
          <app-check *ngIf="assetType === '${IAsset.Type.ALBUM}'"
            label="Include only labelled pictures"
            [control]="cloneForm.controls['copyOnlyLabelledPictures']"
          ></app-check>
        </div>
      </form>
    </app-modal>
  `,
})
export class CloneModalComponent {
  config = config;
  assetId: TObjectId;
  assetType: IAsset.Type;
  assetName: string;
  readonly cloneForm = new AppFormGroup({
    name: new FormControl('', Validators.required),
    copyOnlyLabelledPictures: new FormControl(false, Validators.required),
  });
  readonly _savingObserver = new ActivityObserver();
  @ViewChild('modal') private modal: ModalComponent;

  constructor(
    private tables: TableService,
    private albums: AlbumService,
    private flows: FlowService,
    private models: ModelService,
  ) {}

  open(assetType: IAsset.Type, assetId: TObjectId, assetName: string = 'Asset'): Observable<void> {
    this.assetType = assetType;
    this.assetId = assetId;
    this.assetName = assetName;
    this.cloneForm.reset({
      name: '',
      copyOnlyLabelledPictures: false,
    });
    return this.modal.show();
  }

  doClone() {
    let observable: Observable<any>;
    switch (this.assetType) {
      case IAsset.Type.TABLE:
        observable = this.tables.clone(this.assetId, { name: this.cloneForm.controls['name'].value });
        break;
      case IAsset.Type.ALBUM:
        observable = this.albums.clone(this.assetId, {
          name: this.cloneForm.controls['name'].value || undefined,
          copyOnlyLabelledPictures: this.cloneForm.controls.copyOnlyLabelledPictures.value,
        });
        break;
      case IAsset.Type.FLOW:
        observable = this.flows.clone(this.assetId, { name: this.cloneForm.controls['name'].value });
        break;
      case IAsset.Type.MODEL:
        observable = this.models.clone(this.assetId, { name: this.cloneForm.controls['name'].value });
        break;
      case IAsset.Type.OPTIMIZATION:
        break;
      default:
        console.error('Unknown Asset Type');
        throw new Error('Unknown Asset Type');
    }

    this._savingObserver.observe(observable).subscribe(() => {
      this.modal.hide();
    });
  }

  static canClone(assetType: IAsset.Type): boolean {
    return [
      IAsset.Type.TABLE,
      IAsset.Type.ALBUM,
      IAsset.Type.MODEL,
      IAsset.Type.FLOW,
    ].includes(assetType);
  }
}
