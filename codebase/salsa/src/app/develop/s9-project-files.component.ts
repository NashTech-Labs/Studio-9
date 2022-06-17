import { Component, HostBinding, HostListener, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { HierarchyNode, stratify } from 'd3-hierarchy';
import { Observable } from 'rxjs/Observable';
import { forkJoin } from 'rxjs/observable/forkJoin';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import { AppSelectOptionData } from '../core-ui/components/app-select.component';
import { ModalComponent } from '../core-ui/components/modal.component';
import { ModalService } from '../core-ui/services/modal.service';
import { HasUnsavedData } from '../core/core.interface';
import { TObjectId } from '../core/interfaces/common.interface';
import { EventService, IEvent } from '../core/services/event.service';
import { ProcessService } from '../core/services/process.service';
import { ActivityObserver } from '../utils/activity-observer';
import { ReactiveLoader } from '../utils/reactive-loader';

import { IS9Project, IS9ProjectFile, IOpenedS9ProjectFile } from './s9-project.interfaces';
import { S9ProjectService } from './s9-project.service';

interface IS9ProjectFilesTreeElement extends IS9ProjectFile {
  collapsed: boolean;
}

enum FileType {
  TEXT,
  NOTEBOOK,
  IMAGE,
  BINARY,
}

@Component({
  selector: 's9-project-files',
  template: `
    <asset-operations
      [type]="config.asset.values.S9_PROJECT"
      [selectedItems]="[s9Project]"
      (onDelete)="onProjectDeleted()"
    >
      <h2 class="asset-view-title">
        {{s9Project?.name}}
      </h2>
    </asset-operations>
    <app-spinner
      [visibility]="(_projectLoader.active | async) || (_fileOperationObserver.active | async)"
    ></app-spinner>

    <s9-project-session
      [s9Project]="s9Project"
    ></s9-project-session>

    <ng-container *ngIf="s9Project">
      <div
        *ngIf="s9Project.status === '${IS9Project.Status.INTERACTIVE}'"
        class="alert alert-warning alert-with-spaces"
      >
        <strong>Warning!</strong> You have a running interactive session.
        Quick file editing is blocked in Knoldus UI to avoid version conflicts.
        Please, reopen interactive session to continue editing or shut it down in
        order to be able to quickly edit files in Knoldus UI.
      </div>

      <div
        *ngIf="s9Project.status === '${IS9Project.Status.BUILDING}'"
        class="alert alert-warning alert-with-spaces"
      >
        <strong>Warning!</strong> S9 Project is in building state. Files editing is blocked.
        Please, wait until the operation is finished.
      </div>
    </ng-container>

    <app-tabs
      [(active)]="_selectedTab"
      (onTabClose)="$event > 0 && closeFile($event - 1)"
      [tabs]="_prepareTabs(filesOpened)"
    ></app-tabs>

    <div class="panel" [hidden]="_selectedTab !== 0">
      <div class="panel-body">
        <div class="row">
          <div class="col-md-3 col-md-push-9 col-sm-5 col-sm-push-7">
            <app-check [(checked)]="_showHidden" label="Show hidden files"></app-check>
          </div>
        </div>
        <ng-container
          *ngIf="s9Project"
          [ngTemplateOutlet]="filesList"
          [ngTemplateOutletContext]="{ filesTree: filesTree, s9Project: s9Project }"
        ></ng-container>
      </div>
    </div>
    <div
      class="panel"
      *ngFor="let file of filesOpened; let i = index"
      [hidden]="_selectedTab !== i+1"
      [ngSwitch]="getFileType(file.file.name)"
      style="margin-bottom: 0"
    >
      <notebook-viewer
        *ngSwitchCase="'${FileType.NOTEBOOK}'"
        [content]="file.content"
      ></notebook-viewer>
      <image-viewer
        *ngSwitchCase="'${FileType.IMAGE}'"
        [src]="file.file.name | apply: getImageSrc | async"
        [alt]="file.file.name"
      ></image-viewer>
      <binary-viewer
        *ngSwitchCase="'${FileType.BINARY}'"
        [file]="file"
      ></binary-viewer>
      <code-editor
        *ngSwitchCase="'${FileType.TEXT}'"
        [file]="file"
        [readonly]="s9Project.status !== '${IS9Project.Status.IDLE}'"
        (onSave)="saveFileContent($event)"
        (onClose)="closeFile(i)"
      ></code-editor>
    </div>

    <ng-template #filesList let-filesTree="filesTree" let-basePath="basePath || ''" let-s9Project="s9Project">
      <ul class="nav nav-list develop-file-list">
        <ng-container *ngFor="let file of filesTree">
        <li [hidden]="file.data | apply: _isFileHidden: _showHidden">
          <a class="ellipsis develop-file-list-row" title="{{file.data.name}}">
            <ng-container [ngSwitch]="file.data.type">
              <i *ngSwitchCase="'${IS9ProjectFile.Type.DIR}'"
                style="cursor: pointer;"
                class="glyphicon"
                [ngClass]="file.data.collapsed ? 'glyphicon-folder-close' : 'glyphicon-folder-open'"
                (click)="toggleCollapsed(file.data)"
              ></i>
              <i *ngSwitchCase="'${IS9ProjectFile.Type.FILE}'"
                class="glyphicon glyphicon-file"
              ></i>
            </ng-container>
            <span style="cursor: pointer;" (click)="openFile(file.data.name)">
              {{file.data.name | apply: _stripBasePath: basePath}}
            </span>
            <span
              *ngIf="s9Project.status === '${IS9Project.Status.IDLE}'"
              class="pull-right develop-file-list-actions"
            >
              <i class="glyphicon glyphicon-plus"
                title="Create file/directory"
                *ngIf="file.data.type === '${IS9ProjectFile.Type.DIR}'"
                (click)="addFile(file.data.name)"
              ></i>
              <i class="glyphicon glyphicon-italic"
                title="Rename"
                (click)="renameFile(file.data)"
              ></i>
              <i class="glyphicon glyphicon-pencil"
                title="Edit"
                *ngIf="file.data.type === '${IS9ProjectFile.Type.FILE}'"
                (click)="openFile(file.data.name, true)"
              ></i>
              <i class="glyphicon glyphicon-trash"
                title="Trash"
                (click)="deleteFile(file.data)"
              ></i>
            </span>
          </a>
          <div *ngIf="file.data.type === '${IS9ProjectFile.Type.DIR}'" [hidden]="file.data.collapsed">
            <ng-container
              [ngTemplateOutlet]="filesList"
              [ngTemplateOutletContext]="{ filesTree: file.children, basePath: file.data.name, s9Project: s9Project }"
            ></ng-container>
          </div>
        </li>
        </ng-container>
        <li *ngIf="(s9Project.status === '${IS9Project.Status.IDLE}') && !basePath">
          <a class="ellipsis develop-file-list-row"
            style="cursor: pointer;"
            (click)="addFile(basePath)"
          >
            <i class="glyphicon glyphicon-plus"></i>
            [ create file/directory ]
          </a>
        </li>
      </ul>
    </ng-template>

    <app-modal
      #modal
      [buttons]="[{'class': 'btn-primary', disabled: modalForm.invalid || (_fileOperationObserver.active | async), title: 'OK'}]"
      (buttonClick)="modalApprove()"
    >
      <div class="form-group">
        <app-input
          [label]="'Name'"
          [control]="nameControl"
        ></app-input>
        <app-select
          *ngIf="typeControl.enabled"
          [label]="'Type'"
          [control]="typeControl"
          [options]="_fileTypeOptions"
        ></app-select>
      </div>
    </app-modal>
  `,
})
export class S9ProjectFilesComponent implements HasUnsavedData, OnInit, OnDestroy {
  @HostBinding('class') _cssClass = 'app-spinner-box';

  readonly config = config;

  s9Project: IS9Project;

  readonly nameControl: FormControl = new FormControl('', Validators.required);
  readonly typeControl: FormControl = new FormControl('', Validators.required);
  readonly modalForm: FormGroup = new FormGroup({
    'name': this.nameControl,
    'type': this.typeControl,
  });

  readonly _fileTypeOptions: AppSelectOptionData[] = AppSelectOptionData.fromDict({
    [IS9ProjectFile.Type.FILE]: 'File',
    [IS9ProjectFile.Type.DIR]: 'Directory',
  });

  protected _selectedTab: number = 0;
  protected _projectLoader: ReactiveLoader<[IS9Project, IS9ProjectFile[]], TObjectId>;
  protected _fileOperationObserver: ActivityObserver = new ActivityObserver();
  protected _showHidden: boolean = false;

  protected files: IS9ProjectFilesTreeElement[] = [];
  protected filesTree: HierarchyNode<IS9ProjectFilesTreeElement>[];
  protected filesOpened: IOpenedS9ProjectFile[] = [];

  private processSubscription: Subscription;

  private _subscriptions: Subscription[] = [];
  @ViewChild('modal') private modal: ModalComponent;
  private _onModalApprove: () => Observable<any>;
  private readonly _notebookFileRegExp = new RegExp(/.*\.(ipynb)$/i);
  private readonly _imageFileRegExp = new RegExp(/.*\.(jpg|jpeg|gif|png|webp|apng|tiff|svg|xbm|bmp|ico|tga|xpm|pcx|eps)$/i);
  private readonly _textFileRegExp = new RegExp(/.*\.(txt|md|csv|py|cpp|c|rb|pl|sh|.gitignore|Makefile)$/i);


  constructor(
    private _router: Router,
    private _route: ActivatedRoute,
    private s9Projects: S9ProjectService,
    private processService: ProcessService,
    private modalService: ModalService,
    private readonly _eventService: EventService,
  ) {
    this._projectLoader = new ReactiveLoader(projectId => forkJoin(
      this.s9Projects.get(projectId),
      this.s9Projects.listFiles(projectId),
    ));
    function getCollapsed(item: IS9ProjectFile, existingFiles: IS9ProjectFilesTreeElement[]): boolean {
      const existingFile = existingFiles.find(
        (oldFile: IS9ProjectFilesTreeElement) => oldFile.name === item.name,
      );
      if (existingFile) {
        return existingFile.collapsed;
      }
      return item.type === IS9ProjectFile.Type.DIR;
    }
    this._subscriptions.push(this._projectLoader.subscribe(([s9Project, files]) => {
      this.s9Project = s9Project;
      this._handleProcessUpdates(s9Project);

      const newFiles: IS9ProjectFilesTreeElement[] = files.map(file => {
        return {...file, collapsed: getCollapsed(file, this.files)};
      });

      // this is a HACK
      newFiles.forEach(file => {
        const chunks = file.name.split('/');
        for (let i = 0; i < chunks.length; i++) {
          const dirName = chunks.slice(0, i - 1).join('/');
          if (dirName && !newFiles.find(_ => _.name === dirName)) {
            const dirItem = {
              type: IS9ProjectFile.Type.DIR,
              name: dirName,
              modified: file.modified,
              collapsed: false,
            };
            dirItem.collapsed = getCollapsed(dirItem, this.files);
            newFiles.push(dirItem);
          }
        }
      });
      this.files = newFiles;
      this.filesTree = stratify<IS9ProjectFilesTreeElement>()
        .id((_: IS9ProjectFilesTreeElement) => _.name ? `_/${_.name}` : '_')
        .parentId((_: IS9ProjectFilesTreeElement) => (_.name ? `_/${_.name}` : '_').split('/').slice(0, -1).join('/'))
        ([
          { type: IS9ProjectFile.Type.DIR, name: '', modified: '2018-01-01T23:00:00Z', collapsed: false },
          ...this.files,
        ]).children;

      this._updateOpenedTabs();
    }));

    this._subscriptions.push(this._eventService.subscribe((event: IEvent) => {
      if (event.type === IEvent.Type.UPDATE_S9_PROJECT && this.s9Project && event.data.id === this.s9Project.id) {
        this._projectLoader.load(this.s9Project.id);
      }
      if (event.type === IEvent.Type.DELETE_S9_PROJECT && this.s9Project && this.s9Project.id === event.data.id) {
        this.onProjectDeleted();
      }
    }));
  }

  toggleCollapsed(file: IS9ProjectFilesTreeElement) {
    file.collapsed = !file.collapsed;
  }

  getFileType(fileName: string): FileType {
    if (this._notebookFileRegExp.test(fileName)) {
      return FileType.NOTEBOOK;
    }
    if (this._imageFileRegExp.test(fileName)) {
      return FileType.IMAGE;
    }
    if (this._textFileRegExp.test(fileName)) {
      return FileType.TEXT;
    }
    return FileType.BINARY;
  }

  getImageSrc = (filePath: string): Observable<string> => {
    return this.s9Projects
      .getFileBlob(this.s9Project.id, filePath)
      .map((blob: Blob) => URL.createObjectURL(blob));
  };

  openFile(fileName: string, startEditing: boolean = false) {
    const openedIndex = this.filesOpened.findIndex(_ => _.file.name === fileName);
    if (openedIndex >= 0) {
      this._selectedTab = openedIndex + 1;
      return;
    }

    const fileToOpen = this.files.find(_ => _.name === fileName);
    if (fileToOpen && fileToOpen.type === IS9ProjectFile.Type.FILE) {
      this._fileOperationObserver.observe(this.s9Projects.getFileContent(this.s9Project.id, fileToOpen.name))
        .subscribe((content) => {
          Object.assign(fileToOpen, {
            modified: content.lastModified,
          });

          this.filesOpened = [
            ...this.filesOpened,
            {
              file: fileToOpen,
              hasChanges: false,
              content: content.content,
              isEditing: startEditing,
              editorMode: this._getEditorMode(content.contentType),
            },
          ];
          this._selectedTab = this.filesOpened.length;
        });
    }
  }

  closeFile(index: number) {
    const fileToClose = this.filesOpened[index];
    if (fileToClose.hasChanges) {
      this._confirm('This file has unsaved changes. Close anyway?', () => this._doClose(index));
    } else {
      this._doClose(index);
    }
  }

  saveFileContent(file: IOpenedS9ProjectFile) {
    this._fileOperationObserver.observe(this.s9Projects.updateFileContent(
      this.s9Project.id,
      file.file.name,
      file.content,
      file.file.modified,
    )).subscribe(updatedFile => {
      file.hasChanges = false;
      Object.assign(file.file, updatedFile);
    });
  }

  deleteFile(file: IS9ProjectFile) {
    const doDelete = () => {
      this._fileOperationObserver.observe(this.s9Projects.deleteFile(
        this.s9Project.id,
        file.name,
        file.modified,
      )).subscribe(() => {
        this._projectLoader.load(this.s9Project.id);
      });
    };

    this._confirm('Are you sure you want to delete the file/directory?', doDelete);
  }

  renameFile(file: IS9ProjectFile): void {
    const openedFile = this.filesOpened.find(fo => fo.file === file);
    if (openedFile && openedFile.hasChanges) {
      const message = 'This file has unsaved changes that will be lost after renaming.\n' +
        'Do you want to rename and loose all unsaved data?';
      this._confirm(message, () => this._doRename(file));
    } else {
      this._doRename(file);
    }
  }

  addFile(basePath) {
    this.nameControl.reset(basePath ? basePath + '/' : '');
    this.typeControl.enable();
    this.typeControl.reset(IS9ProjectFile.Type.FILE);
    this.modal.caption = 'Create File/Directory';
    this.modal.show();
    this._onModalApprove = () => {
      switch (this.typeControl.value) {
        case IS9ProjectFile.Type.FILE:
          return this.s9Projects.createFileContent(this.s9Project.id, this.nameControl.value, '');
        case IS9ProjectFile.Type.DIR:
          return this.s9Projects.createDirectory(this.s9Project.id, this.nameControl.value);
        default:
          throw new Error('Impossibru');
      }
    };
  }

  modalApprove() {
    if (this._onModalApprove) {
      this._fileOperationObserver.observe(this._onModalApprove())
        .subscribe(() => {
          this.modal.hide();
          this._projectLoader.load(this.s9Project.id);
          this._onModalApprove = null;
        });
    }
  }

  ngOnInit() {
    const paramsSubscription = this._route.params
      .subscribe((params) => {
        const projectId = params['projectId'];
        if (projectId) {
          this._projectLoader.load(projectId);
        } else {
          delete this.s9Project;
        }
      });

    this._subscriptions.push(paramsSubscription);
  }

  ngOnDestroy() {
    this.processSubscription && this.processSubscription.unsubscribe();
    this._subscriptions.forEach((_) => _.unsubscribe());
  }

  @HostListener('window:beforeunload', ['$event'])
  public onPageUnload($event: BeforeUnloadEvent) {
    if (this.hasUnsavedData()) {
      $event.returnValue = true;
    }
  }

  _prepareTabs = function(filesOpened: IOpenedS9ProjectFile[]): string[] {
    return [
      'Files',
      ...filesOpened.map(_ => _.file.name + (_.hasChanges ? '*' : '')),
    ];
  };

  _stripBasePath = function(fileName: string, basePath: string): string {
    if (fileName.startsWith(basePath + '/')) {
      return fileName.slice(basePath.length + 1);
    }

    return fileName;
  };

  hasUnsavedData(): boolean {
    return !!this.filesOpened.find(_ => _.hasChanges);
  }

  onProjectDeleted(): void {
    this._router.navigate(['/desk', 'develop', 'projects']);
  }

  private _handleProcessUpdates(s9Project: IS9Project): void {
    this.processSubscription && this.processSubscription.unsubscribe();
    this.processSubscription = this.s9Projects.getActiveProcess(s9Project)
      .filter(_ => !!_)
      .flatMap(process => {
        return this.processService.observe(process);
      })
      .subscribe(() => {
        this._projectLoader.load(s9Project.id);
      });
  }

  private _getEditorMode(contentType: string): string {
    const bareContentType = contentType.split(',')[0].trim(); // drops extra arguments e.g. encoding
    switch (bareContentType) {
      case 'text/x-python':
        return 'python';
      default:
        return '';
    }
  }

  private _isFileHidden(file: IS9ProjectFilesTreeElement, showHidden: boolean): boolean {
    return !showHidden && file.name.split('/').some(p => p.startsWith('.'));
  }

  private _doClose(index: number): void {
    this.filesOpened.splice(index, 1);
    if (index + 1 < this._selectedTab) {
      this._selectedTab--;
    } else if (index + 1 === this._selectedTab) {
      this._selectedTab = 0;
    }
  }

  private _doRename(file: IS9ProjectFile): void {
    this.nameControl.reset(file.name);
    this.typeControl.disable();
    this.modal.caption = 'Rename File/Directory';
    this.modal.show();
    this._onModalApprove = () => {
      return this.s9Projects
        .moveFile(this.s9Project.id, file, this.nameControl.value)
        .do(newFile => Object.assign(file, newFile));
    };
  }


  private _updateOpenedTabs(): void {
    for (let index = this.filesOpened.length - 1; index >= 0; index--) {
      const openedFile = this.filesOpened[index];
      const fileToOpen = this.files.find(file => file.name === openedFile.file.name);
      if (fileToOpen && fileToOpen.type === IS9ProjectFile.Type.FILE) {
        this._fileOperationObserver
          .observe(this.s9Projects.getFileContent(this.s9Project.id, fileToOpen.name))
          .subscribe((content) => {
            Object.assign(fileToOpen, {
              modified: content.lastModified,
            });

            Object.assign(openedFile, {
              file: fileToOpen,
              content: content.content,
              editorMode: this._getEditorMode(content.contentType),
            });
          });
      } else {
        this._doClose(index);
      }
    }
  }

  private _confirm(message: string, callback: () => void): void {
    this.modalService
      .confirm(message)
      .take(1)
      .filter(Boolean)
      .subscribe(callback);
  }
}
