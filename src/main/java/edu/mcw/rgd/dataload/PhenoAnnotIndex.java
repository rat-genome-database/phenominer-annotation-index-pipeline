package edu.mcw.rgd.dataload;

import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.process.Utils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pipeline job to populate PHENOMINER_RECORD_IDS table
 */
public class PhenoAnnotIndex {

    Logger log = LogManager.getLogger("summary");
    Dao dao = new Dao();

    static int totalRowsInserted = 0;
    static int totalRowsUpdated = 0;
    static int totalRowsDeleted = 0;
    static int totalRowsUpToDate = 0;

    private String version;
    private Map<Integer, String> ontologies;

    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        PhenoAnnotIndex manager = (PhenoAnnotIndex) (bf.getBean("manager"));

        try {
            manager.runPipeline();
        } catch (Exception e) {
            Utils.printStackTrace(e, manager.log);
            throw e;
        }
    }

    public void runPipeline() throws Exception {

        long time0 = System.currentTimeMillis();
        log.info(getVersion());
        log.info("   "+dao.getConnectionInfo());
        SimpleDateFormat sdt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        log.info("   started at "+sdt.format(new Date(time0)));

        for( Map.Entry<Integer, String> entry: getOntologies().entrySet() ) {

            int speciesTypeKey = entry.getKey();
            String[] ontIds = entry.getValue().split("[,]");

            for( String ontId: ontIds ) {
                run(ontId, null, speciesTypeKey);

                // CS,RS ontology run also for sex: 'male' and 'female'
                if (ontId.equals("RS") || ontId.equals("CS")) {
                    run(ontId, "male", speciesTypeKey);
                    run(ontId, "female", speciesTypeKey);
                }
            }
        }

        if( totalRowsInserted!=0 ) {
            log.info("   rows inserted:  " + Utils.formatThousands(totalRowsInserted));
        }
        if( totalRowsDeleted!=0 ) {
            log.info("   rows deleted:   " + Utils.formatThousands(totalRowsDeleted));
        }
        if( totalRowsUpdated!=0 ) {
            log.info("   rows updated:   " + Utils.formatThousands(totalRowsUpdated));
        }
        if( totalRowsUpToDate!=0 ) {
            log.info("   rows up-to-date: " + Utils.formatThousands(totalRowsUpToDate));
        }
        log.info("=== OK ===  elapsed " + Utils.formatElapsedTime(time0, System.currentTimeMillis()));
    }

    public void run(String ontId, String sex, int speciesTypeKey) throws Exception {

        String msgPrefix = ontId+" sex:"+Utils.NVL(sex,"any")+" "+ SpeciesType.getCommonName(speciesTypeKey);

        List<Record> recordsInRgd = dao.getAllRecords(ontId, sex, speciesTypeKey);
        log.info(msgPrefix+" records in rgd "+Utils.formatThousands(recordsInRgd.size()));

        // compute incoming terms
        List<Record> incomingRecords = new ArrayList<>();

        List<Term> terms = dao.getActiveTerms(ontId);
        for( Term term: terms ) {
            List<Integer> recordIds = dao.getRecordIdsForTermAndDescendants(term.getAccId(), sex, speciesTypeKey);
            if( !recordIds.isEmpty() ) {
                Record r = new Record();
                Collections.sort(recordIds);
                r.setExpRecordIds(Utils.concatenate(recordIds,","));
                r.setTermAcc(term.getAccId());
                r.setSex(sex);
                r.setSpeciesTypeKey(speciesTypeKey);
                incomingRecords.add(r);
            }
        }
        log.info(msgPrefix+" records incoming "+Utils.formatThousands(incomingRecords.size()));

        // insert/update new records if needed
        Collection<Record> recordsToBeInserted = CollectionUtils.subtract(incomingRecords, recordsInRgd);
        int rowsInserted = dao.insertRecords(recordsToBeInserted);
        if( rowsInserted!=0 ) {
            log.info(msgPrefix + " records inserted " + Utils.formatThousands(rowsInserted));
            totalRowsInserted += rowsInserted;
        }

        // delete records if needed
        Collection<Record> recordsToBeDeleted = CollectionUtils.subtract(recordsInRgd, incomingRecords);
        int rowsDeleted = dao.deleteRecords(recordsToBeDeleted);
        if( rowsDeleted!=0 ) {
            log.info(msgPrefix + " records deleted " + Utils.formatThousands(rowsDeleted));
            totalRowsDeleted += rowsDeleted;
        }

        // update records if needed
        Collection<Record> recordsMatching = CollectionUtils.intersection(recordsInRgd, incomingRecords);
        handleMatchingRecords(recordsMatching, recordsInRgd, incomingRecords, msgPrefix);

        log.info("");
    }

    void handleMatchingRecords( Collection<Record> recordsMatching, List<Record> recordsInRgd, List<Record> incomingRecords,
                                String msgPrefix) throws Exception {

        final AtomicInteger rowsUpToDate = new AtomicInteger(0);
        final AtomicInteger rowsUpdated = new AtomicInteger(0); // list of expRecordIds could be updated
        final List<Record> rowsForUpdate = new ArrayList<>();

        recordsMatching.parallelStream().forEach(r -> {
            String termAcc = r.getTermAcc();
            Record rInRgd = null;
            Record rIncoming = null;

            for( Record r1: recordsInRgd ) {
                if( r1.getTermAcc().equals(termAcc) ) {
                    rInRgd = r1;
                    break;
                }
            }

            for( Record r2: incomingRecords ) {
                if( r2.getTermAcc().equals(termAcc) ) {
                    rIncoming = r2;
                    break;
                }
            }

            if( rInRgd.getExpRecordIds().equals(rIncoming.getExpRecordIds()) ) {
                rowsUpToDate.incrementAndGet();
            } else {
                rowsUpdated.incrementAndGet();

                synchronized (rowsForUpdate) {
                    rowsForUpdate.add(rIncoming);
                }
            }
        });

        dao.updateRecords(rowsForUpdate);

        if( rowsUpToDate.get()!=0 ) {
            log.info(msgPrefix + " records up-to-date " + Utils.formatThousands(rowsUpToDate.get()));
            totalRowsUpToDate += rowsUpToDate.get();
        }

        if( rowsUpdated.get()!=0 ) {
            log.info(msgPrefix + " records updated " + Utils.formatThousands(rowsUpdated.get()));
            totalRowsUpdated += rowsUpdated.get();
        }
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setOntologies(Map<Integer,String> ontologies) {
        this.ontologies = ontologies;
    }

    public Map<Integer,String> getOntologies() {
        return ontologies;
    }
}

