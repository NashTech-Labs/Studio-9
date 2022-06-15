import { Component, HostBinding, Input, OnDestroy, OnInit, ViewChild } from '@angular/core';

import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { SaveToLibraryModalComponent } from '../core/components/save-to-library.modal.component';
import { IAsset, TObjectId } from '../core/interfaces/common.interface';
import { ProcessService } from '../core/services/process.service';
import { ReactiveLoader } from '../utils/reactive-loader';

import { ITabularModel } from './model.interface';
import { ModelService } from './model.service';
import { trainConfig } from './train.config';

@Component({
  selector: 'model-view-embed',
  template: `
    <app-spinner [visibility]="!model"></app-spinner>
    <ng-template [ngIf]="model">
      <div class="panel">
        <div class="panel-body">
          <div class="row">
            <div class="col-md-6 col-md-push-6">
              <div class="btn-group pull-right" role="group">
                <button type="button"
                  *ngIf="!model.inLibrary"
                  class="btn btn-md"
                  (click)="saveModel()"
                  title="Save Model To The Library"
                ><i class="glyphicon glyphicon-book"></i> Save To The Library</button>
              </div>
            </div>
            <div class="col-md-6 col-md-pull-6">
              <dl>
                <dt>Model Type:</dt>
                <dd>{{trainConfig.model.class.labels[model.class]}}</dd>
                <dt>Predictors:</dt>
                <dd *ngFor="let predictor of model.predictorColumns">
                  {{predictor.displayName || predictor.name}}
                  ({{config.table.column.variableType.labels[predictor.variableType]}}
                  {{config.table.column.dataType.labels[predictor.dataType]}})
                </dd>
                <dt>Response:</dt>
                <dd *ngVar="model.responseColumn as responseColumn">
                  {{responseColumn.displayName || responseColumn.name}}
                  ({{config.table.column.variableType.labels[responseColumn.variableType]}}
                  {{config.table.column.dataType.labels[responseColumn.dataType]}})
                </dd>
                <ng-template *ngIf="model.classes?.length > 0">
                  <dt>Classes:</dt>
                  <dd *ngFor="let className of model.classes">
                    {{className}}
                  </dd>
                </ng-template>
              </dl>
            </div>
          </div>
        </div>
      </div>
    </ng-template>
    <save-to-library-modal #saveToLibraryModal [service]="models"></save-to-library-modal>
  `,
})
export class ModelViewEmbedComponent implements OnInit, OnDestroy {
  @Input() modelId: TObjectId;
  @HostBinding('style.position') position = 'relative';
  @HostBinding('style.min-height') styleHeight = '100px';
  @HostBinding('class') classes = 'flex-col';
  readonly config = config;
  readonly trainConfig = trainConfig;

  activeTab: number = 2;
  modelProgresses: any;
  tableProgresses: any;
  model: ITabularModel;

  @ViewChild('saveToLibraryModal') private saveToLibraryModal: SaveToLibraryModalComponent<ITabularModel>;

  private processSubscription: Subscription;
  private _loader: ReactiveLoader<ITabularModel, TObjectId>;

  constructor(
    private models: ModelService,
    private processes: ProcessService,
  ) {
    this._loader = new ReactiveLoader(modelId => {
      return this.models.get(modelId);
    });

    this._loader.subscribe((model: ITabularModel) => {
      this.setModel(model);
    });
  }

  ngOnInit() {
    this.activeTab = 2;
    this.modelProgresses = this.processes.data.targets[config.asset.aliasesPlural[config.asset.values.MODEL]];
    this.tableProgresses = this.processes.data.targets[config.asset.aliasesPlural[config.asset.values.TABLE]];
    this._loader.load(this.modelId);
  }

  setModel(model: ITabularModel) {
    this.model = model;
    switch (model.status) {
      case ITabularModel.Status.TRAINING:
      case ITabularModel.Status.PREDICTING:
        this.processSubscription && this.processSubscription.unsubscribe();
        this.processSubscription = this.processes.subscribeByTarget(model.id, IAsset.Type.MODEL, () => {
          this._loader.load(model.id);
        });
        break;
    }
  }

  ngOnDestroy() {
    if (this.processSubscription) {
      this.processSubscription.unsubscribe();
    }
  }

  saveModel() {
    this.saveToLibraryModal.open(this.model);
  }
}

