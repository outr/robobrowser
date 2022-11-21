#!/usr/bin/env bash

set -e

sbt +clean
sbt +compile
sbt "+testOnly spec.RoboBrowserSpec"
sbt +publishSigned
sbt sonatypeBundleRelease