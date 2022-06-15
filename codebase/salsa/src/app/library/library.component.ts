import { Component, Inject, Input, OnDestroy, OnInit } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import * as _ from 'lodash';
import { Subscription } from 'rxjs/Subscription';

import { AlbumService } from '../albums/album.service';
import config from '../config';
import { IAsset } from '../core/interfaces/common.interface';
import { FeatureToggleService } from '../core/services/feature-toggle.service';

import { LIBRARY_SECTIONS, LibrarySectionDefinition } from './library.interface';


const SCOPES = [
  'all',
  'personal',
  'shared',
];

@Component({
  selector: 'library-view',
  template: `
    <library-item-list
      *ngIf="sections[form.controls['type'].value]; let section"
      [form]="form"
      [searchControl]="searchControl"
      [options]="section"
    ></library-item-list>
  `,
})
export class LibraryViewComponent implements OnInit, OnDestroy {
  @Input() type: string;
  config = config;
  form: FormGroup;
  searchControl: FormControl;

  readonly assets: IAsset.Type[] = [];
  readonly sections: {[assetType: string]: LibrarySectionDefinition<IAsset>} = {};

  private routeSubscription: Subscription;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private featureService: FeatureToggleService,
    readonly albums: AlbumService,
    @Inject(LIBRARY_SECTIONS) sections: LibrarySectionDefinition<IAsset>[],
  ) {
    this.assets = sections.map(_ => _.assetType);
    this.sections = _.keyBy(sections, _ => _.assetType);

    this.form = new FormGroup({
      type: new FormControl(),
      scope: new FormControl(''),
      search: new FormControl(''),
      order: new FormControl('-created'),
      page: new FormControl(1),
      page_size: new FormControl(config.defaultPageSize),
    });
    this.searchControl = new FormControl();
    this.searchControl.valueChanges.debounceTime(500).subscribe((value) => {
      this.form.patchValue({
        search: value,
        page: 1,
      });
    });
  }

  ngOnInit() {
    this.routeSubscription = this.route.params.subscribe(params => {
      const typeFilter = params['type'];
      const scopeOrItemId = params['scope'];

      if (!typeFilter || !scopeOrItemId) {
        const fallbackAsset = this.assets.filter(
          asset => this.featureService.areFeaturesEnabled(this.sections[asset].features),
        )[0];
        // redirection when incomplete information
        this.router.navigate(['/desk', 'library', typeFilter || config.asset.aliasesPlural[fallbackAsset], scopeOrItemId || 'personal']);
      } else if (!SCOPES.includes(scopeOrItemId)) {
        // scopeOrItemId is itemId
        this.router.navigate(['/desk', 'library', typeFilter, 'personal', scopeOrItemId]);
      } else {
        const paramType = this.assets
            .filter(item => config.asset.aliasesPlural[item] === typeFilter)
            .pop() || this.type || 'table';

        const oldParams = this.form.value;
        if (oldParams.scope !== scopeOrItemId || oldParams.type !== paramType) {
          this.form.setValue(Object.assign({}, oldParams, {
            'type': paramType,
            'scope': scopeOrItemId || '',
            'page': 1,
          }));
        }
      }
    });
  }

  ngOnDestroy() {
    this.routeSubscription && this.routeSubscription.unsubscribe();
  }
}
