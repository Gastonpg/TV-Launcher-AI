#!/usr/bin/env python3
"""TV Box Agent: read-only, token-protected diagnostics for a local Android TV box.

This server deliberately exposes no arbitrary shell, file-write, install, or root
command endpoint. Every command below is fixed and read-only.
"""
import json
import os
import secrets
import subprocess
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse, parse_qs

VERSION = "0.1.0"
HOST = os.environ.get("TVBOX_AGENT_HOST", "0.0.0.0")
PORT = int(os.environ.get("TVBOX_AGENT_PORT", "8765"))
STARTED = time.time()
TOKEN_FILE = Path.home() / ".config" / "tvbox-agent" / "token"
MAX_OUTPUT = 24000

PROPERTIES = (
    "ro.build.version.release",
    "ro.build.version.sdk",
    "ro.build.type",
    "ro.debuggable",
    "ro.secure",
    "ro.product.model",
    "ro.product.board",
    "ro.product.cpu.abi",
    "ro.build.fingerprint",
    "service.adb.tcp.port",
)


def load_or_create_token():
    TOKEN_FILE.parent.mkdir(parents=True, exist_ok=True)
    if TOKEN_FILE.exists():
        token = TOKEN_FILE.read_text(encoding="utf-8").strip()
        os.chmod(str(TOKEN_FILE), 0o600)
        if token:
            return token, False
    token = secrets.token_urlsafe(32)
    fd = os.open(str(TOKEN_FILE), os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        stream.write(token + "\n")
    return token, True


TOKEN, TOKEN_CREATED = load_or_create_token()


def run_root(*args):
    """Run only a fixed argument list through the TV box's existing su binary."""
    su = "/system/xbin/su" if os.path.exists("/system/xbin/su") else "su"
    try:
        result = subprocess.run(
            [su, "0", *args],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=8,
            check=False,
        )
        out = (result.stdout or "").strip()
        err = (result.stderr or "").strip()
        if result.returncode != 0:
            return {"output": out[:MAX_OUTPUT], "error": err[:2000],
                    "returncode": result.returncode}
        return {"output": out[:MAX_OUTPUT]}
    except Exception as exc:
        return {"error": str(exc)[:1000]}


def device_info():
    props = {}
    for key in PROPERTIES:
        result = run_root("getprop", key)
        props[key] = result.get("output", "")
    return {
        "properties": props,
        "root_check": run_root("id"),
        "selinux": run_root("getenforce"),
        "uptime_seconds_since_agent_start": int(time.time() - STARTED),
    }


def launcher_info():
    candidates = run_root(
        "cmd", "package", "query-activities", "--brief", "--components",
        "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.HOME",
    )
    resolved = run_root(
        "cmd", "package", "resolve-activity", "--brief",
        "-a", "android.intent.action.MAIN",
        "-c", "android.intent.category.HOME",
    )
    original = run_root("pm", "list", "packages", "com.droidlogic.mboxlauncher")
    pg = run_root("pm", "list", "packages", "com.tvlaunch.pg")
    original_state = run_root("pm", "list", "packages", "-d", "com.droidlogic.mboxlauncher")
    return {
        "home_candidates": candidates,
        "resolved_home": resolved,
        "original_launcher_installed": original,
        "tv_launcher_pg_installed": pg,
        "disabled_packages_matching_original_launcher": original_state,
    }


def packages_info(query=""):
    result = run_root("pm", "list", "packages", "-3")
    if "error" in result:
        return result
    packages = sorted(
        line.removeprefix("package:").strip()
        for line in result.get("output", "").splitlines()
        if line.startswith("package:")
    )
    if query:
        q = query.lower()
        packages = [name for name in packages if q in name.lower()]
    return {"count": len(packages), "packages": packages}


class Handler(BaseHTTPRequestHandler):
    server_version = "TVBoxAgent/" + VERSION

    def log_message(self, fmt, *args):
        # Avoid logging Authorization headers or other request details.
        print("[TVBoxAgent] " + (fmt % args))

    def send_json(self, status, payload):
        raw = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(raw)

    def authorized(self):
        supplied = self.headers.get("Authorization", "")
        expected = "Bearer " + TOKEN
        return secrets.compare_digest(supplied, expected)

    def do_GET(self):
        if not self.authorized():
            self.send_json(401, {"error": "unauthorized", "hint": "Use Authorization: Bearer <token>."})
            return

        parsed = urlparse(self.path)
        path = parsed.path
        if path == "/v1/health":
            payload = {"ok": True, "service": "TV Box Agent", "version": VERSION,
                       "uptime_seconds": int(time.time() - STARTED)}
        elif path == "/v1/device":
            payload = device_info()
        elif path == "/v1/launchers":
            payload = launcher_info()
        elif path == "/v1/packages":
            query = parse_qs(parsed.query).get("contains", [""])[0][:120]
            payload = packages_info(query)
        elif path == "/v1/diagnostics":
            payload = {
                "service": {"name": "TV Box Agent", "version": VERSION},
                "device": device_info(),
                "launchers": launcher_info(),
                "third_party_packages": packages_info(),
            }
        else:
            self.send_json(404, {
                "error": "not_found",
                "available_read_only_endpoints": [
                    "/v1/health", "/v1/device", "/v1/launchers",
                    "/v1/packages", "/v1/diagnostics",
                ],
            })
            return
        self.send_json(200, payload)

    def do_POST(self):
        self.send_json(405, {"error": "method_not_allowed", "read_only": True})

    def do_PUT(self):
        self.send_json(405, {"error": "method_not_allowed", "read_only": True})

    def do_DELETE(self):
        self.send_json(405, {"error": "method_not_allowed", "read_only": True})


if __name__ == "__main__":
    print("TV Box Agent v" + VERSION)
    print("Solo lectura: no permite ejecutar comandos arbitrarios ni modificar el sistema.")
    print("Escucha en http://%s:%s" % (HOST, PORT))
    print("Token guardado en: %s" % TOKEN_FILE)
    print("Token de acceso: %s" % TOKEN)
    print("No publiques el token ni abras este puerto a Internet.")
    print("Endpoints: /v1/health /v1/device /v1/launchers /v1/packages /v1/diagnostics")
    try:
        ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
    except KeyboardInterrupt:
        print("\nTV Box Agent detenido.")
