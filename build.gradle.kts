// build.gradle.kts
plugins {
	java
	id("org.springframework.boot") version "4.0.8"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.printops"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

// Parches de seguridad por encima de lo que trae Spring Boot 4.0.8 (CVE críticos/altos
// reportados por Grype/Snyk). Revisar y borrar cuando una versión nueva de Boot los incluya.
extra["tomcat.version"] = "11.0.27"
extra["jackson-bom.version"] = "3.1.7"
extra["jackson-2-bom.version"] = "2.21.7"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	// Endpoint /health para Azure Container Apps (factor IX / probes).
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("com.github.ben-manes.caffeine:caffeine")
	runtimeOnly("org.postgresql:postgresql")
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
	// Generación de PDF (US-11). OpenPDF: LGPL/MPL, fork de iText sin copyleft AGPL.
	implementation("com.github.librepdf:openpdf:1.3.43")
	// Notificaciones push con Firebase Cloud Messaging (US-05).
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
