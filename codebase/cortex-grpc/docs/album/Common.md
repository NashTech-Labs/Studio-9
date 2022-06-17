[Album Common Messages](../../src/main/proto/cortex/api/job/album/common.proto)
======

Image
------
| Field name | Description | Mandatory |
|---|---|---|
| file_path | Path to the file. Always relative to some prefix | yes |
| reference_id | Reference identifier to the image for caller to receive later in response | no |
| file_size | Size of the file for the image | no |
| display_name | DisplayName for the image, usually the name given to the image in UI | no |

TagArea
------
| Field name | Description | Mandatory |
|---|---|---|
| top | Top indent from the top-left corner | yes |
| left | Left indent from the top-left corner | yes |
| height | Height | yes |
| width | Width | yes |

Tag
------
| Field name | Description | Mandatory |
|---|---|---|
| label | Label | yes |
| area | Image area where this tag is located. Does not make sense for classification | no |

TaggedImage
------
| Field name | Description | Mandatory |
|---|---|---|
| image | Reference to the raw image | yes |
| tags | Sequence of tags, associated with an image | yes |
