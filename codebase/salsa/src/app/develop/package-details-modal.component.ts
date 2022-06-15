import { Component, ViewChild } from '@angular/core';

import config from '../config';
import { ModalComponent } from '../core-ui/components/modal.component';
import { ReactiveLoader } from '../utils/reactive-loader';

import { IPackage } from './package.interfaces';
import { PackageService } from './package.service';

@Component({
  selector: 'package-details-modal',
  template: `
    <app-modal
      #modal
      [captionPrefix]="'Details'"
      [caption]="s9ProjectPackage?.name"
      [sizeClass]="config.modal.size.LARGE"
      [buttons]="[{'class': 'btn-primary', 'title': 'Close'}]"
      (buttonClick)="modal.hide()"
      [limitedHeight]="true"
    >
      <app-spinner [visibility]="_itemLoader.active | async"></app-spinner>
      <ng-container *ngIf="!(_itemLoader.active | async) && s9ProjectPackage">
        <h4>Package Details</h4>
        <div class="container-fluid">
          <div class="row">
            <div class="col col-md-2"><strong>Package Name:</strong></div>
            <div class="col col-md-10">{{s9ProjectPackage.name}}</div>
          </div>

          <ng-container *ngIf="s9ProjectPackage?.description">
            <div class="row">
              <div class="col col-md-2"><strong>Description:</strong></div>
              <div class="col col-md-10" style="word-break: break-word">{{s9ProjectPackage.description}}</div>
            </div>
            <hr>
          </ng-container>
        </div>

        <h4>Pipeline Operators</h4>
        <ng-template #noPipelineOperators>
          <p>No pipeline operators defined in this package.</p>
        </ng-template>
        <ng-container *ngIf="s9ProjectPackage?.pipelineOperators; else noPipelineOperators">
          <package-operators-list
            [items]="s9ProjectPackage.pipelineOperators"
          ></package-operators-list>
        </ng-container>

        <h4>Primitives</h4>
        <ng-template #noPrimitives>
          <p>No primitives defined in this package.</p>
        </ng-template>
        <ng-container *ngIf="s9ProjectPackage?.primitives; else noPrimitives">
          <package-primitives-list
            [items]="s9ProjectPackage.primitives"
          ></package-primitives-list>
        </ng-container>
      </ng-container>
    </app-modal>
  `,
})
export class PackageDetailsModalComponent {
  s9ProjectPackage: IPackage;
  config = config;
  readonly _itemLoader: ReactiveLoader<IPackage, any>;

  @ViewChild('modal') private modal: ModalComponent;

  constructor(
    private packageService: PackageService,
  ) {
    this._itemLoader = new ReactiveLoader((selectedId: string) => this.packageService.get(selectedId));
    this._itemLoader.subscribe((projectPackage: IPackage) => this.s9ProjectPackage = projectPackage);
  }

  open(selectedId: string): void {
    this._itemLoader.load(selectedId);
    this.modal.show();
  }
}
