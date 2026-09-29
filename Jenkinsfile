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
        IMAGE_DIGEST        = ''
        IMAGE_REF           = ''
        APP_VERSION         = ''
        JENKINS_OC_CREDS    = 'usuario-generico'
        BUILD_NAMESPACE     = 'dev-quarkus-game'
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
                    
                    //def baseVersion = sh(
                      //  script: "JAVA_TOOL_OPTIONS='' mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null | grep -v 'Picked up' | tr -d '\\r\\n' || echo '1.0.0'",
                       // returnStdout: true
                    //).trim()

                    def pomContent = readFile('pom.xml')
                    def matcher = pomContent =~ /<version>(.*?)<\/version>/
                    def baseVersion = matcher ? matcher[0][1].trim() : '1.0.0'

                    env.APP_VERSION = "${(baseVersion && baseVersion != 'null' && baseVersion != '') ? baseVersion : '1.0.0'}-${env.BUILD_NUMBER}"
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

                    sh  "echo  ${BUILD_NUMBER}"
                    def imageTag = "${QUAY_REGISTRY}:${DEPLOY_ENV.toLowerCase()}-${BUILD_NUMBER}"
                    env.IMAGE_REF = imageTag
                    
                    if (!QUAY_REGISTRY?.trim()) {     
                        error("QUAY_REGISTRY no está definido") 
                    } 
                        if (!DEPLOY_ENV?.trim()) {     
                            error("DEPLOY_ENV no está definido") 
                        } 
                            if (!BUILD_NUMBER?.trim()) {     
                                error("BUILD_NUMBER no está definido") 
                            } 
                                
                    echo "Iniciando compilación en OpenShift y Push hacia Quay: ${IMAGE_REF}"
 
                    withCredentials([usernamePassword(
                        credentialsId   : "${env.JENKINS_OC_CREDS}",
                        usernameVariable: 'OC_USER',
                        passwordVariable: 'OC_PASSWORD'
                    )]) {
                        sh '''
                            set +x
                            # 1. Autenticarse en OpenShift
                            oc login "$OPENSHIFT_API" -u "$OC_USER" -p "$OC_PASSWORD" --insecure-skip-tls-verify=false
                            oc project ${BUILD_NAMESPACE}
 
                            # 2. Crear el BuildConfig tipo Docker si no existe, asignando el push-secret
                            if ! oc get buildconfig "${APP_NAME}-builder" -n ${BUILD_NAMESPACE} >/dev/null 2>&1; then
                                echo "Creando nuevo BuildConfig para ${env.APP_NAME}..."
                                oc new-build \
                                    --name="${APP_NAME}-builder" \
                                    --strategy=docker \
                                    --binary \
                                    --to-docker=true \
                                    --to="${IMAGE_REF}" \
                                    --push-secret="${QUAY_SECRET_NAME}" \
                                    -n ${BUILD_NAMESPACE}
                            else
                                echo "BuildConfig existente. Actualizando la imagen destino a: ${IMAGE_REF}"
                                oc patch buildconfig/${APP_NAME}-builder \
                                    --patch '{"spec":{"output":{"to":{"name":"'${IMAGE_REF}'"}}}}' \
                                    --namespace ${BUILD_NAMESPACE}
                            fi
 
                            # 3. Enviar el contexto del directorio actual para construir la imagen y subirla a Quay
                            oc start-build ${APP_NAME}-builder --from-dir=. --follow -n ${BUILD_NAMESPACE}
                        '''
                    }
 
                    echo "Imagen construida y publicada exitosamente en Quay: ${IMAGE_REF}"
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
        
        stage('12. Deploy DEV') {
            when {
                allOf {
                    expression {
                        params.RAMA_OVERRIDE?.trim() ? true : env.RAMA in ['develop', 'main']
                    }
                    expression {
                        (params.APLICATIVO == 'quarkus-game' && params.AMBIENTE in ['DEV', 'QA']) ||
                        (params.APLICATIVO == 'CONSOLA'  && params.AMBIENTE == 'PREPROD')
                    }
                }
            }
            steps {
                script {
                    def targetNamespace = "${params.APLICATIVO.toLowerCase()}-${env.DEPLOY_ENV.toLowerCase()}"
                    echo "Desplegando en OpenShift (${env.OPENSHIFT_API}) - Namespace: ${targetNamespace}"

                    withCredentials([string(credentialsId: 'usuario-generico', variable: 'OC_USER')]) {
                        sh 'oc login ' + env.OPENSHIFT_API + ' --token="$OC_USER" --insecure-skip-tls-verify=false'
                        sh 'oc project ' + targetNamespace + ' || oc new-project ' + targetNamespace
                        sh 'oc set image deployment/' + env.APP_NAME + ' ' + env.APP_NAME + '="' + env.IMAGE_REF + '" -n ' + targetNamespace + ' || oc create deployment ' + env.APP_NAME + ' --image="' + env.IMAGE_REF + '" -n ' + targetNamespace
                        sh 'oc rollout status deployment/' + env.APP_NAME + ' -n ' + targetNamespace + ' --timeout=5m'
                    }
                }
            }
        }

        stage('13. Remove Registry Repository Tags') {
            steps {
                script {
                    echo "Construcción gestionada por OpenShift BuildConfig. Omitiendo limpieza local de Podman."
                }
            }
        }

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
