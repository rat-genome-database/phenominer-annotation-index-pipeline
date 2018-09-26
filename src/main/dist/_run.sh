#!/usr/bin/env bash
# shell script to run PhenominerAnnotationIndex pipeline
. /etc/profile

APPNAME=PhenominerAnnotationIndex
APPDIR=/home/rgddata/pipelines/$APPNAME

cd $APPDIR

DB_OPTS="-Dspring.config=$APPDIR/../properties/default_db.xml"
LOG4J_OPTS="-Dlog4j.configuration=file://$APPDIR/properties/log4j.properties"
export PHENOMINER_ANNOTATION_INDEX_OPTS="$DB_OPTS $LOG4J_OPTS"

bin/$APPNAME "$@"
