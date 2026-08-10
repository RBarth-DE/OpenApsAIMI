#!/usr/bin/env python3
"""
Generate AIMI settings path mapping from OpenAPSAIMIPlugin.kt.

Parses the Compose preference screen tree, resolves string resource IDs to
human-readable titles, looks up gate_key dependencies from key enum files,
skips commented-out (orphaned) parameters, and outputs a JSON mapping of
every reachable setting key to its full navigation path.

Usage:
    python generate_settings_paths.py

Output:
    aimi_settings_paths.json
"""

import re
import json
import os
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from collections import OrderedDict

# ---------------------------------------------------------------------------
# Paths (relative to this script or absolute)
# ---------------------------------------------------------------------------
# Auto-detect repo root: when script lives at <repo>/analyzer/,
# the repo root is the parent directory.
_SCRIPT_DIR = Path(__file__).resolve().parent
_AUTO_ROOT  = _SCRIPT_DIR.parent.parent
PROJECT_ROOT = _AUTO_ROOT if (_AUTO_ROOT / "plugins" / "aps").exists() else _SCRIPT_DIR

if not (PROJECT_ROOT / "plugins" / "aps").exists():
    import sys
    print()
    print("\u274c AAPS repo root not found.")
    print(f"   Tried: {PROJECT_ROOT}")
    print("   Run this script from inside the repo or place it at <repo>/analyzer/")
    sys.exit(1)
PLUGIN_FILE = PROJECT_ROOT / "plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/OpenAPSAIMIPlugin.kt"
STRINGS_XML_PATHS = [
    PROJECT_ROOT / "plugins/aps/src/main/res/values/strings.xml",
    PROJECT_ROOT / "core/keys/src/main/res/values/strings.xml",
    PROJECT_ROOT / "core/ui/src/main/res/values/strings.xml",
]
KEY_FILES = {
    "BooleanKey": PROJECT_ROOT / "core/keys/src/main/kotlin/app/aaps/core/keys/BooleanKey.kt",
    "DoubleKey": PROJECT_ROOT / "core/keys/src/main/kotlin/app/aaps/core/keys/DoubleKey.kt",
    "IntKey": PROJECT_ROOT / "core/keys/src/main/kotlin/app/aaps/core/keys/IntKey.kt",
    "StringKey": PROJECT_ROOT / "core/keys/src/main/kotlin/app/aaps/core/keys/StringKey.kt",
    "UnitDoubleKey": PROJECT_ROOT / "core/keys/src/main/kotlin/app/aaps/core/keys/UnitDoubleKey.kt",
    "AimiStringKey": PROJECT_ROOT / "plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/keys/AimiStringKey.kt",
    "ApsIntentKey": PROJECT_ROOT / "plugins/aps/src/main/kotlin/app/aaps/plugins/aps/keys/ApsIntentKey.kt",
}
OUTPUT_FILE = _SCRIPT_DIR.parent / "data" / "aimi_settings_paths.json"
KNOWN_KEY_CLASSES = set(KEY_FILES.keys())


# ---------------------------------------------------------------------------
# 1. Load string resources: R.string.xxx_name -> "Actual Title Text"
# ---------------------------------------------------------------------------
def load_string_resources(paths: list[Path]) -> dict[str, str]:
    resources: dict[str, str] = {}
    for path in paths:
        if not path.exists():
            continue
        content = path.read_text(encoding="utf-8")
        for m in re.finditer(r'<string name="(\w+)">(.*?)</string>', content, re.DOTALL):
            name = m.group(1)
            text = m.group(2).strip()
            # Handle CDATA
            if text.startswith("<![CDATA[") and text.endswith("]]>"):
                text = text[9:-3]
            # Strip HTML-like formatting tags for path display
            text = re.sub(r'<[^>]+>', '', text)
            # Unescape XML entities
            text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("\\'", "'")
            resources[name] = text
    return resources


# ---------------------------------------------------------------------------
# 2. Load key definitions: key string -> {"enum_class": "DoubleKey", "dependency": "key_something"|None}
# ---------------------------------------------------------------------------
def load_key_definitions(key_files: dict[str, Path]) -> dict[str, dict]:
    """Parse each key enum file and extract key-string -> metadata."""
    key_map: dict[str, dict] = {}

    for enum_class, path in key_files.items():
        if not path.exists():
            print(f"  WARNING: key file not found: {path}")
            continue
        content = path.read_text(encoding="utf-8")

        # Strip inline // comments to avoid regex mismatches
        clean_lines = []
        for line in content.split('\n'):
            # Remove // comments but be careful with strings
            in_string = False
            clean = []
            i = 0
            while i < len(line):
                if line[i] == '"' and (i == 0 or line[i-1] != '\\'):
                    in_string = not in_string
                    clean.append('"')
                elif not in_string and line[i:i+2] == '//':
                    break  # rest is comment
                else:
                    clean.append(line[i])
                i += 1
            clean_lines.append(''.join(clean))
        clean_content = '\n'.join(clean_lines)

        # Find all enum entries by scanning for top-level entry patterns
        # Each entry starts with 4-space indent followed by Name(
        for m in re.finditer(r'^\s{4}(\w+)\(', clean_content, re.MULTILINE):
            entry_name = m.group(1)

            # Skip non-entry constructs (like enum class constructor args, companion objects, etc.)
            if entry_name in ('override', 'enum', 'class', 'companion', 'init', 'constructor'):
                continue

            # Extract the full entry body by finding balanced parens
            start_pos = m.end() - 1  # position of '('
            body = _extract_balanced_parens(clean_content, start_pos)
            if body is None:
                continue

            # Extract key = "..." — prefer the named argument, fall back to first string arg
            key_match = re.search(r'key\s*=\s*"([^"]*)"', body)
            if key_match:
                key_str = key_match.group(1)
            else:
                # Try positional: first string literal after '('
                first_str = re.search(r'"([^"]*)"', body)
                if first_str:
                    key_str = first_str.group(1)
                else:
                    # Try constant reference
                    const_match = re.search(r'key\s*=\s*(\w+)', body)
                    if const_match:
                        key_str = const_match.group(1)
                    else:
                        continue  # skip entries without identifiable key

            # Extract dependency = BooleanKey.SomeName
            dep_match = re.search(r'dependency\s*=\s*(\w+)\.(\w+)', body)
            gate_key = None
            if dep_match:
                dep_class = dep_match.group(1)
                dep_name = dep_match.group(2)
                resolved = _resolve_dependency_key(dep_class, dep_name, key_files)
                gate_key = resolved if resolved else dep_name  # fallback to entry name

            key_map[key_str] = {
                "enum_class": enum_class,
                "entry_name": entry_name,
                "gate_key": gate_key,
            }

    return key_map


def _resolve_dependency_key(enum_class: str, entry_name: str, key_files: dict[str, Path]) -> str | None:
    """Given BooleanKey.SomeName, look up the actual key string from the enum file."""
    path = key_files.get(enum_class)
    if not path or not path.exists():
        # Try to find in key_definitions directly later
        return None
    content = path.read_text(encoding="utf-8")
    # Find the enum entry for this name
    pattern = rf'^\s{{4}}{entry_name}\('
    match = re.search(pattern, content, re.MULTILINE)
    if not match:
        return None
    # Find the end of this entry (matching paren)
    start = match.end() - 1  # position of '('
    body = _extract_balanced_parens(content, start)
    if body is None:
        return None
    # Try named param: key = "..."
    key_match = re.search(r'key\s*=\s*"([^"]*)"', body)
    if key_match:
        return key_match.group(1)
    # Try compact form: first string literal (positional arg)
    first_str = re.search(r'"([^"]*)"', body)
    if first_str:
        return first_str.group(1)
    return None


# ---------------------------------------------------------------------------
# 3. Parse OpenAPSAIMIPlugin.kt — extract screen tree
# ---------------------------------------------------------------------------
def parse_plugin_file(filepath: Path) -> list[dict]:
    """Return the top-level screen tree starting from buildAimiComposePreferenceItems()."""
    content = filepath.read_text(encoding="utf-8")

    # First pass: extract all named sub-screen function definitions
    screen_functions = _extract_screen_functions(content)

    # Parse root: buildAimiComposePreferenceItems()
    root_items = _extract_function_items("buildAimiComposePreferenceItems", content, screen_functions)

    # Build the screen tree and resolve all items_raw recursively
    tree = _resolve_items(root_items, screen_functions, content)

    return tree


def _extract_screen_functions(content: str) -> dict[str, dict]:
    """
    Find all `private fun aimiComposeXxxSubScreen(): PreferenceSubScreenDef = ...`
    and return {function_name: {key, titleResId, items_raw}}.
    """
    functions: dict[str, dict] = {}

    # Match function definitions returning PreferenceSubScreenDef
    pattern = r'private fun (\w+)\(\):\s*PreferenceSubScreenDef\s*=\s*\n?\s*PreferenceSubScreenDef\('
    for m in re.finditer(pattern, content):
        # Skip commented-out code
        line_start = content.rfind('\n', 0, m.start()) + 1
        line_prefix = content[line_start:m.start()]
        if '/*' in line_prefix or line_prefix.lstrip().startswith('//'):
            continue
        func_name = m.group(1)

        # Find the matching closing paren for PreferenceSubScreenDef(...)
        start = m.end() - 1  # position of '(' after PreferenceSubScreenDef
        body = _extract_balanced_parens(content, start)
        if body is None:
            continue

        # Extract key, titleResId, items block
        key_match = re.search(r'key\s*=\s*"([^"]*)"', body)
        title_match = re.search(r'titleResId\s*=\s*(\S+)', body)
        items_raw = _extract_items_raw(body)

        functions[func_name] = {
            "key": key_match.group(1) if key_match else func_name,
            "titleResId": title_match.group(1) if title_match else "0",
            "items_raw": items_raw,
        }

    return functions


def _extract_function_items(func_name: str, content: str, screen_functions: dict[str, dict] | None = None) -> list[dict]:
    """
    Extract items from a function body like aimiComposeUserPreferenceItems().
    Returns list of {type, ...} dicts.
    """
    if screen_functions is None:
        screen_functions = {}
    # Find the function body (skip commented-out code)
    pattern = rf'private fun {func_name}\([^)]*\):\s*\S+\s*=\s*buildList\s*\{{'
    m = None
    for match in re.finditer(pattern, content):
        line_start = content.rfind('\n', 0, match.start()) + 1
        line_prefix = content[line_start:match.start()]
        if '/*' in line_prefix or line_prefix.lstrip().startswith('//'):
            continue
        m = match
        break
    if not m:
        return []

    start = m.end() - 1  # position of '{' in buildList {
    body = _extract_balanced_braces(content, start)
    if body is None:
        return []

    return _parse_items_block(body, screen_functions)


def _parse_items_block(body: str, screen_functions: dict[str, dict]) -> list[dict]:
    """
    Parse the contents of an items block (inside listOf(...) or buildList { ... }).
    Uses character-based scanning to handle multi-line constructs.
    Returns a list of parsed items.
    """
    items = []
    pos = 0
    n = len(body)

    while pos < n:
        # Skip whitespace
        while pos < n and body[pos] in ' \t\n\r,':
            pos += 1
        if pos >= n:
            break

        # Check if this line/position is a comment
        # Find the start of the current line
        line_start = body.rfind('\n', 0, pos) + 1
        line_end = body.find('\n', pos)
        if line_end == -1:
            line_end = n
        current_line = body[line_start:line_end].lstrip()
        if current_line.startswith('//'):
            # Skip entire comment line
            pos = line_end + 1 if line_end < n else n
            continue

        # --- add( ... ) ---
        if body.startswith('add(', pos):
            add_start = pos + 4  # after 'add('
            add_body = _extract_balanced_parens(body, pos + 3)
            if add_body is None:
                pos += 1
                continue
            pos = pos + 4 + len(add_body) + 1  # skip past add(...)

            # Classify what's inside add(...)
            add_stripped = add_body.strip()

            # add(someSubScreenFunction())
            func_call = re.match(r'^(\w+)\(\)$', add_stripped)
            if func_call:
                func_name = func_call.group(1)
                if func_name in screen_functions:
                    sf = screen_functions[func_name]
                    sub_items = _parse_items_block(sf["items_raw"], screen_functions)
                    items.append({
                        "type": "screen",
                        "key": sf["key"],
                        "titleResId": sf["titleResId"],
                        "summaryResId": None,
                        "items_raw": sf["items_raw"],
                        "items": sub_items,
                    })
                else:
                    items.append({"type": "screen_ref", "function": func_name})
                continue

            # add(PreferenceSubScreenDef(...))
            if add_stripped.startswith('PreferenceSubScreenDef('):
                inner_start = add_stripped.find('(')
                inner = _extract_balanced_parens(add_stripped, inner_start)
                if inner:
                    key_match = re.search(r'key\s*=\s*"([^"]*)"', inner)
                    title_match = re.search(r'titleResId\s*=\s*(\S+)', inner)
                    sub_items_raw = _extract_items_raw(inner)
                    items.append({
                        "type": "screen",
                        "key": key_match.group(1) if key_match else "unknown",
                        "titleResId": title_match.group(1) if title_match else "0",
                        "summaryResId": None,
                        "items_raw": sub_items_raw,
                    })
                continue

            # add(XxxKey.YYY.withCompose(...)) or add(XxxKey.YYY.withEntries(...))
            key_wrapper = re.match(
                r'^(\w+)\.(\w+)\.(withCompose|withEntries)\(',
                add_stripped
            )
            if key_wrapper:
                items.append({
                    "type": "key",
                    "key_class": key_wrapper.group(1),
                    "key_name": key_wrapper.group(2),
                })
                continue

            # add(XxxKey.YYY)
            key_add = re.match(r'^(\w+)\.(\w+)$', add_stripped)
            if key_add:
                items.append({
                    "type": "key",
                    "key_class": key_add.group(1),
                    "key_name": key_add.group(2),
                })
                continue

            continue

        # --- PreferenceSubScreenDef(...) (inline, not inside add()) ---
        if body.startswith('PreferenceSubScreenDef(', pos):
            paren_pos = pos + len('PreferenceSubScreenDef')
            inner = _extract_balanced_parens(body, paren_pos)
            if inner:
                key_match = re.search(r'key\s*=\s*"([^"]*)"', inner)
                title_match = re.search(r'titleResId\s*=\s*(\S+)', inner)
                items_match = re.search(r'items\s*=\s*(.*)', inner, re.DOTALL)
                sub_items_raw = items_match.group(1).strip() if items_match else ""
                items.append({
                    "type": "screen",
                    "key": key_match.group(1) if key_match else "unknown",
                    "titleResId": title_match.group(1) if title_match else "0",
                    "summaryResId": None,
                    "items_raw": sub_items_raw,
                })
                pos = paren_pos + len(inner) + 2
            else:
                pos += 1
            continue

        # --- Direct key with wrapper: XxxKey.YYY.withCompose(...) or .withEntries(...) ---
        direct_wrapper = re.match(
            r'(\w+)\.(\w+)\.(withCompose|withEntries)\(',
            body[pos:]
        )
        if direct_wrapper:
            wrapper_start = pos + direct_wrapper.end() - 1  # '(' after wrapper
            wrapper_body = _extract_balanced_parens(body, wrapper_start)
            if wrapper_body:
                pos = wrapper_start + len(wrapper_body) + 2
            else:
                pos += direct_wrapper.end()
            items.append({
                "type": "key",
                "key_class": direct_wrapper.group(1),
                "key_name": direct_wrapper.group(2),
            })
            continue

        # --- Direct key: XxxKey.YYY ---
        direct_key = re.match(r'(\w+)\.(\w+)', body[pos:])
        if direct_key:
            key_class = direct_key.group(1)
            if key_class in KNOWN_KEY_CLASSES:
                end_pos = pos + direct_key.end()
                # Check this isn't just part of a longer expression (like a method call)
                rest = body[end_pos:end_pos+1] if end_pos < n else ''
                if rest in ('', ',', '\n', ' ', '\t', '\r', ')'):
                    items.append({
                        "type": "key",
                        "key_class": key_class,
                        "key_name": direct_key.group(2),
                    })
                    pos = end_pos
                    continue

        # --- Sub-screen call: functionName(), ---
        sub_call = re.match(r'(\w+)\(\)', body[pos:])
        if sub_call:
            func_name = sub_call.group(1)
            end_pos = pos + sub_call.end()
            rest = body[end_pos:end_pos+1] if end_pos < n else ''
            if rest in ('', ',', '\n', ' ', '\t', '\r', ')'):
                if func_name in screen_functions:
                    sf = screen_functions[func_name]
                    sub_items = _parse_items_block(sf["items_raw"], screen_functions)
                    items.append({
                        "type": "screen",
                        "key": sf["key"],
                        "titleResId": sf["titleResId"],
                        "summaryResId": None,
                        "items_raw": sf["items_raw"],
                        "items": sub_items,
                    })
                else:
                    items.append({"type": "screen_ref", "function": func_name})
                pos = end_pos
                continue

        # Couldn't parse — advance
        pos += 1

    return items


def _resolve_items(
    items: list[dict],
    screen_functions: dict[str, dict],
    content: str,
) -> list[dict]:
    """Recursively resolve items, expanding screen references and parsing inline items_raw."""
    resolved = []
    for item in items:
        if item["type"] == "screen" and "items_raw" in item and "items" not in item:
            raw = item["items_raw"]
            # Check if items_raw is a function call like aimiComposeUserPreferenceItems()
            func_ref = re.match(r'^(\w+)\(\),?\s*$', raw.strip())
            if func_ref:
                func_items = _extract_function_items(func_ref.group(1), content, screen_functions)
                if func_items:
                    item["items"] = _resolve_items(func_items, screen_functions, content)
            if "items" not in item:
                # Parse the raw items block normally
                sub = _parse_items_block(raw, screen_functions)
                item["items"] = _resolve_items(sub, screen_functions, content)
        elif item["type"] == "screen" and "items" in item:
            item["items"] = _resolve_items(item["items"], screen_functions, content)
        elif item["type"] == "screen_ref":
            # Try to resolve the screen reference
            func_name = item["function"]
            if func_name in screen_functions:
                sf = screen_functions[func_name]
                sub = _parse_items_block(sf["items_raw"], screen_functions)
                resolved.append({
                    "type": "screen",
                    "key": sf["key"],
                    "titleResId": sf["titleResId"],
                    "summaryResId": None,
                    "items_raw": sf["items_raw"],
                    "items": _resolve_items(sub, screen_functions, content),
                })
                continue
        resolved.append(item)
    return resolved


# ---------------------------------------------------------------------------
# Helper: extract balanced parentheses/braces
# ---------------------------------------------------------------------------
def _extract_balanced_parens(text: str, start_pos: int) -> str | None:
    """Extract content inside balanced parentheses starting at start_pos.
    start_pos should point to the opening '('."""
    depth = 1
    i = start_pos + 1
    while i < len(text) and depth > 0:
        if text[i] == '(':
            depth += 1
        elif text[i] == ')':
            depth -= 1
        i += 1
    if depth != 0:
        return None
    return text[start_pos + 1:i - 1]


def _extract_balanced_braces(text: str, start_pos: int) -> str | None:
    """Extract content inside balanced curly braces starting at start_pos.
    start_pos should point to the opening '{'."""
    depth = 1
    i = start_pos + 1
    while i < len(text) and depth > 0:
        if text[i] == '{':
            depth += 1
        elif text[i] == '}':
            depth -= 1
        i += 1
    if depth != 0:
        return None
    return text[start_pos + 1:i - 1]


def _skip_past_block(lines: list[str], start_idx: int, block_text: str) -> int:
    """Given a block of text, skip past all lines it spans."""
    newline_count = block_text.count('\n')
    return start_idx + newline_count + 1


def _extract_items_raw(inner: str) -> str:
    """Extract the raw items content from an 'items = ...' value.
    Unwraps buildList { ... }, listOf(...), or returns the raw function call.
    """
    items_match = re.search(r'items\s*=\s*(.*)', inner, re.DOTALL)
    if not items_match:
        return ""
    items_val = items_match.group(1).strip()

    # Unwrap buildList { ... }
    if items_val.startswith('buildList'):
        brace_pos = items_val.find('{')
        if brace_pos != -1:
            brace_body = _extract_balanced_braces(items_val, brace_pos)
            if brace_body:
                return brace_body.strip()

    # Unwrap listOf(...)
    if items_val.startswith('listOf('):
        paren_body = _extract_balanced_parens(items_val, items_val.find('('))
        if paren_body:
            return paren_body.strip()

    # It's a function call or reference — return as-is
    return items_val


# ---------------------------------------------------------------------------
# 4. Build paths from the screen tree
# ---------------------------------------------------------------------------
def build_paths(
    tree: list[dict],
    string_resources: dict[str, str],
    key_definitions: dict[str, dict],
) -> dict[str, dict]:
    """Recursively traverse the screen tree and build path entries."""
    paths: dict[str, dict] = OrderedDict()
    unresolved: list[str] = []

    def _resolve_title(title_res_id: str) -> str:
        """Resolve R.string.xxx -> text, R.xxx.xxx -> text, or int -> fallback."""
        rid = title_res_id.strip()
        # R.string.xxx
        m = re.match(r'R\.string\.(\w+)', rid)
        if m:
            return string_resources.get(m.group(1), m.group(1))
        # app.aaps.core.ui.R.string.xxx
        m = re.match(r'app\.aaps\.\w+\.\w+\.R\.string\.(\w+)', rid)
        if m:
            return string_resources.get(m.group(1), m.group(1))
        # app.aaps.core.keys.R.string.xxx
        m = re.match(r'app\.aaps\.core\.keys\.R\.string\.(\w+)', rid)
        if m:
            return string_resources.get(m.group(1), m.group(1))
        # CoreKeysR.string.xxx
        m = re.match(r'CoreKeysR\.string\.(\w+)', rid)
        if m:
            return string_resources.get(m.group(1), m.group(1))
        # Integer resource ID (e.g., app.aaps.core.ui.R.string.something)
        return rid

    def _resolve_key_string(key_class: str, key_name: str) -> str | None:
        """Resolve a (key_class, key_name) to the actual key string."""
        # Only process known key classes
        if key_class not in KNOWN_KEY_CLASSES:
            return None
        # Check key_definitions by entry_name
        for kstr, kdef in key_definitions.items():
            if kdef.get("enum_class") == key_class and kdef.get("entry_name") == key_name:
                return kstr
        # Not found — log and skip
        unresolved.append(f"{key_class}.{key_name}")
        return None

    def _traverse(items: list[dict], screen_titles: list[str]):
        for item in items:
            if item["type"] == "key":
                key_class = item.get("key_class", "")
                key_name = item.get("key_name", "")
                key_str = _resolve_key_string(key_class, key_name)
                if key_str:
                    gate_key = key_definitions.get(key_str, {}).get("gate_key")
                    paths[key_str] = {
                        "path": " → ".join(screen_titles),
                        "screen_titles": list(screen_titles),
                        "gate_key": gate_key,
                    }

            elif item["type"] == "screen":
                title = _resolve_title(item.get("titleResId", "0"))
                if not title or title == "0":
                    title = item.get("key", "Unknown")
                sub_items = item.get("items", [])
                _traverse(sub_items, screen_titles + [title])

            elif item["type"] == "screen_ref":
                # Unresolved screen reference — skip
                pass

    _traverse(tree, ["AIMI"])
    return dict(paths), unresolved


# ---------------------------------------------------------------------------
# 5. Main
# ---------------------------------------------------------------------------
def main():
    print("=== AIMI Settings Path Generator ===\n")

    # Get git commit
    try:
        commit = subprocess.check_output(
            ["git", "-C", str(PROJECT_ROOT), "rev-parse", "HEAD"],
            text=True
        ).strip()
    except Exception:
        commit = "unknown"

    # Step 1: Load string resources
    print("1. Loading string resources...")
    strings = load_string_resources(STRINGS_XML_PATHS)
    print(f"   Found {len(strings)} string resources")

    # Step 2: Load key definitions
    print("2. Loading key definitions...")
    keys = load_key_definitions(KEY_FILES)
    gated = sum(1 for v in keys.values() if v.get("gate_key"))
    print(f"   Found {len(keys)} key definitions ({gated} with gate_key)")

    # Step 3: Parse plugin file
    print("3. Parsing OpenAPSAIMIPlugin.kt...")
    tree = parse_plugin_file(PLUGIN_FILE)
    print(f"   Root has {len(tree)} top-level screens")

    # Step 4: Build paths
    print("4. Building path mapping...")
    paths, unresolved = build_paths(tree, strings, keys)
    print(f"   Generated {len(paths)} paths")
    if unresolved:
        print(f"   Unresolved keys: {len(unresolved)}")
        for u in sorted(set(unresolved))[:10]:
            print(f"     - {u}")

    # Step 5: Validate — report on keys found
    print("5. Key coverage summary:")
    key_types_found = {}
    for kstr, pinfo in paths.items():
        kcls = keys.get(kstr, {}).get("enum_class", "unknown")
        key_types_found[kcls] = key_types_found.get(kcls, 0) + 1
    for kcls, count in sorted(key_types_found.items()):
        print(f"   {kcls}: {count}")

    # Build output
    output = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "source_commit": commit,
        "paths": paths,
    }

    # Write JSON
    with open(OUTPUT_FILE, 'w', encoding='utf-8') as f:
        json.dump(output, f, indent=2, ensure_ascii=False)
    print(f"\n✅ Written {OUTPUT_FILE} ({len(paths)} paths)")
    print(f"   Source commit: {commit[:8]}")


if __name__ == "__main__":
    main()
