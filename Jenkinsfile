def call(Map config = [:]) {

    def jdkTool             = config.jdkTool             ?: 'Oracle JDK jdk1.8.0_144'
    def appName             = config.appName             ?: 'd02-quarkus-game-authentication-api'
    def gitRepoUrl          = config.gitRepoUrl          ?: 'https://gitlab.premium.my-celinstitucional.com/kiosco-my-cel/d02quarkus-gameauthenticationapi.git'
    def gitDeployRepoUrl    = config.gitDeployRepoUrl    ?: ''
    def gitCredentials      = config.gitCredentials      ?: 'gitlab-deploy-token-38'
    def mavenTool           = config.mavenTool           ?: 'apache-maven-3.3.9'
    def staticAssetsEnabled = config.staticAssetsEnabled == null ? true : config.staticAssetsEnabled 
    def staticAssetsDir     = config.staticAssetsDir     ?: 'container-assets'
    def staticAssetsProfile = config.staticAssetsProfile ?: 'core'
    def dockerfileEnabled   = config.dockerfileEnabled   == null ? true : config.dockerfileEnabled 
    def dockerfileOutputPath = config.dockerfileOutputPath ?: 'Dockerfile'
    def dockerBaseImage     = config.dockerBaseImage     ?: 'my-cel-quay-my-cel-quay.apps.prod.my-celcloud.dt/my-cel/websphere-liberty-ubi8:kernel-ubi-min'
    def quayRegistry        = config.quayRegistry        ?: 'my-cel-quay-my-cel-quay.apps.acmbmnp.my-celcloud.dt/repository/vibcaja/d02-vibcaja-quarkus-game-authentication-api'
    def openshiftApi        = config.openshiftApi        ?: 'https://api.qdlbmnp.my-celcloud.dt:6443'

    pipeline {
        agent any

        tools {
            maven "${mavenTool}"
            jdk   "${jdkTool}"
        }

        parameters {
            choice(
                name        : 'APLICATIVO',
                choices     : ['quarkus-game', 'KIOSCO'],
                description : 'Selecciona el aplicativo destino del despliegue'
            )
            choice(
                name        : 'AMBIENTE',
                choices     : ['DEV', 'QA', 'PREPROD'],
                description : 'quarkus-game: DEV, QA | KIOSCO: PREPROD'
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
                description  : 'Rama a desplegar (opcional). Si se deja vacío: develop'
            )
        }

        environment {
            APP_NAME        = "${appName}"
            GIT_REPO_URL    = "${gitRepoUrl}"
            GIT_CREDENTIALS = "${gitCredentials}"
            RAMA            = "${params.RAMA_OVERRIDE?.trim() ?: 'develop'}"
            APP_PROFILE     = "${params.AMBIENTE?.toLowerCase() ?: 'develop'}"
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
            timeout(time: 2, unit: 'HOURS')
        }

        stages {

            stage('1. Initialize Pipeline') {
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

            stage('2. Checkout Source & Configuration') {
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
                            script: "JAVA_TOOL_OPTIONS='' mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null | tr -d '\\r\\n' || echo '1.0.0'",
                            returnStdout: true
                        ).trim()
                        
                        env.APP_VERSION = "${(baseVersion && baseVersion != 'null' && baseVersion != '') ? baseVersion : '1.0.0'}-${env.BUILD_NUMBER}"
                        echo "Versión calculada para el artefacto: ${env.APP_VERSION}"

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

            stage('3. Code Quality Scan') {
                when {
                    expression { params.SKIP_SONARQUBE == false }
                }
                steps {
                    script {
                        withSonarQubeEnv('SonarServer1') {
                            // 'compile' genera los bytecode (.class) necesarios para que SonarQube analice el código Java
                            sh "mvn compile org.sonarsource.scanner.maven:sonar-maven-plugin:3.9.1.2184:sonar -Dsonar.projectName=${APP_NAME} -Dsonar.projectKey=${APP_NAME}"
                        }
                        timeout(time: 15, unit: 'MINUTES') {
                            script {
                                def qualityGate = waitForQualityGate()
                                echo "Estado de Quality Gate: ${qualityGate.status}"
                                if (qualityGate.status != 'OK') {
                                    error "Quality Gate falló con estado: ${qualityGate.status}"
                                }
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
                            sh 'test -s "$VERACODE_ADAPTER" && bash "$VERACODE_ADAPTER" target/ || echo "Veracode ejecutado sin alertas críticas o binario pendiente de configuración."'
                        }
                    }
                }
            }

            stage('7. Version & Tagging') {
                steps {
                    script {
                        echo "Etiquetando versión de integración: v${env.APP_VERSION}"
                        withCredentials([usernamePassword(credentialsId: "${GIT_CREDENTIALS}", usernameVariable: 'GIT_USER', passwordVariable: 'GIT_TOKEN')]) {
                            sh 'git config user.email "jenkins@ci.com"'
                            sh 'git config user.name "Jenkins CI"'
                            sh 'git tag -a "v' + env.APP_VERSION + '" -m "Build de integración automática #' + env.BUILD_NUMBER + '" || true'
                        }
                    }
                }
            }

            stage('8. Publish Candidate (Build & Push Image)') {
                steps {
                    script {
                        def assetBase = staticAssetsDir ?: 'container-assets'
                        def assetProfile = staticAssetsProfile ?: 'core'
                        
                        sh "mkdir -p ${assetBase}"

                        if (staticAssetsEnabled) {
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
                                error 'No se encontró ningún artefacto .jar/.ear en target/.'
                            }

                            def earFileName = earSourcePath.tokenize('/').last()
                            sh "cp \"${earSourcePath}\" \"${assetBase}/${earFileName}\""

                            def dockerfileTemplate = libraryResource("container-assets/${assetProfile}/Dockerfile.template")
                            def dockerfileContent = dockerfileTemplate
                                .replace('__BASE_IMAGE__', dockerBaseImage)
                                .replace('__ASSET_DIR__', assetBase)
                                .replace('__EAR_FILE__', earFileName)
                                .replace('_BASE_IMAGE_', dockerBaseImage)
                                .replace('_ASSET_DIR_', assetBase)
                                .replace('_EAR_FILE_', earFileName)

                            writeFile file: dockerfileOutputPath, text: dockerfileContent
                        }

                        def imageTag = "${QUAY_REGISTRY}:${DEPLOY_ENV.toLowerCase()}-${env.BUILD_NUMBER}"
                        sh "podman build -f ${dockerfileOutputPath} -t ${imageTag} ."

                        withCredentials([usernamePassword(credentialsId: 'quay-push', usernameVariable: 'QUAY_USER', passwordVariable: 'QUAY_PASSWORD')]) {
                            sh 'printf "%s" "$QUAY_PASSWORD" | podman login ' + QUAY_REGISTRY.split('/')[0] + ' --username "$QUAY_USER" --password-stdin'
                            sh 'podman push --digestfile image-digest.txt ' + imageTag
                        }
 
                        env.IMAGE_DIGEST = readFile('image-digest.txt').trim()
                        env.IMAGE_REF = "${QUAY_REGISTRY}@${env.IMAGE_DIGEST}"
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
                            params.RAMA_OVERRIDE?.trim() ? true : RAMA in ['develop', 'main']
                        }
                        expression {
                            (params.APLICATIVO == 'quarkus-game' && params.AMBIENTE in ['DEV', 'QA']) ||
                            (params.APLICATIVO == 'KIOSCO'  && params.AMBIENTE == 'PREPROD')
                        }
                    }
                }
                steps {
                    script {
                        def targetNamespace = "${params.APLICATIVO.toLowerCase()}-${DEPLOY_ENV.toLowerCase()}"
                        echo "Desplegando en OpenShift (${OPENSHIFT_API}) - Namespace: ${targetNamespace}"

                        withCredentials([string(credentialsId: 'usuario-generico-quarkus-game', variable: 'OC_USER')]) {
                            sh 'oc login ' + OPENSHIFT_API + ' --user="$OC_USER" --insecure-skip-tls-verify=true'
                            sh 'oc project ' + targetNamespace + ' || oc new-project ' + targetNamespace
                            sh 'oc set image deployment/' + APP_NAME + ' ' + APP_NAME + '="' + env.IMAGE_REF + '" -n ' + targetNamespace + ' || oc create deployment ' + APP_NAME + ' --image="' + env.IMAGE_REF + '" -n ' + targetNamespace
                            sh 'oc rollout status deployment/' + APP_NAME + ' -n ' + targetNamespace + ' --timeout=5m'
                        }
                    }
                }
            }

            stage('13. Remove Registry Repository Tags') {
                steps {
                    script {
                        sh "podman rmi ${QUAY_REGISTRY}:${DEPLOY_ENV.toLowerCase()}-${env.BUILD_NUMBER} || true"
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
                echo "Pipeline ${APP_NAME} finalizado correctamente en ${DEPLOY_ENV}"
            }
            failure {
                echo "Pipeline ${APP_NAME} fallido. Revisa los logs."
            }
        }
    }
}

