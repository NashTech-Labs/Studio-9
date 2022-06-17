import { Component, ViewChild } from '@angular/core';

import { Observable } from 'rxjs/Observable';

import config from '../config';
import { LibrarySelectorValue } from '../core/components/library-selector.component';
import { ActivityObserver } from '../utils/activity-observer';

export interface IPlayChildComponent {
  submit(): Observable<any>;
  clear?(): void;
}

@Component({
  selector: 'app-play-create',
  template: `
    <asset-operations [type]="selectedAsset?.entity" [selectedItems]="[]"></asset-operations>
    <div class="row">
      <div class="col-md-6">
        <library-selector
          [inputLabel]="'Play Asset'"
          [available]="[config.asset.values.FLOW, config.asset.values.MODEL, config.asset.values.CV_MODEL]"
          [(value)]="selectedAsset"
          [caption]="'Select Asset'"></library-selector>
      </div>
      <div class="col-md-6">
        <div class="pull-right">
          <button class="btn btn-md btn-clear"
            (click)="childComponent?.clear && childComponent.clear(); clear();">
            Clear
          </button>
          <button class="btn btn-md btn-apply" (click)="_savingObserver.observe(childComponent.submit())"
              [disabled]="!validity || (_savingObserver.active | async)">
              Play
          </button>
        </div>
      </div>
    </div>
    <app-play-flow #childComponent *ngIf="selectedAsset?.entity === config.asset.values.FLOW"
      [flow]="selectedAsset.object" (changeValidity)="validity = $event"></app-play-flow>
    <app-play-model #childComponent *ngIf="selectedAsset?.entity === config.asset.values.MODEL"
      [model]="selectedAsset.object" (changeValidity)="validity = $event"></app-play-model>
    <app-play-cv-model #childComponent *ngIf="selectedAsset?.entity === config.asset.values.CV_MODEL"
      [model]="selectedAsset.object" (changeValidity)="validity = $event"></app-play-cv-model>
  `,
})
export class PlayCreateComponent {
  readonly config = config;
  selectedAsset: LibrarySelectorValue;
  validity: boolean = false;
  readonly _savingObserver = new ActivityObserver();
  @ViewChild('childComponent') childComponent: Component & { submit(); clear?(); };

  clear() {
    this.selectedAsset = null;
  }
}
