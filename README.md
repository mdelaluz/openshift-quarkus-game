# Quarkus Snake 🐍

Juego de Snake para probar una aplicación Quarkus en OpenShift. La misma aplicación sirve la interfaz HTML/CSS/JavaScript y una API REST; no se necesita Node.js, una base de datos ni un servidor web adicional.

## Jugar

Abre la URL raíz de la aplicación. Haz clic en **Jugar / reiniciar** o presiona **Espacio**. Usa las **flechas** o **W A S D** para moverte, **Espacio** para pausar y **R** para reiniciar. Come los círculos naranjas y evita las paredes y tu propio cuerpo. Al terminar puedes escribir tu nombre y guardar la puntuación.

El récord personal queda en el almacenamiento del navegador. La lista de las diez mejores puntuaciones se guarda **en memoria del proceso Quarkus**: se pierde al reiniciar y no se comparte entre réplicas. Es un ejemplo de pruebas, no un marcador persistente ni antifraude.

## Ejecutar localmente

Requisitos: Java 21 y Maven 3.9 o posterior.

```bash
mvn quarkus:dev
```

Abre <http://localhost:8080/>. Para ejecutar las pruebas y empaquetar:

```bash
mvn test
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

## API REST

| Método | Ruta | Uso |
| --- | --- | --- |
| GET | `/api/game` | Información del juego y controles |
| GET | `/api/game/scores` | Diez puntuaciones mayores |
| POST | `/api/game/scores` | Guarda `{ "player": "Ada", "points": 5 }` |
| GET | `/q/health/ready` | Comprobación de disponibilidad |
| GET | `/q/health/live` | Comprobación de vida |

```bash
curl http://localhost:8080/api/game
curl -X POST http://localhost:8080/api/game/scores \
  -H 'Content-Type: application/json' \
  -d '{"player":"Ada","points":5}'
curl http://localhost:8080/api/game/scores
```

`player` debe contener de 1 a 20 caracteres y `points` debe estar entre 0 y 400. La API devuelve HTTP 400 si los valores son inválidos.

## Desplegar desde Git en OpenShift

Requisitos: acceso a un clúster OpenShift, `oc` autenticado y permiso para crear proyectos, BuildConfigs, aplicaciones y Routes. El clúster debe poder obtener las imágenes base y las dependencias Maven; para instalaciones desconectadas configura los registros y repositorios internos correspondientes.

```bash
oc new-project quarkus-game
oc new-app https://github.com/psehgaft/openshift-quarkus-game.git \
  --strategy=docker --name=quarkus-game
oc logs -f bc/quarkus-game
oc rollout status deployment/quarkus-game
oc expose service/quarkus-game
oc get route quarkus-game
```

El `Dockerfile` usa una construcción Maven en varias etapas y una imagen de ejecución UBI con Java 21. El build inicial puede tardar mientras descarga dependencias. Abre el **HOST/PORT** mostrado por `oc get route quarkus-game` (normalmente `http://...`); si configuraste TLS en la Route, usa `https://...`. La interfaz llama a la API mediante rutas relativas en el mismo host.

Comprobaciones rápidas desde la terminal:

```bash
oc get pods,svc,route
oc logs deployment/quarkus-game
oc port-forward service/quarkus-game 8080:8080
# En otra terminal:
curl http://localhost:8080/q/health/ready
curl http://localhost:8080/api/game
```

Para repetir el build tras cambios en Git: `oc start-build quarkus-game --follow`. Para conservar puntuaciones y compartirlas entre varias réplicas, integra una base de datos en una iteración posterior.

## Archivos principales

- `src/main/java/io/github/psehgaft/game/GameResource.java`: microservicio REST y marcador en memoria.
- `src/main/resources/META-INF/resources/`: página, estilos y lógica del juego.
- `src/test/java/io/github/psehgaft/game/GameResourceTest.java`: prueba de página y API.
- `Dockerfile`: build y ejecución para OpenShift.
