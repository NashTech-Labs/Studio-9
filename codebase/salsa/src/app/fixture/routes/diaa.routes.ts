import * as _ from 'lodash';

import config from '../../config';
import { IAsset, IObjectId, TObjectId } from '../../core/interfaces/common.interface';
import { IProcess } from '../../core/interfaces/process.interface';
import { diaaConfig } from '../../diaa/diaa.config';
import { IDIAA, IDIAACreate, IDIAARun } from '../../diaa/diaa.interface';
import { IExperimentFull } from '../../experiments/experiment.interfaces';
import { IModelSummary, ITabularModel, ITabularTrainPipeline, ITabularTrainResult } from '../../train/model.interface';
import { MiscUtils } from '../../utils/misc';
import { IFixtureServiceRoute, IFixtureTabularModel } from '../fixture.interface';

export const diaaRoutes: IFixtureServiceRoute[] = [
  { //diaas
    url: 'diaas$',
    method: 'GET',
    handler: function(this, params, user) {
      return this.serveAssetListRequest(this.collections.diaas, IAsset.Type.DIAA, params, user);
    },
  },
  {
    url: 'diaas/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const diaas = this.collections.diaas;
      const diaa = diaas.findOne({id: id, ownerId: user.id});

      if (!diaa) { throw new Error('DIAA Not found'); }

      return diaa;
    },
  },
  {
    url: 'diaas$',
    method: 'POST',
    handler: function(this, params: IDIAACreate, user) {
      const diaas = this.collections.diaas;
      const models = this.collections.models;
      const experiments = this.collections.experiments;

      const model: IFixtureTabularModel = models.findOne({id: params.modelId});
      const experiment: IExperimentFull<ITabularTrainPipeline, ITabularTrainResult> = experiments.findOne({id: model.experimentId}) as any;

      const rowGenerator = (_): IDIAA.SummaryRow => {
        const newRow = Object.keys(params.protectedGroupsColumnMapping).reduce((acc, key: string) => {
          if (params.diaaObjective === 'AIR') {
            switch (key) {
              case 'gender_female':
                acc[key] = MiscUtils.random(0.970, 0.999);
                break;
              case 'race_black':
                acc[key] = MiscUtils.random(0.907, 0.950);
                break;
              case 'race_hispanic':
                acc[key] = MiscUtils.random(0.724, 0.830);
                break;
              case 'race_asian':
                acc[key] = MiscUtils.random(0.907, 0.950);
                break;
              case 'age_older':
                acc[key] = MiscUtils.random(1, 1.159);
                break;
            }
          }
          if (params.diaaObjective === 'SMD') {
            acc[key] = MiscUtils.random(-100, 100);
          }
          return acc;
        }, params.diaaObjective === 'AIR' ? { airDecile: _ } : {});

        Object.keys(newRow)
          .filter(_ => !params.protectedGroupsColumnMapping[_] && _ !== 'airDecile')
          .forEach(key => {
            delete newRow[key];
          });

        return newRow;
      };

      const deciles = [];
      if (params.airSpecification.cutOffMode === 'decile') {
        const [start, end] = params.airSpecification.decile;
        deciles.push(..._.range(start, end + 1, 1));
      } else {
        deciles.push(null);
      }

      const datasetRefs: IDIAA.DatasetRef[] = [
        IDIAA.DatasetRef.INPUT,
      ];
      if (experiment && experiment.pipeline.holdOutInput) {
        datasetRefs.push(IDIAA.DatasetRef.HOLD_OUT);
      }
      if (experiment && experiment.pipeline.outOfTimeInput) {
        datasetRefs.push(IDIAA.DatasetRef.OUT_OF_TIME);
      }

      const summary = datasetRefs.reduce((acc, key) => {
        acc[key] = deciles.map(rowGenerator);
        return acc;
      }, {});

      const newDIAA: IDIAA = Object.assign({
        id: Date.now().toString(),
        ownerId: user.id,
        outputModelId: null,
        created: new Date().toISOString(),
        updated: new Date().toISOString(),
        summary,
        status: IDIAA.Status.CHECKING,
      }, params);

      diaas.insertOne(newDIAA);

      const processes = this.collections.processes;

      processes.insertOne({
        id: Date.now().toString(),
        ownerId: user.id, // TEMP TEMP TEMP
        target: IAsset.Type.DIAA,
        targetId: newDIAA.id,
        progress: 0,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(), // TODO: check it later
        started: new Date().toISOString(), // TODO: check it later
        jobType: IProcess.JobType.TABULAR_EVALUATE,
      });

      return newDIAA;
    },
  },
  {
    url: 'diaas/([\\w\\-]+)/run$',
    method: 'POST',
    handler: function(this, params: IDIAARun & {1: TObjectId}, user) {
      const diaas = this.collections.diaas;
      const models = this.collections.models;
      const experiments = this.collections.experiments;

      const id = params[1];

      const diaa: IDIAA = diaas.findOne({id: id, ownerId: user.id});
      if (!diaa) {
        throw new Error('DIAA item not found');
      }

      const model: ITabularModel = models.findOne({id: diaa.modelId, ownerId: user.id});
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

      const mutateSummary = (summary: IModelSummary): IModelSummary => {
        return Object.assign({}, summary, {
          areaUnderROC: summary.areaUnderROC * 0.95,
          KS: summary.KS * 0.95,
          roc: (summary.roc || []).map(([a, b]) => [a, b * b]),
        });
      };

      const newModel = Object.assign({}, model, {
        id: Date.now().toString(),
        status: config.model.status.values.ACTIVE,
        name: `${diaa.name} Alt.`,
        predictorColumns: model.predictorColumns.filter(_ => expectedPredictors.indexOf(_.name) >= 0),
        summary: Object.assign(mutateSummary(experiment.result.summary), {
          predictors: experiment.result.summary.predictors.filter(_ => expectedPredictors.indexOf(_.name) >= 0),
          variableImportance: experiment.result.summary.variableImportance.filter(_ => expectedPredictors.indexOf(_.name) >= 0),
        }),
        holdOutSummary: experiment.result.holdOutSummary || undefined,
        outOfTimeSummary: experiment.result.outOfTimeSummary || undefined,
      });

      delete newModel['$loki'];
      models.insertOne(newModel);

      if (diaa.outputModelId) {
        const oldModel = models.findOne({id: diaa.outputModelId});
        oldModel && models.remove(oldModel);
      }

      Object.assign(diaa, {
        outputModelId: newModel.id,
        status: IDIAA.Status.RUNNING,
        objective: params.objective,
        diaConstraints: params.diaConstraints,
      });

      diaa.altSummary = Object.keys(diaa.summary).reduce((acc, key) => {
        acc[key] = diaa.summary[key].map((row: IDIAA.SummaryRow): IDIAA.SummaryRow => {
          const newRow: IDIAA.SummaryRow = {
            airDecile: row.airDecile,
          };

          if (params.objective.airDecile && row.airDecile === params.objective.airDecile) {
            Object.keys(IDIAA.ObjectiveMetric)
              .filter(_ => row.hasOwnProperty(_.toLowerCase()))
              .forEach(metric => {
                const key = metric.toLowerCase();
                newRow[key] = (<string[]> params.objective.metrics).indexOf(metric) >= 0
                  ? Math.max(row[key], 0.9) * MiscUtils.random(1.05, 1.2)
                  : MiscUtils.random(0.9, row[key]);
              });
          } else {
            Object.keys(IDIAA.ObjectiveMetric)
              .filter(_ => row.hasOwnProperty(_.toLowerCase()))
              .forEach(metric => {
                const key = metric.toLowerCase();
                if (diaa.diaaObjective === diaaConfig.diaaObjectives.values.AIR) {
                  newRow[key] = (<string[]> params.objective.metrics).indexOf(metric) >= 0
                    ? Math.max(row[key], 0.9) * MiscUtils.random(1.01, 1.05)
                    : MiscUtils.random(0.9, row[key]);
                }
                if (diaa.diaaObjective === diaaConfig.diaaObjectives.values.SMD) {
                  newRow[key] = (<string[]> params.objective.metrics).indexOf(metric) >= 0
                    ? MiscUtils.random(row[key], 0)
                    : MiscUtils.random(-30, row[key]);
                }
              });
          }

          return newRow;
        });
        return acc;
      }, {});

      diaas.update(diaa);

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

      processes.removeWhere(_ => _.target === IAsset.Type.DIAA && _.targetId === diaa.id);

      processes.insertOne({
        id: Date.now().toString(),
        ownerId: user.id, // TEMP TEMP TEMP
        target: IAsset.Type.DIAA,
        targetId: diaa.id,
        progress: 0,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(), // TODO: check it later
        started: new Date().toISOString(), // TODO: check it later
        jobType: IProcess.JobType.TABULAR_EVALUATE,
      });

      return diaa;
    },
  },
  {
    url: 'diaas/([\\w\\-]+)$',
    method: 'PUT',
    handler: function(this, params, user) {
      const id = params[1];
      const diaas = this.collections.diaas;
      const diaa = diaas.findOne({id: id, ownerId: user.id});

      // update (specific properties only)
      ['name'].forEach(prop =>
        params[prop] !== undefined && (diaa[prop] = params[prop]),
      );

      diaas.update(diaa);
      return diaa;
    },
  },
  {
    url: 'diaas/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function(this, params, user): IObjectId {
      const id = params[1];
      const diaas = this.collections.diaas;
      const diaa = diaas.findOne({id: id, ownerId: user.id});

      if (!diaa) { throw new Error('diaa Not found'); }

      diaas.remove(diaa);

      return {id: id};
    },
  },
];
