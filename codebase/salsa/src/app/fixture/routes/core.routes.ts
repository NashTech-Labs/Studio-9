import { HttpResponse } from '@angular/common/http';

import 'rxjs/add/observable/of';
import { Observable } from 'rxjs/Observable';

import { IS3BucketInfo, TokenResponse } from '../../core/core.interface';
import { IAsset, IBackendList } from '../../core/interfaces/common.interface';
import { IFixtureServiceRoute, IFixtureUser } from '../fixture.interface';

export const coreRoutes: IFixtureServiceRoute[] = [
  {
    url: 'signin$',
    method: 'POST',

    handler: function(this, params, _user, next) {
      const users = this.collections.users;
      const user: IFixtureUser = users.findOne({username: params.username, password: params.password});

      if (!user) { throw new Error('Invalid credentials'); } // TODO: specify later

      return next().do((response: HttpResponse<TokenResponse>) => {
        users.update({
          ...user,
          token: response.body.access_token,
          _isRealUser: true,
        });
      }).catch(() => {
        users.update({
          ...user,
          _isRealUser: false,
        });
        return Observable.of({
          access_token: user.token,
          expires: 9999999999999, // forever young
          expires_in: 60 * 60 * 24, // one day
        });
      });
    },
  },
  {
    url: 'signup$',
    method: 'POST',

    handler: function(this, params) {
      const users = this.collections.users;

      // create user entity
      const data = Object.assign({}, params, {
        id: Date.now().toString(),
        token: Date.now().toString(),
      });

      return users.insertOne(data);
    },
  },
  {
    url: 'me$',
    method: 'GET',
    handler: function(this, params, user) {
      // throw error if not authenticated
      if (!user || !user.id) { throw new Error('Not authenticated'); } // TODO: create real error objects

      // return user record if authenticated
      const users = this.collections.users;
      return users.findOne({id: user.id});
    },
  },
  {
    url: 'me$',
    method: 'POST',
    handler: function(this, params, user) {
      // throw error if not authenticated
      if (!user || !user.id) { throw new Error('Not authenticated'); } // TODO: create real error objects

      // update user record if authenticated
      // TODO: implement "update"
      const users = this.collections.users;
      return users.findOne({id: user.id});
    },
  },
  {
    url: 'me/stats$',
    method: 'GET',
    handler: function(this, params, user) {
      // TODO: could be renamed
      // TODO: return general environment params such as "space used/ tables/ flows")
      return {
        tablesCount: this.resultsetByScope(this.collections.tables, IAsset.Type.TABLE, 'all', user).count(),
        modelsCount: this.resultsetByScope(this.collections.models, IAsset.Type.MODEL, 'all', user).count(),
        binaryDatasetsCount: this.resultsetByScope(this.collections.binaryDatasets, IAsset.Type.DATASET, 'all', user).count(),
        cvModelsCount: this.resultsetByScope(this.collections.cvModels, IAsset.Type.CV_MODEL, 'all', user).count(),
        flowsCount: this.resultsetByScope(this.collections.flows, IAsset.Type.FLOW, 'all', user).count(),
        projectsCount: this.resultsetByScope(this.collections.projects, IAsset.Type.S9_PROJECT, 'all', user).count(),
        albumsCount: this.resultsetByScope(this.collections.albums, IAsset.Type.ALBUM, 'all', user).count(),
        pipelinesCount: this.resultsetByScope(this.collections.pipelines, IAsset.Type.PIPELINE, 'all', user).count(),
        experimentsCount: this.resultsetByScope(this.collections.experiments, IAsset.Type.EXPERIMENT, 'all', user).count(),
        s9ProjectsCount: this.resultsetByScope(this.collections.s9Projects, IAsset.Type.S9_PROJECT, 'all', user).count(),
      };
    },
  },
  {
    url: 's3buckets$',
    method: 'GET',
    handler: function (): IBackendList<IS3BucketInfo> {
      return {
        count: 1,
        data: [{
          id: 'foo',
          name: 'GovCloud Bucket',
        }],
      };
    },
  },
  {
    url: 'config/aws-regions$',
    method: 'GET',
    handler: function (this) {
      return Observable.of([
        'us-gov-west-1',
        'us-east-1',
        'us-east-2',
        'us-west-1',
        'us-west-2',
        'eu-west-1',
        'eu-west-2',
        'eu-central-1',
        'ap-south-1',
        'ap-southeast-1',
        'ap-southeast-2',
        'ap-northeast-1',
        'ap-northeast-2',
        'sa-east-1',
        'cn-north-1',
        'ca-central-1',
      ]).delay(2000);
    },
  },
];
