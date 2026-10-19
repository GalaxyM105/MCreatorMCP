#!/usr/bin/env python3
"""
MCreatorMCP JSON-RPC test harness.

Usage:
    python3 scripts/mcp_test.py list          # tools/list validation (count, dupes, schema sanity)
    python3 scripts/mcp_test.py matrix        # call every advertised tool once, record results
    python3 scripts/mcp_test.py call NAME '{"arg": "val"}'   # call one tool
    python3 scripts/mcp_test.py resources     # resources/list + resources/read smoke test

Environment:
    MCP_URL   plugin endpoint (default http://localhost:5175/mcp)
    MCP_OUT   results file    (default /tmp/mcp_matrix_results.json)
"""

import json
import os
import sys
import urllib.request
import urllib.error

MCP_URL = os.environ.get("MCP_URL", "http://localhost:5175/mcp")
OUT = os.environ.get("MCP_OUT", "/tmp/mcp_matrix_results.json")
_id = [0]


def rpc(method, params=None, timeout=120):
    _id[0] += 1
    payload = {"jsonrpc": "2.0", "method": method, "id": _id[0]}
    if params is not None:
        payload["params"] = params
    req = urllib.request.Request(
        MCP_URL, data=json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode())


def call_tool(name, arguments, timeout=120):
    res = rpc("tools/call", {"name": name, "arguments": arguments}, timeout=timeout)
    if "error" in res:
        return {"status": "rpc_error", "detail": res["error"]}
    result = res.get("result", {})
    text = ""
    for c in result.get("content", []):
        if c.get("type") == "text":
            text += c.get("text", "")
    return {"status": "error" if result.get("isError") else "ok",
            "text": text[:2000]}


def init():
    rpc("initialize", {"protocolVersion": "2025-06-18", "capabilities": {},
                       "clientInfo": {"name": "mcp-test", "version": "1.0"}})
    try:
        rpc("notifications/initialized")
    except Exception:
        pass


def list_tools():
    res = rpc("tools/list")
    return res["result"]["tools"]


def default_args(tool):
    """Synthesize arguments from the tool's required string properties."""
    schema = tool.get("inputSchema") or {}
    args = {}
    req = schema.get("required", [])
    props = schema.get("properties", {})
    for key in req:
        t = props.get(key, {}).get("type", "string")
        if t == "string":
            args[key] = synth_string(key, tool.get("name", "probe"))
        elif t in ("integer", "number"):
            args[key] = 0
        elif t == "boolean":
            args[key] = False
        elif t == "object":
            args[key] = {}
        elif t == "array":
            args[key] = []
    return args


import re as _re

def synth_string(key, seed="probe"):
    k = key.lower()
    if "name" in k:
        return "mp_" + _re.sub(r"[^a-z0-9]+", "_", seed.lower())[:24]
    if "elementtype" in k or k == "type":
        return "block"
    if "path" in k or "folder" in k:
        return "/tmp/mcp_probe"
    if "url" in k:
        return "http://localhost:1/invalid"
    if "xml" in k:
        return '<xml xmlns="https://developers.google.com/blockly/xml"></xml>'
    if "prompt" in k:
        return "test texture"
    if "command" in k:
        return "help"
    if "task" in k:
        return "tasks"
    if "version" in k:
        return "1.0"
    if "content" in k or "json" in k:
        return "{}"
    if "query" in k:
        return "mp_probe"
    return "mp_" + _re.sub(r"[^a-z0-9]+", "_", seed.lower())[:24]


# Tools that must not be invoked generically in the matrix, with reason.
SKIP = {
    "switchGenerator": "switches generator mid-run; covered separately",
    "stopMCreator": "launcher-only tool; not on plugin endpoint",
    "generateTextureFromPrompt": "needs external image API; schema/error path probed separately",
}


def cmd_list():
    tools = list_tools()
    names = [t["name"] for t in tools]
    dupes = sorted(n for n in set(names) if names.count(n) > 1)
    bad = [t["name"] for t in tools
           if not t.get("name") or not t.get("description")
           or not isinstance(t.get("inputSchema"), dict)
           or t["inputSchema"].get("type") != "object"]
    print(f"tool count: {len(tools)}")
    print(f"duplicate names: {dupes or 'none'}")
    print(f"malformed tools: {bad or 'none'}")
    return 0 if not dupes and not bad else 1


def cmd_resources():
    init()
    res = rpc("resources/list")
    resources = res.get("result", {}).get("resources", [])
    print(f"resources: {[r.get('uri') for r in resources]}")
    for r in resources:
        try:
            out = rpc("resources/read", {"uri": r["uri"]})
            ok = "result" in out
            print(f"  read {r['uri']}: {'ok' if ok else out}")
        except Exception as e:
            print(f"  read {r['uri']}: FAIL {e}")
    return 0


def cmd_matrix():
    init()
    tools = list_tools()
    results = {}
    for tool in tools:
        name = tool["name"]
        if name in SKIP:
            results[name] = {"status": "skipped", "detail": SKIP[name]}
            print(f"SKIP {name}: {SKIP[name]}")
            continue
        args = dict(default_args(tool))
        try:
            r = call_tool(name, args)
        except Exception as e:
            r = {"status": "exception", "detail": str(e)}
        r["args"] = args
        results[name] = r
        line = r.get("text") or r.get("detail") or ""
        print(f"{r['status'].upper():9} {name}: {str(line)[:140]}")
    with open(OUT, "w") as f:
        json.dump(results, f, indent=2)
    from collections import Counter
    print("\nsummary:", dict(Counter(v["status"] for v in results.values())))
    print("results written to", OUT)
    return 0


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "list"
    if mode == "list":
        sys.exit(cmd_list())
    elif mode == "resources":
        sys.exit(cmd_resources())
    elif mode == "matrix":
        sys.exit(cmd_matrix())
    elif mode == "call":
        init()
        name = sys.argv[2]
        args = json.loads(sys.argv[3]) if len(sys.argv) > 3 else {}
        print(json.dumps(call_tool(name, args), indent=2))
    else:
        print(__doc__)
        sys.exit(2)
