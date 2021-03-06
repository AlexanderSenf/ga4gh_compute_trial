from setuptools import (setup, find_packages)

with open('README.md', 'r') as fh:
    long_description = fh.read()

setup(
    name='pro-tes',
    version='0.1.0',
    author='ELIXIR-Europe',
    author_email='alexander.kanitz@alumni.ethz.ch',
    description='Proxy GA4GH TES server',
    long_description=long_description,
    long_description_content_type="text/markdown",
    license='Apache License 2.0',
    url='https://github.com/elixir-europe/proTES.git',
    packages=find_packages(),
    keywords=(
        'ga4gh tes proxy rest restful api app server openapi '
        'swagger python flask'
    ),
    classifiers=[
        'License :: OSI Approved :: Apache Software License',
        'Development Status :: 3 - Alpha',
        'Intended Audience :: Science/Research',
        'Topic :: Scientific/Engineering :: Bio-Informatics',
        'Natural Language :: English',
        'Programming Language :: Python :: 3.6',
    ],
    install_requires=['connexion', 'Flask-Cors'],
)
