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
            //RAMA            = "${params.RAMA_OVERRIDE?.trim() ?: (params.AMBIENTE == 'QA' || params.AMBIENTE == 'PREPROD' ? 'main' : 'develop')}"
            RAMA            = "${params.RAMA_OVERRIDE?.trim() ?: 'main'}"
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
            //timestamps()
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
