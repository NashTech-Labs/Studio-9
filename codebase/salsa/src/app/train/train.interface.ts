export type IConfusionMatrix = IConfusionMatrixRow[];

export interface IConfusionMatrixRow {
  predicted: number; // references class label
  actual: number; // references class label
  count: number;
}
