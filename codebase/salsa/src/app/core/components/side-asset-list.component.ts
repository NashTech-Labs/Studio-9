import { Component, HostBinding, Input, OnDestroy, OnInit, Optional } from '@angular/core';
import { FormControl } from '@angular/forms';

import 'rxjs/add/operator/debounceTime';
import { merge as mergeObservables } from 'rxjs/observable/merge';
import { ISubscription, Subscription } from 'rxjs/Subscription';

import { ModalService } from '../../core-ui/services/modal.service';
import { ProjectContext } from '../../library/project.context';
import { ReactiveLoader } from '../../utils/reactive-loader';
import { IAsset, IAssetListRequest, IAssetService, IBackendList, TObjectId } from '../interfaces/common.interface';
import { IProcess } from '../interfaces/process.interface';
import { EventService, IEvent } from '../services/event.service';
import { ProcessService } from '../services/process.service';

@Component({
  selector: 'side-asset-list',
  template: `
    <ul class="nav nav-stacked" *ngIf="!hideIfEmpty || !!_items.length">
      <li class="has-submenu" [ngClass]="{'open app-spinner-box': isOpen}">
        <app-spinner [visibility]="_loader.active | async"></app-spinner>
        <a (click)="isOpen = !isOpen">
          <i class="{{icon}}"></i>
          <span>{{caption}}</span>
        </a>
        <ul class="nav nav-pills submenu with-dropdown">
          <li>
            <app-input [control]="searchControl"
              [iconBefore]="'glyphicon-search'"
              [iconAfter]="'glyphicon-remove'"
              (iconAfterClick)="searchControl.setValue('')"
            ></app-input>
          </li>
          <li *ngFor="let item of _items" [routerLinkActive]="['active']">
            <span class="dropdown pull-right" dropdown>
              <a data-toggle="dropdown" aria-haspopup="true" aria-expanded="true">
                <i class="glyphicon glyphicon-option-horizontal"></i>
              </a>
              <ul class="dropdown-menu">
                <li [routerLinkActive]="['active']" [routerLinkActiveOptions]="{exact: true}">
                  <a class="dropdown-item link"
                    [routerLink]="item | apply: _prepareLink: baseRoute"
                  >Preview</a>
                </li>
                <li *ngFor="let action of actions | keys"
                  [routerLinkActive]="['active']"
                  [routerLinkActiveOptions]="{exact: true}"
                >
                  <a class="dropdown-item link"
                    [routerLink]="item | apply: _prepareLink: baseRoute: action"
                  >{{actions[action]}}</a>
                </li>
                <li role="separator" class="divider"></li>
                <li><a (click)="dropItem(item)" class="dropdown-item link">Trash</a></li>
              </ul>
            </span>
            <a [routerLink]="item | apply: _prepareLink: baseRoute"
              [title]="item.name"
              [asset-status]="item.status"
              [asset-status-styles]="statusesDefinition.styles"
              tooltip
              data-toggle="tooltip"
              [attr.data-original-title]="statusesDefinition.labels[item.status]"
            >
              {{item.name}}
              <span *ngIf="_processes[item.id] && _processes[item.id].status === '${IProcess.Status.RUNNING}'">
                {{(_processes[item.id].progress * 100).toFixed(0)}}%
              </span>
            </a>
          </li>
          <li *ngIf="!_items.length"><a>No Items</a></li>
        </ul>
      </li>
    </ul>
  `,
})
export class SideAssetListComponent<T extends IAsset & {status: string}> implements OnInit, OnDestroy {
  @HostBinding('class') readonly cssClass = 'menu';
  @Input() icon: string = 'iconapp iconapp-models';
  @Input() caption: string = 'Items :P';
  @Input() service: IAssetService<T, any>;
  @Input() reloadOn: IEvent.Type[] = [];
  @Input() baseRoute: string[];
  @Input() statusesDefinition: IAsset.StatusesDescription = {
    labels: {},
    styles: {},
  };
  @Input() actions: {[action: string]: string} = {}; // action -> label
  @Input() hideIfEmpty: boolean = false;
  @Input() isOpen: boolean = true;

  _items: T[] = [];
  readonly searchControl = new FormControl();
  readonly _processes: {[itemId: string]: IProcess} = {};
  readonly _loader: ReactiveLoader<IBackendList<T>, any>;

  private eventsSubscription: ISubscription;
  private processesSubscription: Subscription;
  private projectSubscription: Subscription;
  private finishedProcesses: TObjectId[] = [];

  constructor(
    private processService: ProcessService,
    private eventService: EventService,
    private modalService: ModalService,
    @Optional() private projectContext: ProjectContext,
  ) {
    this._loader = new ReactiveLoader(() => {
      const listRequest: IAssetListRequest = {
        search: this.searchControl.value,
        order: '-created',
        page_size: 1000,
      };
      if (this.projectContext) {
        const [projectId, folderId] = this.projectContext.get();
        listRequest.projectId = projectId;
        listRequest.folderId = folderId;
      }

      return this.service.list(listRequest);
    });

    this._loader.subscribe((data: IBackendList<T>) => {
      this._items = data.data;
      this.processesSubscription && this.processesSubscription.unsubscribe();
      this.processesSubscription = mergeObservables(...this._items.map((item) => {
        return this.service.getActiveProcess(item)
          .do(process => {
            // processService will update this process object status, see ProcessService.observe
            this._processes[item.id] = process;
          })
          .filter(_ => !!_ && !this.finishedProcesses.includes(_.id))
          .flatMap(process => {
            return this.processService.observe(process);
          })
          .do(_ => this.finishedProcesses.push(_.id));
      })).debounceTime(100).subscribe(() => {
        this._loader.load();
      });
    });

    this.searchControl.valueChanges.debounceTime(500).subscribe(() => {
      this._loader.load();
    });
  }

  ngOnInit() {
    this._loader.load();
    this.eventsSubscription = this.eventService.subscribe((event: IEvent) => {
      if (this.reloadOn.includes(event.type)) {
        this._loader.load();
      }
    });

    if (this.projectContext) {
      this.projectSubscription = this.projectContext.value.subscribe(() => {
        this._loader.load();
      });
    }
  }

  ngOnDestroy() {
    this.processesSubscription && this.processesSubscription.unsubscribe();
    this.eventsSubscription && this.eventsSubscription.unsubscribe();
    this.projectSubscription && this.projectSubscription.unsubscribe();
  }

  dropItem(item: T) {
    const confirmationMessage = `Are you sure you want to delete ${item.name}?`;

    this.modalService.confirm(confirmationMessage).filter(_ => _).subscribe(() => {
      this.service.delete(item);
    });
  }

  _prepareLink = function(item: T, baseRoute: string[], action?: string) {
    const link = [...baseRoute, item.id];

    if (action) {
      link.push(action);
    }

    return link;
  };
}
