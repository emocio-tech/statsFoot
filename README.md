# statsFoot
Analyse sportive

Projet Maven multi-module **Analyse Foot** : backend Spring Boot 3.5 (Java 25) et frontend Angular 20.

## Prérequis

- JDK 25 (`JAVA_HOME` doit pointer dessus)
- Maven : inutile de l'installer, le wrapper `mvnw` télécharge Maven 3.9.16
- Node n'est pas nécessaire pour le build Maven (il est téléchargé dans `frontend/node/`). Il l'est en revanche pour le dev front avec `ng serve`.

## Arborescence

```
.
├── pom.xml                      # POM parent (modules frontend + backend)
├── backend/                     # Spring Boot
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/emocio/analysefoot/
│       │   ├── AnalyseFootApplication.java
│       │   ├── config/          # configuration, routage SPA
│       │   ├── controller/      # API REST (/api/**)
│       │   ├── model/           # DTO / entités
│       │   ├── repository/      # accès aux données
│       │   └── service/         # logique métier
│       ├── main/resources/application.yml
│       └── test/java/...
└── frontend/                    # Angular
    ├── pom.xml                  # build Angular via frontend-maven-plugin
    ├── package.json / angular.json / proxy.conf.json
    └── src/app/
        ├── core/                # services et modèles partagés
        ├── features/            # pages (home, ...)
        └── shared/              # composants réutilisables
```

## Build et lancement

```bash
./mvnw clean package
java -jar backend/target/analyse-foot.jar
```

L'application (front + API) est servie sur http://localhost:8080, et l'API de test sur `/api/status`.

## Développement

```bash
# Terminal 1 : backend
./mvnw -pl backend spring-boot:run

# Terminal 2 : frontend avec rechargement à chaud (proxy /api -> 8080)
cd frontend
npm install
npm start
```

Le front est alors servi sur http://localhost:4200.
