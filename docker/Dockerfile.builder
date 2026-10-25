# Image de build Android pour hôte aarch64 (DGX Spark).
#
# Google ne publie aapt2 qu'en x86_64 : sans ça, tout build Android échoue en
# "Exec format error". On garde donc l'aapt2 officiel (celui d'AGP 8.7.3) et on
# l'exécute sous qemu-user, uniquement à l'intérieur de ce conteneur.
FROM ghcr.io/cirruslabs/android-sdk:35

ARG AAPT2_VERSION=8.7.3-12006047
ARG AAPT2_URL=https://dl.google.com/dl/android/maven2/com/android/tools/build/aapt2/${AAPT2_VERSION}/aapt2-${AAPT2_VERSION}-linux.jar

# aapt2 est un binaire dynamique x86_64 : il lui faut le loader et la libstdc++ amd64.
# Le miroir arm64 (ports) ne sert pas amd64 : on déclare les deux sources explicitement.
RUN dpkg --add-architecture amd64 \
 && rm -f /etc/apt/sources.list.d/ubuntu.sources \
 && printf '%s\n' \
      'deb [arch=arm64] http://ports.ubuntu.com/ubuntu-ports noble main universe' \
      'deb [arch=arm64] http://ports.ubuntu.com/ubuntu-ports noble-updates main universe' \
      'deb [arch=amd64] http://archive.ubuntu.com/ubuntu noble main universe' \
      'deb [arch=amd64] http://archive.ubuntu.com/ubuntu noble-updates main universe' \
      > /etc/apt/sources.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends \
      qemu-user-static unzip curl ca-certificates \
      libc6:amd64 libstdc++6:amd64 zlib1g:amd64 \
 && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /opt/aapt2 \
 && curl -sSLo /tmp/aapt2.jar "${AAPT2_URL}" \
 && unzip -j -o /tmp/aapt2.jar aapt2 -d /opt/aapt2 \
 && chmod +x /opt/aapt2/aapt2 \
 && rm /tmp/aapt2.jar

# AGP vérifie le nom du binaire : le wrapper doit s'appeler exactement "aapt2".
COPY docker/aapt2-qemu.sh /opt/aapt2-qemu/bin/aapt2
RUN chmod +x /opt/aapt2-qemu/bin/aapt2

# Vérification au build : si l'émulation casse, on le sait tout de suite.
RUN /opt/aapt2-qemu/bin/aapt2 version
