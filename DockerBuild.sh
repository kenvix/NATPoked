#!/bin/bash
# Docker Build Script
# Written by Kenvix <i@kenvix.com>

DOCKER_IMAGE_NAME="kenvix/natpoked"
DOCKER_FILE="Dockerfile-FullBuild"

pushd $(cd "$(dirname "$0")";pwd)
cp -f "$DOCKER_FILE" Dockerfile
DOCKER_BUILDKIT=1 docker build --rm -t "$DOCKER_IMAGE_NAME" .

if [ $? -eq 0 ]; then
    echo "Docker build success"
    if [ "$1" == "push" ]; then
        docker push "$DOCKER_IMAGE_NAME"
        echo "Docker push success"
    fi
else
    echo "Docker build failed"
fi

rm -f "Dockerfile"
popd