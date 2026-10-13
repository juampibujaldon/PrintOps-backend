#!/usr/bin/env python3
"""Operaciones locales de PrintOps backend contra Azure Container Apps.

Uso:  python scripts/azure.py <build|push|deploy|stop|delete|list|scan> [--tag TAG] [--yes]

Toda la configuración sale de variables de entorno (ver DEPLOY.md):
  ACR_NAME                  nombre del Azure Container Registry (sin .azurecr.io)   [push, deploy, list]
  AZURE_RESOURCE_GROUP      grupo de recursos                                        [deploy, stop, delete, list]
  CONTAINERAPP_ENV          entorno de Container Apps (solo al crear la app)         [deploy]
  CONTAINERAPP_NAME         nombre del Container App (default: printops-backend)
  IMAGE_NAME                nombre de la imagen (default: nombre del repo normalizado)
  IMAGE_TAG                 tag (default: dev-<sha corto de git>); también --tag
Secretos de la app, solo al CREAR el Container App (se guardan como secrets del app):
  JWT_SECRET, DB_URL, DB_USERNAME, DB_PASSWORD               (obligatorios)
  APPLICATIONINSIGHTS_CONNECTION_STRING, RESEND_API_KEY      (opcionales)

Los comandos que crean, modifican o borran recursos muestran lo que van a hacer
y piden confirmación (y/n). --yes la saltea.
"""
from __future__ import annotations

import argparse
import os
import re
import shlex
import shutil
import subprocess
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# Límites de costo. Spring Boot necesita 0.5 vCPU / 1 GiB (ver DEPLOY.md).
CPU = "0.5"
MEMORY = "1.0Gi"
MIN_REPLICAS = "0"
MAX_REPLICAS = "1"
TARGET_PORT = "8083"

# Secretos del Container App: nombre del secret → variable de entorno de la app.
APP_SECRETS = {
    "jwt-secret": "JWT_SECRET",
    "db-url": "DB_URL",
    "db-username": "DB_USERNAME",
    "db-password": "DB_PASSWORD",
    "appinsights-connection-string": "APPLICATIONINSIGHTS_CONNECTION_STRING",
    "resend-api-key": "RESEND_API_KEY",
}
REQUIRED_SECRETS = ["JWT_SECRET", "DB_URL", "DB_USERNAME", "DB_PASSWORD"]


# ---------- utilidades ----------

def env(name: str, default: str | None = None, required: bool = False) -> str:
    value = os.environ.get(name, default)
    if required and not value:
        sys.exit(f"Falta la variable de entorno {name} (ver DEPLOY.md).")
    return value or ""


def normalize(name: str) -> str:
    """Minúscula, sin tildes ni guiones bajos (regla de nombres de imagen del TP)."""
    ascii_name = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode()
    return re.sub(r"[^a-z0-9.-]", "", ascii_name.lower().replace("_", ""))


def git_short_sha() -> str:
    try:
        return subprocess.check_output(["git", "rev-parse", "--short=7", "HEAD"], cwd=ROOT, text=True).strip()
    except (OSError, subprocess.CalledProcessError):
        return "local"


def image_name() -> str:
    return env("IMAGE_NAME") or normalize(ROOT.name)


def image_tag(args) -> str:
    return args.tag or env("IMAGE_TAG") or f"dev-{git_short_sha()}"


def registry() -> str:
    return f"{env('ACR_NAME', required=True)}.azurecr.io"


def app_name() -> str:
    return env("CONTAINERAPP_NAME", "printops-backend")


def masked(cmd: list[str]) -> str:
    """El comando para mostrar en pantalla, con los valores de los secretos ocultos."""
    out = []
    for part in cmd:
        name, sep, _ = part.partition("=")
        out.append(f"{name}=****" if sep and name in APP_SECRETS else part)
    return shlex.join(out)


def confirm(summary: str, cmds: list[list[str]], args) -> None:
    print(f"\n{summary}")
    for c in cmds:
        print(f"  $ {masked(c)}")
    if args.yes:
        return
    if input("\n¿Continuar? (y/n) ").strip().lower() not in ("y", "s", "yes", "si", "sí"):
        sys.exit("Cancelado.")


def run(cmd: list[str], check: bool = True, capture: bool = False) -> subprocess.CompletedProcess:
    if not capture:
        print(f"→ {masked(cmd)}")
    return subprocess.run(cmd, check=check, text=True, capture_output=capture)


def require_tool(tool: str, hint: str) -> None:
    if shutil.which(tool) is None:
        sys.exit(f"No se encontró `{tool}`. {hint}")


def app_exists(name: str, rg: str) -> bool:
    r = run(["az", "containerapp", "show", "-n", name, "-g", rg, "--query", "name", "-o", "tsv"],
            check=False, capture=True)
    return r.returncode == 0 and r.stdout.strip() == name


# ---------- comandos ----------

def cmd_build(args) -> None:
    require_tool("docker", "Instalá Docker Desktop.")
    local = f"{image_name()}:{image_tag(args)}"
    tags = ["-t", local]
    if os.environ.get("ACR_NAME"):
        tags += ["-t", f"{registry()}/{local}"]
    run(["docker", "build", *tags, str(ROOT)])
    print(f"\nImagen lista: {local}")


def cmd_push(args) -> None:
    require_tool("az", "Instalá Azure CLI.")
    remote = f"{registry()}/{image_name()}:{image_tag(args)}"
    acr = env("ACR_NAME", required=True)
    confirm(f"Se va a subir la imagen {remote} al ACR {acr}.",
            [["az", "acr", "login", "--name", acr], ["docker", "push", remote]], args)
    run(["az", "acr", "login", "--name", acr])
    run(["docker", "push", remote])


def cmd_deploy(args) -> None:
    require_tool("az", "Instalá Azure CLI.")
    rg = env("AZURE_RESOURCE_GROUP", required=True)
    name = app_name()
    image = f"{registry()}/{image_name()}:{image_tag(args)}"
    limits = ["--cpu", CPU, "--memory", MEMORY, "--min-replicas", MIN_REPLICAS, "--max-replicas", MAX_REPLICAS]

    if app_exists(name, rg):
        cmd = ["az", "containerapp", "update", "-n", name, "-g", rg, "--image", image, *limits, "-o", "table"]
        confirm(f"Se va a ACTUALIZAR el Container App {name} con la imagen {image}.", [cmd], args)
        run(cmd)
        return

    missing = [v for v in REQUIRED_SECRETS if not os.environ.get(v)]
    if missing:
        sys.exit(f"Para crear el Container App faltan: {', '.join(missing)} (ver DEPLOY.md).")
    secrets = {s: os.environ[v] for s, v in APP_SECRETS.items() if os.environ.get(v)}
    env_vars = ["SPRING_PROFILES_ACTIVE=prod", f"PORT={TARGET_PORT}",
                # Sin Resend, el contenido de los emails (invitaciones, reset) sale en el log.
                f"EMAIL_LOG_WHEN_DISABLED={'false' if 'resend-api-key' in secrets else 'true'}"]
    env_vars += [f"{APP_SECRETS[s]}=secretref:{s}" for s in secrets]
    cmd = ["az", "containerapp", "create", "-n", name, "-g", rg,
           "--environment", env("CONTAINERAPP_ENV", required=True),
           "--image", image,
           # Identidad administrada con rol AcrPull: sin usuario/clave del registry.
           "--registry-server", registry(), "--registry-identity", "system",
           "--ingress", "external", "--target-port", TARGET_PORT,
           *limits,
           "--secrets", *[f"{s}={v}" for s, v in secrets.items()],
           "--env-vars", *env_vars,
           "--query", "properties.configuration.ingress.fqdn", "-o", "tsv"]
    confirm(f"Se va a CREAR el Container App {name} en {rg} ({CPU} vCPU, {MEMORY}, "
            f"réplicas {MIN_REPLICAS}-{MAX_REPLICAS}) con la imagen {image}.", [cmd], args)
    fqdn = run(cmd, capture=True).stdout.strip()
    print(f"\nContainer App creado: https://{fqdn}")
    # El magic link de verificación necesita la URL pública.
    set_url = ["az", "containerapp", "update", "-n", name, "-g", rg,
               "--set-env-vars", f"APP_BASE_URL=https://{fqdn}", "-o", "none"]
    run(set_url)


def cmd_stop(args) -> None:
    require_tool("az", "Instalá Azure CLI.")
    rg = env("AZURE_RESOURCE_GROUP", required=True)
    name = app_name()
    r = run(["az", "containerapp", "revision", "list", "-n", name, "-g", rg,
             "--query", "[?properties.active].name", "-o", "tsv"], capture=True)
    revisions = r.stdout.split()
    if not revisions:
        print("No hay revisiones activas.")
        return
    cmds = [["az", "containerapp", "revision", "deactivate", "-n", name, "-g", rg, "--revision", rev]
            for rev in revisions]
    confirm(f"Se van a DESACTIVAR {len(revisions)} revisión(es) de {name}: {', '.join(revisions)}.", cmds, args)
    for c in cmds:
        run(c)
    print("Revisión desactivada. Para volver: az containerapp revision activate ... --revision <nombre>")


def cmd_delete(args) -> None:
    require_tool("az", "Instalá Azure CLI.")
    rg = env("AZURE_RESOURCE_GROUP", required=True)
    cmd = ["az", "containerapp", "delete", "-n", app_name(), "-g", rg, "--yes"]
    confirm(f"Se va a BORRAR el Container App {app_name()} (el ACR y la base no se tocan).", [cmd], args)
    run(cmd)


def cmd_list(args) -> None:
    require_tool("az", "Instalá Azure CLI.")
    rg = env("AZURE_RESOURCE_GROUP", required=True)
    print("== Container Apps")
    run(["az", "containerapp", "list", "-g", rg, "--query",
         "[].{Nombre:name, URL:properties.configuration.ingress.fqdn, "
         "Imagen:properties.template.containers[0].image, Estado:properties.runningStatus}",
         "--output", "table"], check=False)
    print("\n== Imágenes en el ACR")
    acr = env("ACR_NAME", required=True)
    run(["az", "acr", "repository", "show-tags", "-n", acr, "--repository", image_name(),
         "--orderby", "time_desc", "--output", "table"], check=False)


def cmd_scan(args) -> None:
    require_tool("grype", "Instalá Grype: brew install grype")
    image = f"{image_name()}:{image_tag(args)}"
    cmd = ["grype", image]
    if args.fail_on:
        cmd += ["--fail-on", args.fail_on]
    sys.exit(run(cmd, check=False).returncode)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    commands = {
        "build": (cmd_build, "docker build + tag versionado"),
        "push": (cmd_push, "login al ACR y push de la imagen"),
        "deploy": (cmd_deploy, "crea o actualiza el Container App con los límites de costo"),
        "stop": (cmd_stop, "desactiva la revisión activa"),
        "delete": (cmd_delete, "borra el Container App"),
        "list": (cmd_list, "lista apps e imágenes (tabla)"),
        "scan": (cmd_scan, "escanea la imagen local con grype"),
    }
    for name, (func, help_text) in commands.items():
        p = sub.add_parser(name, help=help_text)
        p.add_argument("--tag", help="tag de la imagen (default: IMAGE_TAG o dev-<sha>)")
        p.add_argument("--yes", "-y", action="store_true", help="no pedir confirmación")
        if name == "scan":
            p.add_argument("--fail-on", choices=["negligible", "low", "medium", "high", "critical"],
                           help="salir con error si hay vulnerabilidades de esta severidad o mayor")
        p.set_defaults(func=func)
    args = parser.parse_args()
    try:
        args.func(args)
    except subprocess.CalledProcessError as e:
        sys.exit(f"Falló: {masked(e.cmd)} (código {e.returncode})")
    except KeyboardInterrupt:
        sys.exit("\nCancelado.")


if __name__ == "__main__":
    main()
