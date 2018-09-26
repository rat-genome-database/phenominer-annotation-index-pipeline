# phenominer-annotation-index-pipeline
Populate table populate table PHENOMINER_RECORD_IDS.

<b>LOGIC</b>

processes RS, CS, MMO, CMO and CMO ontologies

sex: all ontologies are processed for sex=null
     CS,RS ontology is also processed for sex='M' (male) and sex='F' (female)

for every ontology term it is determined how many active experiment records are available;
      if there are any, the corresponding experiment record ids are concatenated by ','
      and stored in the table PHENOMINER_RECORD_IDS
