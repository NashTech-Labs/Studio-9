import { ModuleWithProviders, NgModule } from '@angular/core';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { BrowserModule } from '@angular/platform-browser';
import { RouterModule } from '@angular/router';

import { forkJoin } from 'rxjs/observable/forkJoin';

import { maybe } from '../../lib/maybe';
import { AlbumsModule } from '../albums/albums.module';
import { ChartsModule } from '../charts/charts.module';
import config from '../config';
import { CoreUIModule } from '../core-ui/core-ui.module';
import { CoreModule } from '../core/core.module';
import { ASSET_BASE_ROUTE, IAsset, IAssetReference, TObjectId } from '../core/interfaces/common.interface';
import { Feature } from '../core/interfaces/feature-toggle.interface';
import { IEvent } from '../core/services/event.service';
import { EXPERIMENT_TYPES, ExperimentType, ExperimentTypeDefinition } from '../experiments/experiment.interfaces';
import { ExperimentService } from '../experiments/experiment.service';
import { ExperimentsModule } from '../experiments/experiments.module';
import { LIBRARY_SECTIONS, LibrarySectionDefinition } from '../library/library.interface';
import { TablesModule } from '../tables/tables.module';

import { AugmentationSummaryComponent } from './augmentation-summary.component';
import { CommonTrainParamsComponent } from './common-train-params.component';
import { ConfusionChartComponent } from './confusion-chart.component';
import { ConfusionMatrixComponent } from './confusion-matrix.component';
import { ICVArchitecture, ICVClassifier, ICVDecoder, ICVDetector } from './cv-architecture.interfaces';
import { CVArchitectureService } from './cv-architecture.service';
import { CvModelCreateStepAdvancedComponent } from './cv-model-create-step-advanced.component';
import { CvModelCreateStepComponent } from './cv-model-create-step.component';
import { CVModelCreateComponent } from './cv-model-create.component';
import { CvModelExperimentStepViewComponent } from './cv-model-experiment-step-view.component';
import { CVModelSummaryComponent } from './cv-model-summary.component';
import { CVModelTimeSpentSummaryComponent } from './cv-model-time-spent-summary.component';
import { CVModelUploadModalComponent } from './cv-model-upload-modal.component';
import { CVModelViewEmbedComponent } from './cv-model-view-embed.component';
import { CVModelViewComponent } from './cv-model-view.component';
import { CVModelType, ICVModel } from './cv-model.interface';
import { CVModelService } from './cv-model.service';
import { CVPrimitiveParamsComponent } from './cv-primitive-params.component';
import { CVTLTrainExperimentViewComponent } from './cvtl-train-experiment-view.component';
import { ICVTLTrainPipeline, ICVTLTrainResult } from './cvtl-train.interfaces';
import { TrainLabelComponent } from './label.component';
import { LabelsOfInterestComponent } from './labels-of-interest.component';
import { ModelCreateAdvancedComponent } from './model-create-advanced.component';
import { ModelCreateComponent } from './model-create.component';
import { ModelEvaluationSummaryComponent } from './model-evaluation-summary.component';
import { ModelPipelineSummaryComponent } from './model-pipeline-summary.component';
import { ModelSummaryComponent } from './model-summary.component';
import { ModelTrainSummaryComponent } from './model-train-summary.component';
import { ModelViewAdvancedComponent } from './model-view-advanced.component';
import { ModelViewEmbedComponent } from './model-view-embed.component';
import { ModelViewComponent } from './model-view.component';
import { ITabularModel, ITabularTrainPipeline, ITabularTrainResult } from './model.interface';
import { ModelService } from './model.service';
import { ROCChartDirective } from './roc-chart.directive';
import { TabularTrainExperimentViewComponent } from './tabular-train-experiment-view.component';
import { TechniquesChartComponent } from './techniques-chart.component';
import { TrainContextComponent } from './train-context.component';
import { TrainCreateComponent } from './train-create.component';
import { trainConfig } from './train.config';
import { isTLModelType } from './train.helpers';
import { trainModuleAssetURLMap } from './train.routes';

@NgModule({
  imports: [
    RouterModule,
    BrowserModule,
    FormsModule,
    ReactiveFormsModule,
    // deep cortex modules
    CoreModule,
    ChartsModule,
    CoreUIModule,
    TablesModule,
    AlbumsModule,
    ExperimentsModule,
  ],
  declarations: [
    TrainCreateComponent,
    TrainContextComponent,
    ModelCreateComponent,
    ModelCreateAdvancedComponent,
    ModelViewComponent,
    ModelViewAdvancedComponent,
    ModelSummaryComponent,
    ModelPipelineSummaryComponent,
    ModelTrainSummaryComponent,
    CVModelCreateComponent,
    CVModelViewComponent,
    ModelViewEmbedComponent,
    CVModelSummaryComponent,
    ConfusionChartComponent,
    ModelEvaluationSummaryComponent,
    ROCChartDirective,
    TechniquesChartComponent,
    TrainLabelComponent,
    CvModelCreateStepComponent,
    CvModelCreateStepAdvancedComponent,
    ConfusionMatrixComponent,
    CVModelUploadModalComponent,
    AugmentationSummaryComponent,
    CVModelTimeSpentSummaryComponent,
    CVPrimitiveParamsComponent,
    CVTLTrainExperimentViewComponent,
    CvModelExperimentStepViewComponent,
    CVModelViewEmbedComponent,
    TabularTrainExperimentViewComponent,
    CommonTrainParamsComponent,
    LabelsOfInterestComponent,
  ],
  exports: [
    ModelViewEmbedComponent,
    ModelSummaryComponent,
    CVModelSummaryComponent,
    TrainLabelComponent,
    AugmentationSummaryComponent,
    CVModelTimeSpentSummaryComponent,
    CVPrimitiveParamsComponent,
    CVModelViewEmbedComponent,
    ConfusionMatrixComponent,
    LabelsOfInterestComponent,
  ],
  entryComponents: [
    CVModelUploadModalComponent,
    CVModelCreateComponent,
    CVTLTrainExperimentViewComponent,
    CVModelViewComponent,
    ModelCreateComponent,
    TabularTrainExperimentViewComponent,
    ModelViewComponent,
  ],
})
export class TrainModule {
  static forRoot(): ModuleWithProviders {
    type CVAsyncData = [ICVArchitecture[], ICVClassifier[], ICVDetector[], ICVDecoder[]];

    return {
      ngModule: TrainModule,
      providers: [
        ModelService,
        CVModelService,
        CVArchitectureService,
        {
          provide: ASSET_BASE_ROUTE,
          useValue: trainModuleAssetURLMap,
          multi: true,
        },
        {
          provide: LIBRARY_SECTIONS,
          deps: [CVModelService, CVArchitectureService],
          useFactory: (
            service: CVModelService,
            architectureService: CVArchitectureService,
          ): LibrarySectionDefinition<ICVModel, CVAsyncData> => {
            const getNameById = (collection: (ICVArchitecture | ICVClassifier | ICVDetector | ICVDecoder)[], id) => {
              return collection
                .filter(_ => _.id === id)
                .map(_ => _.name)[0];
            };

            const getModelUTLPName = (model: ICVModel, [architectures]: CVAsyncData) => {
              return isTLModelType(model.modelType) ? getNameById(architectures, model.modelType.architecture) : 'n/a';
            };

            const describeModelType = (model: ICVModel, [, classifiers, detectors, decoders]: CVAsyncData): string => {
              if (model.modelType.type === CVModelType.Type.TL) {
                if (model.modelType.tlType === CVModelType.TLType.CLASSIFICATION) {
                  return getNameById(classifiers, (<CVModelType.TLClassifier> model.modelType).classifierType) + ' ' +
                    trainConfig.cvModel.tlType.labels[CVModelType.TLType.CLASSIFICATION];
                }
                if (model.modelType.tlType === CVModelType.TLType.LOCALIZATION) {
                  return getNameById(detectors, (<CVModelType.TLDetector> model.modelType).detectorType) + ' ' +
                    trainConfig.cvModel.tlType.labels[CVModelType.TLType.LOCALIZATION];
                }
                if (model.modelType.tlType === CVModelType.TLType.AUTOENCODER) {
                  return getNameById(decoders, (<CVModelType.TLDecoder> model.modelType).decoderType) + ' ' +
                    trainConfig.cvModel.tlType.labels[CVModelType.TLType.AUTOENCODER];
                }
              } else if (model.modelType.type === CVModelType.Type.CUSTOM) {
                let typeOfClassReference = model.modelType.classReference;
                return `${typeOfClassReference.moduleName}/${typeOfClassReference.className}`
                  + ('packageId' in typeOfClassReference ? ` (${typeOfClassReference.packageId})` : '');
              }

              return '';
            };

            return {
              service,
              assetType: IAsset.Type.CV_MODEL,
              icon: 'glyphicon glyphicon-blackboard',
              inProjects: true,
              actions: {},
              viewComponent: CVModelViewComponent,
              reloadOn: IEvent.Type.UPDATE_CV_MODEL_LIST,
              statusesDefinition: config.cvModel.status,
              completeStatus: ICVModel.Status.ACTIVE,
              features: [Feature.TRAIN_MODULE, Feature.TRAIN_CV_MODELS],
              loadAsyncData: () => forkJoin(
                architectureService.listArchitectures(),
                architectureService.listClassifiers(),
                architectureService.listDetectors(),
                architectureService.listDecoders(),
              ),
              columns: [
                {name: 'UTLP', get: getModelUTLPName},
                {name: 'Type', get: describeModelType},
              ],
              selectorColumns: [
                {name: 'UTLP', get: getModelUTLPName},
                {name: 'Type', get: describeModelType},
              ],
              sidebarActions: [
                {
                  caption: 'Import CV Model',
                  modalClass: CVModelUploadModalComponent,
                },
              ],
            };
          },
          multi: true,
        },
        {
          provide: LIBRARY_SECTIONS,
          deps: [ModelService],
          useFactory: (service: ModelService): LibrarySectionDefinition<ITabularModel> => {
            return {
              service,
              assetType: IAsset.Type.MODEL,
              icon: 'iconapp iconapp-models',
              inProjects: true,
              actions: {},
              viewComponent: ModelViewComponent,
              reloadOn: IEvent.Type.UPDATE_MODEL_LIST,
              statusesDefinition: config.model.status,
              completeStatus: ITabularModel.Status.ACTIVE,
              features: [Feature.TRAIN_MODULE, Feature.TRAIN_MODELS],
              sharable: true,
            };
          },
          multi: true,
        },
        {
          provide: EXPERIMENT_TYPES,
          useFactory: (): ExperimentTypeDefinition => {
            return {
              type: ExperimentType.CVTLTrain,
              name: 'CV TL Train',
              pipelineComponent: CVModelCreateComponent,
              resultComponent: CVTLTrainExperimentViewComponent,
              features: [Feature.TRAIN_MODULE, Feature.TRAIN_CV_MODELS],
            };
          },
          multi: true,
        },
        {
          provide: EXPERIMENT_TYPES,
          useFactory: (): ExperimentTypeDefinition => {
            return {
              type: ExperimentType.TabularTrain,
              name: 'Tabular Train',
              pipelineComponent: ModelCreateComponent,
              resultComponent: TabularTrainExperimentViewComponent,
              features: [Feature.TRAIN_MODULE, Feature.TRAIN_MODELS],
            };
          },
          multi: true,
        },
      ],
    };
  }
}

// Features
declare module '../core/interfaces/feature-toggle.interface' {
  export const enum Feature {
    TRAIN_MODULE = 'TRAIN_MODULE',
    TRAIN_MODELS = 'TRAIN_MODELS',
    TRAIN_CV_MODELS = 'TRAIN_CV_MODELS',
  }
}

// Experiment type
declare module '../experiments/experiment.interfaces' {
  export const enum ExperimentType {
    CVTLTrain = 'CVTLTrain',
    TabularTrain = 'TabularTrain',
  }
}

ExperimentService.registerChildAssetsExtractor(ExperimentType.CVTLTrain, experiment => {
  const pipeline = <ICVTLTrainPipeline> experiment.pipeline;
  const result = maybe(<ICVTLTrainResult> experiment.result);

  function album(id: TObjectId): IAssetReference {
    return { id, type: IAsset.Type.ALBUM };
  }

  function table(id: TObjectId): IAssetReference {
    return { id, type: IAsset.Type.TABLE };
  }

  function model(id: TObjectId): IAssetReference {
    return { id, type: IAsset.Type.CV_MODEL };
  }

  return maybe.seq([
    // inputs
    maybe(pipeline.step1.featureExtractorModelId).map(model),
    maybe(pipeline.step1.input).map(album),
    maybe(pipeline.step1.testInput).map(album),
    ...maybe(pipeline.step2).map(step2 => [
      maybe(step2.input).map(album),
      maybe(step2.testInput).map(album),
    ]).get() || [],
    // results
    ...result.map(result => [
      maybe(result.step1.cvModelId).map(model),
      maybe(result.step1.output).map(album),
      maybe(result.step1.testOutput).map(album),
      maybe(result.step1.augmentedSampleAlbum).map(album),
      maybe(result.step1.probabilityPredictionTableId).map(table),
      maybe(result.step1.testProbabilityPredictionTableId).map(table),
      ...maybe(result.step2).map(step2 => [
        maybe(step2.cvModelId).map(model),
        maybe(step2.output).map(album),
        maybe(step2.testOutput).map(album),
        maybe(step2.augmentedSampleAlbum).map(album),
        maybe(step2.probabilityPredictionTableId).map(table),
        maybe(step2.testProbabilityPredictionTableId).map(table),
      ]).get() || [],
    ]).get() || [],
  ]);
});

ExperimentService.registerChildAssetsExtractor(ExperimentType.TabularTrain, experiment => {
  const pipeline = <ITabularTrainPipeline> experiment.pipeline;
  const result = maybe(<ITabularTrainResult> experiment.result);

  function table(id: TObjectId): IAssetReference {
    return { id, type: IAsset.Type.TABLE };
  }

  function model(id: TObjectId): IAssetReference {
    return { id, type: IAsset.Type.MODEL };
  }

  return maybe.seq([
    // inputs
    maybe(pipeline.input).map(table),
    maybe(pipeline.holdOutInput).map(table),
    maybe(pipeline.outOfTimeInput).map(table),
    // results
    ...result.map(result => [
      maybe(result.modelId).map(model),
      maybe(result.output).map(table),
      maybe(result.holdOutOutput).map(table),
      maybe(result.outOfTimeOutput).map(table),
    ]).get() || [],
  ]);
});
