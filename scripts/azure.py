#!/usr/bin/env python3
"""Operaciones locales de PrintOps backend contra Azure Container Apps.

Uso:  python scripts/azure.py [comando] [opciones]
      python scripts/azure.py                         # menú interactivo

Toda la configuración sale de variables de entorno (ver DEPLOY.md):
  ACR_NAME                  nombre del registry (default: acrprintopscyj28)
  AZURE_RESOURCE_GROUP      grupo de recursos (default: rg-printops)
  CONTAINERAPP_ENV          entorno de Container Apps (default: cae-printops)
  CONTAINERAPP_NAME         nombre del Container App (default: printops-backend)
  IMAGE_NAME                nombre de la imagen (default: nombre del repo normalizado)
  IMAGE_TAG                 tag (default: dev-<sha corto de git>); también --tag
  LOG_ANALYTICS_WORKSPACE   workspace de Log Analytics para `logs` (default: law-printops)
Secretos de la app, solo al CREAR el Container App (se guardan como secrets del app):
  JWT_SECRET, DB_URL, DB_USERNAME, DB_PASSWORD               (obligatorios)
  APPLICATIONINSIGHTS_CONNECTION_STRING, RESEND_API_KEY      (opcionales)

Los comandos que crean, modifican o borran recursos muestran lo que van a hacer
y piden confirmación (y/n). --yes la saltea.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import time
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
    default = default or {
        "ACR_NAME": "acrprintopscyj28",
        "AZURE_RESOURCE_GROUP": "rg-printops",
        "CONTAINERAPP_ENV": "cae-printops",
    }.get(name)
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
    for i, part in enumerate(out[:-1]):
        if part == "--registry-password":
            out[i + 1] = "****"
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
        print(f"→ {masked(cmd)}", flush=True)
    return subprocess.run(cmd, check=check, text=True, capture_output=capture)


def failure(e: subprocess.CalledProcessError) -> str:
    """Mensaje de error de un comando; incluye stderr si se capturó la salida."""
    detail = (e.stderr or "").strip()
    return f"Falló: {masked(e.cmd)} (código {e.returncode})" + (f"\n{detail}" if detail else "")


def require_tool(tool: str, hint: str) -> None:
    if shutil.which(tool) is None:
        sys.exit(f"No se encontró `{tool}`. {hint}")


def acr_password() -> str:
    r = run(["az", "acr", "credential", "show", "-n", env("ACR_NAME", required=True),
             "--query", "passwords[0].value", "-o", "tsv"], check=False, capture=True)
    if r.returncode != 0 or not r.stdout.strip():
        sys.exit("No se pudo leer la clave del ACR. Activá el usuario admin: "
                 "az acr update -n $ACR_NAME --admin-enabled true")
    return r.stdout.strip()


def require_image_in_acr(tag: str) -> None:
    """Corta si el tag no está en el ACR: un update con una imagen inexistente deja la app caída."""
    acr = env("ACR_NAME", required=True)
    r = run(["az", "acr", "repository", "show", "-n", acr, "--image", f"{image_name()}:{tag}",
             "--query", "name", "-o", "tsv"], check=False, capture=True)
    if r.returncode == 0 and r.stdout.strip():
        return
    error = (r.stderr or r.stdout).lower()
    if "does not exist" in error or "not found" in error or "unknown" in error:
        sys.exit(f"La imagen {image_name()}:{tag} no está en el ACR {acr}. Construila y subila primero:\n"
                 f"  python3 scripts/azure.py build --tag {tag}\n"
                 f"  python3 scripts/azure.py push --tag {tag}")
    detail = error.strip().splitlines()[0] if error.strip() else "error de Azure CLI"
    sys.exit(f"No se pudo verificar la imagen en el ACR {acr}: {detail}")


def app_exists(name: str, rg: str) -> bool:
    r = run(["az", "containerapp", "show", "-n", name, "-g", rg, "--query", "name", "-o", "tsv"],
            check=False, capture=True)
    if r.returncode == 0:
        if r.stdout.strip() == name:
            return True
        sys.exit(f"No se pudo confirmar el nombre del Container App {name} en {rg}.")
    error = (r.stderr or r.stdout).lower()
    if "resourcenotfound" in error or "not found" in error:
        return False
    detail = error.splitlines()[0] if error else "error de Azure CLI"
    sys.exit(f"No se pudo consultar el Container App {name} en {rg}: {detail}")


# ---------- comandos ----------

def cmd_build(args) -> None:
    require_tool("docker", "Instalá Docker Desktop.")
    local = f"{image_name()}:{image_tag(args)}"
    tags = ["-t", local]
    if env("ACR_NAME"):
        tags += ["-t", f"{registry()}/{local}"]
    # Container Apps corre linux/amd64: forzarlo aunque se buildee desde una Mac ARM.
    # Mismo formato que la imagen del CI (manifest Docker v2, sin índice ni attestations). Docker
    # Desktop con el store de containerd sube por defecto un índice OCI con un manifest
    # "unknown/unknown", y Container Apps lo reporta como "image not found in the registry".
    run(["docker", "build", "--platform", "linux/amd64", "--provenance=false", "--sbom=false",
         "--output", "type=image,oci-mediatypes=false", *tags, str(ROOT)])
    print(f"\nImagen lista: {local}")


def cmd_push(args) -> None:
    require_tool("az", "Instalá Azure CLI.")
    require_tool("docker", "Instalá Docker Desktop.")
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
    tag = image_tag(args)
    image = f"{registry()}/{image_name()}:{tag}"
    require_image_in_acr(tag)
    limits =["--cpu", CPU, "--memory", MEMORY, "--min-replicas", MIN_REPLICAS, "--max-replicas", MAX_REPLICAS]

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
           # Usuario admin del ACR (az acr update --admin-enabled true). Los entornos
           # "express" de Container Apps no aceptan identidad administrada para el registry.
           "--registry-server", registry(), "--registry-username", env("ACR_NAME", required=True),
           "--registry-password", acr_password(),
           "--ingress", "external", "--target-port", TARGET_PORT,
           *limits,
           "--secrets", *[f"{s}={v}" for s, v in secrets.items()],
           "--env-vars", *env_vars,
           "--query", "properties.configuration.ingress.fqdn", "-o", "tsv"]
    confirm(f"Se va a CREAR el Container App {name} en {rg} ({CPU} vCPU, {MEMORY}, "
            f"réplicas {MIN_REPLICAS}-{MAX_REPLICAS}) con la imagen {image}.", [cmd], args)
    fqdn = run(cmd, capture=True).stdout.strip()
    if not fqdn:
        sys.exit(f"El Container App {name} se creó pero Azure no devolvió la URL; revisá el ingress.")
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
    print("Revisiones desactivadas. Ejecutá `python3 scripts/azure.py start` para reactivar la última.")


def cmd_start(args) -> None:
    require_tool("az", "Instalá Azure CLI.")
    rg, name = env("AZURE_RESOURCE_GROUP", required=True), app_name()
    r = run(["az", "containerapp", "revision", "list", "-n", name, "-g", rg, "--all",
             "--query", "[].{name:name,active:properties.active,created:properties.createdTime}", "-o", "json"],
            capture=True)
    all_revisions = json.loads(r.stdout or "[]")
    if any(revision["active"] for revision in all_revisions):
        print("La app ya tiene una revisión activa.")
        return
    revisions = [revision for revision in all_revisions if not revision["active"]]
    if not revisions:
        print("No hay revisiones inactivas para reactivar.")
        return
    revision = max(revisions, key=lambda item: item["created"] or "")["name"]
    cmd = ["az", "containerapp", "revision", "activate", "-n", name, "-g", rg, "--revision", revision]
    confirm(f"Se va a REACTIVAR la revisión {revision} de {name}.", [cmd], args)
    run(cmd)


def cmd_status(args) -> None:
    require_tool("az", "Instalá Azure CLI.")
    rg, name = env("AZURE_RESOURCE_GROUP", required=True), app_name()
    run(["az", "containerapp", "show", "-n", name, "-g", rg, "--query",
         "{Nombre:name,Estado:properties.runningStatus,URL:properties.configuration.ingress.fqdn,"
         "Imagen:properties.template.containers[0].image,Revision:properties.latestRevisionName}", "-o", "table"])


def cmd_revisions(args) -> None:
    require_tool("az", "Instalá Azure CLI.")
    run(["az", "containerapp", "revision", "list", "-n", app_name(),
         "-g", env("AZURE_RESOURCE_GROUP", required=True), "--all",
         "--query", "[].{Revision:name,Activa:properties.active,Replicas:properties.replicas,Creada:properties.createdTime}",
         "-o", "table"])


def cmd_logs(args) -> None:
    require_tool("az", "Instalá Azure CLI.")
    rg = env("AZURE_RESOURCE_GROUP", required=True)
    workspace = env("LOG_ANALYTICS_WORKSPACE", "law-printops")
    workspace_id = run(["az", "monitor", "log-analytics", "workspace", "show", "-g", rg,
                        "-n", workspace, "--query", "customerId", "-o", "tsv"], capture=True).stdout.strip()
    if not workspace_id:
        sys.exit(f"No se encontró el workspace de Log Analytics {workspace} en {rg}.")

    seen: dict[tuple, None] = {}  # dict para conservar el orden de inserción al recortar
    lookback = "24h"
    if args.follow:
        print("Logs desde Log Analytics; pueden llegar con unos minutos de demora. Ctrl+C para salir.\n")
    while True:
        escaped_app_name = app_name().replace("'", "''")
        query = ("ContainerAppConsoleLogs_CL "
                 f"| where ContainerAppName_s == '{escaped_app_name}' "
                 f"| where TimeGenerated > ago({lookback}) "
                 "| project TimeGenerated, RevisionName_s, ContainerName_s, Log_s "
                 "| top 100 by TimeGenerated desc")
        result = run(["az", "monitor", "log-analytics", "query", "--workspace", workspace_id,
                      "--analytics-query", query, "--output", "json"], capture=True)
        rows = json.loads(result.stdout or "[]")
        # La query ya viene ordenada (más nuevo primero). No reordenar en Python: TimeGenerated
        # es texto con decimales variables (".19Z" vs ".192Z") y no ordena bien como string.
        for row in reversed(rows):
            key = (row["TimeGenerated"], row.get("RevisionName_s"), row.get("ContainerName_s"), row.get("Log_s"))
            if key not in seen:
                print(f"{row['TimeGenerated']}  {row.get('Log_s', '')}", flush=True)
                seen[key] = None
        if not args.follow:
            return
        lookback = "1h"
        while len(seen) > 2000:
            del seen[next(iter(seen))]
        time.sleep(15)


def cmd_url(args) -> None:
    require_tool("az", "Instalá Azure CLI.")
    r = run(["az", "containerapp", "show", "-n", app_name(), "-g", env("AZURE_RESOURCE_GROUP", required=True),
             "--query", "properties.configuration.ingress.fqdn", "-o", "tsv"], capture=True)
    fqdn = r.stdout.strip()
    if not fqdn:
        sys.exit(f"El Container App {app_name()} no tiene ingress público.")
    print(f"https://{fqdn}")


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
    require_tool("docker", "Instalá Docker Desktop y asegurate de que esté iniciado.")
    require_tool("grype", "Instalá Grype: brew install grype")
    tag = image_tag(args)
    local = f"{image_name()}:{tag}"
    candidates = [local, f"{registry()}/{local}"]
    image = None
    for candidate in candidates:
        inspected = run(["docker", "image", "inspect", candidate], check=False, capture=True)
        if inspected.returncode == 0:
            image = candidate
            break
        error = (inspected.stderr or inspected.stdout).lower()
        if not any(marker in error for marker in ("no such image", "no such object", "not found")):
            detail = error.splitlines()[0] if error else "error de Docker"
            sys.exit(f"No se pudo consultar Docker: {detail}")
    if image is None:
        sys.exit(f"No está la imagen {local} en Docker. Construí ese tag con "
                 f"`python3 scripts/azure.py build --tag {tag}` o elegí un tag ya descargado.")
    # docker: hace que grype use la imagen local ya verificada en vez de bajarla del registry.
    cmd = ["grype", f"docker:{image}"]
    if args.fail_on:
        cmd += ["--fail-on", args.fail_on]
    result = run(cmd, check=False)
    if result.returncode:
        raise subprocess.CalledProcessError(result.returncode, cmd)


MENU = [
    ("build", "Construir imagen"), ("push", "Subir imagen al ACR"),
    ("deploy", "Desplegar / actualizar"), ("status", "Estado del Container App"),
    ("url", "Ver URL"), ("logs", "Seguir logs (Log Analytics)"), ("revisions", "Listar revisiones"),
    ("list", "Listar apps e imágenes"),
    ("scan", "Escanear imagen con Grype"), ("start", "Reactivar app"),
    ("stop", "Apagar app"), ("delete", "Borrar Container App"),
]


def interactive_menu() -> None:
    handlers = {
        "build": cmd_build, "push": cmd_push, "deploy": cmd_deploy, "status": cmd_status,
        "url": cmd_url, "logs": cmd_logs, "revisions": cmd_revisions, "list": cmd_list, "scan": cmd_scan,
        "start": cmd_start, "stop": cmd_stop, "delete": cmd_delete,
    }
    while True:
        print("\033[2J\033[H", end="")
        print("\033[1;36m╭──────────────────────────────────────╮")
        print("│       PRINTOPS · AZURE CONTAINER APP │")
        print("╰──────────────────────────────────────╯\033[0m")
        print(f"  App: {app_name()}   Grupo: {env('AZURE_RESOURCE_GROUP')}   ACR: {env('ACR_NAME')}\n")
        for number, (_, label) in enumerate(MENU, 1):
            print(f"  \033[1;33m{number:2}\033[0m  {label}")
        print("\n   0  Salir")
        choice = input("\nElegí una opción: ").strip()
        if choice in ("0", "q", "Q"):
            return
        if not choice.isdigit() or not 1 <= int(choice) <= len(MENU):
            input("Opción inválida. Enter para continuar...")
            continue
        command, _ = MENU[int(choice) - 1]
        args = argparse.Namespace(tag=None, yes=False, fail_on=None, follow=command == "logs")
        if command in {"build", "push", "deploy", "scan"}:
            args.tag = input("Tag de imagen (Enter para usar el tag por defecto): ").strip() or None
        try:
            handlers[command](args)
        except subprocess.CalledProcessError as e:
            print(failure(e))
        except SystemExit as e:
            # confirm() y las validaciones usan sys.exit: en el menú solo cortan la opción actual.
            if e.code not in (None, 0):
                print(e.code)
        except KeyboardInterrupt:
            print("\nOperación cancelada.")
        input("\nEnter para volver al menú...")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command")
    commands = {
        "build": (cmd_build, "docker build + tag versionado"),
        "push": (cmd_push, "login al ACR y push de la imagen"),
        "deploy": (cmd_deploy, "crea o actualiza el Container App con los límites de costo"),
        "status": (cmd_status, "muestra el estado de Azure"),
        "url": (cmd_url, "muestra la URL pública"),
        "logs": (cmd_logs, "muestra logs; --follow para seguirlos"),
        "revisions": (cmd_revisions, "lista las revisiones"),
        "start": (cmd_start, "reactiva la revisión inactiva más reciente"),
        "stop": (cmd_stop, "desactiva las revisiones activas"),
        "delete": (cmd_delete, "borra el Container App"),
        "list": (cmd_list, "lista apps e imágenes (tabla)"),
        "scan": (cmd_scan, "escanea la imagen local con grype"),
    }
    for name, (func, help_text) in commands.items():
        p = sub.add_parser(name, help=help_text)
        p.add_argument("--tag", help="tag de la imagen (default: IMAGE_TAG o dev-<sha>)")
        p.add_argument("--yes", "-y", action="store_true", help="no pedir confirmación")
        if name == "logs":
            p.add_argument("--follow", "-f", action="store_true", help="consultar logs periódicamente; pueden llegar con demora")
        if name == "scan":
            p.add_argument("--fail-on", choices=["negligible", "low", "medium", "high", "critical"],
                           help="salir con error si hay vulnerabilidades de esta severidad o mayor")
        p.set_defaults(func=func)
    args = parser.parse_args()
    if not args.command:
        try:
            if sys.stdin.isatty():
                interactive_menu()
            else:
                parser.print_help()
        except KeyboardInterrupt:
            print("\nMenú cerrado.")
        return
    try:
        args.func(args)
    except subprocess.CalledProcessError as e:
        sys.exit(failure(e))
    except KeyboardInterrupt:
        sys.exit("\nCancelado.")


if __name__ == "__main__":
    main()
