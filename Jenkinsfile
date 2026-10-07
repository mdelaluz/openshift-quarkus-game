// -------------------------------------------------------------------------
// DEFINICIONES GENERALES DEL PIPELINE QA
// -------------------------------------------------------------------------

def appName = 'd02-sicatel-authentication-api'

// -------------------------------------------------------------------------
// REPOSITORIO DE CÓDIGO Y CREDENCIALES DE GITLAB
// -------------------------------------------------------------------------
def gitRepoUrl       = 'https://gitlab.premium.telcelinstitucional.com/kiosco-telcel/d02sicatelauthenticationapi.git'
def gitCredentials   = 'gitlab-deploy-token-38'
def gitDeployRepoUrl = gitRepoUrl

// -------------------------------------------------------------------------
// IMAGEN DEV VALIDADA: ORIGEN DE LA PROMOCIÓN
// -------------------------------------------------------------------------
def devQuayDomain       = 'telcel-quay-telcel-quay.apps.acmbmnp.telcelcloud.dt'
def devQuayOrganization = 'vibcaja'
def devQuayRepository   = 'd02-vibcaja-sicatel-authentication-api'
def devQuayRegistry     = "${devQuayDomain}/${devQuayOrganization}/${devQuayRepository}"

// -------------------------------------------------------------------------
// IMAGEN QA: DESTINO DE LA PROMOCIÓN
// -------------------------------------------------------------------------
def qaQuayDomain       = devQuayDomain
def qaQuayOrganization = 'vibcaja'
def qaQuayRepository   = 'd02-vibcaja-sicatel-authentication-api'
def qaQuayRegistry     = "${qaQuayDomain}/${qaQuayOrganization}/${qaQuayRepository}"

// Credencial Jenkins del Robot Account y secret de pull en OpenShift.
def quayCredentials = 'quay-robot-sicatel'
def quaySecretName  = 'credenciales-quay-telcel'
def qaTagsToKeep = 2
def queryLimit    = 100

// Política HTTP común: máximo 15 minutos por operación, con reintentos acotados.
def curlOptions = '--silent --show-error --insecure --connect-timeout 15 --max-time 900 --retry 2 --retry-delay 5 --retry-max-time 900'

// -------------------------------------------------------------------------
// OPENSHIFT QA
// -------------------------------------------------------------------------

def openshiftApi         = 'https://api.qdlbmnp.telcelcloud.dt:6443'
def jenkinsOcCredentials = 'usuario-generico-sicatel'
def qaNamespace          = 'vibcaja-qa'

// -------------------------------------------------------------------------
// CONFIGURACIÓN QA Y MANIFIESTO DE OPENSHIFT
// -------------------------------------------------------------------------
def staticAssetsDir      = 'container-assets'
def staticAssetsProfile  = 'core'
def qaConfigPath         = "resources/${staticAssetsDir}/qa/qa-promotion.properties"
def deployManifestPath   = "resources/${staticAssetsDir}/${staticAssetsProfile}/D02-sicatel-authentication-api-openshift-qa.yml"

// Propiedades requeridas en qa-promotion.properties:
// DEV_IMAGE_TAG, DEV_IMAGE_DIGEST, QA_IMAGE_TAG, QA_TAG_PREFIX,
// RELEASE_GIT_COMMIT, DEV_BUILD_NUMBER, SONAR_APPROVED,
// VERACODE_APPROVED, SBOM_AVAILABLE, EXPECTED_DEPLOYMENT,
// EXPECTED_SERVICE, EXPECTED_SERVICE_ACCOUNT, REQUIRE_ROUTE, REQUIRE_HPA,
// REQUIRED_PVCS, REQUIRED_CONFIGMAPS, REQUIRED_SECRETS, HEALTH_ENABLED,
// HEALTH_PATH, EXPECTED_HTTP_STATUS y ROLLOUT_TIMEOUT.


pipeline {
    agent any

    // =========================================================================
    // PARÁMETROS DE ENTRADA DEL PIPELINE
    // =========================================================================
    parameters {
        string(
            name         : 'RAMA_OVERRIDE',
            defaultValue : '',
            description  : 'Rama que contiene la configuración QA. Si se deja vacío se utiliza develop.'
        )
        string(
            name         : 'QA_CONFIG_OVERRIDE',
            defaultValue : '',
            description  : 'Ruta alternativa del archivo qa-promotion.properties.'
        )
        booleanParam(
            name         : 'SKIP_SECURITY_EVIDENCE',
            defaultValue : true,
            description  : 'Omitir temporalmente la validación obligatoria de evidencias de seguridad.'
        )
        choice(
            name        : 'CLEANUP_MODE',
            choices     : ['LIST_ONLY', 'DELETE'],
            description : 'LIST_ONLY muestra candidatos. DELETE elimina los tags antiguos de QA.'
        )
    }

    // =========================================================================
    // VARIABLES DE ENTORNO GLOBALES
    // =========================================================================
    environment {

        APP_NAME  = "${appName}"
        DEPLOY_ENV = 'QA'
        GIT_REPO_URL    = "${gitRepoUrl}"
        GIT_CREDENTIALS = "${gitCredentials}"
        RAMA            = "${params.RAMA_OVERRIDE?.trim() ?: 'develop'}"
        DEV_QUAY_DOMAIN       = "${devQuayDomain}"
        DEV_QUAY_ORGANIZATION = "${devQuayOrganization}"
        DEV_QUAY_REPOSITORY   = "${devQuayRepository}"
        DEV_QUAY_REGISTRY     = "${devQuayRegistry}"
        QA_QUAY_DOMAIN       = "${qaQuayDomain}"
        QA_QUAY_ORGANIZATION = "${qaQuayOrganization}"
        QA_QUAY_REPOSITORY   = "${qaQuayRepository}"
        QA_QUAY_REGISTRY     = "${qaQuayRegistry}"
        QUAY_CREDENTIALS     = "${quayCredentials}"
        QUAY_SECRET_NAME     = "${quaySecretName}"
        QA_TAGS_TO_KEEP      = "${qaTagsToKeep}"
        QUERY_LIMIT          = "${queryLimit}"
        CURL_OPTIONS         = "${curlOptions}"
        OPENSHIFT_API    = "${openshiftApi}"
        JENKINS_OC_CREDS = "${jenkinsOcCredentials}"
        QA_NAMESPACE     = "${qaNamespace}"
        QA_CONFIG_PATH      = "${params.QA_CONFIG_OVERRIDE?.trim() ?: qaConfigPath}"
        STATIC_ASSETS_DIR   = "${staticAssetsDir}"
        DEPLOY_MANIFEST_PATH = "${deployManifestPath}"
    }

    // =========================================================================
    // OPCIONES DEL PIPELINE
    // =========================================================================
    options {
        buildDiscarder(logRotator(numToKeepStr: '5'))
        disableConcurrentBuilds()
        timeout(time: 2, unit: 'HOURS')
    }

    // =========================================================================
    // ETAPAS DEL PIPELINE QA
    // =========================================================================
    stages {

        // =========================================================================
        // STAGE 1: INICIALIZACIÓN DEL PIPELINE QA
        // =========================================================================
        stage('1. Initialize QA Pipeline') {
            steps {
                script {
                    echo "=========================================================="
                    echo "  App                  : ${env.APP_NAME}"
                    echo "  Ambiente             : ${env.DEPLOY_ENV}"
                    echo "  Rama configuración   : ${env.RAMA}"
                    echo "  Archivo configuración: ${env.QA_CONFIG_PATH}"
                    echo "  Quay DEV             : ${env.DEV_QUAY_REGISTRY}"
                    echo "  Quay QA              : ${env.QA_QUAY_REGISTRY}"
                    echo "  Namespace QA         : ${env.QA_NAMESPACE}"
                    echo "  Cluster API          : ${env.OPENSHIFT_API}"
                    echo "=========================================================="

                    sh """
                        command -v git  > /dev/null
                        command -v oc   > /dev/null
                        command -v curl > /dev/null
                        command -v awk  > /dev/null
                        command -v sed  > /dev/null
                        command -v grep > /dev/null
                    """

                    echo 'Inicialización QA completada correctamente.'
                }
            }
        }

        // =========================================================================
        // STAGE 2: RECUPERAR RELEASE DEV VALIDADA
        // =========================================================================
        stage('2. Retrieve DEV Validated Release') {
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

                    if (!fileExists(env.QA_CONFIG_PATH)) {
                        error "No existe el archivo de configuración QA: ${env.QA_CONFIG_PATH}"
                    }

                    // El archivo properties se procesa sin requerir plugins adicionales.
                    def qaConfig = [:]

                    readFile(env.QA_CONFIG_PATH).readLines().eachWithIndex { rawLine, index ->
                        def line = rawLine.trim()

                        if (line && !line.startsWith('#')) {
                            def separator = line.indexOf('=')

                            if (separator <= 0) {
                                error "Línea inválida en ${env.QA_CONFIG_PATH}, línea ${index + 1}: ${line}"
                            }

                            def key = line.substring(0, separator).trim()
                            def value = line.substring(separator + 1).trim()

                            if (qaConfig.containsKey(key)) {
                                error "La propiedad ${key} está duplicada en ${env.QA_CONFIG_PATH}."
                            }

                            qaConfig[key] = value
                        }
                    }

                    def requiredProperties = [
                        'DEV_IMAGE_TAG', 'DEV_IMAGE_DIGEST', 'QA_IMAGE_TAG', 'QA_TAG_PREFIX',
                        'RELEASE_GIT_COMMIT', 'DEV_BUILD_NUMBER', 'SONAR_APPROVED',
                        'VERACODE_APPROVED', 'SBOM_AVAILABLE', 'EXPECTED_DEPLOYMENT',
                        'EXPECTED_SERVICE', 'EXPECTED_SERVICE_ACCOUNT', 'REQUIRE_ROUTE',
                        'REQUIRE_HPA', 'REQUIRED_PVCS', 'HEALTH_ENABLED', 'HEALTH_PATH',
                        'EXPECTED_HTTP_STATUS', 'ROLLOUT_TIMEOUT'
                    ]

                    def missingProperties = requiredProperties.findAll { !qaConfig[it]?.trim() }

                    if (missingProperties) {
                        error "Faltan propiedades obligatorias en ${env.QA_CONFIG_PATH}: ${missingProperties.join(', ')}"
                    }

                    // Estas listas deben declararse expresamente, aunque su valor pueda quedar vacío
                    // cuando la aplicación no requiera ConfigMaps o Secrets externos.
                    def requiredListProperties = ['REQUIRED_CONFIGMAPS', 'REQUIRED_SECRETS']
                    def missingListProperties = requiredListProperties.findAll { !qaConfig.containsKey(it) }

                    if (missingListProperties) {
                        error "Faltan listas de prerrequisitos en ${env.QA_CONFIG_PATH}: ${missingListProperties.join(', ')}"
                    }
                    
                    env.DEV_IMAGE_TAG            = qaConfig.DEV_IMAGE_TAG
                    env.DEV_IMAGE_DIGEST         = qaConfig.DEV_IMAGE_DIGEST
                    env.QA_IMAGE_TAG             = qaConfig.QA_IMAGE_TAG
                    env.QA_TAG_PREFIX            = qaConfig.QA_TAG_PREFIX
                    env.RELEASE_GIT_COMMIT       = qaConfig.RELEASE_GIT_COMMIT
                    env.DEV_BUILD_NUMBER         = qaConfig.DEV_BUILD_NUMBER
                    env.SONAR_APPROVED           = qaConfig.SONAR_APPROVED.toLowerCase()
                    env.VERACODE_APPROVED        = qaConfig.VERACODE_APPROVED.toLowerCase()
                    env.SBOM_AVAILABLE           = qaConfig.SBOM_AVAILABLE.toLowerCase()
                    env.EXPECTED_DEPLOYMENT      = qaConfig.EXPECTED_DEPLOYMENT
                    env.EXPECTED_SERVICE         = qaConfig.EXPECTED_SERVICE
                    env.EXPECTED_SERVICE_ACCOUNT = qaConfig.EXPECTED_SERVICE_ACCOUNT
                    env.REQUIRE_ROUTE            = qaConfig.REQUIRE_ROUTE.toLowerCase()
                    env.REQUIRE_HPA              = qaConfig.REQUIRE_HPA.toLowerCase()
                    env.REQUIRED_PVCS            = qaConfig.REQUIRED_PVCS
                    env.REQUIRED_CONFIGMAPS      = qaConfig.REQUIRED_CONFIGMAPS
                    env.REQUIRED_SECRETS         = qaConfig.REQUIRED_SECRETS
                    env.HEALTH_ENABLED           = qaConfig.HEALTH_ENABLED.toLowerCase()
                    env.HEALTH_PATH              = qaConfig.HEALTH_PATH
                    env.EXPECTED_HTTP_STATUS     = qaConfig.EXPECTED_HTTP_STATUS
                    env.ROLLOUT_TIMEOUT          = qaConfig.ROLLOUT_TIMEOUT

                    env.DEV_IMAGE_TAG_REF    = "${env.DEV_QUAY_REGISTRY}:${env.DEV_IMAGE_TAG}"
                    env.DEV_IMAGE_DIGEST_REF = "${env.DEV_QUAY_REGISTRY}@${env.DEV_IMAGE_DIGEST}"
                    env.QA_IMAGE_TAG_REF     = "${env.QA_QUAY_REGISTRY}:${env.QA_IMAGE_TAG}"
                    env.QA_IMAGE_DIGEST_REF  = "${env.QA_QUAY_REGISTRY}@${env.DEV_IMAGE_DIGEST}"

                    echo "=========================================================="
                    echo "  Build DEV       : ${env.DEV_BUILD_NUMBER}"
                    echo "  Commit          : ${env.RELEASE_GIT_COMMIT}"
                    echo "  Tag DEV         : ${env.DEV_IMAGE_TAG}"
                    echo "  Digest esperado : ${env.DEV_IMAGE_DIGEST}"
                    echo "  Tag QA          : ${env.QA_IMAGE_TAG}"
                    echo "=========================================================="
                }
            }
        }

        // =========================================================================
        // STAGE 3: VALIDAR CONFIGURACIÓN QA
        // =========================================================================
        stage('3. Checkout & Validate QA Configuration') {
            steps {
                script {
                    if (!fileExists(env.DEPLOY_MANIFEST_PATH)) {
                        error "No existe el manifiesto de despliegue: ${env.DEPLOY_MANIFEST_PATH}"
                    }

                    def tagPattern = /^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$/
                    def kubernetesNamePattern = /^[a-z0-9]([-a-z0-9.]*[a-z0-9])?$/

                    def patternValidations = [
                        DEV_IMAGE_DIGEST       : [env.DEV_IMAGE_DIGEST, /^sha256:[0-9a-f]{64}$/],
                        DEV_IMAGE_TAG          : [env.DEV_IMAGE_TAG, tagPattern],
                        QA_IMAGE_TAG           : [env.QA_IMAGE_TAG, tagPattern],
                        QA_TAG_PREFIX          : [env.QA_TAG_PREFIX, /^[A-Za-z0-9_][A-Za-z0-9_.-]*$/],
                        RELEASE_GIT_COMMIT     : [env.RELEASE_GIT_COMMIT, /^[0-9a-fA-F]{7,40}$/],
                        DEV_BUILD_NUMBER       : [env.DEV_BUILD_NUMBER, /^[0-9]+$/],
                        EXPECTED_DEPLOYMENT    : [env.EXPECTED_DEPLOYMENT, kubernetesNamePattern],
                        EXPECTED_SERVICE       : [env.EXPECTED_SERVICE, kubernetesNamePattern],
                        EXPECTED_SERVICE_ACCOUNT: [env.EXPECTED_SERVICE_ACCOUNT, kubernetesNamePattern],
                        HEALTH_PATH            : [env.HEALTH_PATH, /^\/[A-Za-z0-9._~\/%?=&-]*$/],
                        EXPECTED_HTTP_STATUS   : [env.EXPECTED_HTTP_STATUS, /^[1-5][0-9]{2}$/],
                        ROLLOUT_TIMEOUT        : [env.ROLLOUT_TIMEOUT, /^[1-9][0-9]*[smh]$/]
                    ]

                    patternValidations.each { propertyName, validation ->
                        if (!(validation[0] ==~ validation[1])) {
                            error "${propertyName} contiene un valor inválido: ${validation[0]}"
                        }
                    }

                    if (!env.QA_IMAGE_TAG.startsWith(env.QA_TAG_PREFIX)) {
                        error "QA_IMAGE_TAG debe comenzar con el prefijo ${env.QA_TAG_PREFIX}."
                    }

                    [
                        REQUIRED_PVCS      : [env.REQUIRED_PVCS, false],
                        REQUIRED_CONFIGMAPS: [env.REQUIRED_CONFIGMAPS, true],
                        REQUIRED_SECRETS   : [env.REQUIRED_SECRETS, true]
                    ].each { propertyName, validation ->
                        def names = validation[0].tokenize(',').collect { it.trim() }
                        if ((!validation[1] && !names) || names.any { !(it ==~ kubernetesNamePattern) }) {
                            error "${propertyName} debe contener nombres válidos separados por comas${validation[1] ? ' o quedar vacío' : ''}."
                        }
                    }

                    def booleanValues = [
                        SONAR_APPROVED   : env.SONAR_APPROVED,
                        VERACODE_APPROVED: env.VERACODE_APPROVED,
                        SBOM_AVAILABLE   : env.SBOM_AVAILABLE,
                        REQUIRE_ROUTE    : env.REQUIRE_ROUTE,
                        REQUIRE_HPA      : env.REQUIRE_HPA,
                        HEALTH_ENABLED   : env.HEALTH_ENABLED
                    ]

                    booleanValues.each { propertyName, propertyValue ->
                        if (!(propertyValue in ['true', 'false'])) {
                            error "${propertyName} debe contener true o false. Valor recibido: ${propertyValue}"
                        }
                    }

                    if (env.EXPECTED_DEPLOYMENT != env.APP_NAME) {
                        error "EXPECTED_DEPLOYMENT debe coincidir con APP_NAME (${env.APP_NAME})."
                    }

                    echo 'Configuración QA validada correctamente.'
                    echo "Manifiesto: ${env.DEPLOY_MANIFEST_PATH}"
                    echo "Namespace : ${env.QA_NAMESPACE}"
                }
            }
        }

        // =========================================================================
        // STAGE 4: VERIFICAR IDENTIDAD DE LA RELEASE
        // =========================================================================
        stage('4. Verify Release Identity') {
            steps {
                script {
                    sh """
                        git cat-file -e "${env.RELEASE_GIT_COMMIT}^{commit}"
                    """

                    withCredentials([usernamePassword(
                        credentialsId   : "${env.QUAY_CREDENTIALS}",
                        usernameVariable: 'QUAY_USER',
                        passwordVariable: 'QUAY_PASSWORD'
                    )]) {
                        sh """
                            set +x
                            AUTH_TOKEN=\$(curl ${env.CURL_OPTIONS} \
                                -u "\$QUAY_USER:\$QUAY_PASSWORD" \
                                "https://${env.DEV_QUAY_DOMAIN}/v2/auth?service=${env.DEV_QUAY_DOMAIN}&scope=repository:${env.DEV_QUAY_ORGANIZATION}/${env.DEV_QUAY_REPOSITORY}:pull" \
                                | grep -o '"token":"[^"]*' \
                                | cut -d'"' -f4)
                            if [ -z "\$AUTH_TOKEN" ]; then
                                echo 'ERROR: No se pudo obtener el token de lectura para la imagen DEV.'
                                exit 1
                            fi
                            SOURCE_DIGEST=\$(curl ${env.CURL_OPTIONS} -I \
                                -H "Authorization: Bearer \$AUTH_TOKEN" \
                                -H 'Accept: application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.manifest.v1+json' \
                                "https://${env.DEV_QUAY_DOMAIN}/v2/${env.DEV_QUAY_ORGANIZATION}/${env.DEV_QUAY_REPOSITORY}/manifests/${env.DEV_IMAGE_TAG}" \
                                | grep -i 'docker-content-digest' \
                                | head -n 1 \
                                | awk '{print \$2}' \
                                | tr -d '\\r\\n')
                            if [ -z "\$SOURCE_DIGEST" ]; then
                                echo 'ERROR: No se pudo obtener el digest del tag DEV.'
                                exit 1
                            fi
                            if [ "\$SOURCE_DIGEST" != "${env.DEV_IMAGE_DIGEST}" ]; then
                                echo 'ERROR: El tag DEV no corresponde al digest esperado.'
                                echo "       Tag             : ${env.DEV_IMAGE_TAG_REF}"
                                echo "       Digest esperado : ${env.DEV_IMAGE_DIGEST}"
                                echo "       Digest obtenido : \$SOURCE_DIGEST"
                                exit 1
                            fi
                            echo 'Identidad de la release DEV validada correctamente.'
                            echo "Imagen: ${env.DEV_IMAGE_DIGEST_REF}"
                        """
                    }
                }
            }
        }

        // =========================================================================
        // STAGE 5: VALIDAR EVIDENCIAS DE SEGURIDAD Y CADENA DE SUMINISTRO
        // =========================================================================
        stage('5. Verify Security & Supply Chain Evidence') {
            steps {
                script {
                    if (params.SKIP_SECURITY_EVIDENCE) {
                        echo 'Validación obligatoria de evidencias omitida temporalmente.'
                        echo "SonarQube aprobado : ${env.SONAR_APPROVED}"
                        echo "Veracode aprobado  : ${env.VERACODE_APPROVED}"
                        echo "SBOM disponible    : ${env.SBOM_AVAILABLE}"
                    } else {
                        if (env.SONAR_APPROVED != 'true') {
                            error 'La evidencia de SonarQube no está aprobada.'
                        }

                        if (env.VERACODE_APPROVED != 'true') {
                            error 'La evidencia de Veracode no está aprobada.'
                        }

                        if (env.SBOM_AVAILABLE != 'true') {
                            error 'No se confirmó la disponibilidad del SBOM.'
                        }

                        echo 'Evidencias de calidad, seguridad y SBOM aprobadas.'
                    }

                    // Trusted Profile Analyzer:
                    // Pendiente integrar la validación del SBOM y evaluación de riesgo.

                    // Trusted Artifact Signer:
                    // Pendiente verificar firma y attestations del digest promovido.

                    // Red Hat Advanced Cluster Security:
                    // Pendiente evaluar la política de imagen antes del despliegue QA.
                }
            }
        }

        // =========================================================================
        // STAGE 6: PREPARACIÓN DE QA Y GATE DE PROMOCIÓN
        // =========================================================================
        stage('6. QA Readiness & Promotion Gate') {
            steps {
                script {
                    def manifestFile = "${env.WORKSPACE}/${env.DEPLOY_MANIFEST_PATH}"
                    def renderedManifest = "${env.WORKSPACE}/qa-rendered-manifest.yml"

                    echo "Imagen origen : ${env.DEV_IMAGE_DIGEST_REF}"
                    echo "Imagen destino: ${env.QA_IMAGE_TAG_REF}"
                    echo "Namespace QA  : ${env.QA_NAMESPACE}"

                    withCredentials([usernamePassword(
                        credentialsId   : "${env.JENKINS_OC_CREDS}",
                        usernameVariable: 'OC_USER',
                        passwordVariable: 'OC_PASSWORD'
                    )]) {
                        sh """
                            set +x
                            # =============================================================
                            # 1. AUTENTICACIÓN EN OPENSHIFT QA
                            # =============================================================
                            oc login \
                                --server="${env.OPENSHIFT_API}" \
                                --username="\$OC_USER" \
                                --password="\$OC_PASSWORD" \
                                --insecure-skip-tls-verify=false
                            echo "Autenticación correcta como: \$(oc whoami)"
                            # =============================================================
                            # 2. VALIDACIÓN DEL NAMESPACE QA
                            # =============================================================
                            if ! oc get project "${env.QA_NAMESPACE}" > /dev/null 2>&1; then
                                echo "ERROR: El namespace QA '${env.QA_NAMESPACE}' no existe."
                                echo '       Debe ser preparado antes de ejecutar la promoción.'
                                exit 1
                            fi
                            oc project "${env.QA_NAMESPACE}"
                            # =============================================================
                            # 3. VALIDACIÓN DE SERVICEACCOUNT Y PULL SECRET
                            # =============================================================
                            if ! oc get serviceaccount "${env.EXPECTED_SERVICE_ACCOUNT}" \
                                -n "${env.QA_NAMESPACE}" > /dev/null 2>&1; then
                                echo "ERROR: No existe la ServiceAccount '${env.EXPECTED_SERVICE_ACCOUNT}'."
                                exit 1
                            fi
                            if ! oc get secret "${env.QUAY_SECRET_NAME}" \
                                -n "${env.QA_NAMESPACE}" > /dev/null 2>&1; then
                                echo "ERROR: No existe el pull secret '${env.QUAY_SECRET_NAME}'."
                                exit 1
                            fi
                            LINKED_PULL_SECRETS=\$(
                                oc get serviceaccount "${env.EXPECTED_SERVICE_ACCOUNT}" \
                                    -n "${env.QA_NAMESPACE}" \
                                    -o jsonpath='{.imagePullSecrets[*].name}'
                            )
                            if ! echo "\$LINKED_PULL_SECRETS" \
                                | tr ' ' '\\n' \
                                | grep -Fxq "${env.QUAY_SECRET_NAME}"; then
                                echo "ERROR: El pull secret '${env.QUAY_SECRET_NAME}' no está vinculado a la ServiceAccount '${env.EXPECTED_SERVICE_ACCOUNT}'."
                                exit 1
                            fi
                            # =============================================================
                            # 4-6. VALIDACIÓN DE RECURSOS PRERREQUERIDOS
                            # =============================================================
                            validate_required_resources() {
                                RESOURCE_KIND="\$1"
                                RESOURCE_LABEL="\$2"
                                RESOURCE_NAMES="\$3"
                                PREVIOUS_IFS="\$IFS"
                                IFS=','
                                for RESOURCE_NAME in \$RESOURCE_NAMES; do
                                    RESOURCE_NAME=\$(echo "\$RESOURCE_NAME" | xargs)
                                    [ -z "\$RESOURCE_NAME" ] && continue
                                    if ! oc get "\$RESOURCE_KIND" "\$RESOURCE_NAME" \
                                        -n "${env.QA_NAMESPACE}" > /dev/null 2>&1; then
                                        echo "ERROR: No existe \$RESOURCE_LABEL requerido: \$RESOURCE_NAME"
                                        exit 1
                                    fi
                                    if [ "\$RESOURCE_KIND" = 'pvc' ]; then
                                        PVC_PHASE=\$(oc get pvc "\$RESOURCE_NAME" \
                                            -n "${env.QA_NAMESPACE}" \
                                            -o jsonpath='{.status.phase}')
                                        if [ "\$PVC_PHASE" != 'Bound' ]; then
                                            echo "ERROR: El PVC '\$RESOURCE_NAME' no está Bound. Estado: \$PVC_PHASE"
                                            exit 1
                                        fi
                                    fi
                                    echo "\$RESOURCE_LABEL disponible: \$RESOURCE_NAME"
                                done
                                IFS="\$PREVIOUS_IFS"
                            }
                            validate_required_resources pvc PVC "${env.REQUIRED_PVCS}"
                            validate_required_resources configmap ConfigMap "${env.REQUIRED_CONFIGMAPS}"
                            validate_required_resources secret Secret "${env.REQUIRED_SECRETS}"
                            # =============================================================
                            # 7. VALIDACIÓN DE PERMISOS DEL USUARIO DE JENKINS
                            # =============================================================
                            require_permission() {
                                VERB="\$1"
                                RESOURCE="\$2"
                                PERMISSION=\$(oc auth can-i "\$VERB" "\$RESOURCE" \
                                    -n "${env.QA_NAMESPACE}" 2>/dev/null || true)
                                if [ "\$PERMISSION" != 'yes' ]; then
                                    echo "ERROR: Jenkins no tiene permiso para '\$VERB \$RESOURCE' en '${env.QA_NAMESPACE}'."
                                    exit 1
                                fi
                            }
                            for RESOURCE in serviceaccounts secrets persistentvolumeclaims configmaps; do
                                require_permission get "\$RESOURCE"
                            done
                            for RESOURCE in deployments.apps services; do
                                for VERB in get create patch; do
                                    require_permission "\$VERB" "\$RESOURCE"
                                done
                            done
                            if [ "${env.REQUIRE_ROUTE}" = 'true' ]; then
                                for VERB in get create patch; do
                                    require_permission "\$VERB" routes.route.openshift.io
                                done
                            fi
                            if [ "${env.REQUIRE_HPA}" = 'true' ]; then
                                for VERB in get create patch; do
                                    require_permission "\$VERB" horizontalpodautoscalers.autoscaling
                                done
                            fi
                            # =============================================================
                            # 8. PREPARACIÓN DEL MANIFIESTO QA
                            # =============================================================
                            # Contrato del pipeline: un único contenedor y un único campo image:.
                            IMAGE_FIELD_COUNT=\$(
                                awk '\$1 == "image:" { count++ } END { print count + 0 }' \
                                    "${manifestFile}"
                            )
                            if [ "\$IMAGE_FIELD_COUNT" -ne 1 ]; then
                                echo 'ERROR: Se esperaba exactamente un campo image en el manifiesto.'
                                echo "       Campos encontrados: \$IMAGE_FIELD_COUNT"
                                exit 1
                            fi
                            sed -E \
                                "s|^([[:space:]]*image:[[:space:]]*).*\$|\\1${env.QA_IMAGE_DIGEST_REF}|" \
                                "${manifestFile}" \
                                > "${renderedManifest}"
                            # =============================================================
                            # 9. VALIDACIÓN DE REQUESTS Y LIMITS DEL DEPLOYMENT
                            # =============================================================
                            CONTAINER_RESOURCES=\$(
                                oc create \
                                    --dry-run=client \
                                    -f "${renderedManifest}" \
                                    -o jsonpath='{range .spec.template.spec.containers[*]}{.name}{"|"}{.resources.requests.cpu}{"|"}{.resources.requests.memory}{"|"}{.resources.limits.cpu}{"|"}{.resources.limits.memory}{"\\n"}{end}'
                            )
                            if [ -z "\$CONTAINER_RESOURCES" ]; then
                                echo 'ERROR: No se encontraron contenedores de un Deployment en el manifiesto.'
                                exit 1
                            fi
                            if ! printf '%s\n' "\$CONTAINER_RESOURCES" | \
                                awk -F'|' 'NF != 5 || \$1 == "" || \$2 == "" || \$3 == "" || \$4 == "" || \$5 == "" { exit 1 }'; then
                                echo 'ERROR: El contenedor no define requests/limits completos de CPU y memoria.'
                                exit 1
                            fi
                            echo "Recursos del contenedor: \$CONTAINER_RESOURCES"
                            # =============================================================
                            # 10. VALIDACIÓN DEL MANIFIESTO DEL LADO DEL SERVIDOR
                            # =============================================================
                            oc apply \
                                --dry-run=server \
                                -f "${renderedManifest}" \
                                -n "${env.QA_NAMESPACE}"
                            echo 'Prerrequisitos técnicos de QA validados correctamente.'
                        """
                    }

                    echo 'Gate QA aprobado mediante la configuración declarativa y las evidencias del archivo properties.'
                }
            }
        }

        // =========================================================================
        // STAGE 7: PROMOVER IMAGEN Y DESPLEGAR EN QA
        // =========================================================================
        stage('7. Promote Image & Deploy QA') {
            steps {
                script {
                    def renderedManifest = "${env.WORKSPACE}/D02-sicatel-authentication-api-openshift-qa.yml"

                    withCredentials([
                        usernamePassword(
                            credentialsId   : "${env.QUAY_CREDENTIALS}",
                            usernameVariable: 'QUAY_USER',
                            passwordVariable: 'QUAY_PASSWORD'
                        ),
                        usernamePassword(
                            credentialsId   : "${env.JENKINS_OC_CREDS}",
                            usernameVariable: 'OC_USER',
                            passwordVariable: 'OC_PASSWORD'
                        )
                    ]) {
                        sh """
                            set +x
                            # =============================================================
                            # 1. AUTENTICACIÓN EN OPENSHIFT QA
                            # =============================================================
                            oc login \
                                --server="${env.OPENSHIFT_API}" \
                                --username="\$OC_USER" \
                                --password="\$OC_PASSWORD" \
                                --insecure-skip-tls-verify=false
                            echo "Autenticación correcta como: \$(oc whoami)"
                            # =============================================================
                            # 2. VALIDACIÓN DEL NAMESPACE QA
                            # =============================================================
                            if ! oc get project "${env.QA_NAMESPACE}" > /dev/null 2>&1; then
                                echo "ERROR: El namespace QA '${env.QA_NAMESPACE}' no existe."
                                echo '       El pipeline QA no crea namespaces automáticamente.'
                                exit 1
                            fi
                            oc project "${env.QA_NAMESPACE}"
                            # =============================================================
                            # 3. PREPARACIÓN DE LA AUTENTICACIÓN DEL REGISTRY
                            # =============================================================
                            QUAY_AUTH_DIR=\$(mktemp -d)
                            QUAY_AUTH_FILE="\${QUAY_AUTH_DIR}/config.json"
                            cleanup_qa_files() {
                                rm -rf "\$QUAY_AUTH_DIR"
                                rm -f "${renderedManifest}"
                            }
                            trap cleanup_qa_files EXIT
                            oc registry login \
                                --registry="${env.DEV_QUAY_DOMAIN}" \
                                --auth-basic="\$QUAY_USER:\$QUAY_PASSWORD" \
                                --to="\$QUAY_AUTH_FILE"
                            if [ "${env.QA_QUAY_DOMAIN}" != "${env.DEV_QUAY_DOMAIN}" ]; then
                                oc registry login \
                                    --registry="${env.QA_QUAY_DOMAIN}" \
                                    --auth-basic="\$QUAY_USER:\$QUAY_PASSWORD" \
                                    --to="\$QUAY_AUTH_FILE"
                            fi
                            # =============================================================
                            # 4. PROTECCIÓN DEL TAG QA
                            # =============================================================
                            TARGET_AUTH_TOKEN=\$(curl ${env.CURL_OPTIONS} \
                                -u "\$QUAY_USER:\$QUAY_PASSWORD" \
                                "https://${env.QA_QUAY_DOMAIN}/v2/auth?service=${env.QA_QUAY_DOMAIN}&scope=repository:${env.QA_QUAY_ORGANIZATION}/${env.QA_QUAY_REPOSITORY}:pull,push" \
                                | grep -o '"token":"[^"]*' \
                                | cut -d'"' -f4)
                            if [ -z "\$TARGET_AUTH_TOKEN" ]; then
                                echo 'ERROR: No se pudo obtener el token del repositorio QA.'
                                exit 1
                            fi
                            CURRENT_TARGET_DIGEST=\$(curl ${env.CURL_OPTIONS} -I \
                                -H "Authorization: Bearer \$TARGET_AUTH_TOKEN" \
                                -H 'Accept: application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.manifest.v1+json' \
                                "https://${env.QA_QUAY_DOMAIN}/v2/${env.QA_QUAY_ORGANIZATION}/${env.QA_QUAY_REPOSITORY}/manifests/${env.QA_IMAGE_TAG}" \
                                | grep -i 'docker-content-digest' \
                                | head -n 1 \
                                | awk '{print \$2}' \
                                | tr -d '\\r\\n')
                            if [ -n "\$CURRENT_TARGET_DIGEST" ] && \
                               [ "\$CURRENT_TARGET_DIGEST" != "${env.DEV_IMAGE_DIGEST}" ]; then
                                echo "ERROR: El tag QA '${env.QA_IMAGE_TAG}' ya existe con otro digest."
                                echo "       Digest existente: \$CURRENT_TARGET_DIGEST"
                                echo "       Digest solicitado: ${env.DEV_IMAGE_DIGEST}"
                                echo '       No se permite sobrescribir un tag QA inmutable.'
                                exit 1
                            fi
                            # =============================================================
                            # 5. PROMOCIÓN Y VALIDACIÓN DEL DIGEST
                            # =============================================================
                            if [ "\$CURRENT_TARGET_DIGEST" = "${env.DEV_IMAGE_DIGEST}" ]; then
                                echo "El tag QA '${env.QA_IMAGE_TAG}' ya apunta al digest esperado."
                                echo 'La promoción es idempotente; no se ejecutará nuevamente.'
                            else
                                echo 'Promoviendo imagen sin ejecutar un nuevo build:'
                                echo "Origen : ${env.DEV_IMAGE_DIGEST_REF}"
                                echo "Destino: ${env.QA_IMAGE_TAG_REF}"
                                oc image mirror \
                                    "${env.DEV_IMAGE_DIGEST_REF}" \
                                    "${env.QA_IMAGE_TAG_REF}" \
                                    --registry-config="\$QUAY_AUTH_FILE" \
                                    --keep-manifest-list=true
                            fi
                            TARGET_DIGEST=\$(curl ${env.CURL_OPTIONS} -I \
                                -H "Authorization: Bearer \$TARGET_AUTH_TOKEN" \
                                -H 'Accept: application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.manifest.v1+json' \
                                "https://${env.QA_QUAY_DOMAIN}/v2/${env.QA_QUAY_ORGANIZATION}/${env.QA_QUAY_REPOSITORY}/manifests/${env.QA_IMAGE_TAG}" \
                                | grep -i 'docker-content-digest' \
                                | head -n 1 \
                                | awk '{print \$2}' \
                                | tr -d '\\r\\n')
                            if [ -z "\$TARGET_DIGEST" ]; then
                                echo 'ERROR: No se pudo obtener el digest del tag QA promovido.'
                                exit 1
                            fi
                            if [ "\$TARGET_DIGEST" != "${env.DEV_IMAGE_DIGEST}" ]; then
                                echo 'ERROR: La promoción modificó el digest de la imagen.'
                                echo "       Digest DEV: ${env.DEV_IMAGE_DIGEST}"
                                echo "       Digest QA : \$TARGET_DIGEST"
                                exit 1
                            fi
                            echo 'Promoción completada con el mismo digest.'
                            # =============================================================
                            # 6. VERIFICACIÓN DEL MANIFIESTO PREVALIDADO
                            # =============================================================
                            if [ ! -s "${renderedManifest}" ]; then
                                echo "ERROR: No existe el manifiesto QA prevalidado: ${renderedManifest}"
                                exit 1
                            fi
                            RENDERED_IMAGE=\$(
                                awk '\$1 == "image:" { print \$2; exit }' \
                                    "${renderedManifest}"
                            )
                            if [ "\$RENDERED_IMAGE" != "${env.QA_IMAGE_DIGEST_REF}" ]; then
                                echo 'ERROR: El manifiesto QA no contiene la imagen esperada.'
                                echo "       Esperada: ${env.QA_IMAGE_DIGEST_REF}"
                                echo "       Obtenida: \$RENDERED_IMAGE"
                                exit 1
                            fi
                            # =============================================================
                            # 7. REVALIDACIÓN DEL LADO DEL SERVIDOR
                            # =============================================================
                            oc apply \
                                --dry-run=server \
                                -f "${renderedManifest}" \
                                -n "${env.QA_NAMESPACE}"
                            # =============================================================
                            # 8. CREACIÓN O ACTUALIZACIÓN DE LOS RECURSOS QA
                            # =============================================================
                            oc apply \
                                -f "${renderedManifest}" \
                                -n "${env.QA_NAMESPACE}"
                            echo 'Promoción y aplicación del manifiesto QA completadas.'
                        """
                    }
                }
            }
        }

        // =========================================================================
        // STAGE 8: VALIDAR DESPLIEGUE QA
        // =========================================================================
        stage('8. Validate QA') {
            steps {
                script {
                    withCredentials([usernamePassword(
                        credentialsId   : "${env.JENKINS_OC_CREDS}",
                        usernameVariable: 'OC_USER',
                        passwordVariable: 'OC_PASSWORD'
                    )]) {
                        sh """
                            set +x
                            oc login \
                                --server="${env.OPENSHIFT_API}" \
                                --username="\$OC_USER" \
                                --password="\$OC_PASSWORD" \
                                --insecure-skip-tls-verify=false
                            oc project "${env.QA_NAMESPACE}"
                            # =============================================================
                            # 1. VALIDACIÓN DEL ROLLOUT
                            # =============================================================
                            oc rollout status deployment/"${env.EXPECTED_DEPLOYMENT}" \
                                -n "${env.QA_NAMESPACE}" \
                                --timeout="${env.ROLLOUT_TIMEOUT}" \
                                --v=6
                            # =============================================================
                            # 2. VALIDACIÓN DE LA IMAGEN DESPLEGADA
                            # =============================================================
                            # Stage 6 ya validó el contrato de un solo contenedor.
                            DEPLOYED_IMAGE=\$(
                                oc get deployment "${env.EXPECTED_DEPLOYMENT}" \
                                    -n "${env.QA_NAMESPACE}" \
                                    -o jsonpath='{.spec.template.spec.containers[0].image}'
                            )
                            if [ "\$DEPLOYED_IMAGE" != "${env.QA_IMAGE_DIGEST_REF}" ]; then
                                echo 'ERROR: El Deployment QA no contiene el digest esperado.'
                                echo "       Esperada  : ${env.QA_IMAGE_DIGEST_REF}"
                                echo "       Desplegada: \$DEPLOYED_IMAGE"
                                exit 1
                            fi
                            # =============================================================
                            # 3. VALIDACIÓN DE SERVICE
                            # =============================================================
                            oc get service "${env.EXPECTED_SERVICE}" \
                                -n "${env.QA_NAMESPACE}"
                            # =============================================================
                            # 4. VALIDACIÓN DE PERSISTENT VOLUME CLAIMS
                            # =============================================================
                            OLD_IFS="\$IFS"
                            IFS=','
                            for REQUIRED_PVC in ${env.REQUIRED_PVCS}; do
                                REQUIRED_PVC=\$(echo "\$REQUIRED_PVC" | xargs)
                                [ -z "\$REQUIRED_PVC" ] && continue
                                if ! oc get pvc "\$REQUIRED_PVC" \
                                    -n "${env.QA_NAMESPACE}" > /dev/null 2>&1; then
                                    echo "ERROR: No existe el PVC requerido: \$REQUIRED_PVC"
                                    exit 1
                                fi
                            done
                            IFS="\$OLD_IFS"
                            # =============================================================
                            # 5. VALIDACIÓN DE ROUTE
                            # =============================================================
                            ROUTE_NAME=""
                            ROUTE_HOST=""
                            if [ "${env.REQUIRE_ROUTE}" = 'true' ]; then
                                ROUTE_NAME=\$(
                                    oc get route \
                                        -n "${env.QA_NAMESPACE}" \
                                        -o jsonpath='{range .items[?(@.spec.to.name=="${env.EXPECTED_SERVICE}")]}{.metadata.name}{"\\n"}{end}' | \
                                    head -n 1
                                )
                                if [ -z "\$ROUTE_NAME" ]; then
                                    echo "ERROR: No se encontró una Route asociada al Service '${env.EXPECTED_SERVICE}'."
                                    exit 1
                                fi
                                ROUTE_HOST=\$(
                                    oc get route "\$ROUTE_NAME" \
                                        -n "${env.QA_NAMESPACE}" \
                                        -o jsonpath='{.spec.host}'
                                )
                                oc get route "\$ROUTE_NAME" \
                                    -n "${env.QA_NAMESPACE}"
                            fi
                            # =============================================================
                            # 6. VALIDACIÓN DE HPA
                            # =============================================================
                            if [ "${env.REQUIRE_HPA}" = 'true' ]; then
                                oc get hpa "${env.EXPECTED_DEPLOYMENT}" \
                                    -n "${env.QA_NAMESPACE}"
                            fi
                            # =============================================================
                            # 7. VALIDACIÓN DEL ENDPOINT DE SALUD
                            # =============================================================
                            if [ "${env.HEALTH_ENABLED}" = 'true' ]; then
                                if [ -z "\$ROUTE_HOST" ]; then
                                    echo 'ERROR: HEALTH_ENABLED=true requiere una Route disponible.'
                                    exit 1
                                fi
                                HEALTH_STATUS=\$(curl ${env.CURL_OPTIONS} \
                                    -o /dev/null \
                                    -w '%{http_code}' \
                                    "https://\${ROUTE_HOST}${env.HEALTH_PATH}")
                                if [ "\$HEALTH_STATUS" != "${env.EXPECTED_HTTP_STATUS}" ]; then
                                    echo 'ERROR: El endpoint de salud no devolvió el estado esperado.'
                                    echo "       Esperado: ${env.EXPECTED_HTTP_STATUS}"
                                    echo "       Obtenido: \$HEALTH_STATUS"
                                    exit 1
                                fi
                                echo "Health check aprobado: HTTP \$HEALTH_STATUS"
                            fi
                            # Pruebas funcionales e integración:
                            # Pendiente integrar el ejecutor y los casos definidos por la aplicación.
                            # Política de despliegue RHACS:
                            # Pendiente integrar la validación cuando RHACS esté habilitado.
                            echo 'Validación QA completada correctamente.'
                            echo "Deployment: ${env.EXPECTED_DEPLOYMENT}"
                            echo "Imagen    : \$DEPLOYED_IMAGE"
                        """
                    }
                }
            }
        }

        // =========================================================================
        // STAGE 9: REGISTRO DE RELEASE QA Y DEPURACIÓN DE TAGS EN QUAY
        // =========================================================================
        stage('9. Record QA Validated Release & Cleanup Quay Tags') {
            steps {
                script {
                    def evidenceFile = "qa-validated-release-${env.BUILD_NUMBER}.properties"

                    writeFile(
                        file: evidenceFile,
                        text: """APP_NAME=${env.APP_NAME}
DEPLOY_ENV=${env.DEPLOY_ENV}
QA_NAMESPACE=${env.QA_NAMESPACE}
DEV_BUILD_NUMBER=${env.DEV_BUILD_NUMBER}
RELEASE_GIT_COMMIT=${env.RELEASE_GIT_COMMIT}
DEV_IMAGE_TAG=${env.DEV_IMAGE_TAG}
DEV_IMAGE_DIGEST=${env.DEV_IMAGE_DIGEST}
QA_IMAGE_TAG=${env.QA_IMAGE_TAG}
QA_IMAGE_REFERENCE=${env.QA_IMAGE_DIGEST_REF}
SONAR_APPROVED=${env.SONAR_APPROVED}
VERACODE_APPROVED=${env.VERACODE_APPROVED}
SBOM_AVAILABLE=${env.SBOM_AVAILABLE}
JENKINS_BUILD_NUMBER=${env.BUILD_NUMBER}
JENKINS_BUILD_URL=${env.BUILD_URL ?: ''}
"""
                    )

                    archiveArtifacts(
                        artifacts: evidenceFile,
                        fingerprint: true,
                        allowEmptyArchive: false
                    )

                    echo "Evidencia QA registrada: ${evidenceFile}"

                    withCredentials([usernamePassword(
                        credentialsId   : "${env.QUAY_CREDENTIALS}",
                        usernameVariable: 'QUAY_USER',
                        passwordVariable: 'QUAY_PASSWORD'
                    )]) {
                        sh """
                            set +x
                            echo '=========================================================================='
                            echo '         DEPURACIÓN CONTROLADA DE TAGS DEL REPOSITORIO QA                 '
                            echo '=========================================================================='
                            echo "Repositorio    : ${env.QA_QUAY_ORGANIZATION}/${env.QA_QUAY_REPOSITORY}"
                            echo "Tag protegido  : ${env.QA_IMAGE_TAG}"
                            echo "Prefijo QA     : ${env.QA_TAG_PREFIX}"
                            echo "Modo            : ${params.CLEANUP_MODE}"
                            echo "Tags a conservar: ${env.QA_TAGS_TO_KEEP}"
                            echo '=========================================================================='
                            AUTH_TOKEN=\$(curl ${env.CURL_OPTIONS} \
                                -u "\$QUAY_USER:\$QUAY_PASSWORD" \
                                "https://${env.QA_QUAY_DOMAIN}/v2/auth?service=${env.QA_QUAY_DOMAIN}&scope=repository:${env.QA_QUAY_ORGANIZATION}/${env.QA_QUAY_REPOSITORY}:pull,push" \
                                | grep -o '"token":"[^"]*' \
                                | cut -d'"' -f4)
                            if [ -z "\$AUTH_TOKEN" ]; then
                                echo 'ERROR: No se pudo obtener el token de autenticación de Quay.'
                                exit 1
                            fi
                            ALL_TAGS=\$(curl ${env.CURL_OPTIONS} \
                                -H "Authorization: Bearer \$AUTH_TOKEN" \
                                "https://${env.QA_QUAY_DOMAIN}/v2/${env.QA_QUAY_ORGANIZATION}/${env.QA_QUAY_REPOSITORY}/tags/list" \
                                | grep -o '"tags":\\[[^]]*\\]' \
                                | sed 's/"tags":\\[//;s/\\]//;s/"//g;s/,/ /g')
                            if [ -z "\$ALL_TAGS" ]; then
                                echo 'No se encontraron tags en el repositorio QA.'
                                exit 0
                            fi
                            TEMP_TAG_DATES=\$(mktemp)
                            for TAG in \$ALL_TAGS; do
                                [ -z "\$TAG" ] && continue
                                [ "\$TAG" = 'latest' ] && continue
                                case "\$TAG" in
                                    "${env.QA_TAG_PREFIX}"*)
                                        ;;
                                    *)
                                        echo "  -> Tag fuera de la política QA; se conserva: \$TAG"
                                        continue
                                        ;;
                                esac
                                MANIFEST=\$(curl ${env.CURL_OPTIONS} \
                                    -H "Authorization: Bearer \$AUTH_TOKEN" \
                                    -H 'Accept: application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.manifest.v1+json' \
                                    "https://${env.QA_QUAY_DOMAIN}/v2/${env.QA_QUAY_ORGANIZATION}/${env.QA_QUAY_REPOSITORY}/manifests/\${TAG}")
                                CONFIG_DIGEST=\$(echo "\$MANIFEST" \
                                    | grep -o '"digest":"sha256:[^"]*"' \
                                    | head -n 1 \
                                    | cut -d'"' -f4)
                                CREATED_AT=''
                                if [ -n "\$CONFIG_DIGEST" ]; then
                                    CONFIG_JSON=\$(curl ${env.CURL_OPTIONS} -L \
                                        -H "Authorization: Bearer \$AUTH_TOKEN" \
                                        "https://${env.QA_QUAY_DOMAIN}/v2/${env.QA_QUAY_ORGANIZATION}/${env.QA_QUAY_REPOSITORY}/blobs/\${CONFIG_DIGEST}")
                                    CREATED_AT=\$(echo "\$CONFIG_JSON" \
                                        | grep -o '"created":"[^"]*"' \
                                        | head -n 1 \
                                        | cut -d'"' -f4)
                                fi
                                [ -z "\$CREATED_AT" ] && CREATED_AT='1970-01-01T00:00:00Z'
                                TAG_MANIFEST_DIGEST=\$(curl ${env.CURL_OPTIONS} -I \
                                    -H "Authorization: Bearer \$AUTH_TOKEN" \
                                    -H 'Accept: application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.manifest.v1+json' \
                                    "https://${env.QA_QUAY_DOMAIN}/v2/${env.QA_QUAY_ORGANIZATION}/${env.QA_QUAY_REPOSITORY}/manifests/\${TAG}" \
                                    | grep -i 'docker-content-digest' \
                                    | head -n 1 \
                                    | awk '{print \$2}' \
                                    | tr -d '\\r\\n')
                                echo "\${CREATED_AT} \${TAG} \${TAG_MANIFEST_DIGEST}" >> "\$TEMP_TAG_DATES"
                            done
                            SORTED_TAGS=\$(sort -r "\$TEMP_TAG_DATES" | grep -v '^\$' || true)
                            rm -f "\$TEMP_TAG_DATES"
                            COUNT=0
                            KEEP_LIMIT=${env.QA_TAGS_TO_KEEP}
                            TAGS_TO_DELETE=''
                            echo '--------------------------------------------------------------------------'
                            echo 'Tags evaluados de más reciente a más antiguo:'
                            echo '--------------------------------------------------------------------------'
                            while read -r created_date name manifest_digest; do
                                [ -z "\$name" ] && continue
                                COUNT=\$((COUNT + 1))
                                DATE_DISPLAY=\$(echo "\$created_date" | sed 's/T/ /;s/\\..*//;s/Z//')
                                if [ "\$manifest_digest" = "${env.DEV_IMAGE_DIGEST}" ]; then
                                    printf '  ✔ [DIGEST ACTIVO] Tag: %-20s | Creado: %s\\n' "\$name" "\$DATE_DISPLAY"
                                elif [ "\$name" = "${env.QA_IMAGE_TAG}" ]; then
                                    printf '  ✔ [PROTEGIDO] Tag: %-20s | Creado: %s\\n' "\$name" "\$DATE_DISPLAY"
                                elif [ "\$COUNT" -le "\$KEEP_LIMIT" ]; then
                                    printf '  ✔ [CONSERVAR] Tag: %-20s | Creado: %s\\n' "\$name" "\$DATE_DISPLAY"
                                else
                                    printf '  ✖ [A BORRAR]  Tag: %-20s | Creado: %s\\n' "\$name" "\$DATE_DISPLAY"
                                    TAGS_TO_DELETE="\$TAGS_TO_DELETE \$name"
                                fi
                            done <<< "\$SORTED_TAGS"
                            TOTAL_DELETE=\$(echo "\$TAGS_TO_DELETE" | awk '{print NF}')
                            echo '--------------------------------------------------------------------------'
                            echo "Tags evaluados          : \$COUNT"
                            echo "Tags marcados para borrar: \$TOTAL_DELETE"
                            echo '--------------------------------------------------------------------------'
                            if [ "\$TOTAL_DELETE" -eq 0 ]; then
                                echo 'No hay tags antiguos para eliminar.'
                            elif [ "${params.CLEANUP_MODE}" = 'LIST_ONLY' ]; then
                                echo "MODO LIST_ONLY: no se eliminó ningún tag."
                            else
                                for TAG in \$TAGS_TO_DELETE; do
                                    echo -n "Eliminando tag QA antiguo: \$TAG ... "
                                    DIGEST=\$(curl ${env.CURL_OPTIONS} -I \
                                        -H "Authorization: Bearer \$AUTH_TOKEN" \
                                        -H 'Accept: application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.manifest.v1+json' \
                                        "https://${env.QA_QUAY_DOMAIN}/v2/${env.QA_QUAY_ORGANIZATION}/${env.QA_QUAY_REPOSITORY}/manifests/\${TAG}" \
                                        | grep -i 'docker-content-digest' \
                                        | head -n 1 \
                                        | awk '{print \$2}' \
                                        | tr -d '\\r\\n')
                                    HTTP_CODE=''
                                    if [ -n "\$DIGEST" ]; then
                                        HTTP_CODE=\$(curl ${env.CURL_OPTIONS} \
                                            -o /dev/null \
                                            -w '%{http_code}' \
                                            -X DELETE \
                                            -H "Authorization: Bearer \$AUTH_TOKEN" \
                                            "https://${env.QA_QUAY_DOMAIN}/v2/${env.QA_QUAY_ORGANIZATION}/${env.QA_QUAY_REPOSITORY}/manifests/\${DIGEST}")
                                    fi
                                    if [ "\$HTTP_CODE" != '202' ] && \
                                       [ "\$HTTP_CODE" != '200' ] && \
                                       [ "\$HTTP_CODE" != '204' ]; then
                                        HTTP_CODE=\$(curl ${env.CURL_OPTIONS} \
                                            -o /dev/null \
                                            -w '%{http_code}' \
                                            -X DELETE \
                                            -u "\$QUAY_USER:\$QUAY_PASSWORD" \
                                            "https://${env.QA_QUAY_DOMAIN}/api/v1/repository/${env.QA_QUAY_ORGANIZATION}/${env.QA_QUAY_REPOSITORY}/tag/\$TAG")
                                    fi
                                    case "\$HTTP_CODE" in
                                        202|200|204)
                                            echo "[HTTP \$HTTP_CODE] -> Eliminado correctamente."
                                            ;;
                                        403)
                                            echo '[HTTP 403] -> El Robot Account no tiene permisos suficientes.'
                                            ;;
                                        *)
                                            echo "[HTTP \$HTTP_CODE] -> No fue posible confirmar el borrado."
                                            ;;
                                    esac
                                done
                            fi
                            echo 'Depuración controlada del repositorio QA finalizada.'
                        """
                    }
                }
            }
        }
    }

    // =========================================================================
    // ACCIONES POST-EJECUCIÓN
    // =========================================================================
    post {
        always {
            deleteDir()
        }
        success {
            echo "Pipeline QA de ${APP_NAME} finalizado correctamente."
        }
        failure {
            echo "Pipeline QA de ${APP_NAME} fallido. Revisa los logs de error."
        }
        aborted {
            echo "Pipeline QA de ${APP_NAME} cancelado o no aprobado."
        }
    }
}
