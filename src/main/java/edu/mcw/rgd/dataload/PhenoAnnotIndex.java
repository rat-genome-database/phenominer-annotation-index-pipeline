package edu.mcw.rgd.dataload;

import edu.mcw.rgd.datamodel.SpeciesType;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.process.MemoryMonitor;
import edu.mcw.rgd.process.Utils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Pipeline job to populate PHENOMINER_RECORD_IDS table
 */
public class PhenoAnnotIndex {

    Logger log = LogManager.getLogger("status");
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

        MemoryMonitor memoryMonitor = new MemoryMonitor();
        memoryMonitor.start();

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
        memoryMonitor.stop();
        log.info("   " + memoryMonitor.getSummary());
        log.info("=== OK ===  elapsed " + Utils.formatElapsedTime(time0, System.currentTimeMillis()));
        log.info("");
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
        handleMatchingRecords(recordsMatching, incomingRecords, msgPrefix);

        log.info("");
    }

    void handleMatchingRecords( Collection<Record> recordsMatching, List<Record> incomingRecords,
                                String msgPrefix) throws Exception {

        // index incoming records by term acc (sex and species are constant within a run, so term acc is the key)
        Map<String, Record> incomingByTermAcc = new HashMap<>();
        for( Record r: incomingRecords ) {
            incomingByTermAcc.put(r.getTermAcc(), r);
        }

        // recordsMatching holds the in-rgd objects; compare each against its incoming counterpart
        int rowsUpToDate = 0;
        List<Record> rowsForUpdate = new ArrayList<>();
        for( Record rInRgd: recordsMatching ) {
            Record rIncoming = incomingByTermAcc.get(rInRgd.getTermAcc());
            if( rInRgd.getExpRecordIds().equals(rIncoming.getExpRecordIds()) ) {
                rowsUpToDate++;
            } else {
                rowsForUpdate.add(rIncoming);
            }
        }

        dao.updateRecords(rowsForUpdate);

        if( rowsUpToDate!=0 ) {
            log.info(msgPrefix + " records up-to-date " + Utils.formatThousands(rowsUpToDate));
            totalRowsUpToDate += rowsUpToDate;
        }

        if( !rowsForUpdate.isEmpty() ) {
            log.info(msgPrefix + " records updated " + Utils.formatThousands(rowsForUpdate.size()));
            totalRowsUpdated += rowsForUpdate.size();
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

