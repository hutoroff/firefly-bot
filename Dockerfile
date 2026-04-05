# ── Stage 1: Build the fat JAR ─────────────────────────────────────────────
FROM gradle:8.10-jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle clean shadowJar --no-daemon

# ── Stage 2: Build a minimal custom JRE via jlink ─────────────────────────
# Uses jdeps to detect required modules, then adds crypto/unsafe extras that
# are loaded via reflection and thus invisible to static analysis.
FROM eclipse-temurin:21-jdk-alpine AS jre-builder
COPY --from=builder /app/build/libs/*.jar /tmp/app.jar
RUN JDEPS=$(jdeps --ignore-missing-deps --multi-release 21 \
              --print-module-deps /tmp/app.jar 2>/dev/null | tr -d '[:space:]') \
 && MODS=$(printf '%s,jdk.crypto.ec,jdk.unsupported' "$JDEPS" \
         | tr ',' '\n' | sort -u | tr '\n' ',' | sed 's/,$//') \
 && jlink \
      --add-modules "$MODS" \
      --strip-debug --no-man-pages --no-header-files \
      --compress=2 \
      --output /opt/custom-jre

# ── Stage 3: Minimal runtime image ────────────────────────────────────────
FROM alpine:3.21
# ca-certificates provides OS-level trust anchors needed for HTTPS connections
# (Telegram API, Firefly III).  The custom JRE carries its own Java cacerts, but
# native tooling and some JVM providers also consult the OS trust store.
RUN apk add --no-cache ca-certificates
WORKDIR /app
COPY --from=jre-builder /opt/custom-jre /opt/java
COPY --from=jre-builder /tmp/app.jar     app.jar
ENV JAVA_HOME=/opt/java
ENV PATH="/opt/java/bin:$PATH"
ENTRYPOINT ["java", "-jar", "app.jar"]
