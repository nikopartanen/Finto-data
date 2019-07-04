import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.vocabulary.DC;
import com.hp.hpl.jena.vocabulary.DCTerms;
import com.hp.hpl.jena.vocabulary.OWL;
import com.hp.hpl.jena.vocabulary.RDF;
import com.hp.hpl.jena.vocabulary.RDFS;

public class Kokoaja2 {

	private final String skosNs = "http://www.w3.org/2004/02/skos/core#";
	private final String skosextNs = "http://purl.org/finnonto/schema/skosext#";
	private final String kokoNs ="http://www.yso.fi/onto/koko/";
	private final String kokoMetaNs ="http://www.yso.fi/onto/koko-meta/";

	private Model koko;
	private Model onto;

	private HashMap<Resource, String> ontologioidenTyypitPolutMap;
	private HashMap<String, Resource> kokoFiLabelitResurssitMap;
	private HashMap<Resource, String> ontoFiResurssitLabelitMap;
	private HashMap<Resource, Resource> ontoKokoResurssivastaavuudetMap;
	private HashMap<Resource, Resource> ontoKokoResurssivastaavuudetJotkaNykyKokossaMap;

	private HashSet<String> sallittujenPropertyjenNimiavaruudet;
	private HashSet<Resource> mustaLista;

	private int viimeisinKokoUrinLoppuosa;

	private int romautetut;
	private int labelinPerusteellaMuuhunKuinYsoonYhdistyneet;

	public Kokoaja2(String uriVastaavuuksiePolku) {
		this.romautetut = 0;
		this.labelinPerusteellaMuuhunKuinYsoonYhdistyneet = 0;
		this.taytaSallittujenPropertyjenNimiavaruudet();
		this.koko = this.luoAihio();
		this.lueUriVastaavuudetTiedostosta(uriVastaavuuksiePolku);

		this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap = new HashMap<Resource, Resource>();
		this.kokoFiLabelitResurssitMap = new HashMap<String, Resource>();
	}

	public Model luoAihio() {
		Model aihio = ModelFactory.createDefaultModel();
		aihio.setNsPrefix("skos", this.skosNs);
		aihio.setNsPrefix("skosext", this.skosextNs);
		aihio.setNsPrefix("koko", this.kokoNs);
		aihio.setNsPrefix("koko-meta", this.kokoMetaNs);
		return aihio;
	}

	public void taytaSallittujenPropertyjenNimiavaruudet() {
		this.sallittujenPropertyjenNimiavaruudet = new HashSet<String>();
		this.sallittujenPropertyjenNimiavaruudet.add(this.skosNs);
		this.sallittujenPropertyjenNimiavaruudet.add(this.skosextNs);
		this.sallittujenPropertyjenNimiavaruudet.add(DC.getURI());
		this.sallittujenPropertyjenNimiavaruudet.add(OWL.getURI());
		this.sallittujenPropertyjenNimiavaruudet.add(RDF.getURI());
		this.sallittujenPropertyjenNimiavaruudet.add(RDFS.getURI());
		this.sallittujenPropertyjenNimiavaruudet.add(DCTerms.getURI());
		this.sallittujenPropertyjenNimiavaruudet.add("http://purl.org/iso25964/skos-thes#");
	}

	public void lueUriVastaavuudetTiedostosta(String polku) {
		System.out.println("Luetaan URI-vastaavuudet tiedostosta " + polku);
		int korkeinNro = 0;
		this.ontoKokoResurssivastaavuudetMap = new HashMap<Resource, Resource>();
		try {
			BufferedReader br = new BufferedReader( new InputStreamReader( new FileInputStream(polku), "UTF8" ) );
			String rivi = br.readLine().trim();
			while (rivi != null) {				
				rivi = rivi.trim();
				String[] uriTaulukko = rivi.split(" ");
				Resource ontoRes = this.koko.createResource(uriTaulukko[0].trim());
				Resource kokoRes = this.koko.createResource(uriTaulukko[1].trim());
				this.ontoKokoResurssivastaavuudetMap.put(ontoRes, kokoRes);
				int uriNro = Integer.parseInt(kokoRes.getLocalName().substring(1));
				if (uriNro > korkeinNro) korkeinNro = uriNro;
				rivi = br.readLine();
			}
			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.viimeisinKokoUrinLoppuosa = korkeinNro;
	}

	public void lueYso(String ysonPolku) {
		this.onto = JenaHelpers.lueMalliModeliksi(ysonPolku); 

		// luodaan YSOConcept-tyyppiluokka
		this.luoYSOConceptTyyppiLuokkaKokoon();
		String ysoMetaNs = "http://www.yso.fi/onto/yso-meta/";
		Resource ysoConcept = this.koko.createResource(ysoMetaNs + "Concept");

		Property skosInScheme = this.koko.createProperty(this.skosNs + "inScheme");
		Resource ysoConceptScheme = this.koko.createResource("http://www.yso.fi/onto/yso/");
		ResIterator resIter = this.onto.listResourcesWithProperty(skosInScheme, ysoConceptScheme);
		while (resIter.hasNext()) {
			Resource ysoSubj = resIter.nextResource();
			if (!this.mustaLista.contains(ysoSubj)) {
				this.lisaaResurssiKokoon(ysoSubj, ysoSubj, true);
				this.koko.add(ysoSubj, RDF.type, ysoConcept);
			}
		}

		// kaivetaan KOKOon viela isReplacedBy-tyyppiset suhteet
		StmtIterator iter = this.onto.listStatements((Resource)null, DCTerms.isReplacedBy, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			Resource subject = stmt.getSubject();
			Resource object = stmt.getResource();
			if (!this.mustaLista.contains(subject)) {

				//Jottei tapahtuisi jakautuvia korvaavuussuhteita, valitaan jokaiselle replaced-suhteelle vain yksi sopiva seuraaja
				object = this.haeKorvaava(subject, this.onto);
				this.koko.add(subject, DCTerms.isReplacedBy, object);
				this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.put(subject, object);
			}
		}
	}

	private void luoYSOConceptTyyppiLuokkaKokoon() {
		String ysoMetaNs = "http://www.yso.fi/onto/yso-meta/";
		Resource skosConcept = this.onto.createResource(this.skosNs + "Concept");

		Resource ysoConcept = this.koko.createResource(ysoMetaNs + "Concept");
		Literal fiLabel = this.koko.createLiteral("YSO-käsite", "fi");
		Literal enLabel = this.koko.createLiteral("YSO Concept", "en");
		Literal svLabel = this.koko.createLiteral("Allfo-begrepp", "sv");
		this.koko.add(ysoConcept, RDF.type, OWL.Class);
		this.koko.add(ysoConcept, RDFS.label, fiLabel);
		this.koko.add(ysoConcept, RDFS.label, enLabel);
		this.koko.add(ysoConcept, RDFS.label, svLabel);
		this.koko.add(ysoConcept, RDFS.subClassOf, skosConcept);
	}

	private HashMap<Resource, String> haeTietynTyyppistenResurssienLabelitMappiin(Model malli, Property labelProp, HashSet<Resource> tietynTyyppisetResurssit, String kieli) {
		HashMap<Resource, String> resurssitLabelitMap = new HashMap<Resource, String>();
		StmtIterator iter = malli.listStatements((Resource)null, labelProp, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (stmt.getLanguage().equals(kieli)) {
				Resource subj = stmt.getSubject();
				if (tietynTyyppisetResurssit.contains(subj)) {
					String labelString = ((Literal)(stmt.getObject())).getLexicalForm();
					resurssitLabelitMap.put(subj, labelString);
				}
			}
		}
		return resurssitLabelitMap;
	}

	/*
	 *  primaryLabelSource on true YSOlle, jolloin YSOn prefLabelit otetaan sellaisinaan ja muille false
	 *  jolloin prefLabeleista tulee skosext:candidateLabeleita
	 */
	private void lisaaResurssiKokoon(Resource ontoSubj, Resource kokoSubj, boolean primaryLabelSource) {
		Property skosExactMatch = this.onto.createProperty(this.skosNs + "exactMatch");
		Property skosPrefLabel = this.onto.createProperty(skosNs + "prefLabel");
		Property skosextCandidateLabel = this.onto.createProperty(skosextNs + "candidateLabel");

		HashSet<Property> propertytJoitaEiHalutaKokoon = new HashSet<Property>();
		propertytJoitaEiHalutaKokoon.add(this.onto.createProperty(this.skosNs + "inScheme"));
		propertytJoitaEiHalutaKokoon.add(this.onto.createProperty(this.skosNs + "topConceptOf"));
		propertytJoitaEiHalutaKokoon.add(this.onto.createProperty(this.skosNs + "narrower"));
		propertytJoitaEiHalutaKokoon.add(DCTerms.modified);
		propertytJoitaEiHalutaKokoon.add(DCTerms.created);

		this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.put(ontoSubj, kokoSubj);
		//this.kokoFiLabelitResurssitMap.put(this.ontoFiResurssitLabelitMap.get(ontoSubj), kokoSubj);
		this.koko.add(kokoSubj, skosExactMatch, ontoSubj);
		StmtIterator iter = this.onto.listStatements(ontoSubj, (Property)null, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (!(stmt.getObject().isURIResource() && this.mustaLista.contains(stmt.getObject()))) {
				if (this.sallittujenPropertyjenNimiavaruudet.contains(stmt.getPredicate().getNameSpace()))
					if (stmt.getPredicate().equals(skosPrefLabel)) {
						if (primaryLabelSource) {
							this.koko.add(kokoSubj, stmt.getPredicate(), stmt.getObject());
						} else {
							this.koko.add(kokoSubj, skosextCandidateLabel, stmt.getObject());
						}
					} else if (!propertytJoitaEiHalutaKokoon.contains(stmt.getPredicate())) {

						//Lisätty tarkistus siitä ettei kokoon oteta ysossa olevia kokoon viittaavia exactMatcheja

						if (stmt.getObject().isURIResource() ) {
							if ( !((Resource)stmt.getObject()).getNameSpace().equals(this.kokoNs) )
								this.koko.add(kokoSubj, stmt.getPredicate(), stmt.getObject());
						} else {
							//Literaaleille ei tällaista terkistusta tehdä
							this.koko.add(kokoSubj, stmt.getPredicate(), stmt.getObject());
						}
					}
			}
		}
	}

	public void lueOnto(String ontonPolku, Resource ontoTyyppi) {
		System.out.println("Luetaan " + ontonPolku);
		this.onto = JenaHelpers.lueMalliModeliksi(ontonPolku);
		this.onto = this.teeExactMatcheistaKaksisuuntaisia(this.onto);

		StmtIterator iter = this.onto.listStatements(ontoTyyppi, (Property)null, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			this.koko.add(stmt);
		}

		// kaivetaan HashSettiin kaikki erikoisontologian oman tyyppiset kasitteet
		HashSet<Resource> ontonOntoTyyppisetResurssit = new HashSet<Resource>();
		ResIterator resIter = this.onto.listResourcesWithProperty(RDF.type, ontoTyyppi);
		Resource deprecated = this.onto.getResource(skosextNs+"DeprecatedConcept");

		while (resIter.hasNext()) {
			Resource ontoSubj = resIter.nextResource();
			//lisättiin tarkistus deprekoitujen käsitteiden välttämiseksi

			if (!this.mustaLista.contains(ontoSubj) && !this.onto.contains(ontoSubj, RDF.type, deprecated)) {
				ontonOntoTyyppisetResurssit.add(ontoSubj);
			}
		}
		System.out.println("Lisataan KOKOon " + ontonOntoTyyppisetResurssit.size() + " " + ontoTyyppi.getURI() + " -tyyppista resurssia.");

		// kaivetaan HashMappiin kaikki erikoisontologian suorat skos:exactMatchit
		HashMap<Resource, HashSet<Resource>> ontonExactMatchitMap = new HashMap<Resource, HashSet<Resource>>();
		Property skosExactMatch = this.onto.createProperty(this.skosNs + "exactMatch");
		iter = this.onto.listStatements((Resource)null, skosExactMatch, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			Resource subj = stmt.getSubject();
			Resource obj = (Resource)stmt.getObject();
			//Lisätty tarkistus siitä ettei exactMatcheja kokoon haeta ontologioista (ts. Koko kootaan ysoon osoittavien exactMatchien perusteella)
			if (ontonOntoTyyppisetResurssit.contains(subj) && !obj.getNameSpace().equals(this.kokoNs)) {
				//lisätty tarkistus siitä ettei linkata deprekoituihin resursseihin
				if ( !obj.hasProperty(DCTerms.isReplacedBy) ) {
					HashSet<Resource> matchitSet = new HashSet<Resource>();
					if (ontonExactMatchitMap.containsKey(subj)) {
						matchitSet = ontonExactMatchitMap.get(subj);
					}
					matchitSet.add((Resource)(stmt.getObject()));
					ontonExactMatchitMap.put(subj, matchitSet);
				}
			}
		}

		/* Ei kayteta tata, koska romautus tehdaan prefLabelin lisaksi vanhemman perusteella ja hoidetaan myohemmin
		// kaivetaan HashMappiin kaikki erikoisontologian fiLabelit
		Property skosPrefLabel = this.onto.createProperty(this.skosNs + "prefLabel");
		this.ontoFiResurssitLabelitMap = this.haeTietynTyyppistenResurssienLabelitMappiin(this.onto, skosPrefLabel, ontonOntoTyyppisetResurssit, "fi");
		 */
		Vector<String> ontoUritVektori = new Vector<String>();
		resIter = this.onto.listResourcesWithProperty(RDF.type, ontoTyyppi);
		while (resIter.hasNext()) {
			Resource ontoSubj = resIter.nextResource();
			if (!this.mustaLista.contains(ontoSubj) && !this.onto.contains(ontoSubj, RDF.type, deprecated)) {
				ontoUritVektori.add(ontoSubj.getURI());
			}
		}
		Collections.sort(ontoUritVektori);

		for (String uri:ontoUritVektori) {
			Resource ontoSubj = this.onto.createResource(uri);
			Resource kokoSubj = null;

			// Tutkitaan mika on resurssia vastaava resurssi kokossa tai jos moista ei ole, paatetaan sailotaanko vai ei
			if (this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.containsKey(ontoSubj)) {
				kokoSubj = ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.get(ontoSubj);
			} else if (ontonExactMatchitMap.containsKey(ontoSubj)) {
				for (Resource matchRes:ontonExactMatchitMap.get(ontoSubj)) {
					if (this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.containsKey(matchRes)) {
						kokoSubj = this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.get(matchRes);
					}
				}
			}
			/* Ei kayteta tata, koska romautus tehdaan prefLabelin lisaksi vanhemman perusteella ja hoidetaan myohemmin
				if (kokoSubj == null && this.ontoFiResurssitLabelitMap.containsKey(ontoSubj)) {
					String ontoLabel = ontoFiResurssitLabelitMap.get(ontoSubj);
					if (this.kokoFiLabelitResurssitMap.containsKey(ontoLabel)) {
						kokoSubj = this.kokoFiLabelitResurssitMap.get(ontoLabel);
						kasiteltavanOntologianLabelinPerusteellaRomautetutKasitteet++;
						if (kokoSubj.getNameSpace().equals("http://www.yso.fi/onto/yso/")) {
							this.labelinPerusteellaYsoonYhdistyneet++;
							//System.out.println(kasiteltavanOntologianLabelinPerusteellaRomautetutKasitteet + ";ye;" + this.labelinPerusteellaYsoonYhdistyneet + ";" + kokoSubj.getURI() + ";" + ontoSubj.getURI() + ";" + ontoLabel);
						} else {
							this.labelinPerusteellaMuuhunKuinYsoonYhdistyneet++;
							//System.out.println(kasiteltavanOntologianLabelinPerusteellaRomautetutKasitteet + ";ee;" + this.labelinPerusteellaMuuhunKuinYsoonYhdistyneet+ ";" + kokoSubj.getURI() + ";" + ontoSubj.getURI() + ";" + ontoLabel);
						}
					}
				}*/
			if (kokoSubj == null) kokoSubj = ontoSubj;

			// laitetaan kasite ja siihen liittyvat triplet KOKOon					
			this.lisaaResurssiKokoon(ontoSubj, kokoSubj, false);
			if (ontonExactMatchitMap.containsKey(ontoSubj)) {
				for (Resource matchRes:ontonExactMatchitMap.get(ontoSubj)) {
					this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.put(matchRes, kokoSubj);
				}
			}
		}


		// kaivetaan KOKOon viela isReplacedBy-tyyppiset suhteet
		iter = this.onto.listStatements((Resource)null, DCTerms.isReplacedBy, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			Resource subject = stmt.getSubject();

			//Jottei tapahtuisi jakautuvia korvaavuussuhteita, valitaan jokaiselle replaced-suhteelle vain yksi sopiva seuraaja
			Resource object = this.haeKorvaava(subject, this.onto);
			this.koko.add(subject, DCTerms.isReplacedBy, object);
		}
	}

	public Model teeExactMatcheistaKaksisuuntaisia(Model ontologia) {
		Property skosExactMatch = ontologia.createProperty(this.skosNs + "exactMatch");
		HashSet<Statement> lisattavat = new HashSet<Statement>();

		HashSet<Resource> resurssitJoillaExactMatcheja = new HashSet<Resource>();
		ResIterator resIter = ontologia.listResourcesWithProperty(skosExactMatch);
		while (resIter.hasNext()) resurssitJoillaExactMatcheja.add(resIter.nextResource());

		for (Resource res:resurssitJoillaExactMatcheja) {
			HashSet<Resource> exactMatchienKohteet = new HashSet<Resource>();
			StmtIterator iter = ontologia.listStatements(res, skosExactMatch, (RDFNode)null);
			while (iter.hasNext()) {
				Statement stmt = iter.nextStatement();
				lisattavat.add(ontologia.createStatement((Resource)(stmt.getObject()), skosExactMatch, stmt.getSubject()));
				exactMatchienKohteet.add((Resource)stmt.getObject());
			}
			for (Resource res2:exactMatchienKohteet) {
				for (Resource res3:exactMatchienKohteet) {
					if (!res2.equals(res3)) {
						lisattavat.add(ontologia.createStatement(res2, skosExactMatch, res3));
						lisattavat.add(ontologia.createStatement(res3, skosExactMatch, res2));
					}
				}
			}
		}
		//	

		for (Statement s:lisattavat) ontologia.add(s);
		return ontologia;
	}

	public Resource luoUusiKokoResurssi() {
		this.viimeisinKokoUrinLoppuosa++;
		Resource uusiResurssi = this.koko.createResource(this.kokoNs + "p" + this.viimeisinKokoUrinLoppuosa);
		return uusiResurssi;
	}

	private void muutaCandidateLabelitPrefJaAltLabeleiksi() {
		Property skosPrefLabel = this.onto.createProperty(skosNs + "prefLabel");
		Property skosAltLabel = this.onto.createProperty(skosNs + "altLabel");
		Property skosextCandidateLabel = this.onto.createProperty(skosextNs + "candidateLabel");

		HashSet<Statement> poistettavat = new HashSet<Statement>();
		HashSet<Statement> lisattavat = new HashSet<Statement>();

		ResIterator resIter = this.koko.listResourcesWithProperty(skosextCandidateLabel);
		while (resIter.hasNext()) {
			Resource subj = resIter.nextResource();
			HashSet<String> fiPrefLabelSet = new HashSet<String>();
			HashSet<String> fiCandidateLabelSet = new HashSet<String>();

			StmtIterator iter = this.koko.listStatements(subj, skosPrefLabel, (RDFNode)null);
			while (iter.hasNext()) {
				Statement stmt = iter.nextStatement();
				if (stmt.getLanguage().equals("fi")) {
					fiPrefLabelSet.add(((Literal)stmt.getObject()).getLexicalForm());
					poistettavat.add(stmt);
				}
			}
			iter = this.koko.listStatements(subj, skosextCandidateLabel, (RDFNode)null);
			while (iter.hasNext()) {
				Statement stmt = iter.nextStatement();
				if (stmt.getLanguage().equals("fi")) {
					fiCandidateLabelSet.add(((Literal)stmt.getObject()).getLexicalForm());
					poistettavat.add(stmt);
				}
			}

			if (fiPrefLabelSet.size() > 0) {
				String prefLabelString = this.palautaPrefLabeliksiSopivin(fiPrefLabelSet);
				lisattavat.add(this.koko.createStatement(subj, skosPrefLabel, this.koko.createLiteral(prefLabelString, "fi")));
				fiPrefLabelSet.remove(prefLabelString);
				for (String altLabelString:fiPrefLabelSet) {
					lisattavat.add(this.koko.createStatement(subj, skosAltLabel, this.koko.createLiteral(altLabelString, "fi")));
				}
				for (String altLabelString:fiCandidateLabelSet) {
					lisattavat.add(this.koko.createStatement(subj, skosAltLabel, this.koko.createLiteral(altLabelString, "fi")));
				}
			} else if (fiCandidateLabelSet.size() > 0) {
				String prefLabelString = this.palautaPrefLabeliksiSopivin(fiCandidateLabelSet);
				lisattavat.add(this.koko.createStatement(subj, skosPrefLabel, this.koko.createLiteral(prefLabelString, "fi")));
				fiCandidateLabelSet.remove(prefLabelString);
				for (String altLabelString:fiCandidateLabelSet) {
					lisattavat.add(this.koko.createStatement(subj, skosAltLabel, this.koko.createLiteral(altLabelString, "fi")));
				}
			} else {
				// Ei loytynyt mitaan jarkevia labeleita, joten poistetaan kokonaan KOKOsta
				System.out.println("Kasitteella " + subj.getURI() + " ei ollut jarkevia labeleita eika sita lisatty KOKOon.");
				iter = this.koko.listStatements(subj, (Property)null, (RDFNode)null);
				while (iter.hasNext()) poistettavat.add(iter.nextStatement());	
			}
		}
		for (Statement stmt:poistettavat) this.koko.remove(stmt);
		for (Statement stmt:lisattavat) this.koko.add(stmt);
	}

	// palautetaan paras prefLabel joukosta kandidaatteja - ensimmäinen kriteeri on sulkutarkenteet (jos on, hyvä), toinen pituus (lyhyempi parempi), kolmas aakkosjärjestys
	public String palautaPrefLabeliksiSopivin(HashSet<String> kandidaatitSet) {
		String paras = null;
		for (String kandidaatti:kandidaatitSet) {
			if (paras == null) paras = kandidaatti;
			else if (paras.contains("(") && !kandidaatti.contains("(")) ; 
			else if (!paras.contains("(") && kandidaatti.contains("(")) paras = kandidaatti;
			else if (paras.length() > kandidaatti.length()) paras = kandidaatti;
			else if (paras.compareToIgnoreCase(kandidaatti) > 0) paras = kandidaatti;
		}
		//System.out.println(paras + " <== " + kandidaatitSet.toString());
		return paras;
	}

	private void romautaFiPrefLabelienJaVanhempienPerusteella() {
		Property skosBroader = this.onto.createProperty(skosNs + "broader");

		HashMap<String, HashSet<Resource>> fiPrefLabelKokoSubjektitMap = this.tuotaPrefLabelKokoSubjektitMap("fi");

		Vector<String> fiLabelAvaimet = new Vector<String>(); 
		for (String avain:fiPrefLabelKokoSubjektitMap.keySet()) {
			fiLabelAvaimet.add(avain);
		}
		Collections.sort(fiLabelAvaimet);

		for (String fiPrefLabel:fiLabelAvaimet) {
			HashSet<Resource> subjektitSet = fiPrefLabelKokoSubjektitMap.get(fiPrefLabel);
			if (subjektitSet.size() > 1) {
				// loytyi useampi sama prefLabel - luodaan HashMap, jossa avaimina on broaderien labelit ja arvoina resurssit
				HashMap<String, HashSet<Resource>> broaderStringResSetMap = new HashMap<String, HashSet<Resource>>();
				for (Resource res:subjektitSet) {
					Vector<String> broaderitVector = new Vector<String>();
					StmtIterator iter = this.koko.listStatements(res, skosBroader, (RDFNode)null);
					while (iter.hasNext()) {
						Statement stmt = iter.nextStatement();
						Resource broader = (Resource)(stmt.getObject());
						if (this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.containsKey(broader))
							broader = this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.get(broader);
						String broaderinFiPrefLabel = this.haeFiPrefLabel(broader);
						if (broaderinFiPrefLabel.equals("")) {
							broaderinFiPrefLabel = this.haeFiPrefLabel(this.haeVastineRomautuksenKesken(broader));
						}
						if (!broaderitVector.contains(broaderinFiPrefLabel)) {
							if (broaderinFiPrefLabel.equals("")) System.out.println("Ongelma romautuksien labeleissa: " + broaderinFiPrefLabel + " -- " + broader.getURI() + " -- " + res.getURI() + " " + fiPrefLabel);
							broaderitVector.add(broaderinFiPrefLabel);
						}
					}
					Collections.sort(broaderitVector);
					String broaderitString = "";
					for (int i = 0; i < broaderitVector.size(); i++) {
						if (broaderitVector.get(i).trim().length() > 0) {
							broaderitString += broaderitVector.get(i);
							if (i < broaderitVector.size()-1) broaderitString += ", ";
						}
					}
					HashSet<Resource> resSet = new HashSet<Resource>();
					if (broaderStringResSetMap.containsKey(broaderitString)) resSet = broaderStringResSetMap.get(broaderitString);
					resSet.add(res);
					broaderStringResSetMap.put(broaderitString, resSet);
				}
				HashMap<String, Resource> romautettuMap = new HashMap<String, Resource>();
				for (String broaderitString:broaderStringResSetMap.keySet()) {
					HashSet<Resource> resSet = broaderStringResSetMap.get(broaderitString);
					Resource romautettuRes = null;
					if (resSet.size() > 1) {
						romautettuRes = this.romauta(resSet);
					} else {
						for (Resource res:resSet) romautettuRes = res;
					}
					romautettuMap.put(broaderitString, romautettuRes);
				}
				if (romautettuMap.size() > 1) {
					this.lisaaSulkutarkenteet(romautettuMap);
				}
			}
		}
	}

	private Resource haeVastineRomautuksenKesken(Resource res) {
		Property skosExactMatch = this.koko.createProperty(this.skosNs + "exactMatch");
		Resource palautettava = res;

		StmtIterator iter = this.koko.listStatements((Resource)null, skosExactMatch, res);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			palautettava = stmt.getSubject();
		}
		iter = this.koko.listStatements(res, DCTerms.isReplacedBy, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			palautettava = (Resource)(stmt.getObject());
		}
		return palautettava;
	}

	private String haeFiPrefLabel(Resource subj) {
		Property skosPrefLabel = this.koko.createProperty(skosNs + "prefLabel");
		String prefLabelString = "";
		StmtIterator iter = this.koko.listStatements(subj, skosPrefLabel, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (stmt.getLanguage().equals("fi"))
				prefLabelString = ((Literal)stmt.getObject()).getLexicalForm();
		}
		if (prefLabelString.equals("")) {
			iter = this.koko.listStatements(subj, skosPrefLabel, (RDFNode)null);
			while (iter.hasNext()) {
				Statement stmt = iter.nextStatement();
				prefLabelString = ((Literal)stmt.getObject()).getLexicalForm();
			}
		}
		return prefLabelString;
	}

	private Resource romauta(HashSet<Resource> romautettavat) {
		this.romautetut += romautettavat.size();
		Property skosExactMatch = this.onto.createProperty(this.skosNs + "exactMatch");
		// valitaan yso-URI tai pienin URI pidettavaksi ja romautetaan loput siihen
		Resource subj = null;
		for (Resource res:romautettavat) {
			if (subj != null) {
				if (subj.getNameSpace().equals("http://www.yso.fi/onto/yso/") && !res.getNameSpace().equals("http://www.yso.fi/onto/yso/")) {

				} else if (!subj.getNameSpace().equals("http://www.yso.fi/onto/yso/") && res.getNameSpace().equals("http://www.yso.fi/onto/yso/")) {
					subj = res;
				} else if (subj.getURI().compareTo(res.getURI()) > 0) {
					subj = res;
				}
			} else {
				subj = res;
			}
		}
		romautettavat.remove(subj);
		for (Resource poistuva:romautettavat) {
			this.muutaKokoSubj(poistuva, subj);
			this.koko.add(subj, skosExactMatch, poistuva);
		}
		return subj;
	}

	private void lisaaSulkutarkenteet(HashMap<String, Resource> tarkenteetResurssitMap) {
		Property skosPrefLabel = this.onto.createProperty(skosNs + "prefLabel");
		HashSet<Statement> lisattavat = new HashSet<>();
		HashSet<Statement> poistettavat = new HashSet<>();
		for (String tarkenne:tarkenteetResurssitMap.keySet()) {
			Resource subj = tarkenteetResurssitMap.get(tarkenne);
			StmtIterator iter = this.koko.listStatements(subj, skosPrefLabel, (RDFNode)null);
			while (iter.hasNext()) {
				Statement stmt = iter.nextStatement();
				if (stmt.getLanguage().equals("fi")) {
					String prefLabelString = ((Literal)stmt.getObject()).getLexicalForm();
					poistettavat.add(stmt);
					prefLabelString += " (" + tarkenne + ")";
					lisattavat.add(this.koko.createStatement(subj, skosPrefLabel, this.koko.createLiteral(prefLabelString, "fi")));
				}
			}
		}
		for (Statement s:poistettavat) this.koko.remove(s);
		for (Statement s:lisattavat) this.koko.add(s);
	}

	public HashMap<String, HashSet<Resource>> tuotaPrefLabelKokoSubjektitMap(String lang) {
		Property skosPrefLabel = this.onto.createProperty(skosNs + "prefLabel");

		HashMap<String, HashSet<Resource>> prefLabelKokoSubjektitMap = new HashMap<String, HashSet<Resource>>();
		StmtIterator iter = this.koko.listStatements((Resource)null, skosPrefLabel, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (stmt.getLanguage().equals(lang)) {
				Resource subj = stmt.getSubject();
				String labelString = ((Literal)stmt.getObject()).getLexicalForm();
				HashSet<Resource> subjektitSet = new HashSet<Resource>();
				if (prefLabelKokoSubjektitMap.containsKey(labelString)) {
					subjektitSet = prefLabelKokoSubjektitMap.get(labelString);
				}
				subjektitSet.add(subj);
				prefLabelKokoSubjektitMap.put(labelString, subjektitSet);
			}
		}
		return prefLabelKokoSubjektitMap;
	}
	/**
	 * Vaihtoehtoinen metodi joka valitsee keskeisen käsitteen kullekin käsiteryppäälle
	 *  @author joelitak
	 *  @version 0.3
	 *  @since 2019-03-07
	 */
	public void vaihtoehtoinenMuutaUritKokoUreiksi() {
		//testataan käsitteiden määrää 1.
		System.out.println("Kokossa käsitteitä: " + this.koko.listSubjects().toList().size());

		HashMap<Resource, HashSet<Resource>> ryhmäIndeksi = new HashMap<>();
		Property skosExactMatch = this.koko.createProperty(this.skosNs + "exactMatch");

		// Muodosta käsiterymiä jotka linkittyvät toisiinsa skos:exactMatchien avulla
		HashSet<Statement> kaikkiLinkitetytKäsitteet = new HashSet<Statement>();

		StmtIterator iter1 = this.koko.listStatements(null, skosExactMatch, (RDFNode)null);
		kaikkiLinkitetytKäsitteet.addAll(iter1.toSet());

		System.out.println("Linkitettyjä käsitteitä oli " + kaikkiLinkitetytKäsitteet.size());
		this.siivoaPoisTuplaVastaavuudet(kaikkiLinkitetytKäsitteet);
		System.out.println("Siivouksen jälkeen linkitettyjä on " + kaikkiLinkitetytKäsitteet.size());

		for (Statement linkki : kaikkiLinkitetytKäsitteet) {

			Resource A = linkki.getSubject();
			Resource B = linkki.getResource();

			if ( ryhmäIndeksi.containsKey(A) &&
					ryhmäIndeksi.containsKey(B) &&
					ryhmäIndeksi.get(A) != ryhmäIndeksi.get(B) ) {

				//yhdistä ryhmät
				ryhmäIndeksi.get(A).addAll(ryhmäIndeksi.get(B));
				ryhmäIndeksi.remove(B);
				ryhmäIndeksi.put(B, ryhmäIndeksi.get(A));

			} else if ( ryhmäIndeksi.containsKey(A) ) {
				//lisää olemassaolevaan
				ryhmäIndeksi.get(A).add(B);
			} else if ( ryhmäIndeksi.containsKey(B) ) {
				//lisää olemassaolevaan
				ryhmäIndeksi.get(B).add(A);
			} else {
				//luo uusi ryhmä
				HashSet<Resource> uusi = new HashSet<Resource>();
				uusi.add(A);
				uusi.add(B);
				ryhmäIndeksi.put(A, uusi);
				ryhmäIndeksi.put(B, uusi);
			}
		}

		//Lisää vielä kaikki skos:Conceptit että erilleen jäävät käsitteet tulevat kokoon mukaan
		StmtIterator iter3 = this.koko.listStatements(null, RDF.type, this.koko.createResource(this.skosNs+"Concept"));

		while (iter3.hasNext()) {
			Resource subj = iter3.next().getSubject();

			if (!ryhmäIndeksi.containsKey(subj)) {
				HashSet<Resource> newSet = new HashSet<Resource>();
				newSet.add(subj);
				ryhmäIndeksi.put(subj, newSet);
			}		

		}

		//Jokaiselle käsiteryhmälle, listaa kaikki käsitteet ja valitse niistä pienin kokourivastaavuus vanhasta kokosta
		for (HashSet<Resource> ryhmä : ryhmäIndeksi.values()) {

			Vector<Resource> ryhmänKokot = new Vector<Resource>();
			for (Resource r : ryhmä) {
				Resource uusiKoko = this.ontoKokoResurssivastaavuudetMap.get(r);
				if (uusiKoko != null) {
					ryhmänKokot.add(uusiKoko);
				} 
			}
			//Jos kokourivastaavuutta ei löydy, luo uusi kokouri
			if (ryhmänKokot.size() == 0 || ryhmänKokot.get(0) == null) {
				ryhmänKokot.add(luoUusiKokoResurssi());
			} else {

				Collections.sort(ryhmänKokot, new ResourceComparator());
			}
			Resource kokoSubj = ryhmänKokot.get(0);
			for (Resource ontoSubj:ryhmä) {

				//Miksi tämä tehdään kummallekin?
				this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.put(ontoSubj, kokoSubj);
				this.ontoKokoResurssivastaavuudetMap.put(ontoSubj, kokoSubj);
			}
			for (Resource r : ryhmä) {
				this.muutaKokoSubj(r, kokoSubj);
			}
			//Poista muut viittaukset kokoureihin ?
			for (int i=1 ; i<ryhmänKokot.size(); i++) {
				if (!kokoSubj.equals(ryhmänKokot.get(i)))
					this.koko.add(ryhmänKokot.get(i), DCTerms.isReplacedBy, kokoSubj);
			}
		}

		//Siivotaan lopuksi hierarkiasta erikoisonto- ja ysourit joihin on saatettu viitata exactMatcheilla
		Property broader = this.koko.getProperty(this.skosNs+"broader");
		Property narrower = this.koko.getProperty(this.skosNs+"narrower");
		HashSet<Statement> korvattavatHierarkiset = new HashSet<Statement>();
		StmtIterator broaderIter = this.koko.listStatements(null, broader, (RDFNode)null);
		while (broaderIter.hasNext()) {
			Statement s = broaderIter.next();
			if (!s.getResource().getNameSpace().endsWith("/koko/")) {
				korvattavatHierarkiset.add(s);
			}
		}
		StmtIterator narrowerIter = this.koko.listStatements(null, narrower, (RDFNode)null);
		while (narrowerIter.hasNext()) {
			Statement s = narrowerIter.next();
			if (!s.getResource().getNameSpace().endsWith("/koko/")) {
				korvattavatHierarkiset.add(s);
			}
		}
		
		for (Statement s : korvattavatHierarkiset) {
			Resource korvaava = this.ontoKokoResurssivastaavuudetMap.get(s.getResource());
			this.koko.remove(s);
			if (korvaava == null) {
				System.out.println("Käsitteelle " + s.getResource() + " ei löytynyt vastaavaa koko-uria. Poistetaan viittaus uriin.");
			} else {
				this.koko.add(s.getSubject(), s.getPredicate(), korvaava);
			}
		}
		
		//testataan käsitteiden määrää 2.
		System.out.println("Kokossa käsitteitä urittamisen jälkeen: " + this.koko.listSubjects().toList().size());
		
	}

	private void valiTarkistus(String filename) {

		//Tehdään välitallennus kokosta tässä kohtaa:
		try {
			System.out.println("Kirjoitetaan välikoko...");
			this.koko.write((new FileWriter(filename)), "TTL");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/* 
	 * Tämä metodi hakee koko-uri-vastaavuudet erikoisontologian käsiteille, tai jos sellaisia ei löydy, luo uudet koko-urit
	 * 
	 */

	public void muutaUritKokoUreiksi() {
		System.out.println("Kokossa käsitteitä ennen kurittamista: " + this.koko.listSubjects().toList().size());
		HashSet<Resource> kokonSubjektitSet = new HashSet<Resource>();
		HashSet<Resource> kokossaOlevatKokoUritTaiOikeamminResurssit = new HashSet<Resource>();
		Vector<HashSet<Resource>> kokonKasitteetSittenReplacedBytVektori = new Vector<HashSet<Resource>>();
		Resource skosConcept = this.koko.createResource(this.skosNs + "Concept");
		ResIterator resIter = this.koko.listResourcesWithProperty(RDF.type, skosConcept);
		while (resIter.hasNext()) {
			Resource subj = resIter.nextResource();
			kokonSubjektitSet.add(subj);
		}
		kokonKasitteetSittenReplacedBytVektori.add(kokonSubjektitSet);
		kokonSubjektitSet = new HashSet<Resource>();
		StmtIterator iter = this.koko.listStatements((Resource)null, DCTerms.isReplacedBy, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			kokonSubjektitSet.add(stmt.getSubject());
		}
		kokonKasitteetSittenReplacedBytVektori.add(kokonSubjektitSet);
		Property skosExactMatch = this.onto.createProperty(this.skosNs + "exactMatch");
		//int laskuri = 0;

		for (HashSet<Resource> kokonSubjektit:kokonKasitteetSittenReplacedBytVektori) {
			//Tehdaan tallekin toimenpiteelle jarjestys - ensin YSOt URI-jarjestyksessa, sitten muut URI-jarjestyksessa
			Vector<String> kokoSubjektitVektori = new Vector<String>();
			Vector<String> eiYsoVektori = new Vector<String>();
			for (Resource res:kokonSubjektit) {
				if (res.getNameSpace().equals("http://www.yso.fi/onto/yso/")) kokoSubjektitVektori.add(res.getURI());
				else eiYsoVektori.add(res.getURI());
			}
			Collections.sort(kokoSubjektitVektori);
			Collections.sort(eiYsoVektori);
			kokoSubjektitVektori.addAll(eiYsoVektori);


			//kokonSubjektiVektorissa on nyt kaikki yhteen kokokäsitteeseen viittaavat urit ysosta ja erikoisontologioista
			//Tällä hetkellä kokoaja suosii liikaa yson kautta tulevia kokokäsitteitä. Kannattaa muuttaa muotoon, jossa otetaan kaikkien subjektivektorin kautta linkittyvien kokourien pienin

			for (String uri:kokoSubjektitVektori) {
				Resource subj = this.koko.createResource(uri);
				Vector<Resource> ontoSubjSet = new Vector<Resource>();
				ontoSubjSet.add(subj);
				Vector<String> eiYsoSubjSet = new Vector<String>(); //sisältää myös yso-subjektin silloin kun käydään tätä läpi erikoisontologian käsitteille
				iter = this.koko.listStatements(subj, skosExactMatch, (RDFNode)null);
				while (iter.hasNext()) {
					Statement stmt = iter.nextStatement();
					String uriString = ((Resource)(stmt.getObject())).getURI();
					if (uriString != null) eiYsoSubjSet.add(uriString);
				}
				if (!eiYsoSubjSet.isEmpty()) {
					//System.out.println(eiYsoSubjSet);
					Collections.sort(eiYsoSubjSet);
					for (String eiYsoUri:eiYsoSubjSet) ontoSubjSet.add(this.koko.createResource(eiYsoUri));
				}

				Resource kokoSubj = null;
				for (Resource ontoSubj:ontoSubjSet) {
					/*
				if (this.ontoKokoResurssivastaavuudetMap.containsKey(ontoSubj)) {
					Resource kandidaattiSubj = this.ontoKokoResurssivastaavuudetMap.get(ontoSubj);
					if (kokoSubj == null) kokoSubj = kandidaattiSubj;
					else if (kokoSubj.getURI().compareTo(kandidaattiSubj.getURI()) > 1) {
						this.koko.add(kokoSubj, DCTerms.isReplacedBy, kandidaattiSubj);
						kokoSubj = kandidaattiSubj;
					}
					if (kokossaOlevatKokoUritTaiOikeamminResurssit.contains(kokoSubj)) {
						//laskuri++;
						//System.out.println(laskuri + ". DEBUG: KOKOssa on jo " + kokoSubj.getURI() + " <-- " + ontoSubj.getURI());
						kokoSubj = this.luoUusiKokoResurssi();
					}
				}*/

					//Tästä eteenpäin loopin sisällä saattaa esiintyä virheitä:



					if (kokoSubj == null && this.ontoKokoResurssivastaavuudetMap.containsKey(ontoSubj)) { 


						if (!kokossaOlevatKokoUritTaiOikeamminResurssit.contains(this.ontoKokoResurssivastaavuudetMap.get(ontoSubj))) { 
							//jos pääkäsitettä ei ole valittu,
							//vuorossa oleva käsite löytyy kokosta
							//ja jos sitä ei ole tämänkertaisessa ajossa ajettu kokoon
							//luodaan uusi pääkäsite
							kokoSubj = this.luoUusiKokoResurssi(); 
						} else {
							//jos pääkäsitettä ei ole valittu,
							//vuorossa oleva käsite löytyy kokosta,
							//ja vuorossa olevaa käsitettä on tämänkertaisessa ajossa ajettu kokoon,
							//tehdään vuorossa olevasta käsitteestä pääkäsite
							kokoSubj = this.ontoKokoResurssivastaavuudetMap.get(ontoSubj); 
						}
					} else if (kokoSubj != null && this.ontoKokoResurssivastaavuudetMap.containsKey(ontoSubj) && !kokoSubj.equals(this.ontoKokoResurssivastaavuudetMap.get(ontoSubj))) { 

						//jos pääkäsite on valittu
						this.koko.add(this.ontoKokoResurssivastaavuudetMap.get(ontoSubj), DCTerms.isReplacedBy, kokoSubj); 
					}

				}


				if (kokoSubj == null) {
					kokoSubj = this.luoUusiKokoResurssi();
				}
				kokossaOlevatKokoUritTaiOikeamminResurssit.add(kokoSubj);
				for (Resource ontoSubj:ontoSubjSet) {
					this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.put(ontoSubj, kokoSubj);
					this.ontoKokoResurssivastaavuudetMap.put(ontoSubj, kokoSubj);
				}
				this.muutaKokoSubj(subj, kokoSubj);
			}
		}
		System.out.println("Kokossa käsitteitä kurittamisen jälkeen: " + this.koko.listSubjects().toList().size());
	}

	private void muutaKokoSubj(Resource vanhaSubj, Resource uusiSubj) {
		HashSet<Statement> poistettavat = new HashSet<Statement>();
		HashSet<Statement> lisattavat = new HashSet<Statement>();
		StmtIterator iter = this.koko.listStatements(vanhaSubj, (Property)null, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			poistettavat.add(stmt);
			lisattavat.add(this.koko.createStatement(uusiSubj, stmt.getPredicate(), stmt.getObject()));
		}
		for (Statement s:poistettavat) this.koko.remove(s);
		for (Statement s:lisattavat) this.koko.add(s);
	}

	public void parsiErikoisontologioidenPolutVektoriin(String ontologioidenPolutJaTyypitTxt) {
		this.ontologioidenTyypitPolutMap = new HashMap<Resource, String>();
		Vector<String> polkuVektori = new Vector<String>();
		try {
			BufferedReader br = new BufferedReader( new InputStreamReader( new FileInputStream(ontologioidenPolutJaTyypitTxt), "UTF8" ) );

			String rivi = br.readLine().trim();
			while (rivi != null) {
				rivi = rivi.trim();
				if (!rivi.startsWith("#")) { 
					String[] uriTaulukko = rivi.split(" ");
					polkuVektori.add(uriTaulukko[1].trim());
					this.ontologioidenTyypitPolutMap.put(this.koko.createResource(uriTaulukko[0].trim()), uriTaulukko[1].trim());
				}
				rivi = br.readLine();
			}
			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void korjaaLopuksiObjectit() {
		System.out.println("Korjataan KOKOn objektit.");		
		HashSet<Statement> poistettavat = new HashSet<Statement>();
		HashSet<Statement> lisattavat = new HashSet<Statement>();
		StmtIterator iter = this.koko.listStatements();
		Property skosExactMatch = this.koko.createProperty(this.skosNs + "exactMatch");
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (!(stmt.getPredicate().equals(skosExactMatch))) {
				RDFNode obj = stmt.getObject();
				if (obj.isURIResource()) {
					if (this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.containsKey(obj)) {
						Resource uusiObj = this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.get(obj);
						poistettavat.add(stmt);
						lisattavat.add(this.koko.createStatement(stmt.getSubject(), stmt.getPredicate(), uusiObj));
					}
				}
			}
		}
		for (Statement stmt:poistettavat) this.koko.remove(stmt);
		for (Statement stmt:lisattavat) this.koko.add(stmt);
	}

	public void tulostaPrefLabelMuutoksetEdelliseenVerrattuna(Model aiempiKoko, String lang) {
		System.out.println("KOKOn prefLabel muutokset edelliseen versioon verrattuna kielella " + lang + ":");
		Property skosPrefLabel = aiempiKoko.createProperty(this.skosNs + "prefLabel");
		int i = 0;

		HashMap<Resource, String> nykyKokonPrefLabelitMap = new HashMap<Resource, String>();
		StmtIterator iter = this.koko.listStatements((Resource)null, skosPrefLabel, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (stmt.getLanguage().equals(lang)) {
				String fiLabelString = ((Literal)stmt.getObject()).getLexicalForm();
				nykyKokonPrefLabelitMap.put(stmt.getSubject(), fiLabelString);
			}
		}

		iter = aiempiKoko.listStatements((Resource)null, skosPrefLabel, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (stmt.getLanguage().equals(lang)) {
				String fiLabelString = ((Literal)stmt.getObject()).getLexicalForm();
				if (nykyKokonPrefLabelitMap.containsKey(stmt.getSubject())) {
					String nykyFiLabelString = nykyKokonPrefLabelitMap.get(stmt.getSubject());
					String nykyFiLabelStringIlmanSulkutarkenteita = nykyFiLabelString;
					if (nykyFiLabelString.contains("(")) nykyFiLabelStringIlmanSulkutarkenteita = nykyFiLabelString.substring(0, nykyFiLabelString.indexOf("(")).trim();
					String fiLabelStringIlmanSulkutarkenteita = fiLabelString;
					if (fiLabelString.contains("(")) fiLabelStringIlmanSulkutarkenteita = fiLabelString.substring(0, fiLabelString.indexOf("(")).trim();
					if (!(nykyFiLabelStringIlmanSulkutarkenteita.equals(fiLabelStringIlmanSulkutarkenteita))) {
						i++;
						System.out.println(i + ". " + stmt.getSubject().getURI() + " -- " + fiLabelString + " => " + nykyFiLabelString);
					}
				}
			}
		}
	}

	public void tulostaMuutoksetEdelliseenVerrattuna(String aiemmanKokonpolku) {
		System.out.println("Tulostetaan prefLabel-muutokset edelliseen KOKOon verrattuna.");		
		int i = 0;
		Model aiempiKoko = JenaHelpers.lueMalliModeliksi(aiemmanKokonpolku);
		Property skosPrefLabel = aiempiKoko.createProperty(this.skosNs + "prefLabel");
		aiempiKoko = JenaHelpers.muunnaKielikoodittomatLabelitSuomenkielisiksi(aiempiKoko, skosPrefLabel);

		HashMap<Resource, String> nykyKokonPrefLabelitMap = new HashMap<Resource, String>();
		StmtIterator iter = this.koko.listStatements((Resource)null, skosPrefLabel, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (stmt.getLanguage().equals("fi")) {
				String fiLabelString = ((Literal)stmt.getObject()).getLexicalForm();
				nykyKokonPrefLabelitMap.put(stmt.getSubject(), fiLabelString);
			}
		}

		this.tulostaPrefLabelMuutoksetEdelliseenVerrattuna(aiempiKoko, "fi");
		this.tulostaPrefLabelMuutoksetEdelliseenVerrattuna(aiempiKoko, "sv");

		Resource skosConcept = this.koko.createResource(this.skosNs + "Concept");
		HashSet<Resource> uudenKokonResurssit = new HashSet<Resource>();
		ResIterator resIter = this.koko.listSubjectsWithProperty(RDF.type, skosConcept);
		while (resIter.hasNext()) {
			uudenKokonResurssit.add(resIter.nextResource());
		}
		resIter = aiempiKoko.listSubjectsWithProperty(RDF.type, skosConcept);
		while (resIter.hasNext()) {
			uudenKokonResurssit.remove(resIter.nextResource());
		}
		System.out.println("Uudessa KOKOssa on " + uudenKokonResurssit.size() + " uutta kasitetta.");
		i = 0; 
		for (Resource uusi:uudenKokonResurssit) {
			i++;
			System.out.println(i + "." + uusi.getURI() + " = " + nykyKokonPrefLabelitMap.get(uusi));
		}
	}

	public void lisaaExactMatchitAiemmassaKokossaOlleisiin(String aiemmanKokonpolku) {
		System.out.println("Lisataan linkit aiemmassa KOKOssa olleisiin kasitteisiin.");		
		int i = 0;
		Model aiempiKoko = JenaHelpers.lueMalliModeliksi(aiemmanKokonpolku);
		Property skosPrefLabel = aiempiKoko.createProperty(this.skosNs + "prefLabel");
		aiempiKoko = JenaHelpers.muunnaKielikoodittomatLabelitSuomenkielisiksi(aiempiKoko, skosPrefLabel);
		HashSet<Resource> nykyKokonResurssit = new HashSet<Resource>();
		Resource skosConcept = this.koko.createResource(this.skosNs + "Concept");
		ResIterator resIter = this.koko.listResourcesWithProperty(RDF.type, skosConcept);
		while (resIter.hasNext()) {
			Resource subj = resIter.nextResource();
			nykyKokonResurssit.add(subj);
		}

		HashSet<Resource> aiemmassaKokossaOlleetMuttaNykyKokostaPuuttuvatSkosConceptit = new HashSet<Resource>();
		resIter = aiempiKoko.listResourcesWithProperty(RDF.type, skosConcept);
		while (resIter.hasNext()) {
			Resource subj = resIter.nextResource();
			if (!nykyKokonResurssit.contains(subj)) {
				aiemmassaKokossaOlleetMuttaNykyKokostaPuuttuvatSkosConceptit.add(subj);
			}
		}

		//System.out.println(aiemmassaKokossaOlleetMuttaNykyKokostaPuuttuvatResurssit.size());
		HashMap<Resource, String> aiemmanKokonNykyKokostaPuuttuvienFiLabelitMap = this.haeTietynTyyppistenResurssienLabelitMappiin(aiempiKoko, skosPrefLabel, aiemmassaKokossaOlleetMuttaNykyKokostaPuuttuvatSkosConceptit, "fi");
		//System.out.println(aiemmanKokonNykyKokostaPuuttuvienFiLabelitMap.size());

		Property skosExactMatch = this.koko.createProperty(skosNs + "exactMatch");
		for (Resource subj:aiemmassaKokossaOlleetMuttaNykyKokostaPuuttuvatSkosConceptit) {
			boolean loytyiVastine = false;
			HashSet<Resource> subjinAiemmassaKokossaOlevatExactMatchit = new HashSet<Resource>();
			StmtIterator iter = aiempiKoko.listStatements(subj, skosExactMatch, (RDFNode)null);
			while (iter.hasNext()) {
				Statement stmt = iter.nextStatement();
				Resource obj = (Resource)(stmt.getObject());
				subjinAiemmassaKokossaOlevatExactMatchit.add(obj);
			}
			iter = aiempiKoko.listStatements(subj, DCTerms.isReplacedBy, (RDFNode)null);
			while (iter.hasNext()) {
				Statement stmt = iter.nextStatement();
				Resource obj = (Resource)(stmt.getObject());
				subjinAiemmassaKokossaOlevatExactMatchit.add(obj);
			}
			for (Resource obj:subjinAiemmassaKokossaOlevatExactMatchit) {
				if (nykyKokonResurssit.contains(obj)) {
					loytyiVastine = true;
					this.koko.add(subj, DCTerms.isReplacedBy, obj);
				}
			}
			if (!loytyiVastine) {
				for (Resource obj:subjinAiemmassaKokossaOlevatExactMatchit) {
					if (this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.containsKey(obj)) {
						loytyiVastine = true;
						this.koko.add(subj, DCTerms.isReplacedBy, this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.get(obj));
					}
				}
			}
			if (!loytyiVastine) {
				if (aiemmanKokonNykyKokostaPuuttuvienFiLabelitMap.containsKey(subj)) {
					String labelString = aiemmanKokonNykyKokostaPuuttuvienFiLabelitMap.get(subj);
					if (this.kokoFiLabelitResurssitMap.containsKey(labelString)) {
						loytyiVastine = true;
						this.koko.add(subj, DCTerms.isReplacedBy, this.ontoKokoResurssivastaavuudetJotkaNykyKokossaMap.get(this.kokoFiLabelitResurssitMap.get(labelString)));
					}
				}
			}
			if (!loytyiVastine) {
				i++;
				if (aiemmanKokonNykyKokostaPuuttuvienFiLabelitMap.containsKey(subj)) {
					System.out.println(i + ". ongelma: Edellisessa KOKOssa oli kasite, jolle ei loytynyt vastinetta uuteen KOKOon: " + aiemmanKokonNykyKokostaPuuttuvienFiLabelitMap.get(subj) + " -- " + subj.getURI());
				} else {
					System.out.println(i + ". ongelma: Edellisessa KOKOssa oli kasite, jolle ei loytynyt vastinetta uuteen KOKOon: " + subj.getURI());
				}
			}
		}
		// Poistetaan itseensa osoittavat replacedByt, seka ne, jotka tulisivat nyky-KOKOssa ihan varsinaisina kasitteina olevista
		HashSet<Statement> poistettavat = new HashSet<Statement>();
		StmtIterator iter = this.koko.listStatements((Resource)null, DCTerms.isReplacedBy, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (stmt.getSubject().equals(stmt.getObject())) poistettavat.add(stmt);
			else {
				StmtIterator iter2 = this.koko.listStatements(stmt.getSubject(), RDF.type, (RDFNode)null);
				if (iter2.hasNext()) poistettavat.add(stmt);
			}
		}
		for (Statement s:poistettavat) this.koko.remove(s);

		HashSet<Statement> lisattavat = new HashSet<Statement>();
		HashSet<Resource> nykyKokonAivanKaikkiResurssitSet = new HashSet<Resource>();
		iter = this.koko.listStatements();
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			nykyKokonAivanKaikkiResurssitSet.add(stmt.getSubject());
		}
		//HashSet<Resource> aiemmanKokonReplacedBytJotkaPuuttuvatUudesta = new HashSet<Resource>();
		iter = aiempiKoko.listStatements((Resource)null, DCTerms.isReplacedBy, (RDFNode)null);
		i = 0;
		int j = 0;
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			if (!nykyKokonAivanKaikkiResurssitSet.contains(stmt.getSubject())) {
				Resource obj = (Resource)stmt.getObject();
				if (nykyKokonAivanKaikkiResurssitSet.contains(obj)) {
					lisattavat.add(stmt);
					j++;
				} else {
					i++;
					System.out.println(i + ". vanhassa KOKOssa oli replaced-by-triple, jota ei voitu toisintaa uuteen KOKOon: " + stmt.toString());
				}
			}
		}
		for (Statement s:lisattavat) this.koko.add(s);
		System.out.println("Lisattiin " + j + " replacedByta aiemmasta KOKOsta.");
		//JenaHelpers.testaaMallinLabelienKielet(aiempiKoko, skosPrefLabel);
	}

	public void tarkistaEtteiKorvattuihinMeneSuhteitaJaPuraMahdollisetKorvaavuusKetjut() {
		System.out.println("Tarkistetaan ettei korvattuihin mene suhteita ja puretaan mahdolliset korvaavuusketjut.");
		HashSet<Statement> lisattavat = new HashSet<Statement>();
		HashSet<Statement> poistettavat = new HashSet<Statement>();

		HashMap<Resource, Resource> korvaavuusMap = new HashMap<Resource, Resource>();
		StmtIterator iter = this.koko.listStatements((Resource)null, DCTerms.isReplacedBy, (RDFNode)null);
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			HashSet<Resource> korvattavat = new HashSet<Resource>();
			Resource korvattava = stmt.getSubject();
			Resource korvaava = (Resource)(stmt.getObject());

			poistettavat.add(stmt);

			/*Ei otetakaan huomioon korvaavuussuhteita kahden koko-käsitteen välillä
			 * jos korvattava koko-käsite on korvattu useammalla koko-käsitteellä 
			 */
			if (this.koko.listStatements(korvattava, DCTerms.isReplacedBy, (RDFNode)null).toSet().size() > 1) {
				continue;
			}

			korvattavat.add(korvattava);
			korvaavuusMap.put(korvattava, korvaava);
			boolean jatka = true;
			while (jatka) {
				StmtIterator iter2 = this.koko.listStatements(korvaava, DCTerms.isReplacedBy, (RDFNode)null);
				if (iter2.hasNext()) {
					Statement stmt2 = iter2.nextStatement();
					//lisätty tarkistus ikuisten looppien ehkäisemiseksi
					if (poistettavat.contains(stmt2))
						jatka = false;
					poistettavat.add(stmt2);
					korvattavat.add(korvaava);
					korvaava = (Resource)(stmt2.getObject());
				} else jatka = false;
			}
			for (Resource r:korvattavat) {
				korvaavuusMap.put(r, korvaava);
				lisattavat.add(this.koko.createStatement(r, DCTerms.isReplacedBy, korvaava));
			}

		}

		for (Statement s:poistettavat) this.koko.remove(s);
		for (Statement s:lisattavat) this.koko.add(s);

		System.out.println("poistettiin:");
		for (Statement s:poistettavat) this.koko.remove(s);
		System.out.println("lisattiin:");
		for (Statement s:lisattavat) this.koko.add(s);


		lisattavat = new HashSet<Statement>();
		poistettavat = new HashSet<Statement>();
		iter = this.koko.listStatements();
		while (iter.hasNext()) {
			Statement stmt = iter.nextStatement();
			Resource obj = null;
			if (stmt.getObject().isURIResource()) obj = (Resource)stmt.getObject(); 
			if (korvaavuusMap.containsKey(obj)) {
				poistettavat.add(stmt);
				lisattavat.add(this.koko.createStatement(stmt.getSubject(), stmt.getPredicate(), korvaavuusMap.get(obj)));
			}
		}
		for (Statement s:poistettavat) this.koko.remove(s);
		for (Statement s:lisattavat) this.koko.add(s);

		System.out.println("triplet korvattuihin muutettu");
		System.out.println("poistettiin:");
		for (Statement s:poistettavat) this.koko.remove(s);
		System.out.println("lisattiin:");
		for (Statement s:lisattavat) this.koko.add(s);

		System.out.println("Tarkistettu.");
	}

	public void lueMustaLista(String mustanListanPolku) {
		this.mustaLista = new HashSet<Resource>();
		try {
			BufferedReader br = new BufferedReader( new InputStreamReader( new FileInputStream(mustanListanPolku), "UTF8" ) );

			String rivi = br.readLine().trim();
			while (rivi != null) {
				rivi = rivi.trim();
				this.mustaLista.add(this.koko.createResource(rivi));
				rivi = br.readLine();
			}
			br.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void kirjoitaUudetUriVastaavuudet(String tiedostonPolku) {
		try {
			FileWriter fstream = new FileWriter(tiedostonPolku);
			BufferedWriter out = new BufferedWriter(fstream);
			for (Resource vanhaUriRes:this.ontoKokoResurssivastaavuudetMap.keySet()) {
				String vanhaUriString = vanhaUriRes.getURI();
				String kokoUriString = this.ontoKokoResurssivastaavuudetMap.get(vanhaUriRes).getURI();
				out.write(vanhaUriString + " " + kokoUriString + "\n");
			}
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Uudet KOKO-URI-vastaavuudet kirjoitettu tiedostoon " + tiedostonPolku);
	}

	public void kokoa(String ysonPolku, String erikoisontologiaTxtnPolku, String edellisenKokonPolku, String uusienUrivastaavuuksienPolku, String mustanListanPolku) {
		this.lueMustaLista(mustanListanPolku);
		this.lueYso(ysonPolku);
		//this.valiTarkistus("koko-vain-YSO.ttl");
		this.parsiErikoisontologioidenPolutVektoriin(erikoisontologiaTxtnPolku);
		for (Resource ontonTyyppi:this.ontologioidenTyypitPolutMap.keySet()) {
			String polku = this.ontologioidenTyypitPolutMap.get(ontonTyyppi);
			this.lueOnto(polku, ontonTyyppi);
		}
		//this.valiTarkistus("koko-0-ontot.ttl");
		this.muutaCandidateLabelitPrefJaAltLabeleiksi();
		//this.valiTarkistus("koko-1-candidate.ttl");
		this.romautaFiPrefLabelienJaVanhempienPerusteella();
		//this.valiTarkistus("koko-2-romautettu.ttl");
		//this.muutaUritKokoUreiksi();
		this.vaihtoehtoinenMuutaUritKokoUreiksi();
		//this.valiTarkistus("koko-3-kuritettu.ttl");
		this.korjaaLopuksiObjectit();
		this.valiTarkistus("koko-4-obejktitkorjattu.ttl");
		this.lisaaExactMatchitAiemmassaKokossaOlleisiin(edellisenKokonPolku);
		this.valiTarkistus("koko-5-exactMatch.ttl");
		this.tarkistaEtteiKorvattuihinMeneSuhteitaJaPuraMahdollisetKorvaavuusKetjut();
		this.valiTarkistus("koko-6-purettuKorvaavuus.ttl");
		this.tulostaMuutoksetEdelliseenVerrattuna(edellisenKokonPolku);
		this.valiTarkistus("koko-7-eiEroa-muutokset-verrattu.ttl");
		this.kirjoitaUudetUriVastaavuudet(uusienUrivastaavuuksienPolku);
		this.valiTarkistus("koko-8-uudetUriVastaavuudet.ttl");
		System.out.println("Labelin perusteella romautettiin " + this.romautetut + ".");
	}

	public void kokoaJaTuotaDebugKoko(String ysonPolku, String erikoisontologiaTxtnPolku, String edellisenKokonPolku, String uusienUrivastaavuuksienPolku, String mustanListanPolku, String debugKokonPolku) {
		this.lueMustaLista(mustanListanPolku);
		this.lueYso(ysonPolku);
		this.parsiErikoisontologioidenPolutVektoriin(erikoisontologiaTxtnPolku);
		for (Resource ontonTyyppi:this.ontologioidenTyypitPolutMap.keySet()) {
			String polku = this.ontologioidenTyypitPolutMap.get(ontonTyyppi);
			this.lueOnto(polku, ontonTyyppi);
		}
		this.kirjoitaKoko(debugKokonPolku);
		this.muutaCandidateLabelitPrefJaAltLabeleiksi();
		this.romautaFiPrefLabelienJaVanhempienPerusteella();
		//this.kirjoitaKoko(debugKokonPolku);
		this.muutaUritKokoUreiksi();
		this.korjaaLopuksiObjectit();
		this.lisaaExactMatchitAiemmassaKokossaOlleisiin(edellisenKokonPolku);
		this.tarkistaEtteiKorvattuihinMeneSuhteitaJaPuraMahdollisetKorvaavuusKetjut();
		this.tulostaMuutoksetEdelliseenVerrattuna(edellisenKokonPolku);
		this.kirjoitaUudetUriVastaavuudet(uusienUrivastaavuuksienPolku);
		System.out.println("Labelin perusteella romautettiin " + this.romautetut + ".");
	}

	public void kirjoitaKoko(String kokonPolku) {
		
		try {
			System.out.println("Kirjoitetaan välikoko...");
			this.koko.write((new FileWriter(kokonPolku+".alt")), "TTL");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
		JenaHelpers.kirjoitaMalli(this.koko, kokonPolku, true);
	}
	private Resource haeKorvaava(Resource res, Model model) {
		List<Resource> results = new ArrayList<>();
		StmtIterator iter = model.listStatements(res, DCTerms.isReplacedBy, (RDFNode)null);
		while (iter.hasNext()) {
			results.add(iter.next().getResource());
		}
		if (results.size() > 1) {
			Collections.sort(results, new ResourceHierarchyComparator());
		} return results.get(0);
	}

	/*
	 * args[0] = vanhatUrit-KokoUrit-vastaavuudettiedosto
	 * args[1] = yson polku
	 * args[2] = kokon erikoisontologioiden polut ja tyypit
	 * args[3] = uusi vanhatUrit-KokoUrit-vastaavuudettiedosto
	 * args[4] = vanha KOKO
	 * args[5] = uusi KOKO
	 * args[6] = musta lista (kasitteet, joita ei haluta KOKOon)
	 * 
	 * optional:
	 * args[7] = debug-koko ennen kuin urit muutetaan koko-ureiksi
	 */
	public static void main(String[] args) {
		Kokoaja2 kokoaja = new Kokoaja2(args[0]);
		if (args.length == 7)
			kokoaja.kokoa(args[1], args[2], args[4], args[3], args[6]);
		else if (args.length == 8)
			kokoaja.kokoaJaTuotaDebugKoko(args[1], args[2], args[4], args[3], args[6], args[7]);
		else
			System.out.println("Vaara maara argumentteja");

		kokoaja.kirjoitaKoko(args[5]);
	}


	private void siivoaPoisTuplaVastaavuudet(HashSet<Statement> kaikkiLinkitetytKäsitteet) {

		HashMap<Resource, HashSet<Statement>> kaikkiLinkitMap = new HashMap<Resource, HashSet<Statement>>();
		for (Statement s : kaikkiLinkitetytKäsitteet) {

			Resource r1 = s.getSubject();
			Resource r2 = s.getResource();

			//Muodostetaan pussukat
			if (!kaikkiLinkitMap.containsKey(r1) && !kaikkiLinkitMap.containsKey(r2)) {
				HashSet<Statement> set = new HashSet<Statement>();
				set.add(s);
				set.add(s);
				kaikkiLinkitMap.put(r1, set);
				kaikkiLinkitMap.put(r2, set);

			}
			else if (kaikkiLinkitMap.containsKey(r1) && kaikkiLinkitMap.containsKey(r2)) {
				HashSet<Statement> s1 = kaikkiLinkitMap.get(r1);
				HashSet<Statement> s2 = kaikkiLinkitMap.get(r2);
				s1.addAll(s2);
				s1.add(s);
				s1.add(s);
				kaikkiLinkitMap.replace(r2, s1);
			}
			else if (kaikkiLinkitMap.containsKey(r1)) {
				HashSet<Statement> set = kaikkiLinkitMap.get(r1);
				set.add(s);
				kaikkiLinkitMap.put(r2, set);
			}
			else if (kaikkiLinkitMap.containsKey(r2)) {

				HashSet<Statement> set = kaikkiLinkitMap.get(r2);
				set.add(s);
				kaikkiLinkitMap.put(r1, set);
			}
		}
		HashSet<HashSet<Statement>> valueSet = new HashSet<>();
		valueSet.addAll(kaikkiLinkitMap.values());

		//Käy läpi kaikki pussukat (niitä on noin 55000)
		for (HashSet<Statement> pussukka : valueSet) {

			HashMap<String, HashSet<Statement>> tämänPussukanRomahtaneet = new HashMap<>();

			for (Statement s : pussukka) {
				String ns1 = s.getSubject().getNameSpace();
				String ns2 = s.getResource().getNameSpace();

				if (s.getSubject().getURI().equals(s.getResource().getURI())) continue; //ei huomioida exactMatcheja itsensä kanssa

				HashSet<Statement> set1 =  tämänPussukanRomahtaneet.containsKey(ns1) ? tämänPussukanRomahtaneet.get(ns1) : new HashSet<Statement>(); 
				set1.add(s);
				tämänPussukanRomahtaneet.put(ns1, set1);
				HashSet<Statement> set2 =  tämänPussukanRomahtaneet.containsKey(ns2) ? tämänPussukanRomahtaneet.get(ns2) : new HashSet<Statement>(); 
				set2.add(s);
				tämänPussukanRomahtaneet.put(ns2, set2);

			}


			for (HashSet<Statement> set : tämänPussukanRomahtaneet.values()) {
				if (set.size() > 1) {

					//jos pussukka romauttaa yhteen useamman käsitteen samasta nimiavaruudesta, valitaan vain yksi sopiva
					kaikkiLinkitetytKäsitteet.removeAll(poistaNäistäTuplat(set));

				}
			}
		}
	}

	private HashSet<Statement> poistaNäistäTuplat(HashSet<Statement> set) {

		HashSet<Statement> result = new HashSet<>();

		////Säännöt tuplien poistamiseksi:

		for (Statement s : set) {
			boolean poisto = false;

			//Hylätään resurssit jolle ei löydy labelia
			if (!poisto && !koko.contains(s.getResource(), koko.getProperty(skosNs+"prefLabel")))
				poisto = true;

			//Hylätään deprekoitu resurssi
			if (!poisto && koko.containsLiteral(s.getResource(), koko.getProperty("http://www.w3.org/2002/07/owl#deprecated"), true))
				poisto = true;

			if (poisto)
				result.add(s);
		}

		// Tähän voi lisäksi lisätä hyvänä lisänä lisää lisäsääntöjä, kuten vertailua prefLabeleiden kesken yms.
		// ...


		if (result.size() +1 < set.size()) {
			//jos näyttää siltä että poistolistan koko ei riitä yksiselitteisen käsitteen löytämiseen
			ArrayList<Statement> list = new ArrayList<>(set);
			int pieninIndeksi = 0;
			for (int i = 1 ; i<list.size(); i++) {
				int levelCurrent = ResourceHierarchyComparator.hierarchyLevel(list.get(i).getResource());
				int levelReference = ResourceHierarchyComparator.hierarchyLevel(list.get(pieninIndeksi).getResource());
				if (levelCurrent > levelReference) {
					pieninIndeksi = i;
				} 
			}
			for (Statement s : set)
				if (!s.equals(list.get(pieninIndeksi)))
					result.add(s);
		} 
		return result;
	}

}

class ResourceComparator implements Comparator<Resource> {

	@Override

	/**
	 * 	The result is a negative integer if the String representation of the first argument lexicographically precedes the argument string.
	 *  The result is a positive integer if the String representation of the first argument lexicographically follows the argument string.
	 *  The result is zero if the String representations of the arguments are equal;
	 *  compareTo returns 0 exactly when the arg0.equals(arg1) method would return true.
	 * 
	 *  @auth joelitak
	 *  @version 1.0
	 *  @since 2019-03-13
	 * 
	 */
	public int compare(Resource arg0, Resource arg1) {
		return arg0.toString().compareTo(arg1.toString());
	}
}

class ResourceHierarchyComparator implements Comparator<Resource> {

	static Property broader = ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#broader");
	static Property narrower = ResourceFactory.createProperty("http://www.w3.org/2004/02/skos/core#narrower");

	@Override
	public int compare(Resource arg0, Resource arg1) {
		int levelOf0 = ResourceHierarchyComparator.hierarchyLevel(arg0);
		int levelOf1 = ResourceHierarchyComparator.hierarchyLevel(arg1);

		if (levelOf0 > levelOf1)
			return 1;
		if (levelOf0 < levelOf1)
			return -1;

		//if both are at the same level:
		int children0 = ResourceHierarchyComparator.numberOfSuccessors(arg0);
		int children1 = ResourceHierarchyComparator.numberOfSuccessors(arg1);

		if (children0 < children1)
			return 1;
		if (children0 > children1)
			return -1;

		//all else equal:
		return 0;
	}

	public static int hierarchyLevel(Resource res) {

		int dist = 0;
		Set<Statement> isot = res.listProperties(broader).toSet();
		while (isot.size() > 0) {

			Set<Statement> tmp = new HashSet<Statement>();
			for (Statement s :isot) {
				tmp.addAll(s.getResource().listProperties(broader).toSet());
			}
			isot = tmp;
			dist ++;
		}
		return dist;
	}

	public static int numberOfSuccessors(Resource res) {

		int child = 0;

		List<Statement> list = res.listProperties(narrower).toList();
		child += list.size();

		for (Statement s : list) {
			child += numberOfSuccessors(s.getResource());
		}

		return child;

	}

}