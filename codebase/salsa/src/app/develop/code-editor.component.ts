import { Component, EventEmitter, Input, Output } from '@angular/core';

import { IOpenedS9ProjectFile } from './s9-project.interfaces';

@Component({
  selector: 'code-editor',
  template: `
      <div class="panel-body">
        <div class="btn-group btn-group-sm pull-right">
          <button
            *ngIf="file.isEditing"
            class="btn btn-primary"
            [disabled]="!file.hasChanges || readonly"
            (click)="_onSaveClicked()"
          >
            <i class="glyphicon glyphicon-save-file"></i>
            Save
          </button>
          <button
            *ngIf="!file.isEditing"
            [disabled]="readonly"
            class="btn btn-primary"
            (click)="file.isEditing = true"
          >
            <i class="glyphicon glyphicon-pencil"></i>
            Edit
          </button>
          <button
            class="btn"
            (click)="onClose.emit()"
          >
            <i class="glyphicon glyphicon-remove"></i>
            Close
          </button>
        </div>
      </div>
      <div
        class="develop-code-editor"
        style="width: 100%; overflow: auto;"
        [code]="file.editorMode"
        [codeReadOnly]="!file.isEditing || readonly"
        [filePath]="file.file.name"
        [(codeValue)]="file.content"
        (codeValueChange)="file.hasChanges = true"
        [adaptiveHeight]="{property: 'height'}"
        #codeDirective="code"
        (onHeightUpdate)="codeDirective.resize()"
      ></div>
  `,
})
export class CodeEditorComponent {
  @Input() file: IOpenedS9ProjectFile;
  @Input() readonly: boolean = false;
  @Output() onSave = new EventEmitter<IOpenedS9ProjectFile>();
  @Output() onClose = new EventEmitter<void>();

  private _onSaveClicked()  {
    this.onSave.emit(this.file);
  }
}
