import {
  Component,
  ComponentFactoryResolver,
  Inject,
  Input,
  OnChanges,
  SimpleChanges,
  ViewContainerRef,
} from '@angular/core';
import { ComponentRef } from '@angular/core/src/linker/component_factory';

import { Dictionary, keyBy } from 'lodash';

import {
  EXPERIMENT_TYPES,
  ExperimentType,
  ExperimentTypeDefinition,
  IAbstractExperimentPipeline,
  IAbstractExperimentResult,
  IExperimentFull,
} from './experiment.interfaces';


export interface IExperimentResultView<
  P extends IAbstractExperimentPipeline = IAbstractExperimentPipeline,
  R extends IAbstractExperimentResult = IAbstractExperimentResult,
> {
  experiment: IExperimentFull<P, R>;
}

@Component({
  selector: 'experiment-result',
  template: ' ',
})
export class ExperimentResultComponent implements OnChanges, IExperimentResultView {
  @Input() type: ExperimentType;
  @Input() experiment: IExperimentFull;

  private _componentRef: ComponentRef<any>;
  private readonly _types: Dictionary<ExperimentTypeDefinition>;

  constructor(
    private viewContainer: ViewContainerRef,
    private componentFactoryResolver: ComponentFactoryResolver,
    @Inject(EXPERIMENT_TYPES) typeDefinitions: ExperimentTypeDefinition[],
  ) {
    this._types = keyBy(typeDefinitions, _ => _.type);
  }

  ngOnChanges(changes: SimpleChanges): void {
    this._compileResultComponent();
  }

  private _compileResultComponent(): void {
    const typeDefinition = this._types[this.type];

    if (this._componentRef) {
      this._componentRef.destroy();
      this._componentRef = null;
    }

    if (typeDefinition && typeDefinition.resultComponent) {
      const factory = this.componentFactoryResolver.resolveComponentFactory(typeDefinition.resultComponent);
      this._componentRef = this.viewContainer.createComponent(factory);
      const componentInstance = this._componentRef.instance as IExperimentResultView;
      componentInstance.experiment = this.experiment;
    }
  }
}
