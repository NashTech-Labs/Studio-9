import { Injectable } from '@angular/core';

import {
  TObjectId,
} from '../core/interfaces/common.interface';

import {
  ICanvasCoordinates,
  ICanvasStep,
} from './pipeline-canvas.interfaces';

interface RowData {
  id: TObjectId;
  width: number;
  height: number;
}

interface CanvasData {
  width: number;
  height: number;
  gridStepX: number;
  gridStepY: number;
  marginX: number;
  marginY: number;
}

@Injectable()
export class PipelineOperatorPositioningService {
  constructor() {
  }

  calculateStepPositions(
    steps: ICanvasStep[],
    canvasEl: HTMLElement,
    gridSize: number[],
  ): {[stepId: string]: ICanvasCoordinates} {
    const canvasRect = canvasEl.getBoundingClientRect();
    const canvasData: CanvasData = {
      width: canvasRect.width,
      height: canvasRect.height,
      gridStepX: gridSize[0],
      gridStepY: gridSize[1],
      marginX: gridSize[0] * 4, // space between operators
      marginY: gridSize[1] * 4, // space between operators
    };

    const nonPositionedSteps = steps.filter(_ => !_.coordinates);
    const positionedSteps = steps.filter(_ => _.coordinates);

    const rows = this._organizeStepsIntoRows(nonPositionedSteps, canvasData);
    const nextAvailableY = this._findNextAvailableY(positionedSteps, canvasData);

    return this._placeOrganizedSteps(rows, nextAvailableY, canvasData);
  }

  private _organizeStepsIntoRows(
    nonPositionedSteps: ICanvasStep[],
    canvasData: CanvasData,
  ): RowData[][] {
    const rows = [];
    let row;
    let rowWidth = canvasData.marginX / 2;

    for (const step of nonPositionedSteps) {
      const stepRect = step.canvasOperator.el.getBoundingClientRect();
      const width = stepRect.width + canvasData.marginX;
      const newRowWidth = rowWidth + width;

      if (newRowWidth > canvasData.width || !row) {
        row = [];
        rows.push(row);
        rowWidth = canvasData.marginX / 2;
      }
      row.push({
        width: stepRect.width,
        height: stepRect.height,
        id: step.id,
      });
      rowWidth += width;
    }

    return rows;
  }

  private _findNextAvailableY(
    positionedSteps: ICanvasStep[],
    canvasData: CanvasData,
  ): number {
    return positionedSteps.reduce(
      (acc: number, step: ICanvasStep) => {
        const stepRect = step.canvasOperator.el.getBoundingClientRect();
        // Ignore steps outside of screen width
        if (step.coordinates.x > canvasData.width) {
          return acc;
        }
        return Math.max(acc, step.coordinates.y + stepRect.height + canvasData.marginY);
      },
      canvasData.marginY / 2,
    );
  }

  private _placeOrganizedSteps(
    rows: RowData[][],
    nextAvailableY: number,
    canvasData: CanvasData,
  ): {[stepId: string]: ICanvasCoordinates} {
    const positions = {};
    let rowY = nextAvailableY;
    let maxOperatorHeightInRow = 0;

    rows.forEach((row, i) => {
      let rowWidth = canvasData.marginX / 2;
      row = i % 2 ? row.reverse() : row;
      row.forEach(step => {
        maxOperatorHeightInRow = Math.max(maxOperatorHeightInRow, step.height);
        positions[step.id] = {
          x: rowWidth,
          y: rowY,
        };
        rowWidth += step.width + canvasData.marginX;
      });
      rowY += maxOperatorHeightInRow + canvasData.marginY;
    });

    return positions;
  }
}
