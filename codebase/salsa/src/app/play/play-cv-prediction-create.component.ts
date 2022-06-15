import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import 'rxjs/add/operator/do';

import { IAlbum } from '../albums/album.interface';
import config from '../config';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { IAsset } from '../core/interfaces/common.interface';
import { ICVModel } from '../train/cv-model.interface';
import { getResultLabelModeByModelType } from '../train/train.helpers';

import { PlayCVPredictionCreateAdvancedComponent } from './cv-prediction-create-advanced.component';
import { ICVPrediction, ICVPredictionCreate } from './cv-prediction.interface';
import { CVPredictionService } from './cv-prediction.service';
import { IPlayChildComponent } from './play-create.component';

// TODO: add validator's errors

@Component({
  selector: 'app-play-cv-model',
  template: `
    <form *ngIf="model" [formGroup]="form" class="brand-tab">
      <div class="row">
        <div class="col-md-6">
          <library-selector
            [inputLabel]="'Select Input Album'"
            [value]="{id: form.value['input'], entity: config.asset.values.ALBUM}"
            (valueChange)="setInputAlbum($event)"
            [itemFilter]="model | apply: _prepareAlbumFilter: form.controls['evaluate'].value"
            [available]="[config.asset.values.ALBUM]"
            [caption]="'Select Album'"
          ></library-selector>
        </div>
        <div class="col-md-4">
          <app-input [label]="'Results Name'" [control]="form.controls['outputAlbumName']"></app-input>
        </div>
        <div class="col-md-2">
          <app-check [label]="'Evaluation Mode'" [control]="form.controls['evaluate']"
            (checkedChange)="evaluationModeChange($event)"
          ></app-check>
        </div>
      </div>
      <div class="row">
        <div class="col-md-6">
          <app-description [control]="form.controls['description']" [editMode]="true"></app-description>
        </div>
      </div>
      <app-tabs [tabs]="(model | apply: _advancedOptionsAvailable)
        ? ['Model Info', 'Input', 'Prediction Options']
        : ['Model Info', 'Input']
      " [(active)]="activeTab"></app-tabs>
      <div class="tab-content brand-tab">
        <div role="tabpanel" class="tab-pane" [ngClass]="{'active': activeTab === 0}">
          <cv-model-view-embed
            [model]="model"
          ></cv-model-view-embed>
        </div>
        <div role="tabpanel" class="tab-pane" [ngClass]="{'active': activeTab === 1}">
          <album-view-embed *ngIf="form.value['input']" [albumId]="form.value['input']"></album-view-embed>
        </div>
        <div *ngIf="model | apply: _advancedOptionsAvailable"
          role="tabpanel" class="tab-pane" [ngClass]="{'active': activeTab === 2}">
          <div class="panel">
            <div class="panel-body">
              <cv-prediction-create-advanced
                [model]="model"
                (valueChange)="form.controls['options'].setValue($event)"
              ></cv-prediction-create-advanced>
            </div>
          </div>
        </div>
      </div>
    </form>
  `,
})
export class PlayCVPredictionCreateComponent implements OnChanges, IPlayChildComponent {
  @Input() model: ICVModel;
  @Output() changeValidity: EventEmitter<boolean> = new EventEmitter<boolean>();
  config = config;
  form: FormGroup;
  activeTab: number = 0;
  album: IAlbum = null;

  constructor(
    private predictions: CVPredictionService,
    private router: Router,
  ) {}

  ngOnChanges(changes: SimpleChanges) {
    if ('model' in changes) {
      this.onModelChanged();
    }
  }

  submit() {
    const formValue = this.form.value;
    let prediction: ICVPredictionCreate = {
      modelId: this.model.id,
      name: formValue.outputAlbumName,
      description: formValue.description,
      input: formValue.input,
      outputAlbumName: formValue.outputAlbumName,
      options: formValue.options,
      evaluate: formValue.evaluate,
    };
    return this.predictions.create(prediction).do((prediction: ICVPrediction) => {
      this.router.navigate(['/desk', 'play', 'cv-predictions', prediction.id]);
    });
  }

  clear() {
    this.initForm();
  }

  evaluationModeChange(evaluationMode: boolean) {
    if (this.album && !this._isAlbumValid(this.model, evaluationMode, this.album)) {
      this.form.controls['input'].setValue(null);
      this.album = null;
      this.activeTab = 0;
    }
  }

  setInputAlbum(selection: LibrarySelectorValue) {
    this.album = selection && selection.object as IAlbum;
    this.form.controls['input'].setValue(selection ? selection.id : null);
    this.activeTab = 1;
  }

  protected _prepareAlbumFilter = (model: ICVModel, evaluationMode: boolean) => {
    return (item: LibrarySelectorValue) =>
      (item.entity === IAsset.Type.ALBUM && this._isAlbumValid(model, evaluationMode, <IAlbum> item.object));
  };

  private onModelChanged() {
    this.initForm();
  }

  private initForm() {
    this.form = new FormGroup({
      input: new FormControl(null, Validators.required),
      description: new FormControl(null),
      outputAlbumName: new FormControl(null, Validators.required),
      options: new FormControl(null),
      evaluate: new FormControl(false),
    });
    this.form.valueChanges.subscribe(() => {
      this.changeValidity.emit(this.form.valid);
    });
    this.album = null;
    this.activeTab = 0;
  }

  private _isAlbumValid(model: ICVModel, evaluationMode: boolean, album: IAlbum): boolean {
    return !evaluationMode
      || !album
      || getResultLabelModeByModelType(model.modelType) === album.labelMode;
  }

  private _advancedOptionsAvailable(model: ICVModel): boolean {
    return model && PlayCVPredictionCreateAdvancedComponent.advancedOptionsAvailable(model);
  }
}
