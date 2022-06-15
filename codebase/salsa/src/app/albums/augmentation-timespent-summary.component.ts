import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

import { IAugmentationTimeSpentSummary } from './album.interface';

@Component({
  selector: 'albums-augmentation-time-spent-summary',
  template: `
    <div class="panel">
      <div class="panel-body">
        <div class="row">
          <div class="col-md-12">
            <h3>Augmentation Time Details</h3>
            <dl *ngIf="augmentationSummary">
              <dt>Tasks Queued Time</dt>
              <dd>{{augmentationSummary.tasksQueuedTime | secondsToTime}}</dd>

              <dt>Total Job Time</dt>
              <dd>{{augmentationSummary.totalJobTime | secondsToTime}}</dd>

              <dt>Data Loading Time</dt>
              <dd>{{augmentationSummary.dataLoadingTime | secondsToTime}}</dd>

              <dt>Augmentation Time</dt>
              <dd>{{augmentationSummary.augmentationTime | secondsToTime}}</dd>
            </dl>
            <span *ngIf="!augmentationSummary">No augmentation summary</span>
          </div>
        </div>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AlbumsAugmentationTimeSpentSummaryComponent {
  @Input() augmentationSummary: IAugmentationTimeSpentSummary;
}
