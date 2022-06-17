import {
  Component,
  ComponentFactoryResolver,
  EventEmitter,
  Inject,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  ViewContainerRef,
} from '@angular/core';
import { ComponentRef } from '@angular/core/src/linker/component_factory';

import { Dictionary, keyBy } from 'lodash';

import { EXPERIMENT_TYPES, ExperimentTypeDefinition, IAbstractExperimentPipeline } from './experiment.interfaces';

export interface IExperimentPipelineForm {
  validityChange: EventEmitter<boolean>;
  dataChange: EventEmitter<IAbstractExperimentPipeline>;
}

@Component({
  selector: 'experiment-pipeline',
  template: ' ',
})
export class ExperimentPipelineComponent implements OnChanges, IExperimentPipelineForm {
  @Input() type: string;
  @Output() validityChange = new EventEmitter<boolean>();
  @Output() dataChange = new EventEmitter<IAbstractExperimentPipeline>();

  private _componentRef: ComponentRef<any>;
  private readonly _types: Dictionary<ExperimentTypeDefinition>;

  constructor(
    private viewContainer: ViewContainerRef,
    private componentFactoryResolver: ComponentFactoryResolver,
    @Inject(EXPERIMENT_TYPES) pipelines: ExperimentTypeDefinition[],
  ) {
    this._types = keyBy(pipelines, _ => _.type);
  }

  ngOnChanges(changes: SimpleChanges): void {
    this._compilePipelineComponent();
  }

  private _compilePipelineComponent(): void {
    const typeDefinition = this._types[this.type];

    if (this._componentRef) {
      this._componentRef.destroy();
      this._componentRef = null;
    }

    if (typeDefinition && typeDefinition.pipelineComponent) {
      const factory = this.componentFactoryResolver.resolveComponentFactory(typeDefinition.pipelineComponent);
      this._componentRef = this.viewContainer.createComponent(factory);
      const componentInstance = this._componentRef.instance as IExperimentPipelineForm;
      componentInstance.dataChange = this.dataChange;
      componentInstance.validityChange = this.validityChange;
    }
  }
}
