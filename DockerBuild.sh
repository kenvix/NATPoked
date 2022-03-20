#!/bin/bash

pushd $(cd "$(dirname "$0")";pwd)

DOCKER_BUILDKIT=1 docker build --rm -t kenvix/natpoked --secret id=resources,src=. .

popd