import config from '../../config';
import { IAsset } from '../../core/interfaces/common.interface';
import { IProcess } from '../../core/interfaces/process.interface';
import { ISharedResource } from '../../core/interfaces/shared-resource.interface';
import { IExperimentFull } from '../../experiments/experiment.interfaces';
import { IOptimizationId } from '../../optimize/optimization.interface';
import { ITabularModel, ITabularTrainPipeline, ITabularTrainResult } from '../../train/model.interface';
import { IFixtureServiceRoute } from '../fixture.interface';

export const optimizationsRoutes: IFixtureServiceRoute[] = [
  {
    url: 'optimizations$',
    method: 'GET',
    handler: function(this, params, user) {
      const optimizations = this.collections.optimizations;
      let resultset;
      if (params['scope'] === 'shared') {
        const shares: ISharedResource[] = this.collections.shares.findObjects({
          assetType: 'optimization',
          recipientId: user.id,
        });
        const optimizationIds = shares.map(_ => _.assetId);
        resultset = optimizations.chain().where(_ => optimizationIds.indexOf(_.id) > -1);
      } else {
        resultset = optimizations.chain().find({ownerId: user.id});
      }

      return this.prepareListResponse(resultset, params);
    },
  },
  {
    url: 'optimizations/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const optimizations = this.collections.optimizations;
      const optimization = optimizations.findOne({id: id, ownerId: user.id});

      if (!optimization) { throw new Error('Optimization Not found'); }

      return optimization;
    },
  },
  {
    url: 'optimizations$',
    method: 'POST',
    handler: function(this, params, user) {
      const optimizations = this.collections.optimizations;
      const models = this.collections.models;
      const experiments = this.collections.experiments;
      const model: ITabularModel = models.findOne({id: params['modelId'], ownerId: user.id});
      if (!model) { throw new Error('Model Not found'); }

      const experiment: IExperimentFull<ITabularTrainPipeline, ITabularTrainResult> = experiments.findOne({id: model.experimentId}) as any;
      if (!experiment) { throw new Error('Experiment Not found'); }

      const expectedPredictors = [
        'score2', 'g041s', 'g042s', 'g096_tuon', 're02_tuon', 'at26',
        'at27', 'br03', 'fr03', 'bc10', 's115', 'cc15',
        'score4', 'rtl_trd', 'cv11', 'cv12', 'cv14', 'at01s',
        'at20s', 'at104s', 'bc01s', 'g057s', 'g064s', 'g068s',
        'g071s', 'g100s', 'g232s', 'g250a', 'hr01s', 'mt01s',
        'at07_tuon', 'at33_tuon', 'bc02_tuon', 'bc07_tuon', 'bc09_tuon', 'coll_tuon',
        'fi03_tuon', 'g057_tuon', 'g083_tuon', 'in02_tuon', 'in07_tuon', 'in09_tuon',
        'in29_tuon', 'in33_tuon', 'mt01_tuon', 'mt03_tuon', 'mt29_tuon', 'mt32_tuon',
        'mt33_tuon', 'pf01_tuon', 'pf07_tuon', 're29_tuon', 're33_tuon', 'rt01_tuon',
        'rt07_tuon', 's012_tuon', 's040_tuon', 's064_tuon', 'at05', 'at06',
        'at23', 're11', 're12', 'fi06', 'fi08', 'in03',
        'in06', 'in10', 'in14', 'mt42', 'pf05', 'pf06',
        'pf08', 'bc13', 'bc14', 'rt05', 'rt06', 'ds07',
        'g046', 'g049', 's004', 's014', 's018', 's019',
        's054', 's055', 's060', 'cr_dti',
      ];
      const newModel = Object.assign({}, model, {
        id: Date.now().toString(),
        status: config.model.status.values.ACTIVE,
        name: params['outputModelName'],
        predictorColumns: model.predictorColumns.filter(_ => expectedPredictors.indexOf(_.name) >= 0),
        summary: Object.assign({}, experiment.result.summary, {
          predictors: experiment.result.summary.predictors.filter(_ => expectedPredictors.indexOf(_.name) >= 0),
          variableImportance: experiment.result.summary.variableImportance.filter(_ => expectedPredictors.indexOf(_.name) >= 0),
          areaUnderROC: experiment.result.summary.areaUnderROC * 1.15,
          KS: experiment.result.summary.KS * 1.15,
          roc: (experiment.result.summary.roc || []).map(([a, b]) => [a, Math.sqrt(b)]),
        }),
      });
      delete newModel['$loki'];
      models.insertOne(newModel);

      const newOptimization = {
        id: Date.now().toString(),
        ownerId: user.id,
        outputModelId: newModel.id,
        created: new Date().toISOString(),
        updated: new Date().toISOString(),
        status: config.optimization.status.values.RUNNING,
      };

      const processes = this.collections.processes;

      /*processes.insertOne({
       id: Date.now().toString(),
       ownerId: user.id, // TEMP TEMP TEMP
       target: IAsset.Type.MODEL,
       targetId: newModel.id,
       progress: 0,
       status: IProcess.Status.RUNNING,
       created: new Date().toISOString(), // TODO: check it later
       updated: new Date().toISOString(), // TODO: check it later
       });*/

      processes.insertOne({
        id: Date.now().toString(),
        ownerId: user.id, // TEMP TEMP TEMP
        target: IAsset.Type.OPTIMIZATION,
        targetId: newOptimization.id,
        progress: 0,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(), // TODO: check it later
        started: new Date().toISOString(), // TODO: check it later
        jobType: IProcess.JobType.ALBUM_AUGMENTATION,
      });
      return optimizations.insertOne(Object.assign(newOptimization, params));
    },
  },
  // TODO: implement POST /models/import
  {
    url: 'optimizations/([\\w\\-]+)$',
    method: 'PUT',
    handler: function(this, params, user) {
      const id = params[1];
      const optimizations = this.collections.optimizations;
      const optimization = optimizations.findOne({id: id, ownerId: user.id});

      // update (specific properties only)
      ['name'].forEach(prop =>
        params[prop] !== undefined && (optimization[prop] = params[prop]),
      );

      optimizations.update(optimization);
      return optimization;
    },
  },
  {
    url: 'optimizations/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function(this, params, user): IOptimizationId {
      const id = params[1];
      const optimizations = this.collections.optimizations;
      const optimization = optimizations.findOne({id: id, ownerId: user.id});

      if (!optimization) { throw new Error('Optimization Not found'); }

      optimizations.remove(optimization);

      return {id: id};
    },
  },
];
