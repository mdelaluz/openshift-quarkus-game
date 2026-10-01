    //def jdkTool             = 'Oracle JDK jdk1.8.0_144'
    def jdkTool             = 'Java 21'
    def appName             = 'quarkus-game'
    def gitRepoUrl          = 'https://github.com/psehgaft/openshift-quarkus-game.git'
    def gitDeployRepoUrl    = ''
    def gitCredentials      = 'gitlab-deploy-token-38'
    def mavenTool           = 'apache-maven-3.9.6'
    def staticAssetsEnabled = true
    def staticAssetsDir     = 'container-assets'
    def staticAssetsProfile = 'core'
    def dockerfileEnabled   = true
    def dockerfileOutputPath = 'Dockerfile'
    def dockerBaseImage     = 'registry.access.redhat.com/ubi9/openjdk-21-runtime:latest'
    def quayRegistry        = 'quay-9tfrr.apps.cluster-9tfrr.9tfrr.sandbox1834.opentlc.com/quayadmin/quarkus-game'
    def openshiftApi        = 'https://api.cluster-9tfrr.9tfrr.sandbox1834.opentlc.com:6443'

pipeline {
    agent any

    tools {
            maven "${mavenTool}"
            jdk   "${jdkTool}"
        }


    parameters {
        choice(
            name        : 'APLICATIVO',
            choices     : ['quarkus-game', 'CONSOLA'],
            description : 'Selecciona el aplicativo destino del despliegue'
        )
        choice(
            name        : 'AMBIENTE',
            choices     : ['DEV', 'QA', 'PREPROD'],
            description : 'quarkus-game: DEV, QA | CONSOLA: PREPROD'
        )
        booleanParam(
            name         : 'SKIP_SONARQUBE',
            defaultValue : false,
            description  : 'Omitir el análisis de SonarQube'
        )
        booleanParam(
            name         : 'SKIP_VERACODE',
            defaultValue : false,
            description  : 'Omitir escaneo de Veracode'
        )
        string(
            name         : 'RAMA_OVERRIDE',
            defaultValue : '',
            description  : 'Rama a desplegar (opcional). Si se deja vacío: main'
        )
    }

    environment {
        APP_NAME            = 'quarkus-game'
        GIT_REPO_URL        = 'https://github.com/psehgaft/openshift-quarkus-game.git'
        GIT_CREDENTIALS     = 'gitlab-deploy-token-38'
        RAMA                = "${params.RAMA_OVERRIDE?.trim() ?: 'main'}"
        APP_PROFILE         = "${params.AMBIENTE?.toLowerCase() ?: 'dev'}"
        DEPLOY_ENV          = "${params.AMBIENTE ?: 'DEV'}"
        QUAY_REGISTRY       = 'quay-9tfrr.apps.cluster-9tfrr.9tfrr.sandbox1834.opentlc.com/quayadmin/quarkus-game'
        OPENSHIFT_API       = 'https://api.cluster-9tfrr.9tfrr.sandbox1834.opentlc.com:6443'
        DOCKER_BASE_IMAGE   = 'registry.access.redhat.com/ubi9/openjdk-17-runtime:latest'
        STATIC_ASSETS_DIR   = 'container-assets'
        DOCKERFILE_OUTPUT   = 'Dockerfile'
        JENKINS_OC_CREDS    = 'usuario-generico'
        BUILD_NAMESPACE     = 'sicatel-dev'
        QUAY_SECRET_NAME    = 'quay-push-secret'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '5'))
        disableConcurrentBuilds()
        timeout(time: 2, unit: 'HOURS')
    }

    stages {

        stage('1. Initialize Pipeline') {
            steps {
                script {
                    echo "========================================="
                    echo "  App         : ${env.APP_NAME}"
                    echo "  Aplicativo  : ${params.APLICATIVO}"
                    echo "  Rama        : ${env.RAMA}"
                    echo "  Ambiente    : ${env.DEPLOY_ENV}"
                    echo "  Perfil      : ${env.APP_PROFILE}"
                    echo "  Build #     : ${env.BUILD_NUMBER}"
                    echo "  Cluster API : ${env.OPENSHIFT_API}"
                    echo "  Quay Repo   : ${env.QUAY_REGISTRY}"
                    echo "========================================="

                    withCredentials([usernamePassword(
                        credentialsId : "${env.GIT_CREDENTIALS}",
                        usernameVariable: 'GIT_USER',
                        passwordVariable: 'GIT_TOKEN'
                    )]) {
                        sh "git ls-remote https://\${GIT_USER}:\${GIT_TOKEN}@${env.GIT_REPO_URL.replace('https://', '')} HEAD"
                    }
                    echo "=== Repositorio Git accesible ==="
                }
            }
        }

        stage('2. Checkout Source & Configuration') {
            steps {
                script {
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: "*/${env.RAMA}"]],
                        extensions: [[$class: 'CleanBeforeCheckout']],
                        userRemoteConfigs: [[
                            url           : "${env.GIT_REPO_URL}",
                            credentialsId : "${env.GIT_CREDENTIALS}"
                        ]]
                    ])

                    def pomContent = readFile('pom.xml')
                    def matcher = pomContent =~ /<version>([^<]+)<\/version>/
                    def baseVersion = '1.0.0'
                    if (matcher.find()) {
                        baseVersion = matcher.group(1).trim()
                    }

                    env.APP_VERSION = "${baseVersion}-${env.BUILD_NUMBER}"
                    echo "Versión calculada para el artefacto: ${env.APP_VERSION}"
                }
            }
        }

        stage('3. Build & Unit Test') {
                steps {
                    //sh "mvn clean verify -B -P${APP_PROFILE} -DskipTests"
                    sh "mvn clean verify -B -DskipTests"
                    //sh "if [ -f ./mvnw ]; then chmod +x ./mvnw && ./mvnw clean verify -B -DskipTests; else mvn clean verify -B -DskipTests; fi"
                    sh 'echo "=== Artefactos generados ==="'
                    sh 'find . -path "*/target/*.jar" -o -path "*/target/*.ear"'
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


       stage('4. Code Quality Scan') {
            when {
                expression { params.SKIP_SONARQUBE == false }
            }
            steps {
                script {
                    withSonarQubeEnv('SonarServer1') {
                        // 1. Compila los .class y ejecuta el análisis
                        sh "mvn compile org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.projectName=${env.APP_NAME} -Dsonar.projectKey=${env.APP_NAME}"
                    }
                    return 
                    //se comenta el return para telcel
                    // 2. Espera nativa del Webhook (máximo 10 minutos)
                    timeout(time: 10, unit: 'MINUTES') {
                        def qg = waitForQualityGate()
                        echo "Estado del Quality Gate recibido por Webhook: ${qg.status}"
                        if (qg.status != 'OK') {
                            error "Quality Gate RECHAZADO con estado: ${qg.status}"
                        }
                    }
                }
            }
        }

         stage('5. Application Security Scan') {
            when {
                expression { params.SKIP_VERACODE == false }
            }        
            steps {
                script {
                    return
                    withCredentials([file(credentialsId: 'veracode-adapter', variable: 'VERACODE_ADAPTER')]) {
                        sh 'test -s "$VERACODE_ADAPTER" && bash "$VERACODE_ADAPTER" target/ || echo "Veracode ejecutado sin alertas críticas o adaptador no disponible."'
                    }
                }
            }
        }

         stage('6. Version & Build Image') {
            steps {
                script {
                    echo "Etiquetando versión de integración: v${env.APP_VERSION}"
                    withCredentials([usernamePassword(credentialsId: "${env.GIT_CREDENTIALS}", usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
                        sh 'git config user.email "jenkins@ci.com"'
                        sh 'git config user.name "Jenkins CI"'
                        sh 'git tag -a "v' + env.APP_VERSION + '" -m "Build de integración automática #' + env.BUILD_NUMBER + '" || true'
                    }
                }
            }
        }

        stage('7. Build Image & Publish to Quay') {
            steps {
                script {
                    echo "Build número: ${env.BUILD_NUMBER}"
                    def appEnvLower = env.DEPLOY_ENV ? env.DEPLOY_ENV.toLowerCase() : 'dev'
                    def computedImageRef = "${env.QUAY_REGISTRY}:${appEnvLower}-${env.BUILD_NUMBER}"
                    env.IMAGE_REF = computedImageRef

                    // 1. Preparar carpeta temporal y recolectar automáticamente todos los assets
                    sh """
                        # Crear carpeta de staging para la construcción
                        mkdir -p container-build-assets

                        # Buscar y copiar los archivos de configuración desde cualquier subcarpeta de resources/
                        find resources/ -name "server.xml" -exec cp {} container-build-assets/ \\;
                        find resources/ -name "init-logs.sh" -exec cp {} container-build-assets/ \\;
                        find resources/ -name "validate-startup.sh" -exec cp {} container-build-assets/ \\;

                        # Buscar y copiar el archivo .ear generado en la compilación
                        find . -path "*/target/*.ear" -exec cp {} container-build-assets/ \\;

                        # Localizar la plantilla Dockerfile y reemplazar las variables
                        DOCKERFILE_SRC=\$(find resources/ -name "Dockerfile" | head -n 1)

                        sed -e 's|__BASE_IMAGE__|${env.DOCKER_BASE_IMAGE}|g' \
                            -e 's|__ASSET_DIR__|container-build-assets|g' \
                            -e 's|__EAR_FILE__|*.ear|g' \
                            "\$DOCKERFILE_SRC" > Dockerfile
                    """

                    echo "Iniciando compilación en OpenShift y Push hacia Quay: ${computedImageRef}"

                    withCredentials([usernamePassword(
                        credentialsId   : "${env.JENKINS_OC_CREDS}",
                        usernameVariable: 'OC_USER',
                        passwordVariable: 'OC_PASSWORD'
                    )]) {
                        sh """
                            set +x
                            # 2. Validar la credencial Username with password
                            if [ -z "\$OC_USER" ]; then
                                echo "ERROR: la credencial ${env.JENKINS_OC_CREDS} no contiene usuario."
                                exit 1
                            fi

                            if [ -z "\$OC_PASSWORD" ]; then
                                echo "ERROR: la credencial ${env.JENKINS_OC_CREDS} no contiene contraseña."
                                exit 1
                            fi

                            # 3. Autenticarse en OpenShift con usuario y contraseña
                            echo "Validando autenticación contra OpenShift..."
                            oc login \
                                --server="${env.OPENSHIFT_API}" \
                                --username="\$OC_USER" \
                                --password="\$OC_PASSWORD" \
                                --insecure-skip-tls-verify=false

                            echo "Autenticación correcta como: \$(oc whoami)"
                            oc project "${env.BUILD_NAMESPACE}" || oc new-project "${env.BUILD_NAMESPACE}"

                            # 4. Eliminar BuildConfig previo
                            oc delete buildconfig "${env.APP_NAME}-builder" -n "${env.BUILD_NAMESPACE}" --ignore-not-found
                           
 
                            # 5. Crear el BuildConfig dinámico
                            oc new-build \
                                --name="${env.APP_NAME}-builder" \
                                --strategy=docker \
                                --binary \
                                --to-docker=true \
                                --to="${computedImageRef}" \
                                --push-secret="${env.QUAY_SECRET_NAME}" \
                                -n "${env.BUILD_NAMESPACE}"
                            # 5.1 Publicacion de Build Config de Imagen resultante
                            oc set build-secret --push buildconfig/"${env.APP_NAME}-builder" "${env.QUAY_SECRET_NAME}" -n "${env.BUILD_NAMESPACE}"

                            # 6. Inyectar recursos (requests/limits) requeridos por la cuota
                            oc patch buildconfig "${env.APP_NAME}-builder" \
                                --type=merge \
                                --patch='{
                                    "spec": {
                                        "resources": {
                                            "requests": {
                                                "cpu": "360m",
                                                "memory": "3Gi"
                                            },
                                            "limits": {
                                                "cpu": "4",
                                                "memory": "4Gi"
                                            }
                                        }
                                    }
                                }' \
                                -n "${env.BUILD_NAMESPACE}"

                            # 7. Enviar contexto e iniciar compilación
                            oc start-build "${env.APP_NAME}-builder" --from-dir=. --follow -n "${env.BUILD_NAMESPACE}"
                        """
                    }

                    echo "Imagen construida y publicada exitosamente en Quay: ${env.IMAGE_REF}"
                }
            }
        }

        stage('9. Generate SBOM & Scan Image') {
            steps {
                echo 'SBOM y escaneo de imagen pendientes de implementación'
            }
        }

        stage('10. Quality & Security Gate') {
            steps {
                echo 'Quality & Security Gate pendiente de implementación'
            }
        }

        stage('11. Sign & Attest') {
            steps {
                echo 'Firma y attestation pendientes de implementación'
            }
        }
        
        stage('13. Remove Registry Repository Tags') {
            steps {
                script {
                    echo "Construcción gestionada por OpenShift BuildConfig. Omitiendo limpieza local de Podman."
                }
            }
        } //cierre de stage 13
        
    }  //cierre de pipeline
post {
        always {
            deleteDir()
        }
        success {
            echo "Pipeline ${env.APP_NAME} finalizado correctamente en ${env.DEPLOY_ENV}"
        }
        failure {
            echo "Pipeline ${env.APP_NAME} fallido. Revisa los logs."
        }
    }
}
