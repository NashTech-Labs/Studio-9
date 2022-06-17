import * as _ from 'lodash';
import * as Loki from 'lokijs';
import 'rxjs/add/operator/map';

import { IAlbum, IAlbumAugmentParams } from '../../albums/album.interface';
import config from '../../config';
import { IAsset, IAssetReference } from '../../core/interfaces/common.interface';
import { IProcess } from '../../core/interfaces/process.interface';
import {
  ExperimentType,
  IAbstractExperimentResult,
  IExperiment,
  IExperimentCreate,
  IExperimentFull,
} from '../../experiments/experiment.interfaces';
import { IGenericExperiment } from '../../pipelines/pipeline.interfaces';
import { ITable, ITableColumn, TTableValue } from '../../tables/table.interface';
import { CVModelType, IAugmentationSummary, ICVModel } from '../../train/cv-model.interface';
import { ICVTLTrainPipeline, ICVTLTrainStepResult } from '../../train/cvtl-train.interfaces';
import {
  IModelColumn,
  IModelEvaluationSummary,
  IModelSummary,
  IModelTrainSummary,
  ITabularModel,
  ITabularTrainPipeline,
  ITabularTrainResult,
  IVariableImportance,
} from '../../train/model.interface';
import { MiscUtils } from '../../utils/misc';
import { IFixtureDataset, IFixturePicture, IFixtureServiceRoute, IFixtureTabularModel } from '../fixture.interface';
import { FixtureServiceCollectionsMap } from '../fixture.service';

import { fixtureModelEquationFunction } from './models.routes';
import { getDataset } from './tables.routes';

const albumSize = function(pictures: Loki.Collection<IFixturePicture>, albumId: string): number {
  return pictures.chain().find({albumId: albumId}).count();
};

const runGenericExperiment = function(genericPipeline: IGenericExperiment.Pipeline, collections: FixtureServiceCollectionsMap, user, experimentId): [IGenericExperiment.StepResult[], IAssetReference[]] {
  const executedSteps: {[parameter: string]: any[]} = {};
  const stepResults: IGenericExperiment.StepResult[] = [];
  const selectedAssets: IAssetReference[] = [];
  do {
    let stuck = true;
    genericPipeline.steps.forEach(step => {
      if (!(step.id in executedSteps)) {
        const requiredSteps: string[] = Object.keys(Object.values(step.inputs).reduce((acc, v) => { acc[v.stepId] = true; return acc; }, {}));
        const eligibleForRun = requiredSteps.every(s => s in executedSteps);
        if (eligibleForRun) {
          const operator = collections.pipelineOperators.find({id: step.operator})[0];
          console.log(`Executing step ${step.id} (${operator.name})`);
          const outputs = [];
          const stepResult: IGenericExperiment.StepResult = {
            stepId: step.id,
            assets: [],
            summaries: [],
            outputValues: {},
            executionTime: 0,
          };
          const inputs = Object.entries(step.inputs).reduce((acc, [inputName, source]) => {
            acc[inputName] = executedSteps[source.stepId].length > source.outputIndex ? executedSteps[source.stepId][source.outputIndex] : null;
            return acc;
          }, {});
          try {
            switch (operator.id) {
              case 'select_model':
                if (!('model' in step.params)) {
                  throw new Error(`model parameter not specified`);
                }
                const models = collections.cvModels.find({id: <string> step.params['model']});
                if (models.length < 1) {
                  throw new Error(`Model not found`);
                }
                selectedAssets.push({type: IAsset.Type.CV_MODEL, id: models[0].id});
                outputs.push({...models[0]});
                break;
              case 'select_album':
                if (!('album_id' in step.params)) {
                  throw new Error(`album_id parameter not specified`);
                }
                const albums = collections.albums.find({id: <string> step.params['album_id']});
                if (albums.length < 1) {
                  throw new Error(`Album not found`);
                }
                selectedAssets.push({type: IAsset.Type.ALBUM, id: albums[0].id});
                outputs.push({...albums[0], type: IAlbum.Type.SOURCE});
                stepResult.executionTime = 1;
                break;
              case 'split_album':
                if (!('album' in inputs)) {
                  throw new Error(`album not specified`);
                }
                outputs.push({...inputs['album']});
                outputs.push({...inputs['album']});
                break;
              case 'transform_album':
                if (!('album' in inputs)) {
                  throw new Error(`album not specified`);
                }
                outputs.push(inputs['album']);
                break;
              case 'create_dataloader':
                if (!('album' in inputs)) {
                  throw new Error(`album not provided`);
                }
                const haveFixture = 'fixtureSummary' in inputs['album'];
                stepResult['summaries'] = [
                  {
                    type: IGenericExperiment.SummaryType.SIMPLE,
                    values: {
                      batch_size: 4,
                      classes: haveFixture ? inputs['album']['fixtureSummary']['labels'].join(', ') : '',
                      image_sizes: '374x500, 419x500, 375x500, 500x360, 333x500, 500x496',
                      number_of_batches: 3,
                      number_of_items: albumSize(collections.pictures, inputs['album'].id),
                    },
                  },
                ];
                outputs.push(inputs['album']);
                stepResult.executionTime = 3;
                break;
              case 'learn_detection_model':
              case 'learn_non_neural_classification_model':
              case 'learn_neural_classification_model':
                if (!inputs['model']) {
                  throw new Error(`model not provided`);
                }
                if (!('train_dataloader' in inputs)) {
                  throw new Error(`train_dataloader not provided`);
                }
                if (!('validate_dataloader' in inputs)) {
                  throw new Error(`validate_dataloader not provided`);
                }
                const model: ICVModel = inputs['model'];
                if ('fixtureSummary' in inputs['train_dataloader']) {
                  model.classes = inputs['train_dataloader']['fixtureSummary']['labels'];
                }
                outputs.push(model);
                stepResult.executionTime = 30/*Math.round(
                  Math.random() * 2 * albumSize(collections.pictures, inputs['train_dataloader'].id),
                )*/;
                break;
              case 'create_kpcn_mnl_classifier':
              case 'create_freescale_classifier':
              case 'create_fcn_classifier':
                const classificationModel: ICVModel = {
                  id: 'm_' + Date.now().toString(),
                  experimentId: experimentId,
                  ownerId: user.id,
                  name: 'm_' + Date.now().toString(),
                  updated: new Date().toString(),
                  created: new Date().toString(),
                  inLibrary: false,
                  status: ICVModel.Status.ACTIVE,
                  modelType: {
                    type: CVModelType.Type.CUSTOM,
                    labelMode: IAlbum.LabelMode.CLASSIFICATION,
                    classReference: {
                      packageId: 'dc-operators',
                      moduleName: 'studio9.ml.cv.transfer_learning',
                      className: 'ClassificationModel',
                    },
                  },
                  classes: [],
                };
                outputs.push(classificationModel);
                break;
              case 'create_prediction_model':
                if (!inputs['model']) {
                  throw new Error(`model not provided`);
                }
                outputs.push(inputs['model']);
                stepResult.executionTime = 1;
                break;
              case 'create_ssd_detector':
                const detectionModel: ICVModel = {
                  id: 'm_' + Date.now().toString(),
                  experimentId: experimentId,
                  ownerId: user.id,
                  name: 'm_' + Date.now().toString(),
                  updated: new Date().toString(),
                  created: new Date().toString(),
                  inLibrary: false,
                  status: ICVModel.Status.ACTIVE,
                  modelType: {
                    type: CVModelType.Type.CUSTOM,
                    labelMode: IAlbum.LabelMode.LOCALIZATION,
                    classReference: {
                      packageId: 'dc-operators',
                      moduleName: 'studio9.ml.cv.transfer_learning',
                      className: 'DetectionModel',
                    },
                  },
                  classes: [],
                };
                outputs.push(detectionModel);
                stepResult.executionTime = 1;
                break;
              case 'save_model':
                if (!('name' in step.params)) {
                  throw new Error(`name parameter not specified`);
                }
                if (!inputs['model']) {
                  throw new Error('model not provided');
                }
                const modelToSave = inputs['model'];
                const modelName = step.params['name'] || 'CVModel-' + Date.now().toString();
                modelToSave.name = modelName;
                collections.cvModels.insertOne({...modelToSave, inLibrary: false, updated: new Date().toString()});
                stepResult.assets.push({ type: IAsset.Type.CV_MODEL, id: modelToSave.id});
                stepResult.executionTime = 1;
                break;
              case 'calculate_praf_matrix':
              case 'calculate_dc_map_score':
                if (!('album' in inputs)) {
                  throw new Error(`no album provided`);
                }
                const album: IAlbum = inputs['album'];
                if ('fixtureSummary' in album) {
                  stepResult['summaries'] = [
                    {
                      type: IGenericExperiment.SummaryType.CONFUSION_MATRIX,
                      labels: album['fixtureSummary']['labels'],
                      rows: album['fixtureSummary']['confusionMatrix'],
                    },
                    {
                      type: IGenericExperiment.SummaryType.SIMPLE,
                      values: {
                        'mAP score': album['fixtureSummary']['mAP'],
                      },
                    },
                  ];
                }
                stepResult.executionTime = 0;
                break;
              case 'predict_2step':
                if (!('detection_model' in inputs)) {
                  throw new Error(`no detection model provided`);
                }
                if (!('classification_model' in inputs)) {
                  throw new Error(`no classification model provided`);
                }
                if (!('album' in inputs)) {
                  throw new Error(`no album provided`);
                }
                stepResult.executionTime = Math.round(
                  Math.random() * 0.05 * albumSize(collections.pictures, inputs['album'].id),
                );
                break;
              case 'predict_1step':
                if (!('model' in inputs)) {
                  throw new Error(`no model provided`);
                }
                if (!('album' in inputs)) {
                  throw new Error(`no album provided`);
                }
                stepResult.executionTime = Math.round(
                  Math.random() * 0.01 * albumSize(collections.pictures, inputs['album'].id),
                );
                break;
              case 'save_album':
                if (!('album' in inputs)) {
                  throw new Error(`no album provided`);
                }
                const oldAlbum = inputs['album'];
                // Save album
                const albumToSave: IAlbum = Object.assign({}, oldAlbum, {
                  id: Date.now().toString(),
                  name: step.params['name'] || 'Album ' + Date.now().toString(),
                  description: step.params['description'] || '',
                  ownerId: user.id,
                  updated: new Date().toString(),
                  created: new Date().toString(),
                  status: IAlbum.Status.ACTIVE,
                });
                delete albumToSave['$loki'];
                collections.albums.insertOne({...albumToSave, inLibrary: false});
                // Save pictures
                const pictures = collections.pictures.chain().find({ albumId: oldAlbum.id }).data();
                const addPredictions = oldAlbum.type !== IAlbum.Type.SOURCE;
                let picturesCount = 0;
                pictures.forEach(picture => {
                  const copiedPicture = Object.assign({}, picture, {
                    albumId: albumToSave.id,
                    id: `${albumToSave.id}_${picture.id}`,
                  });
                  if (addPredictions && picture['fixtureTags']) {
                    copiedPicture.predictedTags = picture.fixtureTags;
                  }
                  delete copiedPicture['$loki'];
                  collections.pictures.insert(copiedPicture);
                  picturesCount++;
                });
                stepResult.assets.push({ type: IAsset.Type.ALBUM, id: albumToSave.id});
                stepResult.summaries = [
                  {
                    type: IGenericExperiment.SummaryType.SIMPLE,
                    values: {
                      'Album ID': albumToSave.id,
                    },
                  },
                ];
                stepResult.executionTime = Math.round(
                  Math.random() * 0.01 * picturesCount,
                );
                break;
              case 'add_prediction_to_album':
                if (!('album' in inputs)) {
                  throw new Error(`album not specified`);
                }
                const albumWithPredictions: IAlbum = {...inputs['album'], type: IAlbum.Type.TRAINRESULTS};
                outputs.push(albumWithPredictions);
                stepResult.executionTime = Math.round(
                  Math.random() * 0.001 * albumSize(collections.pictures, albumWithPredictions.id),
                );
                break;
              default:
                console.log(`No processing for ${operator.id}`);
            }
          } catch (e) {
            throw new Error(`Error while executing pipeline: ${operator.name} ${e}`);
          }
          executedSteps[step.id] = outputs;
          stepResults.push(stepResult);
          stuck = false;
        }
      }
    });
    if (stuck) {
      throw new Error('Error while executing pipeline (stuck)');
    }
  } while (Object.keys(executedSteps).length < genericPipeline.steps.length);
  console.log('Pipeline executed');
  return [stepResults, selectedAssets];
};

const generateSimpleAugmentationAlbumSummary = (
  picturesLength: number,
  augmentations: IAlbumAugmentParams.Augmentation[],
): IAugmentationSummary[] => {
  return augmentations.map(augmentation => {
    return {
      count: MiscUtils.getRandomInt(1, picturesLength),
      augmentation,
    };
  });
};

function createTabularTrainExperiment(fixtureService, params, user) {
  let result: ITabularTrainResult;
  const pipeline: ITabularTrainPipeline = params.pipeline;
  const experiments = fixtureService.collections.experiments;
  const models = fixtureService.collections.models;
  const tables = fixtureService.collections.tables;
  const table = tables.findOne({ id: pipeline.input, ownerId: user.id });
  const processes = fixtureService.collections.processes;
  const modelProgresses = fixtureService.collections.modelProgresses;

  if (!table) {
    throw new Error('Table Not found');
  }

  const variableImportanceColumns = [
    {
      name: `ex_feature_1`,
      displayName: `ex_feature_1`,
      dataType: ITable.ColumnDataType.STRING,
      variableType: ITable.ColumnVariableType.CATEGORICAL,
      columnType: config.table.column.columnType.values.ATTRIBUTE,
    },
    {
      name: `ex_feature_1_desc`,
      displayName: `ex_feature_1_desc`,
      dataType: ITable.ColumnDataType.STRING,
      variableType: ITable.ColumnVariableType.CATEGORICAL,
      columnType: config.table.column.columnType.values.ATTRIBUTE,
    },
    {
      name: `ex_feature_2`,
      displayName: `ex_feature_3`,
      dataType: ITable.ColumnDataType.STRING,
      variableType: ITable.ColumnVariableType.CATEGORICAL,
      columnType: config.table.column.columnType.values.ATTRIBUTE,
    },
    {
      name: `ex_feature_2_desc`,
      displayName: `ex_feature_2_desc`,
      dataType: ITable.ColumnDataType.STRING,
      variableType: ITable.ColumnVariableType.CATEGORICAL,
      columnType: config.table.column.columnType.values.ATTRIBUTE,
    },
    {
      name: `ex_feature_3`,
      displayName: `ex_feature_3`,
      dataType: ITable.ColumnDataType.STRING,
      variableType: ITable.ColumnVariableType.CATEGORICAL,
      columnType: config.table.column.columnType.values.ATTRIBUTE,
    },
    {
      name: `ex_feature_3_desc`,
      displayName: `ex_feature_3_desc`,
      dataType: ITable.ColumnDataType.STRING,
      variableType: ITable.ColumnVariableType.CATEGORICAL,
      columnType: config.table.column.columnType.values.ATTRIBUTE,
    },
  ];

  const newTableColumns: ITableColumn[] = [
    {
      name: `${pipeline.responseColumn.name}_predicted`,
      displayName: `${pipeline.responseColumn.displayName} predicted`,
      dataType: pipeline.responseColumn.dataType,
      variableType: pipeline.responseColumn.variableType,
      columnType: config.table.column.columnType.values.METRIC,
    },
    {
      name: 'predicted_probabilities_class_1',
      displayName: `${pipeline.responseColumn.displayName} probability`,
      dataType: ITable.ColumnDataType.DOUBLE,
      variableType: ITable.ColumnVariableType.CONTINUOUS,
      columnType: config.table.column.columnType.values.METRIC,
    },
  ];

  if (pipeline.trainOptions.modelExplanation) {
    while (pipeline.predictorColumns.length < variableImportanceColumns.length / 2) {
      variableImportanceColumns.splice(variableImportanceColumns.length - 2, 2);
    }
    newTableColumns.push(...variableImportanceColumns);
  }

  newTableColumns.push(...table.columns);

  return getDataset(fixtureService, table).map(dataset => {
    const responseColumnIndex = table.columns.findIndex(_ => _.name === pipeline.responseColumn.name);
    const tableColumnIndices = [...table.columns]
      .map(({ name }) => table.columns.findIndex(_ => _.name === name));

    const fixtureFunction = fixtureModelEquationFunction(table.derivedModelEquation);

    const predictorIndices: { displayName: string; name: string }[] = pipeline.predictorColumns
      .map(({ name, displayName }) => {
        return {
          name,
          displayName,
        };
      });

    const newDataset: IFixtureDataset = {
      id: 'ds_' + Date.now().toString(),
      data: dataset.data.map(row => {
        const predicted = fixtureFunction
          ? fixtureFunction(table.columns.reduce((acc, column, i) => {
            acc[column.name] = row[i];
            return acc;
          }, {}))
          : row[responseColumnIndex];

        const predictedProbability = predicted ? MiscUtils.random(660, 1000) : MiscUtils.random(0, 659);
        const shuffledPredictorIndices: { displayName: string; name: string }[] = MiscUtils.shuffleArray(predictorIndices).slice(0, variableImportanceColumns.length / 2);
        const templates = [
          { name: 'cr_dti', description: '16.8 < cr_dti <= 25.8' },
          { name: 'score2', description: 'score2 <= 589' },
          { name: 'cv14', description: '10 < cv14' },
          { name: 'cr_dti', description: '25.8 < cr_dti' },
          { name: 'bc_trd', description: '10 < bc_trd' },
          { name: 'bc01s', description: '11 < bc01s' },
          { name: 'cr_dti', description: '16.8 < cr_dti <= 25.8' },
          { name: 'at01s', description: 'at01s <= 15' },
          { name: 's055', description: 's055 <= 15' },
          { name: 'bc_trd', description: '10 < bc_trd' },
          { name: 'cr_dti', description: 'cr_dti <= 9.7' },
          { name: 'g096_tuon', description: '12 < g096_tuon' },
          { name: 'cr_dti', description: '9.7 < cr_dti <= 16.8' },
          { name: 'bc_trd', description: 'bc_trd <= 4' },
          { name: 'bc01s', description: 'bc01s <= 4' },
          { name: 'cr_dti', description: '16.8 < cr_dti <= 25.8' },
          { name: 'at01s', description: 'at01s <= 15' },
          { name: 's055', description: 's055 <= 15' },
          { name: 'cr_dti', description: '25.8 < cr_dti' },
          { name: 'at01s', description: 'at01s <= 15' },
          { name: 'score2', description: 'score2 <= 589' },
          { name: 'cr_dti', description: '25.8 < cr_dti' },
          { name: 'at01s', description: 'at01s <= 15' },
          { name: 's055', description: 's055 <= 15' },
          { name: 's114', description: '4 < s114' },
          { name: 'cr_dti', description: '16.8 < cr_dti <= 25.8' },
          { name: 'g096_tuon', description: '12 < g096_tuon' },
          { name: 'cr_dti', description: 'cr_dti <= 9.7' },
          { name: 'bc_trd', description: '7 < bc_trd <= 10' },
          { name: 'bc01s', description: '7 < bc01s <= 11' },
          { name: 'at01s', description: '33 < at01s' },
          { name: 'bc01s', description: '11 < bc01s' },
          { name: 'bc_trd', description: '10 < bc_trd' },
          { name: 'cr_dti', description: 'cr_dti <= 9.7' },
          { name: 'at01s', description: 'at01s <= 15' },
          { name: 's055', description: 's055 <= 15' },
          { name: 'at01s', description: '33 < at01s' },
          { name: 's055', description: '33 < s055' },
          { name: 'cr_dti', description: '16.8 < cr_dti <= 25.8' },
          { name: 'cr_dti', description: '25.8 < cr_dti' },
          { name: 'at01s', description: '33 < at01s' },
          { name: 'bc_trd', description: '10 < bc_trd' },
          { name: 'cr_dti', description: '9.7 < cr_dti <= 16.8' },
          { name: 'score2', description: 'score2 <= 589' },
          { name: 's114', description: 's114 <= 1' },
          { name: 'bc01s', description: '11 < bc01s' },
          { name: 'bc_trd', description: '10 < bc_trd' },
          { name: 'cr_dti', description: '16.8 < cr_dti <= 25.8' },
          { name: 'bc01s', description: '11 < bc01s' },
          { name: 'bc_trd', description: '10 < bc_trd' },
          { name: 'cr_dti', description: '9.7 < cr_dti <= 16.8' },
          { name: 'cr_dti', description: '25.8 < cr_dti' },
          { name: 'at01s', description: '33 < at01s' },
          { name: 's055', description: '33 < s055' },
          { name: 'cr_dti', description: '9.7 < cr_dti <= 16.8' },
          { name: 's114', description: 's114 <= 1' },
          { name: 'bc01s', description: '4 < bc01s <= 7' },
          { name: 's114', description: '4 < s114' },
          { name: 'cr_dti', description: '16.8 < cr_dti <= 25.8' },
          { name: 'bc_trd', description: 'bc_trd <= 4' },
        ];

        //generate pre-defined descriptions randomly
        if (pipeline.trainOptions.modelExplanation) {
          //generate descriptions
          const importanceDescription = table.importanceDescription || shuffledPredictorIndices.map((predictor) => {
            const index = MiscUtils.getRandomInt(0, templates.length - 1);
            return {
              name: predictor.name,
              description: templates[index].description.replace(templates[index].name, predictor.name),
            };
          });

          const values = [];

          while (values.length < variableImportanceColumns.length / 2) {
            let candidate;
            do {
              candidate = _.sample(importanceDescription);
            } while (_.some(values, ({ name }) => name === candidate.name));
            values.push(candidate);
          }

          return [
            predicted,
            predictedProbability / 1000,
            ...(<TTableValue[]> _.flatten(values.map(value => _.values(value)))),
            ...tableColumnIndices.map(_ => row[_]),
          ];
        }
        return [predicted, predictedProbability / 1000, ...tableColumnIndices.map(_ => row[_])];
      }),
      count: dataset.count,
    };

    fixtureService.collections.datasets.insertOne(newDataset);

    const newTable: ITable = Object.assign({}, table, {
      id: 't_' + Date.now().toString(),
      name: pipeline.input + Date.now().toString(),
      datasetType: ITable.DatasetType.DERIVED,
      status: ITable.Status.SAVING,
      created: new Date().toISOString(),
      updated: new Date().toISOString(),
      columns: newTableColumns,
      datasetId: newDataset.id,
    });

    delete newTable['$loki'];
    tables.insertOne(newTable);

    const areaUnderROC = MiscUtils.random(0.7, 0.9);
    const pow = (1 / areaUnderROC) - 1;
    const roc: IModelEvaluationSummary.ROCRow[] = _.range(101).map((idx): IModelEvaluationSummary.ROCRow => {
      const x = Math.pow(idx / 100, 2);
      return <[number, number]> [x, Math.pow(x, pow)];
    });

    const model: IFixtureTabularModel = {
      id: 'm_' + Date.now().toString(),
      name: params.name + ' Model',
      ownerId: user.id,
      status: ITabularModel.Status.TRAINING,
      class: ITabularModel.Class.CLASSIFICATION,
      created: new Date().toISOString(),
      updated: new Date().toISOString(),
      predictorColumns: pipeline.predictorColumns,
      responseColumn: pipeline.responseColumn,
    };

    result = {
      output: newTable.id,
      probabilityColumns: ['predicted_probabilities_class_1'],
      predictedColumn: pipeline.responseColumn.name + '_predicted',
      summary: table.derivedModelSummary || {
        predictors: [],
        roc,
        areaUnderROC,
        KS: MiscUtils.random(0.8, 1.63),
      },
      modelId: null,
    };

    result.summary.predictors = model.predictorColumns.map((column: IModelColumn): IModelSummary.Predictor => {
      const coefficient = result.summary.predictors.find(_ => _.name === column.name);
      if (coefficient) {
        return coefficient;
      }
      return {
        name: column.name,
        estimate: MiscUtils.random(-10, 10),
        stdError: MiscUtils.random(0, 9999999999) / 100000000,
        tvalue: MiscUtils.random(-12, 12),
        pvalue: MiscUtils.random(0, 99999999) / 100000000,
      };
    });

    if (pipeline.trainOptions) {
      result.pipelineSummary = {
        stages: fixtureService.createStages(params),
      };
    }

    if (pipeline.holdOutInput) {
      const pow = MiscUtils.random(0.7, 1.4);
      Object.assign(model, {
        holdOutSummary: {
          roc: result.summary.roc.map(([a, b]) => {
            return [a, Math.pow(b, pow)];
          }),
          confusionMatrix: result.summary.confusionMatrix,
          areaUnderROC: Math.pow(result.summary.areaUnderROC, pow), //@ <0.95*AUC
          KS: result.summary.KS * MiscUtils.random(0.7, 1.4),
        },
      });

      const newTable: ITable = Object.assign({}, table, {
        id: 'h_' + Date.now().toString(),
        name: params.name + ' table',
        datasetType: ITable.DatasetType.DERIVED,
        status: ITable.Status.ACTIVE,
        created: new Date().toISOString(),
        updated: new Date().toISOString(),
        columns: newTableColumns,
        datasetId: newDataset.id,
      });

      delete newTable['$loki'];
      tables.insertOne(newTable);

      Object.assign(result, { holdOutOutput: newTable.id });
    }

    if (pipeline.outOfTimeInput) {
      const pow = MiscUtils.random(0.7, 1.4);
      Object.assign(result, {
        outOfTimeSummary: {
          roc: result.summary.roc.map(([a, b]) => {
            return [a, Math.pow(b, pow)];
          }),
          confusionMatrix: result.summary.confusionMatrix,
          areaUnderROC: Math.pow(result.summary.areaUnderROC, pow),
          KS: result.summary.KS * MiscUtils.random(0.7, 1.4),
        },
      });

      const newTable: ITable = Object.assign({}, table, {
        id: 'o_' + Date.now().toString(),
        name: params.name + 'Out Of Time Output Table',
        datasetType: ITable.DatasetType.DERIVED,
        status: ITable.Status.ACTIVE,
        created: new Date().toISOString(),
        updated: new Date().toISOString(),
        columns: newTableColumns,
        datasetId: newDataset.id,
      });

      delete newTable['$loki'];
      tables.insertOne(newTable);

      Object.assign(model, { outOfTimeOutput: newTable.id });
    }

    processes.insertOne({
      id: 'm_' + Date.now().toString(),
      ownerId: user.id,
      target: IAsset.Type.MODEL,
      targetId: model.id,
      progress: 0,
      status: IProcess.Status.RUNNING,
      created: new Date().toISOString(),
      started: new Date().toISOString(),
      jobType: IProcess.JobType.CV_MODEL_TRAIN,
    });

    modelProgresses.insertOne({
      id: model.id,
      state: IModelTrainSummary.TrainState.TRAINING,
      iterations: [],
      resources: fixtureService.createSingleResourceSummary(),
    });

    processes.insertOne({
      id: 't_' + Date.now().toString(),
      ownerId: user.id,
      target: IAsset.Type.TABLE,
      targetId: result.output,
      progress: null, // start after CV_MODEL PROCESS
      status: IProcess.Status.QUEUED,
      created: new Date().toISOString(),
      started: new Date().toISOString(),
      jobType: IProcess.JobType.TABULAR_TRAIN,
    });

    // Generating new variableImportance  (coefficients should be already generated above)
    if (pipeline.trainOptions.variableImportance && !result.summary.variableImportance) {
      result.summary.variableImportance = result.summary.predictors.map(variable => {
        const min = MiscUtils.random(-6, 10);
        const max = MiscUtils.random(min + 10, 25);
        const lowerQuartile = MiscUtils.random(min + 4, max - 6);
        const upperQuartile = MiscUtils.random(lowerQuartile, max - 3);
        const median = MiscUtils.random(lowerQuartile, upperQuartile);
        const decision = _.sample([IVariableImportance.Decision.TENTATIVE,
          IVariableImportance.Decision.CONFIRMED,
          IVariableImportance.Decision.REJECTED,
          IVariableImportance.Decision.SHADOW,
        ]);
        return {
          name: variable.name,
          min,
          max,
          lowerQuartile,
          upperQuartile,
          median,
          decision,
        };
      });
    }

    const newModel = models.insertOne(model);
    result.modelId = newModel.id;
    const experiment: IExperimentFull = Object.assign(
      {
        id: Date.now().toString(),
        name: null,
        description: null,
        type: null,
        ownerId: user.id,
        status: IExperiment.Status.RUNNING,
        created: new Date().toISOString(),
        updated: new Date().toISOString(),
        result,
      },
      params,
    );

    const newExperiment = experiments.insertOne(experiment);
    newModel.experimentId = newExperiment.id;
    models.update(newModel);

    return newExperiment;
  });
}

function runCVTLTrainExperiment(pipeline: ICVTLTrainPipeline, collections: FixtureServiceCollectionsMap, user, experimentId: string) {
  let stepIdModelId;
  let result: IAbstractExperimentResult;
  console.log(pipeline);
  if (!pipeline.step1.featureExtractorModelId) {
    const step1Model: ICVModel = {
      id: 'm_' + Date.now().toString(),
      ownerId: user.id,
      name: 'CVModel',
      created: new Date().toISOString(),
      updated: new Date().toISOString(),
      inLibrary: false,
      experimentId: experimentId,
      status: ICVModel.Status.ACTIVE,
      modelType: {
        type: CVModelType.Type.TL,
        tlType: CVModelType.TLType.LOCALIZATION,
        architecture: pipeline.step1.architecture,
        labelMode: IAlbum.LabelMode.LOCALIZATION,
        detectorType: 'RFBNET',
      },
      classes: [
        'bmp2_tank',
        'btr60_transport',
        'btr70_transport',
        't72_tank',
      ],
    };
    stepIdModelId = step1Model.id;
    collections.cvModels.insertOne(step1Model);
  } else {
    stepIdModelId = pipeline.step1.featureExtractorModelId;
  }
  const step1TrainAlbum = collections.albums.findOne({id: pipeline.step1.input});
  const step1: ICVTLTrainStepResult = {
    cvModelId: stepIdModelId,
    output: pipeline.step1.input,
    summary: step1TrainAlbum.fixtureSummary,
    trainTimeSpentSummary: {
      tasksQueuedTime: 3401,
      totalJobTime: 15000,
      dataLoadingTime: 1304,
      pipelineDetails: [
        { time: 101, description: 'Feature Extraction' },
        { time: 354, description: 'KPCA Training' },
      ],
      trainingTime: 2422,
      initialPredictionTime: 1105,
      modelSavingTime: 320,
    },
    evaluationTimeSpentSummary: {
      tasksQueuedTime: 3402,
      totalJobTime: 15001,
      dataLoadingTime: 1305,
      pipelineDetails: [],
      modelLoadingTime: 123123,
      scoreTime: 123127,
    },
  };

  if (pipeline.step1.augmentationOptions && pipeline.step1.augmentationOptions.augmentations.length) {
    step1.augmentationSummary = generateSimpleAugmentationAlbumSummary(
      1000,
      pipeline.step1.augmentationOptions.augmentations,
    );

    if (pipeline.step1.augmentationOptions.prepareSampleAlbum) {
      step1.augmentedSampleAlbum = step1.output;
    }
  }

  result = { step1 };
  if (pipeline.step2) {
    const step2TrainAlbum = collections.albums.findOne({id: pipeline.step2.input});
    const step2Model: ICVModel = {
      id: 'm_' + Date.now().toString(),
      ownerId: user.id,
      name: 'CVModel',
      created: new Date().toISOString(),
      updated: new Date().toISOString(),
      inLibrary: false,
      status: ICVModel.Status.ACTIVE,
      modelType: {
        type: CVModelType.Type.CUSTOM,
        labelMode: IAlbum.LabelMode.CLASSIFICATION,
        classReference: {
          packageId: 'some package',
          moduleName: 'some module',
          className: 'some class',
        },
      },
      classes: 'fixtureSummary' in step2TrainAlbum ? step2TrainAlbum.fixtureSummary.labels : [],
    };
    collections.cvModels.insertOne(step2Model);
    const step2: ICVTLTrainStepResult = {
      cvModelId: step2Model.id,
      output: pipeline.step2.input,
      testOutput: pipeline.step2.testInput,
      summary: step2TrainAlbum.fixtureSummary,
      trainTimeSpentSummary: {
        tasksQueuedTime: 3401,
        totalJobTime: 15000,
        dataLoadingTime: 1304,
        pipelineDetails: [
          { time: 101, description: 'Feature Extraction' },
          { time: 354, description: 'KPCA Training' },
        ],
        trainingTime: 2422,
        initialPredictionTime: 1105,
        modelSavingTime: 320,
      },
      evaluationTimeSpentSummary: {
        tasksQueuedTime: 3402,
        totalJobTime: 15001,
        dataLoadingTime: 1305,
        pipelineDetails: [
          { time: 103, description: 'Feature Extraction' },
          { time: 356, description: 'KPCA Training' },
        ],
        modelLoadingTime: 123123,
        scoreTime: 123127,
      },
    };

    if (pipeline.step2.augmentationOptions && pipeline.step2.augmentationOptions.augmentations.length) {
      step2.augmentationSummary = generateSimpleAugmentationAlbumSummary(
        1000,
        pipeline.step2.augmentationOptions.augmentations,
      );

      if (pipeline.step2.augmentationOptions.prepareSampleAlbum) {
        step2.augmentedSampleAlbum = step2.output;
      }
    }

    result['step2'] = step2;
  }
  return result;
}

export const experimentsRoutes: IFixtureServiceRoute[] = [
  {
    url: 'experiments$',
    method: 'GET',
    handler: function (this, params, user) {
      return this.serveAssetListRequest(this.collections.experiments, IAsset.Type.EXPERIMENT, params, user);
    },
  },
  {
    url: 'experiments/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const experiments = this.collections.experiments;
      const experiment = experiments.findOne({id: id, ownerId: user.id});

      if (!experiment) {
        throw new Error('Experiment Not found');
      }

      return experiment;
    },
  },
  {
    url: 'experiments$',
    method: 'POST',
    handler: function(this, params: IExperimentCreate, user) {
      const { experiments, processes } = this.collections;

      let expectedDuration = null;

      const experimentId = Date.now().toString();
      let result: IAbstractExperimentResult;
      switch (params.type) {
        case ExperimentType.TestExperiment:
          result = { ...params.pipeline };
          break;
        case ExperimentType.GenericExperiment:
          const genericPipeline = <IGenericExperiment.Pipeline> params.pipeline;
          const [stepResults, selectedAssets] = runGenericExperiment(genericPipeline, this.collections, user, experimentId);
          result = {
            pipeline: {...genericPipeline, assets: selectedAssets},
            steps: stepResults,
          };
          expectedDuration = _.sum(stepResults.map(_ => _.executionTime)) * 1000 + 5000;
          break;
        case ExperimentType.CVTLTrain:
          const pipeline = params.pipeline as ICVTLTrainPipeline;
          result = runCVTLTrainExperiment(pipeline, this.collections, user, experimentId);

          break;
        case ExperimentType.TabularTrain:
          return createTabularTrainExperiment(this, params, user);
        default:
          result = null;
      }

      const newExperiment: IExperimentFull = Object.assign(
        {
          id: experimentId,
          name: null,
          description: null,
          type: null,
          ownerId: user.id,
          status: IExperiment.Status.RUNNING,
          created: new Date().toISOString(),
          updated: new Date().toISOString(),
          result,
        },
        params,
      );
      processes.insertOne({
        id: 'm_' + Date.now().toString(),
        ownerId: user.id,
        target: IAsset.Type.EXPERIMENT,
        targetId: newExperiment.id,
        progress: 0,
        status: IProcess.Status.RUNNING,
        created: new Date().toISOString(),
        started: new Date().toISOString(),
        jobType: IProcess.JobType.GENERIC_EXPERIMENT,
        _expectedDuration: expectedDuration,
      });
      return experiments.insertOne(newExperiment);
    },
  },
  {
    url: 'experiments/([\\w\\-]+)$',
    method: 'PUT',
    handler: function(this, params, user) {
      const id = params[1];
      const experiments = this.collections.experiments;
      const experiment = experiments.findOne({ id: id, ownerId: user.id });
      if (!experiment) {
        throw new Error('Experiment Not found');
      }

      // update (specific properties only)
      [
        'name',
        'description',
      ].forEach(prop =>
        params[prop] !== undefined && (experiment[prop] = params[prop]),
      );

      experiments.update(experiment);

      return experiment;
    },
  },
  {
    url: 'experiments/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function(this, params, user) {
      const id = params[1];
      const experiments = this.collections.experiments;
      const experiment = experiments.findOne({ id: id, ownerId: user.id });
      if (!experiment) {
        throw new Error('Experiment Not found');
      }

      experiments.remove(experiment);

      return experiment;
    },
  },
];
