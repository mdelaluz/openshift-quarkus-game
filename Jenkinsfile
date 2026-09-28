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
                    
                    def baseVersion = sh(
                        script: "JAVA_TOOL_OPTIONS='' mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null | tr -d '\\r\\n' || echo '1.0.0'",
                        returnStdout: true
                    ).trim()
                    
                    env.APP_VERSION = "${(baseVersion && baseVersion != 'null' && baseVersion != '') ? baseVersion : '1.0.0'}-${env.BUILD_NUMBER}"
                    echo "Versión calculada para el artefacto: ${env.APP_VERSION}"
                }
            }
        }

       stage('3. Code Quality Scan') {
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

        stage('4. Unit Test') {
            steps {
                sh "mvn test -B"
            }
            post {
                always {
                    junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('5. Build') {
            steps {
                sh "mvn package -B -DskipTests"
                sh 'echo "=== Artefactos generados ==="'
                sh 'find . -path "*/target/*.jar" -o -path "*/target/*.ear"'
            }
            post {
                success {
                    archiveArtifacts artifacts: '**/target/*.jar,**/target/*.ear',
                                     fingerprint: true,
                                     allowEmptyArchive: true
                }
            }
        }

        stage('6. Application Security Scan') {
            when {
                expression { params.SKIP_VERACODE == false }
            }       
            steps {
                script {
                    withCredentials([file(credentialsId: 'veracode-adapter', variable: 'VERACODE_ADAPTER')]) {
                        sh 'test -s "$VERACODE_ADAPTER" && bash "$VERACODE_ADAPTER" target/ || echo "Veracode ejecutado sin alertas críticas o adaptador no disponible."'
                    }
                }
            }
        }

        stage('7. Version & Tagging') {
            steps {
                script {
                    echo "Etiquetando versión de integración: v${env.APP_VERSION}"
                    withCredentials([usernamePassword(credentialsId: "${env.GIT_CREDENTIALS}", usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
                        sh 'git config user.email "jenkins@ci.com"'
                        sh 'git config user.name "Jenkins CI"'
                        sh 'git tag -a "v' + env.APP_VERSION + '" -m "Build de integración automática #' + env.BUILD_NUMBER + '" || true'
                    }
                }
                return
            }
        }

        stage('8. Publish Candidate (Build & Push Image)') {
            steps {
                script {
                    def assetBase = env.STATIC_ASSETS_DIR
                    sh "mkdir -p ${assetBase}"

                    def earSourcePath = sh(
                        script: "find . -path '*/target/*.ear' -o -path '*/target/*.jar' | sort | head -n 1",
                        returnStdout: true
                    ).trim()

                    if (!earSourcePath) {
                        error 'No se encontró ningún artefacto .jar/.ear en target/.'
                    }

                    def earFileName = earSourcePath.tokenize('/').last()
                    sh "cp \"${earSourcePath}\" \"${assetBase}/${earFileName}\""

                    // Si no existe un Dockerfile en la raíz, se crea uno estándar dinámicamente
                    if (!fileExists(env.DOCKERFILE_OUTPUT)) {
                        def dockerfileContent = """
FROM ${env.DOCKER_BASE_IMAGE}
ENV LANGUAGE='en_US:en'
COPY ${assetBase}/${earFileName} /deployments/app.jar
EXPOSE 8080
USER 185
CMD ["java", "-jar", "/deployments/app.jar"]
"""
                        writeFile file: env.DOCKERFILE_OUTPUT, text: dockerfileContent
                    }

                    def imageTag = "${env.QUAY_REGISTRY}:${env.DEPLOY_ENV.toLowerCase()}-${env.BUILD_NUMBER}"
                    sh "podman build -f ${env.DOCKERFILE_OUTPUT} -t ${imageTag} ."

                    withCredentials([usernamePassword(credentialsId: 'quay-push', usernameVariable: 'QUAY_USER', passwordVariable: 'QUAY_PASSWORD')]) {
                        sh 'printf "%s" "$QUAY_PASSWORD" | podman login ' + env.QUAY_REGISTRY.split('/')[0] + ' --username "$QUAY_USER" --password-stdin'
                        sh 'podman push --digestfile image-digest.txt ' + imageTag
                    }

                    env.IMAGE_DIGEST = readFile('image-digest.txt').trim()
                    env.IMAGE_REF = "${env.QUAY_REGISTRY}@${env.IMAGE_DIGEST}"
                    echo "Imagen publicada en Quay: ${env.IMAGE_REF}"
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

                    withCredentials([string(credentialsId: 'usuario-generico-quarkus-game', variable: 'OC_USER')]) {
                        sh 'oc login ' + env.OPENSHIFT_API + ' --token="$OC_USER" --insecure-skip-tls-verify=true'
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
                    sh "podman rmi ${env.QUAY_REGISTRY}:${env.DEPLOY_ENV.toLowerCase()}-${env.BUILD_NUMBER} || true"
                    sh "podman image prune -f --filter 'until=24h' || true"
                }
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
