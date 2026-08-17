# Database Migrations — Consolidated

Database migrations for the subscription-service have been consolidated into the environment-service's unified migration path:

    environment-service/src/main/resources/db/migration/

The environment-service owns and runs all Flyway migrations (V1–V20) for every table in the `sif_broker` database, including the `subscriptions` table (V18). Flyway is disabled in this service (`spring.flyway.enabled: false`), and schema validation is handled by JPA `ddl-auto: validate` at startup.

Do not add migration scripts here. All schema changes should be made in the environment-service migration directory.
