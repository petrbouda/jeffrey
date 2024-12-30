VERSION=$1
docker build . -t petrbouda/jeffrey-examples:"$VERSION" -f Dockerfile-examples
docker tag petrbouda/jeffrey-examples:"$VERSION" petrbouda/jeffrey-examples:latest
#docker push petrbouda/jeffrey-examples:"$VERSION" petrbouda/jeffrey-examples:latest
