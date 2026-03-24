# phenominer-annotation-index-pipeline

Populates the `PHENOMINER_RECORD_IDS` table with denormalized data to improve performance of PhenoMiner tool queries.

## Overview

For each ontology term, the pipeline determines the active experiment records associated with that term and all its descendants. The matching record IDs are concatenated and stored as a single row in `PHENOMINER_RECORD_IDS`, keyed by term accession, species, and sex.

## Ontologies processed

RS, CS, CMO, MMO, XCO, VT

## Species

Configured in `properties/AppConfigure.xml`:
- Rat (species_type_key=3): RS, CMO, MMO, XCO, VT
- Mouse (species_type_key=4): CS, CMO, MMO, XCO, VT

## Sex handling

- All ontologies are processed with sex=null (any sex)
- RS and CS ontologies are additionally processed for sex='M' (male) and sex='F' (female)

## QC logic

For each ontology/species/sex combination:
1. Load existing records from `PHENOMINER_RECORD_IDS`
2. Compute incoming records from active ontology terms and their descendants
3. **Insert** new records not yet in the database
4. **Delete** records no longer supported by incoming data
5. **Update** records where the set of experiment record IDs has changed
6. Skip records that are already up-to-date

## Logging

- `status` — main pipeline progress and summary counters
- `inserted` / `updated` / `deleted` — audit logs for each changed record

## Build and run

Requires Java 17. Built with Gradle:
```
./gradlew clean assembleDist
```