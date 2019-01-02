package edu.mcw.rgd.dataload;

import edu.mcw.rgd.dao.impl.OntologyXDAO;
import edu.mcw.rgd.dao.impl.PhenominerDAO;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import org.apache.log4j.Logger;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.object.MappingSqlQuery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;

/**
 * encapsulates all dao code
 */
public class Dao {

    private Logger logInserted = Logger.getLogger("inserted");
    private Logger logUpdated = Logger.getLogger("updated");
    private Logger logDeleted = Logger.getLogger("deleted");
    private PhenominerDAO pdao = new PhenominerDAO();
    private OntologyXDAO odao = new OntologyXDAO();

    public List<Record> getAllRecords(String ontId, final String sex, final int speciesTypeKey) throws Exception {

        String sql = "SELECT term_acc,experiment_record_ids FROM phenominer_record_ids "+
                "WHERE species_type_key=? AND term_acc LIKE ?";
        if( sex==null ) {
            sql += " AND sex IS NULL";
        } else {
            sql += " AND sex=?";
        }

        MappingSqlQuery<Record> q = new MappingSqlQuery<Record>(pdao.getDataSource(), sql) {
            @Override
            protected Record mapRow(ResultSet rs, int rowNum) throws SQLException {
                Record r = new Record();
                r.setTermAcc(rs.getString(1));
                r.setSex(sex);
                r.setExpRecordIds(rs.getString(2));
                r.setSpeciesTypeKey(speciesTypeKey);
                return r;
            }
        };
        q.declareParameter(new SqlParameter(Types.INTEGER));
        q.declareParameter(new SqlParameter(Types.VARCHAR));
        if( sex!=null ) {
            q.declareParameter(new SqlParameter(Types.VARCHAR));
        }
        q.compile();

        if( sex==null ) {
            return q.execute(speciesTypeKey, ontId+"%");
        } else {
            return q.execute(speciesTypeKey, ontId+"%", sex.substring(0, 1).toUpperCase());
        }
    }

    /**
     * Returns the record ids for a given strain term and its descendant terms
     * @param termId ontology term acc id
     * @return the record ids for a given term and its descendants terms
     * @throws Exception when unexpected error in spring framework occurs
     */
    public List<Integer> getRecordIdsForTermAndDescendants(String termId, String sex, int speciesTypeKey) throws Exception {
        return pdao.getRecordIdsForTermAndDescendants(termId, sex, speciesTypeKey);
    }

    /**
     * get all active terms in given ontology
     * @param ontologyId ontology id
     * @return List of Term objects
     * @throws Exception if something wrong happens in spring framework
     */
    public List<Term> getActiveTerms(String ontologyId) throws Exception {
        return odao.getActiveTerms(ontologyId);
    }

    public int updateRecords(Collection<Record> records) throws Exception {

        String sqlUpdate = "UPDATE phenominer_record_ids SET experiment_record_ids=? "+
            "WHERE term_acc=? AND NVL(sex,'*')=NVL(?,'*') AND species_type_key=?";

        int updated = 0;
        for( Record r: records ) {
            updated += pdao.update(sqlUpdate, r.getExpRecordIds(), r.getTermAcc(), r.getSex(), r.getSpeciesTypeKey());
            logUpdated.info(r.getTermAcc() + " (" + r.getSex() + ") [" + r.getSpeciesTypeKey() + "]: " + r.getExpRecordIds());
        }

        return updated;
    }

    public int insertRecords(Collection<Record> records) throws Exception {

        String sqlInsert = "INSERT INTO phenominer_record_ids "+
                "(experiment_record_ids,term_acc,sex,species_type_key) VALUES(?,?,?,?)";

        int inserted = 0;

        for( Record r: records ) {
            inserted += pdao.update(sqlInsert, r.getExpRecordIds(), r.getTermAcc(), r.getSex(), r.getSpeciesTypeKey());
            logInserted.info(r.getTermAcc()+" ("+r.getSex()+") ["+r.getSpeciesTypeKey()+"]: "+r.getExpRecordIds());
        }

        return inserted;
    }

    public int deleteRecords(Collection<Record> records) throws Exception {

        String sqlNullSex = "DELETE FROM phenominer_record_ids WHERE term_acc=? AND species_type_key=? AND sex IS NULL";
        String sqlWithSex = "DELETE FROM phenominer_record_ids WHERE term_acc=? AND species_type_key=? AND sex=?";

        for( Record r: records ) {
            logDeleted.info(r.getTermAcc()+" ("+r.getSex()+") ["+r.getSpeciesTypeKey()+"]: "+r.getExpRecordIds());
            if( r.getSex()==null ) {
                pdao.update(sqlNullSex, r.getTermAcc(), r.getSpeciesTypeKey());
            } else {
                pdao.update(sqlWithSex, r.getTermAcc(), r.getSpeciesTypeKey(), r.getSex());
            }
        }
        return records.size();
    }
}
