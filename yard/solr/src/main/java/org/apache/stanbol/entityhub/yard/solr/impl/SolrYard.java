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
package org.apache.stanbol.entityhub.yard.solr.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.PropertyOption;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.SolrRequest.METHOD;
import org.apache.solr.client.solrj.impl.CommonsHttpSolrServer;
import org.apache.solr.client.solrj.impl.StreamingUpdateSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SolrPingResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.apache.stanbol.entityhub.core.model.InMemoryValueFactory;
import org.apache.stanbol.entityhub.core.query.DefaultQueryFactory;
import org.apache.stanbol.entityhub.core.query.QueryResultListImpl;
import org.apache.stanbol.entityhub.core.utils.AdaptingIterator;
import org.apache.stanbol.entityhub.core.yard.AbstractYard;
import org.apache.stanbol.entityhub.servicesapi.model.Representation;
import org.apache.stanbol.entityhub.servicesapi.model.rdf.RdfResourceEnum;
import org.apache.stanbol.entityhub.servicesapi.query.Constraint;
import org.apache.stanbol.entityhub.servicesapi.query.FieldQuery;
import org.apache.stanbol.entityhub.servicesapi.query.QueryResultList;
import org.apache.stanbol.entityhub.servicesapi.yard.Yard;
import org.apache.stanbol.entityhub.servicesapi.yard.YardException;
import org.apache.stanbol.entityhub.yard.solr.SolrDirectoryManager;
import org.apache.stanbol.entityhub.yard.solr.SolrServerProviderManager;
import org.apache.stanbol.entityhub.yard.solr.SolrServerProvider.Type;
import org.apache.stanbol.entityhub.yard.solr.defaults.IndexDataTypeEnum;
import org.apache.stanbol.entityhub.yard.solr.impl.SolrQueryFactory.SELECT;
import org.apache.stanbol.entityhub.yard.solr.model.FieldMapper;
import org.apache.stanbol.entityhub.yard.solr.model.IndexField;
import org.apache.stanbol.entityhub.yard.solr.model.IndexValue;
import org.apache.stanbol.entityhub.yard.solr.model.IndexValueFactory;
import org.apache.stanbol.entityhub.yard.solr.query.IndexConstraintTypeEnum;
import org.apache.stanbol.entityhub.yard.solr.utils.SolrUtil;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Implementation of the {@link Yard} interface based on a Solr Server.<p>
 * This Yard implementation supports to store data of multiple yard instances
 * within the same Solr index. The {@link FieldMapper#getDocumentDomainField()}
 * with the value of the Yard ID ({@link #getId()}) is used to mark documents
 * stored by the different Yards using the same Index. Queries are also restricted
 * to documents stored by the actual Yard by adding a
 * <a href="http://wiki.apache.org/solr/CommonQueryParameters#fq">FilterQuery</a>
 * <code>fq=fieldMapper.getDocumentDomainField()+":"+getId()</code> to all
 * queries. This feature can be activated by setting the
 * {@link #MULTI_YARD_INDEX_LAYOUT} in the configuration. However this requires,
 * that the documents in the index are already marked with the ID of the Yard.
 * So setting this property makes usually only sense when the Solr index do not
 * contain any data.<p>
 * Also note, that the different Yards using the same index MUST NOT store
 * Representations with the same ID. If that happens, that the Yard writing the
 * Representation last will win and the Representation will be deleted for the
 * other Yard!<p>
 * The SolrJ library is used for the communication with the SolrServer.<p>
 * TODO: There is still some refactoring needed, because a lot of the code
 *       within this bundle is more generic and usable regardless what kind of
 *       "document based" store is used. Currently the Solr specific stuff is in
 *       the impl and the default packages. All the other classes are intended
 *       to be generally useful. However there might be still some unwanted
 *       dependencies.<p>
 * TODO: It would be possible to support for multi cores (see
 *       http://wiki.apache.org/solr/CoreAdmin for more Information)<br>
 *       However it is not possible to create cores on the fly (at least not directly;
 *       one would need to create first the needed directories and than call
 *       CREATE via the CoreAdmin). As soon as Solr is directly started via
 *       OSGI and we do know the Solr home, than it would be possible to
 *       implement "on the fly" generation of new cores. this would also allow
 *       a configuration where - as default - a new core is created automatically
 *       on the integrated Solr Server for any configured SolrYard.
 *
 * @author Rupert Westenthaler
 *
 */
@Component(
        metatype=true,
        configurationFactory=true,
        policy=ConfigurationPolicy.REQUIRE, //the ID and SOLR_SERVER_LOCATION are required!
        specVersion="1.1")
@Service
@Properties(value={
        //TODO: Added propertied from AbstractYard to fix ordering!
        @Property(name=Yard.ID,value="entityhubYard"),
        @Property(name=Yard.NAME,value="Entityhub Yard"),
        @Property(name=Yard.DESCRIPTION,value="The Yard used by the Entityhub to store the data"),
        @Property(name=AbstractYard.DEFAULT_QUERY_RESULT_NUMBER,intValue=-1),
        @Property(name=AbstractYard.MAX_QUERY_RESULT_NUMBER,intValue=-1),
        //BEGIN SolrYard specific Properties
        @Property(name=SolrYard.SOLR_SERVER_LOCATION,value="entityhub"),
        @Property(name=SolrYard.MULTI_YARD_INDEX_LAYOUT,options={
            @PropertyOption(name="true",value="true"),
            @PropertyOption(name="false",value="false")},value="false"),
        @Property(name=SolrYard.MAX_BOOLEAN_CLAUSES,intValue=SolrYard.defaultMaxBooleanClauses)
})
public class SolrYard extends AbstractYard implements Yard {
    /**
     * The key used to configure the URL for the SolrServer
     */
    public static final String SOLR_SERVER_LOCATION = "org.apache.stanbol.entityhub.yard.solr.solrUri";
    /**
     * The key used to configure if data of multiple Yards are stored within the
     * same index (<code>default=false</code>)
     */
    public static final String MULTI_YARD_INDEX_LAYOUT = "org.apache.stanbol.entityhub.yard.solr.multiYardIndexLayout";
    /**
     * The maximum boolean clauses as configured in the solrconfig.xml of the
     * SolrServer. The default value for this config in Solr 1.4 is 1024.<p>
     * This value is important for generating queries that search for multiple
     * documents, because it determines the maximum number of OR combination for
     * the searched document ids.
     */
    public static final String MAX_BOOLEAN_CLAUSES = "org.apache.stanbol.entityhub.yard.solr.maxBooleanClauses";
    /**
     * This property allows to define a field that is used to parse the boost
     * for the parsed representation. Typically this will be the pageRank of
     * that entity within the referenced site (e.g. {@link Math#log1p(double)}
     * of the number of incoming links
     */
    public static final String DOCUMENT_BOOST_FIELD = "org.apache.stanbol.entityhub.yard.solr.documentBoost";
    /**
     * Key used to configure {@link Entry Entry&lt;String,Float&gt;} for fields
     * with the boost. If no Map is configured or a field is not present in the
     * Map, than 1.0f is used as Boost. If a Document boost is present than the
     * boost of a Field is documentBoost*fieldBoost.
     */
    public static final String FIELD_BOOST_MAPPINGS = "org.apache.stanbol.entityhub.yard.solr.fieldBoosts";
    /**
     * Key used to configure the implementation of the {@link SolrServer} to
     * be used by this SolrYard implementation. The default value is determined
     * by the type of the value configured by the {@link #SOLR_SERVER_LOCATION}.
     * In case a path of a File URI is used, the type is set to
     * {@link Type#EMBEDDED} otherwise {@link Type#HTTP} is used as default.
     */
    public static final String SOLR_SERVER_TYPE = "org.apache.stanbol.entityhub.yard.solr.solrServerType";
    /**
     * The default value for the maxBooleanClauses of SolrQueries. Set to
     * {@value #defaultMaxBooleanClauses} the default of Slor 1.4
     */
    protected static final int defaultMaxBooleanClauses = 1024;
    /**
     * What a surprise it's the logger!
     */
    private Logger log = LoggerFactory.getLogger(SolrYard.class);
    /**
     * The SolrServer used for this Yard. Initialisation is done based on the
     * configured parameters in {@link #activate(ComponentContext)}.
     */
    private SolrServer server;
    /**
     * The {@link FieldMapper} is responsible for converting fields of
     * {@link Representation} to fields in the {@link SolrInputDocument} and
     * vice versa
     */
    private FieldMapper fieldMapper;
    /**
     * The {@link IndexValueFactory} is responsible for converting values of
     * fields in the {@link Representation} to the according {@link IndexValue}.
     * One should note, that some properties of the {@link IndexValue} such as
     * the language ({@link IndexValue#getLanguage()}) and the dataType
     * ({@link IndexValue#getType()}) are encoded within the field name inside
     * the {@link SolrInputDocument} and {@link SolrDocument}. This is done by
     * the configured {@link FieldMapper}.
     */
    private IndexValueFactory indexValueFactory;
    /**
     * The {@link SolrQueryFactory} is responsible for converting the
     * {@link Constraint}s of a query to constraints in the index. This requires
     * usually that a single {@link Constraint} is described by several
     * constraints in the index (see {@link IndexConstraintTypeEnum}).<p>
     * TODO: The encoding of such constraints is already designed correctly, the
     * {@link SolrQueryFactory} that implements logic of converting the
     * Incoming {@link Constraint}s and generating the {@link SolrQuery} needs
     * to undergo some refactoring!
     *
     */
    private SolrQueryFactory solrQueryFactoy;
    /**
     * Used to store the name of the field used to get the
     * {@link SolrInputDocument#setDocumentBoost(float)} for a Representation.
     * This name is available via {@link SolrYardConfig#getDocumentBoostFieldName()}
     * however it is stored here to prevent lookups for field of every
     * stored {@link Representation}.
     */
    private String documentBoostFieldName;
    /**
     * Map used to store boost values for fields. The default Boost for fields
     * is 1.0f. This is used if this map is <code>null</code>, a field is not
     * a key in this map, the value of a field in that map is <code>null</code> or
     * lower equals zero. Also NOTE that the boost for fields is multiplied with
     * the boost for the Document if present.
     */
    private Map<String,Float> fieldBoostMap;
    
    /**
     * Manager used to create the {@link SolrServer} instance used by this yard.
     * Supports also {@link Type#STREAMING} and {@link Type#LOAD_BALANCE} type
     * of servers.
     * TODO: In case a remove SolrServer is configured by the
     * {@link SolrYardConfig#getSolrServerLocation()}, than it would be possible
     * to create both an {@link StreamingUpdateSolrServer} (by parsing 
     * {@link Type#STREAMING}) and an normal {@link CommonsHttpSolrServer}. The
     * streaming update one should be used for indexing requests and the
     * commons http one for all other requests. This would provide performance
     * advantages when updating {@link Representation}s stored in a SolrYard
     * using an remote SolrServer.
     */
    @Reference
    private SolrServerProviderManager solrServerProviderManager;
    /**
     * Used to retrieve (and init if not already present) the Solr Index directory
     * for relative paths parsed for {@link SolrYardConfig#getSolrServerLocation()}.
     * Note that the {@link SolrDirectoryManager} only provides the path to the
     * files. The {@link SolrServer} instance is created by the
     * {@link SolrServerProviderManager}!
     */
    @Reference
    private SolrDirectoryManager solrDirectoryManager;
    /**
     * Default constructor as used by the OSGI environment.<p> DO NOT USE to
     * manually create instances! The SolrYard instances do need to be configured.
     * YOU NEED TO USE {@link #SolrYard(SolrYardConfig)} to parse the configuration
     * and the initialise the Yard if running outside a OSGI environment.
     */
    public SolrYard() { super(); }
    /**
     * Constructor to be used outside of an OSGI environment
     * @param config the configuration for the SolrYard
     * @throws IllegalArgumentException if the configuration is not valid
     * @throws YardException on any Error while initialising the Solr Server for
     * this Yard
     */
    public SolrYard(SolrYardConfig config) throws IllegalArgumentException, YardException {
        //we need to change the exceptions, because this will be called outside
        //of an OSGI environment!
        try {
            activate(config);
        } catch (IOException e) {
            new YardException("Unable to access SolrServer" +config.getSolrServerLocation());
        } catch (SolrServerException e) {
            new YardException("Unable to initialize SolrServer" +config.getSolrServerLocation());
        } catch (ConfigurationException e) {
            new IllegalArgumentException("Unable to initialise SolrYard with the provided configuration",e);
        }
    }
    /**
     * Builds an {@link SolrYardConfig} instance based on the parsed {@link ComponentContext}
     * and forwards to {@link #activate(SolrYardConfig)}.
     * @param context The component context only used to create the {@link SolrYardConfig}
     * based on {@link ComponentContext#getProperties()}.
     * @throws ConfigurationException If the configuration is not valid
     * @throws IOException In case the initialisation of the Solr index was not
     * possible
     * @throws SolrServerException Indicates that the referenced SolrServer has
     * some problems (usually an invalid configuration).
     */
    @SuppressWarnings("unchecked")
    @Activate
    protected final void activate(ComponentContext context) throws ConfigurationException,IOException,SolrServerException {
        if(context == null){
            throw new IllegalStateException("No valid"+ComponentContext.class+" parsed in activate!");
        }
        log.info("in "+SolrYard.class+" activate with config "+context.getProperties());
        activate(new SolrYardConfig((Dictionary<String, Object>)context.getProperties()));
    }
    /**
     * Internally used to configure an instance (within and without an OSGI
     * container
     * @param config The configuration
     * @throws ConfigurationException If the configuration is not valid
     * @throws IOException In case the initialisation of the Solr index was not
     * possible
     * @throws SolrServerException Indicates that the referenced SolrServer has
     * some problems (usually an invalid configuration).
     */
    private void activate(SolrYardConfig config) throws ConfigurationException,IOException,SolrServerException {
        //init with the default implementations of the ValueFactory and the QueryFactory
        super.activate(InMemoryValueFactory.getInstance(), DefaultQueryFactory.getInstance(), config);
        //mayby the super activate has updated the configuration
        config = (SolrYardConfig) this.getConfig();
        if(solrServerProviderManager == null){ //not within an OSGI environment
            solrServerProviderManager = SolrServerProviderManager.getInstance();
        }
        if(solrDirectoryManager == null) { //not within an OSGI environment
            //init via java.util.ServiceLoader
            Iterator<SolrDirectoryManager> providerIt = 
                ServiceLoader.load(SolrDirectoryManager.class,SolrDirectoryManager.class.getClassLoader()).iterator();
            if(providerIt.hasNext()){
                solrDirectoryManager = providerIt.next();
            } else {
                throw new IllegalStateException("Unable to instantiate "+SolrDirectoryManager.class.getSimpleName()+" service by using "+ServiceLoader.class.getName()+"!");
            }
        }
        String solrIndexLocation;
        if(config.getSolrServerType() == Type.EMBEDDED){
            File indexDirectory = ConfigUtils.toFile(config.getSolrServerLocation());
            if(!indexDirectory.isAbsolute()){ //relative paths
                // need to be resolved based on the internally managed Solr directory
                //TODO: for now parse TRUE to allow automatic creation if the index
                //      does not already exist. We might add this as an additional
                //      parameter to the SolrIndexConfig 
                indexDirectory = solrDirectoryManager.getSolrDirectory(indexDirectory.toString(),true);
            }
            solrIndexLocation = indexDirectory.toString();
        } else {
            solrIndexLocation = config.getSolrServerLocation();
        }
        server = solrServerProviderManager.getSolrServer(
            config.getSolrServerType(), 
            solrIndexLocation);
        //test the server
        SolrPingResponse pingResponse = server.ping();
        log.info(String.format("Successful ping for SolrServer %s ( %d ms) Details: %s",config.getSolrServerLocation(),pingResponse.getElapsedTime(),pingResponse));
        //the fieldMapper need the Server to store it's namespace prefix configuration
        this.fieldMapper = new SolrFieldMapper(server);
        this.indexValueFactory = IndexValueFactory.getInstance();
        this.solrQueryFactoy = new SolrQueryFactory(getValueFactory(), indexValueFactory, fieldMapper);
        if(config.isMultiYardIndexLayout()){ // set the yardID as domain if multiYardLayout is activated
            solrQueryFactoy.setDomain(config.getId());
        }
        solrQueryFactoy.setDefaultQueryResults(config.getDefaultQueryResultNumber());
        solrQueryFactoy.setMaxQueryResults(config.getMaxQueryResultNumber());
        this.documentBoostFieldName = config.getDocumentBoostFieldName();
        this.fieldBoostMap = config.getFieldBoosts();
    }
    /**
     * Deactivates this SolrYard instance after committing remaining changes
     * @param context
     */
    @Deactivate
    protected final void deactivate(ComponentContext context) {
        SolrYardConfig config = (SolrYardConfig)getConfig();
        log.info("... deactivating SolrYard "+config.getName()+" (id="+config.getId()+")");
        try {
            this.server.commit();
        } catch (SolrServerException e) {
            log.error(String.format("Unable to commit unsaved changes to SolrServer %s during deactivate!",config.getSolrServerLocation()),e);
        } catch (IOException e) {
            log.error(String.format("Unable to commit unsaved changes to SolrServer %s during deactivate!",config.getSolrServerLocation()),e);
        }
        this.server = null;
        this.fieldMapper = null;
        this.indexValueFactory = null;
        this.solrQueryFactoy = null;
        this.documentBoostFieldName  = null;
        this.fieldBoostMap = null;
        super.deactivate(); //deactivate the super implementation
    }

    /**
     * Calls the {@link #deactivate(ComponentContext)} with <code>null</code>
     * as component context
     */
    @Override
    protected void finalize() throws Throwable {
        deactivate(null);
        super.finalize();
    }

    @Override
    public final QueryResultList<Representation> find(final FieldQuery parsedQuery) throws YardException{
        return find(parsedQuery,SELECT.QUERY);
    }
    private QueryResultList<Representation> find(final FieldQuery parsedQuery,SELECT select) throws YardException {
        log.debug("find "+parsedQuery);
        long start = System.currentTimeMillis();
        SolrQuery query = solrQueryFactoy.parseFieldQuery(parsedQuery,select);
        long queryGeneration = System.currentTimeMillis();
        final Set<String> selected;
        if(select == SELECT.QUERY){
			//if query set the fields to add to the result Representations
			selected = new HashSet<String>(parsedQuery.getSelectedFields());
			//add the score to query results!
			selected.add(RdfResourceEnum.resultScore.getUri());
        } else {
            //otherwise add all fields
            selected = null;
        }
        QueryResponse respone;
        try {
            respone = server.query(query,METHOD.POST);
        } catch (SolrServerException e) {
            throw new YardException("Error while performing Query on SolrServer!",e);
        }
        long queryTime = System.currentTimeMillis();
        //return a queryResultList
        QueryResultListImpl<Representation> resultList = new QueryResultListImpl<Representation>(parsedQuery,
                // by adapting SolrDocuments to Representations
                new AdaptingIterator<SolrDocument, Representation>(respone.getResults().iterator(),
                        //inline Adapter Implementation
                        new AdaptingIterator.Adapter<SolrDocument, Representation>() {
                            @Override
                            public Representation adapt(SolrDocument doc, Class<Representation> type) {
                                //use this method for the conversion!
                                return createRepresentation(doc, selected);
                            }
                },Representation.class), Representation.class);
        long resultProcessing = System.currentTimeMillis();
        log.debug(String.format("  ... done [queryGeneration=%dms|queryTime=%dms|resultProcessing=%dms|sum=%dms]",
                (queryGeneration-start),(queryTime-queryGeneration),(resultProcessing-queryTime),(resultProcessing-start)));
        return resultList;
    }

    @Override
    public final QueryResultList<String> findReferences(FieldQuery parsedQuery) throws YardException {
        SolrQuery query = solrQueryFactoy.parseFieldQuery(parsedQuery,SELECT.ID);
        QueryResponse respone;
        try {
            respone = server.query(query,METHOD.POST);
        } catch (SolrServerException e) {
            throw new YardException("Error while performing query on the SolrServer",e);
        }
        //return a queryResultList
        return new QueryResultListImpl<String>(parsedQuery,
                // by adapting SolrDocuments to Representations
                new AdaptingIterator<SolrDocument, String>(respone.getResults().iterator(),
                        //inline Adapter Implementation
                        new AdaptingIterator.Adapter<SolrDocument, String>() {
                            @Override
                            public String adapt(SolrDocument doc, Class<String> type) {
                                //use this method for the conversion!
                                return doc.getFirstValue(fieldMapper.getDocumentIdField()).toString();
                            }
                },String.class), String.class);
    }

    @Override
    public final QueryResultList<Representation> findRepresentation(FieldQuery parsedQuery) throws YardException {
        return find(parsedQuery,SELECT.ALL);
    }

    @Override
    public final Representation getRepresentation(String id) throws YardException {
        if(id == null){
            throw new IllegalArgumentException("The parsed Representation id MUST NOT be NULL!");
        }
        if(id.isEmpty()){
            throw new IllegalArgumentException("The parsed Representation id MUST NOT be empty!");
        }
        SolrDocument doc;
        long start = System.currentTimeMillis();
        try {
            doc = getSolrDocument(id);
        } catch (SolrServerException e) {
            throw new YardException("Error while getting SolrDocument for id"+id,e);
        } catch (IOException e) {
            throw new YardException("Unable to access SolrServer",e);
        }
        long retrieve = System.currentTimeMillis();
        Representation rep;
        if(doc != null){
            //create an Representation for the Doc! retrieve
            log.debug(String.format("Create Representation %s from SolrDocument",doc.getFirstValue(fieldMapper.getDocumentIdField())));
            rep =  createRepresentation(doc,null);
        } else {
            rep = null;
        }
        long create = System.currentTimeMillis();
        log.debug(String.format("  ... %s [retrieve=%dms|create=%dms|sum=%dms]",
                rep==null?"not found":"done",(retrieve-start),(create-retrieve),(create-start)));
        return rep;
    }
    /**
     * Creates the Representation for the parsed SolrDocument!
     * @param doc The Solr Document to convert
     * @param fields if NOT NULL only this fields are added to the Representation
     * @return the Representation
     */
    protected final Representation createRepresentation(SolrDocument doc, Set<String> fields) {
        Object id = doc.getFirstValue(fieldMapper.getDocumentIdField());
        if(id == null){
            throw new IllegalStateException(
                    String.format("The parsed Solr Document does not contain a value for the %s Field!",
                            fieldMapper.getDocumentIdField()));
        }
        Representation rep = getValueFactory().createRepresentation(id.toString());
        for(String fieldName : doc.getFieldNames()){
            IndexField indexField = fieldMapper.getField(fieldName);
            if(indexField != null && indexField.getPath().size() == 1){
                String lang = indexField.getLanguages().isEmpty()?null:indexField.getLanguages().iterator().next();
                if(fields == null || fields.contains(indexField.getPath().get(0))){
                    for(Object value : doc.getFieldValues(fieldName)){
                        if(value != null){
                            IndexDataTypeEnum dataTypeEnumEntry = IndexDataTypeEnum.forIndexType(indexField.getDataType());
                            if(dataTypeEnumEntry != null){
                                Object javaValue = indexValueFactory.createValue(dataTypeEnumEntry.getJavaType(), indexField.getDataType(),value,lang);
                                if(javaValue != null){
                                    rep.add(indexField.getPath().iterator().next(), javaValue);
                                } else {
                                    log.warn(String.format("java value=null for index value %s",value));
                                }
                            } else {
                                log.warn(String.format("No DataType Configuration found for Index Data Type %s!",indexField.getDataType()));
                            }
                        } //else index value == null -> ignore
                    } //end for all values
                }
            } else {
                if(indexField != null){
                    log.warn(String.format("Unable to prozess Index Field %s (for IndexDocument Field: %s)",indexField,fieldName));
                }
            }
        } //end for all fields
        return rep;
    }


    @Override
    public final boolean isRepresentation(String id) throws YardException {
        if(id == null){
            throw new IllegalArgumentException("The parsed Representation id MUST NOT be NULL!");
        }
        if(id.isEmpty()){
            throw new IllegalArgumentException("The parsed Representation id MUST NOT be empty!");
        }
        try {
            return getSolrDocument(id,Arrays.asList(fieldMapper.getDocumentIdField()))!=null;
        } catch (SolrServerException e) {
            throw new YardException("Error while performing getDocumentByID request for id "+id,e);
        } catch (IOException e) {
            throw new YardException("Unable to access SolrServer",e);
        }
    }
    /**
     * Checks what of the documents referenced by the parsed IDs are present
     * in the Solr Server
     * @param ids the ids of the documents to check
     * @return the ids of the found documents
     * @throws SolrServerException on any exception of the SolrServer
     * @throws IOException an any IO exception while accessing the SolrServer
     */
    protected final Set<String> checkRepresentations(Set<String> ids) throws SolrServerException, IOException{
        Set<String> found = new HashSet<String>();
        String field = fieldMapper.getDocumentIdField();
        for(SolrDocument foundDoc : getSolrDocuments(ids,Arrays.asList(field))){
            Object value = foundDoc.getFirstValue(field);
            if(value != null){
                found.add(value.toString());
            }
        }
        return found;
    }

    @Override
    public final void remove(String id) throws YardException, IllegalArgumentException {
        if(id == null){
            throw new IllegalArgumentException("The parsed Representation id MUST NOT be NULL!");
        }
        if(id.isEmpty()){
            throw new IllegalArgumentException("The parsed Representation id MUST NOT be empty!");
        }
        try {
            server.deleteById(id);
            server.commit();
        } catch (SolrServerException e) {
            throw new YardException("Error while deleting document "+id+" from the Solr server",e);
        } catch (IOException e) {
            throw new YardException("Unable to access SolrServer",e);
        }
        //NOTE: We do not need to update all Documents that refer this ID, because
        //      only the representation of the Entity is deleted and not the
        //      Entity itself. So even that we do no longer have an representation
        //      the entity still exists and might be referenced by others!
    }
    @Override
    public final void remove(Iterable<String> ids) throws IllegalArgumentException, YardException {
        if(ids == null){
            throw new IllegalArgumentException("The parsed IDs MUST NOT be NULL");
        }
        List<String> toRemove = new ArrayList<String>();
        for(String id :ids){
            if(id != null && !id.isEmpty()){
                toRemove.add(id);
            }
        }
        try {
            server.deleteById(toRemove);
            server.commit();
        } catch (SolrServerException e) {
            throw new YardException("Error while deleting documents from the Solr server",e);
        } catch (IOException e) {
            throw new YardException("Unable to access SolrServer",e);
        }
        //NOTE: We do not need to update all Documents that refer this ID, because
        //      only the representation of the Entity is deleted and not the
        //      Entity itself. So even that we do no longer have an representation
        //      the entity still exists and might be referenced by others!
    }
    @Override
    public final Representation store(Representation representation) throws YardException,IllegalArgumentException {
        log.debug(String.format("Store %s",representation!= null?representation.getId():null));
        if(representation == null){
            throw new IllegalArgumentException("The parsed Representation MUST NOT be NULL!");
        }
        long start = System.currentTimeMillis();
        SolrInputDocument inputDocument = createSolrInputDocument(representation);
        long create = System.currentTimeMillis();
        try {
            server.add(inputDocument);
            server.commit();
            long stored = System.currentTimeMillis();
            log.debug(String.format("  ... done [create=%dms|store=%dms|sum=%dms]",
                    (create-start),(stored-create),(stored-start)));
        } catch (SolrServerException e) {
            throw new YardException(String.format("Exception while adding Document to Solr",representation.getId()),e);
        } catch (IOException e) {
            throw new YardException("Unable to access SolrServer",e);
        }
        return representation;
    }
    @Override
    public final Iterable<Representation> store(Iterable<Representation> representations) throws IllegalArgumentException, YardException {
        if(representations == null){
            throw new IllegalArgumentException("The parsed Representations MUST NOT be NULL!");
        }
        Collection<Representation> added = new HashSet<Representation>();
        long start = System.currentTimeMillis();
        Collection<SolrInputDocument> inputDocs = new HashSet<SolrInputDocument>();
        for(Representation representation : representations){
            if(representation != null){
                inputDocs.add(createSolrInputDocument(representation));
                added.add(representation);
            }
        }
        long created = System.currentTimeMillis();
        try {
            server.add(inputDocs);
            server.commit();
        } catch (SolrServerException e) {
            throw new YardException("Exception while adding Documents to the Solr Server!",e);
        } catch (IOException e) {
            throw new YardException("Unable to access Solr server",e);
        }
        long ready = System.currentTimeMillis();
        log.debug(String.format("Processed store request for %d documents in %dms (created %dms| stored%dms)",
                inputDocs.size(),ready-start,created-start,ready-created));
        return added;
    }
    /**
     * Internally used to create Solr input documents for parsed representations.<p>
     * This method supports boosting of fields. The boost is calculated by combining<ol>
     * <li> the boot for the whole representation - by calling 
     * {@link #getDocumentBoost(Representation)}
     * <li> the boost of each field - by using the configured {@link #fieldBoostMap}
     * </ol>
     * @param representation the representation
     * @return the Solr document for indexing
     */
    protected final SolrInputDocument createSolrInputDocument(Representation representation) {
        SolrYardConfig config = (SolrYardConfig)getConfig();
        SolrInputDocument inputDocument = new SolrInputDocument();
        // If multiYardLayout is active, than we need to add the YardId as
        // domain for all added documents!
        if(config.isMultiYardIndexLayout()){
            inputDocument.addField(fieldMapper.getDocumentDomainField(), config.getId());
        } // else we need to do nothing
        inputDocument.addField(fieldMapper.getDocumentIdField(), representation.getId());
        //first process the document boost
        float documentBoost = documentBoostFieldName == null ? 1.0f : getDocumentBoost(representation);
        for(Iterator<String> fields = representation.getFieldNames();fields.hasNext();){
            //TODO: maybe add some functionality to prevent indexing of the
            //      field configured as documentBoostFieldName!
            //      But this would also prevent the possibility to intentionally
            //      override the boost. 
            String field = fields.next();
            Float fieldBoost = fieldBoostMap == null ? null : fieldBoostMap.get(field);
            float boost = fieldBoost == null ? documentBoost : fieldBoost >= 0 ? fieldBoost * documentBoost: documentBoost;
            for(Iterator<Object> values = representation.get(field);values.hasNext();){
                //now we need to get the indexField for the value
                Object next = values.next();
                IndexValue value;
                try {
                    value = indexValueFactory.createIndexValue(next);
                    for(String fieldName : fieldMapper.getFieldNames(Arrays.asList(field), value)){
                        inputDocument.addField(fieldName, value.getValue(),boost);
                    }
                }catch(Exception e){
                    log.warn(String.format("Unable to process value %s (type:%s) for field %s!",next,next.getClass(),field),e);
                }
            }
        }
        return inputDocument;
    }
    /**
     * Extracts the document boost from a {@link Representation}.
     * @param representation the representation
     * @return the Boost or <code>null</code> if not found or lower equals zero
     */
    private float getDocumentBoost(Representation representation) {
        if(documentBoostFieldName == null){
            return 1.0f;
        }
        Float documentBoost = null;
        for(Iterator<Object> values =representation.get(documentBoostFieldName);values.hasNext() && documentBoost == null;){
            Object value = values.next();
            if(value instanceof Float){
                documentBoost = (Float) value;
            } else {
                try {
                    documentBoost = Float.parseFloat(value.toString());
                } catch (NumberFormatException e) {
                    log.warn(String.format("Unable to parse the Document Boost from field %s=%s[type=%s] -> The Document Boost MUST BE a Float value!",documentBoostFieldName,value,value.getClass()));
                }
            }
        }
        return documentBoost == null? 1.0f : documentBoost >= 0 ? documentBoost : 1.0f;
    }

    @Override
    public final Representation update(Representation representation) throws IllegalArgumentException, NullPointerException, YardException {
        if(representation == null){
            throw new IllegalArgumentException("The parsed Representation MUST NOT be NULL!");
        }
        boolean found  = isRepresentation(representation.getId());
        if(found) {
            return store(representation); //there is no "update" for solr
        } else {
            throw new IllegalArgumentException("Parsed Representation "+representation.getId()+" in not managed by this Yard "+getName()+"(id="+getId()+")");
        }
    }
    @Override
    public final Iterable<Representation> update(Iterable<Representation> representations) throws YardException, IllegalArgumentException, NullPointerException {
        if(representations == null){
            throw new IllegalArgumentException("The parsed Iterable over Representations MUST NOT be NULL!");
        }
        long start = System.currentTimeMillis();
        Set<String> ids = new HashSet<String>();

        for(Representation representation : representations){
            if(representation != null){
                ids.add(representation.getId());
            }
        }
        int numDocs = ids.size(); //for debuging
        try {
            ids = checkRepresentations(ids); //returns the ids found in the solrIndex
        } catch (SolrServerException e) {
            throw new YardException("Error while searching for alredy present documents before executing the actual update for the parsed Representations",e);
        } catch (IOException e) {
            throw new YardException("Unable to access SolrServer",e);
        }
        long checked = System.currentTimeMillis();
        List<SolrInputDocument> inputDocs = new ArrayList<SolrInputDocument>(ids.size());
        List<Representation> updated = new ArrayList<Representation>();
        for(Representation representation : representations){
            if(representation != null && ids.contains(representation.getId())){ //null parsed or not already present
                inputDocs.add(createSolrInputDocument(representation));
                updated.add(representation);
            }
        }
        long created = System.currentTimeMillis();
        if(!inputDocs.isEmpty()) {
            try {
                server.add(inputDocs);
                server.commit();
            } catch (SolrServerException e) {
                throw new YardException("Error while adding updated Documents to the SolrServer",e);
            } catch (IOException e) {
                throw new YardException("Unable to access Solr server",e);
            }
        }
        long ready = System.currentTimeMillis();
        log.info(String.format("Processed updateRequest for %d documents (%d in index | %d updated) in %dms (checked %dms|created %dms| stored%dms)",
                numDocs,ids.size(),updated.size(),ready-start,checked-start,created-checked,ready-created));
        return updated;
    }
    /**
     * Stores the parsed document within the Index. This Method is also used by
     * other classes within this package to store configurations directly within
     * the index
     * @param inputDoc the document to store
     */
    protected final void storeSolrDocument(SolrInputDocument inputDoc) throws SolrServerException, IOException{
        server.add(inputDoc);
    }
    /**
     * Getter for a SolrDocument based on the ID. This Method is also used by
     * other classes within this package to load configurations directly from
     * the index
     * @param inputDoc the document to store
     */
    public final SolrDocument getSolrDocument(String uri) throws SolrServerException, IOException {
        return getSolrDocument(uri, null);
    }
    protected final Collection<SolrDocument> getSolrDocuments(Collection<String> uris,Collection<String> fields) throws SolrServerException, IOException {
        SolrYardConfig config = (SolrYardConfig)getConfig();
        SolrQuery solrQuery = new SolrQuery();
        if(fields == null || fields.isEmpty()){
            solrQuery.addField("*"); //select all fields
        } else {
            for(String field : fields){
                if(field !=null && !field.isEmpty()){
                    solrQuery.addField(field);
                }
            }
        }
        //NOTE: If there are more requested documents than allowed boolean
        //      clauses in one query, than we need to send several requests!
        Iterator<String> uriIterator = uris.iterator();
        int maxClauses;
        Integer configuredMaxClauses = config.getMaxBooleanClauses();
        if(configuredMaxClauses != null && configuredMaxClauses > 0){
            maxClauses = configuredMaxClauses;
        } else {
            maxClauses = defaultMaxBooleanClauses;
        }
        int num = 0;
        StringBuilder queryBuilder = new StringBuilder();
        boolean myList = false;
        Collection<SolrDocument> resultDocs = null;
        //do while more uris
        while(uriIterator.hasNext()){
            //do while more uris and free boolean clauses
            //num <= maxClauses because 1-items boolean clauses in the query!
            while(uriIterator.hasNext() && num <= maxClauses){
                String uri = uriIterator.next();
                if(uri !=null){
                    if(num > 0){
                        queryBuilder.append(" OR ");
                    }
                    queryBuilder.append(String.format("%s:%s",
                            fieldMapper.getDocumentIdField(),
                            SolrUtil.escapeSolrSpecialChars(uri)));
                    num++;
                }
            }
            log.info("Get SolrDocuments for Query: "+queryBuilder.toString());
            //no more items or all boolean clauses used -> send a request
            solrQuery.setQuery(queryBuilder.toString());
            queryBuilder = new StringBuilder(); // and a new StringBuilder
            //set the number of results to the number of parsed IDs.
            solrQuery.setRows(num); 
            num = 0; //reset to 0
            QueryResponse queryResponse = server.query(solrQuery,METHOD.POST);
            if(resultDocs == null){
                resultDocs = queryResponse.getResults();
            } else {
                if(!myList){
                    //most of the time there will be only one request, so only
                    //create my own list when the second response is processed
                    resultDocs = new ArrayList<SolrDocument>(resultDocs);
                    myList = true;
                }
                resultDocs.addAll(queryResponse.getResults());
            }
        } //end while more uris
        return resultDocs;
    }
    protected final SolrDocument getSolrDocument(String uri,Collection<String> fields) throws SolrServerException, IOException {
        SolrQuery solrQuery = new SolrQuery();
        if(fields == null || fields.isEmpty()){
            solrQuery.addField("*"); //select all fields
        } else {
            for(String field : fields){
                if(field !=null && !field.isEmpty()){
                    solrQuery.addField(field);
                }
            }
        }
        solrQuery.setRows(1); //we query for the id, there is only one result
        String queryString = String.format("%s:%s",
                fieldMapper.getDocumentIdField(),SolrUtil.escapeSolrSpecialChars(uri));
        solrQuery.setQuery(queryString);
        QueryResponse queryResponse = server.query(solrQuery,METHOD.POST);
        if(queryResponse.getResults().isEmpty()){
            return null;
        } else {
            return queryResponse.getResults().get(0);
        }
    }
}
