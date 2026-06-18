# AIMI Parameter Analyzer

Web-Dashboard zur Analyse von AIMI-Parametern auf Basis von Nightscout-CGM-Daten.

## Features

- 📊 **Dashboard**: TIR, GMI, CV, Hypo-Rate, 24h-Glukoseverlauf, stündliche Muster
- ⚙️ **Parameter-Browser**: Alle 211 AIMI-Parameter mit Filterung nach Kategorie, Impact, Typ
- 🔧 **Optimierungsempfehlungen**: Regelbasierte Analyse mit Konfidenz und Richtungsanzeige
- 💾 **Lokale Konfiguration**: Nightscout-URL + eigene Parameterwerte werden im Browser gespeichert

## Deployment

### Voraussetzungen
- Docker + Docker Compose
- Nightscout-Instanz (z.B. `ns-rb.ddnss.org`)

### Starten

```bash
# Im Verzeichnis aimi-analyzer/
docker compose up -d --build

# Logs
docker compose logs -f

# Stoppen
docker compose down
```

Das Dashboard ist dann erreichbar unter: **http://localhost:8765**

Oder vom NUC aus: **http://192.168.1.71:8765**

### Datei-Update

Wenn du eine neue `aimi_parameters.json` von Claude Code bekommst:

```bash
cp /pfad/zur/neuen/aimi_parameters.json data/
docker compose restart
```

### Parameter-Werte eintragen

Im Tab **Konfiguration** → **Aktuelle Parameter-Werte** kannst du deine aktuellen Einstellungen eintragen. Das verbessert die Optimierungsempfehlungen erheblich (sonst werden Default-Werte als Basis verwendet).

## Optimierungslogik

Die Empfehlungen basieren auf folgenden Regeln:

| Metrik | Schwelle | Empfehlung |
|--------|----------|------------|
| Hypo-Rate > 4% | Max-SMB reduzieren, Hypo-Floor erhöhen |
| TIR < 70% + kein Hypo | Max-SMB erhöhen, Mahlzeiten-Faktor prüfen |
| Nächtlicher BZ > 150 mg/dL | Night Growth Rate aktivieren |
| CV > 40% | SMB Tail-Dämpfung erhöhen |
| CV > 45% + Hypo + Hyper | T3c Brittle Mode prüfen |

## API

```
GET  /api/parameters          → alle Parameter (filterbar)
GET  /api/parameters/categories → Kategorien mit Count
POST /api/analyze             → Nightscout-Analyse + Empfehlungen
GET  /api/health              → Status
```

## Struktur

```
aimi-analyzer/
├── backend/
│   ├── app.py           ← FastAPI Backend
│   ├── requirements.txt
│   └── Dockerfile
├── frontend/
│   └── index.html       ← Single-Page Dashboard
├── data/
│   └── aimi_parameters.json  ← Extrahierte Parameter
└── docker-compose.yml
```
