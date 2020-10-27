#!/bin/sh

INFILES="finaf-metadata.ttl rdaa.rdf rdac.rdf rdap.rdf rdau.rdf finaf.ttl"
OUTFILE=finaf-skos.ttl

SKOSIFYCMD="skosify"
LOGFILE=skosify.log
OPTS="--set-modified --no-mark-top-concepts --no-enrich-mappings --namespace http://urn.fi/URN:NBN:fi:au:finaf:"

$SKOSIFYCMD $OPTS $INFILES -o $OUTFILE 2>$LOGFILE
