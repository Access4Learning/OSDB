-- V48: Normalise provider_registry.supported_request_types to the SIF 3.7 nested operations form.
--
-- D1 (requirement 2.13) reshaped the `operations` member of each supportedRequest entry from a bare
-- array into an object carrying a singular `operation` array:
--
--   before:  [{"requestType":"IMMEDIATE","operations":["QUERY"]}]
--   after:   [{"requestType":"IMMEDIATE","operations":{"operation":["QUERY"]}}]
--
-- Every row the application wrote before that change holds the old form, as do rows seeded by
-- V27__align_provider_registry_with_sif37.sql. The reshaped SupportedRequestType cannot bind to the
-- old form at all, so reading such a row fails outright -- and because Hibernate throws while
-- materialising the entity, a single stale row fails the whole query rather than just that row.
-- Provider lookup sits on that read path, so stale rows stop request routing, not just admin reads.
--
-- Rewriting the data here, rather than teaching the read path to accept both forms, keeps the
-- tolerance out of the running system entirely: there is no shim to remove later, and no code path
-- through which the non-conforming form could be accepted over HTTP.
--
-- Idempotent: the EXISTS guard makes a second run a no-op, and rows already in the nested form are
-- never rewritten. Entry order is preserved via WITH ORDINALITY, because round-trip assertions on
-- supportedRequestType compare positionally. Rows holding an empty array are skipped by the guard,
-- so the NOT NULL constraint on the column is never at risk (jsonb_agg over no rows returns NULL).

UPDATE provider_registry
SET supported_request_types = (
        SELECT jsonb_agg(
                   CASE
                       WHEN jsonb_typeof(elem.entry -> 'operations') = 'array'
                           THEN jsonb_set(elem.entry, '{operations}',
                                          jsonb_build_object('operation', elem.entry -> 'operations'))
                       ELSE elem.entry
                   END
                   ORDER BY elem.ord
               )
        FROM jsonb_array_elements(supported_request_types) WITH ORDINALITY AS elem(entry, ord)
    )
WHERE jsonb_typeof(supported_request_types) = 'array'
  AND EXISTS (
      SELECT 1
      FROM jsonb_array_elements(supported_request_types) AS probe(entry)
      WHERE jsonb_typeof(probe.entry -> 'operations') = 'array'
  );
