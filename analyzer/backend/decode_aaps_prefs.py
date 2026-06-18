#!/usr/bin/env python3
"""
AAPS Preferences Export Decoder
================================
Entschlüsselt AAPS-Einstellungsexporte (.json) mit dem Masterpasswort.

AAPS-Verschlüsselungsformat (AAPS 3.x / 4.x):
  - PBKDF2WithHmacSHA1, 65536 Iterationen, 256-bit Key
  - AES/GCM/NoPadding
  - IV (12 Bytes) ist dem Ciphertext vorangestellt
  - Outer-JSON: { "metadata": {...}, "security": {"salt": "<base64>"}, "content": "<base64(iv+ct)>" }

Usage:
  python3 decode_aaps_prefs.py settings.json [password]
  python3 decode_aaps_prefs.py settings.json           # fragt nach Passwort
"""

import sys
import json
import base64
import getpass
import argparse
from pathlib import Path

try:
    from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    from cryptography.hazmat.backends import default_backend
    from cryptography.exceptions import InvalidTag
except ImportError:
    print("ERROR: cryptography library fehlt. Installieren mit:")
    print("  pip install cryptography")
    sys.exit(1)


# ─── Known AIMI Parameter Keys (für gezielte Extraktion) ─────────────────────
AIMI_KEY_PREFIXES = [
    "key_openapsaimi", "key_aimi", "aimi_", "OApsAIMI",
    "key_oaps_aimi", "key_use_Aimi", "key_prebolus",
    "key_prebolus2", "key_prebolussmall", "key_combinedDelta",
    "key_mindeviation", "key_Acceleration", "key_enable_basal",
    "key_enable_ML", "key_cho", "key_aimiweight", "key_tdd7",
    "count_steps_watch", "AIMI_UAM", "oa_aimi_", "key_wcycle",
    "key_use_AimiPregnancy", "key_use_Aimi_honeymoon",
]


def is_aimi_key(key: str) -> bool:
    return any(key.startswith(p) or p.lower() in key.lower() for p in AIMI_KEY_PREFIXES)


def derive_key(password: str, salt_hex: str, iterations: int = 50000) -> bytes:
    """PBKDF2WithHmacSHA1 → 256-bit AES key. Salt ist HEX-String (wie in AAPS)."""
    salt = bytes.fromhex(salt_hex)   # hexStringToByteArray()
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA1(),
        length=32,
        salt=salt,
        iterations=iterations,
        backend=default_backend()
    )
    return kdf.derive(password.encode("utf-8"))


def decrypt_content(key: bytes, content_b64: str) -> bytes:
    """AES-GCM decrypt.
    Buffer-Format (wie in AAPS CryptoUtil.encrypt):
      Byte 0:          iv_length (immer 12)
      Byte 1..12:      IV
      Byte 13..:       Ciphertext + 128-bit GCM Tag
    """
    raw = base64.b64decode(content_b64)
    iv_length = raw[0]                      # erstes Byte = IV-Länge
    iv = raw[1:1 + iv_length]              # IV
    ct = raw[1 + iv_length:]               # Ciphertext + Tag
    aesgcm = AESGCM(key)
    return aesgcm.decrypt(iv, ct, None)


def load_and_decrypt(filepath: Path, password: str) -> dict:
    """Load an AAPS preferences export file and decrypt it."""
    with open(filepath, encoding="utf-8") as f:
        outer = json.load(f)

    # Validate structure
    if "content" not in outer:
        raise ValueError("Keine 'content'-Feld — ist das eine AAPS-Export-Datei?")

    security = outer.get("security", {})
    salt_hex = security.get("salt")        # Hex-String in AAPS
    iterations = security.get("iterations", 50000)  # AAPS default = 50000

    if not salt_hex:
        raise ValueError("Kein 'salt' in security-Feld — unbekanntes Format.")

    if security.get("algorithm") != "v1":
        algo = security.get("algorithm", "unbekannt")
        raise ValueError(f"Unbekannter Algorithmus: '{algo}'. Nur 'v1' unterstützt.")

    key = derive_key(password, salt_hex, iterations)
    try:
        plaintext = decrypt_content(key, outer["content"])
    except InvalidTag:
        raise ValueError("❌ Falsches Masterpasswort oder beschädigte Datei.")

    # Inner content is JSON
    inner = json.loads(plaintext.decode("utf-8"))

    return {
        "metadata": outer.get("metadata", {}),
        "security_info": {
            "iterations": iterations,
            "algorithm": "PBKDF2WithHmacSHA1 + AES/GCM/NoPadding",
        },
        "preferences": inner,
    }


def extract_aimi_params(preferences: dict) -> dict:
    """Filter preferences to only AIMI-relevant keys."""
    return {
        k: v for k, v in preferences.items()
        if is_aimi_key(k)
    }


def format_value(v) -> str:
    if isinstance(v, bool):
        return "✅ true" if v else "☐ false"
    if isinstance(v, float):
        return f"{v:.4g}"
    if isinstance(v, str) and len(v) > 80:
        return v[:77] + "..."
    return str(v)


def print_report(data: dict, aimi_only: bool = True, output_json: str = None):
    meta = data.get("metadata", {})
    prefs = data.get("preferences", {})
    sec = data.get("security_info", {})

    print("\n" + "═" * 60)
    print("  AAPS EINSTELLUNGSEXPORT — ENTSCHLÜSSELT")
    print("═" * 60)
    print(f"  App:       {meta.get('applicationId', '?')}")
    print(f"  Version:   {meta.get('versionName', '?')} (Code: {meta.get('versionCode', '?')})")
    print(f"  Export:    {meta.get('exportDate', '?')}")
    print(f"  Device:    {meta.get('deviceModel', '?')}")
    print(f"  Crypto:    {sec.get('algorithm', '?')} ({sec.get('iterations', '?')} Iter.)")
    print(f"  Total:     {len(prefs)} Preferences")
    print("═" * 60)

    if aimi_only:
        aimi = extract_aimi_params(prefs)
        print(f"\n  AIMI-Parameter: {len(aimi)} von {len(prefs)} gesamt\n")

        # Group by prefix
        groups = {}
        for k, v in sorted(aimi.items()):
            # Simple grouping
            if "smb" in k.lower():
                g = "SMB"
            elif "gov" in k.lower() or "hypo" in k.lower() or "safety" in k.lower() or "emergency" in k.lower() or "tube" in k.lower():
                g = "Safety/Governance"
            elif "autodrive" in k.lower() or "autoDrive" in k:
                g = "Autodrive"
            elif "prebolus" in k.lower() or "pb" in k.lower():
                g = "Prebolus"
            elif "meal" in k.lower() or "BF" in k or "dinner" in k.lower() or "lunch" in k.lower() or "snack" in k.lower() or "sleep" in k.lower() or "cho" in k.lower():
                g = "Meal/Timing"
            elif "pkpd" in k.lower():
                g = "PKPD"
            elif "ngr" in k.lower() or "night" in k.lower():
                g = "Night"
            elif "wcycle" in k.lower() or "cycle" in k.lower():
                g = "Cycle"
            elif "t3c" in k.lower() or "brittle" in k.lower():
                g = "T3c"
            elif "isf" in k.lower():
                g = "ISF"
            elif "basal" in k.lower():
                g = "Basal"
            elif "thyroid" in k.lower():
                g = "Thyroid"
            elif "physio" in k.lower() or "hrv" in k.lower():
                g = "Physio"
            elif "advisor" in k.lower() or "claude" in k.lower() or "openai" in k.lower():
                g = "Advisor/LLM"
            else:
                g = "General"

            groups.setdefault(g, []).append((k, v))

        for group, items in sorted(groups.items()):
            print(f"  ── {group} ({'─' * (50 - len(group))})")
            for k, v in items:
                short_k = k.replace("key_openapsaimi_", "").replace("key_aimi_", "").replace("key_oaps_aimi_", "").replace("aimi_", "")
                print(f"    {short_k:<45} {format_value(v)}")
            print()
    else:
        print(f"\n  Alle {len(prefs)} Preferences:\n")
        for k, v in sorted(prefs.items()):
            print(f"  {k:<60} {format_value(v)}")

    print("═" * 60)

    if output_json:
        out_data = {
            "metadata": meta,
            "security_info": sec,
            "aimi_parameters": extract_aimi_params(prefs) if aimi_only else prefs,
            "all_preferences": prefs,
        }
        with open(output_json, "w", encoding="utf-8") as f:
            json.dump(out_data, f, indent=2, ensure_ascii=False)
        print(f"\n  Gespeichert: {output_json}")


def main():
    parser = argparse.ArgumentParser(
        description="AAPS Einstellungsexport entschlüsseln",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Beispiele:
  %(prog)s 2026-06-08_AAPS.json
  %(prog)s 2026-06-08_AAPS.json --password meinPasswort
  %(prog)s 2026-06-08_AAPS.json --all --output decoded.json
        """
    )
    parser.add_argument("file", help="AAPS Export-Datei (.json)")
    parser.add_argument("--password", "-p", help="Masterpasswort (wenn nicht angegeben: Eingabe per Prompt)")
    parser.add_argument("--all", "-a", action="store_true", help="Alle Preferences anzeigen (nicht nur AIMI)")
    parser.add_argument("--output", "-o", help="Ausgabe als JSON-Datei speichern")
    args = parser.parse_args()

    filepath = Path(args.file)
    if not filepath.exists():
        print(f"FEHLER: Datei nicht gefunden: {filepath}")
        sys.exit(1)

    password = args.password
    if not password:
        password = getpass.getpass("AAPS Masterpasswort: ")

    try:
        data = load_and_decrypt(filepath, password)
        print_report(data, aimi_only=not args.all, output_json=args.output)
    except ValueError as e:
        print(f"\nFEHLER: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"\nUnerwarteter Fehler: {e}")
        import traceback; traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
