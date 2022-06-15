import pathlib

import setuptools
from setuptools import setup

project = "cortex-grpc"

setup(
    name=project,
    use_scm_version={
        'version_scheme': 'post-release',
        # TODO: sync up with scala versioning
        'git_describe_command': 'git describe --dirty --tags --long --match *.* --exclude *.post* --exclude *SNAPSHOT'
    },
    setup_requires=['setuptools_scm'],
    description='Messages for deepcortex GRPC',
    author='Sentrana',
    author_email='no-reply@sentrana.com',
    url='https://github.com/deepcortex/cortex-grpc',
    packages=['cortex'] + setuptools.find_packages(include=['cortex.*']),
    package_data={
        'cortex': [str(x.relative_to('cortex')) for x in pathlib.Path('cortex').glob('**/*.pyi')]
    },
    python_requires='>=3.6.0',
    install_requires=[
        'protobuf==3.8.0'
    ],
    extras=[],
    zip_safe=False,
)
