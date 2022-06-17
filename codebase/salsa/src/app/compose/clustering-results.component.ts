import { Component, HostBinding, Input, OnChanges, OnDestroy } from '@angular/core';

import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { IAsset, TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { ProcessService } from '../core/services/process.service';
import { TableService } from '../tables/table.service';

import { IClusteringResult, IFlow, IFlowstep } from './flow.interface';
import { FlowstepService } from './flowstep.service';

@Component({
  selector: 'clustering-results',
  template: `
    <app-spinner [visibility]="!clusteringResults"></app-spinner>
    <canvas *ngIf="clusteringResults && clusteringResults.centers.length"
      cluster-graph
      [axisLabels]="flowstep.options['columns']"
      [cluster-data]="clusteringResults"></canvas>
    <error-indicator *ngIf="clusteringResults && !clusteringResults.centers.length" [caption]="'Not Supported'"
      [message]="'This clustering can not be visualized in two-dimensional space or exceeds points number limitations'"
      ></error-indicator>`,
})
export class ClusteringResultsComponent implements OnChanges, OnDestroy {
  @HostBinding('class') classes = 'flex-col';
  @HostBinding('style.height') styleHeight = '100%';
  @HostBinding('style.overflow') overflow = 'hidden';
  @Input() flow: IFlow;
  @Input() flowstepId: TObjectId;
  flowstep: IFlowstep;
  readonly config = config;
  clusteringResults: IClusteringResult = null;
  private processSubscription: Subscription;

  constructor(private flowsteps: FlowstepService, private tables: TableService, private processes: ProcessService) {
  }

  ngOnChanges() {
    this.clusteringResults = null;
    if (this.flowstepId && this.flow) {
      this.flowstep = this.flow.steps.find(step => step.id === this.flowstepId);
      //Validate Dimentions Count
      if ((<IFlowstep.ClusterTransformer> this.flowstep.options).columns.length > config.clusteringResults.maxDimensions) {
        this.clusteringResults = { centers: [], points: [] };
        return;
      }
      this.tables.get(this.flowstep.output).subscribe(table => {
        if (table.status === this.config.table.status.values.ERROR) {
          this.clusteringResults = { centers: [], points: [] };
        }
        if (table.status === this.config.table.status.values.SAVING) {
          this.processSubscription && this.processSubscription.unsubscribe();
          this.processSubscription = this.processes.subscribeByTarget(table.id, IAsset.Type.TABLE, (process: IProcess) => {
            if (process.status === config.process.status.values.COMPLETED) {
              this.loadClusteringResults();
            } else {
              this.ngOnChanges();
            }
          });
        }
        if (table.status === this.config.table.status.values.ACTIVE) {
          this.loadClusteringResults();
        }
      });
    }
  }

  ngOnDestroy() {
    this.processSubscription && this.processSubscription.unsubscribe();
  }

  loadClusteringResults() {
    this.flowsteps.clusteringResults(this.flow.id, this.flowstep.id).subscribe(clusteringResults => {
      // Currently validates on Backend Side
      if (ClusteringResultsComponent.validateClusteringResults(clusteringResults)) {
        this.clusteringResults = clusteringResults;
      } else {
        this.clusteringResults = { centers: [], points: [] };
      }
    });
  }

  static validateClusteringResults(clusteringResults: IClusteringResult): boolean {
    return clusteringResults &&
      clusteringResults.centers[0].length - 1 <= config.clusteringResults.maxDimensions &&
      clusteringResults.points.length <= config.clusteringResults.maxPoints;
  }
}
