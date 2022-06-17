import { Component, Input, OnChanges, OnDestroy, SimpleChanges } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { TObjectId } from '../core/interfaces/common.interface';
import { EventService } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { ReactiveLoader } from '../utils/reactive-loader';

import {
  IS9Project,
  IS9ProjectSession,
  IS9ProjectSessionCreate,
} from './s9-project.interfaces';
import { S9ProjectService } from './s9-project.service';

interface IProjectViewToolbarButton {
  name: string;
  title?: string;
  iconClass?: string;
  isVisible?: () => boolean;
  onClick?: () => void;
  isDisabled?: () => boolean;
  subActions?: IProjectViewToolbarButton[];
}

@Component({
  selector: 's9-project-session',
  template: `
    <app-spinner [visibility]="_sessionLoader.active | async"></app-spinner>

    <div class="row bg-gray" style="margin-top: 10px">
      <div
        class="col-xs-12 col-md-4 col-md-offset-4"
        style="padding: 5px 15px;"
        *ngIf="this.s9Project && !(_sessionLoader.active | async)"
      >
        <label class="text-uppercase">
          <span>Interactive IDE: </span>
          <span
            [ngClass]="sessionStatus | apply: _getSessionStatusClass"
            class="label"
          >{{sessionStatus | apply: _getSessionStatus: s9Project}}</span>
        </label>

        <span class="btn-group">
          <ng-container *ngFor="let button of buttons">
            <button
              *ngIf="!button.isVisible || button.isVisible()"
              class="btn btn-default btn-sm"
              [ngClass]="{
                'dropdown-toggle': !!button.subActions
              }"
              [title]="button.title"
              placement="bottom"
              (click)="button.onClick && button.onClick()"
              [attr.data-toggle]="button.subActions ? 'dropdown' : null"
              [disabled]="button.isDisabled && button.isDisabled()"
            >
              <i *ngIf="button.iconClass" [class]="button.iconClass"></i>
              {{button.name}}
              <span *ngIf="button.subActions" class="caret"></span>
            </button>
            <ul
              *ngIf="button.subActions"
              class="dropdown-menu"
              role="menu"
            >
              <li *ngFor="let action of button.subActions">
                <a
                  href="#"
                  (click)="$event.preventDefault(); action.onClick && action.onClick()"
                >
                  <i *ngIf="action.iconClass" [class]="action.iconClass"></i>
                  {{action.name}}
                </a>
              </li>
            </ul>
          </ng-container>
        </span>
      </div>
    </div>
  `,
})
export class S9ProjectSessionComponent implements OnDestroy, OnChanges {
  readonly config = config;

  @Input() s9Project: IS9Project = null;
  session: IS9ProjectSession = null;
  sessionStatus: IS9ProjectSession.Status = null;

  readonly buttons: IProjectViewToolbarButton[] = [
    {
      name: 'Run',
      title: 'Run interactive session',
      iconClass: 'glyphicon glyphicon-play-circle',
      isVisible: () => {
        return this.s9Project.status === IS9Project.Status.IDLE;
      },
      subActions: [
        {
          name: 'Run with GPU',
          onClick: () => this.runSession({ useGPU: true }),
        },
        {
          name: 'Run without GPU',
          onClick: () => this.runSession({ useGPU: false }),
        },
      ],
    },
    {
      name: 'Shutdown',
      title: 'Shutdown interactive session',
      iconClass: 'glyphicon glyphicon-remove-circle',
      isVisible: () => {
        return this.s9Project.status === IS9Project.Status.INTERACTIVE
          && this.session
          && [
            IS9ProjectSession.Status.QUEUED,
            IS9ProjectSession.Status.RUNNING,
            IS9ProjectSession.Status.SUBMITTED,
          ].includes(this.sessionStatus);
      },
      onClick: () => this.shutdownSession(),
    },
    {
      name: 'Open',
      title: 'Open interactive session',
      iconClass: 'glyphicon glyphicon-share',
      isVisible: () => {
        return this.s9Project.status === IS9Project.Status.INTERACTIVE
          && !!this.session;
      },
      isDisabled: () => this.sessionStatus !== IS9ProjectSession.Status.RUNNING,
      onClick: () => this.openSessionTab(this.session),
    },
  ];

  protected readonly _sessionLoader: ReactiveLoader<IS9ProjectSession, TObjectId>;
  private _subscriptions: Subscription[] = [];
  private statusSubscription: Subscription = null;
  private _isSessionTabAutoOpenEnabled = false;

  private processSubscription: Subscription;

  constructor(
    private readonly _router: Router,
    private readonly _route: ActivatedRoute,
    private readonly _processService: ProcessService,
    private readonly _eventService: EventService,
    private readonly _s9ProjectService: S9ProjectService,
  ) {
    this._sessionLoader = new ReactiveLoader(projectId => this._s9ProjectService.getSession(projectId));
    this._subscriptions.push(this._sessionLoader.subscribe(session => {
      this.session = session;
      this._startStatusChangeTracking(this.s9Project);
    }));
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ('s9Project' in changes) {
      const previousId = changes['s9Project'].previousValue && changes['s9Project'].previousValue.id;
      const currentId = this.s9Project && this.s9Project.id;
      if (previousId !== currentId) {
        this.session = null;
        this.sessionStatus = null;
        this._isSessionTabAutoOpenEnabled = false;
        this._stopStatusChangeTracking();
        if (this.s9Project && this.s9Project.status === IS9Project.Status.INTERACTIVE) {
          this._sessionLoader.load(this.s9Project.id);
        }
      }
    }
  }

  ngOnDestroy(): void {
    this._stopStatusChangeTracking();
    this.processSubscription && this.processSubscription.unsubscribe();
    this._subscriptions.forEach((_) => _.unsubscribe());
  }

  runSession(options: IS9ProjectSessionCreate): void {
    const subscription = this._s9ProjectService.startSession(this.s9Project, options)
      .subscribe(() => {
        this._isSessionTabAutoOpenEnabled = true;
        this._sessionLoader.load(this.s9Project.id);
      });
    this._subscriptions.push(subscription);
  }

  shutdownSession(): void {
    this._stopStatusChangeTracking();
    this.session = null;
    this.sessionStatus = null;
    this._s9ProjectService.stopSession(this.s9Project);
  }

  openSessionTab(session: IS9ProjectSession): void {
    if (!session) {
      return;
    }

    const win = window.open();

    // security hack because passing 'noopener' to window.open leads to a new window opening instead of a new tab)
    if (win) {
      win.opener = null;
      win.location.href = this.session.url + `?token=${this.session.authToken}`;
    }
  }

  _getSessionStatus(sessionStatus: string, s9Project: IS9Project): string {
    return s9Project && s9Project.status === IS9Project.Status.INTERACTIVE
      ? config.s9Project.sessionStatus.labels[sessionStatus] || '...'
      : 'Not Running';
  }

  _getSessionStatusClass(sessionStatus: string) {
    return config.s9Project.sessionStatus.styles[sessionStatus] || 'label-default';
  }

  private _onStatusChange(status: IS9ProjectSession.Status): void {
    this.sessionStatus = status;

    if (
      status === IS9ProjectSession.Status.RUNNING
      && this.session
      && this._isSessionTabAutoOpenEnabled
    ) {
      this._isSessionTabAutoOpenEnabled = false;
      this.openSessionTab(this.session);
    }
  }

  private _startStatusChangeTracking(s9Project: IS9Project): void {
    this._stopStatusChangeTracking();
    this.statusSubscription = this._s9ProjectService
      .getSessionStatusStream(s9Project.id)
      .subscribe(status => this._onStatusChange(status));
  }

  private _stopStatusChangeTracking(): void {
    this.statusSubscription && this.statusSubscription.unsubscribe();
    this.statusSubscription = null;
  }
}
