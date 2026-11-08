#!/usr/bin/env bash
# MOMENTUM launcher for macOS.
# Double-click this file in Finder to start the service and open the app.
# Requires Java 21+ and Node.js 22+ (see TUTORIAL.md).
cd "$(dirname "$0")"
exec ./scripts/momentum.sh start