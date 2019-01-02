# run PhenominerAnnotationIndex pipeline with commandline parameters
#    ("$@" passes all cmdline parameters to pipeline program)
#
. /etc/profile

APPNAME=PhenominerAnnotationIndex
APPDIR=/home/rgddata/pipelines/$APPNAME
SERVER=`hostname -s | tr '[a-z]' '[A-Z]'`
EMAIL_LIST=mtutaj@mcw.edu

cd $APPDIR

java -Dspring.config=$APPDIR/../properties/default_db.xml \
    -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
    -jar lib/${APPNAME}.jar "$@" 2>&1

mailx -s "[$SERVER] Output from phenominer annotation index pipeline" $EMAIL_LIST < $APPDIR/logs/summary.log
