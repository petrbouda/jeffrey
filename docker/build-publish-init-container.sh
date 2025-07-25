#!/bin/bash

if [ -z "$1" ]; then
    echo "Error: version parameter is required"
    echo "Usage: $0 <version>"
    exit 1
fi

VERSION=$1

docker build . -t petrbouda/jeffrey-init-container:"${VERSION}" -f Dockerfile-init-container
docker tag petrbouda/jeffrey-init-container:"${VERSION}" petrbouda/jeffrey-init-container:latest

docker push petrbouda/jeffrey-init-container:"${VERSION}"
docker push petrbouda/jeffrey-init-container:latest
