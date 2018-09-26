package edu.mcw.rgd.dataload;

import edu.mcw.rgd.process.Utils;

/**
 * @author mtutaj
 * @since 6/19/2017
 * <p>
 * represents a row in PHENOMINER_RECORD_IDS table
 */
public class Record {

    private String termAcc;
    private String expRecordIds;
    private String sex;
    private int speciesTypeKey;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Record r = (Record) o;

        return Utils.stringsAreEqual(this.termAcc, r.termAcc) &&
                Utils.stringsAreEqual(this.sex, r.sex) &&
//                Utils.stringsAreEqual(this.expRecordIds, r.expRecordIds) &&
                this.speciesTypeKey==r.speciesTypeKey;
    }

    @Override
    public int hashCode() {
        int result = termAcc.hashCode();
        result = 31 * result + Utils.defaultString(sex).hashCode();
  //      result = 31 * result + expRecordIds.hashCode();
        result = 31 * result + speciesTypeKey;
        return result;
    }


    public String getTermAcc() {
        return termAcc;
    }

    public void setTermAcc(String termAcc) {
        this.termAcc = termAcc;
    }

    public String getExpRecordIds() {
        return expRecordIds;
    }

    public void setExpRecordIds(String expRecordIds) {
        this.expRecordIds = expRecordIds;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex){
        String sexInDb = sex==null ? null :
                sex.equals("male") ? "M" :
                sex.equals("female") ? "F" : null;
        this.sex = sexInDb;
    }

    public int getSpeciesTypeKey() {
        return speciesTypeKey;
    }

    public void setSpeciesTypeKey(int speciesTypeKey) {
        this.speciesTypeKey = speciesTypeKey;
    }
}
