import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

import { IAlbum } from '../albums/album.interface';
import { TObjectId } from '../core/interfaces/common.interface';

import { CVModelType, ICVModelSummary } from './cv-model.interface';
import { getResultLabelModeByModelType } from './train.helpers';

@Component({
  selector: 'cv-model-summary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div *ngIf="!summary" class="text-center">
      Model has no summary
    </div>
    <dl *ngIf="summary" class="dl-horizontal train-dl-summary">
      <ng-template [ngIf]="summary.reconstructionLoss">
        <dt>Reconstruction loss</dt>
        <dd>{{summary.reconstructionLoss | number: '1.0-3'}}</dd>
      </ng-template>
      <ng-template [ngIf]="summary.mAP | apply: _isNumber">
        <dt>mAP</dt>
        <dd>{{summary.mAP | percent: '1.0-2'}}</dd>
      </ng-template>
      <ng-template [ngIf]="summary.confusionMatrix">
        <dt>Labels</dt>
        <dd>
          <cv-confusion-matrix
            [confusionMatrix]="summary.confusionMatrix"
            [actualAlbumId]="actualAlbumId"
            [labels]="summary.labels"
            [labelMode]="modelType | apply: getLabelMode"
          ></cv-confusion-matrix>
        </dd>
      </ng-template>
    </dl>
  `,
})

export class CVModelSummaryComponent {
  @Input() summary: ICVModelSummary;
  @Input() modelType: CVModelType.Type;
  @Input() labelMode: IAlbum.LabelMode;
  @Input() actualAlbumId: TObjectId;

  readonly _isNumber = function(x: number): boolean {
    return typeof x === 'number';
  };

  getLabelMode = function(modelType): IAlbum.LabelMode {
    return getResultLabelModeByModelType(modelType);
  };
}
