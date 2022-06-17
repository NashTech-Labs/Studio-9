import { IAsset, IBackendList, IListRequest, IObjectId, TObjectId } from '../../core/interfaces/common.interface';
import { IProcess } from '../../core/interfaces/process.interface';
import { IOnlineAPI, IOnlineAPICreate, IOnlineAPIUpdate } from '../../deploy/online-api.interface';
import { IFixtureProcess, IFixtureServiceRoute } from '../fixture.interface';

export const onlineApisRoutes: IFixtureServiceRoute[] = [
  {
    url: 'online-apis$',
    method: 'GET',
    handler: function (this, params: IListRequest, user): IBackendList<IOnlineAPI<any>> {
      return this.serveAssetListRequest(this.collections.apis, IAsset.Type.ONLINE_API, params, user);
    },
  },
  {
    url: 'online-apis$',
    method: 'POST',
    handler: function<T extends IOnlineAPI.Type>(this, params: IOnlineAPICreate<T> & { 1: TObjectId }, user): IOnlineAPI<T> {
      const apis = this.collections.apis;
      switch (params.target.type) {
        case IAsset.Type.MODEL:
        case IAsset.Type.CV_MODEL:
          return apis.insertOne({
            id: Date.now().toString(),
            ownerId: user.id,
            created: new Date().toISOString(),
            updated: new Date().toISOString(),
            status: params.enabled ? IOnlineAPI.Status.ACTIVE : IOnlineAPI.Status.INACTIVE,
            name: params.name,
            target: params.target,
            enabled: params.enabled,
          });

        case IAsset.Type.PIPELINE:
          const api: IOnlineAPI<typeof params.target.type> = {
            id: Date.now().toString(),
            ownerId: user.id,
            created: new Date().toISOString(),
            updated: new Date().toISOString(),
            status: params.enabled ? IOnlineAPI.Status.PREPARING : IOnlineAPI.Status.INACTIVE,
            name: params.name,
            target: params.target,
            params: params.params,
            enabled: params.enabled,
          };

          if (params.enabled) {
            const pipelineApiProcess: IFixtureProcess = {
              id: 'dc_' + Date.now().toString(),
              ownerId: user.id,
              target: IAsset.Type.ONLINE_API,
              targetId: api.id,
              progress: 0,
              status: IProcess.Status.RUNNING,
              created: new Date().toISOString(),
              started: new Date().toISOString(),
              jobType: IProcess.JobType.API_DEPLOYMENT,
              _speed: 0.1,
            };

            const processes = this.collections.processes;
            processes.insertOne(pipelineApiProcess);
          }

          return apis.insertOne(api);
        default:
          throw new Error('Unsupported target type');
      }
    },
  },
  {
    url: 'online-apis/([\\w\\-]+)$',
    method: 'GET',
    handler: function (this, params: { 1: TObjectId }, user): IOnlineAPI<any> {
      const id = params[1];
      const apis = this.collections.apis;
      const api = apis.findOne({ id: id, ownerId: user.id });
      if (!api) {
        throw new Error('Api Not Found');
      }
      return api;
    },
  },
  {
    url: 'online-apis/([\\w\\-]+)$',
    method: 'PUT',
    handler: function (this, params: IOnlineAPIUpdate & { 1: TObjectId }, user): IOnlineAPI<any> {
      const id = params[1];
      const apis = this.collections.apis;
      const api = apis.findOne({ id: id, ownerId: user.id });
      if (!api) {
        throw new Error('Api Not Found');
      }

      api.name = params.name;

      if (api.enabled !== params.enabled) {
        api.enabled = params.enabled;
        api.status = params.enabled ? IOnlineAPI.Status.PREPARING : IOnlineAPI.Status.INACTIVE;

        if (params.enabled) {
          const pipelineApiProcess: IFixtureProcess = {
            id: 'dc_' + Date.now().toString(),
            ownerId: user.id,
            target: IAsset.Type.ONLINE_API,
            targetId: api.id,
            progress: 0,
            status: IProcess.Status.RUNNING,
            created: new Date().toISOString(),
            started: new Date().toISOString(),
            jobType: IProcess.JobType.API_DEPLOYMENT,
            _speed: 0.1,
          };

          const processes = this.collections.processes;
          processes.removeWhere({targetId: api.id, target: IAsset.Type.ONLINE_API});
          processes.insertOne(pipelineApiProcess);
        }
      }


      return apis.update(api);
    },
  },
  {
    url: 'online-apis/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function (this, params: { 1: TObjectId }, user): IObjectId {
      const id = params[1];
      const apis = this.collections.apis;
      const api = apis.findOne({ id: id, ownerId: user.id });

      if (!api) {
        throw new Error('Api Not Found');
      }

      apis.remove(api);

      return { id: id };
    },
  },
];
