#!/usr/bin/env python3
"""
Standalone MCP launcher for MCreatorMCP.

This launcher runs outside of MCreator and can start MCreator plus the
MCreatorMCP plugin, then proxy MCP tool/resource requests to the plugin's
internal MCP server. It exposes launcher-only tools such as `launchMCreator`
and `openMCreator` so an agent can cold-start MCreator from the IDE.

Default endpoint: http://localhost:5176/mcp
Health endpoint:  http://localhost:5176/health

The plugin is expected to expose its own MCP server at http://localhost:5175/mcp
once MCreator has loaded.
"""

import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from threading import Thread, Lock

LAUNCHER_PORT = int(os.environ.get("MCP_LAUNCHER_PORT", "5176"))
PLUGIN_PORT = int(os.environ.get("MCP_PLUGIN_PORT", "5175"))
PLUGIN_URL = f"http://localhost:{PLUGIN_PORT}/mcp"
HEALTH_URL = f"http://localhost:{PLUGIN_PORT}/health"

mcreator_process = None
plugin_ready = False
plugin_workspace = None
process_lock = Lock()


def _pid_from_ps():
    """Return the PID of the running MCreator main java process, or None."""
    try:
        out = subprocess.check_output(
            ["ps", "aux"], stderr=subprocess.DEVNULL, text=True
        )
        for line in out.splitlines():
            lower = line.lower()
            if "mcreator" in lower and "java" in lower and "gradle" not in lower:
                parts = line.split()
                if parts and parts[1].isdigit():
                    return int(parts[1])
    except Exception:
        pass
    return None


def _default_install_path():
    """Try to find a MCreator installation on this machine."""
    candidates = [
        "/home/ubuntu/repos/MCreator20262",
        "/opt/MCreator20262",
        str(Path.home() / "MCreator20262"),
    ]
    for path in candidates:
        if os.path.isfile(os.path.join(path, "mcreator.sh")):
            return path
    home = Path.home()
    for p in home.glob("MCreator*/mcreator.sh"):
        return str(p.parent)
    return None


def _resolve_workspace_file(workspace_path):
    """Turn a workspace folder or .mcreator file into a concrete .mcreator file path."""
    if not workspace_path:
        return None
    workspace_path = os.path.expanduser(workspace_path)
    if os.path.isfile(workspace_path):
        return workspace_path
    if os.path.isdir(workspace_path):
        folder = Path(workspace_path)
        candidate = folder / (folder.name + ".mcreator")
        if candidate.is_file():
            return str(candidate)
        for f in folder.glob("*.mcreator"):
            return str(f)
    return workspace_path


def _wait_for_plugin(timeout=120):
    """Poll the plugin health endpoint until it responds or timeout."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            with urllib.request.urlopen(HEALTH_URL, timeout=2) as r:
                data = json.loads(r.read().decode("utf-8"))
                return data.get("status") == "healthy"
        except Exception:
            time.sleep(1)
    return False


def _plugin_rpc(method, params=None, timeout=30, req_id=1):
    """Forward a JSON-RPC request to the plugin's MCP server and return the parsed response."""
    payload = {
        "jsonrpc": "2.0",
        "method": method,
        "id": req_id,
    }
    if params is not None:
        payload["params"] = params
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        PLUGIN_URL,
        data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8"))


def _build_error(message):
    return {
        "jsonrpc": "2.0",
        "id": None,
        "error": {"code": -32603, "message": message},
    }


def _build_result(result, req_id):
    return {"jsonrpc": "2.0", "id": req_id, "result": result}


class LauncherTools:
    """Launcher-only MCP tools."""

    @staticmethod
    def handle(name, arguments):
        handlers = {
            "launchMCreator": LauncherTools.launch_mcreator,
            "openMCreator": LauncherTools.launch_mcreator,
            "listMCreatorInstallations": LauncherTools.list_installations,
            "getMCreatorStatus": LauncherTools.get_status,
            "stopMCreator": LauncherTools.stop_mcreator,
        }
        fn = handlers.get(name)
        if fn is None:
            raise ValueError(f"Unknown launcher tool: {name}")
        return fn(arguments)

    @staticmethod
    def launch_mcreator(arguments):
        global mcreator_process, plugin_ready, plugin_workspace

        install_path = arguments.get("installPath") or _default_install_path()
        workspace_path = arguments.get("workspacePath")
        headless = arguments.get("headless", True)
        display = arguments.get("display", "1280x720x24")

        if not install_path:
            raise ValueError("installPath not provided and no default MCreator installation found")

        install_path = os.path.expanduser(install_path)
        mcreator_sh = os.path.join(install_path, "mcreator.sh")
        if not os.path.isfile(mcreator_sh):
            raise ValueError(f"mcreator.sh not found in {install_path}")

        resolved_workspace = _resolve_workspace_file(workspace_path)

        with process_lock:
            if mcreator_process is not None and mcreator_process.poll() is None:
                raise ValueError("MCreator appears to already be running")

            # Kill any lingering MCreator java process so the plugin can claim its port.
            existing_pid = _pid_from_ps()
            if existing_pid:
                try:
                    subprocess.run(["kill", "-9", str(existing_pid)], check=False)
                    time.sleep(2)
                except Exception:
                    pass

            cmd = ["xvfb-run", "-a", "-s", f"-screen 0 {display}", "./mcreator.sh"]
            if resolved_workspace:
                cmd.append(resolved_workspace)

            try:
                log_path = "/tmp/mcreator_launcher.log"
                log_file = open(log_path, "w")
                mcreator_process = subprocess.Popen(
                    cmd,
                    stdout=log_file,
                    stderr=subprocess.STDOUT,
                    cwd=install_path,
                    start_new_session=True,
                )
            except Exception as e:
                raise ValueError(f"Failed to start MCreator: {e}")

        pid = mcreator_process.pid
        plugin_ready = False
        plugin_workspace = None

        def monitor():
            global plugin_ready
            mcreator_process.wait()
            plugin_ready = False

        Thread(target=monitor, daemon=True).start()

        if _wait_for_plugin(timeout=240):
            try:
                health = json.loads(urllib.request.urlopen(HEALTH_URL, timeout=2).read().decode("utf-8"))
                plugin_ready = health.get("status") == "healthy"
            except Exception:
                plugin_ready = False

            # If a workspace was requested, wait for it to actually load.
            if resolved_workspace:
                workspace_deadline = time.time() + 60
                while time.time() < workspace_deadline:
                    try:
                        health = json.loads(urllib.request.urlopen(HEALTH_URL, timeout=2).read().decode("utf-8"))
                        plugin_workspace = health.get("workspace")
                        if plugin_workspace:
                            break
                    except Exception:
                        pass
                    time.sleep(1)

            return (
                f"MCreator started (PID {pid}). Plugin is healthy. "
                f"Workspace: {plugin_workspace or 'none yet'}"
            )
        else:
            return (
                f"MCreator started (PID {pid}) but the plugin did not become healthy within 240s. "
                f"Check {log_path} for details."
            )

    @staticmethod
    def list_installations(_arguments):
        installs = []
        home = Path.home()
        candidates = [
            Path("/home/ubuntu/repos/MCreator20262"),
            Path("/opt/MCreator20262"),
            home / "MCreator20262",
        ]
        for cand in candidates:
            if (cand / "mcreator.sh").is_file():
                installs.append({"path": str(cand), "version": _read_version(cand)})
        for cand in home.glob("MCreator*"):
            if cand.is_dir() and (cand / "mcreator.sh").is_file():
                installs.append({"path": str(cand), "version": _read_version(cand)})
        return installs

    @staticmethod
    def get_status(_arguments):
        pid = _pid_from_ps()
        ready = False
        workspace = None
        try:
            with urllib.request.urlopen(HEALTH_URL, timeout=2) as r:
                data = json.loads(r.read().decode("utf-8"))
                ready = data.get("status") == "healthy"
                workspace = data.get("workspace")
        except Exception:
            pass
        return {
            "running": pid is not None,
            "pid": pid,
            "pluginReady": ready,
            "pluginWorkspace": workspace,
            "launcherPort": LAUNCHER_PORT,
            "pluginPort": PLUGIN_PORT,
        }

    @staticmethod
    def stop_mcreator(_arguments):
        pid = _pid_from_ps()
        if pid:
            try:
                subprocess.run(["kill", "-9", str(pid)], check=False)
                time.sleep(2)
                return f"Sent SIGKILL to MCreator process {pid}"
            except Exception as e:
                raise ValueError(f"Failed to stop MCreator: {e}")
        raise ValueError("No MCreator process found")


def _read_version(install_path):
    props = Path(install_path) / "mcreator.properties"
    try:
        text = props.read_text()
        m = re.search(r"mcreator.version=([^\s]+)", text)
        if m:
            return m.group(1)
    except Exception:
        pass
    return "unknown"


class MCPHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print(f"[MCP-Launcher] {self.address_string()} {fmt % args}")

    def _send_json(self, status, data):
        body = json.dumps(data).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()
        self.wfile.write(body)

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_GET(self):
        if self.path == "/health":
            pid = _pid_from_ps()
            ready = False
            workspace = None
            try:
                with urllib.request.urlopen(HEALTH_URL, timeout=2) as r:
                    data = json.loads(r.read().decode("utf-8"))
                    ready = data.get("status") == "healthy"
                    workspace = data.get("workspace")
            except Exception:
                pass
            self._send_json(200, {
                "status": "healthy",
                "service": "MCreatorMCP Launcher",
                "mcreatorRunning": pid is not None,
                "pluginReady": ready,
                "pluginWorkspace": workspace,
            })
        else:
            self._send_json(404, {"error": "Not found"})

    def do_POST(self):
        if self.path != "/mcp":
            self._send_json(404, {"error": "Not found"})
            return

        length = int(self.headers.get("Content-Length", 0))
        if length <= 0:
            self._send_json(400, {"error": "Empty body"})
            return

        try:
            body = self.rfile.read(length).decode("utf-8")
            request = json.loads(body)
        except Exception as e:
            self._send_json(400, _build_error(f"Invalid JSON: {e}"))
            return

        method = request.get("method")
        req_id = request.get("id")
        params = request.get("params", {})

        if method == "initialize":
            self._send_json(200, _build_result({
                "protocolVersion": "2024-11-05",
                "serverInfo": {"name": "MCreatorMCP Launcher", "version": "1.0.0"},
                "capabilities": {
                    "tools": {"listChanged": True},
                    "resources": {"listChanged": True, "subscribe": False},
                },
            }, req_id))
            return

        if method == "initialized":
            self._send_json(200, _build_result({}, req_id))
            return

        if method == "tools/list":
            tools = _launcher_tool_definitions()
            plugin_tools = []
            try:
                plugin_resp = _plugin_rpc("tools/list")
                plugin_tools = plugin_resp.get("result", {}).get("tools", [])
            except Exception:
                pass

            seen = set()
            merged = []
            for tool in tools + plugin_tools:
                name = tool.get("name")
                if name and name not in seen:
                    seen.add(name)
                    merged.append(tool)
            self._send_json(200, _build_result({"tools": merged}, req_id))
            return

        if method == "tools/call":
            name = params.get("name")
            arguments = params.get("arguments", {})

            if name in LAUNCHER_TOOL_NAMES:
                try:
                    result_text = LauncherTools.handle(name, arguments)
                    self._send_json(200, _build_result({
                        "content": [{"type": "text", "text": str(result_text)}],
                        "isError": False,
                    }, req_id))
                except Exception as e:
                    self._send_json(200, _build_result({
                        "content": [{"type": "text", "text": f"Error: {e}"}],
                        "isError": True,
                    }, req_id))
                return

            # Forward all other tool calls to the plugin.
            try:
                plugin_resp = _plugin_rpc("tools/call", params, req_id=req_id)
                self._send_json(200, plugin_resp)
            except urllib.error.HTTPError as e:
                self._send_json(e.code, {"error": e.reason})
            except Exception as e:
                self._send_json(200, _build_result({
                    "content": [{"type": "text", "text": f"Plugin not reachable: {e}"}],
                    "isError": True,
                }, req_id))
            return

        # Forward resources and everything else to the plugin when possible.
        try:
            plugin_resp = _plugin_rpc(method, params, req_id=req_id)
            self._send_json(200, plugin_resp)
        except urllib.error.HTTPError as e:
            self._send_json(e.code, {"error": e.reason})
        except Exception as e:
            self._send_json(200, _build_error(f"Plugin not reachable: {e}"))


def _launcher_tool_definitions():
    return [
        {
            "name": "launchMCreator",
            "description": "Start MCreator (optionally with a workspace). The MCP server will proxy to the plugin once it is ready.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "installPath": {"type": "string", "description": "Path to MCreator installation containing mcreator.sh"},
                    "workspacePath": {"type": "string", "description": "Optional path to a workspace folder or .mcreator file"},
                    "headless": {"type": "boolean", "description": "Run under xvfb (default true on Linux)"},
                    "display": {"type": "string", "description": "xvfb display dimensions, e.g. 1280x720x24"},
                },
            },
        },
        {
            "name": "openMCreator",
            "description": "Alias for launchMCreator.",
            "inputSchema": {
                "type": "object",
                "properties": {
                    "installPath": {"type": "string"},
                    "workspacePath": {"type": "string"},
                    "headless": {"type": "boolean"},
                    "display": {"type": "string"},
                },
            },
        },
        {
            "name": "listMCreatorInstallations",
            "description": "List discovered MCreator installations on this machine.",
            "inputSchema": {"type": "object", "properties": {}},
        },
        {
            "name": "getMCreatorStatus",
            "description": "Check whether MCreator is running and whether the plugin MCP server is reachable.",
            "inputSchema": {"type": "object", "properties": {}}
        },
        {
            "name": "stopMCreator",
            "description": "Forcefully stop the running MCreator process.",
            "inputSchema": {"type": "object", "properties": {}}
        },
    ]


LAUNCHER_TOOL_NAMES = {t["name"] for t in _launcher_tool_definitions()}


def main():
    server = HTTPServer(("0.0.0.0", LAUNCHER_PORT), MCPHandler)
    print(f"MCreatorMCP launcher listening on http://0.0.0.0:{LAUNCHER_PORT}/mcp")
    print(f"Health: http://0.0.0.0:{LAUNCHER_PORT}/health")
    print(f"Will proxy to plugin at {PLUGIN_URL} when ready")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("Shutting down")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
