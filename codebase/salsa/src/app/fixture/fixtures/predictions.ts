import { IPrediction, IPredictionStatus } from '../../play/prediction.interface';
import { IFixtureData } from '../fixture.interface';

export const predictions: IFixtureData<IPrediction> = {
  data: [
    {
      id: 'model_9_prediction',
      ownerId: 'ownerId1',
      modelId: 'model_9',
      name: 'Repeat_Purchase_Prediction',
      status: IPredictionStatus.DONE,
      input: '311643d5-eec5-4102-aab6-62bf3f3ae5ed',
      output: 'model_9_predicted',
      columnMappings: [
        { sourceColumn: 'category', mappedColumn: 'category' },
        { sourceColumn: 'company', mappedColumn: 'company' },
        { sourceColumn: 'brand', mappedColumn: 'brand' },
        { sourceColumn: 'l_offer', mappedColumn: 'l_offer' },
        { sourceColumn: 'quantity', mappedColumn: 'quantity' },
        { sourceColumn: 'offervalue', mappedColumn: 'offervalue' },
        { sourceColumn: 'agg_qty', mappedColumn: 'agg_qty' },
        { sourceColumn: 'agg_amt', mappedColumn: 'agg_amt' },
        { sourceColumn: 'l_alias_gender', mappedColumn: 'l_alias_gender' },
        { sourceColumn: 'age', mappedColumn: 'age' },
        { sourceColumn: 'state', mappedColumn: 'state' },
        { sourceColumn: '__of_subscriber', mappedColumn: '__of_subscriber' },
        { sourceColumn: 'avg___daily_calls_received', mappedColumn: 'avg___daily_calls_received' },
      ],
      created: '2017-01-01 01:01',
      updated: '2017-05-05 05:05',
    },
  ],
  options: {
    indices: ['id'],
  },
};
