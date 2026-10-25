#!/bin/sh
# aapt2 officiel (x86_64) exécuté sous qemu-user, pour les hôtes aarch64.
# Utilisé via -Pandroid.aapt2FromMavenOverride=/usr/local/bin/aapt2-qemu
exec /usr/bin/qemu-x86_64-static /opt/aapt2/aapt2 "$@"
