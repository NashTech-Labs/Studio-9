import logging
from collections import OrderedDict
from typing import Iterator, List, Iterable

import numpy as np

from deepcortex.ml.cv.base_model import BaseModel, ClassificationResult, ClassificationProbaResult


class TestCustomModel(BaseModel):

    def __init__(self):
        self.logger = logging.getLogger(f'{self.__module__}.{self.__class__.__name__}')
        self.logger.info('TestCustomModel.__init__()')

    def get_state_dict(self) -> OrderedDict:
        self.logger.info('TestCustomModel.get_state_dict()')
        return OrderedDict()

    def set_state_dict(self, state_dict: OrderedDict):
        self.logger.info('TestCustomModel.set_state_dict()')

    def predict(self, data: Iterable[np.array]) -> Iterator[ClassificationResult]:
        self.logger.info('TestCustomModel.predict()')
        for _ in data:
            yield ('something', 0.6)

    def predict_proba(
            self,
            data: Iterable[np.ndarray]
    ) -> Iterator[ClassificationProbaResult]:
        self.logger.info('TestCustomModel.predict_proba()')
        for _ in data:
            yield [0.6 for _ in self.classes]

    def predict_proba(
            self,
            data: Iterable[np.ndarray]
    ) -> Iterator[ClassificationProbaResult]:
        for _ in data:
            yield [0.6 for _ in self.classes]

    @property
    def classes(self) -> List[str]:
        self.logger.info('TestCustomModel.classes')
        return ['something']
