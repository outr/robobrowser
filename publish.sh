#!/usr/bin/env bash

set -e

sbt +clean
sbt +compile
sbt "+cdp / testOnly spec.RoboBrowserSpec"
sbt +publishSigned
sbt sonatypeBundleRelease