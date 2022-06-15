import {
  Component,
  ElementRef,
  EventEmitter,
  Input,
  NgZone,
  OnDestroy,
  OnInit,
  Output,
  ViewChild,
} from '@angular/core';

import {
  DragEventCallbackOptions,
  jsPlumb,
  jsPlumbInstance,
} from 'jsplumb';
import * as _ from 'lodash';
import { DragDropData } from 'ng2-dnd';
import createPanZoom, { PanZoom } from 'panzoom';
import { Subscription } from 'rxjs/Subscription';

import config from '../config';
import {
  IModalButton,
  ModalComponent,
} from '../core-ui/components/modal.component';
import { ParameterValues } from '../core/interfaces/params.interface';
import { ReactiveLoader } from '../utils/reactive-loader';

import { pipelineCanvasConfig } from './canvas.config';
import { OperatorInfoModalComponent } from './operator-info-modal.component';
import { OperatorParamsComponent } from './operator-params.component';
import { PipelineOperatorPositioningService } from './operator-positioning.service';
import {
  ICanvasCoordinates,
  ICanvasOperator,
  ICanvasOperatorInput,
  ICanvasOperatorOutput,
  ICanvasStep,
} from './pipeline-canvas.interfaces';
import {
  Pipeline,
  PipelineDataType,
  PipelineOperator,
} from './pipeline.interfaces';
import { PipelineService } from './pipeline.service';

@Component({
  selector: 'app-pipeline-canvas',
  template: `
    <ng-container *ngIf="steps | apply: isPublishedData: availableOperators; else notPublished">
    <app-spinner [visibility]="initialDataLoader.active | async"></app-spinner>
    <div
      class="pipeline-canvas-wrapper"
      #canvasWrapper
      [stickyHeader]="true"
      [adaptiveHeight]="{
        minHeight: 500,
        pageMargin: 15,
        property: 'min-height'
      }"
    >
      <div
        class="pipeline-canvas canvas-grid jtk-surface"
        #canvas
        dnd-droppable
        (onDropSuccess)="onDrop($event)"
      ></div>
    </div>
    <app-modal #editModeWarningModal
      [captionPrefix]="'Warning'"
      [caption]="'Not in edit mode'"
      [buttons]="[{
        'class': 'btn-primary',
        'title': 'OK'
      }]"
      (buttonClick)="_editModeWarningModal.hide()"
    >
      You are not in edit mode. Please click Edit button to start editing.
    </app-modal>

    <app-modal
      #stepParamsModal
      [caption]="selectedStep?.canvasOperator.operator.name + ' Parameters'"
      [buttons]="getParamsModalButtons | call: isEditMode: selectedStepParamsIsValid"
      (buttonClick)="onStepParamsModalClick($event)"
      [limitedHeight]="true"
      [sizeClass]="config.modal.size.LARGE"
    >
      <pipeline-operator-params
        #pipelineOperatorParams
        *ngIf="selectedStep && _stepParamsModal.shown"
        [parameters]="selectedStep.canvasOperator.operator.params"
        [pipelineParameters]="pipelineEditor ? selectedStep.pipelineParameters : null"
        (pipelineParametersChange)="selectedStepPipelineParams = $event"
        [value]="selectedStep.params"
        (valueChange)="selectedStepParams = $event"
        (validityChange)="selectedStepParamsIsValid = $event"
        [disabled]="!isEditMode"
      ></pipeline-operator-params>
    </app-modal>

    <operator-info-modal #operatorInfoModal></operator-info-modal>

    <app-modal
      #stepRemovalConfirmationModal
      [caption]="'Remove step'"
      [buttons]="[
        { 'id': 'yes', 'class': 'btn-danger', 'title': 'Yes' },
        { 'id': 'no', 'class': 'btn-default', 'title': 'No' }
      ]"
      (buttonClick)="onStepRemovalConfirmed($event)"
    >
      <p
        *ngIf="selectedStep"
      >
        Are you sure you want to delete this operator:
        {{selectedStep.name || selectedStep.canvasOperator.operator.name}}?
      </p>
    </app-modal>
    </ng-container>

    <ng-template #notPublished>
      <div>
        <error-indicator message="Pipeline has operators that are not published or owned by you."></error-indicator>
      </div>
    </ng-template>
  `,
})
export class CanvasComponent implements OnInit, OnDestroy {
  readonly config = config;
  @Input() steps: Pipeline.StepInfo[];
  @Input() isEditMode: boolean = false;
  @Input() availableOperators: PipelineOperator[] = [];
  @Input() pipelineEditor: boolean = true;

  @Output() canvasUpdated: EventEmitter<Pipeline.StepInfo[]> = new EventEmitter();

  @ViewChild('editModeWarningModal') _editModeWarningModal: ModalComponent;
  @ViewChild('stepParamsModal') _stepParamsModal: ModalComponent;
  @ViewChild('operatorInfoModal') _operatorInfoModal: OperatorInfoModalComponent;
  @ViewChild('stepRemovalConfirmationModal') _stepRemovalConfirmationModal: ModalComponent;
  @ViewChild('pipelineOperatorParams') trainCVPipelineParams: OperatorParamsComponent;

  categories: {[id: string]: PipelineOperator.Category} = {};

  protected renderer: jsPlumbInstance;
  protected selectedStep: ICanvasStep;
  protected selectedStepParams: ParameterValues = {};
  protected selectedStepPipelineParams: Pipeline.PipelineParameters = {};
  protected selectedStepParamsIsValid: boolean = false;

  private _subscription: Subscription = null;
  private _steps: ICanvasStep[] = [];
  private _panZoom: PanZoom;

  private readonly initialDataLoader: ReactiveLoader<PipelineOperator.Category[], null>;

  @ViewChild('canvas') private _canvasEl: ElementRef;
  @ViewChild('canvasWrapper') private _canvasWrapperEl: ElementRef;

  constructor(
    private readonly _pipelineService: PipelineService,
    private readonly _positioningService: PipelineOperatorPositioningService,
    private zone: NgZone,
  ) {
    this.initialDataLoader = new ReactiveLoader(() => this._pipelineService.listOperatorCategories());
  }

  ngOnInit(): void {
    this._subscription = this.initialDataLoader.subscribe((categories) => {
      this.categories = _.keyBy(categories, c => c.id);
      if (this.isPublishedData(this.steps, this.availableOperators)) {
        this._initRenderer();
      }
    });
    this.initialDataLoader.load();
  }

  ngOnDestroy(): void {
    this.renderer && this.renderer.reset();
    this._panZoom && this._panZoom.dispose();

    this.zone.runOutsideAngular(() => {
      window.removeEventListener('scroll', this._updateCanvasSizeAccordingToPanningBound);
      window.removeEventListener('resize', this._updateCanvasSizeAccordingToPanningBound);
    });
    this._subscription.unsubscribe();
  }

  protected onDrop(event: DragDropData): void {
    const data = event.dragData;
    if (!data || !('pipelineOperator' in data)) {
      return;
    }

    if (!this.isEditMode) {
      this._editModeWarningModal.show();
    } else if (this.renderer) {
      this._addStep(
        this.renderer,
        event.dragData['pipelineOperator'],
        {
          x: this._calculatePlacementCoordinate(event.mouseEvent.offsetX, pipelineCanvasConfig.gridSize[0]),
          y: this._calculatePlacementCoordinate(event.mouseEvent.offsetY, pipelineCanvasConfig.gridSize[1]),
        },
      );
    }
  }

  protected onStepParamsModalClick(btn: IModalButton): void {
    if (btn.id === 'ok' && this.isEditMode) {
      this.selectedStep.params = this.selectedStepParams;
      this.selectedStep.pipelineParameters = this.selectedStepPipelineParams;
      this._emitCanvasUpdate();
    }
    this._stepParamsModal.hide();
    this.selectedStepParams = null;
    this.selectedStepPipelineParams = null;
  }

  protected onStepRemovalConfirmed(btn: IModalButton): void {
    if (btn.id === 'yes') {
      this.renderer && this.renderer.remove(this.selectedStep.canvasOperator.el);
      const index = this._steps.findIndex((_) => this.selectedStep.id === _.id);
      if (index === -1) {
        throw new Error('Can\'t find step id: ' + this.selectedStep.id);
      }

      this._steps.splice(index, 1);

      for (const step of this._steps) {
        for (const inputName of Object.keys(step.inputs)) {
          if (step.inputs[inputName].stepId === this.selectedStep.id) {
            delete step.inputs[inputName];
          }
        }
      }

      this.selectedStep = null;
      this._emitCanvasUpdate();
    }
    this._stepRemovalConfirmationModal.hide();
  }

  protected getParamsModalButtons(isEditMode: boolean, isValid: boolean): IModalButton[] {
    const buttons: IModalButton[] = [
      { id: 'ok', class: 'btn-apply', title: 'Ok', disabled: isEditMode && !isValid },
    ];

    if (isEditMode) {
      buttons.push({ id: 'cancel', class: 'btn-clear', title: 'Cancel' });
    }

    return buttons;
  }

  protected isPublishedData(steps: Pipeline.Step[], operators: PipelineOperator[]): boolean {
    let operatorIds = steps.map((data: Pipeline.Step) => data.operator);
    let pipeLineOperatorIds: string[];
    pipeLineOperatorIds = operators.map(_ => _.id);
    return operatorIds.every(_ => pipeLineOperatorIds.includes(_));
  }

  private _updateCanvasSizeAccordingToPanning(): void {
    const $canvas = $(this._canvasEl.nativeElement);
    const $canvasWrapper = $(this._canvasWrapperEl.nativeElement);

    const canvasBottom = $canvas.offset().top + $canvas.outerHeight(true);
    const canvasWrapperBottom = $canvasWrapper.offset().top + $canvasWrapper.outerHeight(true);
    const deltaY = canvasWrapperBottom - canvasBottom;

    if (deltaY > 0) {
      $canvas.height($canvas.height() + deltaY);
    }

    const canvasRight = $canvas.offset().left + $canvas.outerWidth(true);
    const canvasWrapperRight = $canvasWrapper.offset().left + $canvasWrapper.outerWidth(true);
    const deltaX = canvasWrapperRight - canvasRight;

    if (deltaX > 0) {
      $canvas.width($canvas.width() + deltaX);
    }
  }

  private _updateCanvasSizeAccordingToPanningBound = () => this._updateCanvasSizeAccordingToPanning();

  private _initPanZoom(): void {
    this._panZoom = createPanZoom(
      this._canvasEl.nativeElement,
      {
        minZoom: 1, // disabled zoom because of incompatibility with jsplumb
        maxZoom: 1, // disabled zoom because of incompatibility with jsplumb
        smoothScroll: false,
        bounds: {
          left: 0,
          top: 0,
          right: 0,
          bottom: 0,
        },
      },
    );

    this._panZoom.on('pan', () => this._updateCanvasSizeAccordingToPanning());

    // fix for fast panning
    this._panZoom.on('panend', () => setTimeout(this._updateCanvasSizeAccordingToPanning.bind(this), 10));

    // fix canvas size on init
    setTimeout(this._updateCanvasSizeAccordingToPanning.bind(this), 10);

    this.zone.runOutsideAngular(() => {
      window.addEventListener('scroll', this._updateCanvasSizeAccordingToPanningBound);
      window.addEventListener('resize', this._updateCanvasSizeAccordingToPanningBound);
    });
  }

  private _initRenderer(): void {
    const renderer = jsPlumb.getInstance(pipelineCanvasConfig.jsPlumbDefaults);
    this.renderer = renderer;

    renderer.ready(() => {
      renderer.registerConnectionType('basic', pipelineCanvasConfig.jsPlumbViewSettings.basicConnectionType);

      this._initPanZoom();

      this._addLoadedSteps(renderer);

      // Drag started
      renderer.bind('connectionDrag', (e) => this._filterInputs(e));

      // Drag stopped
      renderer.bind('connectionDragStop', () => this._resetInputsFilter());

      // Connection established
      renderer.bind('connection', (e) => this._onNewConnection(e));

      // Connection detached
      renderer.bind('connectionDetached', (e) => this._onConnectionDetached(e));

      // Connection moved
      renderer.bind('connectionMoved', (e) => this._onConnectionMoved(e));
    });
  }

  private _addLoadedSteps(renderer: jsPlumbInstance): void {
    if (this.steps.length) {
      // 1. add operators to canvas
      for (const loadedStep of this.steps) {
        const operator = this.availableOperators.find((_) => _.id === loadedStep.operator);
        if (operator) {
          this._addStep(renderer, operator, null, loadedStep);
        } else {
          throw new Error('Can\'t find operator');
        }
      }

      // 2. connect canvas operators
      for (const loadedStep of this.steps) {
        const step = this._steps.find((_) => _.id === loadedStep.id);
        if (!step) {
          // most likely an operator wasn't found on previous step
          continue;
        }
        for (const inputName of Object.keys(loadedStep.inputs)) {
          const input = step.canvasOperator.inputs.find((_) => _.input.name === inputName);
          if (!input) {
            throw new Error('Can\'t find step input');
          }
          const outputRef = loadedStep.inputs[inputName];
          const sourceStep = this._steps.find((_) => _.id === outputRef.stepId);
          if (!sourceStep) {
            throw new Error('Can\'t find corresponding step');
          }
          const sourceOutput = sourceStep.canvasOperator.outputs[outputRef.outputIndex];
          if (!sourceOutput) {
            throw new Error('Can\'t find corresponding step output');
          }

          renderer.connect({
            source: sourceOutput.endpoint,
            target: input.endpoint,
            connector: [
              'Flowchart',
              {
                stub: [10, 20],
                gap: 10,
                cornerRadius: 5,
                alwaysRespectStubs: false,
                midpoint: Math.random() * 0.5 + 0.25,
              },
            ],
          });
        }
      }

      // 3. position operators on canvas automatically
      setTimeout(() => {
        // done in async fashion to let browser calculate operator element sizes
        this._updateStepsCoordinates();
      }, 0);
    }
  }

  private _moveStepTo(step: ICanvasStep, coordinates: ICanvasCoordinates): void {
    const el = step.canvasOperator.el;

    el.style.left = coordinates.x + 'px';
    el.style.top = coordinates.y + 'px';

    step.coordinates = coordinates;

    // Redraw IOs otherwise they are misplaced:
    this.renderer && setTimeout(() => this.renderer.revalidate(el));
  }

  private _updateStepsCoordinates(): void {
    const positions = this._positioningService.calculateStepPositions(
      this._steps,
      this._canvasEl.nativeElement,
      pipelineCanvasConfig.gridSize,
    );

    for (const stepId of Object.keys(positions)) {
      const position = positions[stepId];
      const step = this._steps.find(_ => _.id === stepId);
      if (!step) {
        throw new Error('Could not find step');
      }
      if (position) {
        this._moveStepTo(step, position);
      }
    }
  }

  private _onNewConnection(e: any): void {
    const params = e.connection.getParameters();
    const inputCanvasStep: ICanvasStep = params['inputStep'];
    const outputCanvasStep: ICanvasStep = params['outputStep'];
    const canvasInput: ICanvasOperatorInput = params['input'];
    const canvasOutput: ICanvasOperatorOutput = params['output'];

    if (!inputCanvasStep || !outputCanvasStep || !canvasInput || !canvasOutput) {
      throw new Error('Steps connection data was not provided');
    }

    if (inputCanvasStep.inputs[canvasInput.input.name]) {
      throw new Error('This step input is already connected');
    }

    inputCanvasStep.inputs[canvasInput.input.name] = {
      stepId: outputCanvasStep.id,
      outputIndex: canvasOutput.index,
    };

    this._emitCanvasUpdate();
  }

  private _onConnectionDetached(e: any): void {
    const params = e.connection.getParameters();
    const inputCanvasStep: ICanvasStep = params['inputStep'];
    const canvasInput: ICanvasOperatorInput = params['input'];

    if (!inputCanvasStep || !canvasInput) {
      throw new Error('Connection data was not provided');
    }

    if (!inputCanvasStep.inputs[canvasInput.input.name]) {
      throw new Error('Step input has no such connection');
    }

    delete inputCanvasStep.inputs[canvasInput.input.name];
    this._emitCanvasUpdate();
  }

  private _onConnectionMoved(e: any): void {
    const params = e.originalTargetEndpoint.getParameters();
    const inputCanvasStep: ICanvasStep = params['inputStep'];
    const canvasInput: ICanvasOperatorInput = params['input'];

    if (!inputCanvasStep || !canvasInput) {
      throw new Error('Connection data was not provided');
    }

    delete inputCanvasStep.inputs[canvasInput.input.name];
    this._emitCanvasUpdate();
  }

  private _onStepMoved(event: DragEventCallbackOptions): void {
    const step = this._getStepByElement(event.el);
    const position = $(event.el).position();
    step.coordinates = {
      x: this._calculatePlacementCoordinate(position.left, pipelineCanvasConfig.gridSize[0]),
      y: this._calculatePlacementCoordinate(position.top, pipelineCanvasConfig.gridSize[1]),
    };
    this._emitCanvasUpdate();
  }

  private _emitCanvasUpdate(): void {
    const steps: Pipeline.StepInfo[] = this._steps.map((canvasStep) => {
      return {
        id: canvasStep.id,
        operator: canvasStep.canvasOperator.operator.id,
        inputs: canvasStep.inputs,
        params: canvasStep.params,
        coordinates: canvasStep.coordinates,
        pipelineParameters: canvasStep.pipelineParameters,
      };
    });
    this.canvasUpdated.emit(steps);
  }

  private _filterInputs(e: any): void {
    // Output and operator that were dragged
    const activeOutput: ICanvasOperatorOutput = e.endpoints[0].getParameter('output');
    const activeStep: ICanvasStep = e.endpoints[0].getParameter('outputStep');
    if (!activeStep) {
      throw new Error('Output step wasn\'t provided');
    }

    for (const step of this._steps) {
      const isOwnOutput = step === activeStep;

      for (const canvasInput of step.canvasOperator.inputs) {
        const hasConnections = canvasInput.endpoint.connections && canvasInput.endpoint.connections.length;
        const isEndpointEnabled = !isOwnOutput
          && !hasConnections
          && this._pipelineService.canConnectIOs(activeOutput.output, canvasInput.input);

        canvasInput.endpoint.setEnabled(isEndpointEnabled);
        const endpointEl = canvasInput.endpoint.canvas;
        if (!isEndpointEnabled) {
          endpointEl.classList.add('disabled');
        } else {
          endpointEl.classList.remove('disabled');
        }
      }

      for (const output of step.canvasOperator.outputs) {
        const endpointEl = output.endpoint.canvas;
        endpointEl.classList.add('disabled');
      }
    }
  }

  private _resetInputsFilter(): void {
    for (const operator of this._steps.map(_ => _.canvasOperator)) {
      for (const input of operator.inputs) {
        input.endpoint.setEnabled(true);
        const endpointEl = input.endpoint.canvas;
        endpointEl.classList.remove('disabled');
      }

      for (const output of operator.outputs) {
        const endpointEl = output.endpoint.canvas;
        endpointEl.classList.remove('disabled');
      }
    }
  }

  private _makeElementDraggable(renderer: jsPlumbInstance, el: HTMLElement): void {
    if (!this.isEditMode) {
      return;
    }

    renderer.draggable(
      el,
      {
        containment: 'parent',
        grid: pipelineCanvasConfig.gridSize,
        stop: (e) => this._onStepMoved(e),
      } as any, // workaround for jsplumb index.d.ts (this option is passed to jqueryUI but not specified in jsplumb types)
    );
  }

  private _initOperatorInput(
    renderer: jsPlumbInstance,
    input: PipelineOperator.Input,
    step: ICanvasStep,
    index: number,
    inputsCount: number,
    canvasEl: HTMLElement,
  ): ICanvasOperatorInput {
    const canvasOperatorInput: ICanvasOperatorInput = {
      input,
      endpoint: null,
    };

    const endpointParameters = {
      inputStep: step,
      input: canvasOperatorInput,
    };

    const sidePosition = (1 + index) / (1 + inputsCount);
    const endpoint = renderer.addEndpoint(
      canvasEl,
      {
        anchor: [[0, sidePosition, -1, 0]],
        parameters: endpointParameters,
        connectionType: 'basic',
        maxConnections: 1,
        enabled: this.isEditMode,
      } as any, // workaround for incorrect jsplumb types
      pipelineCanvasConfig.jsPlumbViewSettings.targetEndpoint as any, // workaround for incorrect jsplumb types,
    ) as any; // workaround for incorrect jsplumb types

    endpoint.canvas.setAttribute('title', this._getInputLabel(input));
    if (!input.optional) {
      endpoint.canvas.classList.add('required');
    }

    canvasOperatorInput.endpoint = endpoint;

    return canvasOperatorInput;
  }

  private _initOperatorOutput(
    renderer: jsPlumbInstance,
    output: PipelineOperator.Output,
    step: ICanvasStep,
    index: number,
    outputsCount: number,
    canvasEl: HTMLElement,
  ): ICanvasOperatorOutput {
    const canvasOperatorOutput: ICanvasOperatorOutput = {
      output,
      endpoint: null,
      index,
    };

    const endpointParameters = {
      outputStep: step,
      output: canvasOperatorOutput,
    };

    const sidePosition = (1 + index) / (1 + outputsCount);
    const endpoint = renderer.addEndpoint(
      canvasEl,
      {
        anchor: [[1, sidePosition, 1, 0]],
        connectionType: 'basic',
        parameters: endpointParameters,
        maxConnections: Infinity,
        enabled: this.isEditMode,
      } as any, // workaround for incorrect jsplumb types
      {
        ...pipelineCanvasConfig.jsPlumbViewSettings.sourceEndpoint,
        connector: [
          'Flowchart',
          {
            stub: [10, 20],
            gap: 10,
            cornerRadius: 5,
            alwaysRespectStubs: true,
            midpoint: sidePosition * 0.8 + 0.1,
          },
        ],
      } as any, // workaround for incorrect jsplumb types,
    ) as any; // workaround for incorrect jsplumb types

    endpoint.canvas.setAttribute('title', this._getOutputLabel(output));

    canvasOperatorOutput.endpoint = endpoint;

    return canvasOperatorOutput;
  }

  private _generateNewStepId(): string {
    const step = _.maxBy(this._steps, (step) => parseInt(step.id));
    return ((step ? parseInt(step.id) : 0) + 1).toString();
  }

  private _getStepByElement(el: HTMLElement): ICanvasStep {
    const stepId = el.dataset['stepId'];
    const step = this._steps.find((_) => _.id === stepId);
    if (!step) {
      throw new Error('Can\'t find step');
    }

    return step;
  }

  private _createStepElement(operator: PipelineOperator, originalStep?: Pipeline.Step): HTMLElement {
    const el = document.createElement('div');
    el.className = 'pipeline-operator jtk-node btn';
    if (this.isEditMode) {
      el.classList.add('btn-default');
    }

    const endpoints = Math.max(operator.inputs.length, operator.outputs.length);
    el.classList.add('pipeline-operator-size-' + (endpoints > 7 ? 'max' : endpoints));

    const icon = operator.category
      ? ((operator.category in this.categories) ? this.categories[operator.category].icon : 'unknown')
      : 'not-published';
    el.innerHTML = `<div class="operator-icon"><i class="ml-icon ml-icon-${icon}"></i></div>` +
      `<div class="operator-name">${operator.name}</div>`;

    const controlsPanel = document.createElement('div');
    controlsPanel.className = 'controls-panel';

    const infoEl = document.createElement('i');
    infoEl.className = 'control glyphicon glyphicon-info-sign';
    infoEl.addEventListener('click', (e: MouseEvent) => {
      e.stopPropagation();
      const step = this._getStepByElement(el);
      this._operatorInfoModal.show(step.canvasOperator.operator);
    });
    controlsPanel.appendChild(infoEl);

    if (operator.params.length > 0) {
      const paramsEl = document.createElement('i');
      paramsEl.className = 'control glyphicon glyphicon-cog';
      paramsEl.addEventListener('click', (e: MouseEvent) => {
        e.stopPropagation();
        const step = this._getStepByElement(el);
        this.selectedStep = step;
        this.selectedStepParams = { ...step.params };
        this.selectedStepPipelineParams = { ...step.pipelineParameters };
        this._stepParamsModal.show();
      });
      controlsPanel.appendChild(paramsEl);
    }

    if (this.isEditMode) {
      const removeEl = document.createElement('i');
      removeEl.className = 'control glyphicon glyphicon-remove';
      removeEl.addEventListener('click', (e: MouseEvent) => {
        e.stopPropagation();
        this.selectedStep = this._getStepByElement(el);
        this._stepRemovalConfirmationModal.show();
      });
      controlsPanel.appendChild(removeEl);
    }

    el.appendChild(controlsPanel);
    return el;
  }

  private _addStep(
    renderer: jsPlumbInstance,
    operator: PipelineOperator,
    coordinates: ICanvasCoordinates,
    originalStep: Pipeline.StepInfo = null,
  ) {
    const el = this._createStepElement(operator, originalStep);
    this._canvasEl.nativeElement.appendChild(el);

    const stepId = !originalStep ? this._generateNewStepId() : originalStep.id;

    const canvasOperator: ICanvasOperator = {
      el: el,
      operator: operator,
      inputs: [],
      outputs: [],
      stepId: stepId,
    };

    const step: ICanvasStep = Object.assign(
      {
        id: stepId,
        operator: operator.id,
        canvasOperator: canvasOperator,
        inputs: {},
      },
      originalStep || {
        params: {},
        pipelineParameters: {},
      },
    );
    operator.inputs.forEach((input, i) => {
      if (this.isEditMode || !input.optional || input.name in step.inputs) {
        canvasOperator.inputs.push(this._initOperatorInput(renderer, input, step, i, operator.inputs.length, el));
      }
    });
    operator.outputs.forEach((output, i) => {
      canvasOperator.outputs.push(this._initOperatorOutput(renderer, output, step, i, operator.outputs.length, el));
    });

    coordinates = coordinates || (originalStep && originalStep.coordinates);
    if (coordinates) {
      this._moveStepTo(step, coordinates);
    }

    this._makeElementDraggable(renderer, el);

    this._steps.push(step);
    el.dataset['stepId'] = step.id;

    if (!originalStep) {
      this._emitCanvasUpdate(); // avoid marking as changed for initial rendering on load
    }
  }

  private _getOutputLabel(output: PipelineOperator.Output): string {
    const caption = output.caption || output.description || 'Output';

    if (this._pipelineService.isPipelineDataTypePrimitive(output.type)) {
      return `${caption} (${output.type})`;
    }

    if (this._pipelineService.isPipelineDataTypeComplex(output.type)) {
      return `${caption} (${(output.type as PipelineDataType.Complex).definition})`;
    }

    throw new Error('Unknown output type');
  }

  private _getInputLabel(input: PipelineOperator.Input): string {
    const inputType = input.type;

    let result = input.caption || input.name;

    if (input.optional)  {
      result += ' (optional)';
    }

    if (this._pipelineService.isPipelineDataTypePrimitive(inputType)) {
      result += ` (${input.type})`;
    } else if (this._pipelineService.isPipelineDataTypeComplex(inputType)) {
      result += ` (${inputType.definition})`;
    } else {
      throw new Error('Unknown input type');
    }

    return result;
  }

  //noinspection JSMethodCanBeStatic
  private _calculatePlacementCoordinate(value: number, gridSize: number): number {
    return Math.floor((value + gridSize - 1) / gridSize) * gridSize;
  }
}
