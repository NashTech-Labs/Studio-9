import {
  HttpClient,
  HttpErrorResponse,
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest,
  HttpResponse,
} from '@angular/common/http';
import { Injectable, Injector } from '@angular/core';

import * as d3 from 'd3';
import * as _ from 'lodash';
import * as Loki from 'lokijs';
import 'rxjs/add/observable/empty';
import 'rxjs/add/observable/throw';
import 'rxjs/add/operator/delay';
import 'rxjs/add/operator/map';
import 'rxjs/add/operator/mergeMap';
import 'rxjs/add/operator/shareReplay';
import 'rxjs/Observable';
import { Observable } from 'rxjs/Observable';

import { IAlbum } from '../albums/album.interface';
import { IBackendFlow, IFlow } from '../compose/flow.interface';
import config from '../config';
import { IAsset, IBackendList, IListRequest, TObjectId } from '../core/interfaces/common.interface';
import { IProcess } from '../core/interfaces/process.interface';
import { IProject } from '../core/interfaces/project.interface';
import { ISharedResource } from '../core/interfaces/shared-resource.interface';
import { IUser } from '../core/interfaces/user.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { BinaryDataset } from '../datasets/dataset.interfaces';
import { IOnlineAPI } from '../deploy/online-api.interface';
import { IOnlineTriggeredJob } from '../deploy/online-job.interface';
import { IScriptDeployment } from '../deploy/script-deployment.interface';
import { IS9Project, IS9ProjectSession } from '../develop/s9-project.interfaces';
import { IPackage } from '../develop/package.interfaces';
import { IDIAA } from '../diaa/diaa.interface';
import { IExperiment, IExperimentFull } from '../experiments/experiment.interfaces';
import { IOptimization } from '../optimize/optimization.interface';
import {
  Pipeline,
  PipelineOperator,
} from '../pipelines/pipeline.interfaces';
import { ICVPrediction, ICVPredictionStatus } from '../play/cv-prediction.interface';
import { IPrediction, IPredictionStatus } from '../play/prediction.interface';
import { IReplay } from '../play/replay.interface';
import { ITable, ITableStats } from '../tables/table.interface';
import { ICVArchitecture, ICVClassifier, ICVDecoder, ICVDetector } from '../train/cv-architecture.interfaces';
import { ICVModel } from '../train/cv-model.interface';
import {
  IModelTrainSummary,
  ITabularModel,
  ITabularTrainModel,
  ITabularTrainPipeline,
  ITabularTrainResult,
} from '../train/model.interface';
import { trainConfig } from '../train/train.config';
import { MiscUtils } from '../utils/misc';
import { IDashboard } from '../visualize/dashboard.interface';

import {
  IFixtureAlbum,
  IFixtureBinaryDataset,
  IFixtureDataset,
  IFixtureModelTrainSummary,
  IFixturePicture,
  IFixtureProcess,
  IFixtureServiceRoute,
  IFixtureTable,
  IFixtureTabularModel,
  IFixtureUser,
  MAX_MODEL_PROGRESS_ITERATIONS,
  PROCESS_TICK_INTERVAL,
} from './fixture.interface';
import { routes } from './fixture.service.routes';
import * as fixtures from './fixtures';
import { IProjectAsset } from './fixtures/project-assets';

const LokiIndexedAdapter: any = require('lokijs/src/loki-indexed-adapter');

export type FixtureServiceCollectionsMap = {
  albums: Loki.Collection<IFixtureAlbum>;
  cvModels: Loki.Collection<ICVModel>;
  cvPredictions: Loki.Collection<ICVPrediction>;
  dashboards: Loki.Collection<IDashboard>;
  datasets: Loki.Collection<IFixtureDataset>;
  flows: Loki.Collection<IBackendFlow>;
  modelPredictors: Loki.Collection<any>;
  models: Loki.Collection<IFixtureTabularModel>;
  optimizations: Loki.Collection<IOptimization>;
  pictures: Loki.Collection<IFixturePicture>;
  predictions: Loki.Collection<IPrediction>;
  processes: Loki.Collection<IFixtureProcess>;
  projects: Loki.Collection<IProject>;
  projectsAssets: Loki.Collection<IProjectAsset>;
  replays: Loki.Collection<IReplay>;
  shares: Loki.Collection<ISharedResource>;
  tables: Loki.Collection<IFixtureTable>;
  tableStatistics: Loki.Collection<ITableStats>;
  users: Loki.Collection<IFixtureUser>;
  modelProgresses: Loki.Collection<IFixtureModelTrainSummary>;
  diaas: Loki.Collection<IDIAA>;
  jobs: Loki.Collection<IOnlineTriggeredJob>;
  apis: Loki.Collection<IOnlineAPI>;
  operatorCategories: Loki.Collection<PipelineOperator.Category>;
  scriptDeployments: Loki.Collection<IScriptDeployment>;
  cvArchitectures: Loki.Collection<ICVArchitecture>;
  cvClassifiers: Loki.Collection<ICVClassifier>;
  cvDetectors: Loki.Collection<ICVDetector>;
  cvDecoders: Loki.Collection<ICVDecoder>;
  s9Projects: Loki.Collection<IS9Project>;
  packages: Loki.Collection<IPackage>;
  s9ProjectSessions: Loki.Collection<IS9ProjectSession>;
  pipelines: Loki.Collection<Pipeline>;
  pipelineOperators: Loki.Collection<PipelineOperator>;
  experiments: Loki.Collection<IExperimentFull>;
  binaryDatasets: Loki.Collection<IFixtureBinaryDataset>;
};

@Injectable()
export class FixtureService implements HttpInterceptor {
  readonly collections: FixtureServiceCollectionsMap = <any> {};
  private _db: Loki;
  private _ready: Observable<boolean>;
  private fixtureRoutes = routes;

  constructor(
    private events: EventService,
    private _injector: Injector,
  ) {
    // mockup database
    this._ready = this.initDatabase('insilico')
      .map(([db, collections]) => {
        this._db = db;
        Object.assign(this.collections, collections);
        this.initProcesses();
        return true;
      })
      .shareReplay(1);
  }

  get injector() {
    return this._injector;
  }

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (request.url.indexOf(config.api.base) === 0) {

      console.log(request);

      return this._ready
        .flatMap(() => { // here we read all files
          const bodyParams = {};
          let observable = Observable.of(bodyParams);

          // TODO: check other browsers
          if (request.body instanceof FormData) {
            (<any> request.body).forEach((value, key) => {
              if (value instanceof File) {
                observable = observable.flatMap(values => {
                  return readFileAsString(value).map(data => {
                    return Object.assign(values, {[key]: data});
                  });
                });
              } else {
                bodyParams[key] = value;
              }
            });
          } else {
            Object.assign(bodyParams, request.body);
          }

          return observable;
        })
        .flatMap((bodyParams) => {
          const backendRoute: IFixtureServiceRoute = this.fixtureRoutes.find((route: IFixtureServiceRoute) =>
            request.url.match(new RegExp(config.api.base + route.url)) && request.method === route.method);
          if (backendRoute) {
            const match = request.url.match(new RegExp(config.api.base + backendRoute.url));
            const token = this._token(request.headers.get(config.api.tokenLabel));
            const user = this.collections.users.findOne({ token: token });

            const searchParams = request.params.keys().reduce((acc, key) => {
              const value = request.params.get(key);
              if (value !== 'null') {
                acc[key] = value;
              }
              return acc;
            }, {});

            // add query params (just with numbered indexes)
            for (let i = 1; i < match.length; ++i) {
              searchParams[i] = match[i];
            }

            const mergedParams = _.merge({}, bodyParams, searchParams);

            try {
              const body = backendRoute.handler.call(
                this,
                mergedParams,
                user,
                () => next.handle(request).filter(_ => _ instanceof HttpResponse),
              );
              return (body instanceof Observable)
                ? body : Observable.of(body);
            } catch (e) {
              return Observable.throw(e);
            }
          }
          return Observable.throw(new Error(`No route for ${request.url} found`));
        })
        .delay(100 + Math.round(Math.random() * 300))
        .map(body => {
          if (body instanceof HttpResponse) {
            return body;
          }

          return new HttpResponse({ body: _.cloneDeep(body) });
        })
        .do((body) => console.log('RESPONSE:', {
          request,
          response: body,
        }))
        .catch((error: Error) => Observable.throw(new HttpErrorResponse({
          error: { error: { code: 400, message: error.message } },
          status: 400,
        })))
        .share();
    } else {
      return next.handle(request);
    }
  }

  createStages(model: ITabularTrainModel) {
    return model.pipeline.trainOptions.stages.map((stage): ITabularModel.PipelineSummaryStage => {
      const technique: ITabularModel.StageTechnique = _.sample(model.pipeline.trainOptions.techniques[stage]);
      const paramConstraints = model.pipeline.trainOptions.parameters[technique];
      const paramDefinitions = trainConfig.model.stageTechnique.params[technique];
      return {
        stage,
        technique,
        parameters: paramDefinitions.map((definition): ITabularModel.StageTechniqueParameter => {
          const constraint = paramConstraints.find(_ => _.name === definition.name);
          if (definition.type === 'continuous') {
            const { min, max } = constraint || definition;
            return {
              name: definition.name,
              value: _.random(min, max, true),
            };
          } else if (definition.type === 'categorical') {
            const value = _.sample<number | string>(constraint ? constraint.values : definition.options);
            return {
              name: definition.name,
              stringValue: <string> value,
            };
          }
        }),
      };
    });
  }

  createSingleResourceSummary(resources?: IModelTrainSummary.ResourcesSummary): IModelTrainSummary.ResourcesSummary {
    const nodes = resources ? resources.nodes : MiscUtils.getRandomInt(5, 15); // max nodes
    const nodeNames = d3.range(nodes).map(item => `node_${item}`);

    const cpus = resources ? resources.cpus : d3.range(nodes).reduce((acc, node, nodeIdx) => {
      acc += MiscUtils.getRandomInt(1, 4); // max cpu per node
      return acc;
    }, 0);
    const cpuCores = resources ? resources.cpuCores : d3.range(cpus).reduce((acc, cpu, cpuIdx) => {
      acc +=  MiscUtils.getRandomInt(4, 32); // max cores per cpu
      return acc;
    }, 0);
    const gpus = resources ? resources.gpus : d3.range(nodes).reduce((acc, node, nodeIdx) => {
      acc += MiscUtils.getRandomInt(1, 4); // max gpu per node
      return acc;
    }, 0);

    // CPU
    const cpuLoad: IModelTrainSummary.ResourcesSummaryValue[] = d3.range(cpus).map((cpuName) => {
      return { name: String(cpuName), value: MiscUtils.random(0, 1) };
    });

    const cpuLoadLog: IModelTrainSummary.ResourceLog[] = resources ? resources.cpuLoadLog : [];
    cpuLoadLog.push({
      value: cpuLoad.reduce((acc, percentage) => {
        acc += percentage.value;
        return acc;
      }, 0) / cpuCores,
      iteration: cpuLoadLog.length + 1,
    });

    // GPU
    const gpuLoad: IModelTrainSummary.ResourcesSummaryValue[] = d3.range(gpus).map((gpuName) => {
      return { name: String(gpuName), value: MiscUtils.random(0, 1) };
    });

    const gpuLoadLog: IModelTrainSummary.ResourceLog[] = resources ? resources.gpuLoadLog : [];
    gpuLoadLog.push({
      value: gpuLoad.reduce((acc, percentage) => {
        acc += percentage.value;
        return acc;
      }, 0) / gpus,
      iteration: gpuLoadLog.length + 1,
    });

    // Memory
    const memoryUsage: IModelTrainSummary.ResourcesSummaryValue[] = nodeNames.map((nodeName) => {
      return { name: nodeName, value: MiscUtils.random(0, 1) }; //max memory usage per node
    });

    const memoryUsageLog: IModelTrainSummary.ResourceLog[] = resources ? resources.memoryUsageLog : [];
    memoryUsageLog.push({
      value: memoryUsage.reduce((acc, percentage) => {
        acc += percentage.value;
        return acc;
      }, 0) / nodes,
      iteration: memoryUsageLog.length + 1,
    });

    return {
      nodes: nodes,
      cpus: cpus,
      cpuCores: cpuCores,
      gpus: gpus,
      cpuLoad: cpuLoad,
      cpuLoadLog: cpuLoadLog,
      gpuLoad: gpuLoad,
      gpuLoadLog: gpuLoadLog,
      memoryUsage: memoryUsage,
      memoryUsageLog: memoryUsageLog,
    };
  }

  createResourceSummary(): IModelTrainSummary.ResourcesSummary {
    const nodes = MiscUtils.getRandomInt(1, 5);
    const cpus = d3.range(nodes).reduce((acc) => {
      acc += MiscUtils.getRandomInt(1, 4); // max cpu per node
      return acc;
    }, 0);
    const cpuCores = d3.range(cpus).reduce((acc, cpu, cpuIdx) => {
      acc += MiscUtils.getRandomInt(1, 8); // max cores per cpu
      return acc;
    }, 0);
    const gpus = d3.range(nodes).reduce((acc) => {
      acc += MiscUtils.getRandomInt(1, 4); // max gpu per node
      return acc;
    }, 0);

    return d3.range(MAX_MODEL_PROGRESS_ITERATIONS).reduce((acc) => {
      return this.createSingleResourceSummary(acc);
    }, {
      nodes: nodes,
      cpus: cpus,
      cpuCores: cpuCores,
      gpus: gpus,
      cpuLoad: [],
      cpuLoadLog: [],
      gpuLoad: [],
      gpuLoadLog: [],
      memoryUsage: [],
      memoryUsageLog: [],
    });
  }

  createIteration(model: ITabularTrainModel, index): IModelTrainSummary.TrainIteration {
    return {
      index: index,
      summary: {
        areaUnderROC: Math.min((index / MAX_MODEL_PROGRESS_ITERATIONS) + FixtureService.random(0, 0.1), 1),
        predictors: [],
      },
      hyperParameters: this.createStages(model),
    };
  }
  // commen asset list serving routine
  serveAssetListRequest<T extends IAsset>(
    collection: LokiConstructor.Collection<T>,
    assetType: IAsset.Type,
    params: IListRequest,
    user: IUser,
  ): IBackendList<T> {
    const resultSet = this.resultsetByScope(<Loki.Collection<IAsset>> collection, assetType, params['scope'], user);
    return this.prepareListResponse(resultSet, params);
  }

  resultsetByScope(
    collection: LokiConstructor.Collection<IAsset>,
    assetType: IAsset.Type,
    scope: string,
    user: IUser,
  ): Resultset<IAsset> {
    if (scope === 'shared') {
      const shares: ISharedResource[] = this.collections.shares.findObjects({
        assetType: assetType,
        recipientId: user.id,
      });
      const assetIds = shares.map(_ => _.assetId);
      return collection.chain().where(_ => assetIds.indexOf(_.id) > -1);
    } else if (scope === 'all') {
      const shares: ISharedResource[] = this.collections.shares.findObjects({
        assetType: assetType,
        recipientId: user.id,
      });
      const assetIds = shares.map(_ => _.assetId);
      return collection.chain().where(_ => {
        return assetIds.indexOf(_.id) > -1 || (_.ownerId === user.id && _.inLibrary !== false);
      });
    } else {
      return collection.chain().find({ ownerId: user.id }).where(_ => _.inLibrary !== false);
    }
  }

// commen asset list serving routine
  getAssetWithACL<T extends IAsset>(
    collection: LokiConstructor.Collection<T>,
    assetType: IAsset.Type,
    assetId: TObjectId,
    user: IUser,
    sharedResourceId: TObjectId = null,
  ): T {
    const filter: Partial<IAsset> = { id: assetId, ownerId: user.id };
    if (sharedResourceId) {
      const share = this.collections.shares.findOne({
        id: sharedResourceId,
        recipientId: user.id,
        assetType: assetType,
        assetId: assetId,
      });
      if (share) {
        delete filter.ownerId;
      }
    }
    const asset = (<Loki.Collection<IAsset>> collection).findOne(filter);

    if (!asset) {
      throw new Error(`${config.asset.labels[assetType]} #${assetId} Not found`);
    }

    return <any> asset;
  }

  // default list handler
  prepareListResponse<T>(
    resultset,
    params: IListRequest = {},
    searchFieldName: string = 'name',
    searchFieldInParams: string = 'search',
  ): IBackendList<T> {
    // apply project filtering
    if (params['projectId']) {
      const projectsAssets = this.collections.projectsAssets;

      const assetIds = projectsAssets.findObjects({
        projectId: params['projectId'],
        folderId: params['folderId'] || null,
      }).map(link => link.assetId);

      resultset = resultset.find({'id': {'$in': assetIds}});
    }

    // apply searching (filtering)
    const search = params[searchFieldInParams];
    if (search) {
      let re = new RegExp(search, 'i');
      const searchObject = {};
      searchObject[searchFieldName] = { '$regex': re }; //@TODO ON BACKSLASH INVALID REGULAR EXPRESSION BUG
      resultset = resultset.find(searchObject);
    }

    // apply ordering
    const order = params['order'];
    if (order) {
      let compoundSort = [];
      try {
        order.split(',').forEach(item => {
          compoundSort.push((item[0] === '-') ? [item.substring(1), true] : item);
        });
        resultset = resultset.compoundsort(compoundSort);
      } catch (e) {
        throw e;
      }
    }

    // apply paging
    const page = parseInt(<any> params['page']) || 1;
    const pageSize = parseInt(<any> params['page_size']) || 20;
    const count = resultset.count();
    if (page && pageSize && count) {
      resultset = resultset.offset((page - 1) * pageSize).limit(pageSize);
    }

    return {
      count: count,
      data: resultset.data(),
    };
  }

  private _parse(value): string {
    return value === undefined ? undefined : JSON.parse(value);
  }

  private _token(value): string {
    // unwrap token from header value
    return ('' + value).replace('Bearer ', '');
  }

  private initDatabase(name): Observable<[Loki, FixtureServiceCollectionsMap]> {
    const dbKeyName = 'insilico-paged';

    return new Observable<[Loki, FixtureServiceCollectionsMap]>(observer => {
      const adapter = new LokiIndexedAdapter(dbKeyName);

      const pa = new Loki.LokiPartitioningAdapter(adapter, { paging: true });

      const db = new Loki(name, {
        adapter: pa,
        autoload: false,
        autosave: true,
        autosaveInterval: 1000,
      });

      const onLoad = () => {
        const collectionNames = Object.keys(fixtures);

        if (_.every(collectionNames, (name) => !!db.getCollection(name))) {
          const collections: FixtureServiceCollectionsMap = collectionNames.reduce((acc, prop) => {
            acc[prop] = db.getCollection(prop);

            return acc;
          }, <any> {});

          observer.next([db, collections]);
          observer.complete();
        } else {
          const observable: Observable<FixtureServiceCollectionsMap> = collectionNames.reduce((acc, prop) => {
            return acc.flatMap((collections: FixtureServiceCollectionsMap) => {
              const fixtureData = fixtures[prop];
              collections[prop] = db.addCollection(prop, { indices: fixtureData.options.indices });

              if (Array.isArray(fixtureData.data)) {
                fixtureData.data.forEach(doc => {
                  collections[prop].insert(doc);
                });
                return Observable.of(collections);
              } else if (typeof fixtureData.data === 'function') {
                const data: Observable<any[]> = fixtureData.data(this.injector.get(HttpClient));

                return data.map(rows => {
                  rows.forEach(doc => {
                    collections[prop].insert(doc);
                  });

                  return collections;
                });
              }
            });
          }, Observable.of(<any> {}));

          // preparing albums
          observable.map(collections => {
            collections.albums.find().forEach(function (album: IAlbum) {
              if (!album.name.match(/spacenet|cifar/)) {
                return;
              }

              const pictures = collections.pictures.findObjects({ albumId: album.id });
              if (pictures.length > 70) {
                let picturesWithTags: IFixturePicture[] = [];
                pictures.forEach((picture: IFixturePicture) => {
                  if (picture.tags.length) {
                    picturesWithTags.push(picture);
                  }
                });
                // clone all the pictures to make album big enough
                pictures.filter(_ => _.albumId === album.id).forEach((picture: IFixturePicture) => {
                  //multiply pictures
                  for (let i = 0; i < 7; i++) {
                    const copiedPicture = Object.assign({}, picture, {
                      id: `z${picture.id}_clone${i}`,
                      filename: `z${i}_${picture.filename}`,
                      type: 'SOURCE',
                      cloneId: i + 1,
                    });
                    delete copiedPicture['$loki'];

                    collections.pictures.insert(copiedPicture);
                  }
                });
              }
            });

            return collections;
          }).subscribe(collections => {
            observer.next([db, collections]);
            observer.complete();
          });
        }
      };

      if (navigator && (<any> navigator).webkitPersistentStorage) {
        (<any> navigator).webkitPersistentStorage.requestQuota (
          10000000000,
          function(grantedBytes) {
            console.log('we were granted ', grantedBytes, 'bytes');
            db.loadDatabase({}, onLoad);
          },
          function() { alert('Please refresh the page'); },
        );
      } else {
        db.loadDatabase({}, onLoad);
      }

      this.events.subscribe((event: any) => {
        if (event.type === IEvent.Type.CLEAN_MOCKS_REQUEST) {
          const onComplete = () => {
            window.location.href = '/';
          };

          db.close(() => {
            // TODO: replace this call with `adapter.deleteDatabase(name, onComplete);` once https://github.com/techfort/LokiJS/pull/584 is merged
            adapter.catalog.getAppKey(dbKeyName, name, function (result) {
              const id = result.id;

              if (id !== 0) {
                adapter.catalog.deleteAppKey(id, onComplete);
              } else {
                onComplete();
              }
            });
          });
        }
      });
    });
  }

  private initProcesses(): void {
    const processes = this.collections.processes;
    // update processes
    window.setInterval(() => {
      processes.findAndUpdate(item => item.status === IProcess.Status.RUNNING && item.progress < 1, process => {
        if (!process._touched) {
          process._touched = true;
          return process;
        }

        let progress;

        if (['MODEL'].indexOf(process.target) === -1) {
          const increment = process._expectedDuration
            ? (PROCESS_TICK_INTERVAL / process._expectedDuration)
            : (process._speed || 0.05) + Math.random() / 200;
          progress = Math.min(process.progress + increment, 1);
        } else if (process.target === IAsset.Type.MODEL) {
          const models = this.collections.models;
          const modelProgresses = this.collections.modelProgresses;
          const model = <ITabularModel> models.findOne({ id: process.targetId });
          const modelProgress = modelProgresses.findOne({ id: process.targetId });
          progress = process.progress;

          if (!model) {
            return;
          }

          const experiment = this.collections.experiments.findOne({ id: model.experimentId });

          if (!experiment) {
            return;
          }

          const trainModel: ITabularTrainModel = {
            ...model,
            pipeline: experiment.pipeline as ITabularTrainPipeline,
            result: experiment.result as ITabularTrainResult,
          };

          if (!modelProgress) {
            throw new Error('Model Progress Not Found');
          }
          /*if (modelProgress.progress <= 1) {
            modelProgress.progress += 1;
          }*/
          if (model.status === config.model.status.values.TRAINING) {
            if (modelProgress.state === IModelTrainSummary.TrainState.TRAINING) {
              //if (modelProgress.progress > 1) {
              modelProgress.state = IModelTrainSummary.TrainState.REFINING;
              //modelProgress.progress = 0;
              //}
            } else if (modelProgress.state === IModelTrainSummary.TrainState.REFINING) {
              //if (modelProgress.progress > 1) {
              //  modelProgress.progress = 0;
              modelProgress.resources = this.createSingleResourceSummary(modelProgress.resources);
              modelProgress.iterations.push(this.createIteration(trainModel, modelProgress.iterations.length));
              if (modelProgress.iterations.length < MAX_MODEL_PROGRESS_ITERATIONS) {
                progress = modelProgress.iterations.length / MAX_MODEL_PROGRESS_ITERATIONS;
              } else {
                progress = 1;
                modelProgress.state = IModelTrainSummary.TrainState.COMPLETE;
              }
              //}
            } else if (modelProgress.state === IModelTrainSummary.TrainState.COMPLETE) {
              // none
              progress = 1;
            }

            modelProgresses.update(modelProgress);
          }
        }

        Object.assign(process, {
          progress: Math.min(progress, 1),
        });

        // update target
        if (process.progress >= 1) {
          Object.assign(process, {
            status: IProcess.Status.COMPLETED,
          });

          if (process.jobType === IProcess.JobType.PROJECT_BUILD) {
            const s9Projects = this.collections.s9Projects;
            const s9Project = s9Projects.findOne({ id: process.targetId });
            if (s9Project) {
              s9Project.status = IS9Project.Status.IDLE;
              s9Projects.update(s9Project);
            }
          }

          if (process.target === IAsset.Type.TABLE) {
            let tables = this.collections.tables;
            let table = tables.findOne({ id: process.targetId });

            // TODO: silly detecting that process about Table Statistics, not table itself
            if (table.status === config.table.status.values.ACTIVE) {
              let tableStatistics = this.collections.tableStatistics;
              let tableStatistic = tableStatistics.findOne({ id: table.id });
              if (tableStatistic) {
                tableStatistic.status = config.table.status.values.ACTIVE;
                tableStatistics.update(tableStatistic);
              }
            }

            table.status = ITable.Status.ACTIVE;
            tables.update(table);
          }

          if (process.target === IAsset.Type.DATASET) {
            const dataset = this.collections.binaryDatasets
              .findOne({ id: process.targetId });

            if (dataset) {
              dataset.status = BinaryDataset.Status.IDLE;
              this.collections.binaryDatasets.update(dataset);
            }
          }

          if (process.target === IAsset.Type.PREDICTION) {
            let predictions = this.collections.predictions;
            let prediction = predictions.findOne({ id: process.targetId });

            if (prediction) {
              prediction.status = IPredictionStatus.DONE;
              predictions.update(prediction);

              // update table status
              const tables = this.collections.tables;
              const table = tables.findOne({ id: prediction.output });
              if (table) {
                tables.update(Object.assign(table, { status: config.table.status.values.ACTIVE }));
              }
            }
          }

          if (process.target === IAsset.Type.MODEL) {
            let models = this.collections.models;
            let model = models.findOne({ id: process.targetId });

            if (model) {
              model.status = ITabularModel.Status.PREDICTING;
              models.update(model);
            }
          }

          if (process.target === IAsset.Type.CV_MODEL) {
            const cvModels = this.collections.cvModels;
            const cvModel = cvModels.findOne({ id: process.targetId });

            if (cvModel) {
              cvModel.status = ICVModel.Status.ACTIVE;
              cvModels.update(cvModel);
            }

          }

          if (process.target === IAsset.Type.ALBUM) {
            // complete cvModel
            const albums = this.collections.albums;
            const targetAlbum = albums.findOne({ id: process.targetId });
            // UPLOADING
            if (targetAlbum) {
              targetAlbum.status = IAlbum.Status.ACTIVE;
              albums.update(targetAlbum);
            }
          }

          if (process.target === IAsset.Type.CV_PREDICTION) {
            let cvpredictions = this.collections.cvPredictions;
            let cvprediction = cvpredictions.findOne({ id: process.targetId });

            if (cvprediction) {
              cvprediction.status = ICVPredictionStatus.DONE;
              cvpredictions.update(cvprediction);

              // update album status
              const albums = this.collections.albums;
              const album = albums.findOne({ id: cvprediction.output });
              if (album) {
                albums.update(Object.assign(album, { status: config.album.status.values.ACTIVE }));
              }
            }
          }

          if (process.target === IAsset.Type.FLOW) {
            let replays = this.collections.replays;
            let flows = this.collections.flows;
            let flow = flows.findOne({ id: process.targetId });
            let replay = replays.findOne({ flowId: process.targetId });

            if (replay) {
              replay.status = config.replay.status.values.DONE;
              replays.update(replay);
            }
            if (flow) {
              flow.status = IFlow.Status.DONE;
              flows.update(flow);
            }
          }

          if (process.target === IAsset.Type.REPLAY) {
            let replays = this.collections.replays;
            let flows = this.collections.flows;
            let replay = replays.findOne({ id: process.targetId });
            let flow = flows.findOne({ id: replay.flowId });

            replay.status = config.replay.status.values.DONE;
            replays.update(replay);
            flow.status = IFlow.Status.DONE;
            flows.update(flow);
          }

          if (process.target === IAsset.Type.OPTIMIZATION) {
            let optimizations = this.collections.optimizations;
            let optimization = optimizations.findOne({ id: process.targetId });

            if (optimization) {
              optimization.status = IOptimization.OptimizationStatus.DONE;
              optimizations.update(optimization);
            }
            // update model status
            /*const models = this.collections.models;
            const model = models.findOne({id: optimization.outputModelId});
            if (model) {
              models.update(Object.assign(model, {status: config.album.status.values.ACTIVE}));
            }*/
          }

          if (process.target === IAsset.Type.DIAA) {
            let diaas = this.collections.diaas;
            let diaa = diaas.findOne({ id: process.targetId });

            if (diaa) {
              diaa.status = diaa.status === IDIAA.Status.CHECKING
                ? IDIAA.Status.CHECKED
                : IDIAA.Status.DONE;
              diaas.update(diaa);
            }
            // update model status
            /*const models = this.collections.models;
            const model = models.findOne({id: optimization.outputModelId});
            if (model) {
              models.update(Object.assign(model, {status: config.album.status.values.ACTIVE}));
            }*/
          }

          if (process.target === IAsset.Type.EXPERIMENT) {
            let experiments = this.collections.experiments;
            let experiement = experiments.findOne({ id: process.targetId });

            if (experiement) {
              experiement.status = IExperiment.Status.COMPLETED;
              experiments.update(experiement);
            }
          }

          if (process.target === IAsset.Type.ONLINE_API) {
            let apis = this.collections.apis;
            let api = apis.findOne({ id: process.targetId });

            if (api) {
              api.status = IOnlineAPI.Status.ACTIVE;
              apis.update(api);
            }
          }

          if (process.target === IAsset.Type.SCRIPT_DEPLOYMENT) {
            const scriptDeployments = this.collections.scriptDeployments;
            const sd = scriptDeployments.findOne({ id: process.targetId });

            sd.status = IScriptDeployment.Status.READY;
            scriptDeployments.update(sd);
          }

          processes.update(process);
        }

        return process;
      });
    }, PROCESS_TICK_INTERVAL); // milliseconds
  }

  static getRandomInt(min, max) {
    return Math.floor(Math.random() * (max - min)) + min;
  }

  static random(min, max) {
    return Math.random() * (max - min) + min;
  }

  static shuffleArray(oldArray) {
    let array = oldArray.slice(0),
      j = 0,
      temp = null;

    for (let i = array.length - 1; i > 0; i -= 1) {
      j = Math.floor(Math.random() * (i + 1));
      temp = array[i];
      array[i] = array[j];
      array[j] = temp;
    }
    return array;
  }
}

function readFileAsString(file: File): Observable<string> {
  return new Observable<string>(observer => {
    const fileReader = new FileReader();

    fileReader.onprogress = () => { /* pass */
    };

    fileReader.onerror = () => {
      observer.error(this._error('Error reading file'));
    };

    fileReader.onload = () => {
      // TODO: here we need to implement some compression process
      observer.next(fileReader.result);
      observer.complete();
    };

    fileReader.readAsBinaryString(file);
  });
}

declare module '../core/services/event.service' {
  export namespace IEvent {
    export const enum Type {
      CLEAN_MOCKS_REQUEST = 'CLEAN_MOCKS_REQUEST',
    }
  }
}
