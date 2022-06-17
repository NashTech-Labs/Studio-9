import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { Subscription } from 'rxjs/Subscription';

import { TObjectId } from '../core/interfaces/common.interface';

@Component({
  selector: 'flow-table-view',
  template: `
    <app-spinner [visibility]="!tableId"></app-spinner>
    <table-view-embed [id]="tableId" [adaptiveHeight]="{minHeight: 450, pageMargin: 70}"></table-view-embed>
  `,
})
export class FlowTableViewComponent implements OnInit, OnDestroy {
  routeSubscription: Subscription;
  tableId: TObjectId;

  constructor(private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.routeSubscription = this.route.params.subscribe(params => {
      this.tableId = params['tableId'];
    });
  }

  ngOnDestroy() {
    this.routeSubscription.unsubscribe();
  }
}

