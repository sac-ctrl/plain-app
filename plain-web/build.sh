#!/bin/bash

yarn build
rm -rf ../app/src/main/resources/web/*
cp -r dist/* ../app/src/main/resources/web/

