#!/bin/bash

URL="http://localhost:8080/api/v1/health"
HEADER="X-API-KEY: demo-user"

echo "Generating traffic..."

for i in {1..100}
do
  status=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "$HEADER" \
    "$URL")

  echo "Request $i -> HTTP $status"

  sleep 0.2
done

echo "Done."