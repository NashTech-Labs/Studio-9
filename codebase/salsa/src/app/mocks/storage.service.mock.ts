import { Injectable } from '@angular/core';

@Injectable()
export class StorageServiceMock {
  storage: Object = {};

  get(key: string): any {
    if (this.storage.hasOwnProperty(key)) {
      return this.storage[key];
    }
    return null;
  }

  set(key: string, value: any): void {
    this.storage[key] = value;
  }

  remove(key: string): void {
    if (this.storage.hasOwnProperty(key)) {
      delete this.storage[key];
    }
  }

  clear(): void {
    this.storage = {};
  }
}
