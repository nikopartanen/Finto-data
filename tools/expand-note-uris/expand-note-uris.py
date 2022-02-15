#!/usr/bin/env python

# Expand references to concept URIs to HTML links with concept labels
# in scope notes and definitions. Write the modified vocabulary on stdout.

from __future__ import print_function
from rdflib import Graph, URIRef, Literal
from rdflib.namespace import SKOS
from rdflib.util import guess_format
import re
import sys

inputfile = sys.argv[1]
URIRE = re.compile(r'\[http([^\]]+)\]')

g = Graph()
g.parse(inputfile, format=guess_format(inputfile))

def uri_to_link(sub, prop, lang, matchobj):
    uri = 'http' + matchobj.group(1)
    concept = URIRef(uri)
    labels = g.preferredLabel(concept, lang)
    try:
        label = labels[0][1]
    except IndexError:
        try:
            g2 = Graph()
            g2.parse(uri)
            g2.preferredLabel(concept, lang)
            labels = g2.preferredLabel(concept, lang)
            label = labels[0][1]
            return "<a href='%s'>%s</a>" % (uri, label)
        except:
            print("Concept %s: could not retrieve label for '%s' found in %s in language %s" % (sub, concept, prop, lang), file=sys.stderr)
            return matchobj.group(0) # don't change if we can't find a label
        return matchobj.group(0) # don't change if we can't find a label
    return "<a href='%s'>%s</a>" % (uri, label)
    


NOTEPROPS = (SKOS.note, SKOS.scopeNote, SKOS.definition)
for prop in NOTEPROPS:
    for s,o in g.subject_objects(prop):
        lang = o.language
        if URIRE.search(o) is not None:
            new = URIRE.sub(lambda m:uri_to_link(s, prop, lang, m), o)
            g.remove((s, prop, o))
            g.add((s, prop, Literal(new, lang)))
            

g.serialize(destination=sys.stdout, format='turtle')
