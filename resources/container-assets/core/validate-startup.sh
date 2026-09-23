#!/usr/bin/env bash
set -e

STATUS_CODE=$(curl --write-out "%{http_code}" --silent --output /dev/null http://localhost:9080/health || echo "000")

if [ "$STATUS_CODE" -eq 200 ]; then
    echo "Aplicación iniciada correctamente (HTTP status: 200)"
    exit 0
else
    echo "Error en el arranque de la aplicación (HTTP status: $STATUS_CODE)"
    exit 1
fi
