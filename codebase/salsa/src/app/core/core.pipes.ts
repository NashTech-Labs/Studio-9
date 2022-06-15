import { Pipe, PipeTransform } from '@angular/core';

import { Observable } from 'rxjs/Observable';

import { IAsset, IAssetService, TObjectId } from './interfaces/common.interface';
import { IUser } from './interfaces/user.interface';

@Pipe({name: 'AppUserName'})
export class AppUserNamePipe implements PipeTransform {
  /**
   * @param {IUser} user
   * @param {boolean} loading
   * @returns {string}
   */
  transform(user: IUser, loading?: boolean): string {
    if (loading) {
      return '...';
    } else if (user) {
      return `${user.firstName} ${user.lastName}`;
    } else {
      return 'Unknown';
    }
  }
}


@Pipe({name: 'loadAsset'})
export class LoadAssetPipe implements PipeTransform {
  transform<T extends IAsset>(assetId: TObjectId, service: IAssetService<T, any>): Observable<T> {
    return service.get(assetId);
  }
}
