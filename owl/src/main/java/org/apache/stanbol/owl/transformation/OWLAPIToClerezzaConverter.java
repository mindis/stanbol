/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.stanbol.owl.transformation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.Triple;
import org.apache.clerezza.rdf.core.TripleCollection;
import org.apache.clerezza.rdf.core.impl.SimpleMGraph;
import org.apache.clerezza.rdf.core.serializedform.ParsingProvider;
import org.apache.clerezza.rdf.core.serializedform.SerializingProvider;
import org.apache.clerezza.rdf.core.serializedform.SupportedFormat;
import org.apache.clerezza.rdf.jena.parser.JenaParserProvider;
import org.apache.clerezza.rdf.jena.serializer.JenaSerializerProvider;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.RDFXMLOntologyFormat;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.graph.Graph;
import com.hp.hpl.jena.rdf.model.Model;

/**
 * This class provides static methods to convert:
 * 
 * <ul>
 * <li>a Jena Model (see {@link Model}) to a list of Clerezza triples (see {@link Triple})
 * <li>a Jena Model to a Clerezza MGraph (see {@link MGraph})
 * <li>a Clerezza MGraph a Jena Model
 * <li>a Clerezza MGraph a Jena Graph (see {@link Graph}
 * </ul>
 * 
 * 
 * @author andrea.nuzzolese
 * 
 */

public class OWLAPIToClerezzaConverter {

    private static Logger log = LoggerFactory.getLogger(OWLAPIToClerezzaConverter.class);

    /**
     * 
     * Converts an OWL API {@link OWLOntology} to an {@link ArrayList} of Clerezza triples (instances of class
     * {@link Triple}).
     * 
     * @param ontology
     *            {@link OWLOntology}
     * @return an {@link ArrayList} that contains the generated Clerezza triples (see {@link Triple})
     */
    public static ArrayList<Triple> owlOntologyToClerezzaTriples(OWLOntology ontology) {
        ArrayList<Triple> clerezzaTriples = new ArrayList<Triple>();
        TripleCollection mGraph = owlOntologyToClerezzaMGraph(ontology);
        Iterator<Triple> tripleIterator = mGraph.iterator();
        while (tripleIterator.hasNext()) {
            Triple triple = tripleIterator.next();
            clerezzaTriples.add(triple);
        }
        return clerezzaTriples;
    }

    /**
     * 
     * Converts a OWL API {@link OWLOntology} to Clerezza {@link MGraph}.
     * 
     * @param ontology
     *            {@link OWLOntology}
     * @return the equivalent Clerezza {@link MGraph}.
     */

    public static TripleCollection owlOntologyToClerezzaMGraph(OWLOntology ontology) {
        MGraph mGraph = null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OWLOntologyManager manager = ontology.getOWLOntologyManager();
        try {
            manager.saveOntology(ontology, new RDFXMLOntologyFormat(), out);
            ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
            ParsingProvider parser = new JenaParserProvider();
            mGraph = new SimpleMGraph();
            parser.parse(mGraph, in, SupportedFormat.RDF_XML, null);
        } catch (OWLOntologyStorageException e) {
            log.error("Failed to serialize OWL Ontology " + ontology + "for conversion", e);
        }
        return mGraph;

    }

    /**
     * Converts a Clerezza {@link MGraph} to an OWL API {@link OWLOntology}.
     * 
     * @param mGraph
     *            {@link MGraph}
     * @return the equivalent OWL API {@link OWLOntology}.
     */
    public static OWLOntology clerezzaGraphToOWLOntology(TripleCollection graph) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SerializingProvider serializingProvider = new JenaSerializerProvider();
        serializingProvider.serialize(out, graph, SupportedFormat.RDF_XML);
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
        OWLOntology ontology = null;
        try {
            ontology = OWLManager.createOWLOntologyManager().loadOntologyFromOntologyDocument(in);
        } catch (OWLOntologyCreationException e) {
            log.error("Failed to serialize OWL Ontology " + ontology + "for conversion", e);
        }
        return ontology;
    }

}
