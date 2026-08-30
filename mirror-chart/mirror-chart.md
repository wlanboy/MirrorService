# Helm Chart: mirror

Helm Chart für das Deployment des `MirrorService` in Kubernetes. Der Chart
unterstützt wahlweise Istio (Standard), Traefik oder gar keinen Ingress-Weg,
gesteuert über den Schalter `ingress.controller`.

## Überblick

| Eigenschaft | Wert |
|---|---|
| Chart-Name | `mirror` |
| Chart-Version | 0.1.0 |
| App-Version | `latest` |
| Typ | `application` |

## Komponenten

Das Chart rendert folgende Kubernetes-Ressourcen:

- **Deployment** (`templates/deployment.yaml`) – Replica(s) des Containers `wlanboy/mirrorservice`, mit Liveness- und Readiness-Probe auf `/actuator/health/{liveness,readiness}`, ConfigMap-Mount für `application.properties` und optionalen `extraEnv`/`extraVolumes`/`extraVolumeMounts`.
- **Service** (`templates/service.yaml`) – ClusterIP-Service, leitet Traffic an die Pods weiter.
- **ConfigMap** (`templates/configmap.yaml`) – `application.properties` für Actuator-Endpunkte, Build-Info und Tracing-Propagation.
- **Certificate** (`templates/certificate.yaml`, nur bei `certmanager.enabled: true` und vorhandener `cert-manager.io/v1`-CRD) – cert-manager `Certificate` für die konfigurierten Hosts, abgelegt im `istio.gatewayNamespace`.
- **ServiceEntry** (`templates/serviceentry.yaml`, nur bei `serviceEntry.enabled: true`) – Istio `ServiceEntry` für externen Zugriff auf `serviceEntry.host`.
- **Gateway** (`templates/gateway.yaml`, nur bei `ingress.controller: istio`) – Istio `Gateway` auf Port 443/HTTPS (falls `certmanager.enabled`) und 80/HTTP für die konfigurierten Hosts.
- **VirtualService** (`templates/virtualservice.yaml`, nur bei `ingress.controller: istio`) – Istio `VirtualService`, routet Traffic vom Gateway (und dem internen `mesh`-Gateway) zum Service.
- **IngressRoute** (`templates/traefik-ingressroute.yaml`, nur bei `ingress.controller: traefik`) – Traefik `IngressRoute`, routet die konfigurierten Hosts direkt zum Service.

Welche Ingress-Ressource gerendert wird, steuert `ingress.controller`
(`istio` | `traefik` | `none`). Zusätzlich zum Werte-Schalter prüft jedes
Ingress-Template über `.Capabilities.APIVersions.Has`, ob die passende CRD im
Zielcluster überhaupt vorhanden ist – ein `helm install`/`helm template`
schlägt also nicht fehl, nur weil die CRDs des jeweils anderen Controllers
fehlen. Aus demselben Grund liefert `helm template` ohne `--api-versions`
für das jeweils aktive Ingress-Template kein Ergebnis (`could not find
template ...`), solange die passende CRD nicht via `--api-versions`
vorgegaukelt wird – siehe Testen unten.

## Konfiguration (`values.yaml`)

| Key | Beschreibung | Default |
|---|---|---|
| `replicaCount` | Anzahl Replicas | `1` |
| `deploymentName` | Name für Deployment, Service, ConfigMap, Gateway/VirtualService/IngressRoute | `mirror` |
| `namespace` | Deklarativer Ziel-Namespace (aktuell nicht in Templates referenziert – Ressourcen landen im Namespace des `helm install -n ...`) | `mirror` |
| `image.repository` | Container-Image-Repository | `wlanboy/mirrorservice` |
| `image.tag` | Image-Tag | `latest` |
| `image.pullPolicy` | Pull-Policy | `Always` |
| `hosts` | Liste externer Hostnamen, gilt für Certificate, Gateway/VirtualService und IngressRoute | `mirror.tp.lan`, `mirror.gmk.lan`, `mirror.localhost` |
| `service.port` | Container- und Service-Port (auch für Health-Checks) | `8080` |
| `ingress.controller` | Aktiver Ingress-Weg: `istio`, `traefik` oder `none` | `istio` |
| `istio.gateway` | Name des Istio-Gateway-Objekts | `mirror-gateway` |
| `istio.gatewayNamespace` | Namespace, in dem das TLS-Secret für das Istio-Gateway liegen muss | `istio-ingress` |
| `traefik.entryPoints` | Traefik Entrypoints für die IngressRoute | `[web, websecure]` |
| `serviceEntry.enabled` | Istio `ServiceEntry` für externen Zugriff aktivieren | `false` |
| `serviceEntry.host` | Host für die `ServiceEntry` | `mirror.gmk.lan` |
| `certmanager.enabled` | cert-manager `Certificate` erzeugen und TLS am Istio-Gateway terminieren | `true` |
| `certmanager.issuer` / `certmanager.kind` | Issuer-Referenz für das Certificate | `local-ca-issuer` / `ClusterIssuer` |
| `certmanager.secretName` | Name des TLS-Secrets (Certificate-Output und Gateway-`credentialName`) | `mirrorapi-tls` |
| `extraEnv` / `extraVolumes` / `extraVolumeMounts` | Freie Erweiterungspunkte für das Deployment | `[]` |

Für die Wahl des Controllers stehen zwei schlanke Override-Dateien bereit,
statt `ingress.controller` von Hand setzen zu müssen:
[values-istio.yaml](values-istio.yaml) und
[values-traefik.yaml](values-traefik.yaml).

## Installation

Mit Istio (Standard):

```bash
kubectl create namespace mirror
kubectl label namespace mirror istio-injection=enabled --overwrite
helm install mirror ./mirror-chart -n mirror --create-namespace -f mirror-chart/values-istio.yaml
```

Mit Traefik:

```bash
kubectl create namespace mirror
helm install mirror ./mirror-chart -n mirror --create-namespace -f mirror-chart/values-traefik.yaml

helm status mirror -n mirror
```

Das `istio-injection`-Label wird nur benötigt, wenn `ingress.controller:
istio` aktiv ist.

## Upgrade

```bash
helm upgrade mirror ./mirror-chart -n mirror
```

Bei geänderten Values (z. B. anderes Image-Tag):

```bash
helm upgrade mirror ./mirror-chart -n mirror --set image.tag=1.2.3
```

Da `image.tag` standardmäßig auf `latest` steht und `imagePullPolicy: Always`
gesetzt ist, genügt ein Rollout-Restart, um ein neu gebautes `latest`-Image
zu ziehen, auch ohne Values-Änderung:

```bash
kubectl rollout restart deployment/mirror -n mirror
kubectl rollout status deployment/mirror -n mirror
```

## Hinweise

- Bei `ingress.controller: istio` terminiert das Gateway TLS über das vom
  `Certificate` erzeugte Secret (`certmanager.secretName`), sofern
  `certmanager.enabled: true` ist; dieses Secret muss im
  `istio.gatewayNamespace` liegen.
- `values-traefik.yaml` setzt `certmanager.enabled: false`, da das
  `Certificate` nur den `credentialName` des Istio-Gateways bedient und ohne
  Istio wirkungslos wäre. TLS für Traefik läuft stattdessen über einen
  Default-Certificate/TLSStore-Mechanismus im Cluster (siehe nächster Punkt).
  Das `Certificate`-Template selbst prüft zusätzlich per
  `.Capabilities.APIVersions.Has "cert-manager.io/v1"`, ob cert-manager im
  Cluster installiert ist, und wird sonst übersprungen statt das Install mit
  `no matches for kind "Certificate"` fehlschlagen zu lassen.
- Die IngressRoute setzt kein eigenes `tls`-Feld – `websecure` in
  `traefik.entryPoints` setzt TLS-Terminierung am Entrypoint voraus (z. B.
  Default-Zertifikat via `TLSStore`).
- Bei `ingress.controller: none` wird keine Ingress-Ressource gerendert;
  Deployment und Service laufen weiter.
- `persistence.*` und `namespace` sind aktuell in `values.yaml` deklariert,
  aber in keinem Template referenziert.

## Testen

```bash
# Istio-Pfad rendern (CRD-Vortäuschung nötig, da helm template keine Live-CRDs kennt)
helm template mirror ./mirror-chart -f mirror-chart/values-istio.yaml \
  --api-versions networking.istio.io/v1beta1 \
  --show-only templates/virtualservice.yaml --show-only templates/gateway.yaml

# Traefik-Pfad rendern
helm template mirror ./mirror-chart -f mirror-chart/values-traefik.yaml \
  --api-versions traefik.io/v1alpha1 \
  --show-only templates/traefik-ingressroute.yaml

# sicherstellen, dass bei controller=none keine Ingress-Ressourcen entstehen
helm template mirror ./mirror-chart --set ingress.controller=none | grep -E "IngressRoute|VirtualService|Gateway"

helm lint ./mirror-chart -f mirror-chart/values-istio.yaml
helm lint ./mirror-chart -f mirror-chart/values-traefik.yaml
```
