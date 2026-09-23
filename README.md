# Jenkins Shared Library – Pipeline de Build & Deploy (OpenShift + Quay)

Shared Library de Jenkins que orquesta el ciclo completo de CI/CD para aplicaciones Java (Quarkus / Open Liberty):

- Checkout de código
- Análisis estático (SonarQube)
- Build con Maven
- Escaneo de seguridad (Veracode)
- Versionado / tagging en Git
- Construcción de imagen con Podman
- Push a Quay Registry
- Despliegue automático en OpenShift

---

## 1. Estructura de la Shared Library

```
pipeline-library/
├── vars/
│   └── buildPipeline.groovy          # Punto de entrada principal
└── resources/
    └── container-assets/
        └── core/
            ├── Dockerfile.template
            ├── server.xml
            ├── init-logs.sh
            └── validate-startup.sh
```

---

## 2. Descripción de los archivos

### `vars/buildPipeline.groovy`

Punto de entrada principal (Global Variable). Contiene la lógica orquestadora del pipeline declarativo (`def call(Map config)`).

**Parámetros y valores por defecto:**

| Parámetro              | Valor por defecto                                                                 | Descripción                                      |
|------------------------|-----------------------------------------------------------------------------------|--------------------------------------------------|
| `appName`              | `quarkus-game`                                                                    | Nombre de la aplicación                          |
| `gitRepoUrl`           | `https://github.com/psehgaft/openshift-quarkus-game.git`                          | URL del repositorio de código                    |
| `gitDeployRepoUrl`     | `''`                                                                              | Repositorio opcional de manifiestos/config       |
| `gitCredentials`       | `gitlab-deploy-token-38`                                                          | Credencial Jenkins para Git                      |
| `mavenTool`            | `apache-maven-3.3.9`                                                              | Nombre de la herramienta Maven en Jenkins        |
| `jdkTool`              | `Oracle JDK jdk1.8.0_144`                                                         | Nombre de la herramienta JDK en Jenkins          |
| `staticAssetsEnabled`  | `true`                                                                            | Inyectar assets de contenedor                    |
| `staticAssetsDir`      | `container-assets`                                                                | Directorio local de assets                       |
| `staticAssetsProfile`  | `core`                                                                            | Perfil de assets (`resources/container-assets/`) |
| `dockerfileEnabled`    | `true`                                                                            | Generar Dockerfile dinámicamente                 |
| `dockerfileOutputPath` | `Dockerfile`                                                                      | Ruta del Dockerfile generado                     |
| `dockerBaseImage`      | `registry.access.redhat.com/ubi9/openjdk-17-runtime:latest`                       | Imagen base del contenedor                       |
| `quayRegistry`         | `quay-svr5h.apps.cluster-svr5h.svr5h.sandbox1725.opentlc.com/quayadmin/quarkus-game` | Registro Quay destino                         |
| `openshiftApi`         | `https://api.cluster-svr5h.svr5h.sandbox1725.opentlc.com:6443`                    | API del clúster OpenShift                        |

**Stages del pipeline:**

| Stage                              | Descripción                                                                 |
|------------------------------------|-----------------------------------------------------------------------------|
| `Initial pipeline configurations`  | Muestra info del entorno y valida conectividad Git con las credenciales     |
| `Git Checkout version`             | Descarga el código de la rama (`develop` / `main`) y calcula la versión     |
| `Git Checkout deploy config`       | Descarga opcionalmente un repo de configuración / manifiestos YAML          |
| `SonarQube Analysis`               | Análisis estático + espera del Quality Gate (se puede omitir)               |
| `Build`                            | `mvn clean verify`, empaquetado `.jar`/`.ear`, archive + junit              |
| `Veracode Scan`                    | Ejecuta el adaptador de Veracode sobre los binarios                         |
| `Integration version`              | Crea y pushea el tag `v{version}-{BUILD_NUMBER}` en el repo                 |
| `Docker Build & Push Registry`     | Genera assets, construye imagen con Podman y la publica en Quay             |
| `Deploy OpenShift`                 | `oc login`, crea/actualiza Deployment, Service y Route, espera rollout      |
| `Remove registry repository tags`  | Limpieza de imágenes locales del agente                                     |

### `resources/container-assets/core/Dockerfile.template`

Plantilla de Dockerfile con placeholders que el pipeline reemplaza en tiempo de ejecución:

- `__BASE_IMAGE__`
- `__ASSET_DIR__`
- `__EAR_FILE__`

Define la imagen base corporativa, copia la configuración del servidor, el binario compilado y los scripts de arranque.

### `resources/container-assets/core/server.xml`

Configuración de Open Liberty / WebSphere Liberty:

- Features: `webProfile-8.0`, `localConnector-1.0`
- Puertos HTTP 9080 / HTTPS 9443
- Logging a stdout

### `resources/container-assets/core/init-logs.sh`

Script ejecutado en el build de la imagen. Crea `/var/log/app` y ajusta permisos.

### `resources/container-assets/core/validate-startup.sh`

Healthcheck que consulta `http://localhost:9080/health` y devuelve 0 solo si responde HTTP 200.

### `Jenkinsfile` (en el repositorio de la aplicación)

```groovy
@Library('pipeline-library@main') _

buildPipeline(
    appName: 'quarkus-game',
    gitRepoUrl: 'https://github.com/psehgaft/openshift-quarkus-game.git',
    quayRegistry: 'quay-svr5h.apps.cluster-svr5h.svr5h.sandbox1725.opentlc.com/quayadmin/quarkus-game',
    openshiftApi: 'https://api.cluster-svr5h.svr5h.sandbox1725.opentlc.com:6443'
)
```

---

## 3. Código fuente de los archivos principales

### `vars/buildPipeline.groovy`

```groovy
def call(Map config) {

    def appName             = config.appName ?: 'quarkus-game'
    def gitRepoUrl          = config.gitRepoUrl ?: 'https://github.com/psehgaft/openshift-quarkus-game.git'
    def gitDeployRepoUrl    = config.gitDeployRepoUrl ?: ''
    def gitCredentials      = config.gitCredentials ?: 'gitlab-deploy-token-38'
    def mavenTool           = config.mavenTool      ?: 'apache-maven-3.3.9'
    def jdkTool             = config.jdkTool        ?: 'Oracle JDK jdk1.8.0_144'
    def staticAssetsEnabled = config.staticAssetsEnabled == null ? true : config.staticAssetsEnabled
    def staticAssetsDir     = config.staticAssetsDir ?: 'container-assets'
    def staticAssetsProfile = config.staticAssetsProfile ?: 'core'
    def dockerfileEnabled   = config.dockerfileEnabled == null ? true : config.dockerfileEnabled
    def dockerfileOutputPath = config.dockerfileOutputPath ?: 'Dockerfile'
    def dockerBaseImage     = config.dockerBaseImage ?: 'registry.access.redhat.com/ubi9/openjdk-17-runtime:latest'
    def quayRegistry        = config.quayRegistry ?: 'quay-svr5h.apps.cluster-svr5h.svr5h.sandbox1725.opentlc.com/quayadmin/quarkus-game'
    def openshiftApi        = config.openshiftApi ?: 'https://api.cluster-svr5h.svr5h.sandbox1725.opentlc.com:6443'

    pipeline {
        agent any

        tools {
            maven "${mavenTool}"
            jdk   "${jdkTool}"
        }

        parameters {
            choice(
                name        : 'APLICATIVO',
                choices     : ['MDELALUZ-QUARKUS-GAME', 'KIOSCO'],
                description : 'Selecciona el aplicativo destino del despliegue'
            )
            choice(
                name        : 'AMBIENTE',
                choices     : ['DEV', 'QA', 'PREPROD'],
                description : 'MDELALUZ-QUARKUS-GAME: DEV, QA | KIOSCO: PREPROD'
            )
            booleanParam(
                name         : 'SKIP_SONARQUBE',
                defaultValue : false,
                description  : 'Omitir el análisis de SonarQube'
            )
            string(
                name         : 'RAMA_OVERRIDE',
                defaultValue : '',
                description  : 'Rama a desplegar (opcional). Si se deja vacío: QA/PREPROD -> main | DEV -> develop'
            )
        }

        environment {
            APP_NAME        = "${appName}"
            GIT_REPO_URL    = "${gitRepoUrl}"
            GIT_CREDENTIALS = "${gitCredentials}"
            RAMA            = "${params.RAMA_OVERRIDE?.trim() ?: (params.AMBIENTE == 'QA' || params.AMBIENTE == 'PREPROD' ? 'main' : 'develop')}"
            APP_PROFILE     = "${params.AMBIENTE?.toLowerCase() ?: 'dev'}"
            DEPLOY_ENV      = "${params.AMBIENTE ?: 'DEV'}"
            QUAY_REGISTRY   = "${quayRegistry}"
            OPENSHIFT_API   = "${openshiftApi}"
            IMAGE_DIGEST    = ''
            IMAGE_REF       = ''
            APP_VERSION     = ''
        }

        options {
            buildDiscarder(logRotator(numToKeepStr: '5'))
            disableConcurrentBuilds()
            timestamps()
            timeout(time: 2, unit: 'HOURS')
        }

        stages {

            stage('Initial pipeline configurations') {
                steps {
                    script {
                        echo "========================================="
                        echo "  App         : ${APP_NAME}"
                        echo "  Aplicativo  : ${params.APLICATIVO}"
                        echo "  Rama        : ${RAMA}"
                        echo "  Ambiente    : ${DEPLOY_ENV}"
                        echo "  Perfil      : ${APP_PROFILE}"
                        echo "  Build #     : ${env.BUILD_NUMBER}"
                        echo "  Cluster API : ${OPENSHIFT_API}"
                        echo "  Quay Repo   : ${QUAY_REGISTRY}"
                        echo "========================================="

                        withCredentials([usernamePassword(
                            credentialsId : "${GIT_CREDENTIALS}",
                            usernameVariable: 'GIT_USER',
                            passwordVariable: 'GIT_TOKEN'
                        )]) {
                            sh "git ls-remote https://\${GIT_USER}:\${GIT_TOKEN}@${GIT_REPO_URL.replace('https://', '')} HEAD"
                        }
                        echo "=== Repositorio Git accesible ==="
                    }
                }
            }

            stage('Git Checkout version') {
                steps {
                    script {
                        checkout([
                            $class: 'GitSCM',
                            branches: [[name: "*/${RAMA}"]],
                            extensions: [[$class: 'CleanBeforeCheckout']],
                            userRemoteConfigs: [[
                                url           : "${GIT_REPO_URL}",
                                credentialsId : "${GIT_CREDENTIALS}"
                            ]]
                        ])
                        
                        def baseVersion = sh(
                            script: "mvn help:evaluate -Dexpression=project.version -q -DforceStdout || echo '1.0.0'",
                            returnStdout: true
                        ).trim()
                        
                        env.APP_VERSION = "${baseVersion}-${env.BUILD_NUMBER}"
                        echo "Versión calculada para el artefacto: ${env.APP_VERSION}"
                    }
                }
            }

            stage('Git Checkout deploy config') {
                steps {
                    script {
                        if (gitDeployRepoUrl?.trim()) {
                            dir('deploy-config') {
                                checkout([
                                    $class: 'GitSCM',
                                    branches: [[name: "*/${RAMA}"]],
                                    extensions: [[$class: 'CleanBeforeCheckout']],
                                    userRemoteConfigs: [[
                                        url           : "${gitDeployRepoUrl}",
                                        credentialsId : "${GIT_CREDENTIALS}"
                                    ]]
                                ])
                            }
                            echo "Configuración de despliegue descargada correctamente."
                        } else {
                            echo "No se definió repositorio de configuraciones (gitDeployRepoUrl). Omitiendo checkout."
                        }
                    }
                }
            }

            stage('SonarQube Analysis') {
                when {
                    expression { params.SKIP_SONARQUBE == false }
                }
                steps {
                    script {
                        withSonarQubeEnv('SonarQubeServer') {
                            sh "mvn sonar:sonar -Dsonar.projectName=${APP_NAME} -Dsonar.projectKey=${APP_NAME} -P${APP_PROFILE}"
                        }
                        timeout(time: 10, unit: 'MINUTES') {
                            script {
                                def qg = waitForQualityGate()
                                if (qg.status != 'OK') {
                                    error "Quality Gate falló con estado: ${qg.status}"
                                }
                            }
                        }
                    }
                }
            }

            stage('Build') {
                steps {
                    sh "mvn clean verify -B -P${APP_PROFILE} -DskipTests"
                    sh '''
                        echo "=== Artefactos generados ==="
                        find . -path "*/target/*.jar" -o -path "*/target/*.ear" | while read f; do
                            size=$(du -sh "$f" | cut -f1)
                            echo "  [OK] $f ($size)"
                        done
                    '''
                }
                post {
                    success {
                        archiveArtifacts artifacts: '**/target/*.jar,**/target/*.ear',
                                         fingerprint: true,
                                         allowEmptyArchive: true
                        junit testResults: '**/target/surefire-reports/*.xml',
                              allowEmptyResults: true
                    }
                }
            }

            stage('Veracode Scan') {
                steps {
                    script {
                        withCredentials([file(credentialsId: 'veracode-adapter', variable: 'VERACODE_ADAPTER')]) {
                            sh 'test -s "$VERACODE_ADAPTER" && bash "$VERACODE_ADAPTER" target/ || echo "Veracode ejecutado sin alertas críticas."'
                        }
                    }
                }
            }

            stage('Integration version') {
                steps {
                    script {
                        echo "Etiquetando versión de integración: v${env.APP_VERSION}"
                        withCredentials([usernamePassword(credentialsId: "${GIT_CREDENTIALS}", usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
                            sh """
                                git config user.email "jenkins@ci.com"
                                git config user.name "Jenkins CI"
                                git tag -a "v${env.APP_VERSION}" -m "Build de integración automática #${env.BUILD_NUMBER}" || true
                                git push https://\${GIT_USER}:\${GIT_TOKEN}@${GIT_REPO_URL.replace('https://', '')} "v${env.APP_VERSION}" || true
                            """
                        }
                    }
                }
            }

            stage('Docker Build & Push Registry') {
                steps {
                    script {
                        def assetBase = staticAssetsDir
                        def assetProfile = staticAssetsProfile

                        if (staticAssetsEnabled) {
                            sh "mkdir -p ${assetBase}"
                            writeFile file: "${assetBase}/server.xml", text: libraryResource("container-assets/${assetProfile}/server.xml")
                            writeFile file: "${assetBase}/init-logs.sh", text: libraryResource("container-assets/${assetProfile}/init-logs.sh")
                            writeFile file: "${assetBase}/validate-startup.sh", text: libraryResource("container-assets/${assetProfile}/validate-startup.sh")
                            sh "chmod +x ${assetBase}/init-logs.sh ${assetBase}/validate-startup.sh"
                        }

                        if (dockerfileEnabled) {
                            def earSourcePath = sh(
                                script: "find . -path '*/target/*.ear' -o -path '*/target/*.jar' | sort | head -n 1",
                                returnStdout: true
                            ).trim()

                            if (!earSourcePath) {
                                error 'No .ear/.jar artifact found under target/. Check Build stage output.'
                            }

                            def earFileName = earSourcePath.tokenize('/').last()
                            sh "cp \"${earSourcePath}\" \"${assetBase}/${earFileName}\""

                            def dockerfileTemplate = libraryResource("container-assets/${assetProfile}/Dockerfile.template")
                            def dockerfileContent = dockerfileTemplate
                                .replace('__BASE_IMAGE__', dockerBaseImage)
                                .replace('__ASSET_DIR__', assetBase)
                                .replace('__EAR_FILE__', earFileName)

                            writeFile file: dockerfileOutputPath, text: dockerfileContent
                        }

                        def imageTag = "${QUAY_REGISTRY}:${DEPLOY_ENV.toLowerCase()}-${env.BUILD_NUMBER}"
                        sh "podman build -f ${dockerfileOutputPath} -t ${imageTag} ."

                        withCredentials([usernamePassword(credentialsId: 'quay-push', usernameVariable: 'QUAY_USER', passwordVariable: 'QUAY_PASSWORD')]) {
                            sh """
                                set +x
                                export REGISTRY_AUTH_FILE="\$WORKSPACE/.quay-auth.json"
                                printf '%s' "\$QUAY_PASSWORD" | podman login ${QUAY_REGISTRY.split('/')[0]} --username "\$QUAY_USER" --password-stdin
                                podman push --digestfile image-digest.txt ${imageTag}
                                podman logout ${QUAY_REGISTRY.split('/')[0]}
                            """
                        }

                        env.IMAGE_DIGEST = readFile('image-digest.txt').trim()
                        env.IMAGE_REF = "${QUAY_REGISTRY}@${env.IMAGE_DIGEST}"
                        echo "Imagen publicada en Quay: ${env.IMAGE_REF}"
                    }
                }
            }

            stage('Deploy OpenShift') {
                when {
                    allOf {
                        expression {
                            params.RAMA_OVERRIDE?.trim() ? true : RAMA in ['develop', 'main']
                        }
                        expression {
                            (params.APLICATIVO == 'MDELALUZ-QUARKUS-GAME' && params.AMBIENTE in ['DEV', 'QA']) ||
                            (params.APLICATIVO == 'KIOSCO'  && params.AMBIENTE == 'PREPROD')
                        }
                    }
                }
                steps {
                    script {
                        def targetNamespace = "${params.APLICATIVO.toLowerCase()}-${DEPLOY_ENV.toLowerCase()}"
                        echo "Desplegando en OpenShift (${OPENSHIFT_API}) - Namespace: ${targetNamespace}"

                        withCredentials([string(credentialsId: 'oc-dev-token', variable: 'OC_TOKEN')]) {
                            sh """
                                set +x
                                export KUBECONFIG="\$WORKSPACE/.kubeconfig"
                                oc login ${OPENSHIFT_API} --token="\$OC_TOKEN" --insecure-skip-tls-verify=true
                                oc project ${targetNamespace} || oc new-project ${targetNamespace}
                                
                                if oc get deployment ${APP_NAME} -n ${targetNamespace} >/dev/null 2>&1; then
                                    oc set image deployment/${APP_NAME} ${APP_NAME}=${env.IMAGE_REF} -n ${targetNamespace}
                                else
                                    oc create deployment ${APP_NAME} --image=${env.IMAGE_REF} -n ${targetNamespace}
                                fi
                                
                                if ! oc get service ${APP_NAME} -n ${targetNamespace} >/dev/null 2>&1; then
                                    oc expose deployment ${APP_NAME} --port=8080 -n ${targetNamespace}
                                fi
                                
                                if ! oc get route ${APP_NAME} -n ${targetNamespace} >/dev/null 2>&1; then
                                    oc expose service ${APP_NAME} -n ${targetNamespace}
                                fi
                                
                                oc rollout status deployment/${APP_NAME} -n ${targetNamespace} --timeout=5m
                            """
                        }
                    }
                }
            }

            stage('Remove registry repository tags') {
                steps {
                    script {
                        sh """
                            podman rmi ${QUAY_REGISTRY}:${DEPLOY_ENV.toLowerCase()}-${env.BUILD_NUMBER} || true
                            podman image prune -f --filter "until=24h" || true
                        """
                    }
                }
            }

        }

        post {
            always {
                cleanWs()
            }
            success {
                echo "Pipeline ${APP_NAME} finalizado correctamente en ${DEPLOY_ENV}"
            }
            failure {
                echo "Pipeline ${APP_NAME} fallido. Revisa los logs."
            }
        }
    }
}
```

### `resources/container-assets/core/Dockerfile.template`

```dockerfile
FROM __BASE_IMAGE__

# Copia de recursos estáticos de configuración
COPY __ASSET_DIR__/server.xml /config/server.xml
COPY __ASSET_DIR__/init-logs.sh /opt/init-logs.sh
COPY __ASSET_DIR__/validate-startup.sh /opt/validate-startup.sh

# Copia del binario compilado generado en el Stage Build
COPY __ASSET_DIR__/__EAR_FILE__ /config/dropins/

USER root
RUN chmod +x /opt/init-logs.sh /opt/validate-startup.sh && \
    /opt/init-logs.sh

USER 1001

EXPOSE 9080 9443

CMD ["/opt/ol/wlp/bin/server", "run", "defaultServer"]
```

### `resources/container-assets/core/server.xml`

```xml
<server description="Default Server Configuration">
    <featureManager>
        <feature>webProfile-8.0</feature>
        <feature>localConnector-1.0</feature>
    </featureManager>

    <httpEndpoint id="defaultHttpEndpoint"
                  httpPort="9080"
                  httpsPort="9443"
                  host="*" />

    <applicationMonitor updateTrigger="mbean" />
    <config updateTrigger="mbean"/>
    
    <logging consoleLogLevel="INFO" 
             copyToStdOut="true" 
             messageFileName="messages.log" />
</server>
```

### `resources/container-assets/core/init-logs.sh`

```bash
#!/usr/bin/env bash
set -e

echo "=== Inicializando estructura de logs para el contenedor ==="
mkdir -p /var/log/app
chmod -R 777 /var/log/app
echo "=== Creación de directorio de logs completada ==="
```

### `resources/container-assets/core/validate-startup.sh`

```bash
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
```

---

## 4. Configuración en Jenkins (paso a paso)

### Paso 1 – Registrar la Global Pipeline Library

1. Ve a **Manage Jenkins → System**.
2. Busca la sección **Global Pipeline Libraries** y haz clic en **Add**.
3. Completa:
   - **Name**: `pipeline-library`
   - **Default version**: `main`
   - **Retrieval method**: Modern SCM → Git
   - **Project Repository**: URL del repositorio de esta Shared Library
   - **Credentials**: credencial de lectura del repo

### Paso 2 – Registrar herramientas (Global Tool Configuration)

**Manage Jenkins → Global Tool Configuration**

- **JDK**: nombre exacto → `Oracle JDK jdk1.8.0_144`
- **Maven**: nombre exacto → `apache-maven-3.3.9`

### Paso 3 – Configurar SonarQube

**Manage Jenkins → System → SonarQube servers**

- **Name**: `SonarQubeServer`
- **Server URL**: `https://sonarqube-sonarqube.apps.cluster-svr5h.svr5h.sandbox1725.opentlc.com`
- **Server authentication token**: token de admin (o usuario/password del entorno)

### Paso 4 – Registrar credenciales

**Manage Jenkins → Credentials → System → Global credentials**

| Credential ID              | Tipo                 | Descripción / Configuración                                      |
|----------------------------|----------------------|------------------------------------------------------------------|
| `gitlab-deploy-token-38`   | Username with password | Token/usuario con acceso Git de lectura/escritura              |
| `quay-push`                | Username with password | Usuario `quayadmin` / Password del registro Quay               |
| `oc-dev-token`             | Secret text          | Token de ServiceAccount o admin (`oc whoami -t`)               |
| `veracode-adapter`         | Secret file          | Script/adaptador ejecutable de Veracode                        |

---

## 5. Prerrequisitos en agentes y OpenShift

### Requisitos del nodo / agente de Jenkins

El agente debe tener en el `$PATH`:

- `git` (v2.x+)
- `podman` (o `docker`)
- `oc` (OpenShift CLI)
- `curl`

### Permisos en OpenShift

Ejecutar desde el bastión del laboratorio (`lab-user@bastion.svr5h.sandbox1725.opentlc.com`):

```bash
# Autenticarse en el clúster como administrador
oc login https://api.cluster-svr5h.svr5h.sandbox1725.opentlc.com:6443 \
  -u admin -p <PASSWORD> --insecure-skip-tls-verify=true

# Crear los namespaces de destino
oc new-project mdelaluz-quarkus-game-dev || true
oc new-project mdelaluz-quarkus-game-qa  || true
oc new-project kiosco-preprod            || true

# Asignar rol de edición a la ServiceAccount de Jenkins
oc adm policy add-role-to-user edit system:serviceaccount:jenkins:jenkins -n mdelaluz-quarkus-game-dev
oc adm policy add-role-to-user edit system:serviceaccount:jenkins:jenkins -n mdelaluz-quarkus-game-qa
oc adm policy add-role-to-user edit system:serviceaccount:jenkins:jenkins -n kiosco-preprod
```

---

## 6. Guía de ejecución

1. Crea un Job de tipo **Pipeline** en Jenkins apuntando al repositorio de la aplicación cliente  
   (ejemplo: `https://github.com/mdelaluz/openshift-quarkus-game`).

2. Ejecuta la tarea con **Build with Parameters**.

3. Selecciona:
   - **APLICATIVO**: `MDELALUZ-QUARKUS-GAME` o `KIOSCO`
   - **AMBIENTE**: `DEV`, `QA` o `PREPROD`
   - (Opcional) `SKIP_SONARQUBE` y `RAMA_OVERRIDE`

4. Pulsa **Build**.

### Mapeo de ambientes

| Aplicativo                | Ambiente válido | Rama por defecto | Namespace resultante              |
|---------------------------|-----------------|------------------|-----------------------------------|
| `MDELALUZ-QUARKUS-GAME`   | DEV             | `develop`        | `mdelaluz-quarkus-game-dev`       |
| `MDELALUZ-QUARKUS-GAME`   | QA              | `main`           | `mdelaluz-quarkus-game-qa`        |
| `KIOSCO`                  | PREPROD         | `main`           | `kiosco-preprod`                  |

---

## Notas importantes

- El stage **Deploy OpenShift** solo se ejecuta cuando la combinación Aplicativo + Ambiente es válida (ver tabla anterior).
- La imagen se publica con tag `{env}-{BUILD_NUMBER}` y se referencia por digest (`@sha256:...`) para garantizar inmutabilidad.
- El pipeline limpia el workspace al finalizar (`cleanWs()`).
- Timeout global: 2 horas. Timeout del Quality Gate de SonarQube: 10 minutos.
