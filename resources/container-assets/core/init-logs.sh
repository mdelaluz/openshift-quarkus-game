#!/usr/bin/env bash
set -e

echo "=== Inicializando estructura de logs para el contenedor ==="
mkdir -p /var/log/app
chmod -R 777 /var/log/app
echo "=== Creación de directorio de logs completada ==="
