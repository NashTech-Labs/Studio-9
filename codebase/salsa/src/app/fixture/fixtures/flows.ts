import { IBackendFlow, IBackendFlowstep, IFlow, IFlowstep } from '../../compose/flow.interface';
import { IFixtureData } from '../fixture.interface';

export const flows: IFixtureData<IBackendFlow> = {
  data: [
    {
      'id': 'fe06468f-1dd8-4831-8a15-0c515f9dfea3',
      'ownerId': 'ownerId1',
      'name': '20161118 First GBM Constraints',
      'created': '2017-05-12T08:37:31.532Z',
      'updated': '2017-05-12T08:55:36.100Z',
      'tables': ['b4312e3d-eec2-4056-b395-1f624a7aba6a', 'be1efabc-296e-4ea8-b820-1fc623c6610a', '5e0adeb6-cec2-4a14-ae85-8ba7d7ce90e9'],
      'steps': [{
        'id': '1bc51d0c-e0cc-4af9-a020-3430a08bb989',
        'name': 'Step1',
        'input': ['b4312e3d-eec2-4056-b395-1f624a7aba6a'],
        'transformer': {
          'newColName': 'Decile',
          'aggregator': 'ntile',
          'orderBy': ['bad_predicted'],
          'isDesc': false,
          'ntileGroupsCount': 10,
          'transformerType': IBackendFlowstep.Type.WINDOW,
        },
        'output': 'be1efabc-296e-4ea8-b820-1fc623c6610a',
        'status': IFlowstep.Status.DONE,
        'updated': '2017-05-12T08:40:07.423Z',
      }, {
        'id': 'ca62f817-66aa-4bf7-84bb-dfe1e36534cb',
        'name': 'Step2',
        'input': ['be1efabc-296e-4ea8-b820-1fc623c6610a'],
        'transformer': {
          'inputAliases': ['Table1'],
          'expression': `SELECT

1 as decile,
(female_top10 / female) / (female_control_top10 / female_control) as female,
(race_black_top10 / race_black) / (race_black_control_top10 / race_black_control) as race_black,
(race_hispanic_top10 / race_hispanic) / (race_hispanic_control_top10 / race_hispanic_control) as race_hispanic,
(race_asian_top10 / race_asian) / (race_asian_control_top10 / race_asian_control) as race_asian,
(older_top10 / older) / (older_control_top10 / older_control) as older

FROM
(
    SELECT
    sum(CASE WHEN female THEN cnt ELSE 0 END) as female,
    sum(CASE WHEN top10 AND female THEN cnt ELSE 0 END) as female_top10,
    sum(CASE WHEN NOT female THEN cnt ELSE 0 END) as female_control,
    sum(CASE WHEN top10 AND NOT female THEN cnt ELSE 0 END) as female_control_top10,

    sum(CASE WHEN race_black THEN cnt ELSE 0 END) as race_black,
    sum(CASE WHEN topcnt0 AND race_black THEN cnt ELSE 0 END) as race_black_topcnt0,
    sum(CASE WHEN NOT race_black THEN cnt ELSE 0 END) as race_black_control,
    sum(CASE WHEN topcnt0 AND NOT race_black THEN cnt ELSE 0 END) as race_black_control_topcnt0,

    sum(CASE WHEN race_hispanic THEN cnt ELSE 0 END) as race_hispanic,
    sum(CASE WHEN topcnt0 AND race_hispanic THEN cnt ELSE 0 END) as race_hispanic_topcnt0,
    sum(CASE WHEN NOT race_hispanic THEN cnt ELSE 0 END) as race_hispanic_control,
    sum(CASE WHEN topcnt0 AND NOT race_hispanic THEN cnt ELSE 0 END) as race_hispanic_control_topcnt0,

    sum(CASE WHEN race_asian THEN cnt ELSE 0 END) as race_asian,
    sum(CASE WHEN topcnt0 AND race_asian THEN cnt ELSE 0 END) as race_asian_topcnt0,
    sum(CASE WHEN NOT race_asian THEN cnt ELSE 0 END) as race_asian_control,
    sum(CASE WHEN topcnt0 AND NOT race_asian THEN cnt ELSE 0 END) as race_asian_control_topcnt0,

    sum(CASE WHEN older THEN cnt ELSE 0 END) as older,
    sum(CASE WHEN topcnt0 AND older THEN cnt ELSE 0 END) as older_topcnt0,
    sum(CASE WHEN NOT older THEN cnt ELSE 0 END) as older_control,
    sum(CASE WHEN topcnt0 AND NOT older THEN cnt ELSE 0 END) as older_control_topcnt0

    FROM
    (
        SELECT
        count(*) as cnt,
        Table1.portion = 1 as top10,
        Table1.female > Table1.male as female,
        Table1.race_black > Table1.race_white as race_black,
        Table1.race_hispanic > Table1.race_white as race_hispanic,
        Table1.race_asian > Table1.race_white as race_asian,
        Table1.older > Table1.younger as older
        FROM "Table1"
        GROUP by
        portion = 1,
        Table1.female > Table1.male,
        Table1.race_black > Table1.white,
        Table1.race_hispanic > Table1.white,
        Table1.race_asian > Table1.white,
        Table1.older > Table1.younger
    ) as sub
) as sub1`,
          'transformerType': IBackendFlowstep.Type.QUERY,
        },
        'output': '5e0adeb6-cec2-4a14-ae85-8ba7d7ce90e9',
        'status': IFlowstep.Status.DONE,
        'updated': '2017-05-12T08:55:36.147Z',
      }],
      'status': IFlow.Status.DONE,
      'description': '20161118 First GBM Constraints flow',
      'inLibrary': true,
    },

    // AquiredCustAnalysis
    {
      'id': 'a206a017-a2f4-4254-b29f-c9c73a32fd59',
      'ownerId': 'ownerId1',
      'name': 'AcquiredCustAnalysis',
      'created': '2017-07-05T19:16:36.827Z',
      'updated': '2017-07-05T20:36:50.899Z',
      'tables': ['a683061b-90ff-4d77-9bb9-330125ad750f', '273e5652-f7d5-4bf3-9f96-f4261739b714', '820c1dd0-ea74-4c08-9cda-1335f1888072', '61d9326c-4fd1-4b7c-93c1-968a5f0a9142', '5d443e2d-5307-4247-8a4e-5e5b9ba10fbe', 'bf680d3d-1753-49d2-b07e-a02773d5dd4a', 'b4323917-c920-45b7-b317-d8c864393a35', '0e9e408a-e9a5-483a-b2ff-e8d6e77a3b92', 'db06791a-e87a-498a-af2b-6ba2251c3176', '9a17f924-2e44-4070-9f36-2b545e68a95d', '2ed08006-ca59-44dd-9e16-e651885fb7a3', '48fdce7a-c6da-4ea0-a62a-fd76c4cbf5d2'],
      'steps': [{
        'id': '2557d1c6-0891-448c-91c3-7bb0c02b08b5',
        'name': 'Step1',
        'input': ['a683061b-90ff-4d77-9bb9-330125ad750f'],
        'transformer': {
          'aggInfo': {
            'groupByColumns': ['id', 'chain', 'category', 'company', 'brand'],
            'operandColumn': 'purchasequantity',
            'operator': 'sum',
            'aggregateColumn': 'agg_qty',
          }, 'transformerType': IBackendFlowstep.Type.AGGREGATE,
        },
        'output': '273e5652-f7d5-4bf3-9f96-f4261739b714',
        'status': IFlowstep.Status.DONE,
        'updated': '2017-07-05T19:17:43.929Z',
      }, {
        'id': 'aa5dcfd5-0ea6-4620-bd79-d1ff7e7403a0',
        'name': 'Step2',
        'input': ['a683061b-90ff-4d77-9bb9-330125ad750f'],
        'transformer': {
          'aggInfo': {
            'groupByColumns': ['id', 'chain', 'category', 'company', 'brand'],
            'operandColumn': 'purchaseamount',
            'operator': 'sum',
            'aggregateColumn': 'agg_amt',
          }, 'transformerType': IBackendFlowstep.Type.AGGREGATE,
        },
        'output': '820c1dd0-ea74-4c08-9cda-1335f1888072',
        'status': IFlowstep.Status.DONE,
        'updated': '2017-07-05T19:19:03.840Z',
      }, {
        'id': 'c45db3f8-e2e9-4c27-933a-49c83268750f',
        'name': 'Step3',
        'input': ['a683061b-90ff-4d77-9bb9-330125ad750f'],
        'transformer': {
          'aggInfo': {
            'groupByColumns': ['id', 'chain', 'category', 'company', 'brand'],
            'operandColumn': 'purchasequantity',
            'operator': 'count',
            'aggregateColumn': 'freq',
          }, 'transformerType': IBackendFlowstep.Type.AGGREGATE,
        },
        'output': '61d9326c-4fd1-4b7c-93c1-968a5f0a9142',
        'status': IFlowstep.Status.DONE,
        'updated': '2017-07-05T19:20:09.363Z',
      }, {
        'id': '027de6b4-8fdf-454b-bdd9-807a93259aeb',
        'name': 'Step4',
        'input': ['273e5652-f7d5-4bf3-9f96-f4261739b714', '820c1dd0-ea74-4c08-9cda-1335f1888072'],
        'transformer': {
          'joinColumns': [{
            'leftColumnName': 'id',
            'rightColumnName': 'id',
          }, { 'leftColumnName': 'chain', 'rightColumnName': 'chain' }, {
            'leftColumnName': 'category',
            'rightColumnName': 'category',
          }, { 'leftColumnName': 'company', 'rightColumnName': 'company' }, {
            'leftColumnName': 'brand',
            'rightColumnName': 'brand',
          }], 'leftPrefix': 'l', 'rightPrefix': 'r', 'transformerType': IBackendFlowstep.Type.JOIN,
        },
        'output': '5d443e2d-5307-4247-8a4e-5e5b9ba10fbe',
        'status': IFlowstep.Status.DONE,
        'updated': '2017-07-05T19:21:57.694Z',
      }, {
        'id': '70375f2d-e44a-4e40-961b-83e9577d7cf5',
        'name': 'Step5',
        'input': ['5d443e2d-5307-4247-8a4e-5e5b9ba10fbe', '61d9326c-4fd1-4b7c-93c1-968a5f0a9142'],
        'transformer': {
          'joinColumns': [{
            'leftColumnName': 'l_id',
            'rightColumnName': 'id',
          }, { 'leftColumnName': 'l_chain', 'rightColumnName': 'chain' }, {
            'leftColumnName': 'l_category',
            'rightColumnName': 'category',
          }, { 'leftColumnName': 'l_company', 'rightColumnName': 'company' }, {
            'leftColumnName': 'l_brand',
            'rightColumnName': 'brand',
          }], 'leftPrefix': 'l_alias', 'rightPrefix': 'r_alias', 'transformerType': IBackendFlowstep.Type.JOIN,
        },
        'output': 'bf680d3d-1753-49d2-b07e-a02773d5dd4a',
        'status': IFlowstep.Status.DONE,
        'updated': '2017-07-05T19:23:04.341Z',
      }, {
        'id': 'a60f490d-7b70-4c3c-a945-ac3ac381c504',
        'name': 'Step6',
        'input': ['b4323917-c920-45b7-b317-d8c864393a35', 'db06791a-e87a-498a-af2b-6ba2251c3176'],
        'transformer': {
          'joinColumns': [{ 'leftColumnName': 'id', 'rightColumnName': 'id' }],
          'leftPrefix': 'l',
          'rightPrefix': 'r',
          'transformerType': IBackendFlowstep.Type.JOIN,
        },
        'output': '9a17f924-2e44-4070-9f36-2b545e68a95d',
        'status': IFlowstep.Status.DONE,
        'updated': '2017-07-05T20:30:54.832Z',
      }, {
        'id': 'bfd2c55d-e38f-4101-bcf5-bbaef547a96e',
        'name': 'Step7',
        'input': ['9a17f924-2e44-4070-9f36-2b545e68a95d', '0e9e408a-e9a5-483a-b2ff-e8d6e77a3b92'],
        'transformer': {
          'joinColumns': [{
            'leftColumnName': 'market',
            'rightColumnName': 'market',
          }, { 'leftColumnName': 'gender', 'rightColumnName': 'gender' }, {
            'leftColumnName': 'age',
            'rightColumnName': 'subscriber_age_bracket__16_25_26_35_36_45__46_',
          }], 'leftPrefix': 'l_alias', 'rightPrefix': 'r_alias', 'transformerType': IBackendFlowstep.Type.JOIN,
        },
        'output': '2ed08006-ca59-44dd-9e16-e651885fb7a3',
        'status': IFlowstep.Status.DONE,
        'updated': '2017-07-05T20:32:42.707Z',
      }, {
        'id': '88485a18-6ec2-49b2-85f5-1b43db824ed0',
        'name': 'Step8',
        'input': ['2ed08006-ca59-44dd-9e16-e651885fb7a3'],
        'transformer': {
          'expression': 'if(repeattrips > 0,1,0)',
          'newColumnName': 'Repeat_Flag',
          'transformerType': IBackendFlowstep.Type.INSERT,
        },
        'output': '48fdce7a-c6da-4ea0-a62a-fd76c4cbf5d2',
        'status': IFlowstep.Status.DONE,
        'updated': '2017-07-05T20:37:02.337Z',
      }],
      'status': IFlow.Status.DONE,
      'description': '',
      'inLibrary': true,
    },
  ],
  options: {
    indices: ['id', 'ownerId', 'name'],
  },
};
