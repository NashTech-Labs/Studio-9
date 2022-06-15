from collections import OrderedDict
from typing import List

from deepcortex.ml.cv.transfer_learning.classifier.non_neural_classifier import NonNeuralClassifier
from deepcortex.ml.cv.transfer_learning.detector.detector import Detector

from deepcortex.ml.cv.transfer_learning.cv_model_tl_primitive import CVModelTLPrimitive


@CVModelTLPrimitive(name="test detector",
                  parameters={"some_str": {"conditions": {"value_name": {"values": [1, 2.5], "min": 3.5}}}},
                  description="test detector description"
                  )
class TestDetector(Detector):
    def __init__(self):
        pass

    def get_state_dict(self) -> OrderedDict:
        pass

    def set_state_dict(self, state_dict: OrderedDict, strict=True):
        pass

    @property
    def classes(self):
        pass

    def forward(self, *input):
        pass

    def configure(self, some_str: str, value_name: float):
        pass

@CVModelTLPrimitive(name="test non-neural classifier",
                  parameters={},
                  description="test non-neural classifier description"
                  )
class TestNonNeuralClassifier(NonNeuralClassifier):
    def fit(self, x_train, y_train):
        pass

    def predict(self, x_test):
        pass

    def predict_proba(self, x_test):
        pass

    def get_state_dict(self) -> OrderedDict:
        pass

    def set_state_dict(self, state_dict: OrderedDict):
        pass

    def configure(self):
        pass

    @property
    def classes(self) -> List[str]:
        pass
