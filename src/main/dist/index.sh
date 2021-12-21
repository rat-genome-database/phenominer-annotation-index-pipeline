# run PhenominerAnnotationIndex pipeline with commandline parameters
#    ("$@" passes all cmdline parameters to pipeline program)
#
. /etc/profile

APPNAME="phenominer-annotation-index-pipeline"
APPDIR=/home/rgddata/pipelines/$APPNAME
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
EMAIL_LIST=mtutaj@mcw.edu

cd $APPDIR

java -Dspring.config=$APPDIR/../properties/default_db2.xml \
    -Dlog4j.configurationFile=file://$APPDIR/properties/log4j2.xml \
    -jar lib/${APPNAME}.jar "$@" 2>&1 > $APPDIR/run.log

mailx -s "[$SERVER] Output from phenominer annotation index pipeline" $EMAIL_LIST < $APPDIR/logs/summary.log
