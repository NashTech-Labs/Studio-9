import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild } from '@angular/core';

import { Observable } from 'rxjs/Observable';

import { ModalService } from '../core-ui/services/modal.service';
import { TObjectId } from '../core/interfaces/common.interface';
import { AclService } from '../services/acl.service';
import { bulkAction } from '../utils/observable';

import { IAlbum, IPicture } from './album.interface';
import { AlbumService } from './album.service';
import { PicturesCloneModalComponent } from './pictures-clone-modal.component';

@Component({
  selector: 'picture-operations',
  template: `
    <div class="operations-toolbar row row-flex pt5 pb5" style="align-items: flex-end;">
      <div class="col-xs-12 flex-static">
        <!-- Common Buttons -->
        <ul class="asset-btn-panel nav nav-pills">
          <li class="nav-item"
            [ngClass]="{'disabled': disabledPreview }"
            [ngSwitch]="!!selectedPictures.length && !inPictureView">
            <a *ngSwitchCase="true" (click)="onPicturePreviewClick()" class="nav-link link-colorless">
              <i class="imgaction imgaction-preview center-block"></i>
              <div>preview</div>
            </a>
            <a *ngSwitchDefault="" class="nav-link link-colorless">
              <i class="imgaction imgaction-preview center-block"></i>
              <div>preview</div>
            </a>
          </li>
          <li class="nav-item" [ngClass]="{'disabled': disabledTrash}">
            <a class="nav-link link-colorless" (click)="disabledTrash || trash()">
              <i class="imgaction imgaction-trash center-block"></i>
              <div>Trash</div>
            </a>
          </li>
          <li class="nav-item disabled">
            <a class="nav-link link-colorless">
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
          <li class="nav-item disabled">
            <a class="nav-link link-colorless">
              <i class="imgaction imgaction-download center-block"></i>
              <div>download</div>
            </a>
          </li>
          <li class="nav-item disabled">
            <a class="nav-link link-colorless">
              <i class="imgaction imgaction-download center-block"></i>
              <div>download</div>
            </a>
          </li>
          <li class="nav-item disabled">
            <a class="nav-link link-colorless">
              <i class="imgaction imgaction-refresh center-block"></i>
              <div>refresh</div>
            </a>
          </li>
          <li class="nav-item disabled">
            <a class="nav-link link-colorless">
              <i class="imgaction imgaction-info center-block"></i>
              <div>info</div>
            </a>
          </li>
          <li class="nav-item disabled">
            <a class="nav-link link-colorless">
              <i class="imgaction glyphicon glyphicon-pencil center-block"></i>
              <div>edit</div>
            </a>
          </li>
          <li class="nav-item disabled">
            <a class="nav-link link-colorless">
              <i class="imgaction imgaction-clone center-block"></i>
              <div>Merge</div>
            </a>
          </li>
        </ul>
        <!-- End Common Buttons -->
      </div>
      <div class="col-xs-12 flex-rubber visible-dropdown">
        <ng-content></ng-content>
      </div>
    </div>
    <pictures-clone-modal #picturesCloneModal></pictures-clone-modal>
  `,
})
export class PictureOperationsComponent implements OnChanges {
  @Input() album: IAlbum;
  @Input() selectedPictures: IPicture[] = [];
  @Input() inPictureView: boolean = false;
  @Output() onDelete = new EventEmitter<TObjectId[]>();
  @Output() selectedPicturesChange = new EventEmitter<IPicture[]>();
  @Output() onPicturePreview = new EventEmitter<TObjectId>();
  @ViewChild('picturesCloneModal') picturesCloneModal: PicturesCloneModalComponent;
  disabledClone: boolean;
  disabledTrash: boolean;
  disabledPreview: boolean;

  constructor(
    private acl: AclService,
    private modals: ModalService,
    private albums: AlbumService,
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['selectedPictures']
      && changes['selectedPictures'].currentValue !== changes['selectedPictures'].previousValue) {
      this.selectedPictures = this.selectedPictures.filter(_ => _);
      const readOnly = !this.album || !this.acl.canRemovePicture(<IAlbum> this.album);
      this.disabledTrash = !this.selectedPictures.length || readOnly;
      this.disabledClone = !this.selectedPictures.length || this.inPictureView;
      this.disabledPreview = this.selectedPictures.length !== 1;
    }
  }

  trash() {
    const pictureNames: string = this.selectedPictures.map(fileName => fileName.filename).join(', ');
    const confirmationMessage = 'Are you sure you want to delete the following ' +
      `${pictureNames.length > 1 ? 'pictures' : 'picture'}: ${pictureNames}?`;

    this.modals.confirm(confirmationMessage).filter(_ => _).flatMap(() => {
      const selectedPictures = [...this.selectedPictures];
      const observables: Observable<any>[] = selectedPictures.map((item: IPicture): Observable<TObjectId> => {
        return this.albums.deletePicture(<IAlbum> {id: this.album.id}, <IPicture> {id: item.id});
      });
      return bulkAction(observables).map(results => {
        return results.map((result, index) => result ? selectedPictures[index].id : null);
      });
    }).subscribe((ids: TObjectId[]) => {
      this.resetSelection();
      this.onDelete.emit(ids.filter(_ => !!_));
    });
  }

  resetSelection() {
    this.selectedPicturesChange.emit([]);
  }

  clone() {
    this.picturesCloneModal.open(this.album, this.selectedPictures);
  }

  onPicturePreviewClick() {
    this.onPicturePreview.emit(this.selectedPictures[0].id);
  }
}
