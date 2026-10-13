# Deploy en Azure Container Apps

Backend Spring Boot 4 (Java 21) → imagen Docker en **Azure Container Registry** →
**Azure Container Apps** (escala a cero) + **PostgreSQL Flexible Server** +
**Application Insights**. CI/CD con GitHub Actions.

> Cuenta Azure for Students (100 USD): todo está dimensionado al mínimo. Los costos
> son aproximados; revisalos en *Cost Management* cada tanto.

| Recurso | SKU | Costo aproximado |
|---|---|---|
| Container App | 0.5 vCPU / 1 GiB, 0–1 réplicas | Gratis dentro de la cuota mensual de consumo si escala a cero (≈ 100 h activas) |
| Container Registry | Basic | ≈ 5 USD/mes (el costo fijo principal) |
| PostgreSQL Flexible | Burstable B1ms, 32 GB | 750 h/mes gratis los primeros 12 meses si tu suscripción lo incluye (verificalo en el portal); si no, ≈ 13–15 USD/mes. Apagalo cuando no lo uses. |
| Log Analytics + App Insights | límite diario 0.1 GB | Centavos |

**¿Por qué 0.5 vCPU / 1 GiB y no 0.25 / 0.5 GiB?** Spring Boot con la JVM, Hibernate y
el agente de Application Insights usa más de 512 MB al arrancar: con 0.5 GiB el
contenedor se cae por falta de memoria, y con 0.25 vCPU tarda tanto en iniciar que el
arranque desde cero (escala a cero) se vuelve inusable. 0.5 / 1 GiB es la combinación
válida siguiente y, con `min-replicas 0`, solo se cobra mientras atiende requests.

## 0. Probar la imagen en tu máquina (opcional, recomendado)

Es la misma imagen que corre en Azure. Contra un Postgres local:

```bash
docker build -t printops-backend:local .
grype printops-backend:local                     # objetivo: 0 critical, 0 high
docker run -d --name printops-test -p 8091:8083 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/printops \
  -e DB_PASSWORD=<password local> -e JWT_SECRET="$(openssl rand -base64 32)" \
  printops-backend:local
curl -i http://localhost:8091/health             # 200 {"status":"UP"}
docker stop printops-test && docker rm printops-test
```

Sin `APPLICATIONINSIGHTS_CONNECTION_STRING` el agente avisa *"No connection string
provided"* y la app arranca igual, sin telemetría.

## 1. Setup inicial (una sola vez)

Requisitos: [Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli), Docker y Python 3.

```bash
az login
az extension add --name containerapp --upgrade
az extension add --name application-insights --upgrade
az provider register -n Microsoft.App --wait
az provider register -n Microsoft.OperationalInsights --wait
az provider register -n Microsoft.ContainerRegistry --wait
az provider register -n Microsoft.DBforPostgreSQL --wait

# Ajustá los nombres. ACR_NAME y PG_SERVER tienen que ser únicos en todo Azure.
# Azure for Students limita las regiones: si una falla, probá otra (eastus2, brazilsouth…).
export LOCATION=eastus
export AZURE_RESOURCE_GROUP=rg-printops
export ACR_NAME=acrprintops$RANDOM          # solo letras y números
export PG_SERVER=pg-printops-$RANDOM
export CONTAINERAPP_ENV=cae-printops
export CONTAINERAPP_NAME=printops-backend

# Grupo de recursos
az group create -n $AZURE_RESOURCE_GROUP -l $LOCATION

# Container Registry (Basic)
az acr create -n $ACR_NAME -g $AZURE_RESOURCE_GROUP --sku Basic

# Log Analytics con límite diario de 0.1 GB
az monitor log-analytics workspace create -g $AZURE_RESOURCE_GROUP -n law-printops -l $LOCATION
az monitor log-analytics workspace update -g $AZURE_RESOURCE_GROUP -n law-printops --quota 0.1

# Application Insights (basado en el workspace anterior)
LAW_ID=$(az monitor log-analytics workspace show -g $AZURE_RESOURCE_GROUP -n law-printops --query id -o tsv)
az monitor app-insights component create --app appi-printops -g $AZURE_RESOURCE_GROUP -l $LOCATION --workspace $LAW_ID

# PostgreSQL Flexible Server B1ms. 0.0.0.0 = solo servicios de Azure pueden conectarse.
export DB_USERNAME=printopsadmin
read -rs -p "Password de la base: " DB_PASSWORD; echo; export DB_PASSWORD
az postgres flexible-server create -g $AZURE_RESOURCE_GROUP -n $PG_SERVER -l $LOCATION \
  --tier Burstable --sku-name Standard_B1ms --storage-size 32 --version 16 \
  --admin-user $DB_USERNAME --admin-password "$DB_PASSWORD" --public-access 0.0.0.0 --yes
az postgres flexible-server db create -g $AZURE_RESOURCE_GROUP -s $PG_SERVER -d printops

# Entorno de Container Apps, con los logs en el mismo workspace (sujetos al límite diario)
az containerapp env create -n $CONTAINERAPP_ENV -g $AZURE_RESOURCE_GROUP -l $LOCATION \
  --logs-workspace-id $(az monitor log-analytics workspace show -g $AZURE_RESOURCE_GROUP -n law-printops --query customerId -o tsv) \
  --logs-workspace-key $(az monitor log-analytics workspace get-shared-keys -g $AZURE_RESOURCE_GROUP -n law-printops --query primarySharedKey -o tsv)

# Service principal para GitHub Actions, con permisos SOLO sobre este grupo de recursos.
# Contributor alcanza para hacer push al ACR y actualizar el Container App.
az ad sp create-for-rbac --name sp-printops-github --role Contributor \
  --scopes $(az group show -n $AZURE_RESOURCE_GROUP --query id -o tsv) --json-auth
# → copiá el JSON que imprime: es el secret AZURE_CREDENTIALS. No lo guardes en el repo.
```

## 2. Primer deploy (crea el Container App con sus secretos)

El CI solo **actualiza** la imagen; el Container App se crea una vez desde tu máquina,
porque ahí se cargan los secretos de la app (no viven en GitHub ni en la imagen).

```bash
export JWT_SECRET=$(openssl rand -base64 32)
export DB_URL="jdbc:postgresql://$PG_SERVER.postgres.database.azure.com:5432/printops?sslmode=require"
export APPLICATIONINSIGHTS_CONNECTION_STRING=$(az monitor app-insights component show \
  --app appi-printops -g $AZURE_RESOURCE_GROUP --query connectionString -o tsv)
# export RESEND_API_KEY=...   # opcional (invitaciones y reset de contraseña por email)

python scripts/azure.py build --tag v0.1.0
python scripts/azure.py push --tag v0.1.0
python scripts/azure.py deploy --tag v0.1.0      # muestra la URL pública al final
```

Otros comandos: `list` (apps e imágenes), `scan` (Grype sobre la imagen local),
`stop` (desactiva la revisión activa) y `delete` (borra el Container App). Los que
crean, modifican o borran recursos piden confirmación (y/n).

## 3. Secrets de GitHub

En el repo: **Settings → Secrets and variables → Actions → New repository secret**,
o con [GitHub CLI](https://cli.github.com/):

| Secret | Valor |
|---|---|
| `AZURE_CREDENTIALS` | JSON completo de `az ad sp create-for-rbac ... --json-auth` |
| `ACR_NAME` | nombre del registry, sin `.azurecr.io` |
| `AZURE_RESOURCE_GROUP` | `rg-printops` |
| `SNYK_TOKEN` | [app.snyk.io](https://app.snyk.io) → Account settings → Auth Token |

```bash
gh secret set AZURE_CREDENTIALS < azure-credentials.json   # o pegalo con: gh secret set AZURE_CREDENTIALS
gh secret set ACR_NAME --body "$ACR_NAME"
gh secret set AZURE_RESOURCE_GROUP --body "$AZURE_RESOURCE_GROUP"
gh secret set SNYK_TOKEN
```

### Cómo se versiona

- **push a `develop`** (si cambió código, Dockerfile o dependencias) → `printops-backend:dev-<sha corto>` en el ACR, **sin deploy**.
- **tag `v*`** → `printops-backend:<tag>` + `latest`, push y **deploy a producción**:
  ```bash
  git tag v0.2.0 && git push origin v0.2.0
  ```

## 4. Probar con Postman

URL base: `https://<fqdn>`. La obtenés con
`az containerapp show -n $CONTAINERAPP_NAME -g $AZURE_RESOURCE_GROUP --query properties.configuration.ingress.fqdn -o tsv`.
Con escala a cero, el **primer request tarda 30–60 s** (arranque en frío).

**Forma rápida:** en Postman, *Import* → `postman/PrintOps.postman_collection.json`.
Abrí la colección → pestaña *Variables* → poné tu URL en `url` → guardá. Después
*Run collection*: corre los 5 requests en orden, guarda el JWT solo y valida cada respuesta.

**Paso a paso a mano:**

1. **Health** — `GET {{url}}/health` → `200` con `"status":"UP"` (sin token).
2. **Registro** — `POST {{url}}/api/auth/register`
   ```json
   { "email": "yo@ejemplo.com", "password": "Password123", "workspaceName": "Taller demo" }
   ```
   El usuario queda verificado y como MANAGER de un taller nuevo. Si el email ya
   existe responde `409`: pasá al login.
3. **Login** — `POST {{url}}/api/auth/login`
   ```json
   { "email": "yo@ejemplo.com", "password": "Password123", "deviceId": "postman", "rememberMe": false }
   ```
   La respuesta trae `accessToken` (JWT, 15 min) y `refreshToken`. Para guardarlo solo,
   en la pestaña *Scripts → Post-response* del request:
   `pm.collectionVariables.set("token", pm.response.json().accessToken);`
4. **Endpoint protegido** — `GET {{url}}/api/printers` con
   *Authorization → Bearer Token* = `{{token}}` → `200 []`. Sin token → `401`.

La telemetría aparece en Application Insights (*Live metrics*, *Transaction search*,
*Performance*) un par de minutos después de los requests. Cada request también deja
en el log una línea como `GET /api/printers -> 200 [PrinterController#getAllPrinters] en 111 ms`
(`az containerapp logs show -n $CONTAINERAPP_NAME -g $AZURE_RESOURCE_GROUP --follow`).

**App móvil:** en el repo del frontend (rama `azure`), completá `AZURE_API_URL` en
`src/constants/api.ts` con `https://<fqdn>`. Los builds de release la usan; en debug
sigue apuntando al backend local salvo que pongas `USE_AZURE_IN_DEBUG = true`.

## 5. Parar y borrar todo

```bash
python scripts/azure.py stop                                   # desactiva la revisión (deja de responder)
az postgres flexible-server stop -g $AZURE_RESOURCE_GROUP -n $PG_SERVER   # se reinicia sola a los 7 días

# Borrar TODO (app, registry, base, logs). No se puede deshacer.
az group delete -n $AZURE_RESOURCE_GROUP --yes --no-wait
az ad sp delete --id $(az ad sp list --display-name sp-printops-github --query "[0].appId" -o tsv)
```

## 6. Snyk, Grype y CodeQL

| | Snyk | Grype | CodeQL |
|---|---|---|---|
| Qué analiza | Dependencias (Gradle) y la imagen | La imagen ya construida (SO + jars) | El código fuente propio |
| Cómo | Compara con la base de vulnerabilidades de Snyk; sugiere versiones que las corrigen | Arma el inventario (SBOM) de la imagen y lo cruza con bases públicas (NVD, GitHub, Debian…) | Análisis estático: modela el flujo de datos para encontrar inyecciones, XSS, secretos, etc. |
| Dónde corre | CI (`ci.yml`, falla con severidad alta o crítica) | Local (`scripts/azure.py scan`) | CI (`codeql.yml`), resultados en *Security → Code scanning* |
| Detecta | CVE en librerías de terceros | CVE en paquetes del SO y librerías | Bugs de seguridad en *tu* código |

Snyk y Grype responden "¿uso algo con una vulnerabilidad conocida?"; CodeQL responde
"¿escribí código vulnerable?". Son complementarios.

## Limitaciones conocidas

- **Archivos subidos** (fotos de órdenes, PDFs de presupuestos) se guardan en el disco
  del contenedor, que se pierde con cada reinicio o escala a cero. Para persistirlos
  habría que montar Azure Files o usar Blob Storage (costo extra).
- **Scheduler de mantenimiento**: con `min-replicas 0` solo corre mientras la app está
  despierta.
- **CodeQL** es gratis en repos públicos; en repos privados requiere GitHub Advanced Security.
