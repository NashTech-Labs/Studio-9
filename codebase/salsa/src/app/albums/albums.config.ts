import { describeEnum } from '../utils/misc';

import { IAlbum } from './album.interface';

export interface IAugmentationParamDefinition {
  description?: string;
  caption?: string;
  type: 'number' | 'boolean' | 'enum';
  min?: number;
  max?: number;
  step?: number;
  isInteger?: boolean;
  options?: any[];
  optionNames?: string[];
  multiple?: boolean;
}

export type IAugmentationSteps = {
  [K in IAlbum.AugmentationType]: { [P: string]: IAugmentationParamDefinition };
};

export const albumsConfig = {
  album: {
    augmentationType: describeEnum(IAlbum.AugmentationType, {
      labels: {
        ROTATION: 'Rotation',
        SHEARING: 'Shearing',
        NOISING: 'Noising',
        ZOOM_IN: 'Zoom In',
        ZOOM_OUT: 'Zoom Out',
        OCCLUSION: 'Occlusion',
        TRANSLATION: 'Translation',
        SALT_PEPPER: 'Salt & Pepper',
        MIRRORING: 'Mirroring',
        CROPPING: 'Cropping',
        BLURRING: 'Blurring',
        PHOTO_DISTORT: 'Photometric Distort',
      },
      description: {
        ROTATION: 'Rotate images by a specific angle around it center.',
        SHEARING: 'Shear images by a specific angle along its width (i.e. the horizontal axis).',
        NOISING: 'Add white noise sampled from a zero-centered Normal distribution to each pixel of images.',
        ZOOM_IN: 'Dilate (magnify) image by a specific factor while preserving its aspect ratio.',
        ZOOM_OUT: 'Shrink image by a specific factor while preserving its aspect ratio.',
        OCCLUSION: 'Occlude images by a zeroing pixels inside a randomly chosen area within the target\'s ' +
          'bounding box. For SAR albums where each image contains a single target (e.g. MSTAR), the ' +
          'bounding box can be automatically calculated. Non-SAR albums which have no bounding box annotations are ' +
          'unchanged.',
        TRANSLATION: 'Translate images by a specific amount. Translation direction is chosen randomly so that over ' +
          'an album of images the translation of a given magnitude is isotropic.',
        SALT_PEPPER: 'Knockout random pixels from images ' +
          'by replacing them with either white <i>( salt )</i> or black <i>( pepper )</i> pixels.',
        MIRRORING: 'Reflect images along one or both of ' +
          'their edges.',
        CROPPING: 'Crop image by extracting rectangular ' +
          'sections that have the same aspect ' +
          'ratio as the original image. Multiple ' +
          'crops can be extracted from any ' +
          'image. The location of each cropped ' +
          'area is random.',
        BLURRING: 'Apply Gaussian blurring to images. ' +
          'This is done by convolving the image ' +
          'with a Gaussian kernel that is ' +
          'truncated at 4 standard deviations.',
        PHOTO_DISTORT: 'Apply photometric distortion to color ' +
          '(RGB) images (grayscale images are ' +
          'unchanged). This transform ' +
          'randomly distorts the hue , saturation ' +
          'and value (HSV) of color images . Alt ' +
          'hough this transformation is ' +
          'performed in HSV space, the ' +
          'resulting image will be RGB.',
        BLOAT_FACTOR: {
          COMPOSE: 'Bloat factor used to increase the size of the entire album. ' +
            'Capped at the number of selected DA transformations.',
          DA_TRANSFORM: 'Bloat factor by which the size of the set of images this ' +
            'transform applies on is increased. Capped at the number of parameters given to this DA transformation.',
          EFFECTIVE: 'The system generated net bloat factor resulting from ' +
            'the combination of the compose bloat factor and all DA transform ' +
            'bloat factors. The number of <em>newly generated images</em> in the output album equals the ' +
            'effective bloat factor times the input album size.',
        },
      },
      parameters: <IAugmentationSteps> {
        ROTATION: {
          angles: {
            description: 'A list of angles in degrees. Each angle is used to rotate a random subset of album images.',
            caption: 'Angles',
            type: 'number',
            min: 0,
            max: 360,
            multiple: true,
          },
          resize: {
            description: '' +
              '<i>True</i> produces an image that completely contains' +
              'the transformed image without any truncation.<br><br>' +
              '<i>False</i> will produce an image of the same size as' +
              'the input by truncating the transformed image.',
            caption: 'Resize',
            type: 'boolean',
          },
        },
        SHEARING: {
          angles: {
            description: 'A list of angles in degrees. Each angle is used to shear a random subset of album ' +
              'images along its width (i.e. the horizontal axis).',
            caption: 'Angles',
            type: 'number',
            min: 0,
            max: 60,
            multiple: true,
          },
          resize: {
            description: '' +
              '<i>True</i> produces an image that completely contains' +
              'the transformed image without any truncation.<br><br>' +
              '<i>False</i> will produce an image of the same size as' +
              'the input by truncating the transformed image.',
            caption: 'Resize',
            type: 'boolean',
          },
        },
        NOISING: {
          noiseSignalRatios: {
            description: 'A list of floating point values that are ratios between the standard deviation of image<br>' +
              'pixel values and the standard deviation of the normal ' +
              'distribution from which noise is sampled. Increasing this ratio increases the standard<br>' +
              'deviation of the noise distribution. Each ratio is ' +
              'used to generate noise for a random subset of ' +
              'album images.',
            caption: 'Noise/Signal ratios',
            type: 'number',
            min: 0,
            max: 1,
            multiple: true,
          },
        },
        ZOOM_IN: {
          ratios: {
            description: 'A list of magnification values. Each magnification is used to dilate a random subset ' +
              'of album images.',
            caption: 'Ratios',
            type: 'number',
            min: 1,
            multiple: true,
          },
          resize: {
            description: '' +
              '<i>True</i> produces an image that completely contains' +
              'the transformed image without any truncation.<br><br>' +
              '<i>False</i> will produce an image of the same size as' +
              'the input by truncating the transformed image.',
            caption: 'Resize',
            type: 'boolean',
          },
        },
        ZOOM_OUT: {
          ratios: {
            description: 'A list of \'shrink factor\' values. Each \'shrink factor\' is used to shrink a random ' +
              'subset of album images.',
            caption: 'Ratios',
            type: 'number',
            min: 0,
            max: 1,
            multiple: true,
          },
          resize: {
            description: '<i>True</i> produces an image that exactly contains the ' +
              'transformed image. <br><br>' +
              '<i>False</i> will produce an image of the same size as ' +
              'the input by zero padding the shrunk transformed ' +
              'image.',
            caption: 'Resize',
            type: 'boolean',
          },
        },
        OCCLUSION: {
          occAreaFractions: {
            description: 'A list of values which are fractions of area inside ' +
              'bounding boxes that are occluded. The occluded ' +
              'region is a rectangle with the same aspect ratio as ' +
              'the enclosing bounding box. Each fraction is used ' +
              'to occlude a random subset of album images.',
            caption: 'Occlusion Area Fractions',
            type: 'number',
            min: 0,
            max: 1,
            multiple: true,
          },
          mode: {
            description: '<i>"ZERO"</i>: the occluded region pixels are zeroed. <br><br>' +
              '<i>"BACKGROUND"</i>: the occluded region pixels are ' +
              'replaced by pixels that don\'t lie inside any ' +
              'bounding box (currently only available when is_sar ' +
              '_album is True).',
            caption: 'Mode',
            type: 'enum',
            options: ['BACKGROUND', 'ZERO'],
          },
          isSARAlbum: {
            description: '<i>True</i> Indicates that the entire album consists of\n' +
              'single target SAR images (which are grayscale).',
            caption: 'Is SAR album',
            type: 'boolean',
          },
          targetWindowSize: {
            description: 'Max size of the square target in single target SAR ' +
              'images. Only used when is_sar_album is True. ' +
              'This is used to automatically detect the target\'s ' +
              'bounding box in a SAR image.',
            caption: 'Target widow size',
            type: 'number',
            min: 10,
            step: 1,
            isInteger: true,
          },
        },
        TRANSLATION: {
          translateFractions: {
            description: 'A list of \'translate fraction\' values each of which is used to translate a random ' +
              'subset of album images.The translate fraction is a ratio between the size of image along an axis to the' +
              'amount of its translation along that axis.',
            caption: 'Translate Fractions',
            type: 'number',
            min: 0,
            max: 0.5,
            multiple: true,
          },
          mode: {
            description: 'Specifies how padding is done. <i>\'CONSTANT\'</i> produces 0-padding, ' +
              'while <i>\'REFLECT\'</i> uses pixels reflected along an edge to pad.',
            caption: 'Mode',
            type: 'enum',
            options: ['REFLECT', 'CONSTANT'],
          },
          resize: {
            description: '' +
              '<i>True</i> produces an image that completely contains the transformed image without any truncation. ' +
              '<i>(not recommended for translation)</i><br><br>' +
              '<i>False</i> will produce an image of the same size as' +
              'the input by truncating the transformed image.',
            caption: 'Resize',
            type: 'boolean',
          },
        },
        SALT_PEPPER: {
          knockoutFractions: {
            description: 'A list of values that specifies the fraction of image ' +
              'pixels that will be knocked out. Each knockout ' +
              'fraction is applied to a random subset of album ' +
              'images.',
            caption: 'Knockout Fractions',
            type: 'number',
            min: 0,
            max: 1,
            multiple: true,
          },
          pepperProbability: {
            description: 'Specifies the fraction of knocked out pixels that ' +
              'will be \'peppers\'. A value of pepper_probability = 0 ' +
              'is implies pure \'salting\' while pepper_probability = ' +
              '0 is pure \'peppering\'.',
            caption: 'Pepper Probability',
            type: 'number',
            min: 0,
            max: 1,
          },
        },
        MIRRORING: {
          flipAxes: {
            description: 'A list of codes specifying the edge about which the ' +
              'image will be flipped.<br> Each \'flip axis\' is applied to a ' +
              'random subset of album images. <br><br>Codes are:<br>' +
              '1.horizontal axis<br>' +
              '2.vertical axis<br>' +
              '3.both axes<br>',
            caption: 'Flip Axes',
            type: 'enum',
            multiple: true,
            options: [0, 1, 2],
            optionNames: ['Horizontal', 'Vertical', 'Both'],
          },
        },
        CROPPING: {
          cropAreaFractions: {
            description: 'A list of values specifying fraction of the area of an ' +
              'image that will be in the cropped image. The ' +
              'cropped image will have the same aspect ratio as ' +
              'the original. Its location in the original image is ' +
              'randomly chosen. Each \'crop area fraction\' is ' +
              'applied to a random subset of album images.',
            caption: 'Crop Area Fractions',
            type: 'number',
            min: 0,
            max: 1,
            multiple: true,
          },
          cropsPerImage: {
            description: 'Number of crops to be extracted from each image. ' +
              'All crops from an image will have the same area, ' +
              'but they will be drawn from different randomly ' +
              'chosen locations.',
            caption: 'Crops per Image',
            type: 'number',
            min: 1,
            step: 1,
            isInteger: true,
          },
          resize: {
            description: '<i>True</i> causes the output image to be truncated to ' +
              'only include the cropped region. <br><br>' +
              '<i>False</i> produces an output that has the same size ' +
              'as the input image. The region removed by cropping has its pixels zeroed.',
            caption: 'Resize',
            type: 'boolean',
          },
        },
        BLURRING: {
          sigmas: {
            description: 'A list of values that specify the standard deviation ' +
              '(sigma) of the Gaussian kernel. Each sigma is ' +
              'applied to a random subset of album images.',
            caption: 'Sigma values',
            type: 'number',
            min: 0,
            multiple: true,
          },
        },
        PHOTO_DISTORT: {
          alphaMin: {
            description: 'The minimum and maximum alpha values which ' +
              'specify the bounds within which the \'alpha\' factor ' +
              'is randomly chosen. For each image in an album ' +
              'two alpha factors are chosen to distort the ' +
              'image\'s contrast and saturation.',
            caption: 'min Alpha',
            type: 'number',
            min: 0,
          },
          alphaMax: {
            description: 'The minimum and maximum alpha values which ' +
              'specify the bounds within which the \'alpha\' factor ' +
              'is randomly chosen. For each image in an album ' +
              'two alpha factors are chosen to distort the ' +
              'image\'s contrast and saturation.',
            caption: 'max Alpha',
            type: 'number',
            min: 0,
          },
          deltaMax: {
            description: 'The maximum absolute value of the \'delta\' that is ' +
              'used to additively distort an image\'s hue. The ' +
              'actual delta is randomly chosen so that its ' +
              'absolute value is less that \'delta_max\'.',
            caption: 'max Delta',
            type: 'number',
            min: 0,
          },
        },
      },
    }),
  },
};
