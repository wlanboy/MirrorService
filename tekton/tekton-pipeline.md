# MirrorService – Tekton Pipeline

Dieses Dokument beschreibt den Aufbau und Betrieb der Tekton CI/CD-Pipeline für den MirrorService.
Die Pipeline ersetzt die GitHub Actions Workflows durch eine lokale, Kubernetes-native Lösung auf Basis eines kind-Clusters.
Sie umfasst alle Schritte von Repository-Clone über Maven-Build mit Spring Boot AOT bis zum Docker-Image-Push nach Docker Hub.

## Übersicht

Die Pipeline besteht aus drei aufeinanderfolgenden Tasks und spiegelt den
Ablauf der bestehenden GitHub Actions Workflows wider:

```
git-clone  →  maven-build  →  kaniko-build-push
```

| Schritt | Task | Entspricht GitHub Workflow |
|---|---|---|
| 1 | `git-clone` (Tekton Hub) | `actions/checkout` |
| 2 | `maven-build` (custom) | `maven.yml` – `mvn package` |
| 3 | `kaniko-build-push` (custom) | `dockerpublish.yml` – `docker build + push` |

### Dateien

```
tekton/
├── setup.sh                    # Automatisches Setup-Skript (kind + Tekton + Ressourcen)
├── kind-cluster.yaml           # kind-Cluster-Konfiguration
├── pipeline.yaml               # Pipeline-Definition (drei Tasks)
├── pipeline-run.yaml           # Beispiel-PipelineRun zum Auslösen
├── serviceaccount.yaml         # ServiceAccount für den Pipeline-Runner
├── secret-template.yaml        # Vorlage für Docker Hub Credentials
└── tasks/
    ├── maven-build.yaml        # Baut das JAR (Java 25 + Spring Boot AOT)
    └── kaniko-build-push.yaml  # Baut das Docker Image und pushed nach Docker Hub
```

### Task-Details

**1. git-clone**
Standard-Task aus dem Tekton Hub. Klont das Repository in den geteilten
Workspace `shared-data`.

**2. maven-build**
Führt `mvn compile spring-boot:process-aot package` auf dem UBI9 OpenJDK 25
Image aus – identisch zum Build-Stage des Dockerfiles. Das Maven-Repository
wird im Workspace unter `.m2/repository` gespeichert.

**3. kaniko-build-push**
Baut das mehrstufige Dockerfile mit dem Kaniko-Executor (kein Docker-Daemon
erforderlich). `RUN --mount=type=cache`-Direktiven werden unterstützt, das
Cache-Verzeichnis ist jedoch pro Build ephemer. Das fertige Image wird nach
`wlanboy/mirrorservice:latest` gepusht und der SHA256-Digest als
Pipeline-Result zurückgegeben.

---

## Voraussetzungen

### Werkzeuge installieren

```bash
# kind – Kubernetes in Docker
curl -Lo /usr/local/bin/kind \
  https://kind.sigs.k8s.io/dl/latest/kind-linux-amd64
chmod +x /usr/local/bin/kind

# Helm
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Tekton CLI (tkn) – optional, für bequemeres Log-Tailing
curl -LO https://github.com/tektoncd/cli/releases/latest/download/tkn_Linux_x86_64.tar.gz
tar xzf tkn_Linux_x86_64.tar.gz tkn && sudo mv tkn /usr/local/bin/

# kubectl (falls noch nicht vorhanden)
# https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/
```

### Docker-Anforderungen

kind verwendet Docker-Container als Cluster-Knoten. Docker benötigt daher
ausreichend Ressourcen:

- **RAM:** mindestens 6 GiB für den Docker-Daemon
- **Docker Desktop (Windows/Mac):** Settings → Resources → Memory auf ≥ 6 GiB setzen
- **Linux:** Docker nutzt direkt den Host-Speicher

---

## Setup (automatisch)

Das Skript `setup.sh` erledigt das komplette Setup in einem Schritt:

```bash
# Vollständiges Setup (Cluster + Tekton + Ressourcen)
./tekton/setup.sh

# Setup und Pipeline sofort starten
./tekton/setup.sh --run-pipeline

# Nur Tekton + Ressourcen deployen (Cluster existiert bereits)
./tekton/setup.sh --skip-cluster

# Cluster und alle Ressourcen entfernen
./tekton/setup.sh --delete
```

Das Skript:
1. Prüft ob `kind`, `kubectl`, `docker` (und optional `tkn`) installiert sind
2. Erstellt den kind-Cluster `mirrorservice-pipeline`
3. Installiert Tekton Pipelines
4. Installiert den `git-clone` Task aus dem Tekton Hub
5. Legt das Docker Hub Credentials Secret aus `~/.docker/config.json` an
6. Wendet ServiceAccount, Tasks und Pipeline an

---

## Setup (manuell, Schritt für Schritt)

### 1. kind-Cluster erstellen

```bash
kind create cluster \
  --name mirrorservice-pipeline \
  --config tekton/kind-cluster.yaml \
  --wait 120s

# kubectl-Kontext auf den neuen Cluster setzen
kubectl config use-context kind-mirrorservice-pipeline
```

### 2. Tekton Pipelines via Helm installieren

```bash
helm repo add cdf https://cdfoundation.github.io/tekton-helm-chart
helm repo update cdf

helm upgrade --install tekton-pipeline cdf/tekton-pipeline \
  --namespace tekton-pipelines \
  --create-namespace \
  --wait \
  --timeout 3m
```

### 3. git-clone Task installieren

```bash
kubectl apply -f \
  https://raw.githubusercontent.com/tektoncd/catalog/main/task/git-clone/0.9/git-clone.yaml
```

### 4. Docker Hub Credentials anlegen

```bash
# Sicherstellen, dass docker login ausgeführt wurde
docker login

# Secret aus der lokalen Docker-Konfiguration erstellen
kubectl create secret generic dockerhub-credentials \
  --from-file=config.json=$HOME/.docker/config.json

# Verifizieren
kubectl get secret dockerhub-credentials
```

### 5. Tekton-Ressourcen deployen

```bash
kubectl apply -f tekton/serviceaccount.yaml
kubectl apply -f tekton/tasks/maven-build.yaml
kubectl apply -f tekton/tasks/kaniko-build-push.yaml
kubectl apply -f tekton/pipeline.yaml

# Alle Ressourcen prüfen
kubectl get tasks,pipeline
```

---

## Pipeline ausführen

### Start per kubectl

```bash
# Neuen PipelineRun anlegen (generateName erzeugt bei jedem Aufruf einen neuen Namen)
kubectl create -f tekton/pipeline-run.yaml
```

### Start per tkn CLI

```bash
tkn pipeline start mirrorservice-pipeline \
  --param repo-url=https://github.com/wlanboy/MirrorService.git \
  --param revision=main \
  --param image=wlanboy/mirrorservice:latest \
  --workspace name=shared-data,volumeClaimTemplateFile=- <<'EOF'
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 2Gi
EOF
  --workspace name=docker-credentials,secret=dockerhub-credentials \
  --serviceaccount mirrorservice-pipeline-sa \
  --showlog
```

### Logs in Echtzeit verfolgen

```bash
# Letzten Run verfolgen
tkn pipelinerun logs --last --follow

# Bestimmten Run verfolgen
tkn pipelinerun logs mirrorservice-run-xxxxx --follow

# Alle Runs auflisten
tkn pipelinerun list
```

---

## Troubleshooting

### Pipeline-Run Status prüfen

```bash
tkn pipelinerun describe --last

# Einzelnen Task-Run debuggen
tkn taskrun logs <taskrun-name> --follow
```

### Häufige Fehler

| Problem | Ursache | Lösung |
|---|---|---|
| `unauthorized: authentication required` | Docker-Credentials fehlen oder veraltet | `docker login` erneut ausführen und Secret neu anlegen |
| `Couldn't load specified config` | Secret-Key heißt nicht `config.json` | `kubectl delete secret dockerhub-credentials && kubectl create secret generic dockerhub-credentials --from-file=config.json=~/.docker/config.json` |
| `OOMKilled` im maven-build Task | Zu wenig RAM im kind-Knoten | Docker-Daemon mehr RAM zuweisen (≥ 6 GiB) |
| `PVC pending – no matching StorageClass` | kind-Cluster ohne default StorageClass | kind enthält `rancher.io/local-path` standardmäßig – `kubectl get sc` prüfen |
| `context deadline exceeded` beim Cluster-Start | Docker braucht zu lange | `kind delete cluster --name mirrorservice-pipeline && ./setup.sh` |
| `exec format error` in Kaniko | Architektur-Mismatch | Kein Handlungsbedarf für amd64; für ARM: `--extra_args=--platform=linux/arm64` |

### Tekton-Version prüfen

```bash
helm list --namespace tekton-pipelines
helm show chart cdf/tekton-pipeline | grep appVersion
```

### kind-Cluster neu erstellen

```bash
./tekton/setup.sh --delete
./tekton/setup.sh
```

### Aktiven kubectl-Kontext prüfen

```bash
kubectl config current-context
# Erwartet: kind-mirrorservice-pipeline

# Manuell setzen falls nötig
kubectl config use-context kind-mirrorservice-pipeline
```

---

## Hinweise zur Kaniko + BuildKit Cache-Kompatibilität

Das Dockerfile verwendet `RUN --mount=type=cache,target=/root/.m2` (BuildKit-
Syntax). Kaniko unterstützt diese Direktive syntaktisch (via `--use-new-run`),
behandelt das Cache-Verzeichnis jedoch als ephemeren leeren Ordner – es gibt
kein Build-übergreifendes Caching.

**Effekt:** Der Build funktioniert korrekt, Maven lädt bei jedem Run alle
Dependencies neu herunter. Das ist für gelegentliche lokale Tests akzeptabel.

---

## Pipeline-Ergebnis verifizieren

```bash
# Image-Digest aus dem Pipeline-Result lesen
tkn pipelinerun describe --last -o jsonpath='{.status.pipelineResults}'

# Image lokal testen
docker pull wlanboy/mirrorservice:latest
docker run --rm --name mirror -p 8003:8080 wlanboy/mirrorservice:latest
```
