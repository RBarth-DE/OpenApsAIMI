#!/usr/bin/env python3
"""Generate AutoISF settings path mapping from OpenAPSAutoISFPlugin.kt."""

import re, json, subprocess
from datetime import datetime, timezone
from pathlib import Path

_SCRIPT_DIR = Path(__file__).resolve().parent
_AUTO_ROOT = _SCRIPT_DIR.parent.parent
PROJECT_ROOT = _AUTO_ROOT if (_AUTO_ROOT / "plugins" / "aps").exists() else _SCRIPT_DIR
PLUGIN_FILE = PROJECT_ROOT / "plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAutoISF/OpenAPSAutoISFPlugin.kt"
STRINGS_XML_PATHS = [
    PROJECT_ROOT / "plugins/aps/src/main/res/values/strings.xml",
    PROJECT_ROOT / "core/keys/src/main/res/values/strings.xml",
    PROJECT_ROOT / "core/ui/src/main/res/values/strings.xml",
]
OUTPUT_FILE = _SCRIPT_DIR.parent / "data" / "autoisf_settings_paths.json"
KEY_FILES = {
    "DoubleKey": PROJECT_ROOT / "core/keys/src/main/kotlin/app/aaps/core/keys/DoubleKey.kt",
    "BooleanKey": PROJECT_ROOT / "core/keys/src/main/kotlin/app/aaps/core/keys/BooleanKey.kt",
    "IntKey": PROJECT_ROOT / "core/keys/src/main/kotlin/app/aaps/core/keys/IntKey.kt",
    "UnitDoubleKey": PROJECT_ROOT / "core/keys/src/main/kotlin/app/aaps/core/keys/UnitDoubleKey.kt",
    "StringKey": PROJECT_ROOT / "core/keys/src/main/kotlin/app/aaps/core/keys/StringKey.kt",
}

_key_lookup: dict[str, str] = {}

def build_key_lookup():
    global _key_lookup
    if _key_lookup: return
    for kcls, fp in KEY_FILES.items():
        if not fp.exists(): continue
        for m in re.finditer(r'(\w+)\s*\(\s*(?:key\s*=\s*)?\"([^\"]+)\"', fp.read_text(encoding="utf-8")):
            ename = m.group(1)
            if ename in ('BooleanKey','DoubleKey','IntKey','UnitDoubleKey','LongKey','StringKey','override'): continue
            _key_lookup[f"{kcls}.{ename}"] = m.group(2)

def resolve_key(kcls: str, kname: str) -> str|None:
    return _key_lookup.get(f"{kcls}.{kname}")

def load_strings(paths: list[Path]) -> dict[str, str]:
    res = {}
    for p in paths:
        if not p.exists(): continue
        for m in re.finditer(r'<string name="(\w+)">(.*?)</string>', p.read_text(encoding="utf-8"), re.DOTALL):
            t = m.group(2).strip()
            if t.startswith("<![CDATA[") and t.endswith("]]>"): t = t[9:-3]
            t = re.sub(r'<[^>]+>', '', t)
            t = t.replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").replace("\\'","'")
            res[m.group(1)] = t
    return res

def resolve_title(rid: str, strings: dict) -> str:
    m = re.search(r'R\.string\.(\w+)', rid)
    if m: return strings.get(m.group(1), m.group(1))
    return rid.strip()


def parse_screen(lines: list[str], start_idx: int, strings: dict, paths: dict, pstack: list[str]) -> int:
    """Parse a PreferenceSubScreenDef block, return line after closing."""
    # Extract title
    title = "AutoISF"
    i = start_idx
    while i < len(lines):
        tm = re.search(r'titleResId\s*=\s*([\w.]+)', lines[i])
        if tm:
            title = resolve_title(tm.group(1), strings)
            break
        i += 1

    cpath = pstack + [title]

    # Walk through lines
    j = i
    in_items = False
    idepth = 0
    while j < len(lines):
        s = lines[j].strip()
        if s.startswith('//') or s.startswith('/*'):
            j += 1; continue

        if 'items' in s and ('listOf(' in s or 'buildList' in s):
            in_items = True
            idepth = 1
            j += 1; continue

        # Sub-screen
        if 'PreferenceSubScreenDef(' in s:
            j = parse_screen(lines, j, strings, paths, cpath)
            continue

        # Key: XxxKey.Yyy,
        km = re.match(r'\s*(\w+)\.(\w+),?\s*$', s)
        if km and in_items:
            ks = resolve_key(km.group(1), km.group(2))
            if ks:
                paths[ks] = {"path": " → ".join(cpath), "screen_titles": list(cpath)}

        # Items depth tracking
        if in_items:
            idepth += s.count('(') - s.count(')')
            idepth += s.count('{') - s.count('}')
            if idepth <= 0:
                break

        j += 1
    return j + 1


def main():
    print("=== AutoISF Settings Path Generator ===\n")
    commit = "unknown"
    try: commit = subprocess.check_output(["git","-C",str(PROJECT_ROOT),"rev-parse","HEAD"],text=True).strip()
    except: pass

    build_key_lookup()
    print(f"1. Built {len(_key_lookup)} key refs")
    strings = load_strings(STRINGS_XML_PATHS)
    print(f"2. Loaded {len(strings)} strings")

    content = PLUGIN_FILE.read_text(encoding="utf-8")
    lines = content.split('\n')

    func_start = -1
    for idx, line in enumerate(lines):
        if 'override fun getPreferenceScreenContent()' in line:
            func_start = idx; break

    if func_start < 0:
        print("ERROR: Could not find getPreferenceScreenContent()")
        return 1

    paths: dict[str, dict] = {}
    parse_screen(lines, func_start, strings, paths, [])

    print(f"3. Generated {len(paths)} paths")
    for k, v in sorted(paths.items()):
        print(f"   {k} → {v['path']}")

    output = {"generated_at": datetime.now(timezone.utc).isoformat(), "source_commit": commit, "paths": paths}
    OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2, ensure_ascii=False)
    print(f"\n✅ {OUTPUT_FILE} ({len(paths)} paths)")


if __name__ == "__main__":
    main()
