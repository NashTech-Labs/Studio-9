import { IAsset, IObjectId } from '../../core/interfaces/common.interface';
import { PipelineCreate } from '../../pipelines/pipeline.interfaces';
import { IFixtureServiceRoute } from '../fixture.interface';

export const pipelinesRoutes: IFixtureServiceRoute[] = [
  {
    url: 'pipelines$',
    method: 'GET',
    handler: function(this, params, user) {
      return this.serveAssetListRequest(this.collections.pipelines, IAsset.Type.PIPELINE, params, user);
    },
  },
  {
    url: 'pipelines/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const pipelines = this.collections.pipelines;
      const pipeline =  pipelines.findOne({ id: id, ownerId: user.id });

      if (!pipeline) {
        throw new Error('Pipeline not found');
      }

      return pipeline;
    },
  },
  {
    url: 'pipelines/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function(this, params, user): IObjectId {
      const id = params[1];
      const pipelines = this.collections.pipelines;
      const pipeline = pipelines.findOne({ id: id, ownerId: user.id });

      if (!pipeline) {
        throw new Error('Pipeline not found');
      }

      pipelines.remove(pipeline);

      return { id: id };
    },
  },
  {
    url: 'pipelines$',
    method: 'POST',
    handler: function(this, params: PipelineCreate, user) {
      const pipelines = this.collections.pipelines;

      const pipeline = Object.assign(
        {
          id: Date.now().toString(),
          ownerId: user.id,
          name: null,
          created: new Date().toISOString(),
          updated: new Date().toISOString(),
          steps: [],
        },
        params,
      );

      return pipelines.insertOne(pipeline);
    },
  },
  {
    url: 'pipelines/([\\w\\-]+)$',
    method: 'PUT',
    handler: function (this, params, user) {
      const id = params[1];
      const pipelines = this.collections.pipelines;
      const pipeline = pipelines.findOne({ id: id, ownerId: user.id });

      if (!pipeline) {
        throw new Error('Pipeline not found');
      }

      Object
        .keys(params)
        .forEach(prop =>
          params[prop] !== undefined && (pipeline[prop] = params[prop]),
        );

      pipelines.update(pipeline);

      return pipeline;
    },
  },
  {
    url: 'pipeline-operators$',
    method: 'GET',
    handler: function (this, params) {
      let resultset = this.collections.pipelineOperators.chain();
      return this.prepareListResponse(resultset, params);
    },
  },
  {
    url: 'config/operator-categories$',
    method: 'GET',
    handler: function (this) {
      return this.collections.operatorCategories.chain().data();
    },
  },
];
