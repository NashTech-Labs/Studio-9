import { IAsset, IBackendList, IListRequest, IObjectId, TObjectId } from '../../core/interfaces/common.interface';
import { IProcess } from '../../core/interfaces/process.interface';
import {
  IScriptDeployment,
  IScriptDeploymentCreate,
  IScriptDeploymentUpdate,
} from '../../deploy/script-deployment.interface';
import { IFixtureServiceRoute } from '../fixture.interface';

export const scriptDeploymentsRoutes: IFixtureServiceRoute[] = [
  {
    url: 'script-deployments$',
    method: 'GET',
    handler: function (this, params: IListRequest, user): IBackendList<IScriptDeployment> {
      return this.serveAssetListRequest(
        this.collections.scriptDeployments,
        IAsset.Type.SCRIPT_DEPLOYMENT,
        params,
        user,
      );
    },
  },
  {
    url: 'script-deployments$',
    method: 'POST',
    handler: function(this, params: IScriptDeploymentCreate, user): IScriptDeployment {
      const scriptDeployments = this.collections.scriptDeployments;
      const processes = this.collections.processes;
      const sd: IScriptDeployment = {
        id: Date.now().toString(),
        ownerId: user.id,
        created: new Date().toISOString(),
        updated: new Date().toISOString(),
        status: IScriptDeployment.Status.PREPARING,
        ...params,
      };
      processes.insertOne({
        id: 'm_' + Date.now().toString(),
        ownerId: user.id,
        target: IAsset.Type.SCRIPT_DEPLOYMENT,
        targetId: sd.id,
        progress: 0,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(),
        started: new Date().toISOString(),
        jobType: IProcess.JobType.SCRIPT_DEPLOYMENT,
      });
      return scriptDeployments.insertOne(sd);
    },
  },
  {
    url: 'script-deployments/([\\w\\-]+)$',
    method: 'GET',
    handler: function (this, params: { 1: TObjectId }, user): IScriptDeployment {
      const id = params[1];
      const scriptDeployments = this.collections.scriptDeployments;
      const sd = scriptDeployments.findOne({ id: id, ownerId: user.id });
      if (!sd) {
        throw new Error('Script Deployment Not Found');
      }
      return sd;
    },
  },
  {
    url: 'script-deployments/([\\w\\-]+)$',
    method: 'PUT',
    handler: function (this, params: IScriptDeploymentUpdate & { 1: TObjectId }, user): IScriptDeployment {
      const id = params[1];
      const scriptDeployments = this.collections.scriptDeployments;
      const sd = scriptDeployments.findOne({ id: id, ownerId: user.id });
      if (!sd) {
        throw new Error('Script Deployment Not Found');
      }

      sd.name = params.name;
      sd.description = params.description;

      return scriptDeployments.update(sd);
    },
  },
  {
    url: 'script-deployments/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function (this, params: { 1: TObjectId }, user): IObjectId {
      const id = params[1];
      const scriptDeployments = this.collections.scriptDeployments;
      const sd = scriptDeployments.findOne({ id: id, ownerId: user.id });

      if (!sd) {
        throw new Error('Script Deployment Not Found');
      }

      scriptDeployments.remove(sd);

      return { id: id };
    },
  },
  {
    url: 'script-deployments/([\\w\\-]+)/download',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const scriptDeployments = this.collections.scriptDeployments;
      const sd = scriptDeployments.findOne({id: id, ownerId: user.id});
      return `data:application/octet-stream;charset=utf-8,` +
        encodeURIComponent(JSON.stringify(sd));
    },
  },
];
