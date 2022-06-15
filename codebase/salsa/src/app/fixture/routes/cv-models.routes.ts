import { IAsset, IObjectId } from '../../core/interfaces/common.interface';
import { IProcess } from '../../core/interfaces/process.interface';
import { ICVModel } from '../../train/cv-model.interface';
import { IFixtureServiceRoute } from '../fixture.interface';

export const cvModelsRoutes: IFixtureServiceRoute[] = [
  {
    url: 'cv-models$',
    method: 'GET',
    handler: function(this, params, user) {
      return this.serveAssetListRequest(this.collections.cvModels, IAsset.Type.CV_MODEL, params, user);
    },
  },
  {
    url: 'cv-models/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const models = this.collections.cvModels;
      const model = models.findOne({id: id, ownerId: user.id});

      if (!model) { throw new Error('Model Not found'); }

      return model;
    },
  },
  // TODO: implement POST /models/import
  {
    url: 'cv-models/([\\w\\-]+)$',
    method: 'PUT',
    handler: function(this, params, user) {
      const id = params[1];
      const models = this.collections.cvModels;
      const model = models.findOne({id: id, ownerId: user.id});

      // update (specific properties only)
      ['name'].forEach(prop =>
        params[prop] !== undefined && (model[prop] = params[prop]),
      );

      models.update(model);
      return model;
    },
  },
  {
    url: 'cv-models/([\\w\\-]+)/save$',
    method: 'POST',
    handler: function(this, params, user) {
      const id = params[1];
      const models = this.collections.cvModels;
      const model = models.findOne({id: id, ownerId: user.id});

      // update (specific properties only)
      ['name', 'description'].forEach(prop =>
        params[prop] !== undefined && (model[prop] = params[prop]),
      );

      model.inLibrary = true;

      models.update(model);
      return model;
    },
  },
  {
    url: 'cv-models/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function(this, params, user): IObjectId {
      const id = params[1];
      const models = this.collections.cvModels;
      const model = models.findOne({id: id, ownerId: user.id});

      if (!model) { throw new Error('Model Not found'); }

      models.remove(model);

      return {id: id};
    },
  },
  {
    url: 'cv-models/([\\w\\-]+)/export',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const models = this.collections.cvModels;
      const model = models.findOne({id: id, ownerId: user.id});
      return `data:application/octet-stream;charset=utf-8,` +
        encodeURIComponent(JSON.stringify(model));
    },
  },
  {
    url: 'cv-models/import$',
    method: 'POST',
    handler: function (this, params, user) {
      const cvModels = this.collections.cvModels;
      const processes = this.collections.processes;

      const model = {
        ...this.collections.cvModels.findOne({ownerId: user.id}),
        id: Date.now().toString(),
        name: params.name,
        status: ICVModel.Status.SAVING,
        input: null,
        output: null,
        testInput: null,
        testOutput: null,
      };
      delete model['$loki'];

      processes.insertOne({
        id: 'm_' + Date.now().toString(),
        ownerId: user.id,
        target: IAsset.Type.CV_MODEL,
        targetId: model.id,
        progress: 0,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(),
        started: new Date().toISOString(),
        jobType: IProcess.JobType.CV_MODEL_EVALUATE,
      });

      return cvModels.insertOne(model);
    },
  },
  {
    url: 'config/cv-architectures$',
    method: 'GET',
    handler: function (this) {
      return this.collections.cvArchitectures.chain().data();
    },
  },
  {
    url: 'config/cv-classifiers$',
    method: 'GET',
    handler: function (this) {
      return this.collections.cvClassifiers.chain().data();
    },
  },
  {
    url: 'config/cv-detectors$',
    method: 'GET',
    handler: function (this) {
      return this.collections.cvDetectors.chain().data();
    },
  },
  {
    url: 'config/cv-decoders$',
    method: 'GET',
    handler: function (this) {
      return this.collections.cvDecoders.chain().data();
    },
  },
];
