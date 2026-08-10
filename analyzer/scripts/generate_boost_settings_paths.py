#!/usr/bin/env python3
"""
Generate Boost settings path mapping from OpenAPSBoostPlugin.kt.

Parses the Compose preference screen tree, resolves string resource IDs to
human-readable titles, and outputs a JSON mapping of every reachable setting
key to its full navigation path.

Usage:
    python generate_boost_settings_paths.py

Output:
    data/boost_settings_paths.json
"""

import re
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path

# ─── Paths ───────────────────────────────────────────────────────────────────
_SCRIPT_DIR = Path(__file__).resolve().parent
_AUTO_ROOT  = _SCRIPT_DIR.parent.parent
PROJECT_ROOT = _AUTO_ROOT if (_AUTO_ROOT / "plugins" / "aps").exists() else _SCRIPT_DIR

if not (PROJECT_ROOT / "plugins" / "aps").exists():
    import sys
    print()
    print("❌ AAPS repo root not found.")
    print(f"   Tried: {PROJECT_ROOT}")
    sys.exit(1)

PLUGIN_FILE = PROJECT_ROOT / "plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSBoost/OpenAPSBoostPlugin.kt"
PLUGIN_FILE_V5 = PROJECT_ROOT / "plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSBoostV5/OpenAPSBoostV5Plugin.kt"
STRINGS_XML_PATHS = [
    PROJECT_ROOT / "plugins/aps/src/main/res/values/strings.xml",
    PROJECT_ROOT / "core/keys/src/main/res/values/strings.xml",
    PROJECT_ROOT / "core/ui/src/main/res/values/strings.xml",
]
OUTPUT_FILE = _SCRIPT_DIR.parent / "data" / "boost_settings_paths.json"

KEY_FILES = {
    "DoubleKey": PROJECT_ROOT / "core/keys/src/main/kotlin/app/aaps/core/keys/DoubleKey.kt",
    "BooleanKey": PROJECT_ROOT / "core/keys/src/main/kotlin/app/aaps/core/keys/BooleanKey.kt",
    "IntKey": PROJECT_ROOT / "core/keys/src/main/kotlin/app/aaps/core/keys/IntKey.kt",
    "UnitDoubleKey": PROJECT_ROOT / "core/keys/src/main/kotlin/app/aaps/core/keys/UnitDoubleKey.kt",
    "LongKey": PROJECT_ROOT / "core/keys/src/main/kotlin/app/aaps/core/keys/LongKey.kt",
    "StringKey": PROJECT_ROOT / "core/keys/src/main/kotlin/app/aaps/core/keys/StringKey.kt",
}

# Build lookup: key_class.entry_name → key_string (e.g., "DoubleKey.ApsBoostBolus" → "boost_bolus_cap")
_key_lookup_cache: dict[str, str] = {}


def _build_key_lookup():
    """Parse all key enum files and return class.entry_name → key_string."""
    global _key_lookup_cache
    if _key_lookup_cache:
        return

    for key_class, filepath in KEY_FILES.items():
        if not filepath.exists():
            continue
        content = filepath.read_text(encoding="utf-8")
        for match in re.finditer(r'(\w+)\s*\(\s*(?:key\s*=\s*)?\"([^\"]+)\"', content):
            entry_name = match.group(1)
            if entry_name in ('BooleanKey', 'DoubleKey', 'IntKey', 'UnitDoubleKey',
                              'LongKey', 'StringKey', 'override'):
                continue
            key_str = match.group(2)
            _key_lookup_cache[f"{key_class}.{entry_name}"] = key_str


def resolve_key_str(key_class: str, key_name: str) -> str | None:
    """Resolve a key class.entry_name to the actual key string."""
    return _key_lookup_cache.get(f"{key_class}.{key_name}")


def load_string_resources(paths: list[Path]) -> dict[str, str]:
    resources: dict[str, str] = {}
    for path in paths:
        if not path.exists():
            continue
        content = path.read_text(encoding="utf-8")
        for m in re.finditer(r'<string name="(\w+)">(.*?)</string>', content, re.DOTALL):
            name = m.group(1)
            text = m.group(2).strip()
            if text.startswith("<![CDATA[") and text.endswith("]]>"):
                text = text[9:-3]
            text = re.sub(r'<[^>]+>', '', text)
            text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("\\'", "'")
            resources[name] = text
    return resources


def resolve_title(rid_expr: str, strings: dict[str, str]) -> str:
    """Resolve R.string.xxx, app.aaps.core.ui.R.string.xxx etc. to title text."""
    match = re.search(r'R\.string\.(\w+)', rid_expr)
    if match:
        return strings.get(match.group(1), match.group(1))
    return rid_expr.strip()


def parse_preference_screen(lines: list[str], start_idx: int, strings: dict,
                            paths: dict, path_stack: list[str]) -> int:
    """Parse a PreferenceSubScreenDef block. Returns index after the closing paren."""
    i = start_idx
    # Find the opening of the block
    while i < len(lines):
        stripped = lines[i].strip()
        if 'PreferenceSubScreenDef(' in stripped or stripped.startswith('app.aaps.core.ui.compose.preference.PreferenceSubScreenDef('):
            break
        i += 1

    # Extract title
    title = "BOOST"
    while i < len(lines):
        title_match = re.search(r'titleResId\s*=\s*([\w.]+)', lines[i])
        if title_match:
            title = resolve_title(title_match.group(1), strings)
            break
        i += 1

    # Process items in this screen
    j = i
    depth = 0
    in_items = False
    items_depth = 0
    current_path = path_stack + [title]

    while j < len(lines):
        stripped = lines[j].strip()
        # Skip comments
        if stripped.startswith('//') or stripped.startswith('/*'):
            j += 1
            continue

        # Track when we enter/exit items = listOf( or items = buildList {
        if 'items' in stripped and 'listOf(' in stripped:
            in_items = True
            items_depth = 1
            j += 1
            continue
        if 'items' in stripped and 'buildList' in stripped:
            in_items = True
            items_depth = 1
            j += 1
            continue

        # Track nested PreferenceSubScreenDef
        if 'PreferenceSubScreenDef(' in stripped:
            # Parse sub-screen recursively
            j = parse_sub_screen_inline(lines, j, strings, paths, current_path)
            continue

        # Key reference: XxxKey.YyyName,
        key_match = re.match(r'\s*(\w+)\.(\w+),?\s*$', stripped)
        if key_match and in_items:
            key_class = key_match.group(1)
            key_name = key_match.group(2)
            key_str = resolve_key_str(key_class, key_name)
            if key_str:
                paths[key_str] = {
                    "path": " → ".join(current_path),
                    "screen_titles": list(current_path),
                }

        # Track depth in items list
        if in_items:
            items_depth += stripped.count('(') - stripped.count(')')
            items_depth += stripped.count('{') - stripped.count('}')
            if items_depth <= 0:
                in_items = False

        # Track depth in screen block
        depth += stripped.count('(') - stripped.count(')')
        if depth < 0:
            break

        j += 1

    return j


def parse_sub_screen_inline(lines: list[str], start_idx: int, strings: dict,
                            paths: dict, parent_path: list[str]) -> int:
    """Parse an inline PreferenceSubScreenDef and return index after it."""
    i = start_idx
    title = "Unknown"

    # Find title in this block
    j = i
    while j < len(lines) and j < i + 5:
        title_match = re.search(r'titleResId\s*=\s*([\w.]+)', lines[j])
        if title_match:
            title = resolve_title(title_match.group(1), strings)
            break
        j += 1

    current_path = parent_path + [title]

    # Process keys inside this sub-screen
    in_items = False
    items_depth = 0
    end_idx = i
    while end_idx < len(lines):
        stripped = lines[end_idx].strip()

        if 'items' in stripped and 'listOf(' in stripped:
            in_items = True
            items_depth = 1
            end_idx += 1
            continue

        # Key reference
        key_match = re.match(r'\s*(\w+)\.(\w+),?\s*$', stripped)
        if key_match and in_items:
            key_class = key_match.group(1)
            key_name = key_match.group(2)
            key_str = resolve_key_str(key_class, key_name)
            if key_str:
                paths[key_str] = {
                    "path": " → ".join(current_path),
                    "screen_titles": list(current_path),
                }

        # Track nested PreferenceSubScreenDef (skip the current screen's own opening line)
        if end_idx > i and 'PreferenceSubScreenDef(' in stripped:
            end_idx = parse_sub_screen_inline(lines, end_idx, strings, paths, current_path)
            continue

        if in_items:
            items_depth += stripped.count('(') - stripped.count(')')
            items_depth += stripped.count('{') - stripped.count('}')
            if items_depth <= 0:
                # Items list ended — skip forward to consume this sub-screen's closing ), too
                end_idx += 1
                while end_idx < len(lines):
                    s = lines[end_idx].strip()
                    if s == '),' or s == ')' or s.startswith('),'):
                        end_idx += 1
                        break
                    end_idx += 1
                break

        # Check if this line closes the sub-screen itself (fallback for screens without items list)
        if stripped == '),' or stripped == ')' or stripped.startswith('),'):
            end_idx += 1
            break

        end_idx += 1

    return end_idx


def main():
    print("=== Boost Settings Path Generator ===\n")

    commit = "unknown"
    try:
        commit = subprocess.check_output(
            ["git", "-C", str(PROJECT_ROOT), "rev-parse", "HEAD"],
            text=True
        ).strip()
    except Exception:
        pass

    print("1. Building key lookup...")
    _build_key_lookup()
    print(f"   Built {len(_key_lookup_cache)} key refs")

    print("2. Loading string resources...")
    strings = load_string_resources(STRINGS_XML_PATHS)
    print(f"   Found {len(strings)} string resources")

    print("3. Parsing OpenAPSBoostPlugin.kt (V1)...")
    paths: dict[str, dict] = {}

    # Parse V1 plugin (flat "Boost" screen)
    content = PLUGIN_FILE.read_text(encoding="utf-8")
    lines = content.split('\n')
    func_start = -1
    for idx, line in enumerate(lines):
        if 'override fun getPreferenceScreenContent()' in line:
            func_start = idx
            break
    if func_start >= 0:
        parse_preference_screen(lines, func_start, strings, paths, [])
        print(f"   V1 generated {len(paths)} paths")

    # Parse V5/V6 plugin (nested "Boost V6" screen — the user-facing plugin)
    if PLUGIN_FILE_V5.exists():
        print("4. Parsing OpenAPSBoostV5Plugin.kt (V6)...")
        content_v5 = PLUGIN_FILE_V5.read_text(encoding="utf-8")
        lines_v5 = content_v5.split('\n')
        func_start_v5 = -1
        for idx, line in enumerate(lines_v5):
            if 'override fun getPreferenceScreenContent()' in line:
                func_start_v5 = idx
                break
        if func_start_v5 >= 0:
            v5_paths: dict[str, dict] = {}
            parse_preference_screen(lines_v5, func_start_v5, strings, v5_paths, [])
            # Merge: V5 takes priority over V1 for keys that appear in both
            v5_before = len(paths)
            paths.update(v5_paths)
            print(f"   V6 generated {len(v5_paths)} paths, merged total {len(paths)} paths")
        else:
            print("   WARNING: Could not find getPreferenceScreenContent() in V5 plugin")
    else:
        print("4. Skipping V5 plugin (file not found)")

    print(f"\n   Total paths: {len(paths)}")

    # Show results
    for k, v in sorted(paths.items()):
        print(f"   {k} → {v['path']}")

    output = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source_commit": commit,
        "paths": paths,
    }

    OUTPUT_FILE.parent.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2, ensure_ascii=False)
    print(f"\n✅ Written {OUTPUT_FILE} ({len(paths)} paths)")


if __name__ == "__main__":
    main()
