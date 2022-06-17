import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { ActivatedRoute, Params } from '@angular/router';

import * as _ from 'lodash';
import { forkJoin } from 'rxjs/observable/forkJoin';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { ReactiveLoader } from '../utils/reactive-loader';

import { OperatorInfoModalComponent } from './operator-info-modal.component';
import { PipelineOperator } from './pipeline.interfaces';
import { PipelineService } from './pipeline.service';

const UNPUBLISHED_CATEGORY_ID = ''; // non-existing category

@Component({
  selector: 'canvas-edit-context',
  template: `
    <div class="group">
      <button type="button"
        class="btn btn-primary btn-block"
        routerLinkActive
        #pipelineCreateActive="routerLinkActive"
        [routerLink]="['/desk/pipelines/create']"
        [ngClass]="{'btn-alt': !pipelineCreateActive.isActive}"
      >Create Pipeline
      </button>
    </div>

    <operator-info-modal #operatorInfoModal></operator-info-modal>
    <div class="menu like-side-asset-list app-spinner-box">
      <app-spinner [visibility]="initialDataLoader.active | async"></app-spinner>
      <ul class="nav nav-stacked" *ngIf="initialDataLoader.loaded">
        <li class="top-level-menu">
          <a>
            <i class="glyphicon glyphicon-random"></i>
            Pipeline Operators
          </a>
        </li>
        <li>
          <app-input
            [control]="searchControl"
            iconBefore="glyphicon-search"
            iconAfter="glyphicon-remove"
            (iconAfterClick)="searchControl.setValue('')"
          ></app-input>
        </li>
        <ng-container *ngVar="(operators | apply: _prepareOperators: _searchQuery) as operatorsTree">
          <li *ngIf="!(operatorsTree | keys).length">
            <ul class="nav nav-pills submenu">
              <li>
                <a>{{_searchQuery ? 'No operators found for "' +  _searchQuery + '"' : 'No operators'}}</a>
              </li>
            </ul>
          </li>
          <li class="has-submenu" *ngFor="let categoryId of operatorsTree | apply: _extractCategoryIds; let i = index"
            [ngClass]="{'open': _expandedCategories | apply: _includes: categoryId}"
          >
            <a (click)="_toggleExpanded(categoryId)">
              <i class="ml-icon ml-icon-tight ml-icon-{{categoryId | apply: _getCategoryIcon: categories}}"></i>
              <span>{{categoryId | apply: _getCategoryName: categories}} <span class="badge">{{operatorsTree[categoryId].length}}</span></span>
            </a>
            <ul class="nav nav-pills submenu with-dropdown with-icons"
              *ngIf="_expandedCategories | apply: _includes: categoryId">
              <li *ngFor="let item of operatorsTree[categoryId]"
              >
                <a [title]="item.description || item.name">
                  <span
                    dnd-draggable
                    [dragEnabled]="true"
                    [dragData]="{ pipelineOperator: item }"
                  >{{item.name}}</span>
                </a>
                <i class="info-sign glyphicon glyphicon-info-sign" (click)="_operatorInfoModal.show(item)"></i>
              </li>
              <li *ngIf="!operators.length"><a>No Operators</a></li>
            </ul>
          </li>
        </ng-container>
      </ul>
    </div>
  `,
})
export class CanvasContextComponent implements OnInit, OnDestroy {
  readonly config = config;

  readonly initialDataLoader: ReactiveLoader<[PipelineOperator[], PipelineOperator.Category[]], any>;
  operators: PipelineOperator[] = [];
  categories: {[id: string]: PipelineOperator.Category} = {};

  @ViewChild('operatorInfoModal') protected _operatorInfoModal: OperatorInfoModalComponent;
  protected _pipelineId: string;
  protected _expandedCategories: string[] = [];

  private _subscriptions: Subscription[] = [];
  private searchControl = new FormControl('');
  private _searchQuery = '';

  constructor(
    private pipelineService: PipelineService,
    private _activatedRoute: ActivatedRoute,
  ) {
    this.initialDataLoader = new ReactiveLoader(() => forkJoin(
      this.pipelineService.listAllOperators(),
      this.pipelineService.listOperatorCategories(),
    ));

    this._subscriptions.push(this.initialDataLoader.subscribe(([operators, categories]) => {
      this.operators = operators;
      this.categories = _.keyBy(categories, c => c.id);
    }));

    this.searchControl.valueChanges.debounceTime(500).subscribe((value) => {
      this._searchQuery = value;
    });

  }

  _prepareOperators = (operators: PipelineOperator[], searchQuery: string): {[category: string]: PipelineOperator[]} => {
    let _operators = _.chain(operators)
      .groupBy((o: PipelineOperator) => `${o.packageName}.${o.moduleName}.${o.className}`)
      .map((ops: PipelineOperator[]): PipelineOperator => {
        // sorting by package version to show only newest one
        ops.sort((o1, o2) => {
          const v1 = (o1.packageVersion || '').split('.').map(_ => parseInt(_));
          const v2 = (o2.packageVersion || '').split('.').map(_ => parseInt(_));
          return _.zip(v1, v2).filter(([n1, n2]) => n1 !== n2).map(([n1, n2]) => n2 - n1)[0] || 0;
        });
        return ops[0];
      });
    if (searchQuery) {
      const query = searchQuery.toLocaleLowerCase();
      _operators = _operators.filter((operator: PipelineOperator) => operator.name.toLocaleLowerCase().includes(query));
    }
    return _operators
      .orderBy(o => o.name.toLowerCase())
      .groupBy((o: PipelineOperator) => o.category || UNPUBLISHED_CATEGORY_ID)
      .value();
  };

  ngOnInit(): void {
    this._subscriptions.push(this._activatedRoute.params.subscribe((params: Params) => {
      this._pipelineId = params['pipelineId'];
    }));
    this.initialDataLoader.load();
  }

  ngOnDestroy(): void {
    this._subscriptions.forEach(_ => _.unsubscribe());
  }

  _toggleExpanded(category: string): void {
    const index = this._expandedCategories.indexOf(category);
    if (index < 0) {
      this._expandedCategories = [...this._expandedCategories, category];
    } else {
      this._expandedCategories.splice(index, 1);
      this._expandedCategories = [...this._expandedCategories];
    }
  }

  readonly _includes = function(arr: any[], val: any): boolean {
    return arr.includes(val);
  };

  readonly _extractCategoryIds = function(operatorsTree: {}): string[] {
    return Object.keys(operatorsTree).sort();
  };

  _getCategoryName(categoryId: string, categories: {[key: string]: PipelineOperator.Category}): string {
    if (categoryId === UNPUBLISHED_CATEGORY_ID) {
      return 'Not Published';
    }
    if (categoryId in categories) {
      return categories[categoryId].name;
    }
    return categoryId;
  }

  _getCategoryIcon(categoryId: string, categories: {[key: string]: PipelineOperator.Category}): string {
    if (categoryId === UNPUBLISHED_CATEGORY_ID) {
      return 'not-published';
    }
    if (categoryId in categories) {
      return categories[categoryId].icon;
    }
    return 'unknown';
  }
}
