#!/bin/sh

INFILES="lajisto-metadata.ttl finto_biota.ttl"
OUTFILE=lajisto-skos.ttl

SKOSIFYCMD="skosify"
LOGFILE=skosify.log
OPTS="-c lajisto.cfg --namespace http://tun.fi/"


$SKOSIFYCMD $OPTS $INFILES -o $OUTFILE 2>$LOGFILE
