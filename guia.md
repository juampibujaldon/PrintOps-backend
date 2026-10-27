grype printops-backend:v0.1.1
//Estas alertas son de herramientas Linux que vienen dentro de la imagen

python3 scripts/azure.py deploy --tag dev-7959e2f-r2

python3 scripts/azure.py

# 1. ¿Qué imagen tiene? Tiene que decir dev-<sha de tu commit>
python3 scripts/azure.py status
git log --oneline -1

# 2. ¿La revisión está bien?
az containerapp revision list -n printops-backend -g rg-printops \
  --query "[].{rev:name,estado:properties.provisioningState,imagen:properties.template.containers[0].image}" -o table
#   Provisioned → bien
#   Failed      → mal (la app queda caída)

# 3. ¿Arrancó Spring? Buscá "Started ... in X seconds"
python3 scripts/azure.py logs | grep -E "Started|ERROR|APPLICATION FAILED"

# 4. ¿Responde?
curl https://printops-backend.purplemeadow-6cc5e29a.northcentralus.azurecontainerapps.io/health
#   {"status":"UP"} → listo

# ---------- Apagar y levantar la app ----------
# También: `python3 scripts/azure.py stop` / `start` (opciones 11 y 10 del menú) hacen esto mismo.
# `revision deactivate` NO sirve: en modo Single Azure no deja desactivar la única
# revisión. Se apaga cortando el ingress: la URL deja de recibir
# tráfico, nadie puede despertarla y queda en 0 réplicas (sin costo de cómputo).
# Ojo: mientras esté apagada, el CI/CD puede desplegar igual, pero la app no responde.

# Apagar
az containerapp ingress disable -n printops-backend -g rg-printops

# Levantar (mismos valores que tenía: externo, puerto 8083, sin HTTP inseguro)
az containerapp ingress enable -n printops-backend -g rg-printops \
  --type external --target-port 8083 --transport auto --allow-insecure false

# Verificar el estado
az containerapp show -n printops-backend -g rg-printops --query properties.configuration.ingress.fqdn -o tsv
#   vacío             → apagada
#   printops-backend… → levantada (la URL es la misma de antes)
python3 scripts/azure.py revisions
#   Replicas 0 → no hay instancias corriendo
curl https://printops-backend.purplemeadow-6cc5e29a.northcentralus.azurecontainerapps.io/health
#   tarda ~45 s la primera vez (arranque de Spring); antes puede dar "App port is not ready"

# ---------- Versionado ----------
# dev-<sha>       → cada push a azure/develop (el sha es el commit exacto de la imagen)
# vX.Y.Z + latest → tag de git v* (versión oficial; el CI/CD la despliega)

# ¿Qué versión corre ahora? (columna Imagen)
python3 scripts/azure.py status

# ¿Qué código tiene esa versión? (reemplazá 0afd787 por el sha del tag dev-…)
git show 0afd787 --stat

# ¿Qué versiones hay en el ACR? (de la más nueva a la más vieja)
python3 scripts/azure.py list

# ¿Qué versiones oficiales hay en git?
git tag -l --sort=-creatordate

# Publicar una versión oficial (MAYOR.MENOR.PARCHE: arreglo → PARCHE, función nueva → MENOR,
# cambio incompatible → MAYOR). Dispara el CI/CD: construye vX.Y.Z + latest y despliega.
git tag v0.2.0
git push origin v0.2.0

# Volver a una versión anterior (tiene que existir en el ACR)
python3 scripts/azure.py deploy --tag dev-d7fe258

# El historial de qué se desplegó y cuándo está en GitHub → Actions → CI/CD (paso "Deploy").
# Ojo: v0.1.0 se subió a mano y no tiene tag de git; no se sabe de qué commit salió.
