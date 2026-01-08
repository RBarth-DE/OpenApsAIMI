import xml.etree.ElementTree as ET
import re
import os

# Liste zu prüfender Dateien
XML_FILES = [
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-de-rDE//strings.xml",
    "plugins/aps/src/main/res/values/strings.xml",
    "plugins/aps/src/main/res/values-de-rDE/strings.xml",
    "plugins/main/src/main/res/values/strings.xml",
    "plugins/main/src/main/res/values-de-rDE/strings.xml",
    "plugins/main/src/main/res/values-fr-rFR/strings.xml"
]

# Erlaubte Typen
VALID_TYPES = {'d', 'f', 's'}

# Regex: findet alle Platzhalter, auch kaputte
# z.B. %1$, %2$3.1f, %1$04.2f, %d, %f
FIND_ALL_PLACEHOLDERS = re.compile(r'%(?:\d+\$)?[+-]?(?:0)?(?:\d+)?(?:\.\d+)?[dfs]?|%')

# Regex: gültige Platzhalter
VALID_PLACEHOLDERS = re.compile(r'%(\d+\$)?[+-]?(?:0)?(?:\d+)?(?:\.\d+)?[dfs]')

def analyze_placeholder(ph: str):
    """Gibt None zurück, wenn alles OK, sonst Fehlermeldung."""
    # Ein einzelnes % ohne Typ ist OK
    if ph == '%':
        return None
    # Prüfe, ob Typ fehlt (letztes Zeichen ist keine d/f/s)
    if not re.match(r'.*[dfs]$', ph):
        return f"Lonely % without type" if ph != '%' else None
    return None

def process_file(file_path):
    print(f"\n🔍 Prüfe Datei: {file_path}")

    if not os.path.exists(file_path):
        print("❌ Datei nicht gefunden")
        return

    try:
        tree = ET.parse(file_path)
    except ET.ParseError as e:
        print(f"❌ XML-Syntaxfehler: {e}")
        return

    root = tree.getroot()

    for string in root.findall('string'):
            name = string.attrib.get('name')
            text = string.text or ""

            # volle Platzhalter matchen, nicht nur Gruppe
            all_ph = [m.group(0) for m in FIND_ALL_PLACEHOLDERS.finditer(text)]
            if not all_ph:
                continue

            print(f"\n🧩 {name}:")
            for ph in all_ph:
                error = analyze_placeholder(ph)
                if error:
                    print(f"  ❌ {ph} → {error}")
                else:
                    print(f"  ✔ {ph} OK")

def main():
    print("=== String-Format-Checker mit Typ-Prüfung (inkl. Breite/Präzision) ===")
    for f in XML_FILES:
        process_file(f)
    print("\nFertig.")

if __name__ == "__main__":
    main()
