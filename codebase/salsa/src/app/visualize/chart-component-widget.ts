import { ComponentRef } from '@angular/core';

import { Message } from '@phosphor/messaging/lib';
import { ReplaySubject } from 'rxjs/ReplaySubject';

import { SplitDockTab } from '../../split-dock/split-dock-tab';
import { EventService, IEvent } from '../core/services/event.service';

import { ChartAbstract } from './chart.abstract';
import { DashboardCharts } from './charts/chart.interfaces';

export class ChartComponentWidget extends SplitDockTab.IWidget {
  readonly icons: SplitDockTab.ITabIcon[] = [
    {
      selector: 'p-SplitDockTab-tabEditIcon',
      tooltip: 'Edit',
      processMessage: () => {
        this.onEditRequest();
      },
    },
    {
      selector: 'p-SplitDockTab-tabFilterIcon',
      tooltip: 'Toggle Filter Area',
      processMessage: () => {
        this.componentReference.instance.toggleFilters();
      },
    },
    {
      selector: 'p-SplitDockTab-tabRefreshIcon',
      tooltip: 'Refresh',
      processMessage: () => {
        this.update();
      },
    },
    {
      selector: 'p-SplitDockTab-tabExportIcon',
      tooltip: 'Download',
      processMessage: () => {
        this.componentReference.instance.download();
      },
    },
  ];

  private _refreshSubject: ReplaySubject<boolean>;

  constructor(
    protected events: EventService,
    public componentReference: ComponentRef<ChartAbstract<DashboardCharts.IChartOptions>>,
  ) {
    super();
    this._refreshSubject = new ReplaySubject<boolean>(1);
    this._refreshSubject.asObservable().debounceTime(100).subscribe(() => {
      this.componentReference.instance.refresh();
    });
    this.node.appendChild(this.componentReference.location.nativeElement);
  }

  get instance(): ChartAbstract<DashboardCharts.IChartOptions> {
    return this.componentReference.instance;
  }

  onAfterAttach(msg: Message): void {
    this.events.emit(IEvent.Type.CHART_ADDED, this);
  }

  onCloseRequest(msg: Message): void {
    this.componentReference.destroy();
    this.dispose();
    this._refreshSubject.unsubscribe();
    // @TODO remove widget IDashboardWidget from dashboard.widgets array
    this.events.emit(IEvent.Type.CHART_REMOVED, this);
  }

  onUpdateRequest(): void {
    this._refreshSubject.next(true);
  }

  onResize(): void {
    this._refreshSubject.next(true);
  }

  onActivateRequest(): void {
    this.events.emit(IEvent.Type.CHART_SELECTED, this);
  }

  onEditRequest(): void {
    this.events.emit(IEvent.Type.CHART_EDIT, this.componentReference.instance.config.guid);
  }
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CHART_ADDED = 'CHART_ADDED',
      CHART_REMOVED = 'CHART_REMOVED',
      CHART_SELECTED = 'CHART_SELECTED',
      CHART_EDIT = 'CHART_EDIT',
    }
  }
}
