#!/usr/bin/env bash

# This script is adapted from the build_images.sh script from the batfish/docker repo on github

# Asset directory setup
WORK_DIR="$PWD"
ASSETS_REL_PATH=./assets
ASSETS_FULL_PATH=${WORK_DIR}/${ASSETS_REL_PATH}

# Batfish
echo "Cloning and building batfish"

mvn clean -f projects/pom.xml package
BATFISH_VERSION=$(grep -1 batfish-parent "projects/pom.xml" | grep version | sed 's/[<>]/|/g' | cut -f3 -d\|)
cp batfish/projects/allinone/target/allinone-bundle-${BATFISH_VERSION}.jar ${ASSETS_FULL_PATH}/allinone-bundle.jar
cp -r batfish/questions ${ASSETS_FULL_PATH}
docker build -f ${WORK_DIR}/batfish.dockerfile -t modnetv/campion --build-arg ASSETS=${ASSETS_REL_PATH} .

