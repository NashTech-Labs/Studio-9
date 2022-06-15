import {
  AfterViewInit,
  Component,
  ComponentFactoryResolver,
  ComponentRef,
  ElementRef,
  EventEmitter,
  HostBinding,
  Input,
  NgZone,
  OnDestroy,
  OnInit,
  Output,
  ViewChild,
  ViewContainerRef,
} from '@angular/core';

import { toArray } from '@phosphor/algorithm/lib';
import { Widget } from '@phosphor/widgets/lib/widget';

import { SplitDockLayout } from '../../split-dock/split-dock-layout';
import { SplitDockPanel } from '../../split-dock/split-dock-panel';
import { EventService } from '../core/services/event.service';
import { MiscUtils } from '../utils/misc';

import { ChartComponentWidget } from './chart-component-widget';
import { ChartFactory } from './chart.factory';
import { CrossFilterBus } from './cross-filter-bus';
import {
  IDashboard,
  IDashboardWidget,
  IDashboardXFilter,
  ILayout,
  ILayoutWidget,
} from './dashboard.interface';

@Component({
  selector: 'charts-dock-panel',
  template: '<div #anchor></div>',
})
export class ChartsDockComponent implements OnInit, OnDestroy, AfterViewInit {
  @Input() editMode: boolean = false;
  @Output() onWidgetsLoaded = new EventEmitter<ChartComponentWidget[]>();
  @Output() onLayoutModified = new EventEmitter<SplitDockLayout.ILayoutConfig>();
  @HostBinding('class') classes = 'flex-col';
  @ViewChild('anchor', {read: ViewContainerRef}) readonly viewContainer: ViewContainerRef;

  dock: SplitDockPanel;
  constructor(
    readonly componentFactoryResolver: ComponentFactoryResolver,
    readonly crossFilterBus: CrossFilterBus,
    private zone: NgZone,
    private events: EventService,
    private el: ElementRef,
  ) {
    this.zone.runOutsideAngular(() => {
      this.dock = new SplitDockPanel({});
      this.dock.addClass('flex-rubber');
      this.dock.layoutModified.connect(() => {
        this.onLayoutModified.emit(this.saveLayout());
      }, this);
    });
  }

  @Input() set crossFilters(value: IDashboardXFilter[]) {
    this.crossFilterBus.crossFilters = value || [];
  }

  @Input() set dashboard(value: IDashboard) {
    if (value) {
      this.crossFilterBus.crossFilters = value.crossFilters;
      this.dock.restoreLayout(<SplitDockLayout.ILayoutConfig> {
        main: this.loadLayout(value.layout, value.widgets),
      });
      this.onWidgetsLoaded.emit(<ChartComponentWidget[]> toArray(this.dock.widgets()));
    }
  }

  createContent(componentReference: ComponentRef<any>, title?: string): ChartComponentWidget {
    let widget = new ChartComponentWidget(this.events, componentReference);
    widget.title.label = title;
    widget.title.closable = this.editMode;
    return widget;
  }

  ngAfterViewInit(): void {
    this.zone.runOutsideAngular(() => {
      try {
        Widget.attach(this.dock, this.el.nativeElement);
      } catch (e) {
        console.log(e); // @TODO DO SOMETHING
      }
    });
  }

  ngOnInit(): void {
    this.zone.runOutsideAngular(() => {
      window.addEventListener('resize', this._onWindowResize);
    });
  }

  ngOnDestroy() {
    this.zone.runOutsideAngular(() => {
      window.removeEventListener('resize', this._onWindowResize);
    });
  }

  loadLayout(layout: ILayout | ILayoutWidget, widgets: IDashboardWidget[]): any {
    if (!layout) {
      return null;
    }
    if (layout.type === 'split-area') {
      return {
        type: layout.type,
        sizes: (<ILayout> layout).sizes,
        orientation: (<ILayout> layout).orientation,
        children: layout['children'].map(item => {
          return this.loadLayout(item, widgets);
        }),
      };
    }
    if (layout.type === 'tab-area') {
      return {
        type: layout.type,
        widget: this.getWidget(widgets[(<ILayoutWidget> layout).widget]),
      };
    }
  }

  getWidget(data: IDashboardWidget): Widget {
    // Trying to find existing widget in layout
    const oldWidget = <ChartComponentWidget> toArray(this.dock.widgets()).find((widget: ChartComponentWidget) => {
      return data.guid === widget.componentReference.instance.config.guid;
    });
    if (oldWidget) {
      oldWidget.instance.config = data;
      return oldWidget;
    }
    // If no widget foung in layout - create one
    let compRef = ChartFactory.createComponentInstance(
      this.viewContainer,
      this.componentFactoryResolver,
      data,
    );
    return this.createContent(compRef, data.name);
  }

  createWidget(data: IDashboardWidget): ChartComponentWidget {
    if (!data.guid) {
      data.guid = MiscUtils.generateUUID();
    }
    let compRef = ChartFactory.createComponentInstance(
      this.viewContainer,
      this.componentFactoryResolver,
      data,
    );
    return this.createContent(compRef, data.name);
  }

  addComponent(data: IDashboardWidget, mode: SplitDockLayout.InsertMode = 'split-top'): ChartComponentWidget {
    const widget = this.createWidget(data);
    this.dock.addWidget(widget, { mode });
    return widget;
  }

  addComponentDetached(data: IDashboardWidget, event: MouseEvent): ChartComponentWidget {
    const widget = this.createWidget(data);
    const { clientX, clientY } = event;
    this.dock.addWidgetDetached(widget, clientX, clientY);
    return widget;
  }

  saveLayout(): SplitDockLayout.ILayoutConfig {
    return this.dock.saveLayout();
  }

  private _onWindowResize = () => {
    this.zone.run(() => {
      this.dock && this.dock.update();
    });
  };
}
