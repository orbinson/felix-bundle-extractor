#!/usr/bin/env bash

trap break INT

for file in *.jar; do
  if (unzip -c "$file" | grep -q "$1"); then
    echo "$file"
  fi
done
