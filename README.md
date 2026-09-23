# Enterprise CI/CD Pipeline - Jenkins Shared Library para OpenShift

Este repositorio contiene la definición e implementación de una **Jenkins Shared Library** modular y reutilizable para automatizar los procesos de integración continua (CI) y despliegue continuo (CD) de aplicaciones Java/Quarkus/WebSphere Liberty sobre clústeres de **OpenShift**, integrando escaneos de calidad de código (**SonarQube**), seguridad de software (**Veracode**), construcción de imágenes de contenedor (**Podman**) y registro de imágenes en **Quay**.

---

## 1. Estructura del Repositorio

Para que Jenkins reconozca esta biblioteca compartida de forma global, la estructura de carpetas debe mantenerse con la siguiente jerarquía estándar:

```text
jenkins-shared-library/
├── vars/
│   └── buildPipeline.groovy       # Script principal que define el Pipeline de CI/CD
├── resources/
│   └── container-assets/
│       └── core/
│           ├── Dockerfile.template # Plantilla dinámica para generación de imágenes Docker
│           ├── server.xml          # Configuración del servidor de aplicaciones (Open Liberty)
│           ├── init-logs.sh        # Script de inicialización de logs en el contenedor
│           └── validate-startup.sh # Script de validación de arranque del contenedor
└── README.md                       # Documentación técnica del proyecto
```

## 2. Descripción Paso a Paso de los Archivos 

```shell
 vars/buildPipeline.groovy
```

Es el punto de entrada principal (Global Variable) de la Shared Library. Contiene la lógica orquestadora del pipeline declarativo (def call(Map config)).

Parámetros y Valores por Defecto: Define configuraciones iniciales para el nombre de la app, repositorio Git, herramientas (Maven, JDK), perfil de assets estáticos y URLs de OpenShift/Quay.

Stage 'Initial pipeline configurations': Muestra información del entorno y valida la conectividad HTTP/HTTPS contra GitLab probando las credenciales asignadas.

Stage 'Git Checkout version': Descarga el código fuente de la aplicación desde la rama correspondiente (develop o main) y calcula la versión única del artefacto a partir del pom.xml y $BUILD_NUMBER.

Stage 'Git Checkout deploy config': Opcionalmente descarga artefactos de configuración o manifiestos YAML desde un repositorio externo si gitDeployRepoUrl está presente.

Stage 'SonarQube Analysis': Si no se omite (SKIP_SONARQUBE=false), ejecuta el análisis estático de código enviando las métricas al servidor SonarQube y se bloquea activamente hasta que el Quality Gate apruebe la revisión.

Stage 'Build': Compila el código mediante Maven (mvn clean verify), empaqueta los artefactos (.jar o .ear), valida el tamaño generado y archiva los resultados en Jenkins junto con las pruebas unitarias.

Stage 'Veracode Scan': Ejecuta la herramienta/script adaptador para análisis de seguridad estático/dinámico en los binarios compilados.

Stage 'Integration version': Etiqueta automáticamente el repositorio Git original con el tag de la versión integrada (v1.0.0-X) y sincroniza los cambios de forma remota.

Stage 'Docker Build & Push Registry': Genera dinámicamente los scripts de entorno (server.xml, init-logs.sh), inyecta el binario compilado dentro de la plantilla Dockerfile.template, construye la imagen con Podman y la publica en Quay Registry capturando el hash sha256 (IMAGE_DIGEST).

resources/container-assets/core/Dockerfile.template
Plantilla utilizada para empaquetar la aplicación dentro de la imagen de contenedor runtime. Contiene variables dinámicas (__BASE_IMAGE__, __ASSET_DIR__, __EAR_FILE__) que el pipeline reemplaza en tiempo de ejecución.

Función: Define la imagen base corporativa aprobada, copia las configuraciones del servidor de aplicaciones, inyecta el binario compilado y configura las instrucciones de arranque.

```shell
resources/container-assets/core/server.xml
```

Archivo de configuración para servidores de aplicaciones Java empresariales (ej. Open Liberty / WebSphere Liberty).

Función: Define las características del servidor, puertos HTTP/HTTPS activados, configuración de logs, orígenes CORS y parámetros de la máquina virtual Java.

```shell
resources/container-assets/core/init-logs.sh
```

Script Shell en bash inyectado dentro de la imagen para ejecutarse antes de la aplicación.

Función: Crea las carpetas del sistema de archivos donde se escribirán los registros (/logs, /var/log/app), ajusta los permisos de usuario en tiempo de ejecución y prepara la salida estándar para la recolección de logs en OpenShift.

```shell
resources/container-assets/core/validate-startup.sh
```

Script de comprobación de salud (Healthcheck) inyectado en el contenedor.

Función: Consulta endpoints internos (ej. /health/ready o /actuator/health) para verificar que la aplicación responda correctamente antes de que OpenShift la considere en estado Running.

```shell
Jenkinsfile (En el Repositorio de la Aplicación)
```

Ubicado en la raíz de los proyectos clientes (ej. https://github.com/mdelaluz/openshift-quarkus-game).

Función: Invoca la Shared Library cargada en Jenkins enviando únicamente los parámetros específicos del proyecto.

## 3. Código Fuente de los Archivos

## 4. Configuración en Jenkins (Paso a Paso)

### Paso 1: Registrar la Global Pipeline Library
1. Ve a **Administrar Jenkins (Manage Jenkins)** > **Configurar el Sistema (System)**.
2. Busca la sección **Global Pipeline Libraries** y haz clic en **Añadir (Add)**.
3. Completa los campos:
   * **Nombre (Name):** `pipeline-library`
   * **Versión por defecto (Default version):** `main`
   * **Retrieval method:** Selecciona *Modern SCM* -> *Git*.
   * **Project Repository:** Ingresa la URL Git de este repositorio con la Shared Library.
   * **Credentials:** Selecciona las credenciales de lectura para este repo.

### Paso 2: Registrar Herramientas (Global Tool Configuration)
Ve a **Administrar Jenkins** > **Global Tool Configuration**:
* **JDK:** Añade un JDK llamado exactamente: `Oracle JDK jdk1.8.0_144`
* **Maven:** Añade una herramienta Maven llamada exactamente: `apache-maven-3.3.9`

### Paso 3: Configurar el Servidor SonarQube
1. Ve a **Administrar Jenkins** > **Configurar el Sistema (System)**.
2. Localiza la sección **SonarQube servers**.
3. Añade un servidor con el nombre: `SonarQubeServer`
4. Proporciona la URL de SonarQube y el token de autenticación.

### Paso 4: Registrar Credenciales
Ve a **Administrar Jenkins** > **Credenciales (Credentials)** > **System** > **Global credentials** y crea los siguientes registros:

| Credential ID | Tipo | Descripción |
| :--- | :--- | :--- |
| `gitlab-deploy-token-38` | Username with password | Token/Usuario con acceso Git de lectura/escritura al código. |
| `quay-push` | Username with password | Cuenta con permisos de subida (push) al Quay Container Registry. |
| `oc-dev-token` | Secret text | Token ServiceAccount con rol de despliegue en OpenShift. |
| `veracode-adapter` | Secret file | Archivo ejecutable/adaptador para lanzar los análisis de Veracode. |

---

## 5. Prerrequisitos en Agentes y OpenShift

### Requisitos del Nodo/Agente de Jenkins
El servidor o contenedor que ejecute el pipeline debe contar con las siguientes utilidades en la variable de entorno `$PATH`:
* `git` (v2.x+)
* `podman` (o `docker`)
* `oc` (OpenShift Command Line Interface)
* `curl`

### Permisos en OpenShift
El token asignado en `oc-dev-token` debe pertenecer a una **ServiceAccount** con permisos de administración/edición sobre los namespaces destino:

```bash
oc adm policy add-role-to-user edit system:serviceaccount:jenkins:jenkins -n sicatel-dev
oc adm policy add-role-to-user edit system:serviceaccount:jenkins:jenkins -n sicatel-qa
oc adm policy add-role-to-user edit system:serviceaccount:jenkins:jenkins -n kiosco-preprod
```

---

## 6. Guía de Ejecución

1. Crea un Job de tipo **Pipeline** en Jenkins apuntando al repositorio de la aplicación cliente (ej. `https://github.com/mdelaluz/openshift-quarkus-game`).
2. Ejecuta la tarea mediante **Construir con parámetros (Build with Parameters)**.
3. Selecciona el **APLICATIVO** (`SICATEL` o `KIOSCO`), el **AMBIENTE** (`DEV`, `QA` o `PREPROD`) y presiona **Construir**.

Stage 'Deploy OpenShift': Autentica al clúster mediante oc login, selecciona el espacio de trabajo objetivo (namespace), actualiza o crea el Deployment, expone los objetos Service/Route y espera el estado exitoso mediante oc rollout status.

Stage 'Remove registry repository tags': Realiza labores de limpieza eliminando las imágenes locales creadas durante el build para evitar la saturación de disco del agente.
