import { Component, ComponentFactoryResolver, ViewContainerRef } from '@angular/core';

import config from '../config';
import { Feature } from '../core/interfaces/feature-toggle.interface';
import { IEvent } from '../core/services/event.service';

import { CVModelUploadModalComponent } from './cv-model-upload-modal.component';
import { ModelService } from './model.service';
import { trainConfig } from './train.config';

@Component({
  selector: 'app-train-context',

  template: `
    <div class="group">
      <button type="button"
        *featureToggle="'${Feature.TRAIN_MODELS}'"
        class="btn btn-primary btn-block"
        [routerLink]="['/desk', 'train', 'create']"
        routerLinkActive #trainCreateActive="routerLinkActive"
        [ngClass]="{'btn-alt': !trainCreateActive.isActive}"
      >
        Train Tabular Model
      </button>
      <button
        type="button"
        *featureToggle="'${Feature.TRAIN_CV_MODELS}'"
        class="btn btn-primary btn-block btn-alt"
        (click)="importCVModel()"
      >
        Import CV model
      </button>
    </div>

    <core-project-context></core-project-context>

    <side-asset-list
      *featureToggle="'${Feature.TRAIN_MODELS}'"
      [caption]="'Models'"
      icon="iconapp iconapp-models"
      [statusesDefinition]="config.model.status"
      [baseRoute]="['/desk', 'train']"
      [service]="modelService"
      [reloadOn]="['${IEvent.Type.UPDATE_MODEL_LIST}', '${IEvent.Type.UPDATE_EXPERIMENT_LIST}']"
      [actions]="{'retrain': 'Retrain'}"
    ></side-asset-list>
  `,
})
export class TrainContextComponent {
  readonly config = config;
  readonly trainConfig = trainConfig;

  constructor(
    readonly modelService: ModelService,
    private viewContainer: ViewContainerRef,
    private componentFactoryResolver: ComponentFactoryResolver,
  ) {
  }

  importCVModel() {
    const factory = this.componentFactoryResolver.resolveComponentFactory(CVModelUploadModalComponent);
    const modalRef = this.viewContainer.createComponent(factory);
    modalRef.instance.open().subscribe(() => modalRef.destroy());
  }
}
