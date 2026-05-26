#!/usr/bin/env bash
# min-setup.sh – Minimales Setup: Tekton + Ressourcen ohne Tool-Prüfung.
#
# Verwendung:
#   ./tekton/min-setup.sh                  # vollständiges Setup
#   ./tekton/min-setup.sh --skip-cluster   # Cluster existiert bereits, nur Tekton + Ressourcen
#   ./tekton/min-setup.sh --run-pipeline   # Setup + Pipeline direkt starten
#   ./tekton/min-setup.sh --delete         # Cluster entfernen

set -euo pipefail

CLUSTER_NAME="mirrorservice-pipeline"
TEKTON_HELM_REPO="https://cdfoundation.github.io/tekton-helm-chart"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }
step()  { echo -e "\n${GREEN}══ $* ${NC}"; }

SKIP_CLUSTER=false
RUN_PIPELINE=false
DELETE=false
for arg in "$@"; do
  case $arg in
    --skip-cluster)  SKIP_CLUSTER=true ;;
    --run-pipeline)  RUN_PIPELINE=true ;;
    --delete)        DELETE=true ;;
    *) error "Unbekanntes Argument: $arg" ;;
  esac
done

if $DELETE; then
  step "Cluster '$CLUSTER_NAME' wird gelöscht"
  kind delete cluster --name "$CLUSTER_NAME" && info "Cluster gelöscht." || warn "Cluster nicht gefunden."
  exit 0
fi

# ── kind-Cluster erstellen ────────────────────────────────────────────────────
if ! $SKIP_CLUSTER; then
  step "kind-Cluster '$CLUSTER_NAME' erstellen"

  if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
    warn "Cluster '$CLUSTER_NAME' existiert bereits – überspringe Erstellung."
  else
    info "Erstelle Cluster mit Konfiguration aus kind-cluster.yaml ..."
    kind create cluster \
      --name "$CLUSTER_NAME" \
      --config "$SCRIPT_DIR/kind-cluster.yaml" \
      --wait 120s
    info "Cluster erstellt."
  fi
fi

kubectl config use-context "kind-${CLUSTER_NAME}"
info "kubectl-Kontext: kind-${CLUSTER_NAME}"

# ── Tekton Pipelines via Helm installieren ───────────────────────────────────
step "Tekton Pipelines (Helm) installieren"

helm repo add cdf "$TEKTON_HELM_REPO" --force-update
helm repo update cdf

helm upgrade --install tekton-pipeline cdf/tekton-pipeline \
  --namespace tekton-pipelines \
  --create-namespace \
  --wait \
  --timeout 3m

info "Tekton Pipelines ist bereit."

# ── git-clone Task aus lokaler Ressource installieren ────────────────────────
step "git-clone Task (lokal) installieren"
kubectl apply -f "$SCRIPT_DIR/resources/git-clone.yaml"
info "git-clone Task installiert."

# ── Docker Hub Credentials anlegen ───────────────────────────────────────────
step "Docker Hub Credentials einrichten"

if kubectl get secret dockerhub-credentials &>/dev/null; then
  info "Secret 'dockerhub-credentials' existiert bereits – wird übersprungen."
else
  DOCKER_CONFIG="$HOME/.docker/config.json"

  if [[ -f "$DOCKER_CONFIG" ]]; then
    kubectl create secret generic dockerhub-credentials \
      --from-file=config.json="$DOCKER_CONFIG"
    info "Secret 'dockerhub-credentials' angelegt."
  else
    warn "$DOCKER_CONFIG nicht gefunden."
    warn "Bitte manuell einloggen: docker login && ./min-setup.sh --skip-cluster"
    warn "Setup wird fortgesetzt – Pipeline-Run schlägt ohne Credentials fehl."
  fi
fi

# ── Tekton-Ressourcen deployen ────────────────────────────────────────────────
step "ServiceAccount, Tasks und Pipeline deployen"

kubectl apply -f "$SCRIPT_DIR/serviceaccount.yaml"
kubectl apply -f "$SCRIPT_DIR/tasks/maven-build.yaml"
kubectl apply -f "$SCRIPT_DIR/tasks/kaniko-build-push.yaml"
kubectl apply -f "$SCRIPT_DIR/pipeline.yaml"
info "Ressourcen angelegt."

step "Installierte Ressourcen"
kubectl get tasks,pipeline
echo ""

# ── Optional: Pipeline direkt starten ────────────────────────────────────────
if $RUN_PIPELINE; then
  step "Pipeline starten"
  RUN_NAME=$(kubectl create -f "$SCRIPT_DIR/pipeline-run.yaml" -o name)
  info "Gestartet: $RUN_NAME"

  if command -v tkn &>/dev/null; then
    sleep 3
    tkn pipelinerun logs --last --follow
  else
    info "Logs verfolgen:"
    info "  kubectl get pipelineruns"
    info "  kubectl logs -l tekton.dev/pipelineRun=\$(kubectl get pr --sort-by=.metadata.creationTimestamp -o name | tail -1 | cut -d/ -f2) --all-containers --follow"
  fi
fi

echo ""
info "Setup abgeschlossen."
echo ""
echo "  Pipeline starten:    kubectl create -f tekton/pipeline-run.yaml"
echo "  Cluster entfernen:   ./tekton/min-setup.sh --delete"
echo ""
