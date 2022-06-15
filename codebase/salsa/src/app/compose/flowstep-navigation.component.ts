import {
  AfterViewInit,
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  Renderer,
  ViewChild,
} from '@angular/core';
import { Router } from '@angular/router';

import config from '../config';
import { ModalService } from '../core-ui/services/modal.service';
import { TObjectId } from '../core/interfaces/common.interface';

import { IFlow, IFlowstep } from './flow.interface';
import { FlowService } from './flow.service';
import { FlowstepService } from './flowstep.service';

@Component({
  selector: 'flowstep-navigation',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div #flexWrapper *ngIf="flow" class="flex-static" style="height: 60px">
      <div #container class="brand-right-panel groupsteps-container">
        <ul #scrollArea class="nav nav-tabs groupsteps" role="tablist" [ngClass]="{'with-scroll': hScroll}">
          <li role="presentation" [routerLinkActive]="['active']" *ngFor="let step of flow.steps">
            <a [routerLink]="['/desk','flows', flow.id, 'steps', step.id]"
              [attr.aria-controls]="step.id" role="tab" data-toggle="tab">
              <span class="stepicon" [ngClass]="config.flowstep.type.icons[step.type] || 'iconapp iconapp-question'"></span>{{step.name}}
            </a>
            <a (click)="deleteFlowstep(flow.id, step)"
              class="icon-el remove_tab" title="Remove Step"><i class="glyphicon glyphicon-remove"></i></a>
          </li>
        </ul>
        <div *ngIf="hScroll" class="groupsteps-scroll">
          <button class="btn btn-link" type="button" [disabled]="hScrollLeftDisabled" (click)="scroll()"><i class="glyphicon glyphicon-triangle-left"></i></button>
          <button class="btn btn-link" type="button" [disabled]="hScrollRightDisabled" (click)="scroll(true)"><i class="glyphicon glyphicon-triangle-right"></i></button>
        </div>
      </div>
    </div>
  `,
})
export class FlowstepNavigationComponent implements OnChanges, OnDestroy, AfterViewInit {
  @Input() flow: IFlow;
  readonly config = config;
  hScroll: boolean = false;
  hScrollLeftDisabled: boolean = false;
  hScrollRightDisabled: boolean = false;
  @ViewChild('flexWrapper') private flexWrapper: ElementRef;
  @ViewChild('container') private container: ElementRef;
  @ViewChild('scrollArea') private scrollArea: ElementRef;
  private listener: Function;

  constructor(
    private flows: FlowService,
    private flowsteps: FlowstepService,
    private router: Router,
    private renderer: Renderer,
    private cd: ChangeDetectorRef,
    private modals: ModalService,
  ) {}

  ngOnChanges(): void {
    setTimeout(() => this.updateView());
  }

  ngAfterViewInit() {
    this.cd.detach();
    this.updateView();
    this.listener = this.renderer.listenGlobal('window', 'resize', () => this.updateView());
  }

  ngOnDestroy() {
    this.listener && this.listener();
  }

  deleteFlowstep(flowId: TObjectId, flowstep: IFlowstep) {
    this.modals.confirm('Are you sure to delete the step?').filter(_ => _).flatMap(() => {
      return this.flowsteps.delete(flowId, flowstep);
    }).subscribe(() => {
      this.flows.get(flowId);
      this.router.navigate(['/desk', 'flows', flowId]);
    });
  }

  scroll(right?: boolean) {
    const $area = $(this.scrollArea.nativeElement);
    const areaWidth = $area.width();
    const scrollPosition = right ? Math.max(0, $area.scrollLeft() + (areaWidth / 4)) : Math.max(0, $area.scrollLeft() - (areaWidth / 4));

    $area.stop().animate({ scrollLeft: scrollPosition }, 500);

    this.hScrollLeftDisabled = scrollPosition <= 0;
    this.hScrollRightDisabled = (areaWidth + scrollPosition) >= $area.get(0).scrollWidth;
    this.cd.detectChanges();
  }

  private updateView() {
    if (this.container) {
      $(this.container.nativeElement).css('width', ($(this.flexWrapper.nativeElement).width() + 30) + 'px');
    }

    if (this.scrollArea) {
      const $area = $(this.scrollArea.nativeElement);
      const areaWidth = $area.width();
      const scrollPosition = $area.scrollLeft();
      this.hScroll = $area.get(0).scrollWidth > $area.width();
      this.hScrollLeftDisabled = scrollPosition <= 0;
      this.hScrollRightDisabled = (areaWidth + scrollPosition) === $area.get(0).scrollWidth;
    }
    this.cd.detectChanges();
  }
}
