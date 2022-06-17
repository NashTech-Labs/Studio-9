import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  HostBinding,
  Input,
  OnChanges,
  OnDestroy,
  SimpleChanges,
} from '@angular/core';

import * as _ from 'lodash';
import { Subscription } from 'rxjs/Subscription';

import { IAlbum, IAlbumTagsSummary } from '../albums/album.interface';
import { AlbumService } from '../albums/album.service';
import { TObjectId } from '../core/interfaces/common.interface';
import { ReactiveLoader } from '../utils/reactive-loader';

import { IConfusionMatrix } from './train.interface';

interface ConfusionMatrixSummaryRow {
  label: string;
  predicted: number[];
  truePositive: number;
  trueNegative: number;
  falsePositive: number;
  falseNegative: number;
  notDetected: number;
  falseDetected: number;
}

@Component({
  selector: 'cv-confusion-matrix',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-spinner [visibility]="_actualStatsLoader.active | async"></app-spinner>

    <div class="table-scroll" *ngIf="_actualStatsLoader.loaded">
      <table class="table table-bordered table-condensed"
        [capture]="confusionMatrix | apply: _getLabelsSummary: labels"
        #matrix="capture"
      >
        <thead>
        <tr>
          <th rowspan="2">Label</th>
          <th rowspan="2">Actual count</th>
          <th rowspan="2">Predicted count</th>
          <th attr.colspan="{{showMatrix ? (labelMode == '${IAlbum.LabelMode.LOCALIZATION}' ? labels.length + 1 : labels.length) : 1}}"
            style="cursor: pointer"
            (click)="showMatrix = !showMatrix"
          >
            Predicted
            <i class="pull-right glyphicon"
              [ngClass]="showMatrix ? 'glyphicon-eye-open' : 'glyphicon-eye-close'"
            ></i>
          </th>
          <th rowspan="2">Precision</th>
          <th rowspan="2">Recall</th>
          <th rowspan="2">F1 Score</th>
          <th rowspan="2">Accuracy</th>
        </tr>
        <tr>
          <ng-template [ngIf]="showMatrix">
            <th *ngFor="let predictedLabel of labels">{{predictedLabel}}</th>
            <th *ngIf="labelMode == '${IAlbum.LabelMode.LOCALIZATION}'">-Not detected-</th>
          </ng-template>
          <th *ngIf="!showMatrix">...</th>
        </tr>
        </thead>
        <tbody>
        <tr *ngFor="let row of matrix.value">
          <td>{{row.label}}</td>
          <td>{{actualAlbumId ? (_actualCount[row.label] || 0) : (row.truePositive + row.falseNegative)}}</td>
          <td>{{row.truePositive + row.falsePositive}}</td>
          <ng-template [ngIf]="showMatrix">
            <td *ngFor="let count of row.predicted">{{count}}</td>
            <td *ngIf="labelMode == '${IAlbum.LabelMode.LOCALIZATION}'">{{row.notDetected}}</td>
          </ng-template>
          <td *ngIf="!showMatrix">...</td>
          <td>{{row | apply: _precisionMetric | number: '1.3-3'}}</td>
          <td>{{row | apply: _recallMetric | number: '1.3-3'}}</td>
          <td>{{row | apply: _F1Metric | number: '1.3-3'}}</td>
          <td>{{row | apply: _accuracyMetric | percent: '1.2-2'}}</td>
        </tr>
        <tr *ngIf="showMatrix && labelMode == '${IAlbum.LabelMode.LOCALIZATION}'">
          <td title="Not a target">NAT</td>
          <td colspan="2"></td>
          <td *ngFor="let row of matrix.value">{{row.falseDetected}}</td>
          <td>--</td>
          <td colspan="4"></td>
        </tr>
        </tbody>
      </table>
    </div>
  `,
})
export class ConfusionMatrixComponent implements OnDestroy, OnChanges {
  @HostBinding('class') _cssClass = 'app-spinner-box';

  @Input() confusionMatrix: IConfusionMatrix;
  @Input() labels: string[];
  @Input() labelMode: IAlbum.LabelMode;
  @Input() actualAlbumId: TObjectId;

  showMatrix: boolean = true;

  protected _actualStatsLoader: ReactiveLoader<IAlbumTagsSummary, TObjectId>;
  protected _actualCount: {[label: string]: number} = {};
  private _loaderSubscription: Subscription;

  constructor(
    _albumService: AlbumService,
    _cdr: ChangeDetectorRef,
  ) {
    this._actualStatsLoader = new ReactiveLoader(albumId => _albumService.getTags(albumId), true);

    this._loaderSubscription = this._actualStatsLoader.subscribe(labelStats => {
      this._actualCount = labelStats.reduce((acc, {label, count}) => {
        if (label) {
          acc[label] = count;
        }
        return acc;
      }, {});

      _cdr.markForCheck();
    });
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ('actualAlbumId' in changes) {
      //this._actualCount = {};
      if (this.actualAlbumId) {
        this._actualStatsLoader.load(this.actualAlbumId);
      }
    }
  }

  ngOnDestroy(): void {
    this._loaderSubscription.unsubscribe();
  }

  _getLabelsSummary = (confusionMatrix: IConfusionMatrix, labels: string[]): ConfusionMatrixSummaryRow[] => {
    const rows: ConfusionMatrixSummaryRow[] = labels.map(label => ({
      label,
      predicted: labels.map(() => 0),
      truePositive: 0,
      trueNegative: 0,
      falsePositive: 0,
      falseNegative: 0,
      notDetected: 0,
      falseDetected: 0,
    }));

    if (confusionMatrix) {
      const totalActual = _.chain(confusionMatrix)
        .filter(_ => typeof _.actual === 'number')
        .map(_ => _.count)
        .sum()
        .value();

      confusionMatrix.forEach(({predicted, actual, count}) => {
        if (typeof actual === 'number' && typeof predicted === 'number') {
          rows[actual].predicted[predicted] += count;
          if (predicted === actual) {
            rows[predicted].truePositive += count;
          } else  {
            rows[predicted].falsePositive += count;
            rows[actual].falseNegative += count;
          }
        } else if (typeof actual === 'number') {
          rows[actual].falseNegative += count;
          rows[actual].notDetected += count;
        } else if (typeof predicted === 'number') {
          rows[predicted].falsePositive += count;
          rows[predicted].falseDetected += count;
        }
      });

      rows.forEach(row => {
        row.trueNegative = Math.max(totalActual - row.truePositive - row.falsePositive - row.falseNegative, 0);
      });
    }

    return rows;
  };

  _F1Metric = (row: ConfusionMatrixSummaryRow): number => {
    const TPx2 = row.truePositive * 2;
    return TPx2 > 0
      ? TPx2 / (TPx2 + row.falsePositive + row.falseNegative)
      : 0;
  };

  _precisionMetric = (row: ConfusionMatrixSummaryRow): number => {
    return row.truePositive > 0
      ? row.truePositive / (row.truePositive + row.falsePositive)
      : 0;
  };

  _recallMetric = (row: ConfusionMatrixSummaryRow): number => {
    return row.truePositive > 0
      ? row.truePositive / (row.truePositive + row.falseNegative)
      : 0;
  };

  _accuracyMetric = (row: ConfusionMatrixSummaryRow): number => {
    return (row.truePositive + row.trueNegative) > 0
      ? (row.truePositive + row.trueNegative) / (row.truePositive + row.falsePositive + row.trueNegative + row.falseNegative)
      : 0;
  };
}
