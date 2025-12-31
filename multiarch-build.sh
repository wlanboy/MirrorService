#!/bin/bash

# --- Konfiguration ---
DOCKER_USERNAME="wlanboy"
IMAGE_NAME="wlanboy/mirrorservice:latest"
PLATFORMS="linux/amd64,linux/arm64"
BUILDER_NAME="multiarch-builder"

# Fehlerbehandlung
set -e

# --- 1. Vorab-Abfragen & Cleanup ---
echo "--- Vorbereitung ---"
read -p "Möchtest du den Docker Build-Cache vorab bereinigen? (y/N): " PRE_CLEANUP
if [[ "$PRE_CLEANUP" =~ ^[Yy]$ ]]; then
    echo "-> Bereinige Build-Cache..."
    docker builder prune -a -f
fi

# Zeitmessung starten
START_TIME=$(date +%s)

# --- 2. System-Check & Login ---
echo -e "\n--- System-Check & Login ---"

if docker info 2>/dev/null | grep -iq "Username"; then
    CURRENT_USER=$(docker info 2>/dev/null | grep -i "Username" | awk '{print $2}')
    if [ "$CURRENT_USER" == "$DOCKER_USERNAME" ]; then
        echo "✅ Bereits als $DOCKER_USERNAME eingeloggt."
    else
        LOGIN_REQUIRED=true
    fi
else
    LOGIN_REQUIRED=true
fi

if [ "$LOGIN_REQUIRED" = true ]; then
    read -sp "Docker Hub Passwort für $DOCKER_USERNAME eingeben: " DOCKER_PASSWORD
    echo ""
    echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
fi

# 3. QEMU Check
if ! dpkg -l | grep -q qemu-user-static; then
    echo "-> Installiere QEMU für Multi-Arch Support..."
    sudo apt-get update && sudo apt-get install -y qemu-user-static binfmt-support
    sudo update-binfmts --enable
fi

# 4. Builder Setup
if ! docker buildx inspect $BUILDER_NAME &>/dev/null; then
    docker buildx create --name $BUILDER_NAME --use
else
    docker buildx use $BUILDER_NAME
fi
docker buildx inspect --bootstrap

# --- 5. Auswahl der Aktion ---
echo -e "\nWas möchtest du tun?"
echo "1) Nur bauen (lokal laden - nur aktuelle Architektur)"
echo "2) Bauen und pushen (Multi-Arch zu Docker Hub)"
read -p "Auswahl [1 oder 2]: " ACTION_CHOICE

case $ACTION_CHOICE in
    1)
        echo "-> Starte lokalen Build..."
        docker buildx build -t "$IMAGE_NAME" --load .
        echo -e "\n--- Image-Informationen ---"
        docker buildx imagetools inspect "$IMAGE_NAME"
        echo "Größe auf dem Host:"
        docker images "$IMAGE_NAME" 
        ;;
    2)
        echo "-> Starte Multi-Arch Build & Push ($PLATFORMS)..."
        docker buildx build --platform "$PLATFORMS" -t "$IMAGE_NAME" --push .
        echo -e "\n--- Remote Manifest-Informationen ---"
        docker buildx imagetools inspect "$IMAGE_NAME"
        ;;
    *)
        echo "Ungültige Auswahl. Abbruch."
        exit 1
        ;;
esac

# --- 6. Abschluss & Zeitmessung ---
END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))
MINUTES=$((DURATION / 60))
SECONDS=$((DURATION % 60))

echo -e "\n------------------------------------------"
echo "✅ Vorgang erfolgreich abgeschlossen!"
echo "⏱️  Gesamtdauer: ${MINUTES}m ${SECONDS}s"
echo "------------------------------------------"