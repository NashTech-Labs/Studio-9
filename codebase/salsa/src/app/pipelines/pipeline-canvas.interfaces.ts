// Internal interfaces for canvas

import {
  Pipeline,
  PipelineOperator,
} from './pipeline.interfaces';

export interface ICanvasCoordinates {
  x: number;
  y: number;
}

export interface ICanvasOperatorInput {
  input: PipelineOperator.Input;
  endpoint: any; // hack for jsplumb types
}

export interface ICanvasOperatorOutput {
  output: PipelineOperator.Output;
  endpoint: any; // hack for jsplumb types
  index: number;
}

export interface ICanvasOperator {
  el: HTMLElement;
  operator: PipelineOperator;
  inputs: ICanvasOperatorInput[];
  outputs: ICanvasOperatorOutput[];
  stepId: string;
}

export interface ICanvasStep extends Pipeline.StepInfo {
  canvasOperator: ICanvasOperator;
}

