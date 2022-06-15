import * as d3 from 'd3';
import * as _ from 'lodash';

import config from '../../config';
import { IAsset, IObjectId } from '../../core/interfaces/common.interface';
import { TTableValue } from '../../tables/table.interface';
import {
  IModelTrainSummary,
  ITabularModel,
  ITabularTrainModel,
  ITabularTrainPipeline,
  ITabularTrainResult,
} from '../../train/model.interface';
import { IFixtureModelEquation, IFixtureServiceRoute, MAX_MODEL_PROGRESS_ITERATIONS } from '../fixture.interface';

export function fixtureModelEquationFunction(equation: IFixtureModelEquation) {
  if (!equation) {
    return null;
  }

  return function(values: {[k: string]: TTableValue}): TTableValue {
    const categorical = (name: string, expected: string, coeff: number) => {
      const value = <string> values[name];
      return (value === expected) ? coeff : 0.0;
    };
    const continuous = (name: string, coeff: number) => {
      let value = parseFloat(<string> values[name]);
      if (name in equation.scales) {
        const [scale, mean] = equation.scales[name];
        value = (value - mean) / scale;
      }
      return value * coeff;
    };

    const pow = equation.intercept +
      _.sum(equation.categorical.map(([name, expected, coeff]) => categorical(name, expected, coeff))) +
      _.sum(equation.continuous.map(([name, coeff]) => continuous(name, coeff)));

    return (1 / (1 + Math.exp(-pow)) < equation.threshold) ? equation.answers[0] : equation.answers[1];
  };
}

export const modelsRoutes: IFixtureServiceRoute[] = [
  {
    url: 'models$',
    method: 'GET',
    handler: function(this, params, user) {
      return this.serveAssetListRequest(this.collections.models, IAsset.Type.MODEL, params, user);
    },
  },
  {
    url: 'models/([\\w\\-]+)$',
    method: 'GET',
    handler: function(this, params, user) {
      const id = params[1];
      const models = this.collections.models;
      const model = models.findOne({id: id, ownerId: user.id});

      if (!model) { throw new Error('Model Not found'); }

      return model;
    },
  },
  // TODO: implement POST /models/import
  {
    url: 'models/([\\w\\-]+)$',
    method: 'PUT',
    handler: function(this, params, user) {
      const id = params[1];
      const models = this.collections.models;
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
    url: 'models/([\\w\\-]+)$',
    method: 'DELETE',
    handler: function (this, params, user): IObjectId {
      const id = params[1];
      const models = this.collections.models;
      const model = models.findOne({id: id, ownerId: user.id});

      if (!model) { throw new Error('Model Not found'); }

      models.remove(model);

      return {id: id};
    },
  },
  {
    url: 'models/([\\w\\-]+)/stop-refining$',
    method: 'POST',
    handler: function(this, params, user) { //TODO OUTPUT TYPE
      const id = params[1];
      const models = this.collections.models;
      const model = <ITabularModel> models.findOne({ id: id, ownerId: user.id });
      const modelProgresses = this.collections.modelProgresses;
      const modelProgress = modelProgresses.findOne({ id: id });

      if (!model) {
        throw new Error('Model Not found');
      }

      if (!modelProgress) {
        throw new Error('Model Progress Not found');
      }

      if (model.status === config.model.status.values.TRAINING) {
        modelProgress.state = IModelTrainSummary.TrainState.COMPLETE;
        modelProgresses.update(modelProgress);
      }
      return true;
    },
  },
  {
    url: 'models/([\\w\\-]+)/train-summary$',
    method: 'GET',
    handler: function(this, params, user): IModelTrainSummary {
      const id = params[1];
      const modelProgresses = this.collections.modelProgresses;
      const models = this.collections.models;
      const model = <ITabularModel> models.findOne({ id: id, ownerId: user.id });
      const modelProgress = modelProgresses.findOne({ id: id });

      if (!model) {
        throw new Error('Model Not found');
      }

      const experiment = this.collections.experiments.findOne({ id: model.experimentId });
      if (!experiment) {
        throw new Error('Experiment Not found');
      }

      const tabularModel: ITabularTrainModel = {
        ...model,
        pipeline: experiment.pipeline as ITabularTrainPipeline,
        result: experiment.result as ITabularTrainResult,
      };

      // for models in collection without any training progress
      if (!modelProgress) {
        return {
          resources: this.createResourceSummary(),
          iterations: d3.range(MAX_MODEL_PROGRESS_ITERATIONS).map((index) =>
            this.createIteration(tabularModel, index),
          ),
          state: IModelTrainSummary.TrainState.COMPLETE,
        };
      }

      return modelProgress;
    },
  },

];
