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
package org.apache.stanbol.contenthub.search.solr.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.LukeRequest;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.stanbol.contenthub.servicesapi.store.vocabulary.SolrVocabulary;
import org.apache.stanbol.contenthub.servicesapi.store.vocabulary.SolrVocabulary.SolrFieldName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author anil.pacaci
 * 
 */
public class SolrQueryUtil {

    private final static Logger log = LoggerFactory.getLogger(SolrQueryUtil.class);

    public static final String CONTENT_FIELD = "content_t";
    public static final String ID_FIELD = "id";
    public static final String SCORE_FIELD = "score";

    public final static String and = "AND";
    public final static String or = "OR";
    public final static String facetDelimiter = ":";
    public final static char quotation = '"';

    public final static List<Character> queryDelimiters = Arrays.asList(' ', ',');

    private static SolrQuery keywordQueryWithFacets(String keyword, Map<String,List<Object>> constraints) {
        SolrQuery query = new SolrQuery();
        //String queryString = keyword;
        query.setQuery(keyword);
        if (constraints != null) {
            try {
                for (Entry<String,List<Object>> entry : constraints.entrySet()) {
                    String fieldName = ClientUtils.escapeQueryChars(entry.getKey());
                    for (Object value : entry.getValue()) {
                        query.addFilterQuery(fieldName + facetDelimiter + quotation + (String) value + quotation);
                        /*query.addFacetQuery(fieldName + facetDelimiter + (SolrVocabulary.isNameRangeField(fieldName) ? (String) value
                                    : ClientUtils.escapeQueryChars(quotation + (String) value
                                                                   + quotation)));*/
                        /*queryString = queryString
                                      + " "
                                      + and
                                      + " "
                                      + fieldName
                                      + facetDelimiter
                                      + (SolrVocabulary.isNameRangeField(fieldName) ? (String) value
                                              : ClientUtils.escapeQueryChars(quotation + (String) value
                                                                             + quotation));*/
                    }
                }
            } catch (Exception e) {
                log.warn("Facet constraints could not be added to Query", e);
            }
        }
        //query.setQuery(queryString);
        return query;
    }

    private static String removeFacetConstraints(String query) {
        int delimiteri = query.indexOf(facetDelimiter);
        while (delimiteri > -1) {
            int starti = delimiteri;
            for (starti = delimiteri; starti >= 0 && !queryDelimiters.contains(query.charAt(starti)); starti--)
                ;
            ++starti;

            int endi = delimiteri + 1;
            if (endi < query.length()) {
                if (query.charAt(endi) == quotation) {
                    ++endi;
                    for (; endi < query.length() && query.charAt(endi) != quotation; endi++)
                        ;
                    ++endi;
                } else {
                    for (; endi < query.length() && !queryDelimiters.contains(query.charAt(endi)); endi++)
                        ;
                }
            }
            query = query.substring(0, starti) + (endi >= query.length() ? "" : query.substring(endi));
            delimiteri = query.indexOf(facetDelimiter);
        }
        query = query.replaceAll(and + '|' + or + '|' + and.toLowerCase() + '|' + or.toLowerCase(), "");
        return query;
    }

    private static String removeSpecialCharacter(String query, char ch) {
        int starti = query.indexOf(ch);
        while (starti > -1) {
            int endi = -1;
            for (endi = starti; endi < query.length() && !queryDelimiters.contains(query.charAt(endi)); endi++)
                ;
            query = query.substring(0, starti) + (endi >= query.length() ? "" : query.substring(endi));
            starti = query.indexOf(ch);
        }
        return query;
    }

    private static String removeSpecialCharacters(String query) {
        query = query.replaceAll("[+|\\-&!\\(\\)\\{\\}\\[\\]\\*\\?\\\\]", "");
        query = removeSpecialCharacter(query, '^');
        query = removeSpecialCharacter(query, '~');
        return query;
    }

    public static String extractQueryTermFromSolrQuery(SolrParams solrQuery) {
        String queryFull = solrQuery instanceof SolrQuery ? ((SolrQuery) solrQuery).getQuery() : solrQuery
                .get(CommonParams.Q);
        queryFull = removeSpecialCharacters(queryFull);
        queryFull = removeFacetConstraints(queryFull);
        return queryFull.trim();
    }

    public static SolrQuery prepareFacetedSolrQuery(String queryTerm,
                                                    List<String> allAvailableFacetNames,
                                                    Map<String,List<Object>> constraints) {
        SolrQuery solrQuery = keywordQueryWithFacets(queryTerm, constraints);
        setDefaultQueryParameters(solrQuery, allAvailableFacetNames);
        return solrQuery;
    }

    private static void setDefaultQueryParameters(SolrQuery solrQuery, List<String> allAvailableFacetNames) {
        solrQuery.setFields("*", SCORE_FIELD);
        solrQuery.setSortField(SolrFieldName.CREATIONDATE.toString(), SolrQuery.ORDER.desc);
        solrQuery.setFacet(true);
        solrQuery.setFacetMinCount(1);
        if (allAvailableFacetNames != null) {
            for (String facetName : allAvailableFacetNames) {
                if (SolrFieldName.CREATIONDATE.toString().equals(facetName)
                    || (!SolrFieldName.isNameReserved(facetName) && !SolrVocabulary.isNameExcluded(facetName))) {
                    solrQuery.addFacetField(facetName);
                }
            }
        }
    }

    public static SolrQuery prepareDefaultSolrQuery(SolrServer solrServer, String queryTerm) throws SolrServerException,
                                                                                            IOException {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery(queryTerm);
        setDefaultQueryParameters(solrQuery, getFacetNames(solrServer));
        return solrQuery;
    }

    public static SolrQuery prepareDefaultSolrQuery(String queryTerm, List<String> allAvailableFacetNames) {
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery(queryTerm);
        setDefaultQueryParameters(solrQuery, allAvailableFacetNames);
        return solrQuery;
    }

    public static List<String> getFacetNames(SolrServer solrServer) throws SolrServerException, IOException {
        List<String> facetNames = new ArrayList<String>();
        LukeRequest qr = new LukeRequest();
        NamedList<Object> qresp = solrServer.request(qr);
        Object fields = qresp.get("fields");
        if (fields instanceof NamedList<?>) {
            @SuppressWarnings("unchecked")
            NamedList<Object> fieldsList = (NamedList<Object>) fields;
            for (int i = 0; i < fieldsList.size(); i++) {
                facetNames.add(fieldsList.getName(i));
            }
        } else {
            log.warn("Fields container is not a NamedList, so there is no facet information available");
        }
        return facetNames;
    }
}
