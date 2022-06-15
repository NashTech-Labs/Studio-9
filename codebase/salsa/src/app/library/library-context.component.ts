import {
  Component,
  ComponentFactoryResolver,
  HostBinding,
  Inject,
  OnDestroy,
  OnInit,
  ViewContainerRef,
} from '@angular/core';
import { Event, NavigationEnd, Router } from '@angular/router';

import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { IAsset, IBackendList } from '../core/interfaces/common.interface';
import { Feature } from '../core/interfaces/feature-toggle.interface';
import { IProject } from '../core/interfaces/project.interface';
import { IUserStats } from '../core/interfaces/user.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { FeatureToggleService } from '../core/services/feature-toggle.service';
import { UserService } from '../core/services/user.service';
import { ReactiveLoader } from '../utils/reactive-loader';

import { LIBRARY_SECTIONS, LibrarySectionDefinition } from './library.interface';
import { ProjectService } from './project.service';

// TODO: opened and projectOpened should be refreshed after changing route and other (i.e. delete last project)
// TODO: UI for Projects (highlighting, margins, etc)

@Component({
  selector: 'library-context',
  template: `
    <div class="group">
      <button *ngFor="let action of actions"
        type="button"
        class="btn btn-primary btn-alt btn-block"
        (click)="performAction(action)"
      >{{action.caption}}</button>
    </div>
    <core-project-context></core-project-context>
    <div class="menu">
      <ul class="nav nav-stacked">
        <li *ngFor="let menuItem of menu; let i = index" class="top-level-menu"
          [ngClass]="{
            'has-submenu': filters[menuItem.id] && filters[menuItem.id].length,
            'open': opened[i],
            'disabled': menuItem.disabled || false
          }">
          <a (click)="menuItem.disabled || (opened[i] = !opened[i])">
            <i *ngIf="menuItem.icon" [ngClass]="menuItem.icon"></i>
            <span>
                {{menuItem.name}}<ng-template [ngIf]="stats[menuItem.id]">&nbsp;<span class="badge">{{stats[menuItem.id]}}</span></ng-template>
            </span>
          </a>
          <ul class="nav submenu" *ngIf="filters[menuItem.id] && filters[menuItem.id].length">
            <li *ngFor="let subItem of filters[menuItem.id]"
              [routerLinkActive]="['active']"
              [ngClass]="{'has-submenu': (subItem?.children || false),'open': subItem?.children && isRouteActive(subItem.route), 'disabled': subItem.disabled}"
            >
              <a *ngIf="!subItem.disabled" [routerLink]="subItem.route">
                <span>{{subItem.name}}</span>
              </a>
              <a *ngIf="subItem.disabled">
                <span>{{subItem.name}}</span>
              </a>
            </li>
          </ul>
        </li>
      </ul>
    </div>
  `,
})
export class LibraryContextComponent implements OnInit, OnDestroy {
  @HostBinding('class') classes = 'fixed-width';
  readonly config = config;
  readonly filters = {};
  readonly menu = [];
  readonly projectAssets = [];
  readonly actions: (LibrarySectionDefinition.SidebarAction & {sectionAlias: string})[] = [];

  currentUrl: string = '';
  itemId: string = '';
  opened: boolean[] = [];
  projectsOpened: boolean = false;
  projectOpened: boolean[] = [];
  projectList: IProject[] = [];
  stats: any = {};

  private routeSubscription: Subscription;
  private eventSubscription: Subscription;
  private projectsLoader: ReactiveLoader<IBackendList<IProject>, any>;
  private statsLoader: ReactiveLoader<IUserStats, any>;

  constructor(
    private router: Router,
    private projects: ProjectService,
    private events: EventService,
    private user: UserService,
    private viewContainer: ViewContainerRef,
    private componentFactoryResolver: ComponentFactoryResolver,
    private featureService: FeatureToggleService,
    @Inject(LIBRARY_SECTIONS) sections: LibrarySectionDefinition<IAsset>[],
  ) {
    sections = sections.filter(_ => !_.features || featureService.areFeaturesEnabled(_.features));

    this.menu = sections.map(section => {
      const id = config.asset.aliasesPlural[section.assetType];
      return {
        id,
        name: config.asset.labelsPlural[section.assetType],
        icon: section.icon,
        route: ['/desk', 'library', id],
        disabled: false,
      };
    })/*.concat(ifMocks( [{
        id: 'robots',
        name: 'Robots',
        icon: 'iconapp iconapp-robots',
        route: ['/desk', 'deploy'],
        disabled: true,
      },
      {
        id: 'visuals',
        name: 'Visuals',
        icon: 'iconapp iconapp-visuals',
        route: ['/desk', 'visualize'],
        disabled: true,
      }], []))*/;

    this.projectAssets = sections.filter(_ => _.inProjects).map(section => {
      const id = config.asset.aliasesPlural[section.assetType];
      return {
        name: config.asset.labelsPlural[section.assetType],
        subRoute: [id],
      };
    });

    this.actions = sections.reduce((acc, section) => {
      return [...acc, ...(section.sidebarActions || []).map(_ => ({
        ..._,
        sectionAlias: config.asset.aliasesPlural[section.assetType],
      }))];
    }, []);

    this.filters = this.menu.reduce((acc, {id}) => {
      acc[id] = [
        {
          name: 'All',
          route: ['/desk', 'library', id, 'all'],
        },
        {
          name: 'Personal',
          route: ['/desk', 'library', id, 'personal'],
        },
        {
          name: 'Shared w/Me',
          route: ['/desk', 'library', id, 'shared'],
        },
        {
          name: 'Enterprise',
          route: ['/desk', 'library', id, 'enterprise'],
          disabled: true,
        },
        {
          name: 'Third Party',
          route: ['/desk', 'library', id, 'thirdparty'],
          disabled: true,
        },
        {
          name: 'Recent',
          route: ['/desk', 'library', id, 'recent'],
          disabled: true,
        },
      ];

      return acc;
    }, {});

    this.projectsLoader = new ReactiveLoader(() => this._loadProjectList());
    this.projectsLoader.subscribe((_: IBackendList<IProject>) => {
      this.projectList = _.data;
      this.projectOpened = this.projectList.map(project => this.isRouteActive(['/desk', 'library', 'projects', project.id]));
    });

    this.statsLoader = new ReactiveLoader(() => this.user.getStats('all'));
    this.statsLoader.subscribe((stats: IUserStats) => {
      this.stats = {
        tables: 'tablesCount' in stats ? stats.tablesCount : 0,
        flows: 'flowsCount' in stats ? stats.flowsCount : 0,
        models: 'modelsCount' in stats ? stats.modelsCount : 0,
        datasets: 'binaryDatasetsCount' in stats ? stats.binaryDatasetsCount : 0,
        pipelines: 'pipelinesCount' in stats ? stats.pipelinesCount : 0,
        projects: 'projectsCount' in stats ? stats.projectsCount : 0,
        'cv-models': 'cvModelsCount' in stats ? stats.cvModelsCount : 0,
        'cv-predictions': 'cvPredictionsCount' in stats ? stats.cvPredictionsCount : 0,
        'predictions': 'tabularPredictionsCount' in stats ? stats.tabularPredictionsCount : 0,
        's9-projects': 's9ProjectsCount' in stats ? stats.s9ProjectsCount : 0,
        albums: 'albumsCount' in stats ? stats.albumsCount : 0,
        experiments: 'experimentsCount' in stats ? stats.experimentsCount : 0,
      };
    });
  }

  ngOnInit() {
    this.currentUrl = this.router.url;
    this.opened = this.menu.map(menuItem => this.isRouteActive(menuItem.route));
    this.routeSubscription = this.router.events.subscribe((event: Event) => {
      if (event instanceof NavigationEnd) {
        this.currentUrl = event.urlAfterRedirects;
        this.opened = this.menu.map((_, i) => this.opened[i] || this.isRouteActive(_.route));
        this.projectsOpened = this.projectsOpened || this.isRouteActive(['/desk', 'library', 'projects']);
      }
    });

    this.projectsLoader.load();
    this.statsLoader.load();

    this.eventSubscription = this.events.subscribe((event) => {
      let type = event.type;
      if (type === IEvent.Type.UPDATE_PROJECT_LIST) {
        this.projectsLoader.load();
      }
      switch (event.type) {
        case IEvent.Type.UPDATE_TABLE_LIST:
        case IEvent.Type.UPDATE_FLOW_LIST:
        case IEvent.Type.UPDATE_MODEL_LIST:
        case IEvent.Type.UPDATE_CV_MODEL_LIST:
        case IEvent.Type.UPDATE_PROJECT_LIST:
        case IEvent.Type.UPDATE_EXPERIMENT_LIST:
          this.statsLoader.load();
          break;
      }
    });
  }

  ngOnDestroy() {
    this.routeSubscription && this.routeSubscription.unsubscribe();
    this.eventSubscription && this.eventSubscription.unsubscribe();
  }

  isRouteActive(route: string[]): boolean {
    return this.currentUrl.indexOf(route.join('/')) === 0;
  }

  performAction(action: LibrarySectionDefinition.SidebarAction & {sectionAlias: string}) {
    if (action.modalClass) {
      const factory = this.componentFactoryResolver.resolveComponentFactory(action.modalClass);
      const modalRef = this.viewContainer.createComponent(factory);
      const sectionRoute = ['/desk', 'library', action.sectionAlias];
      const itemId = this.currentUrl.split('/')[sectionRoute.length + 2]; // e.g. /desk/library/s/scope/item

      modalRef.instance.open(this.isRouteActive(sectionRoute) ? itemId : null)
        .subscribe(() => modalRef.destroy());
    } else {
      this.router.navigate(action.navigateTo);
    }
  }

  private _loadProjectList(): Observable<IBackendList<IProject>> {
    return this.featureService.isFeatureEnabled(Feature.LIBRARY_PROJECTS)
      ? this.projects.list()
      : Observable.of({
        data: [],
        count: 0,
      });
  }
}
