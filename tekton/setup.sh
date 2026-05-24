#!/usr/bin/env bash
# setup.sh – Richtet eine lokale Tekton-Umgebung mit kind ein und deployed
# alle Ressourcen für die MirrorService-Pipeline.
#
# Verwendung:
#   ./tekton/setup.sh                  # vollständiges Setup
#   ./tekton/setup.sh --skip-cluster   # Cluster existiert bereits, nur Tekton + Ressourcen
#   ./tekton/setup.sh --run-pipeline   # Setup + Pipeline direkt starten
#   ./tekton/setup.sh --delete         # Cluster und alle Ressourcen entfernen

set -euo pipefail

# ── Konfiguration ─────────────────────────────────────────────────────────────
CLUSTER_NAME="mirrorservice-pipeline"
TEKTON_HELM_REPO="https://cdfoundation.github.io/tekton-helm-chart"
GIT_CLONE_TASK_URL="https://raw.githubusercontent.com/tektoncd/catalog/main/task/git-clone/0.9/git-clone.yaml"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ── Farben ────────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()    { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }
step()    { echo -e "\n${GREEN}══ $* ${NC}"; }

# ── Flags parsen ──────────────────────────────────────────────────────────────
SKIP_CLUSTER=false
RUN_PIPELINE=false
DELETE=false
for arg in "$@"; do
  case $arg in
    --skip-cluster)   SKIP_CLUSTER=true ;;
    --run-pipeline)   RUN_PIPELINE=true ;;
    --delete)         DELETE=true ;;
    *) error "Unbekanntes Argument: $arg" ;;
  esac
done

# ── Löschen ───────────────────────────────────────────────────────────────────
if $DELETE; then
  step "Cluster '$CLUSTER_NAME' wird gelöscht"
  kind delete cluster --name "$CLUSTER_NAME" && info "Cluster gelöscht." || warn "Cluster nicht gefunden."
  exit 0
fi

# ── Voraussetzungen prüfen ────────────────────────────────────────────────────
step "Voraussetzungen prüfen"

check_tool() {
  if ! command -v "$1" &>/dev/null; then
    error "'$1' ist nicht installiert. Installationsanleitung: $2"
  fi
  info "$1 gefunden: $(command -v "$1")"
}

check_tool kind    "https://kind.sigs.k8s.io/docs/user/quick-start/#installation"
check_tool kubectl "https://kubernetes.io/docs/tasks/tools/install-kubectl-linux/"
check_tool helm    "https://helm.sh/docs/intro/install/"
check_tool docker  "https://docs.docker.com/engine/install/"

if ! command -v tkn &>/dev/null; then
  warn "'tkn' (Tekton CLI) nicht gefunden – Logs und Status können nicht per tkn abgefragt werden."
  warn "Installation: https://tekton.dev/docs/cli/#installation"
  TKN_AVAILABLE=false
else
  info "tkn gefunden: $(command -v tkn)"
  TKN_AVAILABLE=true
fi

# Docker läuft?
if ! docker info &>/dev/null; then
  error "Docker-Daemon antwortet nicht. Bitte Docker starten."
fi
info "Docker-Daemon erreichbar."

# ── kind-Cluster erstellen ────────────────────────────────────────────────────
if ! $SKIP_CLUSTER; then
  step "kind-Cluster '$CLUSTER_NAME' erstellen"

  if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
    warn "Cluster '$CLUSTER_NAME' existiert bereits. Überspringe Erstellung."
    warn "Zum Neuerstellen: ./setup.sh --delete && ./setup.sh"
  else
    info "Erstelle Cluster mit Konfiguration aus kind-cluster.yaml ..."
    kind create cluster \
      --name "$CLUSTER_NAME" \
      --config "$SCRIPT_DIR/kind-cluster.yaml" \
      --wait 120s
    info "Cluster erstellt."
  fi
fi

# kubeconfig auf den kind-Cluster setzen
kubectl config use-context "kind-${CLUSTER_NAME}"
info "kubectl-Kontext: kind-${CLUSTER_NAME}"

# ── Tekton Pipelines via Helm installieren ───────────────────────────────────
step "Tekton Pipelines (Helm) installieren"

info "Helm-Repository 'cdf' hinzufügen ..."
helm repo add cdf "$TEKTON_HELM_REPO" --force-update
helm repo update cdf

info "Tekton Pipelines installieren/aktualisieren ..."
helm upgrade --install tekton-pipeline cdf/tekton-pipeline \
  --namespace tekton-pipelines \
  --create-namespace \
  --wait \
  --timeout 3m

info "Tekton Pipelines ist bereit."

# ── git-clone Task aus dem Tekton Hub installieren ───────────────────────────
step "git-clone Task (Tekton Hub) installieren"
kubectl apply -f "$GIT_CLONE_TASK_URL"
info "git-clone Task installiert."

# ── Docker Hub Credentials anlegen ───────────────────────────────────────────
step "Docker Hub Credentials einrichten"

if kubectl get secret dockerhub-credentials &>/dev/null; then
  info "Secret 'dockerhub-credentials' existiert bereits – wird übersprungen."
else
  DOCKER_CONFIG="$HOME/.docker/config.json"

  if [[ -f "$DOCKER_CONFIG" ]]; then
    info "Erstelle Secret aus $DOCKER_CONFIG ..."
    kubectl create secret generic dockerhub-credentials \
      --from-file=config.json="$DOCKER_CONFIG"
    info "Secret 'dockerhub-credentials' angelegt."
  else
    warn "$DOCKER_CONFIG nicht gefunden."
    warn "Bitte manuell einloggen und erneut ausführen:"
    warn "  docker login && ./setup.sh --skip-cluster"
    warn ""
    warn "Oder Secret direkt anlegen:"
    warn "  kubectl create secret generic dockerhub-credentials \\"
    warn "    --from-file=config.json=\$HOME/.docker/config.json"
    warn ""
    warn "Setup wird fortgesetzt – Pipeline-Run schlägt ohne Credentials fehl."
  fi
fi

# ── Tekton-Ressourcen deployen ────────────────────────────────────────────────
step "ServiceAccount, Tasks und Pipeline deployen"

kubectl apply -f "$SCRIPT_DIR/serviceaccount.yaml"
info "ServiceAccount angelegt."

kubectl apply -f "$SCRIPT_DIR/tasks/maven-build.yaml"
kubectl apply -f "$SCRIPT_DIR/tasks/kaniko-build-push.yaml"
info "Tasks angelegt."

kubectl apply -f "$SCRIPT_DIR/pipeline.yaml"
info "Pipeline angelegt."

# ── Status ausgeben ───────────────────────────────────────────────────────────
step "Installierte Ressourcen"
kubectl get tasks,pipeline
echo ""

# ── Optional: Pipeline direkt starten ────────────────────────────────────────
if $RUN_PIPELINE; then
  step "Pipeline starten"
  info "Erstelle PipelineRun ..."
  RUN_NAME=$(kubectl create -f "$SCRIPT_DIR/pipeline-run.yaml" -o name)
  info "Gestartet: $RUN_NAME"

  if $TKN_AVAILABLE; then
    info "Logs werden gestreamt (Strg+C zum Beenden, Pipeline läuft weiter) ..."
    sleep 3
    tkn pipelinerun logs --last --follow
  else
    info "Logs verfolgen:"
    info "  kubectl get pipelineruns"
    info "  kubectl logs -l tekton.dev/pipelineRun=\$(kubectl get pr --sort-by=.metadata.creationTimestamp -o name | tail -1 | cut -d/ -f2) --all-containers --follow"
  fi
fi

# ── Abschluss ─────────────────────────────────────────────────────────────────
echo ""
info "Setup abgeschlossen."
echo ""
echo "  Pipeline starten:         kubectl create -f tekton/pipeline-run.yaml"
if $TKN_AVAILABLE; then
  echo "  Logs verfolgen:           tkn pipelinerun logs --last --follow"
  echo "  Alle Runs auflisten:      tkn pipelinerun list"
fi
echo "  Cluster entfernen:        ./tekton/setup.sh --delete"
echo ""
