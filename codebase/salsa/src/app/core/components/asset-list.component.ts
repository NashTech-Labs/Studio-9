import {
  Component,
  Inject,
  Input,
  OnChanges,
  SimpleChanges,
} from '@angular/core';

import { Observable } from 'rxjs/Observable';

import {
  LIBRARY_SECTIONS,
  LibrarySectionDefinition,
} from '../../library/library.interface';
import {
  IAsset,
  IAssetReference,
  IAssetService,
} from '../interfaces/common.interface';
import { AssetURLService } from '../services/asset-url.service';

interface IAssetWithType extends IAsset {
  type: IAsset.Type;
}

@Component({
  selector: 'asset-list',
  template: `
    <ul *ngIf="assets.length">
      <li *ngFor="let asset$  of assets">
        <a *ngIf="asset$ | async as asset; else loading"
          [routerLink]="assetUrlService.assetURL(asset.type, asset.id)"
        >
          {{asset.name}}
        </a>
        <ng-template #loading>Loading...</ng-template>
      </li>
    </ul>

    <p *ngIf="!assets.length">No assets</p>
  `,
})
export class AssetListComponent implements OnChanges {
  @Input() assetReferences: IAssetReference[];

  protected assets: Observable<IAssetWithType>[] = [];

  constructor(
    protected assetUrlService: AssetURLService,
    @Inject(LIBRARY_SECTIONS) private sections: LibrarySectionDefinition<IAsset>[],
  ) {
  }

  ngOnChanges(changes: SimpleChanges): void {
    if ('assetReferences' in changes) {
      this.assets = this._prepareAssetLoaders(changes['assetReferences'].currentValue);
    }
  }

  private _getServiceByAssetType(type: IAsset.Type): IAssetService<IAsset, any> {
    const section = this.sections.find((section) => section.assetType === type);
    if (!section) {
      throw new Error('Unknown service type: ' + type);
    }

    return section.service;
  }

  private _prepareAssetLoaders(references: IAssetReference[]): Observable<IAssetWithType>[] {
    const observables = references.map((assetRef: IAssetReference): Observable<IAssetWithType> => {
      return this._getServiceByAssetType(assetRef.type)
        .get(assetRef.id)
        .map(asset => Object.assign({}, asset, { type: assetRef.type}));
    });

    return observables;
  }
}
