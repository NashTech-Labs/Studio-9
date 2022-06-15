import { Directive, ElementRef, EventEmitter, Input, OnDestroy, OnInit, Output, Renderer } from '@angular/core';

import { Observable } from 'rxjs/Observable';

import config from '../../config';
import { MiscUtils } from '../../utils/misc';
import { AppHttpFile } from '../services/http.service';
import { NotificationService } from '../services/notification.service';

@Directive({
  selector: '[file-upload]',
})
export class FileUploadDirective implements OnInit, OnDestroy {
  @Input('file-upload-click') click: boolean = false;
  @Input('file-upload-multiple') multiple: boolean = false;
  @Input() accept: string[] = config.table.import.extension.list;
  @Output() onSelectFile: EventEmitter<AppHttpFile> = new EventEmitter<AppHttpFile>();
  @Output() onSelectFiles: EventEmitter<AppHttpFile[]> = new EventEmitter<AppHttpFile[]>();

  private el: HTMLElement;
  private listeners: Function[] = [];

  constructor(
    el: ElementRef,
    private renderer: Renderer,
    private notificationService: NotificationService,
  ) {
    this.el = el.nativeElement;
  }

  ngOnInit() {
    if (this.click) {
      let elFileInput = document.createElement('input');

      elFileInput.setAttribute('type', 'file');
      elFileInput.setAttribute('multiple', this.multiple.toString());
      elFileInput.setAttribute('accept', this.accept.join(', '));
      elFileInput.style.display = 'none';

      this.el.appendChild(elFileInput);

      this.listeners.push(this.renderer.listen(this.el.querySelector('input'), 'click', event => {
        event.toElement !== elFileInput && elFileInput.click();
      }));

      this.listeners.push(this.renderer.listen(elFileInput, 'change', event => {
        let files = event.target.files;
        this.handleFiles(files);
        // this needs to be done to clear selection for next use
        elFileInput.value = null;
      }));
    }

    this.listeners.push(this.renderer.listen(this.el, 'drop', event => {
      event.stopPropagation();
      event.preventDefault();

      this.isDragFileEvent(event) && this.handleDataTransferItems(event.dataTransfer.items);
    }));

    this.listeners.push(this.renderer.listen(this.el, 'dragover', event => {
      event.stopPropagation();
      event.preventDefault();

      // TODO: maybe we should change some css of element here like dashed div
      this.isDragFileEvent(event) && (event.dataTransfer.dropEffect = 'copy');
    }));
  }

  ngOnDestroy() {
    this.listeners.forEach((fn: Function) => fn());
  }

  private handleFiles(files: FileList | File[] | AppHttpFile[]): void {
    // Filter files via size limit
    let acceptedFiles: File[] = [];

    let i = 0;
    Object.keys(files).forEach((key: string) => {
      const file = files[key];
      if (config.table.import.sizeLimit && file.size > config.table.import.sizeLimit) {
        this.notificationService.create(
          `"${file.name}" file has ${MiscUtils.formatBytes(file.size)} size \
          that exceeds the allowable limit of ${MiscUtils.formatBytes(config.table.import.sizeLimit)}`,
          config.notification.level.values.WARNING,
        );
        return false;
      }

      acceptedFiles[i] = files[key];
      i++;
      return true;
    });

    if (!acceptedFiles.length) return; // Exit if there are no files

    this.onSelectFiles.emit(acceptedFiles);
    acceptedFiles.forEach((file: AppHttpFile) => this.onSelectFile.emit(file));
  }

  private handleDataTransferItems(items: DataTransferItem): void {
    Object.keys(items).reduce((acc, key) => {
      const item = items[key];
      const entry = item.webkitGetAsEntry && item.webkitGetAsEntry();
      const file = item.getAsFile();
      if (entry) {
        return acc.flatMap(soFar => {
          return traverseFileTree(entry).map(more => soFar.concat(more));
        });
      } else if (file) {
        return acc.map(soFar => soFar.concat([file]));
      }
    }, Observable.of([])).subscribe(files => {
      this.handleFiles(files);
    });
  }

  //noinspection JSMethodCanBeStatic
  private isDragFileEvent(event: DragEvent) {
    return event.dataTransfer.files && event.dataTransfer.files.length && event.dataTransfer.files instanceof FileList;
  }
}


function traverseFileTree(item: WebKitEntry, path = ''): Observable<AppHttpFile[]> {
  if (item.isFile) {
    return new Observable<File[]>(subscriber => {
      (<WebKitFileEntry> item).file((file: any) => {
        file.realName = path + file.name;
        subscriber.next([file]);
        subscriber.complete();
      }, error => subscriber.error(error));
    }).share();
  } else if (item.isDirectory) {
    // Get folder contents
    const dirReader = (<WebKitDirectoryEntry> item).createReader();
    return new Observable<File[]>(subscriber => {
      dirReader.readEntries((entries: any) => {
        (<WebKitEntry[]> entries).reduce((acc, entry) => {
          return acc.flatMap(soFar => {
            return traverseFileTree(entry, path + item.name + '/').map(more => soFar.concat(more));
          });
        }, Observable.of([])).subscribe(subscriber);
      }, error => subscriber.error(error));
    }).share();
  }
}
