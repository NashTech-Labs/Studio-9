import { OverlaySpec } from 'jsplumb';

export const pipelineCanvasConfig = {
  gridSize: [20, 20],
  jsPlumbDefaults: {
    Container: '.pipeline-container',
    ConnectionOverlays: <OverlaySpec[]> [
      [
        'Arrow',
        {
          location: 1,
          visible: true,
          width: 11,
          length: 11,
        },
      ],
    ],
  },
  jsPlumbViewSettings: {
    basicConnectionType: {
    },
    connectorPaintStyle: {
      stroke: '#0d47a1',
    },
    connectorHoverStyle: {
      stroke: '#216477',
    },
    endpointHoverStyle: {
    },
    sourceEndpoint: {
      endpoint: 'Dot',
      paintStyle: {
        stroke: '#0d47a1',
        fill: 'transparent',
        radius: 7,
        strokeWidth: 1,
      },
      isSource: true,
      maxConnections: 1,
      connectorStyle: this.connectorPaintStyle,
      hoverPaintStyle: this.endpointHoverStyle,
      connectorHoverStyle: this.connectorHoverStyle,
      dragOptions: {},
      overlays: [
        [
          'Label',
          {
            location: [0.5, 1.5],
            label: 'Drag',
            cssClass: 'endpointSourceLabel',
            visible: false,
          },
        ],
      ],
    },
    targetEndpoint: {
      endpoint: 'Dot',
      paintStyle: {
        fill: '#0d47a1',
        radius: 7,
      },
      hoverPaintStyle: this.endpointHoverStyle,
      maxConnections: 1,
      dropOptions: { hoverClass: 'hover', activeClass: 'active' },
      isTarget: true,
      overlays: [
        [
          'Label',
          {
            location: [0.5, -0.5],
            label: 'Drop',
            cssClass: 'endpointTargetLabel',
            visible: false,
          },
        ],
      ],
    },
  },
};

