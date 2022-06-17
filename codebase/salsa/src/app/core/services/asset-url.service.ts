import { Inject, Injectable } from '@angular/core';

import { ASSET_BASE_ROUTE, IAsset } from '../interfaces/common.interface';

export type AssetURLMap = {[k: string]: string[]};

@Injectable()
export class AssetURLService {
  private readonly _baseUrlMap: AssetURLMap = {};

  constructor(
    @Inject(ASSET_BASE_ROUTE) assetURLMaps: AssetURLMap[],
  ) {
    this._baseUrlMap = assetURLMaps.reduce((acc, map) => {
      return Object.assign(acc, map);
    }, {});
  }

  assetURL(assetType: IAsset.Type, assetId: string): string[] {
    if (assetType in this._baseUrlMap) {
      return this._baseUrlMap[assetType].concat([assetId]);
    }
    return null;
  }
}
