import {
  Component,
  ComponentFactoryResolver,
  EventEmitter,
  Inject,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  ViewChild,
  ViewContainerRef,
} from '@angular/core';
import { Router } from '@angular/router';

import * as _ from 'lodash';
import { Observable } from 'rxjs/Observable';

import { IAlbum } from '../../albums/album.interface';
import { AlbumService } from '../../albums/album.service';
import { IFlow } from '../../compose/flow.interface';
import { FlowService } from '../../compose/flow.service';
import config from '../../config';
import { ModalService } from '../../core-ui/services/modal.service';
import { BinaryDataset } from '../../datasets/dataset.interfaces';
import { BinaryDatasetService } from '../../datasets/dataset.service';
import { deployConfig } from '../../deploy/deploy.config';
import { IOnlineTriggeredJob } from '../../deploy/online-job.interface';
import { OnlineJobService } from '../../deploy/online-job.service';
import { IScriptDeployment } from '../../deploy/script-deployment.interface';
import { ScriptDeploymentService } from '../../deploy/script-deployment.service';
import { IS9Project } from '../../develop/s9-project.interfaces';
import { S9ProjectService } from '../../develop/s9-project.service';
import { IDIAA } from '../../diaa/diaa.interface';
import { DIAAService } from '../../diaa/diaa.service';
import { IExperiment } from '../../experiments/experiment.interfaces';
import { ExperimentService } from '../../experiments/experiment.service';
import { LIBRARY_SECTIONS, LibrarySectionDefinition } from '../../library/library.interface';
import { ProjectService } from '../../library/project.service';
import { IOptimization } from '../../optimize/optimization.interface';
import { OptimizationService } from '../../optimize/optimization.service';
import { Pipeline } from '../../pipelines/pipeline.interfaces';
import { PipelineService } from '../../pipelines/pipeline.service';
import { ICVPrediction } from '../../play/cv-prediction.interface';
import { CVPredictionService } from '../../play/cv-prediction.service';
import { IPrediction } from '../../play/prediction.interface';
import { PredictionService } from '../../play/prediction.service';
import { IReplay } from '../../play/replay.interface';
import { ReplayService } from '../../play/replay.service';
import { AclService } from '../../services/acl.service';
import { ITable } from '../../tables/table.interface';
import { TableService } from '../../tables/table.service';
import { ICVModel } from '../../train/cv-model.interface';
import { CVModelService } from '../../train/cv-model.service';
import { ITabularModel } from '../../train/model.interface';
import { ModelService } from '../../train/model.service';
import { ActivityObserver } from '../../utils/activity-observer';
import { bulkAction } from '../../utils/observable';
import { IDashboard } from '../../visualize/dashboard.interface';
import { DashboardService } from '../../visualize/dashboard.service';
import { IAsset, TObjectId } from '../interfaces/common.interface';
import { Feature } from '../interfaces/feature-toggle.interface';
import { IProject } from '../interfaces/project.interface';
import { AssetURLService } from '../services/asset-url.service';
import { NotificationService } from '../services/notification.service';
import { UserService } from '../services/user.service';

import { CloneModalComponent } from './clone-modal.component';
import { LibrarySelectorComponent } from './library-selector.component';
import { ProjectLinkModalComponent } from './project-link-modal.component';
import { SaveToLibraryModalComponent } from './save-to-library.modal.component';
import { ShareModalComponent } from './share-modal.component';


@Component({
  selector: 'asset-operations',
  template: `
    <div class="operations-toolbar row row-flex pt5 pb5" style="align-items: flex-end; flex-wrap: wrap;">
      <div class="col-xs-12 flex-static">
        <!-- Common Buttons -->
        <ul class="asset-btn-panel nav nav-pills">
          <ng-container *ngVar="sections[type] && sections[type].service.save && selectedItems.length === 1 && selectedItems[0].inLibrary === false as needsSaveToLibrary">
            <li *ngIf="needsSaveToLibrary" class="nav-item">
              <a class="nav-link link-colorless" (click)="saveToLibrary()">
                <i class="imgaction glyphicon glyphicon-book center-block"></i>
                <div>Save To Library</div>
              </a>
            </li>
            <ng-container *featureToggle="'${Feature.LIBRARY_PROJECTS}'">
              <ng-container *ngIf="!needsSaveToLibrary && sections[type] && sections[type].inProjects">
                <li *ngIf="!project"
                  class="nav-item" [ngClass]="{'disabled': disabledAddToProject}">
                  <a class="nav-link link-colorless" (click)="disabledAddToProject || addToProject()">
                    <i class="imgaction imgaction-projects center-block"></i>
                    <div>Add to Project</div>
                  </a>
                </li>
                <li *ngIf="project" class="nav-item"
                  [ngClass]="{'disabled': disabledRemoveFromProject || (projectSavingObserver.active | async)}">
                  <a class="nav-link link-colorless"
                    (click)="disabledRemoveFromProject || projectSavingObserver.isActive || removeFromProject()">
                    <i class="imgaction imgaction-trash center-block"></i>
                    <div>Remove from Project</div>
                  </a>
                </li>
              </ng-container>
            </ng-container>
          </ng-container>
          <li *ngIf="type !== config.asset.values.FLOW" class="nav-item"
            [ngClass]="{'disabled': disabledPreview}" [routerLinkActive]="['active']"
            [routerLinkActiveOptions]="{exact: true}"
            [ngSwitch]="selectedItems.length">
            <a *ngSwitchCase="1" class="nav-link link-colorless"
              [routerLink]="previewRoute">
              <i class="imgaction imgaction-preview center-block"></i>
              <div>preview</div>
            </a>
            <a *ngSwitchDefault class="nav-link link-colorless">
              <i class="imgaction imgaction-preview center-block"></i>
              <div>preview</div>
            </a>
          </li>
          <li class="nav-item" [ngClass]="{'disabled': disabledTrash}">
            <a class="nav-link link-colorless" (click)="disabledTrash || trash(selectedItems)">
              <i class="imgaction imgaction-trash center-block"></i>
              <div>Trash</div>
            </a>
          </li>
          <li class="nav-item" [ngClass]="{'disabled': disabledShare}">
            <a class="nav-link link-colorless" (click)="disabledShare || share()">
              <i class="imgaction imgaction-share center-block"></i>
              <div>share</div>
            </a>
          </li>
          <li class="nav-item" [ngClass]="{'disabled': disabledClone}">
            <a class="nav-link link-colorless" (click)="disabledClone || clone()">
              <i class="imgaction imgaction-clone center-block"></i>
              <div>clone</div>
            </a>
          </li>
          <li *ngIf="type === config.asset.values.TABLE
            || type === config.asset.values.DATASET
            || type === config.asset.values.CV_MODEL
            || type === config.asset.values.SCRIPT_DEPLOYMENT"
            class="nav-item" [ngClass]="{'disabled': disabledDownload || (downloadObserver.active | async)}">
            <a class="nav-link link-colorless" (click)="disabledDownload || downloadObserver.isActive || download()">
              <i class="imgaction imgaction-download center-block"></i>
              <div>download</div>
            </a>
          </li>
          <li *ngIf="type === config.asset.values.ALBUM"
            class="nav-item" [ngClass]="{'disabled': disabledDownload || (downloadObserver.active | async)}">
            <a class="nav-link link-colorless" (click)="disabledDownload || downloadObserver.isActive || download()">
              <i class="imgaction imgaction-download center-block"></i>
              <div>download labels</div>
            </a>
          </li>
          <ng-template [ngIf]="type === config.asset.values.FLOW">
            <li class="nav-item"
              [ngClass]="{'disabled': disabledVisualize}" [routerLinkActive]="['active']" [ngSwitch]="selectedItems.length">
              <a *ngSwitchCase="1" class="nav-link link-colorless"
                [routerLink]="['/desk', 'flows', selectedItems[0].id, 'graph']">
                <i class="imgaction imgaction-visualize center-block"></i>
                <div>visualize</div>
              </a>
              <a *ngSwitchDefault class="nav-link link-colorless">
                <i class="imgaction imgaction-visualize center-block"></i>
                <div>visualize</div>
              </a>
            </li>
            <li class="nav-item" [ngClass]="{'disabled': disabledAddTable}">
              <a class="nav-link link-colorless" (click)="disabledAddTable || showLibrarySelector()">
                <i class="imgaction imgaction-table center-block"></i>
                <div>add table</div>
              </a>
            </li>
          </ng-template>
          <li class="nav-item" *ngIf="!disabledRefresh" [ngClass]="{'disabled': disabledRefresh}">
            <a class="nav-link link-colorless" (click)="disabledRefresh || refresh()">
              <i class="imgaction imgaction-refresh center-block"></i>
              <div>refresh</div>
            </a>
          </li>
          <li class="nav-item" [ngClass]="{'disabled': disabledInfo}" [routerLinkActive]="['active']">
            <ng-template [ngIf]="!disabledInfo">
              <a *ngIf="type === config.asset.values.FLOW"
                class="nav-link link-colorless"
                [routerLink]="['/desk', 'flows', selectedItems[0].id, 'info']">
                <i class="imgaction imgaction-info center-block"></i>
                <div>info</div>
              </a>
              <a *ngIf="type === config.asset.values.TABLE || type === config.asset.values.ALBUM"
                class="nav-link link-colorless"
                (click)="onInfoClick()">
                <i class="imgaction imgaction-info center-block"></i>
                <div>info</div>
              </a>
            </ng-template>
            <a *ngIf="disabledInfo" class="nav-link link-colorless">
              <i class="imgaction imgaction-info center-block"></i>
              <div>info</div>
            </a>
          </li>

          <li class="nav-item" [ngClass]="{'disabled': disabledEdit}" [routerLinkActive]="['active']">
            <ng-template [ngIf]="!disabledEdit">
              <a *ngIf="type === config.asset.values.PIPELINE"
                class="nav-link link-colorless"
                [routerLink]="['/desk', 'pipelines', selectedItems[0].id, 'edit']">
                <i class="imgaction glyphicon glyphicon-pencil center-block"></i>
                <div>edit</div>
              </a>
              <a *ngIf="type === config.asset.values.DASHBOARD"
                class="nav-link link-colorless"
                [routerLink]="['/desk', 'visualize', 'dashboards', selectedItems[0].id, 'edit']">
                <i class="imgaction glyphicon glyphicon-pencil center-block"></i>
                <div>edit</div>
              </a>
              <a *ngIf="type === config.asset.values.FLOW"
                class="nav-link link-colorless"
                [routerLink]="['/desk', 'flows', selectedItems[0].id, 'edit']">
                <i class="imgaction glyphicon glyphicon-pencil center-block"></i>
                <div>edit</div>
              </a>
              <a *ngIf="type === config.asset.values.ALBUM"
                class="nav-link link-colorless"
                [routerLink]="['/desk', 'albums', selectedItems[0].id, 'edit']">
                <i class="imgaction glyphicon glyphicon-pencil center-block"></i>
                <div>edit</div>
              </a>
            </ng-template>
            <a *ngIf="disabledEdit" class="nav-link link-colorless">
              <i class="imgaction glyphicon glyphicon-pencil center-block"></i>
              <div>edit</div>
            </a>
          </li>
          <ng-template [ngIf]="sections[type]">
            <li *ngFor="let operation of sections[type].bulkOperations"
              class="nav-item"
              [ngClass]="{'disabled': !(selectedItems | apply: _operationIsAvailable: operation)}"
              [routerLinkActive]="['active']">
              <a
                class="nav-link link-colorless"
                [title]="operation.description || operation.name"
                (click)="doCustomOperation(selectedItems, operation)"
              >
                <i [class]="operation.iconClass + ' imgaction center-block'"></i>
                <div>{{operation.name}}</div>
              </a>
            </li>
            <li *ngFor="let action of sections[type].actions | keys"
              class="nav-item"
              [ngClass]="{
                'disabled': selectedItems.length !== 1,
                'active': _isLinkActive(selectedItems | apply: _actionLink: sections[type]: action)
              }"
              [title]="sections[type].actions[action].title || ''"
            >
              <a class="nav-link link-colorless"
                [routerLink]="selectedItems | apply: _actionLink: sections[type]: action"
                [ngClass]="{'disable-nav-link': selectedItems.length !== 1}"
                [tabIndex]="selectedItems.length !== 1 ? -1:0"
              >
                <i [class]="sections[type].actions[action].iconClass + ' imgaction center-block'"></i>
                <div>{{sections[type].actions[action].name}}</div>
              </a>
            </li>
          </ng-template>
        </ul>
        <!-- End Common Buttons -->
      </div>
      <div class="col-xs-12 flex-rubber visible-dropdown">
        <ng-content></ng-content>
      </div>
    </div>
    <share-modal #shareModal></share-modal>
    <clone-modal #cloneModal></clone-modal>
    <project-link-modal #projectLinkModal (onComplete)="resetSelection()"></project-link-modal>
    <library-selector
      #addTableSelector
      *ngIf="type === config.asset.values.FLOW"
      [hidden]="true"
      (valueChange)="$event && addTable($event.object)"
      [available]="[config.asset.values.TABLE]"
      [caption]="'Add Table To A Flow'"
    ></library-selector>
    <save-to-library-modal #saveToLibraryModal
      *ngIf="sections[type]"
      [service]="sections[type]?.service"
    ></save-to-library-modal>
  `,
})
export class AssetOperationsComponent implements OnChanges {
  @Input() type: IAsset.Type = IAsset.Type.TABLE;
  @Input() selectedItems: IAsset[] = [];
  @Input() project: IProject;
  @Input() scope: null | 'all' | 'personal' | 'shared';
  @Output() selectedItemsChange = new EventEmitter<IAsset[]>();
  @Output() onAddTable = new EventEmitter<ITable>();
  @Output() onDelete = new EventEmitter<TObjectId[]>();
  @Output() onRefresh = new EventEmitter<any>();
  @Output() onInfo = new EventEmitter<any>();
  @ViewChild('shareModal') readonly shareModal: ShareModalComponent;
  @ViewChild('cloneModal') readonly cloneModal: CloneModalComponent;
  @ViewChild('addTableSelector') readonly addTableSelector: LibrarySelectorComponent;
  @ViewChild('projectLinkModal') readonly projectLinkModal: ProjectLinkModalComponent;
  @ViewChild('saveToLibraryModal') readonly saveToLibraryModal: SaveToLibraryModalComponent<IAsset>;

  config = config;
  disabledPreview: boolean = false;
  disabledTrash: boolean = false;
  disabledAddToProject: boolean = true;
  disabledRemoveFromProject: boolean = false;
  disabledShare: boolean = true;
  disabledClone: boolean = true;
  disabledDownload: boolean = true;
  disabledRefresh: boolean = true;
  disabledInfo: boolean = true;
  disabledVisualize: boolean = false;
  disabledAddTable: boolean = true;
  disabledEdit: boolean = true;
  previewRoute: string[] = ['/desk'];

  readonly projectSavingObserver = new ActivityObserver();
  readonly downloadObserver = new ActivityObserver();
  readonly sections: { [assetType: string]: LibrarySectionDefinition<IAsset> } = {};
  private readonly _editableAssetsTypes = [
    IAsset.Type.PIPELINE,
    IAsset.Type.DASHBOARD,
    IAsset.Type.FLOW,
    IAsset.Type.ALBUM,
  ];

  constructor(
    private flows: FlowService,
    private tables: TableService,
    private models: ModelService,
    private cvModels: CVModelService,
    private predictions: PredictionService,
    private binaryDatasets: BinaryDatasetService,
    private replays: ReplayService,
    private notifications: NotificationService,
    private user: UserService,
    private acl: AclService,
    private projects: ProjectService,
    private modals: ModalService,
    private albums: AlbumService,
    private cvpredictions: CVPredictionService,
    private optimizations: OptimizationService,
    private dashboards: DashboardService,
    private diaaService: DIAAService,
    private jobs: OnlineJobService,
    private sdService: ScriptDeploymentService,
    private viewContainer: ViewContainerRef,
    private componentFactoryResolver: ComponentFactoryResolver,
    private s9Projects: S9ProjectService,
    private _urlService: AssetURLService,
    private router: Router,
    private pipelines: PipelineService,
    private experimentService: ExperimentService,
    private userService: UserService,
    @Inject(LIBRARY_SECTIONS) sections: LibrarySectionDefinition<IAsset>[],
  ) {
    this.sections = _.keyBy(sections, _ => _.assetType);
    this.updateDisabledFlags();
  }

  showLibrarySelector() {
    this.addTableSelector.show();
  }

  onInfoClick() {
    this.onInfo.emit();
  }

  addTable(table: ITable) {
    this.onAddTable.emit(table);
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['selectedItems'] && changes['selectedItems'].currentValue !== changes['selectedItems'].previousValue) {
      this.selectedItems = this.selectedItems.filter(_ => _);
      this.updateDisabledFlags();
      if (this.type) {
        this.generatePreviewLink();
      }
    }
  }

  updateDisabledFlags() {
    const readOnly = !!this.selectedItems.find((item) => {
      switch (this.type) {
        case IAsset.Type.TABLE:
          return !this.acl.canUpdateTable(<ITable> item);
        case IAsset.Type.FLOW:
          return !this.acl.canUpdateFlow(<IFlow> item);
        case IAsset.Type.MODEL:
          return !this.acl.canUpdateModel(<ITabularModel> item);
        case IAsset.Type.PREDICTION:
          return !this.acl.canUpdatePrediction(<IPrediction> item);
        case IAsset.Type.CV_MODEL:
          return !this.acl.canUpdateCVModel(<ICVModel> item);
        case IAsset.Type.CV_PREDICTION:
          return !this.acl.canUpdateCVPrediction(<ICVPrediction> item);
        case IAsset.Type.REPLAY:
          return !this.acl.canUpdateReplay(<IReplay> item);
        case IAsset.Type.ALBUM:
          return !this.acl.canUpdateAlbum(<IAlbum> item);
        case IAsset.Type.OPTIMIZATION:
          return !this.acl.canUpdateOptimization(<IOptimization> item);
        case IAsset.Type.DASHBOARD:
          return !this.acl.canUpdateDashboard(<IDashboard> item);
        case IAsset.Type.ONLINE_JOB:
          return false;
        case IAsset.Type.ONLINE_API:
          return true;
        case IAsset.Type.SCRIPT_DEPLOYMENT:
          return !this.acl.canUpdateScriptDeployment(<IScriptDeployment> item);
        case IAsset.Type.S9_PROJECT:
          return !this.acl.canUpdates9Project(<IS9Project> item);
        case IAsset.Type.EXPERIMENT:
          return !this.acl.canUpdateExperiment(item as IExperiment);
        case IAsset.Type.PIPELINE:
          return !this.acl.canUpdatePipeline(<Pipeline> item);
        case IAsset.Type.DATASET:
          return !this.acl.canUpdateBinaryDataset(<BinaryDataset> item);
        default:
          throw new Error('Dunno what to do with this asset');
      }
    });
    this.disabledPreview = this.selectedItems.length !== 1;
    this.disabledInfo = this.selectedItems.length !== 1
      || ![IAsset.Type.FLOW, IAsset.Type.TABLE, IAsset.Type.ALBUM].includes(this.type)
      || (
        this.type === IAsset.Type.ALBUM
        && (this.selectedItems[0] as IAlbum).status !== config.album.status.values.ACTIVE
      );
    this.disabledTrash = this.selectedItems.length < 1 || readOnly;
    this.disabledRemoveFromProject = this.selectedItems.length < 1 || readOnly;
    this.disabledVisualize = this.selectedItems.length !== 1;
    this.disabledClone = this.selectedItems.length !== 1 || !CloneModalComponent.canClone(this.type);
    this.disabledAddToProject = this.selectedItems.length < 1 || readOnly;
    this.disabledShare = this.selectedItems.length < 1 || readOnly || !this.isAssetTypeShareable(this.type);
    this.disabledDownload = this._isDisabledDownload();
    this.disabledAddTable = this.selectedItems.length !== 1 || readOnly || !this.onAddTable.observers.length;
    this.disabledEdit = this.selectedItems.length !== 1 || readOnly
      || !this._editableAssetsTypes.includes(this.type);
  }

  isAssetTypeShareable(assetType: IAsset.Type): boolean {
    const nonShareableAssets = [
      IAsset.Type.ONLINE_JOB,
      IAsset.Type.S9_PROJECT,
    ];

    return !nonShareableAssets.includes(assetType);
  }

  generatePreviewLink() {
    // TODO use AssetUrlService
    if (this.selectedItems[0]) {
      switch (this.type) {
        case IAsset.Type.TABLE:
        case IAsset.Type.MODEL:
        case IAsset.Type.CV_MODEL:
        case IAsset.Type.DATASET:
          const entitySection = config.asset.aliasesPlural[this.type];
          if (this.scope) {
            this.previewRoute = ['/desk', 'library', entitySection, this.scope, this.selectedItems[0].id];
          } else {
            this.previewRoute = ['/desk', 'library', entitySection, this.selectedItems[0].id];
          }
          break;
        case IAsset.Type.FLOW:
          this.previewRoute = ['/desk', 'flows', this.selectedItems[0].id];
          break;
        case IAsset.Type.REPLAY:
          this.previewRoute = ['/desk', 'play', 'replays', this.selectedItems[0].id];
          break;
        case IAsset.Type.PREDICTION:
          this.previewRoute = ['/desk', 'play', 'predictions', this.selectedItems[0].id];
          break;
        case IAsset.Type.CV_PREDICTION:
          this.previewRoute = ['/desk', 'play', 'cv-predictions', this.selectedItems[0].id];
          break;
        case IAsset.Type.ALBUM:
          this.previewRoute = ['/desk', 'albums', this.selectedItems[0].id];
          break;
        case IAsset.Type.OPTIMIZATION:
          this.previewRoute = ['/desk', 'optimization', this.selectedItems[0].id];
          break;
        case IAsset.Type.DASHBOARD:
          this.previewRoute = ['/desk', 'visualize', this.selectedItems[0].id];
          break;
        case IAsset.Type.DIAA:
          this.previewRoute = ['/desk', 'diaa', this.selectedItems[0].id];
          break;
        case IAsset.Type.ONLINE_JOB:
          this.previewRoute = ['/desk', 'deploy', 'online-job', this.selectedItems[0].id];
          break;
        case IAsset.Type.ONLINE_API:
          this.previewRoute = ['/desk', 'deploy', 'online-api', this.selectedItems[0].id];
          break;
        case IAsset.Type.S9_PROJECT:
          this.previewRoute = ['/desk', 'develop', 'projects', this.selectedItems[0].id];
          break;
        case IAsset.Type.SCRIPT_DEPLOYMENT:
          this.previewRoute = ['/desk', 'deploy', 'script-deployment', this.selectedItems[0].id];
          break;
        case IAsset.Type.PIPELINE:
          this.previewRoute = ['/desk', 'pipelines', this.selectedItems[0].id];
          break;
        case IAsset.Type.EXPERIMENT:
          this.previewRoute = ['/desk', 'experiments', this.selectedItems[0].id];
          break;
        default:
          this.notifications.create('Unknown Asset Type', config.notification.level.values.DANGER);
      }
    }
  }

  trash(items: IAsset[]) {
    const itemNames: string = items.map(items => items.name).join(', ');
    const itemTypeCaption = config.asset[items.length > 1 ? 'labelsPlural' : 'labels'][this.type] || 'item(s)';
    const confirmationMessage = `Are you sure you want to delete the following ${itemTypeCaption}: ${itemNames}?`;

    this.modals.confirm(confirmationMessage).filter(_ => _).flatMap(() => {
      const observables: Observable<any>[] = (<any[]> items).map((item: any): Observable<any> => {
        let observable: Observable<any>;

        switch (this.type) {
          case IAsset.Type.TABLE:
            observable = this.tables.delete(<ITable> item);
            break;
          case IAsset.Type.FLOW:
            observable = this.flows.delete(<IFlow> item);
            break;
          case IAsset.Type.MODEL:
            observable = this.models.delete(<ITabularModel> item);
            break;
          case IAsset.Type.CV_MODEL:
            observable = this.cvModels.delete(<ICVModel> item);
            break;
          case IAsset.Type.PREDICTION:
            observable = this.predictions.remove(<TObjectId> item.id);
            break;
          case IAsset.Type.CV_PREDICTION:
            observable = this.cvpredictions.remove(<TObjectId> item.id);
            break;
          case IAsset.Type.REPLAY:
            observable = this.replays.remove(<TObjectId> item.id);
            break;
          case IAsset.Type.ALBUM:
            observable = this.albums.delete(item);
            break;
          case IAsset.Type.OPTIMIZATION:
            observable = this.optimizations.delete(<IOptimization> item);
            break;
          case IAsset.Type.DASHBOARD:
            observable = this.dashboards.delete(<IDashboard> item);
            break;
          case IAsset.Type.DIAA:
            observable = this.diaaService.delete(<IDIAA> item);
            break;
          case IAsset.Type.ONLINE_JOB:
            observable = this.jobs.delete(<IOnlineTriggeredJob> item);
            break;
          case IAsset.Type.S9_PROJECT:
            observable = this.s9Projects.delete(<IS9Project> item);
            break;
          case IAsset.Type.SCRIPT_DEPLOYMENT:
            observable = this.sdService.delete(<IScriptDeployment> item);
            break;
          case IAsset.Type.PIPELINE:
            observable = this.pipelines.delete(<Pipeline> item);
            break;
          case IAsset.Type.EXPERIMENT:
            observable = this.experimentService.delete(item as IExperiment);
            break;
          case IAsset.Type.DATASET:
            observable = this.binaryDatasets.delete(<IOnlineTriggeredJob> item);
            break;
          default:
            this.notifications.create('Unknown Asset Type', config.notification.level.values.DANGER);
        }

        return observable;
      });

      return bulkAction(observables).map(results => {
        return results.map((result, index) => result ? items[index].id : null);
      });
    }).subscribe((ids: TObjectId[]) => {
      this.resetSelection();
      this.onDelete.emit(ids.filter(_ => !!_));
    });
  }

  removeFromProject(): void {
    let observable;
    observable = this.projects.unlinkAssets<IAsset>(this.type, this.project, this.selectedItems);

    this.projectSavingObserver.observe(observable).subscribe(() => this.resetSelection());
  }

  addToProject(): void {
    this.projectLinkModal.open(this.type, this.selectedItems);
  }

  saveToLibrary(): void {
    this.saveToLibraryModal.open(this.selectedItems[0]);
  }

  share() {
    if (this.selectedItems.length > 0) {
      this.shareModal.open(this.type, this.selectedItems);
    }
  }

  resetSelection() {
    this.selectedItemsChange.emit([]);
  }

  refresh() {
    this.onRefresh.emit();
  }

  clone() {
    if (this.selectedItems.length === 1) {
      this.cloneModal.open(this.type, this.selectedItems[0].id, this.selectedItems[0].name);
    }
  }

  download() {
    if (this.selectedItems[0]) {
      switch (this.type) {
        case IAsset.Type.TABLE:
          this.downloadObserver.observe(this.tables.download(this.selectedItems[0].id, this.user.token()));
          break;
        case IAsset.Type.ALBUM:
          this.downloadObserver.observe(this.albums.download(this.selectedItems[0].id, this.user.token()));
          break;
        case IAsset.Type.CV_MODEL:
          this.downloadObserver.observe(this.cvModels.download(this.selectedItems[0].id));
          break;
        case IAsset.Type.DATASET:
          this.downloadObserver.observe(this.binaryDatasets.downloadAllDatasetFiles(this.selectedItems[0].id, this.userService.token()));
          break;
        case IAsset.Type.SCRIPT_DEPLOYMENT:
          this.downloadObserver.observe(this.sdService.download(this.selectedItems[0].id));
          break;
      }
    }
  }

  doCustomOperation(items: IAsset[], operation: LibrarySectionDefinition.BulkOperation<IAsset>) {
    if (this._operationIsAvailable(items, operation)) {
      if (operation.onClick) {
        operation.onClick(items);
      }

      if (operation.modalClass) {
        const factory = this.componentFactoryResolver.resolveComponentFactory(operation.modalClass);
        const modalRef = this.viewContainer.createComponent(factory);
        modalRef.instance.open(items).subscribe(() => {
          modalRef.destroy();
          this.resetSelection();
        });
      }
    }
  }

  _operationIsAvailable = function(items: IAsset[], operation: LibrarySectionDefinition.BulkOperation<IAsset>): boolean {
    if (!items.length) {
      return false;
    }
    if (operation.isAvailable) {
      return operation.isAvailable(items);
    }

    return true;
  };

  _isLinkActive = (path: string[]): boolean => {
    return !!(path && path.length && this.router.isActive(this.router.createUrlTree(path), false));
  };

  _actionLink = function(selectedItems: IAsset[], section: LibrarySectionDefinition<IAsset>, action: string): string[] {
    return selectedItems.length !== 1 ? null : [
      ...section.baseRoute,
      selectedItems.map(_ => _.id)[0] || '_',
      action,
    ];
  };

  private _isDisabledDownload(): boolean {
    if (this.selectedItems.length !== 1) {
      return true;
    }

    // noinspection JSRedundantSwitchStatement
    switch (this.type) {
      case IAsset.Type.TABLE:
        return (<ITable> this.selectedItems[0]).status !== config.table.status.values.ACTIVE;
      case IAsset.Type.SCRIPT_DEPLOYMENT:
        return (<IScriptDeployment> this.selectedItems[0]).status !== deployConfig.scriptDeployment.status.values.READY;
      default:
        return false; // a quick fix but I think we should use white list instead
    }
  }
}

